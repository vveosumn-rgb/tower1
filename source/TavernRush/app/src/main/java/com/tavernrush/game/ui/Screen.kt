package com.tavernrush.game.ui

import android.graphics.Canvas

/** Экран UI. Касания приходят уже в игровом потоке (через InputQueue). */
abstract class Screen(protected val app: GameApp) {
    protected val ui: UiMetrics get() = app.ui
    protected val d: UiDraw get() = app.draw
    protected val game get() = app.game
    protected val world get() = app.game.world

    /** Пересчёт раскладки (смена размера/отступов). */
    open fun layout() {}
    /** Экран стал активным. */
    open fun onEnter() {}
    open fun update(dt: Float) {}
    abstract fun draw(c: Canvas)
    open fun onDown(id: Int, x: Float, y: Float) {}
    open fun onMove(id: Int, x: Float, y: Float) {}
    open fun onUp(id: Int, x: Float, y: Float) {}
    open fun onCancel() {}

    // ---- общий помощник для кнопок
    private var pressed: Button? = null
    private var pressedId = -1

    protected fun pressButtons(id: Int, x: Float, y: Float, vararg buttons: Button): Boolean {
        for (b in buttons) {
            if (b.visible && b.hit(x, y, 10f * ui.u)) {
                pressed?.pressed = false
                pressed = b; pressedId = id
                b.pressed = true
                return true
            }
        }
        return false
    }

    /** Возвращает нажатую кнопку, если палец поднят над ней же. */
    protected fun releaseButton(id: Int, x: Float, y: Float): Button? {
        val b = pressed ?: return null
        if (id != pressedId) return null
        b.pressed = false
        pressed = null; pressedId = -1
        return if (b.hit(x, y, 24f * ui.u) && b.enabled) b else null
    }

    /** Сбросить нажатие (смена экрана, отмена жеста). */
    fun resetPress() {
        pressed?.pressed = false
        pressed = null; pressedId = -1
    }
}
