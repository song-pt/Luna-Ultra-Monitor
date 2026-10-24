// Original Swift adaptation of this project's Kotlin UCD2/protobuf implementation.
import Foundation

enum Wire {
    static func varint(_ n: UInt64) -> [UInt8] {
        var n=n; var b=[UInt8]()
        repeat { let v=UInt8(n & 127); n >>= 7; b.append(v | (n > 0 ? 128:0)) } while n>0
        return b
    }
    static func int(_ field: Int, _ n: Int) -> [UInt8] { varint(UInt64(field << 3)) + varint(UInt64(n)) }
    static func bytes(_ field:Int,_ b:[UInt8])->[UInt8] { varint(UInt64((field<<3)|2)) + varint(UInt64(b.count)) + b }
    static func double(_ field:Int,_ value:Double)->[UInt8] { var b=value.bitPattern.littleEndian;return varint(UInt64((field<<3)|1))+withUnsafeBytes(of:&b){Array($0)} }
    static func zig(_ n:Int)->Int { (n<<1) ^ (n>>63) }
    static func le(_ n:UInt32)->[UInt8] { (0..<4).map {UInt8(truncatingIfNeeded:n >> ($0*8))} }
    static func u32(_ b:[UInt8],_ i:Int)->UInt32 { UInt32(b[i]) | UInt32(b[i+1])<<8 | UInt32(b[i+2])<<16 | UInt32(b[i+3])<<24 }
    static let crcTable:[UInt32]=(0..<256).map { i in
        var v=UInt32(i)<<24
        for _ in 0..<8 {v = (v & 0x80000000 != 0) ? (v<<1)^0x04c11db7 : v<<1}
        return v
    }
    static func crc(_ bytes:[UInt8])->UInt32 {
        var v:UInt32=0xffffffff
        for b in bytes { v ^= UInt32(b);for _ in 0..<4 {v=(v<<8)^crcTable[Int(v>>24)]} }
        return v
    }
    static let magic:[UInt8]=[0x55,0x43,0x44,0x32]
    static func hello(_ seq:UInt8)->[UInt8] {magic+[1,12,5,seq,0,0,0,0,0xf6,0xcc,0x4f,9]}
    static func command(seq:UInt8, code:Int, id:UInt16, body:[UInt8])->[UInt8] {
        let raw:[UInt8]=[UInt8(code&255),UInt8((code>>8)&255),2,UInt8(id&255),UInt8(id>>8),0,0x80,0,0]+body
        let packet=magic+[1,12,4,seq]+le(UInt32(raw.count))+raw
        return packet+le(crc(packet))
    }
    static func photography(_ field:Int,_ value:Int)->[UInt8] { int(1,field)+bytes(2,int(field,value))+int(3,7) }
    static func supportsFirmware(_ version:String?)->Bool {
        guard let version=version else{return false}
        let parts=version.split(whereSeparator:{!$0.isNumber}).prefix(3).compactMap{Int($0)}
        guard parts.count==3 else{return false}
        return parts[0]>1 || (parts[0]==1 && (parts[1]>0 || parts[2]>=288))
    }
    static func gimbal(x:Int,y:Int)->[UInt8] {
        let x=max(-99,min(99,x)),y=max(-99,min(99,y))
        let axes=(y == 0 ? []:int(1,zig(y)))+(x == 0 ? []:int(2,zig(x)))
        return int(1,1)+bytes(2,axes)+int(4,4)
    }
    static func fields(_ data:[UInt8])->[Int: [[UInt8]]] {
        var at=0;var result=[Int:[[UInt8]]]()
        func read()->UInt64? {var v:UInt64=0;var shift=0
            while at<data.count && shift<64 {let b=data[at];at+=1;v |= UInt64(b&127)<<shift;if b&128 == 0 {return v};shift+=7};return nil}
        while at<data.count {
            guard let tag=read(),tag>0 else {break};let field=Int(tag>>3);let type=tag&7
            switch type {
            case 0: guard let n=read() else{return result};result[field,default:[]].append(varint(n))
            case 1,5: let n=type==1 ? 8:4;guard at+n<=data.count else{return result};result[field,default:[]].append(Array(data[at..<at+n]));at+=n
            case 2: guard let n=read(), n<=UInt64(data.count-at) else{return result};result[field,default:[]].append(Array(data[at..<at+Int(n)]));at+=Int(n)
            default:return result
            }
        };return result
    }
    static func value(_ data:[UInt8],_ field:Int)->Int? {
        guard let b=fields(data)[field]?.first else{return nil};var v:UInt64=0
        for (i,x) in b.prefix(9).enumerated() {v |= UInt64(x&127)<<(7*i);if x&128==0 {break}}
        return Int(exactly:v)
    }
}
struct Packet {let type:Int;let subtype:Int;let code:Int;let id:UInt16;let body:[UInt8]}
struct Scanner {
    var pending=[UInt8]()
    mutating func append(_ bytes:[UInt8])->[Packet] {
        pending += bytes;var output=[Packet]()
        while pending.count>=12 {
            if Array(pending.prefix(4)) != Wire.magic {pending.removeFirst();continue}
            let n=Int(Wire.u32(pending,8));if n>8*1024*1024 {pending.removeFirst(4);continue}
            guard pending.count>=n+16 else{break}
            let p=Array(pending[12..<12+n]);let type=Int(pending[6])
            let expected=Wire.u32(pending,12+n)
            // Check every complete frame before feeding a native decoder.
            if Wire.crc(Array(pending.prefix(12+n)))==expected {
                if type==1 && n>9 { output.append(Packet(type:type,subtype:Int(p[0]),code:0,id:0,body:Array(p.dropFirst(9)))) }
                if type==4 && n>=9 {output.append(Packet(type:type,subtype:0,code:Int(p[0])|Int(p[1])<<8,id:UInt16(p[3])|UInt16(p[4])<<8,body:Array(p.dropFirst(9))))}
            }
            pending.removeFirst(n+16)
        };return output
    }
}
struct Cube {
    let name:String;let size:Int;let rgb:[Float]
    static func parse(_ data:Data,name:String)throws->Cube {
        guard let text=String(data:data,encoding:.utf8) else{throw MonitorError.invalidLUT}
        var n=0;var rgb=[Float]()
        for source in text.replacingOccurrences(of:"\u{FEFF}",with:"").components(separatedBy:.newlines) {
            let line=String(source.split(separator:"#",omittingEmptySubsequences:false)[0]).trimmingCharacters(in:.whitespaces)
            let t=line.split(whereSeparator:{$0.isWhitespace}).map(String.init);if t.isEmpty{continue}
            if ["TITLE","DOMAIN_MIN","DOMAIN_MAX"].contains(t[0].uppercased()){continue}
            if t[0].uppercased()=="LUT_3D_SIZE" {guard t.count==2,let size=Int(t[1]),(2...65).contains(size), n==0 || n==size else{throw MonitorError.invalidLUT};n=size;continue}
            guard n>0,t.count==3,rgb.count < n*n*n*3 else{throw MonitorError.invalidLUT}
            for s in t {guard let f=Float(s),f.isFinite else{throw MonitorError.invalidLUT};rgb.append(max(0,min(1,f)))}
        }
        guard n>0,rgb.count==n*n*n*3 else{throw MonitorError.invalidLUT}
        return Cube(name:name,size:n,rgb:rgb)
    }
}
enum MonitorError:Error {case disconnected,timeout,invalidLUT,rejected,decoder(Int32)}
