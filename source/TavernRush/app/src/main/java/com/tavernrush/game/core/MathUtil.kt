package com.tavernrush.game.core

import kotlin.math.exp
import kotlin.math.sqrt

const val PI_F = 3.1415927f
const val TWO_PI = 6.2831855f
const val RAD_TO_DEG = 57.29578f

/** Мелкая математика без аллокаций. */
object M {
    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
    fun clampI(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    fun len(x: Float, y: Float): Float = sqrt(x * x + y * y)

    fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy
    }

    /** Плавная ступенька 0..1. */
    fun smooth(t: Float): Float {
        val c = clamp(t, 0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** Экспоненциальное приближение к цели, не зависящее от шага. */
    fun damp(current: Float, target: Float, rate: Float, dt: Float): Float =
        current + (target - current) * (1f - exp(-rate * dt))

    /** Детерминированный хэш пары целых (декор мира, вариации). */
    fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * -1640531535
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /** Хэш → [0,1). */
    fun hash01(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) ushr 8).toFloat() / 16777216f
}
