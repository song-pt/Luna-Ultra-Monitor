package com.lunamonitor.beta.lut

import com.lunamonitor.beta.model.CubeLut
import java.io.InputStream

object CubeLutParser {
    fun parse(fileName: String, input: InputStream): CubeLut {
        var size = 0
        val values = ArrayList<Float>(32 * 32 * 32 * 3)
        input.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, source ->
                val line = source.substringBefore('#').trim().trimStart('\uFEFF')
                if (line.isEmpty() || line.startsWith("TITLE", true) ||
                    line.startsWith("DOMAIN_MIN", true) || line.startsWith("DOMAIN_MAX", true)) return@forEachIndexed
                val parts = line.split(Regex("\\s+"))
                when {
                    parts[0].equals("LUT_3D_SIZE", true) -> {
                        require(parts.size == 2) { "Invalid LUT_3D_SIZE at line ${index + 1}" }
                        val declaredSize = parts[1].toIntOrNull()
                            ?: error("Invalid LUT_3D_SIZE at line ${index + 1}")
                        require(declaredSize in 2..65) { "3D LUT size must be between 2 and 65" }
                        require(size == 0 || size == declaredSize) { "Conflicting LUT_3D_SIZE declarations" }
                        size = declaredSize
                    }
                    parts[0].startsWith("LUT_", true) -> error("Only 3D .cube LUT files are supported")
                    else -> {
                        require(size > 0) { "LUT_3D_SIZE must appear before samples" }
                        require(parts.size == 3) { "Invalid RGB sample at line ${index + 1}" }
                        parts.forEach { values += it.toFloat().coerceIn(0f, 1f) }
                    }
                }
            }
        }
        require(size > 0) { "Missing LUT_3D_SIZE" }
        require(values.size == size * size * size * 3) {
            "Expected ${size * size * size} RGB samples, found ${values.size / 3}"
        }
        return CubeLut(fileName.substringBeforeLast('.'), size, values.toFloatArray())
    }
}
