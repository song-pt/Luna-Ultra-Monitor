package com.lunamonitor.beta.stream

enum class VideoCodec(val mime: String, val label: String) {
    AVC("video/avc", "H.264"),
    HEVC("video/hevc", "H.265/HEVC"),
}

data class AccessUnit(val bytes: ByteArray, val keyFrame: Boolean, val codec: VideoCodec, val receivedAtNs: Long = 0)

/** Incremental Annex-B splitter with on-wire H.264/H.265 detection. */
class AnnexBAssembler {
    private var bytes = ByteArray(0)
    private val undecidedNals = mutableListOf<ByteArray>()
    private val pendingNals = mutableListOf<ByteArray>()
    private var currentHasVcl = false
    private var currentKey = false
    var codec: VideoCodec? = null
        private set

    fun append(chunk: ByteArray): List<AccessUnit> {
        if (chunk.isNotEmpty()) bytes += chunk
        val starts = startCodes(bytes)
        if (starts.size < 2) return emptyList()
        val output = mutableListOf<AccessUnit>()
        for (index in 0 until starts.lastIndex) {
            val payloadStart = starts[index].first + starts[index].second
            val payloadEnd = starts[index + 1].first
            if (payloadEnd > payloadStart) consumeNal(bytes.copyOfRange(payloadStart, payloadEnd), output)
        }
        bytes = bytes.copyOfRange(starts.last().first, bytes.size)
        return output
    }

    fun appendMediaPacket(chunk: ByteArray, receivedAtNs: Long = System.nanoTime()): List<AccessUnit> {
        val output = append(chunk).toMutableList()
        val starts = startCodes(bytes)
        if (starts.size == 1 && bytes.size > starts[0].second) {
            consumeNal(bytes.copyOfRange(starts[0].second, bytes.size), output)
            bytes = ByteArray(0)
        }
        codec?.let { flush(output, it) }
        return output.map { it.copy(receivedAtNs = receivedAtNs) }
    }

    fun reset() {
        bytes = ByteArray(0)
        undecidedNals.clear()
        pendingNals.clear()
        currentHasVcl = false
        currentKey = false
        codec = null
    }

    private fun consumeNal(nal: ByteArray, output: MutableList<AccessUnit>) {
        if (nal.isEmpty()) return
        val selected = codec
        if (selected == null) {
            undecidedNals += nal
            codec = detectCodec(nal) ?: return
            val buffered = undecidedNals.toList()
            undecidedNals.clear()
            buffered.forEach { consumeKnown(it, codec!!, output) }
            return
        }
        consumeKnown(nal, selected, output)
    }

    private fun consumeKnown(nal: ByteArray, selected: VideoCodec, output: MutableList<AccessUnit>) {
        val type = nalType(nal, selected)
        val isVcl = when (selected) {
            VideoCodec.AVC -> type == 1 || type == 5
            VideoCodec.HEVC -> type in 0..31
        }
        val headerSize = if (selected == VideoCodec.HEVC) 2 else 1
        val startsPicture = isVcl && nal.size > headerSize && (nal[headerSize].toInt() and 0x80) != 0
        val leadingForNextPicture = when (selected) {
            VideoCodec.AVC -> type in setOf(6, 7, 8, 9)
            VideoCodec.HEVC -> type in setOf(32, 33, 34, 35, 39, 40)
        }
        if (currentHasVcl && (startsPicture || leadingForNextPicture)) flush(output, selected)
        pendingNals += nal
        if (isVcl) currentHasVcl = true
        if (selected == VideoCodec.AVC && type == 5) currentKey = true
        if (selected == VideoCodec.HEVC && type in 16..23) currentKey = true
    }

    private fun flush(output: MutableList<AccessUnit>, selected: VideoCodec) {
        if (!currentHasVcl) return
        val start = byteArrayOf(0, 0, 0, 1)
        val size = pendingNals.sumOf { it.size + start.size }
        val joined = ByteArrayOutput(size)
        pendingNals.forEach { joined.add(start); joined.add(it) }
        output += AccessUnit(joined.toByteArray(), currentKey, selected)
        pendingNals.clear()
        currentHasVcl = false
        currentKey = false
    }

    private fun detectCodec(nal: ByteArray): VideoCodec? {
        if (nal.isEmpty() || (nal[0].toInt() and 0x80) != 0) return null
        val hevcType = (nal[0].toInt() ushr 1) and 0x3f
        // Luna's HEVC preview uses layer 0, whose low header bit is zero.
        // Requiring that bit avoids mistaking an H.264 0x41 delta slice for VPS 32.
        if (hevcType in 32..34 && (nal[0].toInt() and 1) == 0 && nal.size > 1) return VideoCodec.HEVC
        val avcType = nal[0].toInt() and 0x1f
        if (avcType == 7 || avcType == 8) return VideoCodec.AVC
        return null
    }

    private fun nalType(nal: ByteArray, selected: VideoCodec): Int = when (selected) {
        VideoCodec.AVC -> nal[0].toInt() and 0x1f
        VideoCodec.HEVC -> (nal[0].toInt() ushr 1) and 0x3f
    }

    private fun startCodes(data: ByteArray): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 3 < data.size) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) { result += i to 3; i += 3; continue }
                if (data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) { result += i to 4; i += 4; continue }
            }
            i++
        }
        return result
    }
}

private class ByteArrayOutput(private val size: Int) {
    private val target = ByteArray(size)
    private var offset = 0
    fun add(bytes: ByteArray) { bytes.copyInto(target, offset); offset += bytes.size }
    fun toByteArray(): ByteArray = target
}
