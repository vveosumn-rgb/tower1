package com.tavernrush.game.render.pixel

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Цвет для пиксель-арта. Главный приём — рампы со сдвигом оттенка: тени уходят в холодный
 * сине-фиолетовый, света — в тёплый жёлтый. Так форма читается без «грязных» серых теней.
 * Чистый Kotlin без android.* (запекание моделей гоняется и в обычных JVM-тестах).
 */
object PxColor {
    const val OUTLINE = 0xFF1B1224.toInt()

    fun a(c: Int) = c ushr 24
    fun r(c: Int) = (c shr 16) and 255
    fun g(c: Int) = (c shr 8) and 255
    fun b(c: Int) = c and 255
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun opaque(c: Int) = c or (0xFF shl 24)

    /** HSV: h в градусах [0,360), s и v в [0,1]. */
    fun toHsv(c: Int, out: FloatArray) {
        val rf = r(c) / 255f; val gf = g(c) / 255f; val bf = b(c) / 255f
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
        val d = mx - mn
        var h = when {
            d < 1e-6f -> 0f
            mx == rf -> 60f * (((gf - bf) / d) % 6f)
            mx == gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }
        if (h < 0f) h += 360f
        out[0] = h
        out[1] = if (mx <= 1e-6f) 0f else d / mx
        out[2] = mx
    }

    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        var hh = h % 360f
        if (hh < 0f) hh += 360f
        val ss = s.coerceIn(0f, 1f); val vv = v.coerceIn(0f, 1f)
        val c = vv * ss
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = vv - c
        val (rf, gf, bf) = when (floor(hh / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((rf + m) * 255f + 0.5f).toInt().coerceIn(0, 255), ((gf + m) * 255f + 0.5f).toInt().coerceIn(0, 255), ((bf + m) * 255f + 0.5f).toInt().coerceIn(0, 255))
    }

    private val HUE = floatArrayOf(-26f, -13f, 0f, 9f, 17f)
    private val VAL = floatArrayOf(0.36f, 0.62f, 1f, 1.16f, 1.34f)
    private val SAT = floatArrayOf(1.12f, 1.05f, 1f, 0.9f, 0.6f)

    /** Пятиступенчатая рампа: [0] глубокая тень … [2] базовый цвет … [4] блик. contrast < 1 смягчает. */
    fun ramp(base: Int, contrast: Float = 1f): IntArray {
        val hsv = FloatArray(3)
        toHsv(base, hsv)
        val h = hsv[0]; val s = hsv[1]
        // Яркий базовый цвет «опускаем», чтобы для света осталось место.
        val v = min(hsv[2], 0.86f)
        val out = IntArray(5)
        for (i in 0 until 5) {
            val k = 1f + (VAL[i] - 1f) * contrast
            var vi = v * k
            if (i >= 3) vi += 0.02f * i
            if (hsv[2] < 0.3f && i >= 3) vi += 0.05f
            val si = if (s < 0.08f) s else (s * (1f + (SAT[i] - 1f) * contrast)).coerceIn(0f, 1f)
            out[i] = fromHsv(h + HUE[i] * contrast, si, vi.coerceIn(0f, 1f))
        }
        return out
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return argb(
            (a(a) + (a(b) - a(a)) * k).toInt(),
            (r(a) + (r(b) - r(a)) * k).toInt(),
            (g(a) + (g(b) - g(a)) * k).toInt(),
            (b(a) + (b(b) - b(a)) * k).toInt(),
        )
    }

    fun mul(c: Int, k: Float): Int = argb(
        a(c),
        (r(c) * k).toInt().coerceIn(0, 255),
        (g(c) * k).toInt().coerceIn(0, 255),
        (b(c) * k).toInt().coerceIn(0, 255),
    )
}
