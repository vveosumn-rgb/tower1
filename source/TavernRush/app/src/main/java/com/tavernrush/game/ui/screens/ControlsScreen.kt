package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import com.tavernrush.game.render.Palette
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/** R05: одно место для настройки левого пальца; проверка без запуска забега. */
class ControlsScreen(app: GameApp) : Screen(app) {
    val rows = Array(6) { Button("", Button.DARK) }
    val practice = Button("Попробовать", Button.PRIMARY)
    val defaults = Button("Сбросить", Button.DARK)
    val back = Button("Назад", Button.GOLD)
    val movementTab = Button("Движение", Button.GOLD)
    val actionsTab = Button("Действия", Button.DARK)
    private var actionsPage = false
    override fun layout() {
        val u = ui.u
        val l = ui.cx - ui.contentW / 2f; val r = ui.cx + ui.contentW / 2f
        val tabs = ui.top + 220f * u
        movementTab.set(l, tabs, ui.cx - 12f * u, tabs + 144f * u).textSize = 47f * u
        actionsTab.set(ui.cx + 12f * u, tabs, r, tabs + 144f * u).textSize = 47f * u
        val top = ui.top + 410f * u
        val gap = 24f * u
        val h = minOf(190f * u, (ui.bottom - 435f * u - top - gap * 2f) / 3f)
        for (i in rows.indices) {
            val slot = i % 3
            rows[i].set(l, top + slot * (h + gap), r, top + slot * (h + gap) + h)
            rows[i].textSize = 52f * u
        }
        val y = ui.bottom - 350f * u
        practice.set(l, y, ui.cx - 12f * u, y + 128f * u).textSize = 51f * u
        defaults.set(ui.cx + 12f * u, y, r, y + 128f * u).textSize = 48f * u
        back.set(l, ui.bottom - 190f * u, r, ui.bottom - 50f * u).textSize = 60f * u
    }
    override fun onEnter() = refresh()
    private fun refresh() {
        for (i in rows.indices) rows[i].visible = (i >= 3) == actionsPage
        movementTab.style = if (actionsPage) Button.DARK else Button.GOLD
        actionsTab.style = if (actionsPage) Button.GOLD else Button.DARK
        val p = game.profile
        rows[0].label = "Стик: ${if (p.stickMode == 1) "фиксированный" else "локальный плавающий"}"
        rows[0].sub = "Всегда в нижней левой области"
        rows[1].label = "Размер: ${p.stickSize}%"; rows[1].sub = "70 · 100 · 120 · 140"
        rows[2].label = "Положение: ${when (p.stickHeight) { 0 -> "ниже"; 2 -> "выше"; else -> "обычное" }}"
        rows[2].sub = "Выберите удобную высоту большого пальца"
        rows[3].label = "Отклик: ${if (p.stickResponse == 1) "прямой" else "мягкий"}"
        rows[3].sub = "Прямой — быстрее; мягкий — точнее малый ход"
        rows[4].label = "Двойной тап: ${if (p.doubleTapDash) "вкл" else "выкл"}"
        rows[4].sub = "Только по стику; отдельная кнопка всегда есть"
        rows[5].label = "Рывок: ${if (p.dashRight) "справа" else "слева выше стика"}"
        rows[5].sub = "Не меняет сторону движения"
    }
    override fun draw(c: Canvas) {
        val u = ui.u
        app.hubBackdrop?.let { c.drawBitmap(it, 0f, 0f, null) } ?: c.drawColor(Palette.WOOD_DARK)
        d.dim(c, 90)
        val tx = d.txt
        tx.setup(88f * u, Palette.GOLD_LIGHT, outlineW = 16f * u)
        tx.fit("Управление", ui.contentW, 88f * u); tx.draw(c, "Управление", ui.cx, ui.top + 125f * u)
        tx.setup(40f * u, Palette.TEXT)
        tx.fit("Небольшой ход слева — весь мир свободен", ui.contentW, 40f * u)
        tx.draw(c, "Небольшой ход слева — весь мир свободен", ui.cx, ui.top + 183f * u)
        d.button(c, movementTab, app.time); d.button(c, actionsTab, app.time)
        for (b in rows) if (b.visible) d.button(c, b, app.time)
        d.button(c, practice, app.time); d.button(c, defaults, app.time); d.button(c, back, app.time)
    }
    override fun onDown(id: Int, x: Float, y: Float) { pressButtons(id, x, y, *rows, practice, defaults, back, movementTab, actionsTab) }
    override fun onUp(id: Int, x: Float, y: Float) {
        val b = releaseButton(id, x, y) ?: return
        val p = game.profile
        when (b) {
            movementTab -> { actionsPage = false; refresh(); return }
            actionsTab -> { actionsPage = true; refresh(); return }
            rows[0] -> p.stickMode = 1 - p.stickMode
            rows[1] -> p.stickSize = when (p.stickSize) { 70 -> 100; 100 -> 120; 120 -> 140; else -> 70 }
            rows[2] -> p.stickHeight = (p.stickHeight + 1) % 3
            rows[3] -> p.stickResponse = 1 - p.stickResponse
            rows[4] -> p.doubleTapDash = !p.doubleTapDash
            rows[5] -> p.dashRight = !p.dashRight
            defaults -> {
                p.stickMode = 1; p.stickSize = 100; p.stickHeight = 1
                p.stickResponse = 1; p.doubleTapDash = false; p.dashRight = true
            }
            practice -> { game.openPractice(); return }
            back -> { game.openSettings(); return }
        }
        p.controlsRevision = 5
        game.saveSettings(); app.joystick.release(); refresh()
    }
    override fun onCancel() = resetPress()
}
