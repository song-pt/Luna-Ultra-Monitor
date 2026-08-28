package com.lunamonitor.beta.lut

import com.lunamonitor.beta.model.CubeLut
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LutAtlasPackerTest {
    @Test fun packsBlueSlicesSideBySideWithoutChangingSamples() {
        val samples = FloatArray(2 * 2 * 2 * 3) { it / 24f }
        val atlas = LutAtlasPacker.pack(CubeLut("test", 2, samples))

        assertEquals(4, atlas.width)
        assertEquals(2, atlas.height)
        assertEquals(2, atlas.columns)
        assertEquals(32, atlas.rgba.size)
        assertArrayEquals(samples.copyOfRange(0, 3), atlas.rgba.copyOfRange(0, 3), 0f)
        assertArrayEquals(samples.copyOfRange(12, 15), atlas.rgba.copyOfRange(8, 11), 0f)
        assertArrayEquals(samples.copyOfRange(6, 9), atlas.rgba.copyOfRange(16, 19), 0f)
        assertArrayEquals(samples.copyOfRange(18, 21), atlas.rgba.copyOfRange(24, 27), 0f)
    }

    @Test fun keepsSixtyFivePointLutWithinCompactTextureDimensions() {
        val size = 65
        val atlas = LutAtlasPacker.pack(CubeLut("65-point", size, FloatArray(size * size * size * 3)))
        assertEquals(9, atlas.columns)
        assertEquals(585, atlas.width)
        assertEquals(520, atlas.height)
    }
}
