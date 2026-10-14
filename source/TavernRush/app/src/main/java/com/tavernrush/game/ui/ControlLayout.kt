package com.tavernrush.game.ui

import com.tavernrush.game.meta.Profile

/** R05: движение всегда слева; сторона кнопки рывка не переставляет стик. */
object ControlLayout {
    fun apply(ui: UiMetrics, p: Profile, j: Joystick) {
        val u = ui.u
        j.fixed = p.stickMode == 1
        j.sizeK = p.stickSize / 100f
        j.curve = if (p.stickResponse == 0) Joystick.CURVE else 1f
        j.touchZone.set(ui.left + 24f * u, maxOf(ui.top + 300f * u, ui.bottom - 740f * u), ui.cx - 24f * u, ui.bottom - 36f * u)
        val r = j.radius
        j.homeX = (ui.left + 260f * u).coerceIn(j.touchZone.left + r, j.touchZone.right - r)
        val height = when (p.stickHeight) { 0 -> 225f; 2 -> 385f; else -> 305f }
        j.homeY = (ui.bottom - height * u).coerceIn(j.touchZone.top + r, j.touchZone.bottom - r)
    }
    fun dashX(ui: UiMetrics, p: Profile): Float = if (p.dashRight) ui.right - 160f * ui.u else ui.left + 160f * ui.u
    fun dashY(ui: UiMetrics, p: Profile, j: Joystick): Float = if (p.dashRight) j.homeY else j.homeY - (j.radius * 1.45f + 175f * ui.u)
}
