package com.tavernrush.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.tavernrush.game.render.Palette
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * R05: неподвижный левый стик по умолчанию. Альтернатива — локальный плавающий в том же левом блоке.
 * Один палец владеет жестом до UP/CANCEL; правая половина не захватывается. Мёртвая зона 8%,
 * прямой или мягкий отклик; визуальная рукоятка никогда не выходит за радиус.
 */
class Joystick(private val ui: UiMetrics) {
    var active = false
        private set
    var pointer = -1
        private set
    var baseX = 0f
    var baseY = 0f
    var knobX = 0f
    var knobY = 0f
    var outX = 0f
        private set
    var outY = 0f
        private set
    private var idleFade = 0f
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Фиксированный режим, множитель размера и «домашняя» точка стика (задаёт GameApp). */
    var fixed = true
    var curve = 1f
    /** Локальная область левого большого пальца; ни меню, ни правая половина не дают движение. */
    val touchZone = android.graphics.RectF()
    var sizeK = 1f
    var homeX = 0f
    var homeY = 0f

    val radius: Float get() {
        val desired = 135f * ui.u * sizeK
        if (touchZone.isEmpty) return desired
        // Большой размер не должен аварийно обрезаться вырезом/узкой поверхностью.
        val available = minOf(touchZone.width(), touchZone.height()) * 0.5f - 4f * ui.u
        return minOf(desired, available.coerceAtLeast(1f))
    }

    /** false — касание вне зоны фиксированного стика (его не захватываем). */
    fun canStart(x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || !touchZone.contains(x, y)) return false
        if (!fixed) return true
        val dx = x - homeX; val dy = y - homeY
        val lim = radius * 1.45f
        return dx * dx + dy * dy <= lim * lim
    }

    fun down(id: Int, x: Float, y: Float): Boolean {
        if (active || !canStart(x, y)) return false
        if (fixed) {
            val dx = x - homeX; val dy = y - homeY
            val lim = radius * 1.45f
            if (dx * dx + dy * dy > lim * lim) return false
            baseX = homeX; baseY = homeY
        } else {
            baseX = x.coerceIn(touchZone.left + radius, touchZone.right - radius)
            baseY = y.coerceIn(touchZone.top + radius, touchZone.bottom - radius)
        }
        active = true; pointer = id
        knobX = x; knobY = y
        outX = 0f; outY = 0f
        if (fixed) update(x, y)
        return true
    }

    fun move(id: Int, x: Float, y: Float) {
        if (!active || id != pointer || !x.isFinite() || !y.isFinite()) return
        update(x, y)
    }

    private fun update(x: Float, y: Float) {
        var dx = x - baseX
        var dy = y - baseY
        val r = radius
        val d = sqrt(dx * dx + dy * dy)
        if (d > r) {
            if (!fixed) {
                // База тянется за пальцем.
                val k = (d - r) / d
                baseX = (baseX + dx * k).coerceIn(touchZone.left + r, touchZone.right - r)
                baseY = (baseY + dy * k).coerceIn(touchZone.top + r, touchZone.bottom - r)
                dx = x - baseX; dy = y - baseY
                val clampedD = sqrt(dx * dx + dy * dy)
                if (clampedD > r) { dx *= r / clampedD; dy *= r / clampedD }
            } else {
                dx *= r / d; dy *= r / d
            }
        }
        knobX = baseX + dx; knobY = baseY + dy
        // Рукоятка и плавающая база остаются внутри локального левого управления.
        val dd = sqrt(dx * dx + dy * dy)
        val m = dd / r
        if (m < DEAD || dd < 0.001f) { outX = 0f; outY = 0f } else {
            val mag = ((m - DEAD) / (1f - DEAD)).coerceAtMost(1f).pow(curve)
            outX = dx / dd * mag; outY = dy / dd * mag
        }
    }

    fun up(id: Int) {
        if (id != pointer) return
        release()
    }

    fun release() {
        active = false; pointer = -1
        outX = 0f; outY = 0f
        knobX = homeX; knobY = homeY
    }

    fun draw(c: Canvas, dt: Float) {
        val show = active || fixed
        idleFade = if (fixed) 1f else if (show) minOf(1f, idleFade + dt * 8f) else maxOf(0f, idleFade - dt * 5f)
        if (idleFade <= 0f) return
        val a = if (active) idleFade else idleFade * 0.65f
        val r = radius
        val bx = if (active) baseX else homeX
        val by = if (active) baseY else homeY
        val kx = if (active) knobX else homeX
        val ky = if (active) knobY else homeY
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(0xFF000000.toInt(), (60 * a).toInt())
        c.drawCircle(bx, by, r, fp)
        sp.color = Palette.withAlpha(0xFFFFF6E6.toInt(), (110 * a).toInt())
        sp.strokeWidth = 6f * ui.u
        c.drawCircle(bx, by, r, sp)
        val kr = 56f * ui.u * sizeK.coerceIn(0.8f, 1.2f)
        fp.color = Palette.withAlpha(0xFF000000.toInt(), (70 * a).toInt())
        c.drawCircle(kx, ky + 6f * ui.u, kr + 2f * ui.u, fp)
        fp.color = Palette.withAlpha(Palette.GOLD, (230 * a).toInt())
        c.drawCircle(kx, ky, kr, fp)
        fp.color = Palette.withAlpha(Palette.GOLD_LIGHT, (200 * a).toInt())
        c.drawCircle(kx - 14f * ui.u, ky - 16f * ui.u, kr * 0.36f, fp)
    }

    companion object {
        const val DEAD = 0.08f
        const val CURVE = 1.25f
    }
}
