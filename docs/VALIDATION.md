# 1.1.0 verification boundaries / 验证边界 / 検証範囲

## 中文
本次没有可操控的相机/Android/iPhone测试设备。APK/IPA构建通过、单元测试通过和桌面Metal合成像素测试通过，不代表相机实机通过。IPA未做SideStore安装。禁止发布“保证200ms”“高清图传已解锁”等未实测结论。

### 底部窄带
1. 在同一固件和同一模式下保留用户同意采集的原始0x20码流，仅本地保存；不上传密码或私有素材。
2. 用独立解码器输出无缩放图像，与本应用关闭所有效果的显示对照。
3. 记录VPS/SPS裁剪、解码有效尺寸、SurfaceTexture变换，再分别启用LUT、锐度、峰值。
4. 原始图像已有窄带：标记相机输出，不擅自裁边。仅应用渲染有窄带：按真实裁剪矩形和纹理坐标修复。
本版已约束软件滤镜邻域采样，合成均匀输入在首末行相同；这不能证明真实相机的窄带已消除。

### 延迟与控制验收
记录接收完整媒体帧→解码输出→GPU提交→实际呈现。UI解码读数不等于端到端延迟；iOS读数从提交本机解码起算。用外部高速录像拍摄同一计时器测端到端。分别测试无效果、单LUT、双LUT、双LUT+辅助显示，报告P50/P95而非仅平均数。200ms是本机处理目标，尚未实测。

摇杆至少检查上下左右/斜向、松手、触摸取消、快速松手重按、收起、开工具栏、后台、锁屏、断线、重连。确认旧指令不会进入新会话。网络断开后用户必须检查机身是否停止。回中/翻转在拍照、待机、录像中分别验证。

布局覆盖短横屏、平板、分屏、大字体、中英日。实际内容不足时不显示箭头；还有内容时两列分别可滚动。保留1.0.2回归功能，不自动降低画质。

## English
No physical camera, Android phone or iPhone was available for this delivery. Builds, unit tests and synthetic desktop Metal pixels are not field validation. SideStore installation and real latency remain unverified. Compare the untouched 0x20 stream using an independent decoder against effects-off app output before attributing a bottom strip; never hide source defects with an arbitrary crop. Measure receive/decode/GPU/present stages and external glass-to-glass latency separately; report P50/P95 for each effect configuration. UI decode timings are not end-to-end measurements. Test all joystick cancellation, lifecycle and connection boundaries, including a quick release/re-touch; stop delivery cannot be guaranteed after link loss. Check short screens, tablets, large text and all languages.

## 日本語
今回、相機・Android端末・iPhoneの実機検証は未実施です。ビルド、単体テスト、デスクトップMetal合成画素テストは現場検証の代わりにはなりません。SideStore導入と実遅延も未確認。下端の帯は未加工0x20ストリームを別デコーダーで確認し、任意のクロップで隠さないでください。受信・デコード・GPU・表示と外部計測の遅延を分け、構成別P50/P95を記録します。スティックのキャンセル、閉じる操作、バックグラウンド、通信断、再接続と短画面/大文字/全言語を確認してください。通信断後の停止到達は保証できません。
