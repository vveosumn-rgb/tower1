package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.RectF
import com.tavernrush.game.render.Palette
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/** Пауза: продолжить, звук, вибрация, завершить забег (с подтверждением вторым нажатием). */
class PauseScreen(app: GameApp) : Screen(app) {
    val map = Button("Карта маршрута", Button.GOLD)
    val resume = Button("Продолжить", Button.PRIMARY)
    private val sound = Button("", Button.DARK)
    private val vibro = Button("", Button.DARK)
    val quit = Button(QUIT, Button.RED)
    private val panel = RectF()
    private var confirm = 0f

    override fun layout() {
        val u = ui.u
        val pw = minOf(ui.contentW, 860f * u)
        map.visible=world.adventure.active
        val ph = (if(map.visible)1160f else 1000f) * u
        val top = ui.h * 0.5f - ph * 0.5f
        panel.set(ui.cx - pw / 2f, top, ui.cx + pw / 2f, top + ph)
        val l = panel.left + 50f * u
        val r = panel.right - 50f * u
        var y = panel.top + 300f * u
        resume.set(l, y, r, y + 180f * u); resume.textSize = 72f * u
        y += 220f * u
        val half = (r - l - 30f * u) / 2f
        sound.set(l, y, l + half, y + 130f * u); sound.textSize = 42f * u
        vibro.set(r - half, y, r, y + 130f * u); vibro.textSize = 42f * u
        y += 190f * u
        if(map.visible){map.set(l,y-25f*u,r,y+110f*u);map.textSize=48f*u;y+=160f*u}
        quit.set(l, y, r, y + 140f * u); quit.textSize = 50f * u
    }

    override fun onEnter() {
        layout()
        confirm = 0f
        quit.label = QUIT
        refresh()
    }

    private fun refresh() {
        sound.label = if (game.profile.soundOn) "Звук: вкл" else "Звук: выкл"
        vibro.label = if (game.profile.vibrationOn) "Вибрация: вкл" else "Вибрация: выкл"
    }

    override fun update(dt: Float) {
        if (confirm > 0f) {
            confirm -= dt
            if (confirm <= 0f) quit.label = QUIT
        }
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        pressButtons(id, x, y, resume, sound, vibro, map, quit)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        when (releaseButton(id, x, y)) {
            resume -> game.resume()
            map -> game.openRouteMap()
            sound -> { game.toggleSound(); refresh() }
            vibro -> { game.toggleVibration(); refresh() }
            quit -> if (confirm > 0f) game.abandonRun() else { confirm = 3f; quit.label = CONFIRM }
        }
    }

    override fun onCancel() = resetPress()

    override fun draw(c: Canvas) {
        val u = ui.u
        app.drawWorld(c)
        app.hud.drawHud(c, false)
        d.dim(c, 175)
        d.panel(c, panel)
        val tx = d.txt
        tx.setup(104f * u, Palette.GOLD_LIGHT, outlineW = 20f * u)
        tx.draw(c, "Пауза", ui.cx, panel.top + 140f * u)
        val w = world
        tx.setup(44f * u, Palette.TEXT)
        val b = tx.buf.clear().add("Волна ").add(w.director.waveIndex + 1).add("  ·  ").addTime(w.time.toInt())
            .add("  ·  убито: ").addGrouped(w.run.kills)
        if(w.adventure.active){tx.fit(w.adventure.names[w.adventure.region],ui.contentW-90*u,44*u);tx.draw(c,w.adventure.names[w.adventure.region],ui.cx,panel.top+220*u)}else tx.draw(c, b, ui.cx, panel.top + 220f * u)
        d.button(c, resume, app.time)
        d.button(c, sound, app.time)
        d.button(c, vibro, app.time)
        if(map.visible)d.button(c,map,app.time)
        d.button(c, quit, app.time)
        tx.setup(30f * u, Palette.TEXT_DIM, outlineW = 0f)
        tx.draw(c, app.buildLabel, ui.cx, panel.bottom - 40f * u, false)
    }

    private companion object {
        const val QUIT = "Завершить забег"
        const val CONFIRM = "Точно? Нажмите ещё раз"
    }
}
