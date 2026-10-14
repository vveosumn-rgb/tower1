package com.tavernrush.game.render.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Пиксельные цифры 3×5 с контуром для чисел урона/лечения/золота прямо в мире.
 * Глифы запекаются заранее по цветам и размерам: число рисуется несколькими drawBitmap без аллокаций.
 */
class PixelFont {
    class Style(val color: Int, val scale: Int)

    private val glyphs = HashMap<Long, Bitmap>()
    private val paint = Paint()
    private val chars = "0123456789+-!"

    private fun key(color: Int, scale: Int, ch: Char): Long = (color.toLong() and 0xFFFFFFFFL) or (scale.toLong() shl 32) or (ch.code.toLong() shl 40)

    private fun glyph(color: Int, scale: Int, ch: Char): Bitmap {
        val k = key(color, scale, ch)
        glyphs[k]?.let { return it }
        val bits = PixelAssets.glyphBits[ch.code]
        val w = 3 * scale + 2; val h = 5 * scale + 2
        val px = IntArray(w * h)
        val dark = 0xFF1B1224.toInt()
        val hi = PxColor.mix(color, 0xFFFFFFFF.toInt(), 0.45f)
        for (gy in 0 until 5) for (gx in 0 until 3) {
            if ((bits shr (gy * 3 + gx)) and 1 == 0) continue
            for (sy in 0 until scale) for (sx in 0 until scale) {
                val x = 1 + gx * scale + sx; val y = 1 + gy * scale + sy
                px[y * w + x] = if (gy == 0 && sy == 0) hi else color
            }
        }
        val out = px.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] != 0) continue
            var n = false
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && px[yy * w + xx] != 0) n = true
            }
            if (n) out[y * w + x] = dark
        }
        val b = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        glyphs[k] = b
        return b
    }

    /** Число с центром в (cx, top): знак (+/−) для золота/лечения/урона по герою. */
    fun draw(c: Canvas, value: Int, sign: Char, cx: Int, top: Int, color: Int, scale: Int, alpha: Int) {
        var v = if (value < 0) 0 else value
        var digits = 0
        var t = v
        do { digits++; t /= 10 } while (t > 0)
        val gw = 3 * scale + 2 - 1   // соседние глифы делят пиксель контура
        val total = (digits + (if (sign != ' ') 1 else 0)) * gw + 1
        var x = cx - total / 2 + total - gw - 1
        paint.alpha = alpha.coerceIn(0, 255)
        for (i in 0 until digits) {
            val d = (v % 10)
            v /= 10
            c.drawBitmap(glyph(color, scale, chars[d]), x.toFloat(), top.toFloat(), paint)
            x -= gw
        }
        if (sign != ' ') c.drawBitmap(glyph(color, scale, sign), x.toFloat(), top.toFloat(), paint)
    }

    /** Один знак ('!', '+', '-') с центром в (cx, top): эмоции над существами. */
    fun char(c: Canvas, ch: Char, cx: Int, top: Int, color: Int, scale: Int, alpha: Int) {
        if (chars.indexOf(ch) < 0) return
        val gw = 3 * scale + 2
        paint.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(glyph(color, scale, ch), (cx - gw / 2).toFloat(), top.toFloat(), paint)
    }

    fun clear() { glyphs.clear() }
}
