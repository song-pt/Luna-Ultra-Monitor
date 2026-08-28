package com.lunamonitor.beta.render

import org.junit.Assert.assertTrue
import org.junit.Test

class RendererSamplerLayoutTest {
    @Test fun externalVideoAndBothLutsUseDistinctTextureUnits() {
        assertTrue(RendererSamplerLayout.isValid())
    }
}
