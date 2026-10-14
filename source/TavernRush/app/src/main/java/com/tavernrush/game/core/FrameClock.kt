package com.tavernrush.game.core

/**
 * Монотонный накопитель времени. Быстрая отрисовка не создаёт лишнее игровое время:
 * пока не накоплен целый период дисплея, шаг равен нулю.
 * После долгой блокировки отбрасываем долг, а не телепортируем героя.
 */
class FrameClock(private val maxFrame: Double = 0.1) {
    private var accumulator = 0.0

    fun reset() { accumulator = 0.0 }

    fun advance(elapsedSeconds: Double, refreshHz: Float): Float {
        if (!elapsedSeconds.isFinite() || elapsedSeconds <= 0.0) return 0f
        accumulator = minOf(maxFrame, accumulator + elapsedSeconds)
        val period = period(refreshHz)
        val frames = ((accumulator + 1e-12) / period).toInt()
        if (frames == 0) return 0f
        val delta = minOf(maxFrame, frames * period)
        accumulator = maxOf(0.0, accumulator - delta)
        return delta.toFloat()
    }

    /** Не busy-wait: короткий сон до следующего периода, максимум 8 мс. */
    fun waitMillis(refreshHz: Float): Long =
        ((period(refreshHz) - accumulator) * 1000.0).toLong().coerceIn(1L, 8L)

    private fun period(hz: Float): Double {
        val safe = if (hz.isFinite() && hz in 30f..240f) hz else 60f
        return 1.0 / safe.toDouble()
    }
}