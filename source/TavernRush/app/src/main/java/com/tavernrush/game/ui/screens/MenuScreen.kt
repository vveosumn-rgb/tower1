package com.tavernrush.game.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.IconId
import com.tavernrush.game.meta.Meta
import com.tavernrush.game.render.Backdrop
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.PixelBlit
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.Ease
import com.tavernrush.game.ui.GameApp
import com.tavernrush.game.ui.Screen
import kotlin.math.sin

/** Главное меню: «Играть», «Таверна», рекорд, золото, звук/вибрация. */
class MenuScreen(app: GameApp) : Screen(app) {
    val play = Button("В мир", Button.PRIMARY)
    val tavern = Button("Таверна", Button.GOLD)
    val heroes = Button("Герои", Button.GOLD)
    val journey = Button("Мир", Button.GOLD)
    val friends = Button("Друзья", Button.GOLD)
    private val sound = Button("", Button.DARK)
    private val vibro = Button("", Button.DARK)
    val settings = Button("", Button.DARK)
    private val note = RectF()
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private var titleY = 0f
    private var heroY = 0f
    private var recordY = 0f

    override fun layout() {
        val u = ui.u
        val cx = ui.cx
        val bw = minOf(760f * u, ui.contentW)
        titleY = ui.top + maxOf(250f * u, ui.h * 0.13f)
        val ground = ui.h * Backdrop.MENU_GROUND
        heroY = ground + 10f * u
        recordY = ground + 205f * u
        val playTop = ground + 260f * u
        // Нижний край ряда «Звук/Вибрация»: если стопка кнопок не помещается, «Играть» и зазоры сжимаются.
        val fullStack = 210f + 44f + 170f + 22f + 112f + 28f + 104f
        val room = (ui.bottom - 24f * u - playTop) / u
        val squeeze = (fullStack - room).coerceIn(0f, 140f)
        val playH = (210f - squeeze * 0.45f) * u
        val gap1 = (44f - squeeze * 0.15f) * u
        val rowH = (170f - squeeze * 0.25f) * u
        play.set(cx - bw / 2f, playTop, cx + bw / 2f, playTop + playH)
        play.textSize = 92f * u
        val tTop = play.rect.bottom + gap1
        tavern.set(cx - bw / 2f, tTop, cx - 16f * u, tTop + rowH)
        tavern.textSize = 66f * u
        heroes.set(cx + 16f * u, tTop, cx + bw / 2f, tTop + rowH)
        heroes.textSize = 66f * u
        heroes.sub = "4 стиля"
        val fTop = tavern.rect.bottom + (22f - squeeze * 0.05f) * u
        friends.set(cx - bw / 2f, fTop, cx - 16f*u, fTop + 112f * u)
        journey.set(cx + 16f*u, fTop, cx + bw/2f, fTop + 112f*u)
        friends.textSize = 56f * u
        val sw = (bw - 32f * u) / 2f
        val sTop = friends.rect.bottom + (28f - squeeze * 0.1f) * u
        sound.set(cx - bw / 2f, sTop, cx - bw / 2f + sw, sTop + 104f * u)
        vibro.set(cx + bw / 2f - sw, sTop, cx + bw / 2f, sTop + 104f * u)
        sound.textSize = 40f * u
        vibro.textSize = 40f * u
        note.set(cx - bw / 2f, titleY + 70f * u, cx + bw / 2f, titleY + 190f * u)
        val gs = 120f * u
        settings.set(ui.left + 28f * u, ui.top + 40f * u, ui.left + 28f * u + gs, ui.top + 40f * u + gs)
    }

    override fun onEnter() = refresh()

    private fun refresh() {
        val p = game.profile
        sound.label = if (p.soundOn) "Звук: вкл" else "Звук: выкл"
        vibro.label = if (p.vibrationOn) "Вибрация: вкл" else "Вибрация: выкл"
        var can = false
        for (i in Content.hubUpgrades.indices) if (Meta.canBuy(p, i)) can = true
        tavern.enabled=game.postRunHubAvailable
        tavern.sub = if(!tavern.enabled)"После похода" else if (can) "Есть покупка!" else "Отдых после похода"
        friends.sub = if (p.friends.isEmpty()) "Встретите в походе" else "${p.friends.size} из ${Content.creatures.size}"
        play.sub = Content.heroes[game.selectedHero].name
    }

    override fun draw(c: Canvas) {
        val u = ui.u
        val p = game.profile
        val t = app.time
        val bg = app.menuBackdrop
        if (bg != null) c.drawBitmap(bg, 0f, 0f, null) else c.drawColor(0xFF1B1424.toInt())

        // логотип
        val tx = d.txt
        val bob = sin(t * 1.6f) * 6f * u
        d.icon(c, IconId.MUG, ui.cx, titleY - 190f * u + bob, 150f * u)
        tx.setup(140f * u, Palette.GOLD_LIGHT, outlineW = 24f * u)
        tx.fit(TITLE, ui.contentW, 140f * u)
        tx.draw(c, TITLE, ui.cx, titleY + bob)

        // уведомление о зачисленной страховке прерванного забега
        if (p.lastBankedGold > 0) {
            d.parchment(c, note.left, note.top, note.right, note.bottom)
            tx.setup(40f * u, Palette.INK, outline = 0, outlineW = 0f)
            val b = tx.buf.clear().add("Прерванный забег: +").add(p.lastBankedGold).add(" золота")
            c.drawText(b.chars, 0, b.length, ui.cx, note.centerY() + 14f * u, tx.fill)
        } else {
            tx.setup(50f * u, Palette.TEXT)
            tx.fit(SUB, ui.contentW, 50f * u)
            tx.draw(c, SUB, ui.cx, titleY + 90f * u)
        }

        // герой у дверей таверны: пиксельная модель, целое увеличение, проекционная тень
        val anim = SpriteBank.heroIdle[game.selectedHero.coerceIn(0, SpriteBank.HEROES - 1)]
        val fr = anim.frame(0, (t * 4f).toInt())
        val kk = (300f * u / fr.h.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val gy = heroY + fr.h * kk * 0.5f
        PixelBlit.sprite(c, anim.shadow(0, (t * 4f).toInt()), ui.cx, gy, kk)
        PixelBlit.sprite(c, fr, ui.cx, gy, kk)
        // друзья ждут у двери: ведущий справа, резерв слева (дом хранит след походов)
        drawFriend(c, p.friend(p.teamLead), ui.cx + 215f * u, gy, 1, t)
        drawFriend(c, p.friend(p.teamReserve), ui.cx - 215f * u, gy, 0, t + 0.4f)

        // рекорд и золото
        if (p.runs > 0) {
            tx.setup(46f * u, Palette.TEXT)
            val b = tx.buf.clear().add("Рекорд: ").addTime(p.bestTime).add("  ·  волна ").add(p.bestWave)
            tx.draw(c, b, ui.cx, recordY)
        } else {
            tx.setup(44f * u, Palette.TEXT)
            tx.fit(FIRST, ui.contentW, 44f * u)
            tx.draw(c, FIRST, ui.cx, recordY)
        }
        d.goldCounter(c, ui.right - 36f * u, ui.top + 96f * u, 60f * u, p.gold.toLong(), Paint.Align.RIGHT)

        // кнопки
        val pulse = 1f + 0.025f * Ease.inOutSine((t * 0.8f) % 1f)
        drawPulsing(c, play, pulse)
        d.button(c, tavern, t)
        d.button(c, heroes, t)
        d.button(c, friends, t); d.button(c, journey, t)
        d.button(c, sound, t)
        d.button(c, vibro, t)
        drawGear(c)
    }

    private fun drawFriend(c: Canvas, f: com.tavernrush.game.meta.Friend?, x: Float, gy: Float, dirIdx: Int, t: Float) {
        f ?: return
        val anim = SpriteBank.creature(f.def.look, "idle")
        val fr = anim.frame(dirIdx, (t * 3.2f).toInt())
        val k = (170f * ui.u / fr.h.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        PixelBlit.sprite(c, anim.shadow(dirIdx, (t * 3.2f).toInt()), x, gy, k)
        PixelBlit.sprite(c, fr, x, gy, k)
    }

    /** Шестерёнка настроек: зубцы и отверстие рисуются кругами и ромбами. */
    private fun drawGear(c: Canvas) {
        d.button(c, settings, app.time)
        val r = settings.rect
        val off = if (settings.pressed) 6f * ui.u else 0f
        val cx = r.centerX(); val cy = r.centerY() - 3f * ui.u + off
        val rad = r.width() * 0.26f
        val fp = Paint(Paint.ANTI_ALIAS_FLAG)
        fp.color = Palette.TEXT
        for (k in 0 until 8) {
            val a = k * 0.7853982f
            c.drawCircle(cx + kotlin.math.cos(a) * rad, cy + kotlin.math.sin(a) * rad, rad * 0.3f, fp)
        }
        c.drawCircle(cx, cy, rad, fp)
        fp.color = Palette.WOOD
        c.drawCircle(cx, cy, rad * 0.45f, fp)
    }

    private fun drawPulsing(c: Canvas, b: Button, k: Float) {
        c.save()
        c.scale(k, k, b.rect.centerX(), b.rect.centerY())
        d.button(c, b, app.time)
        c.restore()
    }

    override fun onDown(id: Int, x: Float, y: Float) {
        pressButtons(id, x, y, play, tavern, heroes, friends, journey, sound, vibro, settings)
    }

    override fun onUp(id: Int, x: Float, y: Float) {
        when (releaseButton(id, x, y)) {
            play -> game.startExpedition()
            tavern -> game.openHub()
            heroes -> game.openHeroes()
            friends -> game.openTeam()
            journey -> game.openJourney()
            sound -> { game.toggleSound(); refresh() }
            vibro -> { game.toggleVibration(); refresh() }
            settings -> game.openSettings()
        }
    }

    override fun onCancel() = resetPress()

    private companion object {
        const val TITLE = "Tavern Rush"
        const val SUB = "Один мир — ваши приключения"
        const val FIRST = "Исследуйте, находите друзей, меняйте мир"
    }
}
