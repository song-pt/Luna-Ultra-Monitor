import SwiftUI
import Combine
import UniformTypeIdentifiers

struct VideoFormat:Decodable,Identifiable {let label:String;let code:Int;var id:Int{code}}
enum L {
    static let strings:[String:[String]] = {
        guard let url=Bundle.main.url(forResource:"Strings",withExtension:"json"),let data=try? Data(contentsOf:url),let value=try? JSONDecoder().decode([String:[String]].self,from:data)else{return [:]};return value
    }()
    static func text(_ key:String,_ language:Int)->String {guard let values=strings[key],values.count==3 else{return key};return values[max(0,min(2,language))]}
}
@MainActor final class MonitorModel:ObservableObject {
    @Published var language=UserDefaults.standard.integer(forKey:"language")
    @Published var section:String?=nil
    @Published var cameraOpen=false
    @Published var gimbalOpen=false
    @Published var cameraRight=true
    @Published var clean=false
    @Published var values=[Double](repeating:0,count:20)
    @Published var scale=0
    @Published var thirds=false;@Published var center=false;@Published var safe=false
    @Published var guideWidth=2.0;@Published var guideOpacity=0.72;@Published var centerSize=32.0
    @Published var zebra=false;@Published var falseColor=false;@Published var peaking=false
    @Published var recoveryName="";@Published var styleName="";@Published var lutError=""
    @Published var thermalPrompt=false
    var thermalObserver:NSObjectProtocol?
    let renderer:MetalMonitor?
    let renderError:String?
    let formats:[VideoFormat]
    init(){
        do {renderer=try MetalMonitor(validating:true);renderError=nil}catch{renderer=nil;renderError=String(describing:error)}
        if let u=Bundle.main.url(forResource:"Formats",withExtension:"json"),let d=try? Data(contentsOf:u),let f=try? JSONDecoder().decode([VideoFormat].self,from:d){formats=f}else{formats=[]}
        thermalObserver=NotificationCenter.default.addObserver(forName:ProcessInfo.thermalStateDidChangeNotification,object:nil,queue:.main){[weak self] _ in
            if ProcessInfo.processInfo.thermalState == .serious || ProcessInfo.processInfo.thermalState == .critical {Task {@MainActor in self?.thermalPrompt=true}}
        }
    }
    func apply(){renderer?.values=values.map(Float.init);renderer?.scale=scale;renderer?.zebra=zebra;renderer?.falseColor=falseColor;renderer?.peaking=peaking;renderer?.redraw()}
    func importLUT(_ url:URL,recovery:Bool){
        let access=url.startAccessingSecurityScopedResource();defer{if access{url.stopAccessingSecurityScopedResource()}}
        do {
            let size=(try url.resourceValues(forKeys:[.fileSizeKey])).fileSize ?? 0
            guard size<64*1024*1024 else{throw MonitorError.invalidLUT}
            let cube=try Cube.parse(Data(contentsOf:url),name:url.deletingPathExtension().lastPathComponent)
            renderer?.lut(cube,recoverySlot:recovery)
            if recovery{recoveryName=cube.name}else{styleName=cube.name};lutError=""
        }catch{lutError=L.text("failed",language)+": .cube 3D / 2–65"}
    }
    func t(_ key:String)->String{L.text(key,language)}
}
@main struct LunaMonitorApplication:App {
    @StateObject private var model=MonitorModel()
    @StateObject private var camera=CameraSession()
    var body:some Scene {WindowGroup {MonitorRoot(model:model,camera:camera).preferredColorScheme(.dark).tint(.white)}}
}
struct BoundsKey:PreferenceKey {
    static var defaultValue:CGRect = .zero
    static func reduce(value:inout CGRect,nextValue:()->CGRect){value=nextValue()}
}
struct HintScroll<Content:View>:View {
    let content:Content
    @State private var rect=CGRect.zero
    @State private var space=UUID().uuidString
    init(@ViewBuilder content:()->Content){self.content=content()}
    var body:some View {
        GeometryReader {geo in
            ScrollView {content.frame(maxWidth:.infinity).padding(.vertical,12).background(GeometryReader {g in Color.clear.preference(key:BoundsKey.self,value:g.frame(in:.named(space)))})}
                .coordinateSpace(name:space).onPreferenceChange(BoundsKey.self){rect=$0}
                .overlay(alignment:.bottom){if rect.maxY>geo.size.height+2 {indicator("chevron.down",bottom:true)}}
                .overlay(alignment:.top){if rect.minY < -2 {indicator("chevron.up",bottom:false)}}
        }
    }
    private func indicator(_ icon:String,bottom:Bool)->some View {
        Image(systemName:icon).font(.system(size:11,weight:.semibold)).frame(maxWidth:.infinity).frame(height:24)
            .background(LinearGradient(colors:bottom ? [.clear,.black.opacity(0.85)]:[.black.opacity(0.85),.clear],startPoint:.top,endPoint:.bottom)).allowsHitTesting(false)
    }
}
struct MonitorRoot:View {
    @ObservedObject var model:MonitorModel
    @ObservedObject var camera:CameraSession
    @Environment(\.scenePhase) private var phase
    @State private var selectedSize="1080P"
    @State private var picker=false
    @State private var recoverySlot=true
    private let tools=[("color","paintpalette"),("lut","square.stack.3d.up"),("exposure","sun.max"),("focus","mountain.2"),("composition","crop"),("display","display"),("parameters","slider.horizontal.3")]
    var body:some View {
        GeometryReader {geo in
            ZStack {
                Color.black.ignoresSafeArea()
                if let renderer=model.renderer {MonitorCanvas(renderer:renderer)}
                if camera.hasFrame {guides(size:geo.size).allowsHitTesting(false)}
                if !camera.connected {
                    VStack(spacing:12){
                        Text("LUNA MONITOR 1.1.0").font(.headline)
                        Text(model.t("sideloadNote")).font(.caption).multilineTextAlignment(.center)
                        if let error=model.renderError{Text(error).font(.caption).foregroundStyle(.red)}
                        Button(model.t(camera.connecting ? "connecting":"connect")){camera.connect()}.disabled(camera.connecting || model.renderer==nil)
                        HStack{ForEach(Array(["中文","English","日本語"].enumerated()),id:\.offset){i,title in Button(title){model.language=i;camera.language=i;UserDefaults.standard.set(i,forKey:"language")}}}
                        Text(camera.message).font(.caption).lineLimit(3)
                    }.padding(24).background(.black.opacity(0.75),in:RoundedRectangle(cornerRadius:18)).frame(maxWidth:420)
                }
                if model.clean {
                    VStack{Button(model.t("cleanExit")){model.clean=false}.buttonStyle(.bordered).padding(.top,8);Spacer()}
                }else {
                    chrome(geo:geo)
                }
                if model.thermalPrompt {
                    VStack{HStack{
                        Text(model.t("thermal")).font(.caption)
                        Button(model.t("agree")){model.zebra=false;model.falseColor=false;model.peaking=false;model.apply();model.thermalPrompt=false}
                        Button(model.t("keep")){model.thermalPrompt=false}
                    }.padding(10).background(.ultraThinMaterial,in:Capsule()).padding(.top,38);Spacer()}
                }
            }
        }
        .onAppear{camera.language=model.language;camera.onPixel={image in model.renderer?.frame(image)};UIApplication.shared.isIdleTimerDisabled=true}
        .onChange(of:phase){phase in if phase != .active {camera.stopStick()};if phase == .background {camera.disconnect()} }
        .onChange(of:model.values){_ in model.apply()}
        .onChange(of:model.scale){_ in model.apply()}
        .onChange(of:model.zebra){_ in model.apply()}
        .onChange(of:model.falseColor){_ in model.apply()}
        .onChange(of:model.peaking){_ in model.apply()}
        .fileImporter(isPresented:$picker,allowedContentTypes:[UTType(filenameExtension:"cube") ?? .data,.plainText],allowsMultipleSelection:false){result in
            if case .success(let urls)=result,let url=urls.first{model.importLUT(url,recovery:recoverySlot)}
        }
    }
    @ViewBuilder private func chrome(geo:GeometryProxy)->some View {
        VStack(spacing:0){
            HStack{
                Text("LUNA MONITOR").font(.caption.bold())
                Circle().fill(camera.hasFrame ? Color.green:Color.gray).frame(width:6,height:6)
                Text(camera.hasFrame ? String(format:"%.1f fps",camera.fps):camera.message).font(.caption).lineLimit(1)
                Spacer()
                if camera.connected {Text(String(format:model.t("decodeMS"),camera.decodeMS)).font(.caption2)}
            }.padding(9).background(.black.opacity(0.55))
            Spacer()
            HStack(spacing:3){ForEach(tools,id:\.0){key,icon in
                Button {
                    camera.stopStick();model.cameraOpen=false;model.gimbalOpen=false;model.section=model.section==key ? nil:key
                }label:{VStack(spacing:3){Image(systemName:icon).font(.system(size:19));Text(model.t(key)).font(.system(size:9)).lineLimit(1)}.frame(maxWidth:.infinity).padding(.vertical,7).background(model.section==key ? Color.white.opacity(0.14):.clear,in:RoundedRectangle(cornerRadius:10))}
            }}.padding(5).background(.black.opacity(0.85),in:RoundedRectangle(cornerRadius:14)).padding(.bottom,6)
        }
        if let section=model.section {
            HStack{Spacer();VStack(alignment:.leading,spacing:4){
                Text(model.t(section)).font(.headline);Divider()
                HintScroll{panel(section)}
            }.padding(10).frame(width:min(310,max(1,geo.size.width-16)),height:max(40,geo.size.height-112)).background(.black.opacity(0.92),in:RoundedRectangle(cornerRadius:18))}.padding(.horizontal,8)
        }else if camera.connected {
            HStack{
                if model.cameraRight {gimbal(height:max(40,geo.size.height-112))}else{cameraControl(height:max(40,geo.size.height-112))}
                Spacer(minLength:4)
                if model.cameraRight {cameraControl(height:max(40,geo.size.height-112))}else{gimbal(height:max(40,geo.size.height-112))}
            }.padding(.horizontal,8).padding(.top,40).padding(.bottom,72)
        }
    }
    private func cameraControl(height:CGFloat)->some View {
        VStack(alignment:model.cameraRight ? .trailing:.leading,spacing:5){
            Button {model.cameraOpen.toggle()}label:{Image(systemName:model.cameraOpen ? "chevron.compact.down":"camera").padding(9).background(.black.opacity(0.7),in:RoundedRectangle(cornerRadius:10))}
                .simultaneousGesture(DragGesture(minimumDistance:25).onEnded{v in if !model.cameraOpen{camera.stopStick();model.cameraRight=v.translation.width>0}})
            if model.cameraOpen {
                HintScroll { HStack(spacing:6){
                    if model.cameraRight{zoomRail}
                    VStack(spacing:9){
                        Button(model.t(camera.mode==1 ? "switchVideo":"switchPhoto")){camera.switchMode()}.font(.caption2).disabled(camera.busy || camera.recording || camera.mode==0)
                        Button{camera.capture()}label:{
                            ZStack{
                                Circle().stroke(.white,lineWidth:2).frame(width:66,height:66)
                                if camera.recording {RoundedRectangle(cornerRadius:5).fill(.red).frame(width:26,height:26)}
                                else{Circle().fill(camera.mode==2 ? .red:.white).frame(width:52,height:52)}
                            }
                        }.disabled(camera.busy).accessibilityLabel(model.t(camera.mode==1 ? "takePhoto":camera.recording ? "stopRecord":"startRecord"))
                        Text(model.t(camera.mode==1 ? "photoMode":camera.mode==2 ? "videoMode":"detectingMode")).font(.caption2)
                        Button(model.t("disconnect")){camera.disconnect()}.font(.caption2)
                    }.frame(width:76)
                    if !model.cameraRight{zoomRail}
                } }.frame(width:154,height:max(1,min(205,height-40)))
            }
        }
    }
    private var zoomRail:some View {
        HStack(spacing:4){
            GeometryReader{g in
                ZStack{Capsule().fill(.gray).frame(width:3);Circle().fill(.white).frame(width:13,height:13).position(x:g.size.width/2,y:8+(g.size.height-16)*(1-(camera.zoom-1)/11))}
                    .contentShape(Rectangle()).gesture(DragGesture(minimumDistance:0).onChanged{v in camera.zoom=1+(1-max(0,min(1,v.location.y/g.size.height)))*11}.onEnded{_ in camera.commitZoom()})
            }.frame(width:24)
            VStack{ForEach([12.0,6,3,2,1],id:\.self){z in Button(String(format:"%.0f×",z)){camera.zoom=z;camera.commitZoom()}.font(.caption2).padding(5).background(abs(camera.zoom-z)<0.1 ? Color.white.opacity(0.25):Color.black.opacity(0.5),in:Capsule());if z != 1{Spacer(minLength:2)}}}
        }.frame(width:65,height:155)
    }
    private func gimbal(height:CGFloat)->some View {
        VStack(spacing:4){
            Button(model.t(model.gimbalOpen ? "collapse":"gimbal")){camera.stopStick();model.gimbalOpen.toggle()}.font(.caption).buttonStyle(.bordered)
            if model.gimbalOpen {
                HintScroll { VStack(spacing:6){
                    Stick{camera.moveStick(x:$0,y:$1)} stop:{camera.stopStick()}
                    Text("↑ ↓ ← →").font(.caption)
                    if !Wire.supportsFirmware(camera.firmware) {Text(model.t("gimbalFirmware")).font(.caption2)}
                    HStack{
                        Button(model.t("recenter")){camera.gimbalAction(2)}
                        Button(model.t("flip")){camera.gimbalAction(3)}
                    }.font(.caption).disabled(camera.busy || !Wire.supportsFirmware(camera.firmware))
                }.padding(8) }.frame(width:156,height:max(1,min(208,height-40))).background(.black.opacity(0.72),in:RoundedRectangle(cornerRadius:22))
            }
        }.onDisappear{camera.stopStick()}
    }
    @ViewBuilder private func panel(_ section:String)->some View {
        switch section {
        case "color":
            VStack(spacing:8){ForEach(0..<20,id:\.self){i in adjustment(i)}}
        case "lut":
            VStack(alignment:.leading,spacing:12){
                lutSlot(true);lutSlot(false);Text(model.t("lutOrder")).font(.caption);Text(model.lutError).font(.caption).foregroundStyle(.orange)
            }
        case "exposure":VStack{Toggle(model.t("zebra"),isOn:$model.zebra);Toggle(model.t("falseColor"),isOn:$model.falseColor)}
        case "focus":Toggle(model.t("peaking"),isOn:$model.peaking)
        case "composition":
            VStack{Toggle(model.t("thirds"),isOn:$model.thirds);Toggle(model.t("centerMark"),isOn:$model.center);Toggle(model.t("safeArea"),isOn:$model.safe)
                Text(model.t("lineWidth"));Slider(value:$model.guideWidth,in:1...6,step:0.5)
                Text(model.t("opacity"));Slider(value:$model.guideOpacity,in:0.2...1,step:0.05)
                Text(model.t("centerSize"));Slider(value:$model.centerSize,in:12...96,step:4)
            }
        case "display":VStack(alignment:.leading,spacing:12){ForEach(Array(["fit","cropFill","stretch","pixel"].enumerated()),id:\.offset){i,k in Button {model.scale=i}label:{HStack{Text(model.t(k));Spacer();if model.scale==i{Image(systemName:"checkmark")}}}}
            Button(model.t("clean")){camera.stopStick();model.clean=true;model.section=nil}
        }
        case "parameters":formatPanel
        default:EmptyView()
        }
    }
    private func adjustment(_ i:Int)->some View {
        let keys=["adjustExposure","brightness","contrast","highlights","shadows","temperature","tint","saturation","vibrance","whites","blacks","sharpness","midtones","gamma","hue","fade","clarity","dehaze","softHighlights","vignette"]
        let r:ClosedRange<Double> = i==0 ? -5...5 : i==14 ? -180...180 : (i==11 || i==15 || i==16 || i==17 || i==18) ? 0...100 : -100...100
        return VStack(spacing:2){HStack{Text(model.t(keys[i]));Spacer();Text(String(format:"%.1f",model.values[i])).monospacedDigit()}.font(.caption)
            HStack{Button("−"){model.values[i]=max(r.lowerBound,model.values[i]-(i==0 ? 0.1:1))};Slider(value:$model.values[i],in:r);Button("+"){model.values[i]=min(r.upperBound,model.values[i]+(i==0 ? 0.1:1))}}
        }
    }
    private func lutSlot(_ recovery:Bool)->some View {
        VStack(alignment:.leading){Text(model.t(recovery ? "recoveryLut":"styleLut"));Text(recovery ? model.recoveryName:model.styleName).font(.caption)
            HStack{Button(model.t("selectCube")){recoverySlot=recovery;picker=true};Button(model.t("clear")){model.renderer?.lut(nil,recoverySlot:recovery);if recovery{model.recoveryName=""}else{model.styleName=""}}}.font(.caption)
        }
    }
    private var formatPanel:some View {
        VStack(alignment:.leading,spacing:8){
            HStack{ForEach([1,5,2],id:\.self){c in Button(model.t(c==1 ? "cameraColorStandard":c==5 ? "cameraColorDolby":"cameraColorILog")){camera.setPhotoOption(35,value:c)}.font(.caption2).padding(5).background(camera.color==c ? Color.white.opacity(0.2):.clear,in:RoundedRectangle(cornerRadius:8))}}
                .disabled(camera.mode != 2 || camera.recording || camera.busy)
            Text(model.t("cameraColorVideoOnly")).font(.caption2)
            HStack(alignment:.top,spacing:10){
                VStack(alignment:.leading,spacing:12){Text(model.t("resolution")).font(.caption)
                    ForEach(["1080P","2.7K","4K","8K"],id:\.self){size in Button(size){selectedSize=size}.font(.caption).padding(6).background(size==selectedSize ? Color.white.opacity(0.2):.clear,in:RoundedRectangle(cornerRadius:8))}
                }.frame(maxWidth:.infinity)
                VStack(spacing:10){Text(model.t("frameRate")).font(.caption)
                    ForEach(model.formats.filter{$0.label.hasPrefix(selectedSize+" ")}){f in Button(f.label.components(separatedBy:" ").last!+" fps"){camera.setPhotoOption(31,value:f.code)}.font(.caption).padding(6).background(camera.format==f.code ? Color.white.opacity(0.2):.clear,in:RoundedRectangle(cornerRadius:8))}
                }.frame(maxWidth:.infinity).disabled(camera.mode != 2 || camera.recording || camera.busy)
            }
            Text(camera.message).font(.caption2).lineLimit(3)
        }
    }
    private func guides(size:CGSize)->some View {
        Canvas {ctx,_ in
            guard let view=model.renderer?.view else{return}
            let aspect=Double(view.drawableSize.width)/max(1,Double(view.drawableSize.height))
            // Effective decoded aspect is supplied by the renderer rather than assuming 16:9.
            let a=model.renderer?.videoAspect ?? aspect
            var rect=CGRect(origin:.zero,size:size)
            if model.scale==0 {if Double(size.width/size.height)>a{rect.size.width=size.height*a;rect.origin.x=(size.width-rect.width)/2}else{rect.size.height=size.width/a;rect.origin.y=(size.height-rect.height)/2}}
            var path=Path()
            if model.thirds {for f in [1.0/3,2.0/3] {path.move(to:CGPoint(x:rect.minX+rect.width*f,y:rect.minY));path.addLine(to:CGPoint(x:rect.minX+rect.width*f,y:rect.maxY));path.move(to:CGPoint(x:rect.minX,y:rect.minY+rect.height*f));path.addLine(to:CGPoint(x:rect.maxX,y:rect.minY+rect.height*f))}}
            if model.safe {path.addRect(rect.insetBy(dx:rect.width*0.05,dy:rect.height*0.05))}
            if model.center {let r=model.centerSize/2;path.move(to:CGPoint(x:rect.midX-r,y:rect.midY));path.addLine(to:CGPoint(x:rect.midX+r,y:rect.midY));path.move(to:CGPoint(x:rect.midX,y:rect.midY-r));path.addLine(to:CGPoint(x:rect.midX,y:rect.midY+r))}
            ctx.stroke(path,with:.color(.white.opacity(model.guideOpacity)),lineWidth:model.guideWidth)
        }
    }
}
struct Stick:View {
    var move:(Double,Double)->Void
    var stop:()->Void
    @State private var offset=CGSize.zero
    var body:some View {
        ZStack{
            Circle().fill(.white.opacity(0.12));Circle().stroke(.white.opacity(0.4),lineWidth:1)
            Path{p in p.move(to:CGPoint(x:60,y:10));p.addLine(to:CGPoint(x:60,y:110));p.move(to:CGPoint(x:10,y:60));p.addLine(to:CGPoint(x:110,y:60))}.stroke(.white.opacity(0.2),lineWidth:1)
            Circle().fill(.white.opacity(0.9)).frame(width:32,height:32).offset(offset)
        }.frame(width:120,height:120).contentShape(Circle()).gesture(DragGesture(minimumDistance:0).onChanged{v in
            let x=v.location.x-60,y=v.location.y-60,r=max(48,hypot(x,y));offset=CGSize(width:x/r*48,height:y/r*48);move(x/48,y/48)
        }.onEnded{_ in offset = .zero;stop()}).onDisappear{stop()}.accessibilityLabel("Joystick ↑ ↓ ← →")
    }
}
