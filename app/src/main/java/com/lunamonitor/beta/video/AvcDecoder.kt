package com.lunamonitor.beta.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.lunamonitor.beta.stream.AccessUnit
import com.lunamonitor.beta.stream.VideoCodec
import java.util.concurrent.atomic.AtomicLong

/** Surface decoder that configures itself from the codec detected on the wire. */
class StreamDecoder(
    private val onFormatChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onFrameDecoded: (Long) -> Unit = {},
) {
    private val outputThread = HandlerThread("LunaDecodeOutput").apply { start() }
    private val outputHandler = Handler(outputThread.looper)
    private val pump = object : Runnable {
        override fun run() {
            synchronized(this@StreamDecoder) {
                codec?.let { decoder ->
                    runCatching { drain(decoder) }.onFailure { lastError = it.message; releaseCodec() }
                    if (codec != null) outputHandler.postDelayed(this, 4)
                }
            }
        }
    }
    init { outputHandler.post(pump) }
    private var codec: MediaCodec? = null
    private var activeCodec: VideoCodec? = null
    private var surface: Surface? = null
    private var lastError: String? = null
    private val presentation = AtomicLong(0)
    private val arrivals = mutableMapOf<Long, Long>()

    @Synchronized
    fun attachSurface(value: Surface) {
        if (surface === value) return
        releaseCodec()
        surface = value
    }

    @Synchronized
    fun detectedCodecLabel(): String? = activeCodec?.label

    @Synchronized
    fun lastErrorMessage(): String? = lastError

    @Synchronized
    fun queue(unit: AccessUnit): Boolean {
        val targetSurface = surface ?: return false
        return try {
            if (codec == null || activeCodec != unit.codec) {
                // Starting from a delta frame yields a permanent black decoder on
                // several Qualcomm implementations. Wait for VPS/SPS/PPS + IRAP/IDR.
                if (!unit.keyFrame) return false
                configure(targetSurface, unit.codec)
            }
            val decoder = codec ?: return false
            val index = decoder.dequeueInputBuffer(0)
            if (index < 0) {
                // Missing a reference AU cannot be hidden; resync at a complete keyframe.
                releaseCodec()
                return false
            }
            val buffer = decoder.getInputBuffer(index)
            if (buffer == null || buffer.capacity() < unit.bytes.size) {
                decoder.queueInputBuffer(index, 0, 0, presentation.get(), 0)
                lastError = "输入帧 ${unit.bytes.size}B 超过解码缓冲区 ${buffer?.capacity() ?: 0}B"
                return false
            }
            buffer.clear()
            buffer.put(unit.bytes)
            val pts = System.nanoTime() / 1000L
            arrivals[pts] = unit.receivedAtNs.takeIf { it > 0 } ?: System.nanoTime()
            decoder.queueInputBuffer(
                index, 0, unit.bytes.size, pts,
                if (unit.keyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
            )
            drain(decoder)
            true
        } catch (error: Throwable) {
            lastError = "${unit.codec.label}：${error.message ?: error.javaClass.simpleName}"
            releaseCodec()
            false
        }
    }

    private fun configure(targetSurface: Surface, selected: VideoCodec) {
        releaseCodec()
        val format = MediaFormat.createVideoFormat(selected.mime, 1280, 720).apply {
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, 30)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024)
        }
        val decoder = MediaCodec.createDecoderByType(selected.mime)
        if (Build.VERSION.SDK_INT >= 30) {
            val capabilities = decoder.codecInfo.getCapabilitiesForType(selected.mime)
            if (capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }
        decoder.configure(format, targetSurface, null, 0)
        decoder.start()
        codec = decoder
        outputHandler.removeCallbacks(pump)
        outputHandler.post(pump)
        activeCodec = selected
        presentation.set(0)
        lastError = null
    }

    private fun drain(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var latest = -1
        var latestArrival = 0L
        while (true) {
            val output = decoder.dequeueOutputBuffer(info, 0)
            when {
                output >= 0 -> {
                    if (latest >= 0) decoder.releaseOutputBuffer(latest, false)
                    latest = output
                    latestArrival = arrivals.remove(info.presentationTimeUs) ?: System.nanoTime()
                }
                output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = decoder.outputFormat
                    val codedWidth = format.getInteger(MediaFormat.KEY_WIDTH)
                    val codedHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
                    val width = if (format.containsKey("crop-right") && format.containsKey("crop-left"))
                        format.getInteger("crop-right") - format.getInteger("crop-left") + 1 else codedWidth
                    val height = if (format.containsKey("crop-bottom") && format.containsKey("crop-top"))
                        format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1 else codedHeight
                    if (width > 0 && height > 0) onFormatChanged(width, height)
                }
                else -> {
                    if (latest >= 0) { decoder.releaseOutputBuffer(latest, true); onFrameDecoded((System.nanoTime()-latestArrival).coerceAtLeast(0L)) }
                    return
                }
            }
        }
    }

    @Synchronized
    fun resetCodec() = releaseCodec()

    @Synchronized
    fun stop() {
        releaseCodec()
        surface = null
        outputHandler.removeCallbacksAndMessages(null)
        outputThread.quitSafely()
    }

    private fun releaseCodec() {
        outputHandler.removeCallbacks(pump)
        val decoder = codec
        arrivals.clear()
        codec = null
        activeCodec = null
        if (decoder != null) {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
        }
    }
}
