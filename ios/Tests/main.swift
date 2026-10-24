import Foundation
import Metal

func expect(_ ok:Bool,_ name:String){if !ok{fputs("FAIL \(name)\n",stderr);exit(1)};print("PASS \(name)")}
expect(Wire.gimbal(x:0,y:0)==[8,1,18,0,32,4],"release is MOVE + empty virtual_rc, not RECENTER")
expect(Wire.gimbal(x:-99,y:99)==[8,1,18,6,8,198,1,16,197,1,32,4],"signed joystick ZigZag")
expect(Wire.photography(35,5)==[8,35,18,3,152,2,5,24,7],"Dolby Vision field 35")
expect(!Wire.supportsFirmware(nil),"unknown firmware gated")
expect(!Wire.supportsFirmware("1.0.238"),"old firmware gated")
expect(Wire.supportsFirmware("v1.1.15"),"documented firmware enabled")
let captured=Wire.command(seq:0x10,code:8,id:1,body:[8,48,8,15,8,11])
expect(captured.map{String(format:"%02x",$0)}.joined()=="55434432010c04100f0000000800020100008000000830080f080b7c008e7c","Kotlin captured packet parity")
let p=Wire.command(seq:0x24,code:226,id:21,body:Wire.gimbal(x:0,y:0))
expect(Array(p.prefix(4))==Wire.magic,"UCD2 magic")
expect(Int(Wire.u32(p,8))==15,"packet payload length")
expect(Wire.u32(p,p.count-4)==Wire.crc(Array(p.dropLast(4))),"checksum roundtrip")
var scanner=Scanner();expect(scanner.append(Array(p.prefix(13))).isEmpty,"fragment buffered")
expect(scanner.append(Array(p.dropFirst(13))).first?.id==21,"fragment reassembled")
var invalid=p;invalid[invalid.count-1] ^= 1;expect(scanner.append(invalid).isEmpty,"corrupt packet rejected")
let cube="\u{FEFF}LUT_3D_SIZE\t2\n"+Array(repeating:"0 0.5 1",count:8).joined(separator:"\n")
expect((try? Cube.parse(Data(cube.utf8),name:"test"))?.size==2,"BOM cube import")
expect((try? Cube.parse(Data("LUT_3D_SIZE 2\n0 0 0".utf8),name:"bad"))==nil,"incomplete cube rejected")
if CommandLine.arguments.count>1 {
    guard let device=MTLCreateSystemDefaultDevice()else{fputs("Metal device unavailable\n",stderr);exit(2)}
    do {let source=try String(contentsOfFile:CommandLine.arguments[1],encoding:.utf8);let lib=try device.makeLibrary(source:source,options:nil)
        expect(lib.makeFunction(name:"monitorFragment") != nil,"Metal fragment compiled")
        expect(lib.makeFunction(name:"monitorVertex") != nil,"Metal vertex compiled")
        let d=MTLRenderPipelineDescriptor();d.vertexFunction=lib.makeFunction(name:"monitorVertex");d.fragmentFunction=lib.makeFunction(name:"monitorFragment");d.colorAttachments[0].pixelFormat = .bgra8Unorm
        _=try device.makeRenderPipelineState(descriptor:d)
        print("PASS Metal render pipeline created")
        guard let queue=device.makeCommandQueue() else{exit(1)}
        let textureDesc=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.r8Unorm,width:8,height:8,mipmapped:false)
        textureDesc.storageMode = .shared
        let y=device.makeTexture(descriptor:textureDesc)!
        var luma=[UInt8](repeating:16,count:64)
        y.replace(region:MTLRegionMake2D(0,0,8,8),mipmapLevel:0,withBytes:&luma,bytesPerRow:8)
        let uvDesc=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rg8Unorm,width:4,height:4,mipmapped:false);uvDesc.storageMode = .shared
        let uv=device.makeTexture(descriptor:uvDesc)!
        var chroma=[UInt8](repeating:128,count:32)
        uv.replace(region:MTLRegionMake2D(0,0,4,4),mipmapLevel:0,withBytes:&chroma,bytesPerRow:8)
        func cube(_ rgb:[Float16])->MTLTexture {
            let d=MTLTextureDescriptor();d.textureType = .type3D;d.width=2;d.height=2;d.depth=2;d.pixelFormat = .rgba16Float;d.storageMode = .shared
            let t=device.makeTexture(descriptor:d)!;var data=[Float16]()
            for _ in 0..<8{data += rgb+[1]}
            t.replace(region:MTLRegionMake3D(0,0,0,2,2,2),mipmapLevel:0,slice:0,withBytes:&data,bytesPerRow:16,bytesPerImage:32);return t
        }
        let red=cube([1,0,0]),green=cube([0,1,0])
        let outDesc=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.bgra8Unorm,width:8,height:8,mipmapped:false);outDesc.storageMode = .shared;outDesc.usage=[.renderTarget,.shaderRead]
        let out=device.makeTexture(descriptor:outDesc)!
        let pipeline=try device.makeRenderPipelineState(descriptor:d)
        func render(_ recovery:Bool,_ style:Bool)->[UInt8] {
            let p=MTLRenderPassDescriptor();p.colorAttachments[0].texture=out;p.colorAttachments[0].loadAction = .clear;p.colorAttachments[0].storeAction = .store
            let command=queue.makeCommandBuffer()!;let e=command.makeRenderCommandEncoder(descriptor:p)!
            e.setRenderPipelineState(pipeline);e.setFragmentTexture(y,index:0);e.setFragmentTexture(uv,index:1);e.setFragmentTexture(red,index:2);e.setFragmentTexture(green,index:3)
            var u=[Float](repeating:0,count:28);u[23]=recovery ? 1:0;u[24]=1;u[25]=1;u[27]=style ? 1:0
            e.setFragmentBytes(&u,length:u.count*4,index:0);e.drawPrimitives(type:.triangleStrip,vertexStart:0,vertexCount:4);e.endEncoding();command.commit();command.waitUntilCompleted()
            expect(command.status == .completed,"GPU command completed")
            var pixels=[UInt8](repeating:0,count:256);out.getBytes(&pixels,bytesPerRow:32,from:MTLRegionMake2D(0,0,8,8),mipmapLevel:0);return pixels
        }
        let black=render(false,false)
        expect(black[0]<4 && black[1]<4 && black[2]<4,"limited-range black remains black")
        let r=render(true,false);expect(r[2]>250 && r[1]<4,"single recovery LUT sampled")
        let g=render(true,true);expect(g[1]>250 && g[2]<4,"style LUT follows recovery LUT")
        expect(Array(black.prefix(32)) == Array(black.suffix(32)),"uniform input has no synthetic bottom band")

    }catch{fputs("Metal: \(error)\n",stderr);exit(1)}
}
print("IOS_PROTOCOL_TESTS_OK")
