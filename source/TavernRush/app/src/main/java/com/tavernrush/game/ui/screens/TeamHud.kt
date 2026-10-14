package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.entities.Mate
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.PixelBlit
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.systems.CompanionSystem
import com.tavernrush.game.systems.EncounterSystem
import com.tavernrush.game.systems.World
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * HUD друзей: плашки спутников под кнопкой паузы, кнопка приказа или смены (над кнопкой рывка),
 * стрелка к месту встречи и баннеры «кто-то зовёт на помощь» / «новый друг». Рисуется поверх мира.
 */
class TeamHud(private val app: GameApp) {
    /** Круглая кнопка: приказ ведущему (режим «один») или смена ведущего и резерва (режим «смена»). */
    val button = RectF()
    private val chip = RectF()
    private var chipSize = 0f
    private var chipGap = 0f
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = RectF()
    private val tri = Path()
    private var pulse = 0f

    private val ui get() = app.ui
    private val d get() = app.draw

    fun layout(pause: RectF, dash: RectF) {
        val u = ui.u
        chipSize = 104f * u
        chipGap = 14f * u
        chip.set(ui.right - 26f * u - chipSize, pause.bottom + 24f * u, ui.right - 26f * u, pause.bottom + 24f * u + chipSize)
        val r = 78f * u
        val cx = dash.centerX()
        val cy = dash.top - 112f * u - r
        button.set(cx - r, cy - r, cx + r, cy + r)
    }

    /** Кнопка видна, если в режиме есть что нажимать. */
    fun hasButton(w: World): Boolean = !w.player.dead && !w.victory && (w.team.hasOrder || w.team.canSwap)

    /** Нажатие по кнопке: приказ или смена. true — касание обработано. */
    fun press(x: Float, y: Float, w: World): Boolean {
        if (!hasButton(w)) return false
        val pad = 22f * ui.u
        if (x < button.left - pad || x > button.right + pad || y < button.top - pad || y > button.bottom + pad) return false
        val ok = if (w.team.hasOrder) w.team.order() else w.team.swap()
        pulse = if (ok) 1f else 0.4f
        if (!ok) w.events.sfx(com.tavernrush.game.core.Sfx.CLICK)
        return true
    }

    fun update(dt: Float) { if (pulse > 0f) pulse -= dt * 4f }

    fun draw(c: Canvas, w: World) {
        if (w.player.dead || w.victory) return
        drawChips(c, w)
        drawButton(c, w)
        drawEncounter(c, w)
    }

    // ------------------------------------------------------------ плашки

    private fun drawChips(c: Canvas, w: World) {
        val u = ui.u
        val tm = w.team
        var y = chip.top
        for (i in 0 until tm.count) {
            val m = tm.mates[i]!!
            val def = Content.creatures[m.species]
            val x = chip.left
            val s = chipSize
            val active = m.active
            val downed = m.state == Mate.DOWNED
            fp.style = Paint.Style.FILL
            fp.color = Palette.withAlpha(0xFF1B1224.toInt(), if (active) 190 else 120)
            c.drawCircle(x + s / 2f, y + s / 2f, s / 2f, fp)
            fp.color = Palette.withAlpha(def.color, if (active) 80 else 36)
            c.drawCircle(x + s / 2f, y + s / 2f, s * 0.43f, fp)
            val anim = SpriteBank.creature(def.look, "idle")
            val fr = anim.frame(0, (app.time * 3.2f).toInt())
            val k = (s * 0.62f / fr.h.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            val gy = y + s * 0.52f + fr.h * k * 0.5f
            PixelBlit.sprite(c, fr, x + s / 2f, gy, k, if (!active || downed) 120 else 255)
            // здоровье дугой по краю плашки; у лежащего — прогресс подъёма
            val frac = if (downed) m.reviveT / CompanionSystem.REVIVE_TIME else m.hp / m.maxHp
            sp.strokeWidth = 7f * u
            sp.color = Palette.withAlpha(0xFF000000.toInt(), 90)
            arc.set(x + 4f * u, y + 4f * u, x + s - 4f * u, y + s - 4f * u)
            c.drawArc(arc, -90f, 360f, false, sp)
            sp.color = if (downed) 0xFFFFE066.toInt() else if (frac < 0.35f) 0xFFFF6B6B.toInt() else 0xFF7CFF8A.toInt()
            c.drawArc(arc, -90f, 360f * frac.coerceIn(0f, 1f), false, sp)
            // имя под плашкой
            val tx = d.txt
            tx.setup(30f * u, if (active) Palette.TEXT else Palette.TEXT_DIM)
            val name = m.name
            tx.fit(name, s + 20f * u, 30f * u)
            tx.draw(c, name, x + s / 2f, y + s + 24f * u)
            if (downed) {
                tx.setup(28f * u, 0xFFFFE066.toInt())
                tx.draw(c, "Помогите!", x + s / 2f, y + s + 52f * u)
            } else if (!active) {
                tx.setup(26f * u, Palette.TEXT_DIM)
                tx.draw(c, "отдыхает", x + s / 2f, y + s + 50f * u)
            }
            y += s + chipGap + 40f * u
        }
    }

    // ------------------------------------------------------------ кнопка приказа / смены

    fun statusText(w:World):String? {
        val tm=w.team;val m=tm.lead()?:return null
        val seconds=if(tm.hasOrder)tm.orderCd else tm.swapCd
        if(seconds>0f)return "Через ${kotlin.math.ceil(seconds).toInt()} с"
        if(!tm.hasOrder)return if(tm.swapReady())null else "Друг отдыхает"
        return when {
            m.state==Mate.DOWNED->"Помогите другу"
            m.state==Mate.ACT->"Прыжок"
            m.state==Mate.RECOVER||m.state==Mate.ENTER->"Передышка"
            m.species==CompanionSystem.S_LYNX&&m.charge<CompanionSystem.ORDER_CHARGE->"Копите заряд бегом"
            m.species==CompanionSystem.S_LYNX&&!tm.leapTargetReady->"Прыжок: нужна цель"
            !tm.orderReady()->"Копите заряд бегом"
            else->null
        }
    }

    private fun drawButton(c: Canvas, w: World) {
        if (!hasButton(w)) return
        val tm = w.team
        val u = ui.u
        val order = tm.hasOrder
        val m = tm.lead() ?: return
        val ready = if (order) tm.orderReady() else tm.swapReady()
        val cd = if (order) tm.orderCd / CompanionSystem.ORDER_CD else tm.swapCd / CompanionSystem.SWAP_CD
        val def = Content.creatures[m.species]
        val cx = button.centerX(); val cy = button.centerY()
        val rad = button.width() * 0.5f * (1f + 0.1f * pulse.coerceIn(0f, 1f))
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(0xFF1B1224.toInt(), 160)
        c.drawCircle(cx, cy, rad, fp)
        val col = if (order) def.color else Creatures.let { 0xFFFFD27A.toInt() }
        fp.color = Palette.withAlpha(col, if (ready) 85 else 36)
        c.drawCircle(cx, cy, rad * 0.86f, fp)
        // кольцо: перезарядка (или заряд Грозорыса до готовности приказа)
        var frac = 1f - cd.coerceIn(0f, 1f)
        if (order && m.species == CompanionSystem.S_LYNX && cd <= 0f) frac = (m.charge / CompanionSystem.ORDER_CHARGE).coerceIn(0f, 1f)
        sp.strokeWidth = 8f * u
        sp.color = Palette.withAlpha(0xFF000000.toInt(), 90)
        arc.set(cx - rad * 0.9f, cy - rad * 0.9f, cx + rad * 0.9f, cy + rad * 0.9f)
        c.drawArc(arc, -90f, 360f, false, sp)
        sp.color = if (ready) col else Palette.withAlpha(col, 170)
        c.drawArc(arc, -90f, 360f * frac, false, sp)
        // значок и подпись
        val tx = d.txt
        val sprAnim = SpriteBank.creature(def.look, "idle")
        val fr = sprAnim.frame(if (m.faceLeft) 1 else 0, 0)
        val k = (rad * 1.0f / fr.h.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        PixelBlit.sprite(c, fr, cx, cy + rad * 0.34f + fr.h * k * 0.0f, k, if (ready) 255 else 150)
        val label = if (order) def.orderLabel else "Смена"
        tx.setup(34f * u, if (ready) Palette.TEXT else Palette.TEXT_DIM)
        tx.draw(c, label, cx, cy + rad + 40f * u)
        val hint=statusText(w)
        if(hint!=null){
            val width=minOf(ui.w-24f*u,380f*u);val hx=cx.coerceIn(ui.left+10f*u+width*.5f,ui.right-10f*u-width*.5f)
            tx.setup(28f*u,Palette.TEXT_DIM);tx.fit(hint,width,28f*u);tx.draw(c,hint,hx,cy+rad+76f*u)
        }
    }

    // ------------------------------------------------------------ встреча

    private fun drawEncounter(c: Canvas, w: World) {
        val enc = w.encounter
        val u = ui.u
        val tx = d.txt
        // баннер: зовут на помощь / новый друг / убежал
        if (enc.species >= 0 && enc.hint > 0f && w.director.bannerTimer <= 0f) {
            val def = Content.creatures[enc.species]
            val total = if (enc.outcome == EncounterSystem.OUT_JOINED) 4f else if (enc.outcome == EncounterSystem.OUT_FLED || enc.outcome == EncounterSystem.OUT_LEFT) 3.5f else 4.5f
            val t = (total - enc.hint).coerceAtLeast(0f)
            val a = (255f * Ease.window(t, total, 0.2f, 0.6f)).toInt()
            // ниже баннеров волн и событий (они на 29–32% высоты), чтобы надписи не накладывались
            val y = ui.h * 0.43f + (1f - Ease.outCubic(t / 0.3f)) * 36f * u
            val phase = if (enc.outcome == EncounterSystem.OUT_JOINED) EncounterSystem.JOINED else if (enc.outcome == EncounterSystem.OUT_FLED) EncounterSystem.FLED
                else if (enc.outcome == EncounterSystem.OUT_LEFT) EncounterSystem.LEFT else enc.state
            when (phase) {
                EncounterSystem.LEFT -> {
                    tx.setup(70f * u, Palette.withAlpha(Palette.TEXT_DIM, a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 14f * u)
                    tx.draw(c, "Существо ушло", ui.cx, y)
                    tx.setup(42f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
                    tx.draw(c, "Вы можете встретиться снова", ui.cx, y + 62f * u)
                }
                EncounterSystem.FLED -> {
                    tx.setup(76f * u, Palette.withAlpha(0xFFFF9E9E.toInt(), a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 16f * u)
                    tx.draw(c, "Убежал…", ui.cx, y)
                    tx.setup(44f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
                    tx.fit("Вы можете встретиться снова", ui.contentW, 44f * u)
                    tx.draw(c, "Вы можете встретиться снова", ui.cx, y + 64f * u)
                }
                EncounterSystem.ACTIVE -> {
                    tx.setup(84f * u, Palette.withAlpha(def.color, a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 18f * u)
                    tx.fit("Кто-то зовёт на помощь!", ui.contentW, 84f * u)
                    tx.draw(c, "Кто-то зовёт на помощь!", ui.cx, y)
                    tx.setup(44f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
                    tx.drawWrapped(c, def.meeting, ui.cx, y + 70f * u, ui.contentW, 52f * u)
                }
                EncounterSystem.JOINED -> {
                    tx.setup(92f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a), outline = Palette.withAlpha(Palette.OUTLINE, a), outlineW = 20f * u)
                    tx.draw(c, "Новый друг!", ui.cx, y)
                    val line = def.names[enc.nameIdx.coerceIn(0, def.names.size - 1)] + " · " + def.name
                    tx.setup(54f * u, Palette.withAlpha(def.color, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
                    tx.fit(line, ui.contentW, 54f * u)
                    tx.draw(c, line, ui.cx, y + 72f * u)
                    tx.setup(40f * u, Palette.withAlpha(Palette.TEXT, a), outline = Palette.withAlpha(Palette.OUTLINE, a))
                    val sub = if (enc.joinedAsMate) "Уже в бою рядом с вами" else "Ждёт в таверне"
                    tx.draw(c, sub, ui.cx, y + 126f * u)
                }
                else -> {}
            }
        }
        // подсказка про доверие, пока герой в кольце
        if (enc.active) {
            val hx = w.player.x - enc.siteX; val hy = w.player.y - enc.siteY
            val inRing = hx * hx + hy * hy <= EncounterSystem.RING_R * EncounterSystem.RING_R
            if (enc.remaining < EncounterSystem.WARN_TIME) {
                tx.setup(40f * u, Palette.withAlpha(0xFFFFD27A.toInt(), 235))
                tx.draw(c, "Существо скоро уйдёт…", ui.cx, ui.bottom - 520f * u)
            }
            if (inRing && enc.hint <= 0f) {
                tx.setup(40f * u, Palette.withAlpha(0xFFFFC2D8.toInt(), 235))
                tx.draw(c, if (enc.trust > 0.01f) "Доверие растёт — держите врагов подальше" else "Отгоните врагов и постойте рядом", ui.cx, ui.bottom - 470f * u)
            }
        }
        // стрелка к существу за краем экрана
        if (enc.active) drawArrow(c, w, enc)
    }

    private fun drawArrow(c: Canvas, w: World, enc: EncounterSystem) {
        val u = ui.u
        val rend = app.worldRenderer
        val sx = rend.screenX(w, enc.x); val sy = rend.screenY(w, enc.y)
        val l = ui.left + 60f * u; val r = ui.right - 60f * u
        val t = ui.top + 380f * u; val b = ui.bottom - 90f * u
        if (sx in l..r && sy in t..b) return
        val cx = ui.cx; val cy = (t + b) * 0.5f
        val dx = sx - cx; val dy = sy - cy
        val kx = if (abs(dx) < 0.01f) 1e9f else (r - cx) / abs(dx)
        val ky = if (abs(dy) < 0.01f) 1e9f else (b - cy) / abs(dy)
        val kk = minOf(kx, ky, (cy - t) / maxOf(abs(dy), 0.01f))
        val px = (cx + dx * kk).coerceIn(l, r); val py = (cy + dy * kk).coerceIn(t, b)
        val ang = atan2(dy, dx)
        val def = Content.creatures[enc.species]
        val size = 30f * u * (1f + 0.15f * sin(app.time * 8f))
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(0xFF1B1224.toInt(), 190)
        c.drawCircle(px, py, 46f * u, fp)
        fp.color = Palette.withAlpha(def.color, 200)
        c.drawCircle(px, py, 36f * u, fp)
        tri.reset()
        val tx0 = px + cos(ang) * 62f * u; val ty0 = py + sin(ang) * 62f * u
        tri.moveTo(tx0 + cos(ang) * size * 0.6f, ty0 + sin(ang) * size * 0.6f)
        tri.lineTo(tx0 + cos(ang + 2.4f) * size * 0.7f, ty0 + sin(ang + 2.4f) * size * 0.7f)
        tri.lineTo(tx0 + cos(ang - 2.4f) * size * 0.7f, ty0 + sin(ang - 2.4f) * size * 0.7f)
        tri.close()
        fp.color = def.color
        c.drawPath(tri, fp)
        val tx = d.txt
        tx.setup(46f * u, Palette.OUTLINE)
        tx.draw(c, "!", px, py + 16f * u, false)
    }
}
