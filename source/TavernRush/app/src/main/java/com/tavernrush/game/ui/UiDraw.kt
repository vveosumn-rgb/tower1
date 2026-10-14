package com.tavernrush.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.IconId
import com.tavernrush.game.render.Icons
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.Txt

/** Общие элементы оформления UI: деревянные панели, пергамент, кнопки, полосы, монеты. */
class UiDraw(val icons: Icons, val ui: UiMetrics) {
    val txt = Txt()
    val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun rrect(c: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float, color: Int) {
        fp.style = Paint.Style.FILL
        fp.color = color
        c.drawRoundRect(l, t, rt, b, rad, rad, fp)
    }

    /** Деревянная панель с золотой каймой. */
    fun panel(c: Canvas, rect: RectF, alpha: Int = 255) {
        val u = ui.u
        val rad = 34f * u
        rrect(c, rect.left, rect.top + 10f * u, rect.right, rect.bottom + 10f * u, rad, Palette.withAlpha(0xFF000000.toInt(), alpha / 3))
        rrect(c, rect.left, rect.top, rect.right, rect.bottom, rad, Palette.withAlpha(Palette.WOOD_DARK, alpha))
        rrect(c, rect.left + 8f * u, rect.top + 8f * u, rect.right - 8f * u, rect.bottom - 8f * u, rad * 0.8f, Palette.withAlpha(Palette.WOOD, alpha))
        // доски
        fp.color = Palette.withAlpha(Palette.WOOD_DARK, alpha / 3)
        var y = rect.top + 90f * u
        while (y < rect.bottom - 30f * u) {
            c.drawRect(rect.left + 14f * u, y, rect.right - 14f * u, y + 4f * u, fp)
            y += 90f * u
        }
        sp.color = Palette.withAlpha(Palette.GOLD_DARK, alpha)
        sp.strokeWidth = 5f * u
        c.drawRoundRect(rect.left + 4f * u, rect.top + 4f * u, rect.right - 4f * u, rect.bottom - 4f * u, rad * 0.9f, rad * 0.9f, sp)
    }

    /** Пергаментная карточка. */
    fun parchment(c: Canvas, l: Float, t: Float, rt: Float, b: Float, edge: Int = Palette.PARCHMENT_DARK) {
        val u = ui.u
        val rad = 26f * u
        rrect(c, l, t + 8f * u, rt, b + 8f * u, rad, 0x55000000)
        rrect(c, l, t, rt, b, rad, edge)
        rrect(c, l + 6f * u, t + 6f * u, rt - 6f * u, b - 6f * u, rad * 0.8f, Palette.PARCHMENT)
    }

    fun button(c: Canvas, b: Button, time: Float) {
        if (!b.visible) return
        val u = ui.u
        val rect = b.rect
        val base: Int
        val light: Int
        val dark: Int
        when {
            !b.enabled -> { base = Palette.DISABLED; light = 0xFF8A7E75.toInt(); dark = 0xFF453B35.toInt() }
            b.style == Button.PRIMARY -> { base = Palette.GREEN; light = Palette.GREEN_LIGHT; dark = Palette.GREEN_DARK }
            b.style == Button.GOLD -> { base = Palette.GOLD; light = Palette.GOLD_LIGHT; dark = Palette.GOLD_DARK }
            b.style == Button.RED -> { base = 0xFFD84B3C.toInt(); light = 0xFFF07A6A.toInt(); dark = Palette.RED_DARK }
            else -> { base = Palette.WOOD_LIGHT; light = 0xFF8D6A52.toInt(); dark = Palette.WOOD_DARK }
        }
        val press = if (b.pressed) 6f * u else 0f
        val rad = minOf(rect.height() * 0.32f, 40f * u)
        rrect(c, rect.left, rect.top + 12f * u, rect.right, rect.bottom + 12f * u, rad, 0x66000000)
        rrect(c, rect.left, rect.top + press, rect.right, rect.bottom + 10f * u, rad, Palette.OUTLINE)
        rrect(c, rect.left + 4f * u, rect.top + 4f * u + press, rect.right - 4f * u, rect.bottom + 4f * u, rad, dark)
        rrect(c, rect.left + 4f * u, rect.top + 4f * u + press, rect.right - 4f * u, rect.bottom - 4f * u + press * 0.5f, rad, base)
        val shine = if (b.enabled && b.style == Button.GOLD) (150f + 16f * kotlin.math.sin(time * 1.2f)).toInt() else 150
        rrect(c, rect.left + 18f * u, rect.top + 12f * u + press, rect.right - 18f * u, rect.top + rect.height() * 0.38f + press, rad * 0.6f, Palette.withAlpha(light, shine))
        val size = if (b.textSize > 0f) b.textSize else minOf(rect.height() * 0.42f, 64f * u)
        val textColor = if (b.style == Button.GOLD && b.enabled) Palette.INK else Palette.TEXT
        val outline = if (b.style == Button.GOLD && b.enabled) 0 else Palette.OUTLINE
        var cx = rect.centerX()
        val icon = b.icon
        val sub = b.sub
        txt.setup(size, textColor, Paint.Align.CENTER, outline, if (outline == 0) 0f else size * 0.14f)
        val maxW = rect.width() - 50f * u - (if (icon != null) rect.height() * 0.8f else 0f)
        txt.fit(b.label, maxW, size)
        if (icon != null) {
            val isz = rect.height() * 0.62f
            val tw = txt.width(b.label)
            val total = isz + 14f * u + tw
            val ix = cx - total / 2f
            r.set(ix, rect.centerY() - isz / 2f + press, ix + isz, rect.centerY() + isz / 2f + press)
            c.drawBitmap(icon, null, r, bp)
            cx = ix + isz + 14f * u + tw / 2f
        }
        if (sub != null) {
            txt.draw(c, b.label, cx, rect.centerY() - size * 0.12f + press, outline != 0)
            val ss = size * 0.62f
            txt.setup(ss, textColor, Paint.Align.CENTER, outline, if (outline == 0) 0f else ss * 0.14f)
            txt.fit(sub, maxW, ss)
            txt.draw(c, sub, cx, rect.centerY() + ss * 1.0f + press, outline != 0)
        } else {
            txt.draw(c, b.label, cx, rect.centerY() + size * 0.36f + press, outline != 0)
        }
    }

    fun bar(c: Canvas, l: Float, t: Float, rt: Float, b: Float, frac: Float, color: Int, back: Int) {
        val rad = (b - t) * 0.5f
        rrect(c, l - 4f * ui.u, t - 4f * ui.u, rt + 4f * ui.u, b + 4f * ui.u, rad + 4f * ui.u, Palette.OUTLINE)
        rrect(c, l, t, rt, b, rad, back)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0.001f) {
            val x = l + (rt - l) * f
            rrect(c, l, t, maxOf(x, l + 2f * rad), b, rad, color)
            rrect(c, l + 6f * ui.u, t + (b - t) * 0.18f, maxOf(x - 6f * ui.u, l + 2f * rad - 6f * ui.u), t + (b - t) * 0.42f, rad * 0.4f, Palette.withAlpha(0xFFFFFFFF.toInt(), 70))
        }
    }

    /** Иконка: растр берётся размером size (кэш), рисуется с масштабом k (анимации без новых растров). */
    fun icon(c: Canvas, id: IconId, cx: Float, cy: Float, size: Float, alpha: Int = 255, k: Float = 1f) {
        val b = icons.get(id, size.toInt())
        val hs = size * 0.5f * k
        r.set(cx - hs, cy - hs, cx + hs, cy + hs)
        bp.alpha = alpha
        c.drawBitmap(b, null, r, bp)
        bp.alpha = 255
    }

    /** Значок монеты + число (TextBuf). Возвращает правый край. */
    fun goldCounter(c: Canvas, x: Float, y: Float, size: Float, value: Long, align: Paint.Align = Paint.Align.LEFT): Float {
        val b = txt.buf.clear().addGrouped(value)
        txt.setup(size, Palette.GOLD_LIGHT, Paint.Align.LEFT)
        val tw = txt.width(b)
        val total = size * 1.1f + 12f * ui.u + tw
        val l = when (align) { Paint.Align.LEFT -> x; Paint.Align.CENTER -> x - total / 2f; else -> x - total }
        icon(c, IconId.LUCKY_COIN, l + size * 0.55f, y - size * 0.34f, size * 1.1f)
        txt.draw(c, b, l + size * 1.1f + 12f * ui.u, y)
        return l + total
    }

    /** Затемнение экрана. */
    fun dim(c: Canvas, alpha: Int) {
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(0xFF0E0A12.toInt(), alpha)
        c.drawRect(0f, 0f, ui.w.toFloat(), ui.h.toFloat(), fp)
    }
}
