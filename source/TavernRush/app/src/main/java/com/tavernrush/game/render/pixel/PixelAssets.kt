package com.tavernrush.game.render.pixel

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Палитра биома: ступени от тени до света + акценты для мелких деталей. */
class BiomePalette(
    val dark: Int, val base: Int, val light: Int, val high: Int,
    /** Цвета цветов/грибов/камешков. */
    val accents: IntArray,
    /** Цвет окружающего света (умножается на картинку) и туман дали. */
    val ambient: Int, val fog: Int,
)

object Biomes {
    val MEADOW = BiomePalette(
        0xFF336B3A.toInt(), 0xFF47883F.toInt(), 0xFF5CA449.toInt(), 0xFF88C45E.toInt(),
        intArrayOf(0xFFF6F2FF.toInt(), 0xFFFFD84A.toInt(), 0xFFFF7FA8.toInt(), 0xFF7FC4FF.toInt()),
        0xFFD6D6EC.toInt(), 0xFF2A2650.toInt(),
    )
    private fun tint(a:Int,b:Int,c:Int,d:Int,accent:Int)=BiomePalette(a,b,c,d,intArrayOf(accent,accent,0xffb6b6cb.toInt(),0xffe4daba.toInt()),0xffa2a3b8.toInt(),0xff28233c.toInt())
    val ASH=tint(0xff473f38.toInt(),0xff625345.toInt(),0xff807057.toInt(),0xffab956e.toInt(),0xffefb765.toInt())
    val ICE=tint(0xff304d61.toInt(),0xff4b7181.toInt(),0xff6ba0ab.toInt(),0xffa0ccd1.toInt(),0xffd9f5eb.toInt())
    val STORM=tint(0xff36344e.toInt(),0xff534966.toInt(),0xff716080.toInt(),0xffa184a7.toInt(),0xffcdb4f4.toInt())
    val FOG=tint(0xff344b40.toInt(),0xff496050.toInt(),0xff657861.toInt(),0xff91a380.toInt(),0xffb4c28d.toInt())
    val CRYSTAL=tint(0xff363e4d.toInt(),0xff4e5d6b.toInt(),0xff728795.toInt(),0xffa0b8ba.toInt(),0xffeedca0.toInt())
    fun region(i:Int)=when(i){1->ASH;2->ICE;3->STORM;4->FOG;5->CRYSTAL;else->MEADOW}

}

/** Процедурные ассеты арены: бесшовная земля, мелкий декор, облачные тени, пиксельные цифры. Чистый Kotlin. */
object PixelAssets {
    private val BAYER = floatArrayOf(0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f)
    fun bayer(x: Int, y: Int): Float = BAYER[(y and 3) * 4 + (x and 3)] / 16f - 0.47f

    private fun hash(x: Int, y: Int, s: Int): Int {
        var h = x * 374761393 + y * 668265263 + s * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7fffffff
    }

    private fun h01(x: Int, y: Int, s: Int): Float = (hash(x, y, s) and 0xFFFFFF) / 16777216f

    /** Бесшовный value-noise: решётка n×n с заворачиванием. */
    private fun vnoise(x: Float, y: Float, n: Int, seed: Int): Float {
        val xf = floor(x); val yf = floor(y)
        val fx = x - xf; val fy = y - yf
        val x0 = ((xf.toInt() % n) + n) % n; val y0 = ((yf.toInt() % n) + n) % n
        val x1 = (x0 + 1) % n; val y1 = (y0 + 1) % n
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = h01(x0, y0, seed); val b = h01(x1, y0, seed)
        val c = h01(x0, y1, seed); val d = h01(x1, y1, seed)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy
    }

    /** Трава: три октавы шума → четыре тона с дизерингом + травинки. size должен делиться на 16. */
    fun grassTile(size: Int, seed: Int, p: BiomePalette): IntArray {
        val out = IntArray(size * size)
        val tones = intArrayOf(p.dark, p.base, p.light, p.high)
        for (y in 0 until size) for (x in 0 until size) {
            val u = x.toFloat() / size; val v = y.toFloat() / size
            val n = 0.52f * vnoise(u * 4, v * 4, 4, seed) + 0.30f * vnoise(u * 8, v * 8, 8, seed + 1) + 0.18f * vnoise(u * 16, v * 16, 16, seed + 2)
            val t = (n - 0.2f) * 3.1f + bayer(x, y) * 0.3f
            out[y * size + x] = tones[floor(t).toInt().coerceIn(0, 3).let { if (it == 3 && h01(x, y, seed + 9) < 0.55f) 2 else it }]
        }
        // травинки: тёмное основание + два светлых пикселя
        val blades = size * size / 70
        for (i in 0 until blades) {
            val x = hash(i, 11, seed) % size
            val y = hash(i, 17, seed) % size
            val tall = h01(i, 5, seed) < 0.45f
            put(out, size, x, y, p.dark)
            put(out, size, x, y - 1, p.light)
            if (tall) put(out, size, x, y - 2, p.high)
            if (h01(i, 8, seed) < 0.3f) put(out, size, x + 1, y, p.base)
        }
        return out
    }

    private fun put(a: IntArray, size: Int, x: Int, y: Int, c: Int) {
        val xx = ((x % size) + size) % size; val yy = ((y % size) + size) % size
        a[yy * size + xx] = c
    }

    /** Мелкий декор поверх травы: пучок, цветок, камешек, лепестки. Возвращает (пиксели, ширина, высота). */
    class Tiny(val px: IntArray, val w: Int, val h: Int, val ox: Int, val oy: Int)

    private fun tiny(w: Int, h: Int, ox: Int, oy: Int, draw: (IntArray, Int) -> Unit): Tiny {
        val px = IntArray(w * h)
        draw(px, w)
        // тёмный контур по ореолу (читается на траве, как и всё остальное)
        val res = px.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] != 0) continue
            val n = (x > 0 && px[y * w + x - 1] != 0) || (x + 1 < w && px[y * w + x + 1] != 0) || (y > 0 && px[(y - 1) * w + x] != 0) || (y + 1 < h && px[(y + 1) * w + x] != 0)
            if (n) res[y * w + x] = 0x66101820
        }
        return Tiny(res, w, h, ox, oy)
    }

    fun tuft(p: BiomePalette, variant: Int): Tiny = tiny(7, 6, 3, 5) { a, w ->
        val blades = if (variant == 0) intArrayOf(1, 3, 5) else intArrayOf(0, 2, 4, 6)
        for ((i, x) in blades.withIndex()) {
            val hgt = 3 + ((i + variant) % 3)
            for (k in 0 until hgt) a[(5 - k) * w + x] = if (k == hgt - 1) p.high else if (k == 0) p.dark else p.light
        }
    }

    fun flower(p: BiomePalette, color: Int): Tiny = tiny(5, 7, 2, 6) { a, w ->
        a[6 * w + 2] = p.dark; a[5 * w + 2] = p.base; a[4 * w + 2] = p.base
        a[5 * w + 1] = p.light; a[5 * w + 3] = p.light
        val hi = PxColor.mix(color, 0xFFFFFFFF.toInt(), 0.5f)
        a[2 * w + 2] = color; a[3 * w + 1] = color; a[3 * w + 3] = color; a[3 * w + 2] = hi; a[2 * w + 1] = PxColor.mul(color, 0.8f); a[2 * w + 3] = color
        a[1 * w + 2] = hi
    }

    fun pebble(): Tiny = tiny(6, 4, 3, 3) { a, w ->
        val c = intArrayOf(0xFF6A7088.toInt(), 0xFF8A90A6.toInt(), 0xFFB4BAD0.toInt())
        for (x in 1..4) a[1 * w + x] = c[1]
        for (x in 0..5) a[2 * w + x] = c[1]
        for (x in 1..4) a[3 * w + x] = c[0]
        a[1 * w + 1] = c[2]; a[1 * w + 2] = c[2]; a[2 * w + 0] = c[2]
    }

    /** Облачная тень: полупрозрачные блобы, бесшовные. */
    fun cloudTile(size: Int, seed: Int): IntArray {
        val out = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val u = x.toFloat() / size; val v = y.toFloat() / size
            val n = 0.65f * vnoise(u * 3, v * 3, 3, seed) + 0.35f * vnoise(u * 6, v * 6, 6, seed + 3)
            val t = (n - 0.52f) * 5.5f + bayer(x, y) * 0.5f
            if (t > 0.5f) out[y * size + x] = PxColor.argb(if (t > 1.1f) 34 else 20, 20, 16, 52)
        }
        return out
    }

    // ------------------------------------------------------------ свечение

    /** Гладкий белый радиальный градиент (альфа убывает) — для карты света и мягких вспышек. */
    fun softGlow(radius: Int): Bitmap {
        val d = radius * 2 + 1
        val px = IntArray(d * d)
        for (y in 0 until d) for (x in 0 until d) {
            val dx = (x - radius).toFloat(); val dy = (y - radius).toFloat()
            val t = 1f - sqrt(dx * dx + dy * dy) / radius
            if (t > 0f) {
                val a = (t * t * (3 - 2 * t))
                px[y * d + x] = PxColor.argb((a * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            }
        }
        return Bitmap.createBitmap(px, d, d, Bitmap.Config.ARGB_8888)
    }

    /** Пиксельное свечение: те же градиенты, но 5 ступеней с дизерингом — в стиле всей картинки. */
    fun pixelGlow(radius: Int): Bitmap {
        val d = radius * 2 + 1
        val px = IntArray(d * d)
        val levels = intArrayOf(0, 56, 110, 170, 235)
        for (y in 0 until d) for (x in 0 until d) {
            val dx = (x - radius).toFloat(); val dy = (y - radius).toFloat()
            val t = 1f - sqrt(dx * dx + dy * dy) / radius
            if (t <= 0f) continue
            val v = t * t * 4f + bayer(x, y) * 0.8f
            val l = floor(v + 0.5f).toInt().coerceIn(0, 4)
            if (levels[l] > 0) px[y * d + x] = PxColor.argb(levels[l], 255, 255, 255)
        }
        return Bitmap.createBitmap(px, d, d, Bitmap.Config.ARGB_8888)
    }

    /** Пиксельный диск с дизерингом по краю (аура, телеграфы) — заливка альфой a. */
    fun ditherDisc(radius: Int, alpha: Int, color: Int): Bitmap {
        val d = radius * 2 + 1
        val px = IntArray(d * d)
        for (y in 0 until d) for (x in 0 until d) {
            val dx = (x - radius).toFloat(); val dy = (y - radius).toFloat()
            val r = sqrt(dx * dx + dy * dy) / radius
            if (r > 1f) continue
            val inner = 1f - r
            val v = inner * 3f + bayer(x, y) * 0.9f
            val a = when {
                v > 1.6f -> alpha
                v > 0.7f -> alpha * 3 / 4
                v > 0.15f -> alpha / 2
                else -> alpha / 3
            }
            px[y * d + x] = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
        }
        return Bitmap.createBitmap(px, d, d, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------ цифры 3×5

    private val GLYPH = mapOf(
        '0' to "111101101101111", '1' to "010110010010111", '2' to "111001111100111", '3' to "111001111001111",
        '4' to "101101111001001", '5' to "111100111001111", '6' to "111100111101111", '7' to "111001010010010",
        '8' to "111101111101111", '9' to "111101111001111", '+' to "000010111010000", '-' to "000000111000000",
        '!' to "010010010000010",
    )
    /** Растр глифа: 15 бит (3×5) в Int; индекс по коду символа ASCII 33..57. */
    val glyphBits: IntArray = IntArray(128).also { arr ->
        for ((ch, s) in GLYPH) {
            var v = 0
            for (i in 0 until 15) if (s[i] == '1') v = v or (1 shl i)
            arr[ch.code] = v
        }
    }

    fun clampInt(v: Int, lo: Int, hi: Int) = max(lo, min(hi, v))
    fun absF(v: Float) = abs(v)
}
