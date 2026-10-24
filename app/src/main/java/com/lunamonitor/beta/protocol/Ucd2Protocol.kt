package com.lunamonitor.beta.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Ucd2Protocol {
    const val CONTROL_PORT = 6666
    const val TYPE_MEDIA = 0x01
    const val TYPE_FILE = 0x04
    const val TYPE_STREAM = 0x05
    const val MEDIA_VIDEO = 0x20
    const val CODE_START_LIVE_STREAM = 1
    const val CODE_STOP_LIVE_STREAM = 2
    const val CODE_TAKE_PICTURE = 3
    const val CODE_START_CAPTURE = 4
    const val CODE_STOP_CAPTURE = 5
    const val CODE_SET_OPTIONS = 7
    const val CODE_GET_OPTIONS = 8
    const val CODE_SET_PHOTOGRAPHY_OPTIONS = 9
    const val CODE_GET_PHOTOGRAPHY_OPTIONS = 10
    const val CODE_GET_CURRENT_CAPTURE_STATUS = 15
    const val MAX_DECLARED_SIZE = 8 * 1024 * 1024
    private val magic = byteArrayOf(0x55, 0x43, 0x44, 0x32)

    fun streamHello(sequence: Int): ByteArray = frame(
        TYPE_STREAM,
        sequence,
        byteArrayOf(0, 0, 0, 0, 0xf6.toByte(), 0xcc.toByte(), 0x4f, 0x09),
    )

    fun startLiveBody(): ByteArray = byteArrayOf(
        0x10, 0x01, 0x30, 0x28, 0x38, 0x09, 0x40, 0x01, 0x48, 0x28, 0x50, 0x12,
    )

    fun smallOptionsBody(): ByteArray = byteArrayOf(0x08, 0x30, 0x08, 0x0f, 0x08, 0x0b)

    fun fileCommand(sequence: Int, code: Int, requestId: Int, body: ByteArray): ByteArray {
        val raw = ByteBuffer.allocate(9 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(code.toShort()).put(0x02).putShort(requestId.toShort())
            .putInt(0x8000).put(body).array()
        val payload = ByteBuffer.allocate(4 + raw.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(raw.size).put(raw).array()
        val withoutChecksum = frame(TYPE_FILE, sequence, payload)
        return withoutChecksum + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(checksum(withoutChecksum)).array()
    }

    private fun frame(type: Int, sequence: Int, payload: ByteArray): ByteArray =
        magic + byteArrayOf(1, 0x0c, type.toByte(), sequence.toByte()) + payload

    internal fun checksum(data: ByteArray): Int {
        var value = -1
        val table = checksumTable
        data.forEach { byte ->
            value = value xor (byte.toInt() and 0xff)
            repeat(4) { value = (value shl 8) xor table[value ushr 24] }
        }
        return value
    }

    private val checksumTable: IntArray by lazy {
        IntArray(256) { index ->
            var value = index shl 24
            repeat(8) { value = if (value and Int.MIN_VALUE != 0) (value shl 1) xor 0x04c11db7 else value shl 1 }
            value
        }
    }
}

sealed interface Ucd2Frame {
    data class Response(val code: Int, val requestId: Int, val body: ByteArray) : Ucd2Frame
    data class Media(val substream: Int, val data: ByteArray, val receivedAtNs: Long = System.nanoTime()) : Ucd2Frame
    data object KeepAlive : Ucd2Frame
}

class Ucd2FrameScanner {
    private var pending = ByteArray(0)

    fun append(chunk: ByteArray): List<Ucd2Frame> {
        if (chunk.isNotEmpty()) pending += chunk
        val output = mutableListOf<Ucd2Frame>()
        var offset = 0
        while (true) {
            val start = findMagic(pending, offset)
            if (start < 0) {
                pending = pending.takeLast(minOf(3, pending.size)).toByteArray()
                return output
            }
            if (pending.size - start < 12) {
                pending = pending.copyOfRange(start, pending.size)
                return output
            }
            val type = pending[start + 6].toInt() and 0xff
            if (type !in setOf(Ucd2Protocol.TYPE_FILE, Ucd2Protocol.TYPE_STREAM, Ucd2Protocol.TYPE_MEDIA)) {
                offset = start + 4
                continue
            }
            val declared = littleEndianInt(pending, start + 8)
            if (declared < 0 || declared > Ucd2Protocol.MAX_DECLARED_SIZE) {
                offset = start + 8
                continue
            }
            val frameLength = 12L + declared + 4L
            if (frameLength > Int.MAX_VALUE || pending.size - start < frameLength.toInt()) {
                pending = pending.copyOfRange(start, pending.size)
                return output
            }
            val payloadStart = start + 12
            if (Ucd2Protocol.checksum(pending.copyOfRange(start, payloadStart + declared)) != littleEndianInt(pending, payloadStart + declared)) {
                pending = pending.copyOfRange(start + frameLength.toInt(), pending.size)
                offset = 0
                continue
            }
            when (type) {
                Ucd2Protocol.TYPE_STREAM -> output += Ucd2Frame.KeepAlive
                Ucd2Protocol.TYPE_MEDIA -> if (declared > 9) {
                    output += Ucd2Frame.Media(
                        pending[payloadStart].toInt() and 0xff,
                        pending.copyOfRange(payloadStart + 9, payloadStart + declared),
                    )
                }
                Ucd2Protocol.TYPE_FILE -> if (declared >= 9) {
                    output += Ucd2Frame.Response(
                        littleEndianShort(pending, payloadStart),
                        littleEndianShort(pending, payloadStart + 3),
                        pending.copyOfRange(payloadStart + 9, payloadStart + declared),
                    )
                }
            }
            offset = start + frameLength.toInt()
            if (offset >= pending.size) {
                pending = ByteArray(0)
                return output
            }
            pending = pending.copyOfRange(offset, pending.size)
            offset = 0
        }
    }

    fun reset() { pending = ByteArray(0) }

    private fun findMagic(bytes: ByteArray, from: Int): Int {
        if (bytes.size < 4 || from > bytes.size - 4) return -1
        for (i in from..bytes.size - 4) {
            if (bytes[i] == 0x55.toByte() && bytes[i + 1] == 0x43.toByte() &&
                bytes[i + 2] == 0x44.toByte() && bytes[i + 3] == 0x32.toByte()) return i
        }
        return -1
    }

    private fun littleEndianInt(bytes: ByteArray, at: Int): Int = ByteBuffer
        .wrap(bytes, at, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun littleEndianShort(bytes: ByteArray, at: Int): Int = ByteBuffer
        .wrap(bytes, at, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
}
