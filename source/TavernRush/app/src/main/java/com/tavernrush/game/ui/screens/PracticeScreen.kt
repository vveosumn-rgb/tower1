package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Player
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.PixelBlit
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.systems.World
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.ControlLayout
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen
import kotlin.math.abs
import kotlin.math.sqrt

/** Независимая тренировка: не запускает World, не меняет экономику, RNG, встречи или статистику. */
class PracticeScreen(app: GameApp) : Screen(app) {
    val back = Button("Настроить", Button.GOLD)
    val reset = Button("Заново", Button.DARK)
    val dash = Button("Рывок", Button.PRIMARY)
    val field = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var heroX = 0f; private set
    var heroY = 0f; private set
    var marks = 0; private set
    var charges = Player.MAX_DASH; private set
    private var vx = 0f; private var vy = 0f
    private var lookX = 1f; private var lookY = 0f
    private var dashT = 0f; private var dashX = 1f; private var dashY = 0f
    private var recharge = 0f
    private var elapsed = 0f
    private var tapT = -10f; private var tapX = 0f; private var tapY = 0f
    private var downId = -1; private var downT = 0f; private var downX = 0f; private var downY = 0f
    private fun goalX() = field.left + field.width() * when (marks % 3) { 0 -> 0.23f; 1 -> 0.77f; else -> 0.5f }
    private fun goalY() = field.top + field.height() * when (marks % 3) { 0 -> 0.35f; 1 -> 0.65f; else -> 0.23f }
    override fun layout() {
        ControlLayout.apply(ui, game.profile, app.joystick)
        val u = ui.u
        back.set(ui.left + 28f * u, ui.top + 34f * u, ui.cx - 18f * u, ui.top + 150f * u).textSize = 43f * u
        reset.set(ui.cx + 18f * u, ui.top + 34f * u, ui.right - 28f * u, ui.top + 150f * u).textSize = 43f * u
        field.set(ui.left + 60f * u, ui.top + 340f * u, ui.right - 60f * u, app.joystick.homeY - app.joystick.radius - 90f * u)
        val x = ControlLayout.dashX(ui, game.profile); val y = ControlLayout.dashY(ui, game.profile, app.joystick)
        dash.set(x - 96f * u, y - 96f * u, x + 96f * u, y + 96f * u).textSize = 48f * u
        if (heroX != 0f || heroY != 0f) { heroX = heroX.coerceIn(field.left, field.right); heroY = heroY.coerceIn(field.top, field.bottom) }
    }
    override fun onEnter() { layout(); restart() }
    fun restart() {
        onCancel()
        heroX = field.centerX(); heroY = field.centerY()
        vx = 0f; vy = 0f; marks = 0; charges = Player.MAX_DASH
        dashT = 0f; recharge = 0f; elapsed = 0f; lookX = 1f; lookY = 0f
    }
    private fun attemptDash() {
        if (charges <= 0 || dashT > 0f) return
        val j = app.joystick
        val len = sqrt(j.outX * j.outX + j.outY * j.outY)
        dashX = if (len > 0.01f) j.outX / len else lookX
        dashY = if (len > 0.01f) j.outY / len else lookY
        if (charges == Player.MAX_DASH) recharge = World.DASH_RECHARGE
        charges--; dashT = World.DASH_TIME
        game.events.sfx(Sfx.CLICK)
    }
    private fun approach(a: Float, b: Float, step: Float): Float = if (a < b) minOf(b, a + step) else maxOf(b, a - step)
    override fun update(dt: Float) {
        elapsed += dt
        val j = app.joystick; val u = ui.u
        if (charges < Player.MAX_DASH) {
            recharge -= dt
            if (recharge <= 0f) { charges++; recharge = if (charges < Player.MAX_DASH) World.DASH_RECHARGE else 0f }
        }
        val len = sqrt(j.outX * j.outX + j.outY * j.outY)
        if (len > 0.01f) { lookX = j.outX / len; lookY = j.outY / len }
        if (dashT > 0f) {
            val t = minOf(dt, dashT)
            heroX += dashX * World.DASH_SPEED * u * t
            heroY += dashY * World.DASH_SPEED * u * t
            dashT = maxOf(0f, dashT - dt)
        } else {
            val tx = j.outX * World.BASE_SPEED * u; val ty = j.outY * World.BASE_SPEED * u
            vx = approach(vx, tx, (if (abs(tx) < abs(vx)) World.DECEL else World.ACCEL) * u * dt)
            vy = approach(vy, ty, (if (abs(ty) < abs(vy)) World.DECEL else World.ACCEL) * u * dt)
            heroX += vx * dt; heroY += vy * dt
        }
        heroX = heroX.coerceIn(field.left + 24f * u, field.right - 24f * u)
        heroY = heroY.coerceIn(field.top + 24f * u, field.bottom - 24f * u)
        val dx = heroX - goalX(); val dy = heroY - goalY()
        if (dx * dx + dy * dy < 55f * u * 55f * u) { marks++; game.events.sfx(Sfx.CLICK) }
    }
    override fun draw(c: Canvas) {
        val u = ui.u
        c.drawColor(0xFF14252B.toInt())
        paint.color = 0xFF1D343B.toInt(); paint.style = Paint.Style.FILL
        c.drawRoundRect(field, 30f * u, 30f * u, paint)
        paint.color = 0xFF2A444B.toInt(); paint.strokeWidth = 2f * u
        var x = field.left + 80f * u
        while (x < field.right) { c.drawLine(x, field.top, x, field.bottom, paint); x += 80f * u }
        var y = field.top + 80f * u
        while (y < field.bottom) { c.drawLine(field.left, y, field.right, y, paint); y += 80f * u }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 8f * u; paint.color = Palette.GOLD
        c.drawCircle(goalX(), goalY(), 55f * u, paint)
        paint.style = Paint.Style.FILL; paint.color = 0x447CFF8A
        c.drawCircle(goalX(), goalY(), 40f * u, paint)
        val index = game.selectedHero.coerceIn(0, Content.heroes.size - 1)
        val anim = if (abs(vx) + abs(vy) > 1f || dashT > 0f) SpriteBank.heroWalk[index] else SpriteBank.heroIdle[index]
        val facing = if (lookX < 0f) 1 else 0
        val frame = anim.frame(facing, (elapsed * 7f).toInt())
        val scale = (125f * u / frame.h).toInt().coerceAtLeast(1)
        PixelBlit.sprite(c, anim.shadow(facing, (elapsed * 7f).toInt()), heroX, heroY, scale)
        PixelBlit.sprite(c, frame, heroX, heroY, scale)
        val tx = d.txt
        tx.setup(64f * u, Palette.GOLD_LIGHT)
        tx.fit("Тренировка движения", ui.contentW, 64f * u); tx.draw(c, "Тренировка движения", ui.cx, ui.top + 238f * u)
        tx.setup(37f * u, Palette.TEXT)
        val text = tx.buf.clear().add("Дойдите до круга · пройдено ").add(marks)
        tx.draw(c, text, ui.cx, ui.top + 296f * u)
        app.joystick.draw(c, app.frameDt)
        d.button(c, dash, app.time); d.button(c, back, app.time); d.button(c, reset, app.time)
        tx.setup(32f * u, Palette.TEXT)
        tx.draw(c, tx.buf.clear().add("Заряды: ").add(charges), dash.rect.centerX(), dash.rect.bottom + 44f * u)
        tx.setup(34f * u, Palette.TEXT_DIM)
        tx.fit("Без врагов, наград и потерь. Движение — слева.", ui.contentW, 34f * u)
        tx.draw(c, "Без врагов, наград и потерь. Движение — слева.", ui.cx, ui.bottom - 10f * u)
    }
    override fun onDown(id: Int, x: Float, y: Float) {
        if (pressButtons(id, x, y, back, reset)) { tapT = -10f; downId = -1; return }
        if (dash.hit(x, y, 18f * ui.u)) { tapT = -10f; downId = -1; attemptDash(); return }
        if (!app.joystick.down(id, x, y)) { if (!app.joystick.active) tapT = -10f; return }
        downId = id; downT = app.time; downX = x; downY = y
        val dx = x - tapX; val dy = y - tapY
        if (game.profile.doubleTapDash && app.time - tapT < 0.3f && dx * dx + dy * dy < 110f * ui.u * 110f * ui.u) { tapT = -10f; attemptDash() }
    }
    override fun onMove(id: Int, x: Float, y: Float) = app.joystick.move(id, x, y)
    override fun onUp(id: Int, x: Float, y: Float) {
        when (releaseButton(id, x, y)) { back -> game.openControls(); reset -> restart() }
        if (id == downId) {
            val dx = x - downX; val dy = y - downY
            if (app.time - downT < 0.22f && dx * dx + dy * dy < 45f * ui.u * 45f * ui.u) { tapT = app.time; tapX = x; tapY = y }
            downId = -1
        }
        app.joystick.up(id)
    }
    override fun onCancel() { resetPress(); app.joystick.release(); downId = -1; tapT = -10f; vx = 0f; vy = 0f }
}
