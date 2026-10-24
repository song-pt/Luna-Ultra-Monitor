package com.lunamonitor.beta.protocol

import com.lunamonitor.beta.model.CaptureMode
import com.lunamonitor.beta.model.CameraColorMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CaptureStatus(val state: Int, val captureTimeSeconds: Int) {
    val recording: Boolean get() = state != 0 && state != 8
}

object CameraControlProtocol {
    fun parseFirmware(body: ByteArray): String? = ProtoReader(body).lengthDelimited(2)
        ?.let { ProtoReader(it).lengthDelimited(30)?.toString(Charsets.UTF_8) }

    const val ZOOM_SCALE = 53
    const val COLOR_MODE = 35
    const val NORMAL_VIDEO_MODE = 7
    const val NORMAL_IMAGE_MODE = 6
    private const val PHOTO_SUB_MODE = 40
    private const val VIDEO_SUB_MODE = 41

    fun startRecordingBody(): ByteArray = byteArrayOf(0x08, 0x01)
    fun stopRecordingBody(): ByteArray = byteArrayOf(0x10, 0x01)
    fun takePictureBody(): ByteArray = ByteArray(0)

    fun getCameraModeBody(): ByteArray = byteArrayOf(0x08, PHOTO_SUB_MODE.toByte(), 0x08, VIDEO_SUB_MODE.toByte())

    fun setCameraModeBody(mode: CaptureMode): ByteArray {
        val option = when (mode) {
            CaptureMode.PHOTO -> PHOTO_SUB_MODE
            CaptureMode.VIDEO -> VIDEO_SUB_MODE
            CaptureMode.UNKNOWN -> error("目标拍摄模式未指定")
        }
        // PHOTO_SINGLE and VIDEO_NORMAL are both protobuf enum value 0, so the
        // nested Options message is canonically empty; option_types selects the field.
        return byteArrayOf(0x08, option.toByte(), 0x12, 0x00)
    }

    fun cameraModeAccepted(body: ByteArray, mode: CaptureMode): Boolean {
        if (body.isEmpty()) return false
        val expected = if (mode == CaptureMode.PHOTO) PHOTO_SUB_MODE else VIDEO_SUB_MODE
        return ProtoReader(body).allVarints(1).any { it == expected.toLong() }
    }

    fun parseCameraMode(body: ByteArray): CaptureMode {
        val options = ProtoReader(body).lengthDelimited(2) ?: return CaptureMode.UNKNOWN
        val reader = ProtoReader(options)
        val photo = reader.varint(PHOTO_SUB_MODE)?.toInt()
        val video = reader.varint(VIDEO_SUB_MODE)?.toInt()
        return when {
            video != null && video != 100 -> CaptureMode.VIDEO
            photo != null && photo != 100 -> CaptureMode.PHOTO
            video == 100 && photo == null -> CaptureMode.PHOTO
            photo == 100 && video == null -> CaptureMode.VIDEO
            else -> CaptureMode.UNKNOWN
        }
    }

    fun getZoomBody(functionMode: Int = NORMAL_VIDEO_MODE): ByteArray = byteArrayOf(
        0x08, ZOOM_SCALE.toByte(), 0x10, functionMode.toByte(),
    )

    fun setZoomBody(scale: Double, functionMode: Int = NORMAL_VIDEO_MODE): ByteArray {
        val safeScale = scale.coerceIn(1.0, 12.0)
        val option = ByteArrayOutputStream().apply {
            writeVarint((ZOOM_SCALE shl 3) or 1)
            write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(safeScale).array())
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeVarint((1 shl 3) or 0); writeVarint(ZOOM_SCALE)
            writeVarint((2 shl 3) or 2); writeVarint(option.size); write(option)
            writeVarint((3 shl 3) or 0); writeVarint(functionMode)
        }.toByteArray()
    }

    fun parseCaptureStatus(body: ByteArray): CaptureStatus? {
        val outer = ProtoReader(body).lengthDelimited(1) ?: return null
        val reader = ProtoReader(outer)
        return CaptureStatus(reader.varint(1)?.toInt() ?: 0, reader.varint(2)?.toInt() ?: 0)
    }

    fun parseZoom(body: ByteArray): Double? {
        val options = ProtoReader(body).lengthDelimited(2) ?: return null
        return ProtoReader(options).fixed64(ZOOM_SCALE)?.let(Double::fromBits)?.takeIf { it in 1.0..12.0 }
    }

    fun setZoomAccepted(body: ByteArray): Boolean = ProtoReader(body).allVarints(1).any { it == ZOOM_SCALE.toLong() }

    fun getVideoResolutionBody(): ByteArray = byteArrayOf(0x08, 0x1f, 0x10, NORMAL_VIDEO_MODE.toByte())

    fun setVideoResolutionBody(resolutionCode: Int): ByteArray {
        require(resolutionCode >= 0)
        val option = ByteArrayOutputStream().apply {
            writeVarint((31 shl 3) or 0)
            writeVarint(resolutionCode)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeVarint((1 shl 3) or 0); writeVarint(31)
            writeVarint((2 shl 3) or 2); writeVarint(option.size); write(option)
            writeVarint((3 shl 3) or 0); writeVarint(NORMAL_VIDEO_MODE)
        }.toByteArray()
    }

    fun parseVideoResolution(body: ByteArray): Int? =
        ProtoReader(body).lengthDelimited(2)?.let { ProtoReader(it).varint(31)?.toInt() }

    fun getVideoColorModeBody(): ByteArray = byteArrayOf(0x08, COLOR_MODE.toByte(), 0x10, NORMAL_VIDEO_MODE.toByte())

    fun setVideoColorModeBody(mode: CameraColorMode): ByteArray {
        val option = ByteArrayOutputStream().apply {
            writeVarint((COLOR_MODE shl 3) or 0)
            writeVarint(mode.protocolValue)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeVarint((1 shl 3) or 0); writeVarint(COLOR_MODE)
            writeVarint((2 shl 3) or 2); writeVarint(option.size); write(option)
            writeVarint((3 shl 3) or 0); writeVarint(NORMAL_VIDEO_MODE)
        }.toByteArray()
    }

    fun parseVideoColorMode(body: ByteArray): CameraColorMode? {
        val value = ProtoReader(body).lengthDelimited(2)?.let { ProtoReader(it).varint(COLOR_MODE)?.toInt() }
        return CameraColorMode.entries.firstOrNull { it.protocolValue == value }
    }

    private fun ByteArrayOutputStream.writeVarint(value: Int) {
        var remaining = value.toLong()
        while (remaining >= 0x80) { write(((remaining and 0x7f) or 0x80).toInt()); remaining = remaining ushr 7 }
        write(remaining.toInt())
    }
}

private class ProtoReader(private val bytes: ByteArray) {
    fun varint(field: Int): Long? = fields().firstOrNull { it.number == field && it.wire == 0 }?.numberValue
    fun allVarints(field: Int): List<Long> = fields().filter { it.number == field && it.wire == 0 }.map { it.numberValue }
    fun lengthDelimited(field: Int): ByteArray? = fields().firstOrNull { it.number == field && it.wire == 2 }?.bytesValue
    fun fixed64(field: Int): Long? = fields().firstOrNull { it.number == field && it.wire == 1 }?.numberValue

    private fun fields(): List<Field> {
        val result = mutableListOf<Field>(); var at = 0
        while (at < bytes.size) {
            val tag = readVarint(at) ?: break; at = tag.second
            val number = (tag.first ushr 3).toInt(); val wire = (tag.first and 7).toInt()
            if (number == 0) break
            when (wire) {
                0 -> { val value = readVarint(at) ?: break; at = value.second; result += Field(number, wire, value.first) }
                1 -> { if (at + 8 > bytes.size) break; val value = ByteBuffer.wrap(bytes, at, 8).order(ByteOrder.LITTLE_ENDIAN).long; at += 8; result += Field(number, wire, value) }
                2 -> { val length = readVarint(at) ?: break; at = length.second; val end = at + length.first.toInt(); if (end !in at..bytes.size) break; result += Field(number, wire, bytesValue = bytes.copyOfRange(at, end)); at = end }
                5 -> { if (at + 4 > bytes.size) break; at += 4 }
                else -> break
            }
        }
        return result
    }

    private fun readVarint(start: Int): Pair<Long, Int>? {
        var result = 0L; var shift = 0; var at = start
        while (at < bytes.size && shift < 64) {
            val value = bytes[at++].toInt() and 0xff; result = result or ((value and 0x7f).toLong() shl shift)
            if (value and 0x80 == 0) return result to at
            shift += 7
        }
        return null
    }

    private data class Field(val number: Int, val wire: Int, val numberValue: Long = 0, val bytesValue: ByteArray? = null)
}
