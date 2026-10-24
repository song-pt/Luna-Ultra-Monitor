package com.lunamonitor.beta.ui

import com.lunamonitor.beta.model.AppLanguage

private data class Localized(val zh: String, val en: String, val ja: String)

object UiText {
    private val values = mapOf(
        "gimbalFirmware" to Localized("尚未识别支持的固件，请重新连接", "Supported firmware not identified; reconnect", "対応ファームウェア未確認。再接続してください"),
        "gimbal" to Localized("云台", "Gimbal", "ジンバル"),
        "recenter" to Localized("回中", "Center", "中央"),
        "flip" to Localized("翻转", "Flip", "反転"),
        "gimbalDirections" to Localized("摇杆：上、下、左、右", "Joystick: up, down, left, right", "スティック：上下左右"),
        "gimbalRelease" to Localized("松手停止 · 断网时请检查相机", "Release to stop. Check camera if disconnected.", "離すと停止。切断時は本体を確認"),
        "color" to Localized("颜色", "Color", "カラー"), "lut" to Localized("LUT", "LUT", "LUT"),
        "exposure" to Localized("曝光", "Exposure", "露出"), "focus" to Localized("对焦", "Focus", "フォーカス"),
        "composition" to Localized("构图", "Frame", "構図"), "display" to Localized("显示", "Display", "表示"),
        "parameters" to Localized("参数", "Format", "設定"), "camera" to Localized("相机", "Camera", "カメラ"),
        "collapse" to Localized("收起", "Collapse", "閉じる"), "connectTitle" to Localized("连接 Luna Ultra 开始监看", "Connect Luna Ultra to monitor", "Luna Ultra に接続してモニター"),
        "wifiHint" to Localized("请先在系统设置连接相机 Wi-Fi", "Connect to the camera Wi-Fi in system settings first", "先にシステム設定でカメラ Wi-Fi に接続してください"),
        "connecting" to Localized("连接中…", "Connecting…", "接続中…"), "connect" to Localized("连接相机", "Connect camera", "カメラに接続"),
        "language" to Localized("语言", "Language", "言語"), "cleanExit" to Localized("纯净模式 · 点击退出", "Clean view · Tap to exit", "クリーン表示・タップで終了"),
        "colorTitle" to Localized("标准画面调色", "Manual color preview", "手動カラープレビュー"),
        "colorSubtitle" to Localized("只改变监看，不影响相机素材", "Preview only; camera files are unchanged", "プレビューのみ。カメラ素材は変更しません"),
        "lutTitle" to Localized("双 LUT 预览", "Dual LUT preview", "デュアル LUT プレビュー"),
        "lutSubtitle" to Localized("还原 LUT → 风格 LUT，可单独使用任意一个", "Recovery LUT → Look LUT; either slot can be used alone", "変換 LUT → ルック LUT。片方だけでも使用可能"),
        "exposureTitle" to Localized("曝光辅助", "Exposure tools", "露出アシスト"), "exposureSubtitle" to Localized("按需启用，不遮挡主要画面", "Enable only the tools you need", "必要なツールだけ有効にします"),
        "focusTitle" to Localized("对焦辅助", "Focus tools", "フォーカスアシスト"), "focusSubtitle" to Localized("确认清晰区域", "Confirm sharp areas", "合焦領域を確認"),
        "compositionTitle" to Localized("构图辅助", "Composition guides", "構図ガイド"), "compositionSubtitle" to Localized("辅助线以有效画面为基准", "Guides follow the visible image area", "ガイドは有効映像領域に合わせます"),
        "displayTitle" to Localized("画面显示", "Image display", "映像表示"), "displaySubtitle" to Localized("默认保持原比例", "Aspect ratio is preserved by default", "既定では縦横比を維持します"),
        "parametersTitle" to Localized("视频参数", "Video format", "動画設定"), "parametersSubtitle" to Localized("左侧选择画质，右侧仅显示支持的帧率", "Choose resolution on the left; supported frame rates appear on the right", "左で解像度を選択し、右に対応フレームレートのみ表示"),
        "recoveryLut" to Localized("还原 LUT", "Recovery LUT", "変換 LUT"), "styleLut" to Localized("风格 LUT", "Look LUT", "ルック LUT"),
        "selectCube" to Localized("选择 .cube", "Choose .cube", ".cube を選択"), "clear" to Localized("清除", "Clear", "削除"),
        "none" to Localized("未加载", "Not loaded", "未読込"), "lutOrder" to Localized("处理顺序：还原 LUT → 风格 LUT → 手动调色", "Order: Recovery LUT → Look LUT → manual adjustments", "処理順：変換 LUT → ルック LUT → 手動調整"),
        "zebra" to Localized("斑马纹", "Zebras", "ゼブラ"), "falseColor" to Localized("假色", "False color", "フォルスカラー"), "peaking" to Localized("峰值对焦", "Focus peaking", "ピーキング"),
        "thirds" to Localized("三分线", "Rule of thirds", "三分割"), "centerMark" to Localized("中心标记", "Center mark", "センターマーク"), "safeArea" to Localized("90% 安全框", "90% safe area", "90% セーフエリア"),
        "lineWidth" to Localized("线宽", "Width", "線幅"), "opacity" to Localized("不透明度", "Opacity", "不透明度"), "centerSize" to Localized("中心大小", "Center size", "中心サイズ"),
        "fit" to Localized("完整显示 · 无变形", "Fit · no distortion", "全体表示・歪みなし"), "cropFill" to Localized("裁切填满 · 无变形", "Crop to fill · no distortion", "クロップ・歪みなし"),
        "stretch" to Localized("拉伸填满 · 有变形", "Stretch to fill · distorted", "引き伸ばし・歪みあり"), "pixel" to Localized("1:1 像素检查", "1:1 pixel check", "1:1 ピクセル確認"), "clean" to Localized("纯净画面", "Clean view", "クリーン表示"),
        "resolution" to Localized("画质", "Resolution", "解像度"), "frameRate" to Localized("帧率", "Frame rate", "フレームレート"),
        "cameraColorMode" to Localized("相机色彩模式", "Camera color mode", "カメラカラーモード"),
        "cameraColorStandard" to Localized("标准", "Standard", "標準"),
        "cameraColorDolby" to Localized("杜比视界", "Dolby Vision", "Dolby Vision"),
        "cameraColorILog" to Localized("I-Log", "I-Log", "I-Log"),
        "cameraColorVideoOnly" to Localized("仅在未录制的视频模式下可调整", "Available only in video mode while not recording", "動画モードで録画停止中のみ変更できます"),
        "applyReadback" to Localized("选择后写入相机并回读确认", "Applied to camera and verified by read-back", "カメラへ適用後、読戻しで確認"),
        "cameraControl" to Localized("相机控制", "Camera control", "カメラ操作"), "photoMode" to Localized("拍照模式", "Photo mode", "写真モード"),
        "videoMode" to Localized("视频模式", "Video mode", "動画モード"), "detectingMode" to Localized("正在识别模式", "Detecting mode", "モード検出中"),
        "takePhoto" to Localized("拍照", "Take photo", "撮影"), "startRecord" to Localized("开始录制", "Start recording", "録画開始"), "stopRecord" to Localized("结束录制", "Stop recording", "録画停止"),
        "readMode" to Localized("读取模式", "Read mode", "モード読込"), "switchVideo" to Localized("切换视频", "Switch to video", "動画へ切替"), "switchPhoto" to Localized("切换拍照", "Switch to photo", "写真へ切替"),
        "redetect" to Localized("重新识别", "Detect again", "再検出"), "disconnect" to Localized("断开", "Disconnect", "切断"),
        "dragHint" to Localized("手动收回后可拖到另一侧", "Collapse, then drag to the other side", "閉じてから反対側へドラッグ"),
        "agree" to Localized("同意", "Accept", "同意"), "keep" to Localized("保持当前", "Keep current", "現状維持"), "close" to Localized("关闭", "Close", "閉じる"),
        "adjustExposure" to Localized("曝光", "Exposure", "露出"), "brightness" to Localized("亮度", "Brightness", "明るさ"), "contrast" to Localized("对比度", "Contrast", "コントラスト"),
        "highlights" to Localized("高光", "Highlights", "ハイライト"), "shadows" to Localized("阴影", "Shadows", "シャドウ"), "whites" to Localized("白色", "Whites", "白レベル"), "blacks" to Localized("黑色", "Blacks", "黒レベル"),
        "temperature" to Localized("色温", "Temperature", "色温度"), "tint" to Localized("色调", "Tint", "色かぶり"), "saturation" to Localized("饱和度", "Saturation", "彩度"), "vibrance" to Localized("自然饱和度", "Vibrance", "自然な彩度"),
        "sharpness" to Localized("锐度", "Sharpness", "シャープネス"), "midtones" to Localized("中间调", "Midtones", "中間調"), "gamma" to Localized("Gamma", "Gamma", "ガンマ"), "hue" to Localized("色相", "Hue", "色相"),
        "fade" to Localized("淡化", "Fade", "フェード"), "clarity" to Localized("清晰度", "Clarity", "明瞭度"), "dehaze" to Localized("去雾", "Dehaze", "かすみ除去"), "softHighlights" to Localized("高光柔化", "Highlight softness", "ハイライト柔化"), "vignette" to Localized("暗角", "Vignette", "周辺減光")
    )

    fun get(language: AppLanguage, key: String): String {
        val value = values[key] ?: return key
        return when (language) { AppLanguage.ZH -> value.zh; AppLanguage.EN -> value.en; AppLanguage.JA -> value.ja }
    }

    fun runtime(language: AppLanguage, source: String): String {
        if (language == AppLanguage.ZH) return source
        val exact = mapOf(
            "图传连接已中断" to Localized("图传连接已中断", "Camera connection lost", "カメラ接続が切断されました"),
            "云台控制" to Localized("云台控制", "Gimbal control", "ジンバル操作"),
            "未连接" to Localized("未连接", "Disconnected", "未接続"), "实时监看" to Localized("实时监看", "Live monitor", "ライブモニター"),
            "正在连接 Luna Ultra" to Localized("正在连接 Luna Ultra", "Connecting to Luna Ultra", "Luna Ultra に接続中"),
            "正在建立图传会话" to Localized("正在建立图传会话", "Starting live-view session", "ライブビューを開始中"),
            "已连接，等待实时画面" to Localized("已连接，等待实时画面", "Connected; waiting for video", "接続済み・映像待機中"),
            "照片已拍摄" to Localized("照片已拍摄", "Photo captured", "写真を撮影しました"), "相机正在录制" to Localized("相机正在录制", "Camera is recording", "録画中"),
            "录制已结束" to Localized("录制已结束", "Recording stopped", "録画を停止しました"), "拍照模式" to Localized("拍照模式", "Photo mode", "写真モード"), "视频模式" to Localized("视频模式", "Video mode", "動画モード")
            ,"色彩模式仅可在未录制的视频模式下设置" to Localized("色彩模式仅可在未录制的视频模式下设置", "Color mode is available only in video mode while not recording", "カラーモードは動画モードで録画停止中のみ設定できます")
            ,"3D LUT size must be between 2 and 65" to Localized("3D LUT 尺寸必须在 2 到 65 之间", "3D LUT size must be between 2 and 65", "3D LUT サイズは 2〜65 にしてください")
            ,"检测中" to Localized("检测中", "Detecting", "検出中"),
            "设备温度较高，是否降低示波器刷新率以保持流畅？" to Localized("设备温度较高，是否降低示波器刷新率以保持流畅？", "Device temperature is high. Reduce scope refresh rate?", "端末温度が高いです。スコープ更新頻度を下げますか？")
        )[source]
        if (exact != null) return when (language) { AppLanguage.EN -> exact.en; AppLanguage.JA -> exact.ja; else -> exact.zh }
        if (source.startsWith("正在设置 ")) return if (language == AppLanguage.EN) "Applying ${source.removePrefix("正在设置 ")}" else "${source.removePrefix("正在设置 ")} を適用中"
        if (source.startsWith("视频规格 ")) return if (language == AppLanguage.EN) "Video format ${source.removePrefix("视频规格 ")}" else "動画設定 ${source.removePrefix("视频规格 ")}"
        fun colorName(name: String) = when (name) {
            "标准" -> if (language == AppLanguage.EN) "Standard" else "標準"
            "杜比视界" -> "Dolby Vision"
            else -> name
        }
        if (source.startsWith("正在设置色彩模式 ")) return if (language == AppLanguage.EN) "Applying color mode ${colorName(source.removePrefix("正在设置色彩模式 "))}" else "カラーモード ${colorName(source.removePrefix("正在设置色彩模式 "))} を適用中"
        if (source.startsWith("色彩模式 ")) return if (language == AppLanguage.EN) "Color mode ${colorName(source.removePrefix("色彩模式 "))}" else "カラーモード ${colorName(source.removePrefix("色彩模式 "))}"
        if (source.startsWith("变焦已设为 ")) return if (language == AppLanguage.EN) "Zoom ${source.removePrefix("变焦已设为 ")}" else "ズーム ${source.removePrefix("变焦已设为 ")}"
        return if (language == AppLanguage.EN) "Camera message" else "カメラメッセージ"
    }
}
