package com.tavernrush.game.ui

/**
 * Масштаб UI: 1u = 1px на эталонном экране 1080×1920; u = min(w/1080, h/1920).
 * Безопасные отступы — вырезы камеры и системные панели (px).
 */
class UiMetrics {
    var w = 1080
        private set
    var h = 1920
        private set
    var u = 1f
        private set
    var safeL = 0f
        private set
    var safeT = 0f
        private set
    var safeR = 0f
        private set
    var safeB = 0f
        private set

    fun set(width: Int, height: Int, l: Int, t: Int, r: Int, b: Int) {
        w = width; h = height
        u = minOf(w / 1080f, h / 1920f)
        safeL = l.toFloat(); safeT = t.toFloat(); safeR = r.toFloat(); safeB = b.toFloat()
    }

    val cx: Float get() = w * 0.5f
    val left: Float get() = safeL
    val top: Float get() = safeT
    val right: Float get() = w - safeR
    val bottom: Float get() = h - safeB
    /** Ширина контента: не шире 1000u (планшеты) и не шире безопасной области. */
    val contentW: Float get() = minOf(1000f * u, (right - left) - 40f * u)
}
