package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import com.tavernrush.game.content.IconId
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.render.Palette
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/** Заставка: логотип, 1.6 с, тап — пропустить. */
class SplashScreen(app: GameApp) : Screen(app) {
    override fun draw(c: Canvas) {
        val u = ui.u
        val bg = app.menuBackdrop
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null) else c.drawColor(0xFF1B1424.toInt())
        d.dim(c, 170)
        val t = game.screenTime
        val a = (255f * Ease.window(t, Game.SPLASH_TIME, 0.35f, 0.3f)).toInt()
        val pop = 0.75f + 0.25f * Ease.outBack(t / 0.5f)
        val cy = ui.h * 0.42f
        d.icon(c, IconId.MUG, ui.cx, cy - 110f * u, 320f * u, a, pop)
        val tx = d.txt
        tx.setup(150f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 24f * u)
        tx.fit(TITLE, ui.contentW, 150f * u)
        tx.draw(c, TITLE, ui.cx, cy + 170f * u)
        tx.setup(52f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
        tx.fit(SUB, ui.contentW, 52f * u)
        tx.draw(c, SUB, ui.cx, cy + 260f * u)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        if (game.screenTime > 0.25f) game.go(ScreenId.MENU)
    }

    private companion object {
        const val TITLE = "Tavern Rush"
        const val SUB = "Трактирщик против ночи"
    }
}
