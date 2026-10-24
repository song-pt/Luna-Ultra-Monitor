package com.lunamonitor.beta.protocol
import org.junit.Assert.*
import org.junit.Test

class GimbalProtocolTest {
    @Test fun unsupportedFirmwareIsNotControlled() {
        assertFalse(GimbalProtocol.supportsFirmware(null))
        assertFalse(GimbalProtocol.supportsFirmware("unknown"))
        assertFalse(GimbalProtocol.supportsFirmware("1.0.238"))
        assertTrue(GimbalProtocol.supportsFirmware("v1.1.15"))
        assertTrue(GimbalProtocol.supportsFirmware("1.0.288"))
    }
    @Test fun releaseIsNotRecenter() {
        assertArrayEquals(byteArrayOf(8,1,18,0,32,4),GimbalProtocol.move(0,0))
        assertArrayEquals(byteArrayOf(8,2),GimbalProtocol.action(2))
        assertArrayEquals(byteArrayOf(8,3),GimbalProtocol.action(3))
    }
    @Test fun signedAxesUseZigzagRatherThanNegativeVarints() {
        assertArrayEquals(byteArrayOf(8,1,18,6,8,198.toByte(),1,16,197.toByte(),1,32,4),GimbalProtocol.move(-99,99))
    }
    @Test fun deadZoneAndNonFiniteInputsNeverMove() {
        assertEquals(0 to 0,GimbalProtocol.normalized(.02f,.02f))
        assertEquals(0 to 0,GimbalProtocol.normalized(Float.NaN,1f))
    }
    @Test fun diagonalIsCircularAndScreenUpMapsToPositivePitch() {
        val (x,y)=GimbalProtocol.normalized(1f,-1f)
        assertTrue(x in 40..43 && y in 40..43)
        assertEquals(0 to 60,GimbalProtocol.normalized(0f,-1f))
    }
}
