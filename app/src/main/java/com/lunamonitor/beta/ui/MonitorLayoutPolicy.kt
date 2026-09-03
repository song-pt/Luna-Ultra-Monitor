package com.lunamonitor.beta.ui

object MonitorLayoutPolicy {
    fun panelWidth(availableWidthDp: Float): Float = availableWidthDp.coerceAtMost(310f).coerceAtLeast(0f)
    fun compactToolbar(availableWidthDp: Float): Boolean = availableWidthDp < 600f
    fun compactHeight(availableHeightDp: Float): Boolean = availableHeightDp < 360f
    fun showFullStats(availableWidthDp: Float): Boolean = availableWidthDp >= 720f
    fun showBitrate(availableWidthDp: Float): Boolean = availableWidthDp >= 540f
    fun hideBrand(availableWidthDp: Float): Boolean = availableWidthDp < 460f
}
