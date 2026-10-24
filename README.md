# Luna Monitor 1.1.0

[中文](#中文) · [English](#english) · [日本語](#日本語)

## 中文

面向 Insta360 Luna Ultra 的非官方无线监看工具。Android 最低 12；新增 iOS/iPadOS 16+ 原生平台层。**本版已进行构建、协议和合成 GPU 测试；尚未完成本次相机实测、iPhone/SideStore 安装测试。不能将这些测试等同于现场兼容性保证。**

### 1.1.0 更新
- 保留可收起/手动展开的相机入口，展开后为拍照/录像切换键、圆形快门和**竖向倍率区**，不再是大菜单；默认在右侧，收起后拖动可换边。
- 对侧独立摇杆，支持二维速度控制、回中、翻转；松手、收起、打开工具面板、进入后台时发送停止。读取不到支持固件时禁用移动；无网络时不能保证停止包到达，须检查相机。
- 公开协议记录基于固件 1.0.288/1.1.15；本版保守门槛为识别到 1.0.288 以上版本。版本号不是厂商兼容性承诺。变焦保留已支持的 1×–12×；滑动松手后提交，不新增未经确认的倍率。
- 菜单实际还有内容时显示渐隐/方向提示，到底消失；短屏布局保留。
- 及时排出解码输出、优先呈现最新已解码帧、减少整帧边界等待及 UI 更新频率；协议校验后再解码，溢出不继续使用丢失的参考帧。
- 修复软件侧锐度/对焦辅助的邻域采样越界风险；**未随意裁掉底部画面**。真实相机底部窄带的来源仍需按[诊断方法](docs/VALIDATION.md)确认。
- iOS 是对现有协议、数据、调色公式和交互的原生适配；Android 沿用 Kotlin/Compose/MediaCodec/OpenGL ES，未更换跨平台框架。iOS 使用 SwiftUI/Network/VideoToolbox/Metal。

### 保留功能
双 `.cube` LUT（还原→风格，任一可单独使用，2–65 点）、20 项手动调色、斑马纹/假色/峰值、构图线宽/透明度/中心大小、原比例/裁切/拉伸/1:1 显示、视频规格与标准/杜比视界/I-Log 相机色彩控制、中英日界面。预览 LUT/调色不写入相机素材；画质选择控制**录制规格**，不是图传分辨率升级。未加入官方 LUT、水印或第三方图标资源。

### 安装
- Android：安装 `LunaMonitor-1.1.0.apk`。先在系统设置连接相机 Wi-Fi，关闭占用连接的官方 App，再连接。本机构建沿用 debug 签名机制；正式维护者应使用自己保管的签名密钥。覆盖安装仍须证书一致，勿先卸载而丢失设置。
- iOS：`LunaMonitor-1.1.0-unsigned.ipa` **无开发者签名，不能直接安装**。通过 SideStore 或自己的签名工具重签，或在 Xcode 配置自己的 Team。SideStore 的账号、配置文件、系统兼容性和续签限制以该工具文档为准；本项目不提供账号、证书或安装绕过。
- iOS 先手动加入相机 Wi-Fi，再允许「本地网络」访问。基础功能不依赖自动加入 Wi-Fi/SSID entitlement、私有 API 或越狱。后台会停止摇杆并断开预览，不会因此停止相机正在进行的录制。
- 热管理仍先通过顶部提示征求同意，不擅自降低效果；系统热降频不受应用控制。

### 构建
Android：JDK 17、Android SDK/Build Tools 36，在仓库根目录执行 `./gradlew testDebugUnitTest assembleRelease`。

iOS：Xcode 工程为 `ios/LunaMonitor.xcodeproj`。在此电脑上：

```sh
DEVELOPER_DIR='/Volumes/LKX/临时Xcode/Xcode.app/Contents/Developer' ./ios/build-unsigned.sh
```

脚本不修改全局 Xcode 选择，不访问签名账号。其他电脑设置 `DEVELOPER_DIR` 为自己的 Xcode Developer 目录。自签请在工程中配置自己的标识和 Team，并显式开启签名。协议与 macOS Metal 合成测试见 `ios/Tests/main.swift`；构建不代表真实相机或安装验证。

### 代码、协议及权利说明
本项目使用了AI进行开发。

本次改动在既有自编代码上实现；iOS 层适配现有协议和调色公式，仅参考公开协议事实，没有搬用参考项目的 TypeScript/Rust/Swift/Kotlin 实现、界面或官方资产。[SOURCE_REFERENCES.md](SOURCE_REFERENCES.md) 列出项目及新增协议指引；[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 列出依赖。项目自编源码采用 [Apache-2.0](LICENSE)，第三方组件各自遵循自身许可证。协议公开不等于厂商授权或对所有知识产权风险的法律保证。

本软件与 Insta360/影石无隶属、授权或合作关系，商标归权利人所有。如权利人认为具体内容侵犯其合法权益，请通过 **TonggithubContact@tongnet.top** 或 Bilibili **@Tong不是元素** 联系，我会核实并删除侵权内容。

## English

An unofficial wireless monitor for Insta360 Luna Ultra. Android 12+; a native iOS/iPadOS 16+ platform adapter is added. **Build, protocol and synthetic GPU tests are not camera/phone field validation. This delivery has not been tested on a real Luna, iPhone, or through SideStore.**

### Changes
- Collapsible camera controls now use a capture-mode switch, round shutter and **vertical zoom rail**. Default right; collapse and drag to swap sides.
- Opposite-side joystick, recenter and flip. Release, collapse, opening a tool panel and backgrounding send stop commands. Motion is disabled if supported firmware cannot be identified. A disconnected link cannot guarantee stop delivery; check the camera.
- Protocol evidence covers firmware 1.0.288/1.1.15. The version gate is a conservative research threshold, not a vendor compatibility promise. Existing 1×–12× zoom remains; continuous zoom commits on release.
- Scroll-edge fades/chevrons appear only when more content is reachable.
- Independent decoder draining, newest decoded-frame presentation, earlier complete-packet delivery and throttled UI statistics reduce avoidable buffering. No measured end-to-end latency claim is made.
- Neighbor sampling is clamped at image boundaries; no arbitrary bottom crop. The real-camera bottom-strip source remains unconfirmed; see [validation](docs/VALIDATION.md).
- Existing Android Kotlin/Compose/MediaCodec/GLES architecture is retained. iOS adapts the existing protocol, resources and grade formulas using SwiftUI, Network, VideoToolbox and Metal; this is not an Android framework rewrite.

### Features and installation
Dual 2–65 point `.cube` LUTs (recovery then look; either alone), 20 preview adjustments, zebras/false color/peaking, adjustable guides, fit/crop/stretch/1:1, video-format and Standard/Dolby Vision/I-Log controls, Chinese/English/Japanese UI. Preview grading does not alter recorded camera files. Recording resolution is separate from live-feed resolution. No official LUTs or watermark assets are bundled.

Android: install `LunaMonitor-1.1.0.apk`, manually join camera Wi-Fi and close other camera clients. The local release uses the existing debug-signing mechanism; maintainers should keep their own signing key. Updating requires the same certificate; uninstalling can lose settings.

iOS: `LunaMonitor-1.1.0-unsigned.ipa` is **unsigned and cannot install directly**. Re-sign it using SideStore/your own signing setup, or configure your Team and signing in Xcode. No signing identity is supplied, and SideStore installation is not verified. Manually join camera Wi-Fi and allow Local Network access. No automatic Wi-Fi entitlement, private API, jailbreak or background keepalive is required. Backgrounding stops the joystick and closes preview, not an ongoing camera recording. Thermal effect reduction requires an unobtrusive user confirmation; OS thermal throttling is outside app control.

Build Android with JDK 17 and SDK/Build Tools 36: `./gradlew testDebugUnitTest assembleRelease`. Build iOS with `DEVELOPER_DIR=/path/to/Xcode.app/Contents/Developer ./ios/build-unsigned.sh`. The Xcode project and native protocol/Metal tests are in `ios/`. The script never changes the system-wide Xcode selection or uses a signing account.

### Code, license and rights
This project was developed with AI assistance.

The changes extend this project's own code. Public command numbers, field meanings and observed behavior inform independent implementations; no reference project's implementation, UI or proprietary assets were copied. See [references](SOURCE_REFERENCES.md) and [third-party notices](THIRD_PARTY_NOTICES.md). Project-authored code is [Apache-2.0](LICENSE); third-party components retain their own licenses. Public protocol research is not vendor authorization or a legal guarantee.

This is not affiliated with, authorized by or sponsored by Insta360. Trademarks belong to their owners. Rights holders may contact **TonggithubContact@tongnet.top** or Bilibili **@Tong不是元素**; identified infringing content will be reviewed and removed.

## 日本語

Insta360 Luna Ultra 用の非公式ワイヤレスモニターです。Android 12以降、追加のiOS/iPadOS版は16以降に対応する設計です。**ビルド・プロトコル・合成GPUテストのみ実施しています。今回のLuna実機、iPhoneおよびSideStoreでの検証は未実施です。**

### 更新内容
- 収納可能なカメラ操作部を、写真/動画切替、円形シャッター、**縦方向ズーム**に変更。既定は右側。閉じてからドラッグすると左右を変更できます。
- 反対側にスティック、中央復帰、反転を配置。指を離す、閉じる、ツールを開く、バックグラウンド移行時に停止指令を送ります。対応ファームウェアを確認できない場合は移動を無効化。通信断時の停止到達は保証できないため本体を確認してください。
- 公開の検証資料は1.0.288/1.1.15に基づきます。バージョン判定は互換性保証ではありません。ズームは既存の1〜12倍を維持し、指を離した時に確定します。
- 続きがある時だけスクロール方向のフェードと矢印を表示します。
- デコード出力の独立処理、最新デコード画像の表示、パケット完了時の即時処理、統計更新の間引きで不要な待ちを減らします。実測遅延値は未確認です。
- 補助表示の隣接ピクセルを画像端内に制限。任意の下端クロップは行いません。実機の帯の原因は[検証手順](docs/VALIDATION.md)で確認が必要です。
- Androidの既存構造を維持し、iOSには既存プロトコル、データ、調色式をSwiftUI/Network/VideoToolbox/Metalへ適応しました。

### 機能・導入
2〜65ポイントのデュアル`.cube` LUT、20項目のプレビュー調整、ゼブラ/フォルスカラー/ピーキング、調整可能なガイド、全体/クロップ/引き伸ばし/1:1表示、動画形式、標準/Dolby Vision/I-Log、中国語/英語/日本語を提供します。プレビュー調整は録画ファイルを変更しません。録画解像度とライブ映像解像度は別です。公式LUTや透かし素材は含みません。

AndroidはAPKをインストールし、カメラWi-Fiへ手動接続してください。更新には同じ署名証明書が必要です。現行のローカルビルドはdebug署名を使用します。公開保守時は自身の鍵を安全に管理してください。

iOSの`LunaMonitor-1.1.0-unsigned.ipa`は未署名で、そのままインストールできません。SideStore等で再署名するか、Xcodeで自身のTeamと署名を設定してください。証明書やアカウントは提供しません。SideStore実機導入は未確認です。カメラWi-Fiへの手動接続とローカルネットワーク許可を使用し、特殊なWi-Fi entitlement、非公開API、脱獄には依存しません。バックグラウンドではスティックとプレビュー接続を停止しますが、録画中のカメラ録画は停止しません。負荷軽減はユーザー確認後のみ。OSの熱制御はアプリでは無効にできません。

AndroidのビルドにはJDK 17とSDK/Build Tools 36を使用し、`./gradlew testDebugUnitTest assembleRelease`を実行します。iOSは`DEVELOPER_DIR=/path/to/Xcode.app/Contents/Developer ./ios/build-unsigned.sh`。Xcodeの全体設定や署名アカウントを変更しません。

### コード・ライセンス・権利
本プロジェクトはAIを使用して開発されました。

既存の独自コードを拡張し、公開されたコマンド番号・フィールド・挙動のみを互換性調査に利用しました。参照プロジェクトの実装、UI、専有素材はコピーしていません。[参照一覧](SOURCE_REFERENCES.md)と[第三者通知](THIRD_PARTY_NOTICES.md)をご覧ください。独自ソースは[Apache-2.0](LICENSE)、第三者コンポーネントは各自のライセンスに従います。公開プロトコルはメーカー認可や法的保証ではありません。

Insta360との提携・認可・協力関係はなく、商標は各権利者に帰属します。権利に関する連絡先は **TonggithubContact@tongnet.top** または Bilibili **@Tong不是元素**。具体的な侵害内容を確認し削除します。
