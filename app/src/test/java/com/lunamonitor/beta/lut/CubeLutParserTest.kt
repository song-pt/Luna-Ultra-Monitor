package com.lunamonitor.beta.lut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CubeLutParserTest {
    @Test fun parsesTwoPointIdentityCube() {
        val rows = buildString {
            appendLine("TITLE \"identity\"")
            appendLine("LUT_3D_SIZE 2")
            for (b in 0..1) for (g in 0..1) for (r in 0..1) appendLine("$r $g $b")
        }
        val lut = CubeLutParser.parse("identity.cube", rows.byteInputStream())
        assertEquals("identity", lut.name)
        assertEquals(2, lut.size)
        assertEquals(24, lut.rgb.size)
    }

    @Test fun rejectsIncompleteCube() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLutParser.parse("bad.cube", "LUT_3D_SIZE 2\n0 0 0\n".byteInputStream())
        }
    }

    @Test fun acceptsUtf8BomAndCommonWhitespace() {
        val rows = buildString {
            appendLine("\uFEFFLUT_3D_SIZE\t2")
            repeat(8) { appendLine("0.0  0.5\t1.0") }
        }
        assertEquals(2, CubeLutParser.parse("bom.cube", rows.byteInputStream()).size)
    }
}
