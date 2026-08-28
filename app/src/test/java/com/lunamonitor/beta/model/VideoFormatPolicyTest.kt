package com.lunamonitor.beta.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatPolicyTest {
    @Test
    fun frameRateIsActionableWhileConnectedEvenWhenModeWasNotDetected() {
        assertTrue(VideoFormatPolicy.canApply(ConnectionState.WAITING_VIDEO, busy = false, recording = false))
        assertTrue(VideoFormatPolicy.requiresVideoMode(CaptureMode.UNKNOWN))
    }

    @Test
    fun frameRateIsBlockedDuringRecordingOrCommand() {
        assertFalse(VideoFormatPolicy.canApply(ConnectionState.STREAMING, busy = false, recording = true))
        assertFalse(VideoFormatPolicy.canApply(ConnectionState.STREAMING, busy = true, recording = false))
    }

    @Test
    fun onlyNonVideoModesNeedAnAutomaticModeSwitch() {
        assertFalse(VideoFormatPolicy.requiresVideoMode(CaptureMode.VIDEO))
        assertTrue(VideoFormatPolicy.requiresVideoMode(CaptureMode.PHOTO))
    }

    @Test
    fun cameraColorModeIsLimitedToIdleVideoCapture() {
        assertTrue(VideoFormatPolicy.canSetColorMode(ConnectionState.STREAMING, CaptureMode.VIDEO, busy = false, recording = false))
        assertFalse(VideoFormatPolicy.canSetColorMode(ConnectionState.STREAMING, CaptureMode.PHOTO, busy = false, recording = false))
        assertFalse(VideoFormatPolicy.canSetColorMode(ConnectionState.STREAMING, CaptureMode.UNKNOWN, busy = false, recording = false))
        assertFalse(VideoFormatPolicy.canSetColorMode(ConnectionState.STREAMING, CaptureMode.VIDEO, busy = false, recording = true))
        assertFalse(VideoFormatPolicy.canSetColorMode(ConnectionState.IDLE, CaptureMode.VIDEO, busy = false, recording = false))
    }
}
