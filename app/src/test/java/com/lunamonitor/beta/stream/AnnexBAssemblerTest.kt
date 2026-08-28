package com.lunamonitor.beta.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnexBAssemblerTest {
    @Test fun preservesStartCodeAcrossChunksAndEmitsCompletedPictures() {
        val assembler = AnnexBAssembler()
        assertTrue(assembler.append(byteArrayOf(0, 0)).isEmpty())
        assertTrue(assembler.append(byteArrayOf(0, 1, 0x67, 1, 0, 0, 1, 0x65, 0x80.toByte(), 2)).isEmpty())
        val result = assembler.append(byteArrayOf(0, 0, 1, 0x41, 0x80.toByte(), 3, 0, 0, 1, 0x41, 0x80.toByte()))
        assertEquals(1, result.size)
        assertTrue(result.single().keyFrame)
        assertTrue(result.single().bytes.startsWith(byteArrayOf(0, 0, 0, 1)))
    }

    @Test fun parameterSetStartsTheFollowingAccessUnit() {
        val assembler = AnnexBAssembler()
        val stream = byteArrayOf(
            0, 0, 0, 1, 0x65, 0x80.toByte(), 1,
            0, 0, 0, 1, 0x67, 2,
            0, 0, 0, 1, 0x68, 3,
            0, 0, 0, 1, 0x65, 0x80.toByte(), 4,
            0, 0, 0, 1, 0x41, 0x80.toByte(), 5,
            0, 0, 0, 1, 0x09, 0,
        )
        val result = assembler.append(stream)
        assertEquals(2, result.size)
        assertTrue(result[0].keyFrame)
        assertTrue(result[1].keyFrame)
        // The second access unit starts with SPS (NAL type 7), not the prior VCL.
        assertEquals(7, result[1].bytes[4].toInt() and 0x1f)
    }

    @Test fun detectsAndGroupsHevcStream() {
        val assembler = AnnexBAssembler()
        val stream = annexB(
            byteArrayOf(0x40, 0x01, 0x0c), // VPS 32
            byteArrayOf(0x42, 0x01, 1, 2), // SPS 33
            byteArrayOf(0x44, 0x01, 3), // PPS 34
            byteArrayOf(0x26, 0x01, 0x80.toByte(), 4), // IDR 19
            byteArrayOf(0x02, 0x01, 0x80.toByte(), 5), // TRAIL 1
            byteArrayOf(0x46, 0x01, 0), // AUD 35
            byteArrayOf(0x40, 0x01, 0x0c),
        )
        val result = assembler.append(stream)
        assertEquals(VideoCodec.HEVC, assembler.codec)
        assertEquals(2, result.size)
        assertEquals(VideoCodec.HEVC, result[0].codec)
        assertTrue(result[0].keyFrame)
    }

    private fun annexB(vararg units: ByteArray): ByteArray {
        val start = byteArrayOf(0, 0, 0, 1)
        return units.fold(ByteArray(0)) { all, unit -> all + start + unit }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = prefix.indices.all { this[it] == prefix[it] }
}
