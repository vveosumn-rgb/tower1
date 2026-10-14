package com.tavernrush.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.InputQueue
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.meta.Meta
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.systems.CardSystem
import com.tavernrush.game.ui.Button
import com.tavernrush.game.ui.GameApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Скриншоты ключевых экранов (PNG из каталога screenshots; обзорная сетка в verify.sh) и проверка
 * исторической арены: явный startRun → карты → пауза → смерть → итоги → таверна → покупка.
 * Новый первичный путь «В мир» проверяется настоящим касанием в World17VisualTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    private val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    class Rig(val w: Int, val h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, seed: Long = 20261013L) {
        val game = Game(MemoryStore()).also { it.fixedSeed = seed }
        val app = GameApp(game, "Tavern Rush test")
        private val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        private val canvas = Canvas(bmp)

        init {
            app.setInsets(l, t, r, b)
            app.resize(w, h)
        }

        fun advance(sec: Float) {
            val n = (sec * 60f).toInt()
            for (i in 0 until n) app.step(DT)
        }

        fun until(maxSec: Float, cond: () -> Boolean): Boolean {
            var n = (maxSec * 60f).toInt()
            while (n-- > 0) {
                if (cond()) return true
                app.step(DT)
            }
            return cond()
        }

        fun tap(x: Float, y: Float) {
            app.input.push(InputQueue.DOWN, 1, x, y)
            app.pumpInput()
            app.step(DT)
            app.input.push(InputQueue.UP, 1, x, y)
            app.pumpInput()
        }

        fun tap(b: Button) = tap(b.rect.centerX(), b.rect.centerY())

        fun back() {
            app.input.push(InputQueue.BACK, 0, 0f, 0f)
            app.pumpInput()
        }

        fun shot(dir: File, name: String) {
            app.render(canvas)
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun legacyArenaFlowWithExplicitStart() {
        // 1080×2340 (20:9) с вырезом камеры сверху и жестовой панелью снизу
        val r = Rig(1080, 2340, 0, 96, 0, 48)
        r.advance(0.7f); r.shot(dir, "01_splash")
        assertTrue(r.until(3f) { r.game.screen == ScreenId.MENU })
        r.advance(0.5f); r.shot(dir, "02_menu")

        r.game.startRun(); r.advance(.02f) // Legacy arena fixture, no longer the primary menu route.
        assertEquals(ScreenId.RUN, r.game.screen)
        r.advance(1.2f); r.shot(dir, "03_run_start")

        r.app.autopilot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 7L)
        assertTrue("нет первого уровня за минуту", r.until(60f) { r.game.screen == ScreenId.CARD_PICK })
        r.app.autopilot = null
        r.advance(0.2f)
        val early = r.app.cards.cardRect(0)
        r.tap(early.centerX(), early.centerY())
        assertEquals("тап в первые 0.4 с не должен выбирать карту", ScreenId.CARD_PICK, r.game.screen)
        r.advance(0.6f); r.shot(dir, "04_cards")
        val rect = r.app.cards.cardRect(0)
        r.tap(rect.centerX(), rect.centerY())
        assertEquals(ScreenId.RUN, r.game.screen)

        r.app.autopilot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 7L)
        assertTrue(r.until(90f) { r.game.world.time >= 62f && r.game.screen == ScreenId.RUN })
        r.shot(dir, "05_run_1min")

        r.game.world.godMode = true
        assertTrue(r.until(420f) { r.game.world.time >= 385f && r.game.screen == ScreenId.RUN })
        r.shot(dir, "06_run_late")

        r.tap(r.app.hud.pauseBtn)
        assertEquals(ScreenId.PAUSE, r.game.screen)
        r.advance(0.3f); r.shot(dir, "07_pause")
        r.back()
        assertEquals("«назад» на паузе — продолжить", ScreenId.RUN, r.game.screen)
        r.back()
        assertEquals("«назад» в забеге — пауза", ScreenId.PAUSE, r.game.screen)
        r.tap(r.app.pauseScreen.resume)
        assertEquals(ScreenId.RUN, r.game.screen)

        r.app.autopilot = null          // стоим на месте — орда быстро догонит
        r.game.world.godMode = false
        r.game.world.player.hp = 1f
        assertTrue(r.until(60f) { r.game.world.player.dead })
        r.advance(0.8f); r.shot(dir, "08_death")
        assertTrue(r.until(5f) { r.game.screen == ScreenId.RESULTS })
        r.advance(2.8f); r.shot(dir, "09_results")
        assertTrue(r.game.result.total > 0)

        r.tap(r.app.results.tavern)
        assertEquals(ScreenId.HUB, r.game.screen)
        r.advance(0.6f); r.shot(dir, "10_hub")
        val gold0 = r.game.profile.gold
        val i = (0 until 4).firstOrNull { Meta.canBuy(r.game.profile, it) }
        assertTrue("после 6+ минут должно хватать на улучшение (золото $gold0)", i != null)
        r.tap(r.app.hub.buy[i!!])
        assertEquals(1, r.game.profile.upgrades[i])
        assertTrue(r.game.profile.gold < gold0)
        r.advance(0.2f); r.shot(dir, "11_hub_bought")

        r.tap(r.app.hub.menuBtn)
        assertEquals(ScreenId.MENU, r.game.screen)
        r.advance(0.5f); r.shot(dir, "12_menu_return")
    }

    @Test
    fun otherSizes() {
        shotsFor(Rig(720, 1280, 0, 48, 0, 0, seed = 11L), "13_small")
        shotsFor(Rig(1440, 3200, 0, 130, 0, 70, seed = 12L), "14_qhd")
        val tab = Rig(1600, 2560, 0, 50, 0, 40, seed = 13L)
        shotsFor(tab, "15_tablet")
        tab.back()   // пауза
        tab.tap(tab.app.pauseScreen.quit)
        tab.tap(tab.app.pauseScreen.quit)   // подтверждение
        assertEquals(ScreenId.RESULTS, tab.game.screen)
        tab.advance(2.5f); tab.shot(dir, "15_tablet_c_results")
    }

    @Test
    fun arsenalFeatures() {
        val r = Rig(1080, 2340, 0, 96, 0, 48, seed = 21L)
        assertTrue(r.until(3f) { r.game.screen == ScreenId.MENU })
        r.tap(r.app.menu.heroes)
        assertEquals(ScreenId.HEROES, r.game.screen)
        r.advance(0.6f); r.shot(dir, "16_heroes")
        r.tap(r.app.heroes.choices[2])
        assertEquals(2, r.game.selectedHero)
        r.shot(dir, "17_hero_sage_selected")
        r.tap(r.app.heroes.playBtn)
        assertEquals(ScreenId.RUN, r.game.screen)
        assertEquals(2, r.game.world.heroIndex)
        val w = r.game.world
        w.godMode = true
        for (id in arrayOf("frost", "trap", "raven", "bell", "cauldron")) assertTrue(w.addWeapon(Content.weaponIndex(id)))
        w.player.hp = 50f
        for (i in 0 until 12) {
            val x = (i % 4 - 1.5f) * 85f
            val y = (i / 4 - 1f) * 95f + 30f
            val enemy = w.spawnEnemy(i % Content.enemies.size, x, y, false)!!
            enemy.hp = 100000f; enemy.maxHp = enemy.hp; enemy.speed = 22f
        }
        r.advance(3.4f)
        w.weapons[w.findWeapon(Content.weaponIndex("sunbeam"))].timer = 0f
        r.app.step(DT)
        r.shot(dir, "18_new_arsenal_run")

        val e = Rig(720, 1280, 0, 48, 0, 0, seed = 22L)
        e.game.startRun()
        e.game.world.godMode = true
        val ew = e.game.world
        repeat(ew.weapons[0].def.maxLevel - 1) { ew.upgradeWeapon(0) }
        repeat(3) { ew.addPassive(Content.passiveIndex("fingers")) }
        ew.run.gold = 50f
        ew.pendingLevelUps = 1
        e.game.tick(DT)
        assertEquals(ScreenId.CARD_PICK, e.game.screen)
        assertEquals(CardSystem.EVOLVE, ew.cards.offers[0].type)
        e.advance(0.8f); e.shot(dir, "19_evolution_offer_small")
        e.tap(e.app.cards.reroll)
        assertEquals(40f, ew.run.gold, 0f)
        assertEquals(1, ew.cards.rerollsUsed)
        e.advance(0.8f); e.shot(dir, "20_reroll_small")
        val card = e.app.cards.cardRect(0)
        e.tap(card.centerX(), card.centerY())
        assertEquals(ScreenId.RUN, e.game.screen)
        assertEquals(0, ew.weapons[0].evolutionIndex)
        ew.spawnEnemy(1, 120f, 30f, true)!!.also { it.hp = 100000f; it.maxHp = it.hp; it.speed = 0f }
        e.advance(0.45f); e.shot(dir, "21_evolved_run_small")

        val j = Rig(1440, 3200, 0, 130, 0, 70, seed = 23L)
        j.game.startRun()
        j.game.world.godMode = true
        j.game.world.run.gold = 40f
        var guard = 0
        do {
            j.game.world.pendingChests++
            j.game.tick(DT)
            if (!j.game.world.cards.jackpot) j.game.pickCard(0)
        } while (!j.game.world.cards.jackpot && guard++ < 1000)
        assertTrue(j.game.world.cards.jackpot)
        j.advance(0.8f); j.shot(dir, "22_jackpot_qhd")

        val small = Rig(720, 1280, 0, 48, 0, 0, seed = 24L)
        assertTrue(small.until(3f) { small.game.screen == ScreenId.MENU })
        small.tap(small.app.menu.heroes)
        small.tap(small.app.heroes.choices[3])
        small.advance(0.6f); small.shot(dir, "23_heroes_small")
        small.tap(small.app.heroes.menuBtn)
        small.advance(0.6f); small.shot(dir, "24_ranger_menu_small")
    }

    @Test
    fun extraArsenalLayouts() {
        val t = Rig(1600, 2560, 0, 50, 0, 40, seed = 25L)
        assertTrue(t.until(3f) { t.game.screen == ScreenId.MENU })
        t.tap(t.app.menu.heroes)
        t.tap(t.app.heroes.choices[1])
        t.advance(0.6f); t.shot(dir, "25_guardian_tablet")
        t.tap(t.app.heroes.playBtn)
        t.game.world.godMode = true
        for (id in arrayOf("frost", "sunbeam", "trap", "raven", "cauldron"))
            assertTrue(t.game.world.addWeapon(Content.weaponIndex(id)))
        for (i in 0 until 8) t.game.world.spawnEnemy(i % Content.enemies.size, (i % 4 - 1.5f) * 75f, (i / 4 - 0.5f) * 110f, false)!!.also {
            it.hp = 100000f; it.maxHp = it.hp; it.speed = 12f
        }
        t.advance(3f); t.shot(dir, "26_guardian_arsenal_tablet")

        val h = Rig(720, 1280, 0, 48, 0, 0, seed = 26L)
        h.game.startRun()
        h.game.world.player.hp = 5f
        h.game.world.pendingLevelUps = 1
        h.game.tick(DT)
        assertEquals(CardSystem.HEAL, h.game.world.cards.offers[0].type)
        h.advance(0.8f); h.shot(dir, "27_emergency_heal_small")
        val card = h.app.cards.cardRect(0)
        h.tap(card.centerX(), card.centerY())
        assertEquals(ScreenId.RUN, h.game.screen)
        assertTrue(h.game.world.player.hp > 50f)
    }

    private fun shotsFor(r: Rig, prefix: String) {
        assertTrue(r.until(3f) { r.game.screen == ScreenId.MENU })
        r.game.startRun(); r.advance(.02f) // Legacy arena fixture, no longer the primary menu route.
        r.app.autopilot = BotPilot(BotPilot.CASUAL, BotPilot.GREEDY, 3L)
        assertTrue(r.until(80f) { r.game.world.time >= 45f && r.game.screen == ScreenId.RUN })
        r.shot(dir, "${prefix}_a_run")
        // Это проверка раскладки, не баланса: вызываем уровень через настоящий XP-путь.
        // Реальный первый уровень и экономика отдельно проверяются в mainFlow.
        r.app.autopilot = null
        r.game.world.addXp(r.game.world.player.xpNeed)
        assertTrue(r.until(2f) { r.game.screen == ScreenId.CARD_PICK })
        r.advance(0.8f); r.shot(dir, "${prefix}_b_cards")
        val c = r.app.cards.cardRect(1)
        r.tap(c.centerX(), c.centerY())
        assertEquals(ScreenId.RUN, r.game.screen)
    }

    companion object {
        const val DT = 1f / 60f
    }
}
