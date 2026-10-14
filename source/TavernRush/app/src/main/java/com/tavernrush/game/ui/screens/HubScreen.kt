package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.IconId
import com.tavernrush.game.meta.Meta
import com.tavernrush.game.render.Palette
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/** Таверна (хаб): постоянные улучшения за золото, «В бой!», «Меню». */
class HubScreen(app: GameApp) : Screen(app) {
    private val n = Content.hubUpgrades.size
    private val rows = Array(n) { RectF() }
    val buy = Array(n) { Button("", Button.GOLD) }
    val fight = Button("В мир", Button.PRIMARY)
    val menuBtn = Button("Меню", Button.DARK)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lacking = IntArray(n)
    private val lackTimer = FloatArray(n)
    private var titleY = 0f
    private var goldY = 0f
    private var statsY = 0f

    override fun layout() {
        val u = ui.u
        val cw = ui.contentW
        val l = ui.cx - cw / 2f
        val r = ui.cx + cw / 2f
        titleY = ui.top + maxOf(170f * u, ui.h * 0.09f)
        goldY = titleY + 160f * u
        val bottomBtnTop = ui.bottom - 60f * u - 190f * u
        fight.set(l + 260f * u, bottomBtnTop, r, bottomBtnTop + 190f * u); fight.textSize = 84f * u
        menuBtn.set(l, bottomBtnTop + 30f * u, l + 230f * u, bottomBtnTop + 190f * u); menuBtn.textSize = 48f * u
        statsY = bottomBtnTop - 50f * u
        val top = goldY + 70f * u
        val bottom = statsY - 70f * u
        val gap = 30f * u
        val rh = minOf(290f * u, (bottom - top - gap * (n - 1)) / n)
        val total = n * rh + (n - 1) * gap
        var y = top + ((bottom - top) - total) * 0.45f
        for (i in 0 until n) {
            rows[i].set(l, y, r, y + rh)
            val bw = 270f * u
            val bh = minOf(130f * u, rh - 60f * u)
            buy[i].set(r - 30f * u - bw, y + rh / 2f - bh / 2f, r - 30f * u, y + rh / 2f + bh / 2f)
            buy[i].textSize = 52f * u
            y += rh + gap
        }
        refresh()
    }

    override fun onEnter() {
        for (i in 0 until n) lackTimer[i] = 0f
        refresh()
    }

    /** Подписи кнопок меняются только после покупки — строки создаются здесь, не в кадре. */
    private fun refresh() {
        val p = game.profile
        val coin = icons().get(IconId.LUCKY_COIN, (56f * ui.u).toInt())
        for (i in 0 until n) {
            val b = buy[i]
            val cost = Meta.nextCost(p, i)
            if (cost < 0) {
                b.label = "Макс."; b.icon = null; b.enabled = false; b.style = Button.DARK
            } else {
                b.label = cost.toString(); b.icon = coin; b.enabled = true
                b.style = if (p.gold >= cost) Button.GOLD else Button.DARK
                lacking[i] = (cost - p.gold).toInt().coerceAtLeast(0)
            }
        }
    }

    private fun icons() = d.icons

    override fun update(dt: Float) {
        for (i in 0 until n) if (lackTimer[i] > 0f) lackTimer[i] -= dt
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        if (pressButtons(id, x, y, fight, menuBtn)) return
        pressButtons(id, x, y, *buy)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        val b = releaseButton(id, x, y) ?: return
        when (b) {
            fight -> game.startExpedition()
            menuBtn -> game.openMenu()
            else -> for (i in 0 until n) if (b === buy[i]) {
                if (game.buyUpgrade(i)) refresh() else if (Meta.nextCost(game.profile, i) > 0) lackTimer[i] = 1.4f
            }
        }
    }

    override fun onCancel() = resetPress()

    override fun draw(c: Canvas) {
        val u = ui.u
        val p = game.profile
        val bg = app.hubBackdrop
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null) else c.drawColor(0xFF2A1C14.toInt())
        val tx = d.txt
        tx.setup(112f * u, Palette.GOLD_LIGHT, outlineW = 22f * u)
        tx.draw(c, "Таверна", ui.cx, titleY)
        tx.setup(40f * u, Palette.TEXT)
        tx.fit(SUB, ui.contentW, 40f * u)
        tx.draw(c, SUB, ui.cx, titleY + 64f * u)
        d.goldCounter(c, ui.cx, goldY, 64f * u, p.gold, Paint.Align.CENTER)

        for (i in 0 until n) drawRow(c, i)

        // статистика
        tx.setup(38f * u, Palette.TEXT)
        val b = tx.buf.clear().add("Забегов: ").add(p.runs).add("  ·  рекорд ").addTime(p.bestTime).add("  ·  побед: ").add(p.victories)
        tx.draw(c, b, ui.cx, statsY)
        d.button(c, menuBtn, app.time)
        val k = 1f + 0.02f * Ease.inOutSine((app.time * 0.8f) % 1f)
        c.save()
        c.scale(k, k, fight.rect.centerX(), fight.rect.centerY())
        d.button(c, fight, app.time)
        c.restore()
    }

    private fun drawRow(c: Canvas, i: Int) {
        val u = ui.u
        val p = game.profile
        val def = Content.hubUpgrades[i]
        val r = rows[i]
        val lvl = p.upgrades[i]
        val bought = game.lastBought == i && game.lastBoughtTime > 0f
        if (bought) {
            val a = (200f * Ease.clamp01(game.lastBoughtTime / 0.8f)).toInt()
            d.rrect(c, r.left - 12f * u, r.top - 12f * u, r.right + 12f * u, r.bottom + 12f * u, 34f * u, Palette.withAlpha(Palette.GOLD_LIGHT, a))
        }
        d.parchment(c, r.left, r.top, r.right, r.bottom)
        val h = r.height()
        val ix = r.left + h * 0.42f
        val iy = r.centerY()
        fp.color = Palette.withAlpha(Palette.GOLD_DARK, 70)
        c.drawCircle(ix, iy, h * 0.33f, fp)
        d.icon(c, def.icon, ix, iy, h * 0.5f)

        val tl = ix + h * 0.33f + 28f * u
        val maxW = buy[i].rect.left - tl - 20f * u
        val tx = d.txt
        tx.setup(50f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
        tx.fit(def.name, maxW, 50f * u)
        tx.draw(c, def.name, tl, r.top + h * 0.36f, false)
        tx.setup(38f * u, Palette.mix(Palette.INK, Palette.PARCHMENT, 0.2f), Paint.Align.LEFT, 0, 0f)
        tx.fit(def.desc, maxW, 38f * u)
        tx.draw(c, def.desc, tl, r.top + h * 0.58f, false)
        // уровни
        val pr = 14f * u
        var px = tl + pr
        val py = r.top + h * 0.78f
        for (k in 1..def.maxLevel) {
            fp.color = if (k <= lvl) (if (bought && k == lvl) Palette.GOLD_LIGHT else Palette.GOLD_DARK) else Palette.withAlpha(Palette.INK, 45)
            c.drawCircle(px, py, pr, fp)
            px += pr * 2.8f
        }
        d.button(c, buy[i], app.time)
        if (lackTimer[i] > 0f) {
            val a = (255f * Ease.clamp01(lackTimer[i] / 0.4f)).toInt()
            tx.setup(32f * u, Palette.withAlpha(Palette.RED_DARK, a), Paint.Align.CENTER, 0, 0f)
            val bb = tx.buf.clear().add("Не хватает ").add(lacking[i])
            c.drawText(bb.chars, 0, bb.length, buy[i].rect.centerX(), buy[i].rect.bottom + 34f * u, tx.fill)
        }
    }

    private companion object {
        const val SUB = "Улучшения действуют в каждом забеге"
    }
}
