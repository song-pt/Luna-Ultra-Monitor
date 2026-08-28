package com.lunamonitor.beta

import android.app.Application
import android.os.PowerManager
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lunamonitor.beta.camera.LunaSession
import com.lunamonitor.beta.model.ColorAdjustments
import com.lunamonitor.beta.model.ConnectionState
import com.lunamonitor.beta.model.CaptureMode
import com.lunamonitor.beta.model.CameraColorMode
import com.lunamonitor.beta.model.DrawerSide
import com.lunamonitor.beta.model.MonitorUiState
import com.lunamonitor.beta.model.ScaleMode
import com.lunamonitor.beta.model.ToolSection
import com.lunamonitor.beta.model.VideoFormatOption
import com.lunamonitor.beta.model.VideoFormatPolicy
import com.lunamonitor.beta.model.AppLanguage
import com.lunamonitor.beta.model.CubeLut
import com.lunamonitor.beta.stream.AnnexBAssembler
import com.lunamonitor.beta.video.StreamDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = mutableState.asStateFlow()
    private val session = LunaSession(application)
    private val assembler = AnnexBAssembler()
    private val bytes = AtomicLong(0)
    private val frames = AtomicLong(0)
    private val packets = AtomicLong(0)
    private val zoomRevision = AtomicLong(0)
    private val decoder = StreamDecoder(
        onFormatChanged = { width, height ->
            mutableState.update { current ->
                current.copy(streamStats = current.streamStats.copy(width = width, height = height))
            }
        },
        onFrameDecoded = { frames.incrementAndGet() },
    )
    private var streamJob: Job? = null
    private var watchdogJob: Job? = null
    private var zoomJob: Job? = null
    private var disconnectJob: Job? = null
    private var lastAppliedZoom = Float.NaN
    private var decoderSurface: Surface? = null
    private var startedAt = 0L
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        if (status >= PowerManager.THERMAL_STATUS_SEVERE && !state.value.thermalReductionAccepted) {
            mutableState.update { it.copy(thermalSuggestion = "设备温度较高，是否降低示波器刷新率以保持流畅？") }
        }
    }

    init {
        application.getSystemService(PowerManager::class.java).addThermalStatusListener(thermalListener)
    }

    fun connect() {
        if (state.value.connection in setOf(ConnectionState.CONNECTING, ConnectionState.HANDSHAKING, ConnectionState.STREAMING)) return
        mutableState.update { it.copy(connection = ConnectionState.CONNECTING, connectionMessage = "正在连接 Luna Ultra", error = null) }
        startCollector()
        viewModelScope.launch {
            disconnectJob?.join()
            disconnectJob = null
            runCatching {
                mutableState.update { it.copy(connection = ConnectionState.HANDSHAKING, connectionMessage = "正在建立图传会话") }
                withContext(Dispatchers.IO) { session.connect() }
                mutableState.update {
                    if (it.connection == ConnectionState.STREAMING) it
                    else it.copy(connection = ConnectionState.WAITING_VIDEO, connectionMessage = "已连接，等待实时画面")
                }
                runCatching { withContext(Dispatchers.IO) { session.cameraMode() } }.getOrNull()?.let { mode ->
                    mutableState.update { it.copy(captureMode = mode) }
                }
                runCatching { withContext(Dispatchers.IO) { session.captureStatus() } }.getOrNull()?.let { status ->
                    mutableState.update { it.copy(recording = status.recording && it.captureMode == CaptureMode.VIDEO) }
                }
                if (state.value.captureMode == CaptureMode.VIDEO) {
                    runCatching { withContext(Dispatchers.IO) { session.videoResolution() } }.getOrNull()?.let { code ->
                        mutableState.update { it.copy(videoFormat = VIDEO_FORMATS.firstOrNull { option -> option.resolutionCode == code }) }
                    }
                    runCatching { withContext(Dispatchers.IO) { session.videoColorMode() } }.getOrNull()?.let { mode ->
                        mutableState.update { it.copy(cameraColorMode = mode) }
                    }
                }
                startWatchdog()
            }.onFailure { error ->
                streamJob?.cancel(); streamJob = null
                session.forceClose()
                mutableState.update { it.copy(connection = ConnectionState.ERROR, connectionMessage = "连接失败", error = error.message ?: "未知错误") }
            }
        }
    }

    fun disconnect() {
        if (state.value.connection == ConnectionState.IDLE) return
        watchdogJob?.cancel(); watchdogJob = null
        streamJob?.cancel(); streamJob = null
        zoomJob?.cancel(); zoomJob = null
        decoder.resetCodec(); assembler.reset()
        lastAppliedZoom = Float.NaN
        bytes.set(0); frames.set(0); packets.set(0)

        // Return to the main page synchronously. Socket shutdown continues in IO
        // and cannot leave a close-related exception attached to the UI coroutine.
        mutableState.update {
            MonitorUiState(
                language = it.language,
                adjustments = it.adjustments,
                scaleMode = it.scaleMode,
                controlsSide = it.controlsSide,
                guideWidth = it.guideWidth,
                guideOpacity = it.guideOpacity,
                centerMarkSize = it.centerMarkSize,
                selectedVideoSize = it.selectedVideoSize,
                recoveryLut = it.recoveryLut,
                styleLut = it.styleLut,
            )
        }
        disconnectJob?.cancel()
        disconnectJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { session.disconnect() }.onFailure { session.forceClose() }
        }
    }

    fun attachDecoderSurface(surface: Surface) {
        if (decoderSurface === surface) return
        decoderSurface = surface
        runCatching { decoder.attachSurface(surface) }.onFailure { error ->
            mutableState.update { it.copy(error = "解码器初始化失败：${error.message}") }
        }
    }

    private fun startCollector() {
        streamJob?.cancel()
        startedAt = System.nanoTime(); bytes.set(0); frames.set(0); packets.set(0)
        assembler.reset()
        streamJob = viewModelScope.launch(Dispatchers.Default) {
            session.media.collect { media ->
                bytes.addAndGet(media.size.toLong())
                val packetCount = packets.incrementAndGet()
                if (packetCount == 1L) {
                    mutableState.update { it.copy(connectionMessage = "已收到视频码流，正在同步关键帧") }
                }
                val accessUnits = assembler.append(media)
                assembler.codec?.let { selected ->
                    if (state.value.streamStats.codec != selected.label) {
                        mutableState.update { current ->
                            current.copy(
                                connectionMessage = "已识别 ${selected.label}，正在等待关键帧",
                                streamStats = current.streamStats.copy(codec = selected.label),
                            )
                        }
                    }
                }
                var queuedAny = false
                accessUnits.forEach { unit ->
                    if (decoder.queue(unit)) {
                        queuedAny = true
                    }
                }
                val decodedFrames = frames.get()
                if (decodedFrames > 0L) {
                    val elapsed = ((System.nanoTime() - startedAt) / 1_000_000_000.0).coerceAtLeast(0.001)
                    mutableState.update { current ->
                        current.copy(
                            connection = ConnectionState.STREAMING,
                            connectionMessage = "实时监看",
                            streamStats = current.streamStats.copy(
                                fps = (decodedFrames / elapsed).toFloat(),
                                bitrateKbps = (bytes.get() * 8 / elapsed / 1000).toInt(),
                                mediaFrames = decodedFrames,
                                mediaPackets = packetCount,
                            ),
                        )
                    }
                } else if (queuedAny) {
                    mutableState.update { it.copy(connectionMessage = "${assembler.codec?.label ?: "视频"} 已送入硬件解码器") }
                } else if (accessUnits.isNotEmpty()) {
                    mutableState.update { it.copy(connectionMessage = "已收到完整视频帧，正在启动硬件解码") }
                }
            }
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch(Dispatchers.IO) {
            delay(4_500)
            if (frames.get() == 0L && state.value.connection !in setOf(ConnectionState.IDLE, ConnectionState.ERROR)) {
                val hadPackets = packets.get() > 0L
                if (!hadPackets) {
                    mutableState.update { it.copy(connectionMessage = "未收到码流，正在重新请求图传") }
                    val retry = runCatching { session.restartLiveStream() }
                    if (retry.isFailure) {
                        mutableState.update { it.copy(error = "重新请求图传失败：${retry.exceptionOrNull()?.message}") }
                        return@launch
                    }
                } else {
                    val codec = assembler.codec?.label ?: "未知编码"
                    mutableState.update { it.copy(connectionMessage = "已收到 $codec 码流，正在等待首个关键帧") }
                }
                delay(4_500)
                if (frames.get() == 0L && state.value.connection !in setOf(ConnectionState.IDLE, ConnectionState.ERROR)) {
                    mutableState.update {
                        it.copy(error = if (packets.get() == 0L)
                            "相机已连接，但未返回视频码流。请关闭其他已连接的 Insta360 App 后重连。"
                        else {
                            val codec = assembler.codec?.label ?: "未识别编码"
                            val detail = decoder.lastErrorMessage()?.let { message -> "；解码器：$message" } ?: ""
                            "已收到 ${packets.get()} 个媒体包（$codec），但尚未获得可解码关键帧$detail。"
                        })
                    }
                }
            }
        }
    }

    fun selectSection(section: ToolSection) = mutableState.update {
        it.copy(selectedSection = if (it.selectedSection == section) null else section, controlsExpanded = false)
    }
    fun setScaleMode(mode: ScaleMode) = mutableState.update { it.copy(scaleMode = mode) }
    fun setAdjustments(value: ColorAdjustments) = mutableState.update { it.copy(adjustments = value) }
    fun toggleControls() = mutableState.update {
        if (it.selectedSection != null) it else it.copy(controlsExpanded = !it.controlsExpanded)
    }
    fun setControlsSide(side: DrawerSide) = mutableState.update {
        if (it.controlsExpanded) it else it.copy(controlsSide = side)
    }

    fun triggerCapture() {
        val current = state.value
        when (current.captureMode) {
            CaptureMode.PHOTO -> runCameraControl("正在拍照") {
                session.takePicture()
                mutableState.update { it.copy(cameraControlMessage = "照片已拍摄") }
            }
            CaptureMode.VIDEO -> runCameraControl(if (current.recording) "正在结束录制" else "正在开始录制") {
                val status = if (state.value.recording) session.stopRecording() else session.startRecording()
                val recording = status?.recording ?: !state.value.recording
                mutableState.update { it.copy(recording = recording, cameraControlMessage = if (recording) "相机正在录制" else "录制已结束") }
            }
            CaptureMode.UNKNOWN -> refreshCameraMode()
        }
    }

    fun switchCaptureMode() {
        if (state.value.recording) return
        val target = if (state.value.captureMode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
        runCameraControl(if (target == CaptureMode.PHOTO) "正在切换到拍照" else "正在切换到视频") {
            val confirmed = session.setCameraMode(target)
            mutableState.update {
                it.copy(captureMode = confirmed, recording = false, cameraControlMessage = if (confirmed == CaptureMode.PHOTO) "拍照模式" else "视频模式")
            }
            if (confirmed == CaptureMode.VIDEO) {
                session.videoColorMode()?.let { mode -> mutableState.update { it.copy(cameraColorMode = mode) } }
            }
        }
    }

    fun refreshCameraMode() = runCameraControl("正在读取拍摄模式") {
        val mode = session.cameraMode()
        mutableState.update { it.copy(captureMode = mode, cameraControlMessage = if (mode == CaptureMode.UNKNOWN) "尚未识别拍摄模式" else if (mode == CaptureMode.PHOTO) "拍照模式" else "视频模式") }
    }

    fun setVideoFormat(option: VideoFormatOption) = runCameraControl("正在设置 ${option.label}") {
        if (VideoFormatPolicy.requiresVideoMode(state.value.captureMode)) {
            val mode = session.setCameraMode(CaptureMode.VIDEO)
            check(mode == CaptureMode.VIDEO) { "相机未切换到视频模式" }
            mutableState.update { it.copy(captureMode = mode, recording = false) }
            session.videoColorMode()?.let { colorMode -> mutableState.update { it.copy(cameraColorMode = colorMode) } }
        }
        val confirmed = session.setVideoResolution(option.resolutionCode)
        mutableState.update {
            it.copy(videoFormat = VIDEO_FORMATS.first { item -> item.resolutionCode == confirmed }, cameraControlMessage = "视频规格 ${option.label}")
        }
    }

    fun setVideoColorMode(mode: CameraColorMode) {
        val current = state.value
        if (!VideoFormatPolicy.canSetColorMode(current.connection, current.captureMode, current.cameraControlBusy, current.recording)) {
            mutableState.update { it.copy(cameraControlMessage = "色彩模式仅可在未录制的视频模式下设置") }
            return
        }
        val name = when (mode) {
            CameraColorMode.STANDARD -> "标准"
            CameraColorMode.DOLBY_VISION -> "杜比视界"
            CameraColorMode.I_LOG -> "I-Log"
        }
        runCameraControl("正在设置色彩模式 $name") {
            val latest = state.value
            check(VideoFormatPolicy.canSetColorMode(latest.connection, latest.captureMode, busy = false, recording = latest.recording)) {
                "色彩模式仅可在未录制的视频模式下设置"
            }
            val confirmed = session.setVideoColorMode(mode)
            mutableState.update { it.copy(cameraColorMode = confirmed, cameraControlMessage = "色彩模式 $name") }
        }
    }

    fun setLanguage(language: AppLanguage) = mutableState.update { it.copy(language = language) }
    fun selectVideoSize(size: String) = mutableState.update { it.copy(selectedVideoSize = size) }
    fun setRecoveryLut(lut: CubeLut?) = mutableState.update { it.copy(recoveryLut = lut, lutMessage = null) }
    fun setStyleLut(lut: CubeLut?) = mutableState.update { it.copy(styleLut = lut, lutMessage = null) }
    fun setLutError(message: String) = mutableState.update { it.copy(lutMessage = message) }

    fun previewZoom(value: Float) {
        val safe = value.coerceIn(1f, 12f)
        mutableState.update { it.copy(zoom = safe) }
    }

    fun commitZoom() {
        zoomJob?.cancel()
        disconnectJob?.cancel()
        val selected = state.value.zoom
        if (lastAppliedZoom.isFinite() && kotlin.math.abs(lastAppliedZoom - selected) < 0.01f) return
        val revision = zoomRevision.incrementAndGet()
        // A GET_PHOTOGRAPHY_OPTIONS read-back immediately after SET makes some
        // Luna firmware pause the live stream. The UI already commits only on
        // pointer release, so send one coalesced SET and keep media reads free.
        zoomJob = viewModelScope.launch { applyZoom(selected, verify = false, revision) }
    }

    fun stepZoom(delta: Float) {
        mutableState.update { it.copy(zoom = (it.zoom + delta).coerceIn(1f, 12f)) }
        commitZoom()
    }

    fun setZoomPreset(value: Float) {
        zoomJob?.cancel()
        mutableState.update { it.copy(zoom = value.coerceIn(1f, 12f)) }
        commitZoom()
    }

    private suspend fun applyZoom(value: Float, verify: Boolean, revision: Long) {
        if (state.value.connection !in setOf(ConnectionState.STREAMING, ConnectionState.WAITING_VIDEO)) return
        runCatching { withContext(Dispatchers.IO) { session.setZoom(value.toDouble(), verify, state.value.captureMode) } }
            .onSuccess {
                if (zoomRevision.get() == revision) mutableState.update { current ->
                    lastAppliedZoom = value
                    current.copy(cameraControlMessage = "变焦已设为 %.1f×".format(value))
                }
            }
            .onFailure { error ->
                if (zoomRevision.get() == revision) mutableState.update { it.copy(cameraControlMessage = "变焦失败：${error.message}") }
            }
    }

    private fun runCameraControl(progress: String, block: suspend () -> Unit) {
        if (state.value.cameraControlBusy || state.value.connection !in setOf(ConnectionState.STREAMING, ConnectionState.WAITING_VIDEO)) return
        viewModelScope.launch {
            mutableState.update { it.copy(cameraControlBusy = true, cameraControlMessage = progress) }
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onFailure { error -> mutableState.update { it.copy(cameraControlMessage = "控制失败：${error.message}") } }
            mutableState.update { it.copy(cameraControlBusy = false) }
        }
    }
    fun toggleCleanView() = mutableState.update { it.copy(cleanView = !it.cleanView, selectedSection = null) }
    fun toggleThirds() = mutableState.update { it.copy(showThirds = !it.showThirds) }
    fun toggleCenter() = mutableState.update { it.copy(showCenter = !it.showCenter) }
    fun toggleSafeArea() = mutableState.update { it.copy(showSafeArea = !it.showSafeArea) }
    fun setGuideWidth(value: Float) = mutableState.update { it.copy(guideWidth = value.coerceIn(1f, 6f)) }
    fun setGuideOpacity(value: Float) = mutableState.update { it.copy(guideOpacity = value.coerceIn(0.2f, 1f)) }
    fun setCenterMarkSize(value: Float) = mutableState.update { it.copy(centerMarkSize = value.coerceIn(12f, 96f)) }
    fun toggleZebra() = mutableState.update { it.copy(zebra = !it.zebra, falseColor = false) }
    fun toggleFalseColor() = mutableState.update { it.copy(falseColor = !it.falseColor, zebra = false) }
    fun togglePeaking() = mutableState.update { it.copy(peaking = !it.peaking) }
    fun acceptThermalReduction() = mutableState.update { it.copy(thermalSuggestion = null, thermalReductionAccepted = true) }
    fun dismissThermalSuggestion() = mutableState.update { it.copy(thermalSuggestion = null) }
    fun clearError() = mutableState.update { it.copy(error = null) }

    override fun onCleared() {
        getApplication<Application>().getSystemService(PowerManager::class.java).removeThermalStatusListener(thermalListener)
        streamJob?.cancel()
        watchdogJob?.cancel()
        zoomJob?.cancel()
        decoder.stop()
        session.forceClose()
        super.onCleared()
    }

    companion object {
        // Luna Ultra firmware values measured through RECORD_RESOLUTION (option 31).
        val VIDEO_FORMATS = listOf(
            VideoFormatOption("1080P 24", 53), VideoFormatOption("1080P 25", 52), VideoFormatOption("1080P 30", 29),
            VideoFormatOption("1080P 48", 260), VideoFormatOption("1080P 50", 81), VideoFormatOption("1080P 60", 40),
            VideoFormatOption("1080P 100", 150), VideoFormatOption("1080P 120", 28), VideoFormatOption("1080P 200", 26), VideoFormatOption("1080P 240", 27),
            VideoFormatOption("2.7K 24", 248), VideoFormatOption("2.7K 25", 247), VideoFormatOption("2.7K 30", 246),
            VideoFormatOption("2.7K 48", 331), VideoFormatOption("2.7K 50", 245), VideoFormatOption("2.7K 60", 244),
            VideoFormatOption("2.7K 100", 243), VideoFormatOption("2.7K 120", 242),
            VideoFormatOption("4K 24", 49), VideoFormatOption("4K 25", 48), VideoFormatOption("4K 30", 24), VideoFormatOption("4K 48", 258),
            VideoFormatOption("4K 50", 92), VideoFormatOption("4K 60", 23), VideoFormatOption("4K 100", 220), VideoFormatOption("4K 120", 214),
            VideoFormatOption("8K 24", 210), VideoFormatOption("8K 25", 211), VideoFormatOption("8K 30", 154),
        )
    }
}
