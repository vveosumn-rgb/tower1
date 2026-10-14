package com.tavernrush.game

import com.tavernrush.game.core.Game
import com.tavernrush.game.core.InputQueue
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.save.SaveCodec
import com.tavernrush.game.ui.GameApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Управление v2: рывок кнопкой и двойным касанием, направление по стику, фиксированный стик, настройки. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlsTest {
    private class Env(val store: MemoryStore = MemoryStore()) {
        private val DT = 1f / 60f
        val game = Game(store).also { it.fixedSeed = 5L; it.profile.stickMode = 0; it.profile.doubleTapDash = true; it.profile.stickResponse = 0 }
        val app = GameApp(game, "test").also { it.setInsets(0, 96, 0, 48); it.resize(1080, 2340) }
        fun step(n: Int = 1) { repeat(n) { app.step(DT) } }
        fun down(id: Int, x: Float, y: Float) { app.input.push(InputQueue.DOWN, id, x, y); app.pumpInput() }
        fun move(id: Int, x: Float, y: Float) { app.input.push(InputQueue.MOVE, id, x, y); app.pumpInput() }
        fun up(id: Int, x: Float, y: Float) { app.input.push(InputQueue.UP, id, x, y); app.pumpInput() }
        fun tap(x: Float, y: Float, id: Int = 1) { down(id, x, y); step(); up(id, x, y) }
        fun inRun(): Env { step(120); game.startRun(); game.world.godMode = true; step(2); return this }
    }

    @Test fun dashButtonSpendsAChargeAndMovesTheHero() {
        val e = Env().inRun()
        val b = e.app.hud.dashBtn.rect
        val x0 = e.game.world.player.x
        e.tap(b.centerX(), b.centerY())
        assertEquals(1, e.game.world.player.dashCharges)
        e.step(15)
        assertTrue("герой сместился рывком", Math.abs(e.game.world.player.x - x0) + Math.abs(e.game.world.player.y) > 100f)
    }

    @Test fun doubleTapDashesButSlowSecondTapAndDisabledSettingDoNot() {
        val e = Env().inRun()
        e.tap(300f, 1800f)
        e.step(4)
        assertEquals("одно касание — не рывок", 2, e.game.world.player.dashCharges)
        e.tap(300f, 1800f)
        assertEquals("быстрое второе касание — рывок", 1, e.game.world.player.dashCharges)

        val slow = Env().inRun()
        slow.tap(300f, 1800f); slow.step(40); slow.tap(300f, 1800f)
        assertEquals("медленное второе касание — не рывок", 2, slow.game.world.player.dashCharges)

        val off = Env().inRun()
        off.game.profile.doubleTapDash = false
        off.tap(300f, 1800f); off.step(4); off.tap(300f, 1800f)
        assertEquals("в настройках выключено", 2, off.game.world.player.dashCharges)
    }

    @Test fun dashFollowsTheStickDirection() {
        val e = Env().inRun()
        e.down(1, 400f, 1700f)
        e.move(1, 400f, 1700f + 140f)      // стик вниз
        e.step(2)
        val b = e.app.hud.dashBtn.rect
        val y0 = e.game.world.player.y
        e.down(2, b.centerX(), b.centerY()); e.up(2, b.centerX(), b.centerY())
        e.step(8)
        assertTrue("рывок вниз по стику", e.game.world.player.y - y0 > 60f)
        assertEquals("по горизонтали почти нет сдвига", 0f, e.game.world.player.x, 25f)
    }

    @Test fun stickHasADeadZoneAndAGentleCurve() {
        val e = Env().inRun()
        val r = e.app.joystick.radius
        e.down(1, 300f, 1800f)
        e.move(1, 300f + r * 0.05f, 1800f)
        assertEquals("мёртвая зона 8%", 0f, e.app.joystick.outX, 0f)
        e.move(1, 300f + r * 0.5f, 1800f)
        val half = e.app.joystick.outX
        assertTrue("половина хода даёт меньше половины скорости ($half)", half in 0.30f..0.45f)
        e.move(1, 300f + r * 1.0f, 1800f)
        assertEquals("полный ход у края", 1f, e.app.joystick.outX, 0.02f)
    }

    @Test fun fixedStickStaysHomeAndIgnoresFarTouches() {
        val e = Env()
        e.game.profile.stickMode = 1
        e.inRun()
        val j = e.app.joystick
        assertTrue(j.fixed)
        e.down(1, e.app.ui.right - 60f, 300f)
        assertFalse("касание вдали от стика не захватывает его", j.active)
        e.up(1, 0f, 0f)
        e.down(2, j.homeX + 40f, j.homeY)
        assertTrue(j.active)
        val bx = j.baseX
        e.move(2, j.homeX + 900f, j.homeY)
        assertEquals("база не уезжает за пальцем", bx, j.baseX, 0.01f)
        assertTrue(j.outX > 0.9f)
    }

    @Test fun dashSideFollowsTheSetting() {
        val e = Env().inRun()
        val right = e.app.hud.dashBtn.rect.centerX()
        assertTrue(right > e.app.ui.cx)
        e.game.profile.dashRight = false
        e.app.hud.layoutDash()
        assertTrue(e.app.hud.dashBtn.rect.centerX() < e.app.ui.cx)
    }

    @Test fun settingsCycleApplyAndPersist() {
        val e = Env()
        e.step(120)
        e.game.openSettings()
        e.step(2)
        val rows = e.app.settings.rows
        e.tap(rows[0].rect.centerX(), rows[0].rect.centerY())
        assertEquals(1, e.game.profile.gfxPreset)
        e.tap(rows[1].rect.centerX(), rows[1].rect.centerY())
        assertEquals(ScreenId.CONTROLS, e.game.screen)
        val mode = e.app.controls.rows[0].rect
        e.tap(mode.centerX(), mode.centerY())
        assertEquals(1, e.game.profile.stickMode)
        val tab = e.app.controls.actionsTab.rect
        e.tap(tab.centerX(), tab.centerY())
        val side = e.app.controls.rows[5].rect
        e.tap(side.centerX(), side.centerY())
        assertFalse(e.game.profile.dashRight)
        e.game.back(); e.step()
        e.tap(rows[2].rect.centerX(), rows[2].rect.centerY())
        assertEquals(40, e.game.profile.shakePct)
        e.step(3)
        assertEquals("пресет применён к рендереру", com.tavernrush.game.render.pixel.GfxPreset.LOW, e.app.worldRenderer.cfg.preset)
        val saved = SaveCodec.decode(e.store.raw()!!)
        assertEquals(1, saved.gfxPreset); assertEquals(1, saved.stickMode); assertFalse(saved.dashRight); assertEquals(40, saved.shakePct)
        e.game.back()
        assertEquals(ScreenId.MENU, e.game.screen)
    }

    @Test fun cameraLooksAheadAndHeroAcceleratesSmoothly() {
        val e = Env().inRun()
        val w = e.game.world
        e.down(1, 300f, 1700f)
        e.move(1, 300f + e.app.joystick.radius * 1.2f, 1700f)
        e.step(3)
        assertTrue("разгон: за 3 кадра скорость не полная (${w.player.vx})", w.player.vx in 1f..(World_BASE() - 1f))
        e.step(60)
        assertEquals("полная скорость", World_BASE() * w.stats.moveSpeed, w.player.vx, 1f)
        assertTrue("камера впереди героя по бегу (${w.camX - w.player.x})", w.camX - w.player.x in 5f..60f)
    }

    private fun World_BASE() = com.tavernrush.game.systems.World.BASE_SPEED
}
