package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.IconId
import com.tavernrush.game.render.Palette
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/** Итоги забега: показатели, разбивка награды, ближайшая цель в таверне, «Ещё раз» / «В таверну». */
class ResultsScreen(app: GameApp) : Screen(app) {
    val again = Button("Ещё раз", Button.PRIMARY)
    val tavern = Button("В таверну", Button.GOLD)
    private val panel = RectF()
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val rowLabel = arrayOfNulls<String>(8)
    private val rowValue = IntArray(8)
    /** R04: строка «Реакции: Проводимость ×5 · Пар ×2» (собирается при входе, не при каждой отрисовке). */
    private var reactionLine = ""
    private var rows = 0

    override fun layout() = layoutPanel()

    /** Высота панели — по содержимому (строк награды 1–6), не больше экрана. */
    private fun layoutPanel() {
        val u = ui.u
        val pw = ui.contentW
        val need = (1200f + 64f * rows) * u
        val ph = minOf(need, ui.bottom - ui.top - 100f * u)
        val top = (ui.top + ui.bottom) * 0.5f - ph * 0.5f
        panel.set(ui.cx - pw / 2f, top, ui.cx + pw / 2f, top + ph)
        val bTop = panel.bottom - 60f * u - 170f * u
        val half = (pw - 100f * u - 30f * u) / 2f
        again.set(panel.left + 50f * u, bTop, panel.left + 50f * u + half, bTop + 170f * u)
        tavern.set(panel.right - 50f * u - half, bTop, panel.right - 50f * u, bTop + 170f * u)
        again.textSize = 60f * u
        tavern.textSize = 60f * u
    }

    override fun onEnter() {
        t = 0f
        val r = game.result
        rows = 0
        addRow("Собрано золота", r.goldCollected, true)
        addRow("За время", r.timeBonus, false)
        addRow("За врагов", r.killBonus, false)
        addRow("За волны", r.waveBonus, false)
        addRow("За победу", r.victoryBonus, false)
        addRow("Боссы повержены", r.bossBonus, false)
        addRow("Реванш над немезидой", r.revengeBonus, false)
        addRow("Копилка трактирщика", r.piggyBonus, false)
        tavern.sub = if (r.goalUpgrade >= 0 && r.goalMissing <= 0) "Есть покупка!" else null
        val sb = StringBuilder()
        for (k in 1 until com.tavernrush.game.content.Reactions.COUNT) if (r.reactions[k] > 0) {
            if (sb.isNotEmpty()) sb.append(" · ")
            sb.append(com.tavernrush.game.content.Reactions.names[k]).append(" ×").append(r.reactions[k])
        }
        reactionLine = if (sb.isEmpty()) "" else "Реакции: $sb"
        layoutPanel()
    }

    private fun addRow(label: String, v: Int, always: Boolean) {
        if (v <= 0 && !always) return
        rowLabel[rows] = label
        rowValue[rows] = v
        rows++
    }

    override fun update(dt: Float) { t += dt }

    override fun onDown(id: Int, x: Float, y: Float) {
        if (t < BLOCK) return
        pressButtons(id, x, y, again, tavern)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        when (releaseButton(id, x, y)) {
            again -> game.startRun()
            tavern -> game.openHub()
        }
    }

    override fun onCancel() = resetPress()

    override fun draw(c: Canvas) {
        val u = ui.u
        val r = game.result
        app.drawWorld(c)
        d.dim(c, (200f * Ease.clamp01(t / 0.3f)).toInt())
        val slide = (1f - Ease.outCubic(t / 0.45f)) * ui.h * 0.4f
        c.save()
        c.translate(0f, slide)
        d.panel(c, panel)
        val tx = d.txt
        val l = panel.left + 50f * u
        val rt = panel.right - 50f * u
        var y = panel.top + 130f * u

        // заголовок
        val defeated = !r.victory && game.world.player.dead
        val title = if (r.victory) "Победа!" else if (defeated) "Вы пали" else "Забег окончен"
        tx.setup(108f * u, if (defeated) 0xFFFF8A70.toInt() else Palette.GOLD_LIGHT, outlineW = 20f * u)
        tx.fit(title, panel.width() - 70f * u, 108f * u)
        tx.draw(c, title, ui.cx, y)
        y += 72f * u
        tx.setup(44f * u, Palette.TEXT)
        when {
            r.victory -> {
                val line = if (r.nemesisSlain) "Реванш взят — таверна выстояла" else "Все волны отбиты — таверна выстояла"
                tx.fit(line, panel.width() - 70f * u, 44f * u)
                tx.draw(c, line, ui.cx, y)
            }
            r.killerLabel.isNotEmpty() -> {
                val line = "Вас убил: " + r.killerLabel
                tx.fit(line, panel.width() - 70f * u, 44f * u)
                tx.draw(c, line, ui.cx, y)
            }
            r.killerEnemy >= 0 && r.killerEnemy < Content.enemies.size ->
                tx.draw(c, tx.buf.clear().add("Противник: ").add(Content.enemies[r.killerEnemy].name), ui.cx, y)
            else -> tx.draw(c, "Забег завершён досрочно", ui.cx, y)
        }
        if (r.newNemesis.isNotEmpty()) {
            tx.setup(36f * u, 0xFFFF6B7A.toInt(), outlineW = 7f * u)
            val line = "Немезида запомнила вас: " + r.newNemesis
            tx.fit(line, panel.width() - 70f * u, 36f * u)
            tx.draw(c, line, ui.cx, again.rect.top - 30f * u)
        }
        // друг и реакции забега: личный результат, а не только золото
        var noteY = again.rect.top - 30f * u - (if (r.newNemesis.isNotEmpty()) 50f * u else 0f)
        if (r.newFriendId.isNotEmpty()) {
            val ci = Content.creatureIndex(r.newFriendId)
            val def = Content.creatures[ci.coerceAtLeast(0)]
            tx.setup(38f * u, 0xFFFFC2D8.toInt(), outlineW = 7f * u)
            val line = "Новый друг: " + r.newFriendName + " · " + def.name
            tx.fit(line, panel.width() - 70f * u, 38f * u)
            tx.draw(c, line, ui.cx, noteY)
            noteY -= 50f * u
        }
        if (reactionLine.isNotEmpty()) {
            tx.setup(34f * u, 0xFFBFE6FF.toInt(), outlineW = 6f * u)
            tx.fit(reactionLine, panel.width() - 70f * u, 34f * u)
            tx.draw(c, reactionLine, ui.cx, noteY)
        }

        // плитки показателей
        y += 50f * u
        val gap = 18f * u
        val tw = (rt - l - 3f * gap) / 4f
        val th = 172f * u
        tile(c, l, y, tw, th, "Время", 0, r.time.toInt(), true)
        tile(c, l + (tw + gap), y, tw, th, "Убито", r.kills, 0, false)
        tile(c, l + 2f * (tw + gap), y, tw, th, "Уровень", r.level, 0, false)
        tile(c, l + 3f * (tw + gap), y, tw, th, "Волна", r.wave, 0, false)
        if (r.newBestTime || r.newBestKills) {
            val bx = if (r.newBestTime) l + tw / 2f else l + (tw + gap) + tw / 2f
            val k = 1f + 0.06f * kotlin.math.sin(app.time * 6f)
            c.save()
            c.scale(k, k, bx, y)
            d.rrect(c, bx - 120f * u, y - 30f * u, bx + 120f * u, y + 22f * u, 18f * u, Palette.RED)
            tx.setup(32f * u, Palette.TEXT, outlineW = 0f)
            tx.draw(c, "Рекорд!", bx, y + 6f * u, false)
            c.restore()
        }
        y += th + 64f * u

        // главное оружие
        var best = -1
        var bestDmg = -1f
        for (i in 0 until r.weaponCount) if (r.weaponDamage[i] > bestDmg) { bestDmg = r.weaponDamage[i]; best = i }
        if (best >= 0) {
            val wd = Content.weapons[r.weaponIdx[best]]
            d.icon(c, wd.icon, l + 30f * u, y - 14f * u, 60f * u)
            tx.setup(40f * u, Palette.TEXT, Paint.Align.LEFT)
            val evolution = r.weaponEvolution[best]
            val name = if (evolution in Content.evolutions.indices) Content.evolutions[evolution].name else wd.name
            val b = tx.buf.clear().add(name).add(", ур. ").add(r.weaponLevel[best]).add(" — ").addGrouped(bestDmg.toLong()).add(" урона")
            tx.draw(c, b, l + 76f * u, y)
            y += 84f * u
        }

        // награда
        tx.setup(52f * u, Palette.GOLD_LIGHT, Paint.Align.LEFT)
        tx.draw(c, "Награда", l, y)
        y += 70f * u
        for (i in 0 until rows) {
            val ra = (255f * Ease.clamp01((t - 0.35f - i * 0.12f) / 0.25f)).toInt()
            if (ra <= 0) { y += 64f * u; continue }
            tx.setup(42f * u, Palette.withAlpha(Palette.TEXT, ra), Paint.Align.LEFT, Palette.withAlpha(Palette.OUTLINE, ra))
            tx.draw(c, rowLabel[i] ?: "", l, y)
            tx.setup(44f * u, Palette.withAlpha(Palette.GOLD_LIGHT, ra), Paint.Align.RIGHT, Palette.withAlpha(Palette.OUTLINE, ra))
            tx.draw(c, tx.buf.clear().add(if (i == 0) "" else "+").addGrouped(rowValue[i]), rt, y)
            y += 64f * u
        }
        fp.color = Palette.withAlpha(Palette.GOLD_DARK, 200)
        c.drawRect(l, y - 34f * u, rt, y - 30f * u, fp)
        y += 46f * u
        val countT = Ease.outCubic((t - 0.5f - rows * 0.12f) / 0.9f)
        val shown = (r.total * countT).toInt()
        tx.setup(60f * u, Palette.TEXT, Paint.Align.LEFT)
        tx.draw(c, "Итого", l, y)
        d.goldCounter(c, rt, y, 70f * u, shown.toLong(), Paint.Align.RIGHT)
        y += 60f * u

        // цель в таверне
        if (r.goalUpgrade >= 0 && r.goalUpgrade < Content.hubUpgrades.size) {
            val gd = Content.hubUpgrades[r.goalUpgrade]
            val gTop = y
            val gBot = minOf(y + 110f * u, again.rect.top - 30f * u)
            if (gBot - gTop > 70f * u) {
                d.parchment(c, l, gTop, rt, gBot)
                d.icon(c, gd.icon, l + 62f * u, (gTop + gBot) / 2f, 76f * u)
                val ready = r.goalMissing <= 0
                tx.setup(40f * u, if (ready) Palette.GREEN_DARK else Palette.INK, Paint.Align.LEFT, 0, 0f)
                val b = tx.buf.clear()
                if (ready) b.add("Хватает на «").add(gd.name).add("»!")
                else b.add("До «").add(gd.name).add("» ещё ").addGrouped(r.goalMissing)
                c.drawText(b.chars, 0, b.length, l + 120f * u, (gTop + gBot) / 2f + 14f * u, tx.fill)
            }
        }

        again.enabled = t >= BLOCK
        tavern.enabled = t >= BLOCK
        d.button(c, again, app.time)
        d.button(c, tavern, app.time)
        c.restore()
    }

    private fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: Int, time: Int, isTime: Boolean) {
        val u = ui.u
        d.rrect(c, x, y, x + w, y + h, 22f * u, Palette.withAlpha(Palette.OUTLINE, 150))
        d.rrect(c, x + 5f * u, y + 5f * u, x + w - 5f * u, y + h - 5f * u, 18f * u, Palette.WOOD_DARK)
        val tx = d.txt
        tx.setup(32f * u, Palette.TEXT_DIM, outlineW = 0f)
        tx.draw(c, label, x + w / 2f, y + 58f * u, false)
        tx.setup(58f * u, Palette.TEXT, outlineW = 10f * u)
        val b = tx.buf.clear()
        if (isTime) b.addTime(time) else b.addGrouped(value)
        tx.fit("00:00", w - 20f * u, 58f * u)
        tx.draw(c, b, x + w / 2f, y + h - 38f * u)
    }

    private companion object {
        const val BLOCK = 0.8f
    }
}
