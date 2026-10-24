package com.lunamonitor.beta.protocol

import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

object GimbalProtocol {
    const val COMMAND = 226
    fun supportsFirmware(firmware: String?): Boolean {
        val numbers = firmware?.let { Regex("\\d+").findAll(it).map { m -> m.value.toIntOrNull() ?: 0 }.take(3).toList() } ?: return false
        if (numbers.size < 3) return false
        return numbers[0] > 1 || (numbers[0] == 1 && (numbers[1] > 0 || numbers[2] >= 288))
    }
    fun normalized(x: Float, y: Float): Pair<Int, Int> {
        if (!x.isFinite() || !y.isFinite()) return 0 to 0
        val length = sqrt(x*x + y*y)
        if (length < 0.10f) return 0 to 0
        val scale = 60f / length.coerceAtLeast(1f)
        return (x * scale).toInt().coerceIn(-60, 60) to (-y * scale).toInt().coerceIn(-60, 60)
    }
    fun move(horizontal: Int, vertical: Int): ByteArray {
        val nested = ByteArrayOutputStream()
        fun add(field: Int, value: Int) {
            if (value == 0) return
            nested.write(field shl 3)
            val safe = value.coerceIn(-99,99)
            var v = (safe shl 1) xor (safe shr 31)
            while (v >= 128) { nested.write((v and 127) or 128); v = v ushr 7 }
            nested.write(v)
        }
        add(1, vertical); add(2, horizontal)
        return byteArrayOf(8,1,18,nested.size().toByte()) + nested.toByteArray() + byteArrayOf(32,4)
    }
    fun action(action: Int): ByteArray {
        require(action == 2 || action == 3)
        return byteArrayOf(8, action.toByte())
    }
}
