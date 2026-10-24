package com.lunamonitor.beta.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ucd2ProtocolTest {
    @Test fun startLiveBodyMatchesVerifiedCapture() {
        assertArrayEquals(
            hex("100130283809400148285012"),
            Ucd2Protocol.startLiveBody(),
        )
    }

    @Test fun getOptionsPacketMatchesCapturedFrame() {
        val built = Ucd2Protocol.fileCommand(
            sequence = 0x10,
            code = 8,
            requestId = 1,
            body = hex("0830080f080b"),
        )
        assertArrayEquals(hex("55434432010c04100f0000000800020100008000000830080f080b7c008e7c"), built)
    }

    @Test fun handshakeBodiesMatchCapturedProbe() {
        assertArrayEquals(hex("0830080f080b"), Ucd2Protocol.smallOptionsBody())
        assertEquals(15, Ucd2Protocol.CODE_GET_CURRENT_CAPTURE_STATUS)
        assertEquals(16, Ucd2Protocol.streamHello(0x24).size)
    }

    @Test fun scannerAcceptsEveryFragmentBoundary() {
        val packet = Ucd2Protocol.fileCommand(0x10, 8, 17, byteArrayOf(1, 2, 3))
        for (split in 1 until packet.size) {
            val scanner = Ucd2FrameScanner()
            assertTrue(scanner.append(packet.copyOfRange(0, split)).isEmpty())
            val frames = scanner.append(packet.copyOfRange(split, packet.size))
            assertEquals(1, frames.size)
            assertEquals(17, (frames.single() as Ucd2Frame.Response).requestId)
        }
    }

    @Test fun scannerRejectsAbsurdLengthWithoutAllocating() {
        val bad = hex("55434432010c0400ffffffff") + ByteArray(8)
        assertTrue(Ucd2FrameScanner().append(bad).isEmpty())
    }

    @Test fun corruptedChecksumNeverReachesDecoder() {
        val packet=Ucd2Protocol.fileCommand(1,226,20,GimbalProtocol.move(0,0))
        packet[packet.lastIndex]=(packet.last().toInt() xor 1).toByte()
        assertTrue(Ucd2FrameScanner().append(packet).isEmpty())
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
