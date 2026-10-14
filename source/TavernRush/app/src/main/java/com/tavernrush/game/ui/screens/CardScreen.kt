package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.render.Palette
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.systems.CardSystem
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/**
 * Выбор карты (новый уровень / сундук / привал в таверне). Три крупные вертикальные карты
 * под большой палец; первые [BLOCK] с ввод заблокирован, чтобы не выбрать случайно пальцем,
 * которым только что вели героя.
 */
class CardScreen(app: GameApp) : Screen(app) {
    val reroll = Button("", Button.GOLD)
    private val rects = Array(CardSystem.MAX_OFFERS) { RectF() }
    private val tmp = RectF()
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private var appear = 0f
    private var pressedCard = -1
    private var pressedId = -1
    private var headerY = 0f

    override fun onEnter() {
        appear = 0f
        pressedCard = -1; pressedId = -1
        layoutCards()
    }

    override fun layout() = layoutCards()

    /** Прямоугольник карты i (тесты, автотапы). */
    fun cardRect(i: Int): RectF = rects[i]

    private fun layoutCards() {
        val u = ui.u
        val n = world.cards.offerCount.coerceIn(1, CardSystem.MAX_OFFERS)
        val cw = ui.contentW
        headerY = ui.top + maxOf(500f * u, ui.h * 0.2f)
        val top = headerY + 150f * u
        val bottom = ui.bottom - 260f * u
        val gap = 36f * u
        val ch = minOf(380f * u, (bottom - top - 2f * gap) / 3f)
        val total = n * ch + (n - 1) * gap
        var y = top + ((bottom - top) - total) * 0.42f
        for (i in 0 until CardSystem.MAX_OFFERS) {
            rects[i].set(ui.cx - cw / 2f, y, ui.cx + cw / 2f, y + ch)
            if (i < n) y += ch + gap
        }
        reroll.set(ui.cx - 320f * u, ui.bottom - 155f * u, ui.cx + 320f * u, ui.bottom - 55f * u)
        reroll.textSize = 40f * u
        reroll.label = when {
            world.cards.rerollsUsed >= CardSystem.MAX_REROLLS -> "Перебросы закончились"
            world.cards.rerollsUsed == 0 -> "Перебросить · 10 золота"
            else -> "Перебросить · 20 золота"
        }
        reroll.enabled = appear >= BLOCK && world.cards.canReroll
        for (i in 0 until world.cards.offerCount) {
            val mr = minOf(rects[i].height() * 0.34f, 130f * u)
            app.icons.get(world.cards.icon(world.cards.offers[i]), (mr * 1.45f).toInt().coerceAtLeast(8))
        }
    }

    override fun update(dt: Float) {
        appear += dt
        reroll.enabled = appear >= BLOCK && world.cards.canReroll
        val ap = app.autopilot
        if (ap != null && appear > BLOCK + 0.3f) game.pickCard(ap.pickCard(world))
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        if (appear < BLOCK) return
        if (pressButtons(id, x, y, reroll)) return
        val n = world.cards.offerCount
        for (i in 0 until n) {
            if (rects[i].contains(x, y)) { pressedCard = i; pressedId = id; return }
        }
    }

    override fun onMove(id: Int, x: Float, y: Float) {
        if (id != pressedId || pressedCard < 0) return
        val r = rects[pressedCard]
        val m = 60f * ui.u
        if (x < r.left - m || x > r.right + m || y < r.top - m || y > r.bottom + m) pressedCard = -1
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        if (releaseButton(id, x, y) === reroll) {
            if (appear >= BLOCK && world.cards.reroll()) {
                pressedCard = -1; pressedId = -1
                appear = 0f
                layoutCards()
            }
            return
        }
        if (id != pressedId) return
        val i = pressedCard
        pressedCard = -1; pressedId = -1
        if (i < 0 || appear < BLOCK) return
        val r = rects[i]
        val m = 30f * ui.u
        if (x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m) game.pickCard(i)
    }

    override fun onCancel() { pressedCard = -1; pressedId = -1; resetPress() }

    override fun draw(c: Canvas) {
        val u = ui.u
        app.drawWorld(c)
        app.hud.drawHud(c, false)
        d.dim(c, (180f * Ease.clamp01(appear / 0.2f)).toInt())

        val cards = world.cards
        val tx = d.txt
        val ha = (255f * Ease.clamp01(appear / 0.25f)).toInt()
        val reason = cards.reason
        val title = if (cards.jackpot) "Джекпот!" else if (reason == CardSystem.CHEST) T_CHEST else if (reason == CardSystem.TAVERN) T_TAVERN else T_LEVEL
        val sub = if (cards.jackpot) "+100 базового золота · шанс 5%" else if (reason == CardSystem.CHEST) S_CHEST else if (reason == CardSystem.TAVERN) S_TAVERN else S_LEVEL
        val hk = 0.8f + 0.2f * Ease.outBack(appear / 0.35f)
        c.save()
        c.scale(hk, hk, ui.cx, headerY)
        tx.setup(112f * u, Palette.withAlpha(Palette.GOLD_LIGHT, ha), outline = Palette.withAlpha(Palette.OUTLINE, ha), outlineW = 22f * u)
        tx.fit(title, ui.contentW, 112f * u)
        tx.draw(c, title, ui.cx, headerY)
        c.restore()
        tx.setup(50f * u, Palette.withAlpha(Palette.TEXT, ha), outline = Palette.withAlpha(Palette.OUTLINE, ha))
        tx.fit(sub, ui.contentW, 50f * u)
        tx.draw(c, sub, ui.cx, headerY + 80f * u)

        for (i in 0 until cards.offerCount) {
            val k = Ease.outBack((appear - 0.06f - i * 0.07f) / 0.32f)
            if (k <= 0f) continue
            val r = rects[i]
            val dy = (1f - k) * (ui.h - r.top) * 0.6f
            tmp.set(r.left, r.top + dy, r.right, r.bottom + dy)
            if (i == pressedCard) tmp.inset(10f * u, 8f * u)
            drawCard(c, i, tmp)
        }
        var recipe = -1
        var level = -1
        for (i in 0 until world.weaponCount) {
            val wi = world.weapons[i]
            val index = Content.evolutionForWeapon(wi.def.index)
            if (index >= 0 && wi.evolutionIndex < 0 && wi.level > level) { recipe = index; level = wi.level }
        }
        if (recipe >= 0) {
            val e = Content.evolutions[recipe]
            tx.setup(32f * u, Palette.TEXT)
            val b = tx.buf.clear().add("Связка: ").add(Content.passives[e.passiveIndex].name).add(' ')
                .add(world.passiveLevels[e.passiveIndex]).add('/').add(e.passiveLevel).add(" · оружие ").add(level).add('/').add(Content.weapons[e.weaponIndex].maxLevel)
            tx.draw(c, b, ui.cx, ui.bottom - 205f * u)
        }
        reroll.enabled = appear >= BLOCK && cards.canReroll
        d.button(c, reroll, app.time)
        if (appear < BLOCK) {
            // карты «проявляются»: заметно, что пока нажать нельзя
            fp.color = Palette.withAlpha(0xFF0E0A12.toInt(), (110f * (1f - appear / BLOCK)).toInt())
            for (i in 0 until cards.offerCount) {
                val r = rects[i]
                c.drawRoundRect(r.left, r.top, r.right, r.bottom, 26f * u, 26f * u, fp)
            }
        }
    }

    private fun drawCard(c: Canvas, i: Int, r: RectF) {
        val u = ui.u
        val cards = world.cards
        val o = cards.offers[i]
        val color = cards.color(o)
        d.parchment(c, r.left, r.top, r.right, r.bottom, Palette.mix(color, Palette.PARCHMENT_DARK, 0.45f))
        val h = r.height()
        // медальон с иконкой
        val mx = r.left + h * 0.42f
        val my = r.centerY()
        val mr = minOf(h * 0.34f, 130f * u)
        fp.color = Palette.withAlpha(color, 90)
        c.drawCircle(mx, my, mr, fp)
        fp.color = Palette.withAlpha(Palette.OUTLINE, 60)
        c.drawCircle(mx, my + mr * 0.08f, mr * 0.8f, fp)
        d.icon(c, cards.icon(o), mx, my, mr * 1.45f)

        val tl = mx + mr + 34f * u
        val maxW = r.right - tl - 34f * u
        val tx = d.txt
        // заголовок
        val titleY = r.top + minOf(h * 0.3f, 96f * u)
        tx.setup(56f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
        val title = cards.title(o)
        val evolved = o.type == CardSystem.EVOLVE
        val badgeW = if (cards.isNew(o) || evolved) 190f * u else 0f
        tx.fit(title, maxW - badgeW, 56f * u)
        tx.draw(c, title, tl, titleY, false)
        // ярлык «новое» / уровень
        if (cards.isNew(o) || evolved) {
            val bx = r.right - 30f * u - 170f * u
            d.rrect(c, bx, r.top + 24f * u, bx + 170f * u, r.top + 82f * u, 20f * u, if (evolved) Palette.GOLD_DARK else Palette.GREEN_DARK)
            tx.setup(if (evolved) 27f * u else 34f * u, Palette.TEXT, Paint.Align.CENTER, 0, 0f)
            tx.draw(c, if (evolved) "ЭВОЛЮЦИЯ" else "НОВОЕ", bx + 85f * u, r.top + 66f * u, false)
        }
        val max = cards.maxLevel(o)
        var descY = titleY + 62f * u
        if (max > 0) {
            // уровни кружками: заполнено → новый (пульсирует)
            val pr = 13f * u
            var px = tl + pr
            val py = titleY + 36f * u
            for (k in 1..max) {
                if (k == o.toLevel) {
                    // новый уровень: яркий пульсирующий кружок с ореолом
                    val pk = 0.5f + 0.5f * kotlin.math.sin(app.time * 7f)
                    fp.color = Palette.withAlpha(Palette.GOLD, (90f + 80f * pk).toInt())
                    c.drawCircle(px, py, pr * (1.45f + 0.25f * pk), fp)
                    fp.color = Palette.GOLD
                    c.drawCircle(px, py, pr * 1.1f, fp)
                } else {
                    fp.color = if (k < o.toLevel) Palette.GOLD_DARK else Palette.withAlpha(Palette.INK, 50)
                    c.drawCircle(px, py, pr, fp)
                }
                px += pr * 3f
            }
            descY = py + 62f * u
        }
        tx.setup(42f * u, Palette.mix(Palette.INK, Palette.PARCHMENT, 0.15f), Paint.Align.LEFT, 0, 0f)
        val lines = tx.drawWrapped(c, cards.desc(o), tl, descY, maxW, 50f * u, false)
        drawElementLine(c, o, r, tl, maxW, descY + lines * 50f * u)
    }

    /**
     * Стихия оружия и реакция, которую оно даст с уже взятым. Если снизу есть место — строка
     * «Вода · с имеющимся: Проводимость», иначе короткая метка под ярлыком «НОВОЕ» (малые экраны).
     */
    private fun drawElementLine(c: Canvas, o: com.tavernrush.game.systems.CardOffer, r: RectF, x: Float, maxW: Float, descBottom: Float) {
        val el = world.cards.element(o)
        if (el == Element.NONE) return
        val u = ui.u
        val hint = world.cards.reactionHint(o)
        val tx = d.txt
        val col = Palette.mix(el.color, Palette.INK, 0.5f)
        val baseline = r.bottom - 30f * u
        fp.color = Palette.withAlpha(el.color, 230)
        if (descBottom <= baseline - 30f * u) {
            c.drawCircle(x + 11f * u, baseline - 12f * u, 11f * u, fp)
            tx.setup(34f * u, col, Paint.Align.LEFT, 0, 0f)
            val text = if (hint != Reactions.NONE) el.label + " · с имеющимся: " + Reactions.names[hint] else el.label
            tx.fit(text, maxW - 34f * u, 34f * u)
            tx.draw(c, text, x + 32f * u, baseline, false)
        } else {
            val right = r.right - 30f * u
            val y = r.top + 126f * u
            val text = if (hint != Reactions.NONE) Reactions.names[hint] else el.label
            tx.setup(30f * u, col, Paint.Align.RIGHT, 0, 0f)
            tx.fit(text, 230f * u, 30f * u)
            val wText = tx.width(text)
            tx.draw(c, text, right, y, false)
            c.drawCircle(right - wText - 20f * u, y - 10f * u, 10f * u, fp)
        }
    }

    private companion object {
        const val BLOCK = 0.4f
        const val T_LEVEL = "Новый уровень!"
        const val S_LEVEL = "Выберите улучшение"
        const val T_CHEST = "Сундук!"
        const val S_CHEST = "Выберите награду · шанс джекпота 5%"
        const val T_TAVERN = "Привал в таверне"
        const val S_TAVERN = "Трактирщик угощает — выберите блюдо"
    }
}
