import Foundation
import MetalKit
import CoreVideo
import SwiftUI

final class MetalMonitor:NSObject,MTKViewDelegate {
    let device:MTLDevice
    private let queue:MTLCommandQueue
    private let pipeline:MTLRenderPipelineState
    private var cache:CVMetalTextureCache?
    private var pixel:CVPixelBuffer?
    weak var view:MTKView?
    var values=[Float](repeating:0,count:20)
    var zebra=false;var falseColor=false;var peaking=false;var scale=0
    private var recovery:MTLTexture?
    private var style:MTLTexture?
    private let fallback:MTLTexture
    private var inFlight=false
    private var pendingDraw=false
    var videoAspect:Double=16.0/9.0
    init(validating:Bool)throws {
        guard let d=MTLCreateSystemDefaultDevice(),let q=d.makeCommandQueue() else{throw MonitorError.rejected}
        device=d;queue=q
        let url=Bundle.main.url(forResource:"Monitor",withExtension:"metal")!
        let lib=try d.makeLibrary(source:String(contentsOf:url),options:nil)
        let desc=MTLRenderPipelineDescriptor();desc.vertexFunction=lib.makeFunction(name:"monitorVertex");desc.fragmentFunction=lib.makeFunction(name:"monitorFragment");desc.colorAttachments[0].pixelFormat = .bgra8Unorm
        pipeline=try d.makeRenderPipelineState(descriptor:desc)
        let texture=MTLTextureDescriptor();texture.textureType = .type3D;texture.width=2;texture.height=2;texture.depth=2;texture.pixelFormat = .rgba16Float;texture.storageMode = .shared
        fallback=d.makeTexture(descriptor:texture)!
        super.init();CVMetalTextureCacheCreate(kCFAllocatorDefault,nil,d,nil,&cache)
    }
    func frame(_ image:CVPixelBuffer?) {pixel=image;view?.setNeedsDisplay()}
    func redraw(){view?.setNeedsDisplay()}
    func lut(_ cube:Cube?,recoverySlot:Bool) {
        var texture:MTLTexture?
        if let cube=cube {
            let desc=MTLTextureDescriptor();desc.textureType = .type3D;desc.width=cube.size;desc.height=cube.size;desc.depth=cube.size;desc.pixelFormat = .rgba16Float;desc.storageMode = .shared
            if let t=device.makeTexture(descriptor:desc) {
                var rgba=[Float16]();rgba.reserveCapacity(cube.size*cube.size*cube.size*4)
                for i in stride(from:0,to:cube.rgb.count,by:3){rgba += [Float16(cube.rgb[i]),Float16(cube.rgb[i+1]),Float16(cube.rgb[i+2]),1]}
                rgba.withUnsafeBytes { t.replace(region:MTLRegionMake3D(0,0,0,cube.size,cube.size,cube.size),mipmapLevel:0,slice:0,withBytes:$0.baseAddress!,bytesPerRow:cube.size*8,bytesPerImage:cube.size*cube.size*8) }
                texture=t
            }
        }
        if recoverySlot{recovery=texture}else{style=texture};redraw()
    }
    func mtkView(_ view:MTKView,drawableSizeWillChange size:CGSize){}
    func draw(in view:MTKView){
        if inFlight {pendingDraw=true;return}
        guard let pass=view.currentRenderPassDescriptor,let drawable=view.currentDrawable,let cmd=queue.makeCommandBuffer(),let enc=cmd.makeRenderCommandEncoder(descriptor:pass)else{return}
        var retained=[CVMetalTexture]();var retainedPixel:CVPixelBuffer?
        if let pixel=pixel,let cache=cache {
            retainedPixel=pixel
            let w=CVPixelBufferGetWidth(pixel);let h=CVPixelBufferGetHeight(pixel)
            videoAspect=Double(w)/Double(h)
            var yt:CVMetalTexture?;var uvt:CVMetalTexture?
            _=CVMetalTextureCacheCreateTextureFromImage(kCFAllocatorDefault,cache,pixel,nil,.r8Unorm,w,h,0,&yt)
            _=CVMetalTextureCacheCreateTextureFromImage(kCFAllocatorDefault,cache,pixel,nil,.rg8Unorm,w/2,h/2,1,&uvt)
            if let yt=yt,let uvt=uvt,let y=CVMetalTextureGetTexture(yt),let uv=CVMetalTextureGetTexture(uvt) {
                retained=[yt,uvt]
                let vw=Double(view.drawableSize.width),vh=Double(view.drawableSize.height)
                let aspect=Double(w)/Double(h),display=vw/max(1,vh)
                var width=vw,height=vh;var cx:Float=1,cy:Float=1
                if scale==0 {
                    if display>aspect{width=vh*aspect}else{height=vw/aspect}
                }else if scale==1 {
                    if display>aspect{cy=Float(aspect/display)}else{cx=Float(display/aspect)}
                }else if scale==3 {width=Double(w);height=Double(h)}
                enc.setViewport(MTLViewport(originX:(vw-width)/2,originY:(vh-height)/2,width:width,height:height,znear:0,zfar:1))
                enc.setRenderPipelineState(pipeline);enc.setFragmentTexture(y,index:0);enc.setFragmentTexture(uv,index:1);enc.setFragmentTexture(recovery ?? fallback,index:2);enc.setFragmentTexture(style ?? fallback,index:3)
                var uniforms=values+[zebra ? 1:0,falseColor ? 1:0,peaking ? 1:0,recovery == nil ? 0:1]+[cx,cy,CVPixelBufferGetPixelFormatType(pixel)==kCVPixelFormatType_420YpCbCr8BiPlanarFullRange ? 1:0,style == nil ? 0:1]
                enc.setFragmentBytes(&uniforms,length:uniforms.count*MemoryLayout<Float>.size,index:0)
                enc.drawPrimitives(type:.triangleStrip,vertexStart:0,vertexCount:4)
            }
        }
        enc.endEncoding();cmd.present(drawable);inFlight=true
        cmd.addCompletedHandler{[weak self,retained,retainedPixel] _ in
            _=retained;_=retainedPixel
            DispatchQueue.main.async{guard let self=self else{return};self.inFlight=false;if self.pendingDraw{self.pendingDraw=false;self.view?.setNeedsDisplay()}}
        }
        cmd.commit()
    }
}
struct MonitorCanvas:UIViewRepresentable {
    let renderer:MetalMonitor
    func makeUIView(context:Context)->MTKView {
        let view=MTKView(frame:.zero,device:renderer.device);view.colorPixelFormat = .bgra8Unorm
        view.clearColor=MTLClearColor(red:0.015,green:0.017,blue:0.017,alpha:1)
        view.isPaused=true;view.enableSetNeedsDisplay=true;view.delegate=renderer;renderer.view=view;return view
    }
    func updateUIView(_ view:MTKView,context:Context){view.setNeedsDisplay()}
}
