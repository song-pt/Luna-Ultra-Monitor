package com.lunamonitor.beta.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.lunamonitor.beta.protocol.Ucd2Frame
import com.lunamonitor.beta.protocol.Ucd2FrameScanner
import com.lunamonitor.beta.protocol.Ucd2Protocol
import com.lunamonitor.beta.protocol.CameraControlProtocol
import com.lunamonitor.beta.protocol.CaptureStatus
import com.lunamonitor.beta.model.CaptureMode
import com.lunamonitor.beta.model.CameraColorMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class LunaSession(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanner = Ucd2FrameScanner()
    private val mediaFlow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val media = mediaFlow.asSharedFlow()
    private var outgoing: Channel<ByteArray>? = null
    private val sequence = AtomicInteger(0x24)
    private val request = AtomicInteger(1)
    private val pendingResponses = ConcurrentHashMap<Int, kotlinx.coroutines.CompletableDeferred<Ucd2Frame.Response>>()
    private var socket: Socket? = null
    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private var keepaliveJob: Job? = null

    suspend fun connect(host: String = "192.168.42.1") {
        close()
        sequence.set(0x24)
        request.set(1)
        val network = findWifiNetwork() ?: error("请先在系统 Wi-Fi 设置中连接 Luna Ultra")
        val newSocket = withContext(Dispatchers.IO) {
            network.socketFactory.createSocket().apply {
                tcpNoDelay = true
                keepAlive = true
                connect(InetSocketAddress(host, Ucd2Protocol.CONTROL_PORT), 3_000)
            }
        }
        socket = newSocket
        val commandQueue = Channel<ByteArray>(Channel.BUFFERED)
        outgoing = commandQueue
        val input = BufferedInputStream(newSocket.getInputStream(), 32 * 1024)
        val output = BufferedOutputStream(newSocket.getOutputStream(), 32 * 1024)
        readerJob = scope.launch { readLoop(input) }
        writerJob = scope.launch {
            try {
                for (packet in commandQueue) {
                    output.write(packet)
                    output.flush()
                }
            } catch (_: CancellationException) {
                // A user-requested disconnect cancels this job before closing the socket.
            } catch (_: IOException) {
                // Closing the socket during disconnect is an expected writer termination.
            }
        }
        // Match the captured app handshake: hello, options, capture status,
        // then allow the camera session two seconds to become stream-ready.
        commandQueue.send(Ucd2Protocol.streamHello(nextSequence()))
        runCatching { sendExact(Ucd2Protocol.CODE_GET_OPTIONS, 1, Ucd2Protocol.smallOptionsBody(), 4_000) }
        runCatching { sendExact(Ucd2Protocol.CODE_GET_CURRENT_CAPTURE_STATUS, 2, ByteArray(0), 4_000) }
        delay(2_000)
        request.set(20)
        sendCommand(Ucd2Protocol.CODE_START_LIVE_STREAM, Ucd2Protocol.startLiveBody(), 5_000)
        keepaliveJob = scope.launch {
            while (isActive) {
                delay(3_000)
                outgoing?.send(Ucd2Protocol.streamHello(nextSequence())) ?: break
            }
        }
    }

    suspend fun restartLiveStream() {
        // Do not send STOP here: on the camera it visibly wakes/restarts the
        // preview screen. This retry is only used when no media arrived.
        outgoing?.send(Ucd2Protocol.streamHello(nextSequence())) ?: error("相机连接已经断开")
        delay(350)
        sendCommand(Ucd2Protocol.CODE_START_LIVE_STREAM, Ucd2Protocol.startLiveBody(), 5_000)
    }

    suspend fun captureStatus(): CaptureStatus? = CameraControlProtocol.parseCaptureStatus(
        sendCommand(Ucd2Protocol.CODE_GET_CURRENT_CAPTURE_STATUS, ByteArray(0), 2_500).body,
    )

    suspend fun cameraMode(): CaptureMode = CameraControlProtocol.parseCameraMode(
        sendCommand(Ucd2Protocol.CODE_GET_OPTIONS, CameraControlProtocol.getCameraModeBody(), 2_500).body,
    )

    suspend fun setCameraMode(mode: CaptureMode): CaptureMode {
        sendCommand(
            Ucd2Protocol.CODE_SET_OPTIONS,
            CameraControlProtocol.setCameraModeBody(mode),
            3_500,
        )
        delay(350)
        val detected = cameraMode()
        check(detected == mode) { "相机未切换到目标模式" }
        return detected
    }

    suspend fun startRecording(): CaptureStatus? {
        sendCommand(Ucd2Protocol.CODE_START_CAPTURE, CameraControlProtocol.startRecordingBody(), 3_500)
        delay(250)
        return captureStatus()
    }

    suspend fun stopRecording(): CaptureStatus? {
        sendCommand(Ucd2Protocol.CODE_STOP_CAPTURE, CameraControlProtocol.stopRecordingBody(), 3_500)
        delay(250)
        return captureStatus()
    }

    suspend fun takePicture() {
        sendCommand(Ucd2Protocol.CODE_TAKE_PICTURE, CameraControlProtocol.takePictureBody(), 4_000)
    }

    suspend fun setZoom(scale: Double, verify: Boolean, mode: CaptureMode): Double {
        val functionMode = if (mode == CaptureMode.PHOTO) CameraControlProtocol.NORMAL_IMAGE_MODE else CameraControlProtocol.NORMAL_VIDEO_MODE
        val response = sendCommand(
            Ucd2Protocol.CODE_SET_PHOTOGRAPHY_OPTIONS,
            CameraControlProtocol.setZoomBody(scale, functionMode),
            2_500,
        )
        check(response.body.isEmpty() || CameraControlProtocol.setZoomAccepted(response.body)) { "相机未接受变焦设置" }
        if (!verify) return scale.coerceIn(1.0, 12.0)
        val readBack = sendCommand(
            Ucd2Protocol.CODE_GET_PHOTOGRAPHY_OPTIONS,
            CameraControlProtocol.getZoomBody(functionMode),
            2_500,
        )
        return CameraControlProtocol.parseZoom(readBack.body) ?: scale.coerceIn(1.0, 12.0)
    }

    suspend fun videoResolution(): Int? = CameraControlProtocol.parseVideoResolution(
        sendCommand(
            Ucd2Protocol.CODE_GET_PHOTOGRAPHY_OPTIONS,
            CameraControlProtocol.getVideoResolutionBody(),
            2_500,
        ).body,
    )

    suspend fun setVideoResolution(resolutionCode: Int): Int {
        sendCommand(
            Ucd2Protocol.CODE_SET_PHOTOGRAPHY_OPTIONS,
            CameraControlProtocol.setVideoResolutionBody(resolutionCode),
            3_500,
        )
        delay(350)
        val readBack = videoResolution()
        check(readBack == resolutionCode) { "相机返回的视频规格与所选值不一致" }
        return readBack
    }

    suspend fun videoColorMode(): CameraColorMode? = CameraControlProtocol.parseVideoColorMode(
        sendCommand(
            Ucd2Protocol.CODE_GET_PHOTOGRAPHY_OPTIONS,
            CameraControlProtocol.getVideoColorModeBody(),
            2_500,
        ).body,
    )

    suspend fun setVideoColorMode(mode: CameraColorMode): CameraColorMode {
        sendCommand(
            Ucd2Protocol.CODE_SET_PHOTOGRAPHY_OPTIONS,
            CameraControlProtocol.setVideoColorModeBody(mode),
            3_500,
        )
        delay(350)
        val readBack = videoColorMode()
        check(readBack == mode) { "相机返回的色彩模式与所选值不一致" }
        return readBack
    }

    suspend fun disconnect() {
        runCatching { sendCommand(Ucd2Protocol.CODE_STOP_LIVE_STREAM, ByteArray(0), 1_000) }
        close()
    }

    private suspend fun sendCommand(code: Int, body: ByteArray, timeoutMs: Long): Ucd2Frame.Response {
        val id = request.getAndIncrement() and 0xffff
        return sendExact(code, id, body, timeoutMs)
    }

    private suspend fun sendExact(code: Int, id: Int, body: ByteArray, timeoutMs: Long): Ucd2Frame.Response {
        val deferred = kotlinx.coroutines.CompletableDeferred<Ucd2Frame.Response>()
        pendingResponses[id] = deferred
        val queue = outgoing ?: error("相机连接尚未就绪")
        queue.send(Ucd2Protocol.fileCommand(nextSequence(), code, id, body))
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pendingResponses.remove(id)
        }
    }

    private suspend fun readLoop(input: BufferedInputStream) {
        val chunk = ByteArray(16 * 1024)
        try {
            while (coroutineContext.isActive) {
                val count = input.read(chunk)
                if (count <= 0) break
                scanner.append(chunk.copyOf(count)).forEach { frame ->
                    when (frame) {
                        is Ucd2Frame.Response -> pendingResponses.remove(frame.requestId)?.complete(frame)
                        is Ucd2Frame.Media -> if (frame.substream == Ucd2Protocol.MEDIA_VIDEO) mediaFlow.tryEmit(frame.data)
                        Ucd2Frame.KeepAlive -> Unit
                    }
                }
            }
        } catch (_: CancellationException) {
        } catch (_: IOException) {
            // Socket.close() wakes a blocked read with IOException during normal disconnect.
        } finally {
            pendingResponses.values.forEach { it.cancel() }
            pendingResponses.clear()
        }
    }

    private fun findWifiNetwork(): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun nextSequence(): Int = sequence.getAndIncrement() and 0xff

    private fun close() {
        keepaliveJob?.cancel()
        keepaliveJob = null
        readerJob?.cancel()
        writerJob?.cancel()
        outgoing?.close()
        outgoing = null
        runCatching { socket?.close() }
        socket = null
        scanner.reset()
    }

    fun forceClose() = close()
}
