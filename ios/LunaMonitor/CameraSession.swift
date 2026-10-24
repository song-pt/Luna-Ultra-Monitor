import Foundation
import Network
import Combine
import CoreVideo

@MainActor final class CameraSession:ObservableObject {
    @Published var connected=false
    @Published var connecting=false
    @Published var hasFrame=false
    @Published var message=""
    @Published var mode=0 // unknown=0, photo=1, video=2
    @Published var recording=false
    @Published var busy=false
    @Published var zoom:Double=1
    @Published var color:Int?=nil
    @Published var format:Int?=nil
    @Published var firmware:String?=nil
    @Published var fps:Double=0
    @Published var decodeMS:Double=0
    var onPixel:((CVPixelBuffer?)->Void)?
    var language=0
    private var connection:NWConnection?
    private var scanner=Scanner()
    private var seq:UInt8=0x24
    private var requestID:UInt16=20
    private var epoch=0
    private var pending=[UInt16:CheckedContinuation<[UInt8],Error>]()
    private var heartbeat:Task<Void,Never>?
    private var stickTask:Task<Void,Never>?
    private var shutdownTask:Task<Void,Never>?
    private var stick=(0,0)
    private var motionRevision=0
    private let decoder=VideoDecoder()
    private var received=0
    private var begin:Double=0
    private var lastUI:Double=0
    init(){
        decoder.onFrame={ [weak self] pixel,_,time in
            Task { @MainActor in
                guard let self=self,self.connected else{return}
                self.onPixel?(pixel)
                self.received+=1
                let now=ProcessInfo.processInfo.systemUptime
                if now-self.lastUI>0.25 || !self.hasFrame {
                    self.hasFrame=true;self.lastUI=now
                    self.fps=Double(self.received)/max(0.001,now-self.begin)
                    self.decodeMS=(now-time)*1000
                }
            }
        }
        decoder.onError={ [weak self] status in Task {@MainActor in self?.message="Decoder: \(status)"} }
    }
    func connect(){
        guard !connecting && !connected else{return}
        Task {
            await shutdownTask?.value
            self.open()
        }
    }
    private func open(){
        guard !connecting && !connected else{return}
        epoch+=1;let current=epoch;connecting=true;message=L.text("connecting",language)
        seq=0x24;requestID=20;scanner=Scanner();decoder.reset();received=0;begin=ProcessInfo.processInfo.systemUptime
        let tcp=NWProtocolTCP.Options();tcp.noDelay=true
        let params=NWParameters(tls:nil,tcp:tcp);params.prohibitedInterfaceTypes=[.cellular]
        let c=NWConnection(host:"192.168.42.1",port:6666,using:params);connection=c
        c.stateUpdateHandler={ [weak self] state in Task {@MainActor in
            guard let self=self,current==self.epoch else{return}
            switch state {
            case .ready:
                self.receive(c,epoch:current)
                Task {
                    do {
                        self.send(Wire.hello(self.nextSeq()))
                        _=try? await self.command(8,[8,48,8,15,8,11])
                        _=try? await self.command(15,[])
                        try await Task.sleep(nanoseconds:2_000_000_000)
                        guard current==self.epoch else{return}
                        _=try? await self.command(118,[8,5])
                        do {_=try await self.command(1,[16,1,32,128,247,2,56,43,80,14,96,1])}
                        catch {_=try await self.command(1,[16,1,48,40,56,9,64,1,72,40,80,18])}
                        guard current==self.epoch else{return}
                        self.connected=true;self.connecting=false;self.message=L.text("waiting",self.language)
                        await self.refresh()
                        self.heartbeat=Task {while !Task.isCancelled {try? await Task.sleep(nanoseconds:3_000_000_000);guard !Task.isCancelled,current==self.epoch else{return};self.send(Wire.hello(self.nextSeq()))}}
                    }catch{guard current==self.epoch else{return};self.fail(error)}
                }
            case .failed(let e):self.fail(e)
            case .waiting: self.message=L.text("wifiHint",self.language)
            default:break
            }
        }}
        c.start(queue:.main)
        Task {try? await Task.sleep(nanoseconds:12_000_000_000);if self.connecting && current==self.epoch {self.fail(MonitorError.timeout)}}
    }
    private func receive(_ c:NWConnection,epoch:Int){
        c.receive(minimumIncompleteLength:1,maximumLength:65536){[weak self] data,_,complete,error in
            Task {@MainActor in
                guard let self=self,epoch==self.epoch else{return}
                if let data=data {
                    for packet in self.scanner.append(Array(data)) {
                        if packet.type==4 {self.pending.removeValue(forKey:packet.id)?.resume(returning:packet.body)}
                        if packet.type==1 && packet.subtype==0x20 {self.decoder.decode(packet.body)}
                    }
                }
                if complete || error != nil {self.fail(error ?? MonitorError.disconnected);return}
                self.receive(c,epoch:epoch)
            }
        }
    }
    private func nextSeq()->UInt8 {defer{seq &+= 1};return seq}
    private func send(_ data:[UInt8]){connection?.send(content:Data(data),completion:.contentProcessed({[weak self] error in
        if let error=error {Task {@MainActor in self?.message=error.localizedDescription}}
    }))}
    func command(_ code:Int,_ body:[UInt8],timeout:UInt64=2500)async throws->[UInt8]{
        guard connection != nil else{throw MonitorError.disconnected}
        requestID &+= 1;let id=requestID;let generation=epoch
        return try await withCheckedThrowingContinuation {continuation in
            pending[id]=continuation
            send(Wire.command(seq:nextSeq(),code:code,id:id,body:body))
            Task {
                try? await Task.sleep(nanoseconds:timeout*1_000_000)
                if generation==epoch {pending.removeValue(forKey:id)?.resume(throwing:MonitorError.timeout)}
            }
        }
    }
    private func fail(_ error:Error){
        closeNow();message=L.text("failed",language)+": "+String(describing:error)
    }
    private func closeNow(){
        motionRevision+=1;stickTask?.cancel();stickTask=nil
        epoch+=1;heartbeat?.cancel();heartbeat=nil
        connection?.cancel();connection=nil
        let requests=pending;pending.removeAll();for (_,c) in requests {c.resume(throwing:MonitorError.disconnected)}
        connected=false;connecting=false;hasFrame=false;busy=false;mode=0;recording=false;color=nil;format=nil;firmware=nil
        decoder.reset();onPixel?(nil)
    }
    func disconnect(){
        stopStick();connected=false;connecting=false;hasFrame=false;onPixel?(nil)
        let captured=epoch
        shutdownTask=Task {
            try? await Task.sleep(nanoseconds:180_000_000)
            if epoch==captured {_=try? await command(2,[],timeout:500);closeNow()}
        }
    }
    func refresh()async {
        if firmware == nil,let b=try? await command(8,[8,30]),let options=Wire.fields(b)[2]?.first,let f=Wire.fields(options)[30]?.first {firmware=String(bytes:f,encoding:.utf8)}
        if let b=try? await command(8,[8,40,8,41]),let options=Wire.fields(b)[2]?.first {
            let p=Wire.value(options,40);let v=Wire.value(options,41)
            if let v=v,v != 100 {mode=2} else if let p=p,p != 100 {mode=1}
            else if p==100 {mode=2} else if v==100 {mode=1}
        }
        if let b=try? await command(15,[]),let status=Wire.fields(b)[1]?.first {
            let s=Wire.value(status,1) ?? 0;recording=s != 0 && s != 8
        }
        if mode==2 {
            if let b=try? await command(10,Wire.int(1,31)+Wire.int(2,7)),let value=Wire.fields(b)[2]?.first {format=Wire.value(value,31)}
            if let b=try? await command(10,Wire.int(1,35)+Wire.int(2,7)),let value=Wire.fields(b)[2]?.first {color=Wire.value(value,35)}
        }
    }
    private func control(_ work:@escaping () async throws->Void){
        guard connected && !busy else{return};busy=true
        Task {do{try await work();message=L.text("confirmed",language)}catch{message=L.text("failed",language)+": "+String(describing:error)};busy=false}
    }
    func capture(){control {
        if self.mode==1 {_=try await self.command(3,[])}
        else if self.mode==2 {
            _=try await self.command(self.recording ? 5:4,self.recording ? [16,1]:[8,1])
            try await Task.sleep(nanoseconds:300_000_000);await self.refresh()
        }else{await self.refresh()}
    }}
    func switchMode(){guard !recording else{return};control {
        let target=self.mode==1 ? 2:1
        _=try await self.command(7,Wire.int(1,target==1 ? 40:41)+Wire.bytes(2,[]))
        try await Task.sleep(nanoseconds:350_000_000);await self.refresh()
        guard self.mode==target else{throw MonitorError.rejected}
    }}
    func commitZoom(){let z=zoom;control {
        let mode=self.mode==1 ? 6:7
        _=try await self.command(9,Wire.int(1,53)+Wire.bytes(2,Wire.double(53,z))+Wire.int(3,mode))
    }}
    func setPhotoOption(_ field:Int,value:Int){guard mode==2 && !recording else{return};control {
        _=try await self.command(9,Wire.photography(field,value))
        try await Task.sleep(nanoseconds:400_000_000)
        let b=try await self.command(10,Wire.int(1,field)+Wire.int(2,7))
        guard let nested=Wire.fields(b)[2]?.first,Wire.value(nested,field)==value else{throw MonitorError.rejected}
        if field==35{self.color=value};if field==31{self.format=value}
    }}
    func moveStick(x:Double,y:Double){
        guard connected && Wire.supportsFirmware(firmware) else{return}
        let radius=hypot(x,y);if radius<0.1{stopStick();return}
        stick=(Int(x/max(1,radius)*60),Int(-y/max(1,radius)*60))
        guard stickTask==nil else{return};motionRevision+=1;let revision=motionRevision
        stickTask=Task {
            while !Task.isCancelled && revision==motionRevision && connected {
                fireGimbal(Wire.gimbal(x:stick.0,y:stick.1))
                try? await Task.sleep(nanoseconds:50_000_000)
            }
        }
    }
    private func fireGimbal(_ body:[UInt8]){requestID &+= 1;send(Wire.command(seq:nextSeq(),code:226,id:requestID,body:body))}
    func stopStick(){
        stickTask?.cancel();stickTask=nil;motionRevision+=1;let revision=motionRevision;let generation=epoch
        guard connection != nil else{return}
        fireGimbal(Wire.gimbal(x:0,y:0))
        Task {try? await Task.sleep(nanoseconds:150_000_000);if revision==motionRevision && generation==epoch {fireGimbal(Wire.gimbal(x:0,y:0))}}
    }
    func gimbalAction(_ action:Int){guard Wire.supportsFirmware(firmware) else{return};stopStick();control {
        if action==2 {_=try? await self.command(238,[])}
        _=try await self.command(226,Wire.int(1,action))
    }}
}
