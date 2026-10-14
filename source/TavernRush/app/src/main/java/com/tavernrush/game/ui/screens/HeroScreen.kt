package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.PixelBlit
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen

/** Четыре разных старта без платных/временных блокировок. */
class HeroScreen(app: GameApp) : Screen(app) {
    val choices = Array(Content.heroes.size) { Button("", Button.DARK) }
    val menuBtn = Button("Меню", Button.DARK)
    val playBtn = Button("В бой!", Button.PRIMARY)
    private val dst = RectF()
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun layout() {
        val u = ui.u
        val l = ui.cx - ui.contentW / 2f
        val r = ui.cx + ui.contentW / 2f
        val top = ui.top + 300f * u
        val bottom = ui.bottom - 260f * u
        val gap = 26f * u
        val height = minOf(290f * u, (bottom - top - gap * 3f) / 4f)
        for (i in choices.indices) choices[i].set(l, top + i * (height + gap), r, top + i * (height + gap) + height)
        val y = ui.bottom - 195f * u
        menuBtn.set(l, y, l + ui.contentW * 0.3f, y + 145f * u).textSize = 54f * u
        playBtn.set(l + ui.contentW * 0.33f, y, r, y + 145f * u).textSize = 66f * u
    }

    override fun draw(c: Canvas) {
        val u = ui.u
        val bg = app.hubBackdrop
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null) else c.drawColor(Palette.WOOD_DARK)
        d.dim(c, 55)
        val tx = d.txt
        tx.setup(100f * u, Palette.GOLD_LIGHT, outlineW = 18f * u)
        tx.fit("Выберите героя", ui.contentW, 100f * u)
        tx.draw(c, "Выберите героя", ui.cx, ui.top + 145f * u)
        tx.setup(40f * u, Palette.TEXT)
        tx.draw(c, "Разный стиль · все доступны сразу", ui.cx, ui.top + 220f * u)
        for (i in choices.indices) {
            val hero = Content.heroes[i]
            val rect = choices[i].rect
            val selected = game.selectedHero == i
            d.parchment(c, rect.left, rect.top, rect.right, rect.bottom, if (selected) Palette.GOLD else Palette.WOOD_LIGHT)
            val x = rect.left + 130f * u
            val y = rect.centerY()
            fp.color = Palette.withAlpha(hero.color, if (selected) 120 else 65)
            c.drawCircle(x, y, minOf(105f * u, rect.height() * 0.4f), fp)
            val anim = SpriteBank.heroIdle[i.coerceIn(0, SpriteBank.HEROES - 1)]
            val fr = anim.frame(0, (app.time * 4f).toInt())
            val kk = (minOf(220f * u, rect.height() * 0.87f) / fr.h.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            val gy = y + fr.h * kk * 0.5f
            PixelBlit.sprite(c, anim.shadow(0, (app.time * 4f).toInt()), x, gy, kk)
            PixelBlit.sprite(c, fr, x, gy, kk)
            val left = rect.left + 270f * u
            val width = rect.right - left - 28f * u
            tx.setup(64f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
            tx.fit(hero.name, width - if (selected) 110f * u else 0f, 64f * u)
            tx.draw(c, hero.name, left, rect.top + rect.height() * 0.32f, false)
            tx.setup(39f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
            val weapon = Content.weapons[hero.weaponIndex]
            tx.fit(weapon.name, width, 39f * u)
            tx.draw(c, weapon.name, left, rect.top + rect.height() * 0.58f, false)
            tx.setup(38f * u, Palette.INK, Paint.Align.LEFT, 0, 0f)
            tx.fit(hero.trait, width, 38f * u)
            tx.draw(c, hero.trait, left, rect.top + rect.height() * 0.82f, false)
            if (selected) {
                tx.setup(52f * u, Palette.GREEN_DARK, Paint.Align.RIGHT, 0, 0f)
                tx.draw(c, "✓", rect.right - 35f * u, rect.top + rect.height() * 0.32f, false)
            }
        }
        d.button(c, menuBtn, app.time)
        d.button(c, playBtn, app.time)
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        pressButtons(id, x, y, *choices, menuBtn, playBtn)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        val selected = releaseButton(id, x, y)
        when (selected) {
            menuBtn -> game.openMenu()
            playBtn -> game.startRun()
            else -> for (i in choices.indices) if (selected === choices[i]) { game.selectHero(i); break }
        }
    }

    override fun onCancel() = resetPress()
}