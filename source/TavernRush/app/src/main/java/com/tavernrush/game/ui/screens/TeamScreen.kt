package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.meta.Friend
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.PixelBlit
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/**
 * Друзья таверны: режим команды (три варианта для сравнения на плейтестах), кто ведущий и кто в резерве,
 * личная история каждого и выбор развития. Развитие — боковая ветка, а не «улучшение»: обе ветки жизнеспособны.
 */
class TeamScreen(app: GameApp) : Screen(app) {
    val modes = Array(Creatures.MODES) { Button(MODE_LABELS[it], Button.DARK) }
    val lead = Array(Content.creatures.size) { Button("Ведущий", Button.DARK) }
    val reserve = Array(Content.creatures.size) { Button("Резерв", Button.DARK) }
    val specs = Array(Content.creatures.size) { Array(2) { Button("", Button.GOLD) } }
    val cards = Array(Content.creatures.size) { Button("", Button.DARK) }
    val moreBtn=Button("4-й вид",Button.DARK)
    var catalogPage=0;private set
    val menuBtn = Button("Меню", Button.DARK)
    val playBtn = Button("В бой!", Button.PRIMARY)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val story = Array(Content.creatures.size) { "" }
    private var hintY = 0f
    private var cardH = 0f

    override fun layout() {
        val u = ui.u
        val l = ui.cx - ui.contentW / 2f
        val r = ui.cx + ui.contentW / 2f
        val gap = 14f * u
        val mw = (ui.contentW - gap * 2f) / 3f
        val my = ui.top + 250f * u
        for (i in modes.indices) modes[i].set(l + i * (mw + gap), my, l + i * (mw + gap) + mw, my + 112f * u).textSize = 46f * u
        hintY = my + 112f * u + 56f * u
        val top = hintY + 78f * u
        val bottom = ui.bottom - 215f * u
        val cg = 20f * u
        cardH = minOf(470f * u, (bottom - top - cg * 2f) / 3f)
        for (i in cards.indices) {
            val t = top + (i%3) * (cardH + cg)
            cards[i].set(l, t, r, t + cardH)
            val bw = 168f * u; val bh = 64f * u
            reserve[i].set(r - 20f * u - bw, t + 18f * u, r - 20f * u, t + 18f * u + bh).textSize = 34f * u
            lead[i].set(r - 30f * u - bw * 2f, t + 18f * u, r - 30f * u - bw, t + 18f * u + bh).textSize = 34f * u
            val sw = (r - l - 270f * u - 20f * u - 14f * u) / 2f
            for (k in 0 until 2) {
                val sl = l + 250f * u + k * (sw + 14f * u)
                specs[i][k].set(sl, t + cardH - 86f * u, sl + sw, t + cardH - 16f * u).textSize = 32f * u
            }
        }
        val y = ui.bottom - 195f * u
        menuBtn.set(l, y, l + ui.contentW * 0.3f, y + 145f * u).textSize = 54f * u
        moreBtn.set(l+ui.contentW*.32f,y,l+ui.contentW*.53f,y+145f*u).textSize=40f*u
        playBtn.set(l + ui.contentW * 0.55f, y, r, y + 145f * u).textSize = 66f * u
        refresh()
    }

    override fun onEnter() = refresh()

    private fun refresh() {
        val p = game.profile
        for (i in modes.indices) modes[i].style = if (p.teamMode == i) Button.GOLD else Button.DARK
        for (i in Content.creatures.indices) {
            val f = p.friend(Content.creatures[i].id)
            val shown=if(catalogPage==0)i<3 else i>=3
            cards[i].visible=shown
            val met = f != null&&shown
            lead[i].visible = met; reserve[i].visible = met
            lead[i].style = if (met && p.teamLead == f!!.id) Button.GOLD else Button.DARK
            reserve[i].style = if (met && p.teamReserve == f!!.id) Button.GOLD else Button.DARK
            if (met) story[i] = "Встреча: волна ${f!!.metWave} · вместе в бою: ${f.bond} · реакций: ${f.practice}"
            val canPick = met && f!!.spec == 0 && f.practice >= Creatures.SPEC_PRACTICE
            for (k in 0 until 2) {
                specs[i][k].visible = canPick
                specs[i][k].label = if (met) Content.creatures[i].specs[k][0] else ""
            }
        }
    }

    override fun draw(c: Canvas) {
        val u = ui.u
        val bg = app.hubBackdrop
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null) else c.drawColor(Palette.WOOD_DARK)
        d.dim(c, 55)
        val tx = d.txt
        tx.setup(100f * u, Palette.GOLD_LIGHT, outlineW = 18f * u)
        tx.fit("Друзья таверны", ui.contentW, 100f * u)
        tx.draw(c, "Друзья таверны", ui.cx, ui.top + 145f * u)
        tx.setup(40f * u, Palette.TEXT)
        tx.draw(c, "Как друзья идут с вами в бой", ui.cx, ui.top + 215f * u)
        for (b in modes) d.button(c, b, app.time)
        val p = game.profile
        tx.setup(38f * u, Palette.TEXT)
        tx.drawWrapped(c, Creatures.modeHints[p.teamMode.coerceIn(0, Creatures.MODES - 1)], ui.cx, hintY, ui.contentW, 44f * u)
        for (i in Content.creatures.indices) if(cards[i].visible)drawCard(c, i, p.friend(Content.creatures[i].id))
        d.button(c,moreBtn,app.time)
        d.button(c, menuBtn, app.time)
        d.button(c, playBtn, app.time)
    }

    private fun drawCard(c: Canvas, i: Int, f: Friend?) {
        val u = ui.u
        val def = Content.creatures[i]
        val rect = cards[i].rect
        val met = f != null
        d.parchment(c, rect.left, rect.top, rect.right, rect.bottom, if (met) Palette.WOOD_LIGHT else Palette.mix(Palette.WOOD_LIGHT, Palette.WOOD_DARK, 0.5f))
        // портрет
        val px = rect.left + 120f * u
        val py = rect.top + minOf(cardH * 0.5f, 190f * u)
        fp.style = Paint.Style.FILL
        fp.color = Palette.withAlpha(def.color, if (met) 95 else 40)
        c.drawCircle(px, py, minOf(100f * u, cardH * 0.34f), fp)
        val anim = SpriteBank.creature(def.look, "idle")
        val fr = anim.frame(0, (app.time * 3.2f).toInt())
        val k = minOf((minOf(200f * u, cardH * 0.52f) / fr.h.coerceAtLeast(1)).toInt(), (205f * u / fr.w.coerceAtLeast(1)).toInt()).coerceAtLeast(1)
        val gy = py + fr.h * k * 0.5f
        PixelBlit.sprite(c, anim.shadow(0, 0), px, gy, k, if (met) 255 else 60)
        PixelBlit.sprite(c, fr, px, gy, k, if (met) 255 else 50)
        val left = rect.left + 250f * u
        val right = rect.right - 24f * u
        val width = right - left
        val tx = d.txt
        if (!met) {
            tx.setup(60f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
            tx.draw(c, "Неизвестный друг", left, rect.top + 72f * u, false)
            tx.setup(38f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
            tx.drawWrapped(c, "Его можно встретить в мире: прислушайтесь к зову или восстановите заросли.", left, rect.top + 130f * u, width, 46f * u, false)
            return
        }
        f!!
        val nameW = width - 370f * u
        tx.setup(60f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
        tx.fit(f.name, nameW, 60f * u)
        tx.draw(c, f.name, left, rect.top + 66f * u, false)
        tx.setup(36f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
        val sub = def.name + " · " + f.traitDef.name
        tx.fit(sub, width, 36f * u)
        tx.draw(c, sub, left, rect.top + 114f * u, false)
        tx.setup(34f * u, Palette.mix(Palette.INK, Palette.PARCHMENT, 0.1f), Paint.Align.LEFT, 0, 0f)
        var y = rect.top + 160f * u
        val lines = tx.drawWrapped(c, def.ability, left, y, width, 40f * u, false)
        y += lines * 40f * u
        val choosing = f.spec == 0 && f.practice >= Creatures.SPEC_PRACTICE
        // История — только если помещается над строкой развития (на малых экранах уступает выбору)
        val storyLimit = rect.bottom - (if (choosing) 112f else 72f) * u
        if (y + 42f * u <= storyLimit) {
            tx.setup(32f * u, Palette.mix(Palette.INK, Palette.PARCHMENT, 0.3f), Paint.Align.LEFT, 0, 0f)
            tx.fit(story[i], width, 32f * u)
            tx.draw(c, story[i], left, y + 6f * u, false)
        }
        // развитие
        val sy = rect.bottom - 100f * u
        tx.setup(33f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
        if (f.spec != 0) {
            val sd = def.specs[f.spec - 1]
            val line = "Развитие: " + sd[0] + " — " + sd[1]
            tx.fit(line, width, 33f * u)
            tx.draw(c, line, left, rect.bottom - 34f * u, false)
        } else if (choosing) {
            if (y + 42f * u + 40f * u <= sy) tx.draw(c, "Выберите развитие:", left, sy + 4f * u, false)
            for (kk in 0 until 2) d.button(c, specs[i][kk], app.time)
        } else if (cardH > 300f * u) {
            val need = Creatures.SPEC_PRACTICE - f.practice
            val line = "Развитие откроется после $need реакций с его участием"
            tx.fit(line, width, 33f * u)
            tx.draw(c, line, left, rect.bottom - 34f * u, false)
        }
        d.button(c, lead[i], app.time)
        d.button(c, reserve[i], app.time)
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        val all = ArrayList<Button>()
        all.addAll(modes); all.addAll(lead); all.addAll(reserve)
        for (r in specs) all.addAll(r)
        all.add(menuBtn); all.add(playBtn);all.add(moreBtn)
        pressButtons(id, x, y, *all.toTypedArray())
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        val b = releaseButton(id, x, y) ?: return
        val p = game.profile
        when {
            b === menuBtn -> game.openMenu()
            b === playBtn -> game.startRun()
            b === moreBtn -> {catalogPage=1-catalogPage;moreBtn.label=if(catalogPage==0)"4-й вид" else "Три вида"}
            else -> {
                for (i in modes.indices) if (b === modes[i]) game.setTeamMode(i)
                for (i in Content.creatures.indices) {
                    val f = p.friend(Content.creatures[i].id) ?: continue
                    if (b === lead[i]) game.setTeamRole(f.id, 0)
                    if (b === reserve[i]) game.setTeamRole(f.id, 1)
                    for (k in 0 until 2) if (b === specs[i][k]) game.chooseFriendSpec(f.id, k + 1)
                }
            }
        }
        refresh()
    }

    override fun onCancel() = resetPress()

    private companion object {
        val MODE_LABELS = arrayOf("Один", "Смена", "Двое")
    }
}
