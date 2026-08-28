package com.lunamonitor.beta.protocol

import com.lunamonitor.beta.model.CaptureMode
import com.lunamonitor.beta.model.CameraColorMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CameraControlProtocolTest {
    @Test fun videoResolutionBodiesRoundTrip() {
        assertArrayEquals(byteArrayOf(0x08, 0x1f, 0x10, 0x07), CameraControlProtocol.getVideoResolutionBody())
        val body = CameraControlProtocol.setVideoResolutionBody(214)
        assertTrue(body.isNotEmpty())
        val response = byteArrayOf(0x12, 0x04, 0xf8.toByte(), 0x01, 0xd6.toByte(), 0x01)
        assertEquals(214, CameraControlProtocol.parseVideoResolution(response))
    }
    @Test fun captureCommandBodiesMatchSchema() {
        assertArrayEquals(byteArrayOf(0x08, 0x01), CameraControlProtocol.startRecordingBody())
        assertArrayEquals(byteArrayOf(0x10, 0x01), CameraControlProtocol.stopRecordingBody())
        assertTrue(CameraControlProtocol.takePictureBody().isEmpty())
    }

    @Test fun zoomWriteContainsField53DoubleAndVideoMode() {
        val body = CameraControlProtocol.setZoomBody(3.5)
        assertArrayEquals(byteArrayOf(0x08, 0x35, 0x12, 0x0a, 0xa9.toByte(), 0x03), body.copyOfRange(0, 6))
        assertEquals(3.5, ByteBuffer.wrap(body, 6, 8).order(ByteOrder.LITTLE_ENDIAN).double, 0.0)
        assertArrayEquals(byteArrayOf(0x18, 0x07), body.copyOfRange(14, 16))
    }

    @Test fun parsesCaptureStatusAndZoomReadback() {
        val capture = CameraControlProtocol.parseCaptureStatus(byteArrayOf(0x0a, 0x04, 0x08, 0x01, 0x10, 0x07))!!
        assertTrue(capture.recording); assertEquals(7, capture.captureTimeSeconds)
        assertFalse(CameraControlProtocol.parseCaptureStatus(byteArrayOf(0x0a, 0x02, 0x08, 0x08))!!.recording)
        val double = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(6.0).array()
        val response = byteArrayOf(0x12, 0x0a, 0xa9.toByte(), 0x03) + double
        assertEquals(6.0, CameraControlProtocol.parseZoom(response)!!, 0.0)
    }

    @Test fun readsAndSwitchesCanonicalPhotoVideoModes() {
        assertArrayEquals(byteArrayOf(0x08, 0x28, 0x08, 0x29), CameraControlProtocol.getCameraModeBody())
        assertArrayEquals(byteArrayOf(0x08, 0x28, 0x12, 0x00), CameraControlProtocol.setCameraModeBody(CaptureMode.PHOTO))
        assertArrayEquals(byteArrayOf(0x08, 0x29, 0x12, 0x00), CameraControlProtocol.setCameraModeBody(CaptureMode.VIDEO))
        assertEquals(CaptureMode.PHOTO, CameraControlProtocol.parseCameraMode(byteArrayOf(0x12, 0x03, 0xc8.toByte(), 0x02, 0x64)))
        assertEquals(CaptureMode.VIDEO, CameraControlProtocol.parseCameraMode(byteArrayOf(0x12, 0x03, 0xc0.toByte(), 0x02, 0x64)))
        assertTrue(CameraControlProtocol.cameraModeAccepted(byteArrayOf(0x08, 0x28), CaptureMode.PHOTO))
    }

    @Test fun videoColorModeUsesMeasuredField35ValuesAndReadback() {
        assertArrayEquals(byteArrayOf(0x08, 0x23, 0x10, 0x07), CameraControlProtocol.getVideoColorModeBody())
        assertArrayEquals(
            byteArrayOf(0x08, 0x23, 0x12, 0x03, 0x98.toByte(), 0x02, 0x01, 0x18, 0x07),
            CameraControlProtocol.setVideoColorModeBody(CameraColorMode.STANDARD),
        )
        assertArrayEquals(
            byteArrayOf(0x08, 0x23, 0x12, 0x03, 0x98.toByte(), 0x02, 0x05, 0x18, 0x07),
            CameraControlProtocol.setVideoColorModeBody(CameraColorMode.DOLBY_VISION),
        )
        assertEquals(
            CameraColorMode.I_LOG,
            CameraControlProtocol.parseVideoColorMode(byteArrayOf(0x12, 0x03, 0x98.toByte(), 0x02, 0x02)),
        )
    }
}
