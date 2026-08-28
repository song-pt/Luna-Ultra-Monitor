# Luna Monitor 1.0

[中文](#中文) · [English](#english) · [日本語](#日本語)

## 中文

面向 Insta360 Luna Ultra 的非官方 Android 12+ 无线外接监看器。应用通过相机 Wi‑Fi 提供低延迟预览、双 LUT、手动调色、监看辅助及经回读验证的基础相机控制。

### 功能

- 保持比例、裁切填满、拉伸填满和 1:1 像素显示；
- 一个还原 `.cube` LUT 加一个风格 `.cube` LUT，可只使用任意一个；
- 曝光、亮度、对比度、高光、阴影、白色、黑色、色温、色调、饱和度、自然饱和度、锐度等实时预览调整；
- 斑马纹、假色、峰值对焦、三分线、中心点和安全框；
- 录像、拍照、拍摄模式、1×–12× 变焦；
- 1080P、2.7K、4K、8K 视频规格与相应帧率，写入后回读确认；

### 安装与构建

安装 Releases 中的 APK，先在 Android 系统设置连接相机 Wi‑Fi，再启动应用。源码构建需要 JDK 17、Android SDK 36：

本项目使用了AI进行开发。

### 代码使用说明

本项目没有照搬参考项目的源代码、UI、图标、水印或官方素材，仅使用公开披露的协议行为、命令号、字段含义、设备响应及兼容性研究结果作为独立实现的参考线索；Android/Kotlin、OpenGL ES、LUT、UI 与相机控制逻辑均在本仓库独立编写。各参考项目的具体用途和边界请查看 [`SOURCE_REFERENCES.md`](SOURCE_REFERENCES.md)。

### 非官方说明

影石 Insta360 和 Luna Ultra 商标归权利人所有。本项目是独立开发的非官方兼容工具，与影石不存在隶属、授权或合作关系。

### 侵删声明

如权利人认为本仓库中的具体内容侵犯其合法权益，请通过邮箱TonggithubContact@tongnet.top或私信本人的Bilibili账号@Tong不是元素 来联系我，我会删除侵权内容。

## English

An unofficial Android 12+ wireless field monitor for Insta360 Luna Ultra. It provides low-latency preview, dual LUT processing, manual preview grading, monitoring tools, and camera controls that use read-back where the protocol supports it.

### Features

- Fit, crop-to-fill, stretch, and 1:1 pixel display modes;
- One recovery `.cube` LUT followed by one look `.cube` LUT; either slot works alone;
- Real-time exposure, brightness, contrast, highlights, shadows, whites, blacks, temperature, tint, saturation, vibrance, sharpness, and other preview adjustments;
- Zebras, false color, focus peaking, thirds, center mark, and safe-area guides;
- Record, still capture, capture-mode switching, and 1×–12× zoom;
- 1080P, 2.7K, 4K, and 8K video formats with supported frame rates and camera read-back;
- Chinese, English, and Japanese UI, switchable before connecting.

### Install

Install the APK from Releases, connect Android to the camera Wi‑Fi, and open the app. Building requires JDK 17 and Android SDK 36.

This project was developed with AI assistance.

### Code-use statement

This project does not copy source code, UI, icons, watermarks, or official assets from the referenced projects. It uses only publicly disclosed protocol behavior, command numbers, field meanings, device responses, and interoperability findings as research leads for an independent implementation. The Android/Kotlin, OpenGL ES, LUT, UI, and camera-control code in this repository was written independently. See [`SOURCE_REFERENCES.md`](SOURCE_REFERENCES.md) for the exact scope of each reference.

### Unofficial status

Insta360 and Luna Ultra are trademarks of their respective owners. This project is an independent, unofficial compatibility tool and is not affiliated with, endorsed by, or sponsored by Insta360.

### Rights-holder removal request

If you believe that specific content in this repository infringes upon your legitimate rights and interests, please contact me via email at TonggithubContact@tongnet.top or by sending a private message to my Bilibili account @Tong不是元素. I will remove the infringing content accordingly.

## 日本語

Insta360 Luna Ultra 用の非公式 Android 12+ ワイヤレス外部モニターです。低遅延プレビュー、デュアル LUT、手動カラー調整、モニタリング支援、および読戻し確認付きカメラ操作を提供します。

### 機能

- 全体表示、クロップ表示、引き伸ばし、1:1 ピクセル表示；
- 変換 `.cube` LUT とルック `.cube` LUT を各1個。片方のみでも使用可能；
- 露出、明るさ、コントラスト、ハイライト、シャドウ、色温度、彩度、シャープネスなどのリアルタイム調整；
- ゼブラ、フォルスカラー、ピーキング、三分割、センターマーク、セーフエリア；
- 録画、写真撮影、撮影モード、1×–12× ズーム；
- 1080P、2.7K、4K、8K と対応フレームレート。設定後にカメラから読戻し確認；
- 中国語、英語、日本語 UI。未接続画面で切替可能。

Releases の APK をインストールし、Android をカメラ Wi‑Fi に接続して起動してください。ビルドには JDK 17 と Android SDK 36 が必要です。

本プロジェクトはAIを使用して開発されました。

### コード利用について

本プロジェクトは、参照プロジェクトのソースコード、UI、アイコン、透かし、公式素材を複製していません。公開されたプロトコル動作、コマンド番号、フィールドの意味、機器応答、互換性調査結果のみを独自実装の参考情報として使用しています。Android/Kotlin、OpenGL ES、LUT、UI、カメラ操作コードは本リポジトリで独自に作成しました。各参照先の利用範囲は [`SOURCE_REFERENCES.md`](SOURCE_REFERENCES.md) を参照してください。

### 非公式声明

Insta360 および Luna Ultra は各権利者の商標です。本プロジェクトは独立した非公式互換ツールであり、Insta360 との提携、承認、または支援関係はありません。

### 権利者からの削除依頼

権利者が本リポジトリ内の具体的な内容がその正当な権益を侵害していると判断した場合、メールアドレスTonggithubContact@tongnet.top、または私のBilibiliアカウント@Tong不是元素へのダイレクトメッセージにてご連絡ください。侵害内容を削除いたします。

## References / 参考项目 / 参考プロジェクト

- https://github.com/diamondfsd/luna-ai-cut — MIT; public protocol research reference.
- https://github.com/Ripwords/insta360-luna-ultra-desktop — public protocol observations only; no source code, UI, watermark, icon, or official asset from this repository is included.
- https://github.com/derrickyau9/insta360-luna-ultra-reverse-engineering — Apache-2.0; device research reference only; its root/ADB/SSH scripts are not included.

Detailed provenance is recorded in [`SOURCE_REFERENCES.md`](SOURCE_REFERENCES.md) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## License

Project-authored source code is licensed under the [Apache License 2.0](LICENSE). Third-party components remain under their respective licenses.
