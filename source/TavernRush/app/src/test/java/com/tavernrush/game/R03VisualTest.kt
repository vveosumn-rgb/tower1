package com.tavernrush.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.tavernrush.game.content.Content
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.render.pixel.GfxPreset
import com.tavernrush.game.systems.BossSystem
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.systems.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Кадры R03 для глаз: боссы (вход, телеграф, смерть), немезида, роли врагов и аффиксы, пресеты графики,
 * HUD рывка, настройки. Попутно замеряет время кадра по пресетам (прокси на JVM — не FPS телефона).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class R03VisualTest {
    private val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun start(w: Int = 1080, h: Int = 2340, seed: Long = 700L, l: Int = 0, t: Int = 96, r: Int = 0, b: Int = 48): ScreenshotTest.Rig {
        val rig = ScreenshotTest.Rig(w, h, l, t, r, b, seed)
        assertTrue(rig.until(3f) { rig.game.screen == ScreenId.MENU })
        rig.game.startRun(); rig.advance(.02f) // Legacy arena fixture; primary route is the world.
        rig.game.world.godMode = true
        return rig
    }

    /** Оставляем на арене только босса и его призванных — иначе из-за толпы крыс боссов не разглядеть. */
    private fun clear(w: World, keep: Enemy?) {
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e !== keep && !e.summoned) e.alive = false }
    }

    @Test fun bossFights() {
        val cases = listOf(
            Triple("rat_king", "41", { b: Enemy -> b.state == BossSystem.TELE && b.act == BossSystem.A_DASH && b.windup > 0.55f }),
            Triple("ogre_chef", "42", { b: Enemy -> b.state == BossSystem.TELE && b.act == BossSystem.A_SLAM && b.windup > 0.5f }),
            Triple("banshee", "43", { b: Enemy -> b.state == BossSystem.ACT && b.act == BossSystem.A_SPIRAL && b.stateT < 1.7f }),
        )
        for ((i, c) in cases.withIndex()) {
            val (id, num, cond) = c
            val r = start(seed = 700L + i)
            val w = r.game.world
            val b = w.spawnBoss(Content.enemyIndex(id))!!
            r.advance(0.9f); clear(w, b)
            r.shot(dir, "${num}a_${id}_intro")
            assertTrue("$id: вход закончился", r.until(4f) { clear(w, b); b.state != BossSystem.INTRO })
            val hit = r.until(40f) { clear(w, b); b.hp = b.maxHp; cond(b) }
            assertTrue("$id: дождались телеграфа/атаки", hit)
            r.shot(dir, "${num}b_${id}_attack")
            r.advance(0.9f); clear(w, b)
            r.shot(dir, "${num}c_${id}_after")
            b.hp = 5f
            w.combat.hitEnemy(b, 50f, b.x, b.y, 0f, -1)
            r.advance(0.25f)
            r.shot(dir, "${num}d_${id}_kill")
            assertEquals(1, w.run.bossesKilled)
        }
    }

    @Test fun ogreOnSmallScreen() {
        val r = start(720, 1280, 33L, 0, 48, 0, 0)
        val w = r.game.world
        val b = w.spawnBoss(Content.enemyIndex("ogre_chef"))!!
        r.advance(1.0f); clear(w, b)
        r.until(4f) { clear(w, b); b.state != BossSystem.INTRO }
        r.until(40f) { clear(w, b); b.hp = b.maxHp; b.state == BossSystem.TELE && b.act == BossSystem.A_SLAM && b.windup > 0.5f }
        r.shot(dir, "44_ogre_small")
    }

    private fun bossFrame(r: ScreenshotTest.Rig, id: String, name: String, cond: (Enemy) -> Boolean) {
        val w = r.game.world
        val b = w.spawnBoss(Content.enemyIndex(id))!!
        r.advance(0.9f); clear(w, b)
        assertTrue("$id: вход закончился", r.until(4f) { clear(w, b); b.state != BossSystem.INTRO })
        assertTrue("$id: дождались телеграфа/атаки", r.until(40f) { clear(w, b); b.hp = b.maxHp; cond(b) })
        r.shot(dir, name)
    }

    private fun rolesScene(r: ScreenshotTest.Rig, name: String) {
        val w = r.game.world
        clear(w, null)
        fun sp(id: String, x: Float, y: Float, elite: Boolean = false): Enemy = w.spawnEnemy(Content.enemyIndex(id), w.player.x + x, w.player.y + y, elite)!!
        sp("archer", -250f, -120f).cd = 0f
        sp("spider", 230f, -150f).cd = 0f
        sp("boar", -90f, 330f).cd = 0f
        sp("bomber", 120f, 80f)
        sp("shaman", 250f, 280f).cd = 0f
        sp("ghost", -220f, 150f)
        for (k in 1..5) { val e = sp("goblin", -300f + k * 120f, -420f, true); e.affix = k }
        r.advance(0.55f)
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e.def.behavior != com.tavernrush.game.content.EnemyBehavior.CHASE) e.speed *= 0.05f }
        r.advance(0.6f)
        r.shot(dir, name)
    }

    /** Контракт раунда: кадры нового контента на малом экране и планшете. */
    @Test fun r03OnTabletAndSmallScreen() {
        bossFrame(start(1600, 2560, 41L, 0, 50, 0, 40), "rat_king", "48a_rat_king_tablet") { it.state == BossSystem.TELE && it.act == BossSystem.A_DASH && it.windup > 0.55f }
        bossFrame(start(720, 1280, 42L, 0, 48, 0, 0), "banshee", "48b_banshee_small") { it.state == BossSystem.ACT && it.act == BossSystem.A_SPIRAL && it.stateT < 1.7f }
        rolesScene(start(720, 1280, 43L, 0, 48, 0, 0), "48c_roles_small")
        rolesScene(start(1600, 2560, 44L, 0, 50, 0, 40), "48d_roles_tablet")
    }

    @Test fun dashHudAndTrail() {
        val r = start(seed = 51L)
        val w = r.game.world
        r.app.autopilot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 4L)
        r.advance(12f)
        r.app.autopilot = null
        w.moveX = 1f
        r.tap(r.app.hud.dashBtn)
        r.advance(0.07f)
        r.shot(dir, "45_dash_trail")
        assertTrue(w.player.dashCharges < 2)
        r.advance(0.2f)
        r.shot(dir, "45b_dash_cooldown")
    }

    @Test fun nemesisReturnsAndIsRememberedAfterDeath() {
        val rig = ScreenshotTest.Rig(1080, 2340, 0, 96, 0, 48, 77L)
        rig.game.profile.nemesisId = "orc"
        rig.game.profile.nemesisRank = 2
        rig.game.profile.nemesisWave = 3
        rig.game.profile.nemesisSeed = 1234
        assertTrue(rig.until(3f) { rig.game.screen == ScreenId.MENU })
        rig.game.startRun(); rig.advance(.02f) // Legacy arena fixture; primary route is the world.
        val w = rig.game.world
        w.godMode = true
        rig.app.autopilot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 9L)
        assertTrue("немезида пришла", rig.until(160f) { if (rig.game.screen == ScreenId.CARD_PICK) rig.game.pickCard(0); w.activeNemesis() != null })
        rig.advance(0.8f)
        rig.shot(dir, "46a_nemesis_banner")
        val n = w.activeNemesis()!!
        assertTrue(n.named); assertEquals(2, n.rank)
        w.player.x = n.x - 150f; w.player.y = n.y
        rig.advance(2.0f)
        rig.shot(dir, "46b_nemesis_fight")
        // гибель от неё → ранг 3
        rig.app.autopilot = null
        w.godMode = false
        n.damage = 500f
        w.player.hp = 1f
        w.player.x = n.x - 30f; w.player.y = n.y
        assertTrue(rig.until(20f) { w.player.dead })
        assertTrue(rig.until(6f) { rig.game.screen == ScreenId.RESULTS })
        rig.advance(2.8f)
        rig.shot(dir, "46c_results_nemesis")
        assertEquals("orc", rig.game.profile.nemesisId)
        assertEquals("гибель от именной повышает ранг", 3, rig.game.profile.nemesisRank)
    }

    @Test fun rolesAndAffixes() {
        val r = start(seed = 88L)
        val w = r.game.world
        clear(w, null)
        fun sp(id: String, x: Float, y: Float, elite: Boolean = false): Enemy = w.spawnEnemy(Content.enemyIndex(id), w.player.x + x, w.player.y + y, elite)!!
        val a = sp("archer", -250f, -120f); a.cd = 0f
        val s = sp("spider", 230f, -150f); s.cd = 0f
        val b = sp("boar", -90f, 330f); b.cd = 0f
        val bm = sp("bomber", 120f, 80f)
        val sh = sp("shaman", 250f, 280f); sh.cd = 0f
        val g = sp("ghost", -220f, 150f)
        for (k in 1..5) { val e = sp("goblin", -300f + k * 120f, -420f, true); e.affix = k; if (k == 5) { e.shieldMax = e.maxHp * 0.3f; e.shield = e.shieldMax } }
        sp("slime", 0f, 220f)
        r.advance(0.55f)
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e.def.behavior != com.tavernrush.game.content.EnemyBehavior.CHASE) e.speed *= 0.05f }
        r.shot(dir, "47_roles_and_affixes")
        r.advance(0.6f)
        r.shot(dir, "47b_roles_telegraphs")
        assertNotNull(g)
    }

    @Test fun graphicsPresetsSideBySide() {
        val r = start(seed = 66L)
        val w = r.game.world
        r.app.autopilot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 3L)
        r.advance(40f)
        // нагрузка «поздней волны»: ~220 врагов в кадре, частицы, снаряды
        val ids = listOf("rat", "goblin", "bat", "slime", "orc", "archer", "boar", "ghost")
        for (i in 0 until 220) {
            val a = i * 2.399963f
            val rr = 90f + (i % 11) * 55f
            val e = w.spawnEnemy(Content.enemyIndex(ids[i % ids.size]), w.player.x + Math.cos(a.toDouble()).toFloat() * rr * 0.8f, w.player.y + Math.sin(a.toDouble()).toFloat() * rr * 1.5f, i % 29 == 0) ?: break
            e.speed = 0f
        }
        for (i in 0 until 12) w.fx.burst(w.player.x, w.player.y, 0xFFFFD54A.toInt(), 40, 300f, 6f, 5f)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sb = StringBuilder("пресет: время кадра на JVM (программный Skia на x86 — прокси, а не FPS телефона); буфер — работа CPU, подача на экран на устройстве идёт на GPU\n")
        for ((k, p) in listOf(GfxPreset.LOW, GfxPreset.MEDIUM, GfxPreset.HIGH, GfxPreset.ULTRA).withIndex()) {
            r.app.worldRenderer.setPreset(p)
            repeat(6) { r.app.render(c) }
            val t0 = System.nanoTime()
            val n = 40
            repeat(n) { r.app.render(c) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            sb.append(String.format("%s: буфер (CPU) %.2f мс + подача %.2f мс; всего %.2f мс; врагов %d, частиц %d\n", p.name, r.app.worldRenderer.lastBufferMs, r.app.worldRenderer.lastPresentMs, ms, w.enemyCount, w.fx.particleCount))
            r.shot(dir, "5${k}_preset_${p.name.lowercase()}")
        }
        File(dir, "preset_timing.txt").writeText(sb.toString())
        r.app.worldRenderer.setPreset(GfxPreset.AUTO)
    }

    @Test fun hudWithFixedStickLeftDashAndSettingsScreen() {
        val r = start(seed = 99L)
        val p = r.game.profile
        p.stickMode = 1; p.dashRight = false; p.stickSize = 120
        r.app.hud.layoutDash()
        r.advance(1f)
        r.shot(dir, "55_hud_fixed_stick_left_dash")
        r.game.pause()
        r.advance(0.2f)
        r.game.back()  // продолжить
        r.game.abandonRun()
        r.until(6f) { r.game.screen == ScreenId.RESULTS }
        r.game.openMenu()
        r.advance(0.6f)
        r.tap(r.app.menu.settings)
        assertEquals(ScreenId.SETTINGS, r.game.screen)
        r.advance(0.4f)
        r.shot(dir, "56_settings")
    }
}
