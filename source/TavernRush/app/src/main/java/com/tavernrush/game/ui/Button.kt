package com.tavernrush.game.ui

import android.graphics.Bitmap
import android.graphics.RectF

/** Кнопка UI: прямоугольник, подпись, стиль. Нажатие засчитывается, если палец поднят над той же кнопкой. */
class Button(var label: String, var style: Int = PRIMARY) {
    val rect = RectF()
    var enabled = true
    var visible = true
    var pressed = false
    var sub: String? = null
    var icon: Bitmap? = null
    var textSize = 0f

    fun set(l: Float, t: Float, r: Float, b: Float): Button { rect.set(l, t, r, b); return this }
    fun hit(x: Float, y: Float, pad: Float = 0f): Boolean =
        visible && x >= rect.left - pad && x <= rect.right + pad && y >= rect.top - pad && y <= rect.bottom + pad

    companion object {
        const val PRIMARY = 0   // зелёная «Играть»
        const val GOLD = 1
        const val DARK = 2
        const val RED = 3
    }
}
