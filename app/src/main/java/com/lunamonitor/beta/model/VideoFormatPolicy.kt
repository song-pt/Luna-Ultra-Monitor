package com.lunamonitor.beta.model

object VideoFormatPolicy {
    fun canApply(connection: ConnectionState, busy: Boolean, recording: Boolean): Boolean =
        connection in setOf(ConnectionState.WAITING_VIDEO, ConnectionState.STREAMING) && !busy && !recording

    fun requiresVideoMode(mode: CaptureMode): Boolean = mode != CaptureMode.VIDEO

    fun canSetColorMode(connection: ConnectionState, captureMode: CaptureMode, busy: Boolean, recording: Boolean): Boolean =
        captureMode == CaptureMode.VIDEO && canApply(connection, busy, recording)
}
