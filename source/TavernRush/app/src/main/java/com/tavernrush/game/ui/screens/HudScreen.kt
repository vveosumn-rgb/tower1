package com.tavernrush.game.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.IconId
import com.tavernrush.game.content.WaveEventType
import com.tavernrush.game.core.Game
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Player
import com.tavernrush.game.systems.BossSystem
import com.tavernrush.game.systems.Nemesis
import com.tavernrush.game.systems.World
import com.tavernrush.game.render.Palette
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Забег: мир, HUD, плавающий джойстик, баннеры волн и событий, подсказки первого забега. */
class HudScreen(app: GameApp) : Screen(app) {
    val actionBtn=Button("Приём",Button.PRIMARY)
    val stanceBtn=Button("Вариант",Button.DARK)
    val contextBtn=Button("К ориентиру",Button.GOLD)
    val pauseBtn = Button("", Button.DARK)
    /** Кнопка рывка (круг у большого пальца; сторона — в настройках). */
    val dashBtn = Button("", Button.DARK)
    /** R04: плашки друзей, кнопка приказа/смены, стрелка и баннеры встречи. */
    val teamHud = TeamHud(app)
    private var touched = false
    private var lastTapT = -10f
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var downT = 0f
    private var downX = 0f
    private var downY = 0f
    private var downId = -1
    private var dashPulse = 0f
    private val arc = RectF()
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tri = Path()
    private var redVignette: Bitmap? = null
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private var barTop = 0f
    private var rowY = 0f

    override fun layout() {
        val u = ui.u
        barTop = ui.top + 22f * u
        rowY = barTop + 120f * u
        val s = 112f * u
        val top = barTop + 62f * u
        pauseBtn.set(ui.right - 26f * u - s, top, ui.right - 26f * u, top + s)
        layoutDash()
        actionBtn.set(ui.right-285*u,ui.bottom-990*u,ui.right-35*u,ui.bottom-850*u)
        stanceBtn.set(ui.right-285*u,ui.bottom-1145*u,ui.right-35*u,ui.bottom-1020*u)
        contextBtn.set(ui.cx-170*u,ui.bottom-160*u,ui.cx+170*u,ui.bottom-45*u)
        actionBtn.textSize=38*u;stanceBtn.textSize=34*u;contextBtn.textSize=38*u
        for (def in Content.weapons) app.icons.get(def.icon, (84f * u * 0.72f).toInt().coerceAtLeast(8))
        for (def in Content.passives) app.icons.get(def.icon, (64f * u * 0.72f).toInt().coerceAtLeast(8))
        redVignette?.recycle()
        redVignette = makeVignette((ui.w / 6).coerceAtLeast(16), (ui.h / 6).coerceAtLeast(16))
    }

    fun layoutDash() {
        val u = ui.u
        val r = 96f * u
        com.tavernrush.game.ui.ControlLayout.apply(ui, game.profile, app.joystick)
        val cx = com.tavernrush.game.ui.ControlLayout.dashX(ui, game.profile)
        val cy = com.tavernrush.game.ui.ControlLayout.dashY(ui, game.profile, app.joystick)
        dashBtn.set(cx - r, cy - r, cx + r, cy + r)
        teamHud.layout(pauseBtn.rect, dashBtn.rect)
    }

    private fun makeVignette(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = maxOf(w, h) * 0.72f
        p.shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0x00FF0000, 0x00FF0000, 0xC0D01010.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return b
    }

    override fun onEnter() {
        if (world.time < 0.05f) touched = false
        clearTap()
        layoutDash()
    }

    private fun tryDash() {
        val j = app.joystick
        if (world.dash(j.outX, j.outY)) dashPulse = 1f
    }

    override fun update(dt: Float) {
        if (dashPulse > 0f) dashPulse -= dt * 4f
        teamHud.update(dt)
        val ap = app.autopilot
        if (ap != null) {
            ap.steer(world)
        } else {
            world.moveX = app.joystick.outX
            world.moveY = app.joystick.outY
        }
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        if (pressButtons(id, x, y, pauseBtn)) { clearTap(); return }
        if(pressButtons(id,x,y,actionBtn,stanceBtn,contextBtn)){clearTap();return}
        val u = ui.u
        if (dashBtn.hit(x, y, 18f * u)) { clearTap(); tryDash(); return }
        if (teamHud.press(x, y, world)) { clearTap(); return }
        val j = app.joystick
        if (!j.down(id, x, y)) { if (!j.active) clearTap(); return }
        touched = true
        downT = app.time; downX = x; downY = y; downId = id
        // Опциональный жест строго в зоне движения, только одним захваченным пальцем.
        val dxT = x - lastTapX; val dyT = y - lastTapY
        if (game.profile.doubleTapDash && app.time - lastTapT < 0.3f && dxT * dxT + dyT * dyT < (110f * u) * (110f * u)) {
            lastTapT = -10f
            tryDash()
        }
    }

    override fun onMove(id: Int, x: Float, y: Float) = app.joystick.move(id, x, y)

    override fun onUp(id: Int, x: Float, y: Float) {
        when(releaseButton(id,x,y)){pauseBtn->game.pause();actionBtn->game.useHeroAction();stanceBtn->game.switchHeroAction();contextBtn->game.interactWorld()}
        if (id == downId) {
            val dx = x - downX; val dy = y - downY
            if (app.time - downT < 0.22f && dx * dx + dy * dy < (45f * ui.u) * (45f * ui.u)) { lastTapT = app.time; lastTapX = x; lastTapY = y }
            downId = -1
        }
        app.joystick.up(id)
    }

    private fun clearTap() { lastTapT = -10f; downId = -1 }

    override fun onCancel() {
        resetPress(); clearTap()
        app.joystick.release()
        world.moveX = 0f; world.moveY = 0f
    }

    override fun draw(c: Canvas) {
        app.drawWorld(c)
        drawHud(c, true)
        if(!world.adventure.active)drawBanners(c)
        drawOutcome(c)
        app.joystick.draw(c, app.frameDt)
        drawDash(c)
        teamHud.draw(c, world)
        drawR03(c)
        if(world.adventure.active)drawAdventure(c)else drawHints(c)
        drawActions(c)
    }

    private fun drawActions(c:Canvas){
        val h=world.heroAction;val u=ui.u
        actionBtn.label=h.label();actionBtn.sub=if(h.cooldown>0f)"Готов через ${kotlin.math.ceil(h.cooldown).toInt()} с" else if(h.counterReady)"Окно ответа" else "Готов";actionBtn.enabled=h.cooldown<=0f&&!world.player.dead
        stanceBtn.label="Вариант ${h.style+1}";stanceBtn.enabled=h.cooldown<=0f&&!world.player.dead
        contextBtn.visible=world.adventure.active;contextBtn.label=world.adventure.actionLabel()
        d.button(c,actionBtn,app.time);d.button(c,stanceBtn,app.time);d.button(c,contextBtn,app.time)
    }
    private fun drawAdventure(c:Canvas){
        val a=world.adventure;val u=ui.u;val n=a.objective;val tx=d.txt
        val top=rowY+140*u
        val bossActive=world.activeBoss()!=null
        val location=if(a.insideConduit())"Старый водовод" else a.names[a.region]
        tx.setup(42*u,Palette.GOLD_LIGHT);tx.fit(location,ui.contentW-180*u,42*u);tx.draw(c,location,ui.cx,top)
        tx.setup(32*u,Palette.TEXT);tx.draw(c,"Цель: ${a.labels[n]}",ui.cx,top+48*u)
        if(a.messageT>0f&&!bossActive){tx.setup(30*u,Palette.TEXT);tx.drawWrapped(c,a.message,ui.cx,top+96*u,ui.contentW-90*u,35*u)}
        val rend=app.worldRenderer;val x=rend.screenX(world,a.nodesX[n]);val y=rend.screenY(world,a.nodesY[n])
        val sx=x.coerceIn(ui.left+60*u,ui.right-60*u);val sy=y.coerceIn(ui.top+540*u,ui.bottom-620*u)
        fp.color=a.colors[a.region];c.drawCircle(sx,sy,18*u,fp)
        val dx=world.player.x-a.nodesX[n];val dy=world.player.y-a.nodesY[n]
        tx.setup(29*u,Palette.TEXT);tx.draw(c,"${kotlin.math.sqrt(dx*dx+dy*dy).toInt()} м",sx,sy+47*u)
        if(a.intentionDone&&!a.insideConduit()&&a.messageT<=0f&&!bossActive){tx.setup(30*u,Palette.TEXT);tx.drawWrapped(c,if(a.quickReturnReady())"Цель выполнена. Можно исследовать дальше или вернуться в лагерь." else "Цель выполнена. Отойдите от угрозы — сможете вернуться в лагерь.",ui.cx,top+96*u,ui.contentW-90*u,35*u)}
        if(a.defence==2){tx.setup(36*u,Palette.GOLD_LIGHT);tx.draw(c,"Защита ${a.defenceWave}/3 · Мастер ${a.masterHp.toInt()}%",ui.cx,ui.top+560*u)}
        if(a.nursery&&a.nearNode()==10){tx.setup(34*u,Palette.GOLD_LIGHT);tx.draw(c,"Спокойное наблюдение ${kotlin.math.min(100,(a.trustT/(if(a.nectar)2f else 4f)*100).toInt())}%",ui.cx,ui.top+610*u)}
        if(a.dangerT>0f){tx.setup(38*u,Palette.GOLD_LIGHT);tx.draw(c,"Разряд по воде — выйдите из русла!",ui.cx,ui.top+610*u)}
    }

    // ------------------------------------------------------------ рывок

    private fun drawDash(c: Canvas) {
        if (world.player.dead || world.victory) return
        val p = world.player
        val u = ui.u
        val r = dashBtn.rect
        val cx = r.centerX(); val cy = r.centerY()
        val rad = r.width() * 0.5f * (1f + 0.08f * dashPulse.coerceIn(0f, 1f))
        val ready = p.dashCharges > 0
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(0xFF1B1224.toInt(), 150)
        c.drawCircle(cx, cy, rad, fp)
        fp.color = Palette.withAlpha(if (ready) Palette.GOLD else Palette.DISABLED, if (ready) 70 else 40)
        c.drawCircle(cx, cy, rad * 0.86f, fp)
        sp.strokeWidth = 11f * u
        arc.set(cx - rad + 8f * u, cy - rad + 8f * u, cx + rad - 8f * u, cy + rad - 8f * u)
        sp.color = Palette.withAlpha(0xFFFFF6E6.toInt(), 70)
        c.drawCircle(cx, cy, rad - 8f * u, sp)
        if (p.dashCharges < Player.MAX_DASH) {
            val prog = 1f - (p.dashRecharge / World.DASH_RECHARGE).coerceIn(0f, 1f)
            sp.color = Palette.GOLD_LIGHT
            c.drawArc(arc, -90f, 360f * prog, false, sp)
        } else {
            sp.color = Palette.withAlpha(Palette.GOLD, 230)
            c.drawCircle(cx, cy, rad - 8f * u, sp)
        }
        // три шеврона «>>>»
        sp.strokeWidth = 12f * u
        sp.color = if (ready) Palette.TEXT else Palette.withAlpha(Palette.TEXT, 90)
        for (k in -1..1) {
            val ox = cx + k * 30f * u
            c.drawLine(ox - 12f * u, cy - 30f * u, ox + 14f * u, cy, sp)
            c.drawLine(ox + 14f * u, cy, ox - 12f * u, cy + 30f * u, sp)
        }
        fp.style = Paint.Style.FILL
        for (k in 0 until Player.MAX_DASH) {
            fp.color = if (k < p.dashCharges) Palette.GOLD_LIGHT else Palette.withAlpha(0xFFFFF6E6.toInt(), 60)
            c.drawCircle(cx + (k - (Player.MAX_DASH - 1) * 0.5f) * 34f * u, cy + rad - 4f * u, 11f * u, fp)
        }
    }

    // ------------------------------------------------------------ босс, немезида, подсказки R03

    private fun drawR03(c: Canvas) {
        val w = world
        if (w.player.dead || w.victory) return
        val u = ui.u
        val tx = d.txt
        val boss = w.activeBoss()
        val nem = w.activeNemesis()
        if (boss != null) drawBossBar(c, boss)
        if (nem != null) drawNemesisTag(c, nem)
        drawOffscreen(c, boss, nem)
        if (boss != null && boss.state == BossSystem.INTRO) {
            val t = (1f - boss.stateT / 1.9f).coerceIn(0f, 1f)
            val a = (255f * Ease.window(t, 1f, 0.12f, 0.35f)).toInt()
            val y = ui.h * 0.36f
            tx.setup(112f * u, Palette.withAlpha(0xFFFFB347.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 22f * u)
            tx.fit(boss.def.name, ui.contentW, 112f * u)
            tx.draw(c, boss.def.name, ui.cx, y)
            tx.setup(52f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx.fit(boss.def.title, ui.contentW, 52f * u)
            tx.draw(c, boss.def.title, ui.cx, y + 74f * u)
        }
        if (boss != null && w.bossPhaseTimer > 0f) {
            val t = 2.4f - w.bossPhaseTimer
            val a = (255f * Ease.window(t, 2.4f, 0.1f, 0.5f)).toInt()
            val y = ui.h * 0.36f + 10f * u
            tx.setup(86f * u, Palette.withAlpha(0xFFFF6B5A.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 18f * u)
            val b = tx.buf.clear().add("Фаза ").add(boss.stage + 1).add('!')
            tx.draw(c, b, ui.cx, y)
        }
        if (w.perfectTimer > 0f) {
            val t = 1.3f - w.perfectTimer
            val a = (255f * Ease.window(t, 1.3f, 0.08f, 0.45f)).toInt()
            val hx = app.worldRenderer.heroScreenX(w)
            val hy = app.worldRenderer.heroScreenY(w) - 170f * u - Ease.outCubic(t / 1.3f) * 50f * u
            tx.setup(70f * u, Palette.withAlpha(0xFF9EEBFF.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 15f * u)
            tx.draw(c, "Идеально!", hx, hy)
        }
        if (w.revengeTimer > 0f) {
            val t = 3.2f - w.revengeTimer
            val a = (255f * Ease.window(t, 3.2f, 0.12f, 0.6f)).toInt()
            val y = ui.h * 0.34f
            tx.setup(118f * u, Palette.withAlpha(0xFFFF5A6A.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 22f * u)
            tx.draw(c, "Реванш!", ui.cx, y)
            tx.setup(52f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx.draw(c, "Немезида повержена", ui.cx, y + 76f * u)
        }
    }

    private fun drawBossBar(c: Canvas, e: Enemy) {
        val u = ui.u
        val tx = d.txt
        val l = ui.left + 28f * u
        val r = ui.right - 28f * u
        val y = rowY + 300f * u
        val intro = e.state == BossSystem.INTRO
        tx.setup(44f * u, Palette.TEXT, outlineW = 9f * u)
        tx.fit(e.def.name + " — " + e.def.title, r - l, 44f * u)
        tx.draw(c, e.def.name + " — " + e.def.title, ui.cx, y - 12f * u)
        val frac = (e.hp / e.maxHp).coerceIn(0f, 1f)
        d.bar(c, l, y, r, y + 38f * u, frac, if (intro) Palette.DISABLED else 0xFFE8483F.toInt(), 0xFF4A1512.toInt())
        // отметки фаз
        val marks = if (e.def.id == "ogre_chef") floatArrayOf(0.55f, 0.25f) else floatArrayOf(0.62f, 0.30f)
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(0xFFFFF6E6.toInt(), 200)
        for (m in marks) c.drawRect(l + (r - l) * m - 2f * u, y - 4f * u, l + (r - l) * m + 2f * u, y + 42f * u, fp)
        if (e.invulnT > 0f && !intro) {
            tx.setup(34f * u, 0xFF9EEBFF.toInt(), outlineW = 7f * u)
            tx.draw(c, "неуязвим", ui.cx, y + 78f * u)
        } else {
            val hint=com.tavernrush.game.systems.RegionalBosses.hint(e)
            if(hint.isNotEmpty()){tx.setup(30f*u,Palette.GOLD_LIGHT,outlineW=6f*u);tx.fit(hint,r-l,30f*u);tx.draw(c,hint,ui.cx,y+78f*u)}
        }
    }

    private fun drawNemesisTag(c: Canvas, e: Enemy) {
        val u = ui.u
        val sx = app.worldRenderer.screenX(world, e.x)
        val sy = app.worldRenderer.screenY(world, e.y) - (e.radius * World.NAMED_SIZE * 0.5f + 70f) * app.worldRenderer.scale * 0.7f
        val tx = d.txt
        val name = Nemesis.name(e.nameSeed, e.rank)
        d.icon(c, IconId.SKULL, sx - tx.width(name) * 0.0f, sy - 46f * u, 46f * u)
        tx.setup(38f * u, 0xFFFF6B7A.toInt(), outlineW = 8f * u)
        tx.draw(c, name, sx, sy)
    }

    /** Стрелка у края экрана, если босс или немезида за кадром. */
    private fun drawOffscreen(c: Canvas, boss: Enemy?, nem: Enemy?) {
        val u = ui.u
        val rend = app.worldRenderer
        val targets = arrayOf(boss, nem)
        for (k in targets.indices) {
            val e = targets[k] ?: continue
            val sx = rend.screenX(world, e.x); val sy = rend.screenY(world, e.y)
            val l = ui.left + 40f * u; val r = ui.right - 40f * u
            val t = ui.top + 330f * u; val b = ui.bottom - 40f * u
            if (sx in l..r && sy in t..b) continue
            val cx = ui.cx; val cy = (t + b) * 0.5f
            val dx = sx - cx; val dy = sy - cy
            val kx = if (abs(dx) < 0.01f) 1e9f else (r - cx) / abs(dx)
            val ky = if (abs(dy) < 0.01f) 1e9f else (b - cy) / abs(dy)
            val kk = minOf(kx, ky, (cy - t) / maxOf(abs(dy), 0.01f))
            val px = (cx + dx * kk).coerceIn(l, r); val py = (cy + dy * kk).coerceIn(t, b)
            val ang = atan2(dy, dx)
            val size = 30f * u * (1f + 0.15f * sin(app.time * 8f))
            tri.reset()
            tri.moveTo(px + cos(ang) * size, py + sin(ang) * size)
            tri.lineTo(px + cos(ang + 2.5f) * size, py + sin(ang + 2.5f) * size)
            tri.lineTo(px + cos(ang - 2.5f) * size, py + sin(ang - 2.5f) * size)
            tri.close()
            fp.style = Paint.Style.FILL
            fp.color = if (e.boss) 0xFFFFB347.toInt() else 0xFFFF4D5E.toInt()
            c.drawPath(tri, fp)
            sp.strokeWidth = 4f * u
            sp.color = Palette.OUTLINE
            c.drawPath(tri, sp)
        }
    }

    /** Верхняя панель HUD (используется и под картами/паузой). */
    fun drawHud(c: Canvas, interactive: Boolean) {
        val w = world
        val u = ui.u
        val tx = d.txt
        val pl = w.player

        // низкое здоровье — красная пульсирующая виньетка
        val hpf = pl.hpFrac
        val vig = redVignette
        if (vig != null && !pl.dead && hpf < 0.35f) {
            val k = (0.35f - hpf) / 0.35f
            val pulse = 0.7f + 0.3f * sin(app.time * 7f)
            bp.alpha = (255f * minOf(1f, 0.45f + 0.55f * k) * pulse).toInt().coerceIn(0, 255)
            dst.set(0f, 0f, ui.w.toFloat(), ui.h.toFloat())
            c.drawBitmap(vig, null, dst, bp)
        }

        // полоса опыта + значок уровня
        val l = ui.left + 24f * u
        val r = ui.right - 24f * u
        val xpFrac = if (pl.xpNeed > 0f) pl.xp / pl.xpNeed else 0f
        d.bar(c, l + 64f * u, barTop, r, barTop + 34f * u, xpFrac, Palette.XP, Palette.XP_DARK)
        val bx = l + 46f * u
        val by = barTop + 17f * u
        fp.style = Paint.Style.FILL
        fp.color = Palette.OUTLINE
        c.drawCircle(bx, by, 50f * u, fp)
        fp.color = if (w.levelFlash > 0f) Palette.mix(Palette.XP, 0xFFFFFFFF.toInt(), w.levelFlash) else Palette.XP_DARK
        c.drawCircle(bx, by, 43f * u, fp)
        tx.setup(46f * u, Palette.TEXT)
        tx.draw(c, tx.buf.clear().add(pl.level), bx, by + 16f * u)

        // таймер и волна
        tx.setup(84f * u, Palette.TEXT, outlineW = 16f * u)
        tx.draw(c, tx.buf.clear().addTime(w.time.toInt()), ui.cx, rowY)
        tx.setup(38f * u, Palette.TEXT_DIM)
        if(!w.adventure.active)tx.draw(c, tx.buf.clear().add("Волна ").add(w.director.waveIndex + 1).add('/').add(w.director.waveCount), ui.cx, rowY + 50f * u)

        // убийства и золото
        val iy = rowY - 28f * u
        d.icon(c, IconId.SKULL, l + 30f * u, iy, 58f * u)
        tx.setup(50f * u, Palette.TEXT, Paint.Align.LEFT)
        tx.draw(c, tx.buf.clear().addGrouped(w.run.kills), l + 70f * u, rowY)
        d.goldCounter(c, l + 2f * u, rowY + 70f * u, 48f * u, w.run.gold.toLong())

        // оружие и пассивки
        var x = l
        val wy = rowY + 112f * u
        val ws = 84f * u
        for (i in 0 until w.weaponCount) {
            val wi = w.weapons[i]
            slot(c, x, wy, ws, wi.def.icon, wi.level, wi.level >= wi.def.maxLevel, wi.evolutionIndex >= 0, wi.synergyActive)
            x += ws + 12f * u
        }
        x = l
        val py = wy + ws + 14f * u
        val ps = 64f * u
        for (k in 0 until w.passiveCount) {
            val pi = w.passiveOrder[k]
            val def = Content.passives[pi]
            val lvl = w.passiveLevels[pi]
            slot(c, x, py, ps, def.icon, lvl, lvl >= def.maxLevel)
            x += ps + 10f * u
        }

        // пауза
        if (interactive) {
            d.button(c, pauseBtn, app.time)
            val pr = pauseBtn.rect
            val off = if (pauseBtn.pressed) 6f * u else 0f
            fp.color = Palette.TEXT
            val bw = pr.width() * 0.13f
            val bh = pr.height() * 0.4f
            val cx = pr.centerX()
            val cy = pr.centerY() + off - 4f * u
            c.drawRoundRect(cx - bw * 1.8f, cy - bh / 2f, cx - bw * 0.8f, cy + bh / 2f, 4f * u, 4f * u, fp)
            c.drawRoundRect(cx + bw * 0.8f, cy - bh / 2f, cx + bw * 1.8f, cy + bh / 2f, 4f * u, 4f * u, fp)
        }
    }

    private fun slot(c: Canvas, x: Float, y: Float, s: Float, icon: IconId, level: Int, max: Boolean, evolved: Boolean = false, synergy: Boolean = false) {
        val u = ui.u
        d.rrect(c, x, y, x + s, y + s, 16f * u, if (synergy) 0xFF6EA9AF.toInt() else Palette.withAlpha(Palette.OUTLINE, 190))
        d.rrect(c, x + 4f * u, y + 4f * u, x + s - 4f * u, y + s - 4f * u, 13f * u, if (evolved) 0xFF70518B.toInt() else if (max) Palette.GOLD_DARK else Palette.WOOD_DARK)
        d.icon(c, icon, x + s / 2f, y + s / 2f, s * 0.72f)
        val tx = d.txt
        tx.setup(s * 0.36f, if (max) Palette.GOLD_LIGHT else Palette.TEXT, Paint.Align.RIGHT, outlineW = s * 0.07f)
        val b = tx.buf.clear()
        if (evolved) b.add("★") else b.add(level)
        tx.draw(c, b, x + s - 6f * u, y + s - 6f * u)
    }

    private fun drawBanners(c: Canvas) {
        val w = world
        if (w.player.dead || w.victory) return
        val u = ui.u
        val tx = d.txt
        val dir = w.director
        if (w.evolutionTimer > 0f && w.lastEvolution >= 0) {
            val y = ui.h * 0.29f
            tx.setup(104f * u, Palette.GOLD_LIGHT, outlineW = 20f * u)
            tx.draw(c, "Эволюция!", ui.cx, y)
            tx.setup(52f * u, Palette.TEXT)
            val name = Content.evolutions[w.lastEvolution].name
            tx.fit(name, ui.contentW, 52f * u)
            tx.draw(c, name, ui.cx, y + 75f * u)
            return
        }
        if (dir.bannerTimer > 0f) {
            val t = 3f - dir.bannerTimer
            val a = (255f * Ease.window(t, 3f, 0.25f, 0.6f)).toInt()
            val k = 0.85f + 0.15f * Ease.outBack(t / 0.4f)
            val y = ui.h * 0.29f
            c.save()
            c.scale(k, k, ui.cx, y)
            tx.setup(118f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 22f * u)
            tx.draw(c, dir.wave.title, ui.cx, y)
            tx.setup(54f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx.fit(dir.wave.subtitle, ui.contentW, 54f * u)
            tx.draw(c, dir.wave.subtitle, ui.cx, y + 80f * u)
            c.restore()
        }
        if (dir.eventBannerTimer > 0f && dir.bannerTimer <= 0f) {
            val t = 2.4f - dir.eventBannerTimer
            val a = (255f * Ease.window(t, 2.4f, 0.15f, 0.5f)).toInt()
            val y = ui.h * 0.32f + (1f - Ease.outCubic(t / 0.3f)) * 40f * u
            val title = when (dir.eventBannerType) {
                WaveEventType.ELITE -> "Элита!"
                WaveEventType.SWARM -> "Стая!"
                WaveEventType.RING -> "Окружают!"
                WaveEventType.BOSS -> "Босс!"
                WaveEventType.NEMESIS -> "Немезида вернулась!"
            }
            tx.setup(96f * u, Palette.withAlpha(0xFFFF6B5A.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 20f * u)
            tx.draw(c, title, ui.cx, y)
            val ei = dir.eventBannerEnemy
            if (ei >= 0 && ei < Content.enemies.size) {
                tx.setup(50f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
                val line = if (dir.eventBannerType == WaveEventType.NEMESIS) Nemesis.fullName(Content.enemies[ei].id, w.nemSeed, w.nemRank) else Content.enemies[ei].name
                tx.fit(line, ui.contentW, 50f * u)
                tx.draw(c, line, ui.cx, y + 70f * u)
            }
        }
        // «На волосок!» — награда за смелость
        if (w.nearMissTimer > 0f && !w.player.dead) {
            val t = 2.6f - w.nearMissTimer
            val a = (255f * Ease.window(t, 2.6f, 0.1f, 0.7f)).toInt()
            val hx = app.worldRenderer.heroScreenX(w)
            val hy = app.worldRenderer.heroScreenY(w) - 150f * u - Ease.outCubic(t / 2.6f) * 60f * u
            val k = 0.7f + 0.3f * Ease.outBack(t / 0.3f)
            c.save()
            c.scale(k, k, hx, hy)
            tx.setup(64f * u, Palette.withAlpha(0xFFFFE27A.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 14f * u)
            tx.draw(c, "На волосок!", hx, hy)
            tx.setup(52f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx.draw(c, tx.buf.clear().add('+').add(w.nearMissGold).add(" золота"), hx, hy + 62f * u)
            c.restore()
        }
    }

    private fun drawOutcome(c: Canvas) {
        val w = world
        val u = ui.u
        val tx = d.txt
        if (w.player.dead) {
            val t = w.deathTimer
            d.dim(c, (170f * Ease.clamp01(t / Game.DEATH_DELAY)).toInt())
            val a = (255f * Ease.clamp01(t / 0.4f)).toInt()
            val k = 0.6f + 0.4f * Ease.outBack(t / 0.5f)
            c.save()
            c.scale(k, k, ui.cx, ui.h * 0.42f)
            tx.setup(120f * u, Palette.withAlpha(0xFFFF6B5A.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 22f * u)
            tx.draw(c, "Вы пали…", ui.cx, ui.h * 0.42f)
            c.restore()
        } else if (w.victory) {
            val t = w.victoryTimer
            val a = (255f * Ease.clamp01(t / 0.6f)).toInt()
            d.dim(c, (90f * Ease.clamp01(t / 1.5f)).toInt())
            tx.setup(130f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 24f * u)
            tx.draw(c, "Рассвет!", ui.cx, ui.h * 0.4f)
            tx.setup(56f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx.fit("Таверна выстояла эту ночь", ui.contentW, 56f * u)
            tx.draw(c, "Таверна выстояла эту ночь", ui.cx, ui.h * 0.4f + 90f * u)
        }
    }

    private fun drawHints(c: Canvas) {
        val w = world
        if (game.profile.runs < 3 && !w.player.dead && w.run.dashes == 0 && w.time > 15f && w.time < 27f) {
            val a = (255f * Ease.window(w.time - 15f, 12f, 0.5f, 1f)).toInt()
            val tx0 = d.txt
            tx0.setup(44f * ui.u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx0.fit(HINT_DASH, ui.contentW, 44f * ui.u)
            tx0.draw(c, HINT_DASH, ui.cx, ui.bottom - 120f * ui.u)
        }
        if (game.profile.runs >= 2 || w.player.dead) return
        val u = ui.u
        val tx = d.txt
        val t = w.time
        if (!touched && t < 12f) {
            // пульсирующее «кольцо джойстика» внизу экрана
            val y = app.joystick.homeY
            val x = app.joystick.homeX
            val ph = (app.time * 1.2f) % 1f
            fp.style = Paint.Style.STROKE
            fp.strokeWidth = 6f * u
            fp.color = Palette.withAlpha(0xFFFFF6E6.toInt(), (160f * (1f - ph)).toInt())
            c.drawCircle(x, y, (90f + 35f * ph) * u, fp)
            fp.style = Paint.Style.FILL
            fp.color = Palette.withAlpha(Palette.GOLD, 200)
            c.drawCircle(x + sin(app.time * 2.4f) * 45f * u, y, 34f * u, fp)
            tx.setup(48f * u, Palette.TEXT)
            tx.fit(HINT_MOVE, ui.contentW, 48f * u)
            tx.draw(c, HINT_MOVE, ui.cx, ui.bottom - 120f * u)
        } else if (game.profile.runs == 0 && t > 3f && t < 13f) {
            val a = (255f * Ease.window(t - 3f, 10f, 0.5f, 1f)).toInt()
            tx.setup(46f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
            tx.fit(HINT_AUTO, ui.contentW, 46f * u)
            tx.draw(c, HINT_AUTO, ui.cx, ui.bottom - 120f * u)
        }
    }

    private companion object {
        const val HINT_MOVE = "Двигайтесь стиком слева — небольшой ход пальца"
        const val HINT_AUTO = "Оружие бьёт само. Собирайте синие кристаллы!"
        const val HINT_DASH = "Рывок — отдельная кнопка. Палец на стике можно держать"
    }
}
