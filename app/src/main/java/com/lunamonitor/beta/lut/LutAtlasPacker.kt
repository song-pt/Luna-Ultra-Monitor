package com.lunamonitor.beta.lut

import com.lunamonitor.beta.model.CubeLut

data class LutAtlas(val width: Int, val height: Int, val columns: Int, val rgba: FloatArray)

/** Packs blue slices into a compact grid, keeping 65-point LUTs below mobile texture limits. */
object LutAtlasPacker {
    fun pack(lut: CubeLut): LutAtlas {
        val size = lut.size
        val columns = kotlin.math.ceil(kotlin.math.sqrt(size.toDouble())).toInt()
        val rows = (size + columns - 1) / columns
        val width = size * columns
        val height = size * rows
        val rgba = FloatArray(width * height * 4)
        for (blue in 0 until size) {
            val tileX = blue % columns
            val tileY = blue / columns
            for (green in 0 until size) {
                for (red in 0 until size) {
                    val source = ((blue * size + green) * size + red) * 3
                    val target = (((tileY * size + green) * width) + tileX * size + red) * 4
                    rgba[target] = lut.rgb[source]
                    rgba[target + 1] = lut.rgb[source + 1]
                    rgba[target + 2] = lut.rgb[source + 2]
                    rgba[target + 3] = 1f
                }
            }
        }
        return LutAtlas(width, height, columns, rgba)
    }
}
