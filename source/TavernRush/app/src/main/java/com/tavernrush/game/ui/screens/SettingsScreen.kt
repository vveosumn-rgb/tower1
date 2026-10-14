package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.GfxPreset
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/**
 * Настройки: пресет графики, управление (режим и размер стика, сторона кнопки рывка, двойное касание),
 * сила тряски, звук и вибрация. Каждое нажатие переключает значение по кругу и сразу сохраняется.
 */
class SettingsScreen(app: GameApp) : Screen(app) {
    val rows = Array(ROWS) { Button("", Button.DARK) }
    val back = Button("Назад", Button.PRIMARY)

    override fun layout() {
        val u = ui.u
        val l = ui.cx - ui.contentW / 2f
        val r = ui.cx + ui.contentW / 2f
        val top = ui.top + 250f * u
        val bottom = ui.bottom - 230f * u
        val gap = 14f * u
        val h = minOf(190f * u, (bottom - top - gap * (ROWS - 1)) / ROWS)
        for (i in rows.indices) {
            rows[i].set(l, top + i * (h + gap), r, top + i * (h + gap) + h)
            rows[i].textSize = 60f * u
        }
        back.set(l, ui.bottom - 190f * u, r, ui.bottom - 190f * u + 140f * u).textSize = 64f * u
    }

    override fun onEnter() = refresh()

    private fun refresh() {
        val p = game.profile
        val gp = GfxPreset.fromOrdinal(p.gfxPreset)
        rows[0].label = "Графика: ${gp.label}"; rows[0].sub = gp.hint
        rows[1].label = "Управление: настроить"
        rows[1].sub = "Левый стик · положение · отклик · тренировка"
        rows[2].label = "Тряска экрана: ${if (p.shakePct == 0) "выкл" else "${p.shakePct}%"}"; rows[2].sub = "Можно уменьшить или отключить"
        rows[3].label = if (p.soundOn) "Звук: вкл" else "Звук: выкл"; rows[3].sub = null
        rows[4].label = if (p.vibrationOn) "Вибрация: вкл" else "Вибрация: выкл"; rows[4].sub = null
    }

    override fun draw(c: Canvas) {
        val u = ui.u
        val bg = app.hubBackdrop
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null) else c.drawColor(Palette.WOOD_DARK)
        d.dim(c, 70)
        val tx = d.txt
        tx.setup(100f * u, Palette.GOLD_LIGHT, outlineW = 18f * u)
        tx.fit("Настройки", ui.contentW, 100f * u)
        tx.draw(c, "Настройки", ui.cx, ui.top + 150f * u)
        for (b in rows) d.button(c, b, app.time)
        d.button(c, back, app.time)
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        pressButtons(id, x, y, *rows, back)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        val b = releaseButton(id, x, y) ?: return
        val p = game.profile
        when (b) {
            rows[0] -> p.gfxPreset = (p.gfxPreset + 1) % GfxPreset.entries.size
            rows[1] -> { game.openControls(); return }
            rows[2] -> p.shakePct = when (p.shakePct) { 100 -> 40; 40 -> 0; else -> 100 }
            rows[3] -> { game.toggleSound(); refresh(); return }
            rows[4] -> { game.toggleVibration(); refresh(); return }
            back -> { game.openMenu(); return }
        }
        game.saveSettings()
        refresh()
    }

    override fun onCancel() = resetPress()

    private companion object {
        const val ROWS = 5
    }
}
