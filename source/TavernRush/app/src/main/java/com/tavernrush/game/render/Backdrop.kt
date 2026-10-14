package com.tavernrush.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.tavernrush.game.core.Rng

/** Фоны меню и таверны: рисуются один раз под размер экрана. */
object Backdrop {
    /** Ночная таверна на холме: небо, звёзды, луна, холмы, дом с тёплыми окнами. Возвращает высоту «земли» (y). */
    /** Доля высоты экрана, где у фона меню линия земли (MenuScreen ставит героя у двери). */
    const val MENU_GROUND = 0.56f

    fun menu(w: Int, h: Int, u: Float): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF0F1033.toInt(), 0xFF2A1F5C.toInt(), 0xFF6B3A6E.toInt(), 0xFFE0805A.toInt()), floatArrayOf(0f, MENU_GROUND * 0.5f, MENU_GROUND * 0.82f, MENU_GROUND * 1.04f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val rng = Rng(9)
        for (i in 0 until 140) {
            val x = rng.nextFloat() * w
            val y = rng.nextFloat() * h * 0.55f
            val r = (1f + rng.nextFloat() * 2.4f) * u
            p.color = Palette.withAlpha(0xFFFFF6E6.toInt(), 90 + rng.nextInt(160))
            c.drawCircle(x, y, r, p)
        }
        // луна
        val mx = w * 0.2f; val my = h * MENU_GROUND - 640f * u; val mr = 80f * u
        p.shader = RadialGradient(mx, my, mr * 3f, 0x55FFF2C0, 0x00FFF2C0, Shader.TileMode.CLAMP)
        c.drawCircle(mx, my, mr * 3f, p)
        p.shader = null
        p.color = 0xFFFFF2C8.toInt(); c.drawCircle(mx, my, mr, p)
        p.color = 0xFFEADBA8.toInt(); c.drawCircle(mx - 26f * u, my + 18f * u, 18f * u, p); c.drawCircle(mx + 20f * u, my - 24f * u, 12f * u, p)
        val ground = h * MENU_GROUND
        // дальние холмы
        hill(c, p, w, ground - 160f * u, 0xFF3B2B5E.toInt(), u, 3)
        hill(c, p, w, ground - 70f * u, 0xFF2C2148.toInt(), u, 5)
        // таверна
        tavern(c, p, w * 0.5f, ground + 10f * u, u)
        // передний план
        p.color = 0xFF1B2A1F.toInt()
        c.drawRect(0f, ground, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(0f, ground, 0f, h.toFloat(), 0xFF22382A.toInt(), 0xFF0E1611.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, ground, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // тропинка к двери
        val path = Path()
        path.moveTo(w * 0.47f, ground + 10f * u)
        path.lineTo(w * 0.53f, ground + 10f * u)
        path.lineTo(w * 0.72f, h.toFloat())
        path.lineTo(w * 0.28f, h.toFloat())
        path.close()
        p.color = 0xFF3A2E22.toInt(); c.drawPath(path, p)
        // ёлки
        for (i in 0 until 7) {
            val x = if (i < 4) w * (0.02f + i * 0.08f) else w * (0.75f + (i - 4) * 0.09f)
            tree(c, p, x, ground + (20f + (i % 3) * 18f) * u, (120f + (i % 3) * 40f) * u)
        }
        return b
    }

    private fun hill(c: Canvas, p: Paint, w: Int, y: Float, color: Int, u: Float, bumps: Int) {
        val path = Path()
        path.moveTo(0f, y + 200f * u)
        val step = w / bumps.toFloat()
        for (i in 0..bumps) {
            val x = i * step
            path.quadTo(x - step * 0.5f, y - (40f + (i * 37 % 60)) * u, x, y + (i % 2) * 20f * u)
        }
        path.lineTo(w.toFloat(), y + 400f * u)
        path.lineTo(0f, y + 400f * u)
        path.close()
        p.color = color
        c.drawPath(path, p)
    }

    private fun tree(c: Canvas, p: Paint, x: Float, base: Float, hgt: Float) {
        p.color = 0xFF0F1A13.toInt()
        val path = Path()
        for (k in 0 until 3) {
            val t = base - hgt * (0.25f + k * 0.25f)
            val wdt = hgt * (0.42f - k * 0.1f)
            path.reset()
            path.moveTo(x, t - hgt * 0.32f)
            path.lineTo(x + wdt, t + hgt * 0.1f)
            path.lineTo(x - wdt, t + hgt * 0.1f)
            path.close()
            c.drawPath(path, p)
        }
        c.drawRect(x - hgt * 0.04f, base - hgt * 0.2f, x + hgt * 0.04f, base, p)
    }

    private fun tavern(c: Canvas, p: Paint, cx: Float, base: Float, u: Float) {
        val wd = 520f * u; val ht = 300f * u
        val l = cx - wd / 2f; val r = cx + wd / 2f; val t = base - ht
        // свечение
        p.shader = RadialGradient(cx, base - ht * 0.4f, wd, 0x66FFB347, 0x00FFB347, Shader.TileMode.CLAMP)
        c.drawCircle(cx, base - ht * 0.4f, wd, p)
        p.shader = null
        // стены
        p.color = 0xFF4A3226.toInt(); c.drawRect(l, t, r, base, p)
        p.color = 0xFF5C4030.toInt()
        var y = t + 30f * u
        while (y < base) { c.drawRect(l, y, r, y + 6f * u, p); y += 44f * u }
        // крыша
        val roof = Path()
        roof.moveTo(l - 50f * u, t + 10f * u); roof.lineTo(cx, t - 200f * u); roof.lineTo(r + 50f * u, t + 10f * u); roof.close()
        p.color = 0xFF7A2E25.toInt(); c.drawPath(roof, p)
        p.color = 0xFF5E211B.toInt()
        for (i in 1..4) c.drawRect(l - 50f * u + i * 20f * u, t + 10f * u - i * 40f * u, r + 50f * u - i * 20f * u, t + 16f * u - i * 40f * u, p)
        // труба с дымом
        p.color = 0xFF3B2B25.toInt(); c.drawRect(r - 140f * u, t - 170f * u, r - 90f * u, t - 60f * u, p)
        p.color = 0x55C8C0D0
        for (i in 0 until 5) c.drawCircle(r - 115f * u + i * 18f * u, t - 200f * u - i * 46f * u, (22f + i * 8f) * u, p)
        // окна
        for (i in 0 until 2) {
            val wx = if (i == 0) l + 70f * u else r - 170f * u
            window(c, p, wx, t + 90f * u, 100f * u, 90f * u)
        }
        // дверь
        p.color = 0xFF2B1D16.toInt(); c.drawRoundRect(cx - 55f * u, base - 160f * u, cx + 55f * u, base, 50f * u, 50f * u, p)
        p.color = 0xFFFFC66B.toInt(); c.drawRoundRect(cx - 45f * u, base - 150f * u, cx + 45f * u, base - 6f * u, 42f * u, 42f * u, p)
        p.color = 0xFFFFE3A0.toInt(); c.drawRect(cx - 4f * u, base - 150f * u, cx + 4f * u, base - 6f * u, p)
        // вывеска
        p.color = 0xFF2B1D16.toInt(); c.drawRect(cx + 120f * u, t + 10f * u, cx + 128f * u, t + 70f * u, p)
        p.color = 0xFF8D5A35.toInt(); c.drawRoundRect(cx + 70f * u, t + 60f * u, cx + 190f * u, t + 130f * u, 12f * u, 12f * u, p)
        p.color = Palette.GOLD; c.drawRoundRect(cx + 108f * u, t + 74f * u, cx + 146f * u, t + 118f * u, 6f * u, 6f * u, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx + 118f * u, t + 76f * u, 10f * u, p); c.drawCircle(cx + 136f * u, t + 74f * u, 11f * u, p)
    }

    private fun window(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float) {
        p.color = 0xFF2B1D16.toInt(); c.drawRect(x - 8f, y - 8f, x + w + 8f, y + h + 8f, p)
        p.color = 0xFFFFB94D.toInt(); c.drawRect(x, y, x + w, y + h, p)
        p.color = 0xFFFFE08A.toInt(); c.drawRect(x + w * 0.1f, y + h * 0.1f, x + w * 0.45f, y + h * 0.45f, p)
        p.color = 0xFF2B1D16.toInt(); c.drawRect(x + w / 2f - 4f, y, x + w / 2f + 4f, y + h, p); c.drawRect(x, y + h / 2f - 4f, x + w, y + h / 2f + 4f, p)
    }

    /** Интерьер таверны: стена из досок, очаг, полки с бочонками, пол. */
    fun hub(w: Int, h: Int, u: Float): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(0xFF3A271D.toInt())
        // доски стены
        val plank = 120f * u
        var x = 0f
        var i = 0
        while (x < w) {
            p.color = if (i % 2 == 0) 0xFF4A3226.toInt() else 0xFF45301F.toInt()
            c.drawRect(x, 0f, x + plank, h.toFloat(), p)
            p.color = 0xFF2B1D16.toInt(); c.drawRect(x, 0f, x + 5f * u, h.toFloat(), p)
            p.color = 0xFF2B1D16.toInt()
            c.drawCircle(x + plank / 2f, (200f + (i * 137) % 500) * u, 6f * u, p)
            x += plank; i++
        }
        // балка
        p.color = 0xFF2B1D16.toInt(); c.drawRect(0f, h * 0.16f, w.toFloat(), h * 0.16f + 40f * u, p)
        p.color = 0xFF5C4030.toInt(); c.drawRect(0f, h * 0.16f + 6f * u, w.toFloat(), h * 0.16f + 30f * u, p)
        // очаг внизу
        val fx = w * 0.5f; val fy = h * 0.93f
        p.shader = RadialGradient(fx, fy, w * 0.7f, 0x77FF8A2B, 0x00FF8A2B, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // пол
        p.color = 0xFF2E1F17.toInt(); c.drawRect(0f, h * 0.9f, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x55000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        return b
    }
}
