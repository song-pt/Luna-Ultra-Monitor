package com.lunamonitor.beta.model

enum class ConnectionState { IDLE, CONNECTING, HANDSHAKING, WAITING_VIDEO, STREAMING, ERROR }
enum class ToolSection { COLOR, LUT, EXPOSURE, FOCUS, COMPOSITION, DISPLAY, CAMERA_SETTINGS }
enum class ScaleMode { FIT, FILL_CROP, STRETCH, PIXEL_TO_PIXEL }
enum class ColorMode { STANDARD, LOG }
enum class DrawerSide { LEFT, RIGHT }
enum class CaptureMode { UNKNOWN, VIDEO, PHOTO }
enum class CameraColorMode(val protocolValue: Int) { STANDARD(1), I_LOG(2), DOLBY_VISION(5) }
enum class AppLanguage { ZH, EN, JA }

class CubeLut(val name: String, val size: Int, val rgb: FloatArray) {
    init {
        require(size in 2..65) { "3D LUT size must be between 2 and 65" }
        require(rgb.size == size * size * size * 3)
    }
}

data class VideoFormatOption(val label: String, val resolutionCode: Int)

data class ColorAdjustments(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val sharpness: Float = 0f,
    val midtones: Float = 0f,
    val gamma: Float = 0f,
    val hue: Float = 0f,
    val fade: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val highlightSoftness: Float = 0f,
    val vignette: Float = 0f,
)

data class StreamStats(
    val width: Int = 1440,
    val height: Int = 720,
    val fps: Float = 0f,
    val bitrateKbps: Int = 0,
    val mediaFrames: Long = 0,
    val mediaPackets: Long = 0,
    val codec: String = "检测中",
    val processingDelayMs: Int? = null,
)

data class MonitorUiState(
    val language: AppLanguage = AppLanguage.ZH,
    val connection: ConnectionState = ConnectionState.IDLE,
    val connectionMessage: String = "未连接",
    val selectedSection: ToolSection? = null,
    val colorMode: ColorMode = ColorMode.STANDARD,
    val adjustments: ColorAdjustments = ColorAdjustments(),
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val showThirds: Boolean = false,
    val showCenter: Boolean = false,
    val showSafeArea: Boolean = false,
    val guideWidth: Float = 2f,
    val guideOpacity: Float = 0.72f,
    val centerMarkSize: Float = 32f,
    val zebra: Boolean = false,
    val falseColor: Boolean = false,
    val peaking: Boolean = false,
    val cleanView: Boolean = false,
    val controlsExpanded: Boolean = false,
    val gimbalExpanded: Boolean = false,
    val cameraFirmware: String? = null,
    val controlsSide: DrawerSide = DrawerSide.RIGHT,
    val cameraControlBusy: Boolean = false,
    val recording: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.UNKNOWN,
    val videoFormat: VideoFormatOption? = null,
    val cameraColorMode: CameraColorMode? = null,
    val selectedVideoSize: String = "1080P",
    val recoveryLut: CubeLut? = null,
    val styleLut: CubeLut? = null,
    val lutMessage: String? = null,
    val zoom: Float = 1f,
    val cameraControlMessage: String? = null,
    val thermalSuggestion: String? = null,
    val thermalReductionAccepted: Boolean = false,
    val streamStats: StreamStats = StreamStats(),
    val error: String? = null,
)
