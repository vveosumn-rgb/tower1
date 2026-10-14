package com.tavernrush.game.ui

/** Кривые анимации UI. Вход и выход — 0..1. */
object Ease {
    fun clamp01(t: Float): Float = if (t < 0f) 0f else if (t > 1f) 1f else t
    fun outCubic(t: Float): Float { val x = 1f - clamp01(t); return 1f - x * x * x }
    fun inOutSine(t: Float): Float = (0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * clamp01(t)))
    fun outBack(t: Float): Float {
        val x = clamp01(t) - 1f
        val s = 1.70158f
        return 1f + (s + 1f) * x * x * x + s * x * x
    }
    /** Появление/исчезание: 0 → 1 за fadeIn, держится, 1 → 0 за fadeOut перед end. */
    fun window(t: Float, end: Float, fadeIn: Float, fadeOut: Float): Float {
        if (t < 0f || t > end) return 0f
        val a = if (fadeIn > 0f) clamp01(t / fadeIn) else 1f
        val b = if (fadeOut > 0f) clamp01((end - t) / fadeOut) else 1f
        return if (a < b) a else b
    }
}
