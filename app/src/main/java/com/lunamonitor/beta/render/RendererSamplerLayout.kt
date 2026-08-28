package com.lunamonitor.beta.render

internal object RendererSamplerLayout {
    const val VIDEO = 0
    const val RECOVERY_LUT = 1
    const val STYLE_LUT = 2

    fun isValid(): Boolean = setOf(VIDEO, RECOVERY_LUT, STYLE_LUT).size == 3
}
