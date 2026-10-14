package com.tavernrush.game.render.pixel

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Рисует запечённый спрайт целым увеличением без сглаживания (меню, экран героев, итоги). */
object PixelBlit {
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = RectF()

    /** (gx, gy) — точка «земли» спрайта на экране; k — увеличение. */
    fun sprite(c: Canvas, s: Spr, gx: Float, gy: Float, k: Int, alpha: Int = 255) {
        dst.set(gx - s.ox * k, gy - s.oy * k, gx + (s.w - s.ox) * k, gy + (s.h - s.oy) * k)
        paint.alpha = alpha
        c.drawBitmap(s.bmp, null, dst, paint)
    }
}
