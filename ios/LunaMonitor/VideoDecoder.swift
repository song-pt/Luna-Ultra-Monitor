import Foundation
import VideoToolbox
import CoreMedia

final class VideoDecoder {
    private let queue=DispatchQueue(label:"luna.decode",qos:.userInteractive)
    private var session:VTDecompressionSession?
    private var format:CMVideoFormatDescription?
    private var parameterSets=[Int:[UInt8]]()
    private var hevc=true
    private var generation=0
    private var needsKey=true
    private let inputSlots=DispatchSemaphore(value:3)
    private let dropLock=NSLock()
    private var droppedInput=false
    var onFrame:((CVPixelBuffer,Int,Double)->Void)?
    var onError:((OSStatus)->Void)?
    private final class Ticket {let generation:Int;let time:Double;init(_ g:Int){generation=g;time=ProcessInfo.processInfo.systemUptime}}
    func reset() {queue.async {self.generation+=1;self.clear()}}
    private func clear() {if let s=session {VTDecompressionSessionInvalidate(s)};session=nil;format=nil;parameterSets=[:];needsKey=true}
    func decode(_ data:[UInt8]) {
        guard inputSlots.wait(timeout:.now()) == .success else {
            dropLock.lock();droppedInput=true;dropLock.unlock();return
        }
        queue.async {
            defer {self.inputSlots.signal()}
            self.dropLock.lock();let dropped=self.droppedInput;self.droppedInput=false;self.dropLock.unlock()
            if dropped {self.needsKey=true}
            self.consume(data)
        }
    }
    private func consume(_ data:[UInt8]) {
        var starts=[(Int,Int)]();var i=0
        while i+3<data.count {
            if data[i]==0 && data[i+1]==0 {
                if data[i+2]==1{starts.append((i,3));i+=3;continue}
                if data[i+2]==0 && data[i+3]==1{starts.append((i,4));i+=4;continue}
            };i+=1
        }
        guard !starts.isEmpty else{return}
        var nals=[[UInt8]]();var key=false;var configChanged=false
        for (j,s) in starts.enumerated() {
            let end=j+1<starts.count ? starts[j+1].0:data.count
            let nal=Array(data[(s.0+s.1)..<end]);guard nal.count>=2 else{continue}
            let h=(Int(nal[0])>>1)&63;let a=Int(nal[0])&31
            if (32...34).contains(h) && nal[0]&1==0 {hevc=true}
            else if a==7 || a==8 {hevc=false}
            let t=hevc ? h:a
            if (hevc && (32...34).contains(t)) || (!hevc && (t==7 || t==8)) {
                if parameterSets[t] != nal{configChanged=true;parameterSets[t]=nal}
            } else {nals.append(nal)}
            if (hevc && (16...23).contains(t)) || (!hevc && t==5){key=true}
        }
        if configChanged,let s=session {VTDecompressionSessionInvalidate(s);session=nil;format=nil;needsKey=true}
        if session==nil {
            guard key else{return}
            let types=hevc ? [32,33,34]:[7,8];guard types.allSatisfy({parameterSets[$0] != nil})else{return}
            let datas=types.map{Data(parameterSets[$0]!)}
            let ns=datas.map{$0 as NSData};let pointers=ns.map{$0.bytes.assumingMemoryBound(to:UInt8.self)};let sizes=ns.map{$0.length}
            var f:CMFormatDescription?
            let status=pointers.withUnsafeBufferPointer { ptr in sizes.withUnsafeBufferPointer {sz in
                if hevc{return CMVideoFormatDescriptionCreateFromHEVCParameterSets(allocator:kCFAllocatorDefault,parameterSetCount:3,parameterSetPointers:ptr.baseAddress!,parameterSetSizes:sz.baseAddress!,nalUnitHeaderLength:4,extensions:nil,formatDescriptionOut:&f)}
                return CMVideoFormatDescriptionCreateFromH264ParameterSets(allocator:kCFAllocatorDefault,parameterSetCount:2,parameterSetPointers:ptr.baseAddress!,parameterSetSizes:sz.baseAddress!,nalUnitHeaderLength:4,formatDescriptionOut:&f)
            }}
            guard status==0,let f=f else{onError?(status);return};format=f
            var callback=VTDecompressionOutputCallbackRecord(decompressionOutputCallback:{ context,ticket,status,_,image,_,_ in
                guard let context=context,let ticket=ticket else{return}
                let selfRef=Unmanaged<VideoDecoder>.fromOpaque(context).takeUnretainedValue()
                let t=Unmanaged<Ticket>.fromOpaque(ticket).takeRetainedValue()
                selfRef.queue.async {
                    guard t.generation == selfRef.generation else { return }
                    if status==0,let image=image {selfRef.onFrame?(image,t.generation,t.time)}else{selfRef.onError?(status)}
                }
            },decompressionOutputRefCon:Unmanaged.passUnretained(self).toOpaque())
            let attrs:[String:Any]=[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,kCVPixelBufferMetalCompatibilityKey as String:true]
            let create=VTDecompressionSessionCreate(allocator:kCFAllocatorDefault,formatDescription:f,decoderSpecification:nil,imageBufferAttributes:attrs as CFDictionary,outputCallback:&callback,decompressionSessionOut:&session)
            guard create==0,let session=session else{onError?(create);return}
            VTSessionSetProperty(session,key:kVTDecompressionPropertyKey_RealTime,value:kCFBooleanTrue)
            needsKey=false
        }
        guard let session=session,let format=format,!needsKey || key else{return}
        if key { needsKey=false }
        var bytes=[UInt8]()
        for nal in nals {let n=UInt32(nal.count);bytes += [UInt8(truncatingIfNeeded:n>>24),UInt8(truncatingIfNeeded:n>>16),UInt8(truncatingIfNeeded:n>>8),UInt8(truncatingIfNeeded:n)]+nal}
        guard !bytes.isEmpty else{return}
        var block:CMBlockBuffer?
        guard CMBlockBufferCreateWithMemoryBlock(allocator:kCFAllocatorDefault,memoryBlock:nil,blockLength:bytes.count,blockAllocator:kCFAllocatorDefault,customBlockSource:nil,offsetToData:0,dataLength:bytes.count,flags:0,blockBufferOut:&block)==0,let block=block else{return}
        _=bytes.withUnsafeBytes {CMBlockBufferReplaceDataBytes(with:$0.baseAddress!,blockBuffer:block,offsetIntoDestination:0,dataLength:bytes.count)}
        var sample:CMSampleBuffer?;var size=bytes.count
        guard CMSampleBufferCreateReady(allocator:kCFAllocatorDefault,dataBuffer:block,formatDescription:format,sampleCount:1,sampleTimingEntryCount:0,sampleTimingArray:nil,sampleSizeEntryCount:1,sampleSizeArray:&size,sampleBufferOut:&sample)==0,let sample=sample else{return}
        let t=Unmanaged.passRetained(Ticket(generation)).toOpaque()
        let status=VTDecompressionSessionDecodeFrame(session,sampleBuffer:sample,flags:[._EnableAsynchronousDecompression],frameRefcon:t,infoFlagsOut:nil)
        if status != 0 {Unmanaged<Ticket>.fromOpaque(t).release();onError?(status)}
    }
}
