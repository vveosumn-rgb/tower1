package com.tavernrush.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.InputQueue
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.meta.Profile
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.save.SaveCodec
import com.tavernrush.game.save.Signer
import com.tavernrush.game.ui.GameApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LeftControlsTest {
    private class Env(w: Int = 1080, h: Int = 2340) {
        val store = MemoryStore()
        val game = Game(store).also { it.fixedSeed = 5L }
        val app = GameApp(game, "R05-test").also { it.setInsets(0, 96, 0, 48); it.resize(w, h) }
        val j get() = app.joystick
        fun step(n: Int = 1) = repeat(n) { app.step(1f / 60f) }
        fun down(id: Int, x: Float, y: Float) { app.input.push(InputQueue.DOWN, id, x, y); app.pumpInput() }
        fun move(id: Int, x: Float, y: Float) { app.input.push(InputQueue.MOVE, id, x, y); app.pumpInput() }
        fun up(id: Int, x: Float, y: Float) { app.input.push(InputQueue.UP, id, x, y); app.pumpInput() }
        fun tap(x: Float, y: Float) { down(1,x,y); step(); up(1,x,y) }
        fun run(): Env { game.startRun(); game.world.godMode = true; step(2); return this }
    }
    private fun oldSigned(p: Profile): String {
        val text = SaveCodec.encode(p)
        val body = text.substringBefore("sig=").lineSequence().filterNot { it.startsWith("controls.rev=") || it.startsWith("stickHeight=") || it.startsWith("stickResponse=") }.joinToString("\n")
        return body + "sig=" + Signer.sign(body) + "\n"
    }
    @Test fun freshProfileUsesFixedLeftAndNoTapDash() {
        val e = Env().run()
        assertEquals(1,e.game.profile.stickMode); assertTrue(e.j.fixed)
        assertFalse(e.game.profile.doubleTapDash); assertTrue(e.j.homeX < e.app.ui.cx)
    }
    @Test fun oldProfileMigratesOnlyControlsAndKeepsProgress() {
        val p = Profile().also { it.gold = 321; it.lifetimeGold = 543; it.runs = 7; it.runCounter = 9; it.stickMode = 0; it.doubleTapDash = true; it.extra["future.x"]="yes" }
        val migrated = SaveCodec.decode(oldSigned(p))
        assertEquals(1,migrated.stickMode); assertFalse(migrated.doubleTapDash); assertEquals(5,migrated.controlsRevision)
        assertEquals(321L,migrated.gold); assertEquals(7,migrated.runs); assertEquals(9L,migrated.runCounter); assertEquals("yes",migrated.extra["future.x"])
    }
    @Test fun explicitR05PreferencesSurviveRoundTrip() {
        val p = Profile().also { it.stickMode=0; it.stickHeight=2; it.stickResponse=0; it.doubleTapDash=true; it.stickSize=140; it.dashRight=false }
        val q=SaveCodec.decode(SaveCodec.encode(p))
        assertEquals(0,q.stickMode);assertEquals(2,q.stickHeight);assertEquals(0,q.stickResponse);assertTrue(q.doubleTapDash);assertEquals(140,q.stickSize);assertFalse(q.dashRight)
    }
    @Test fun wrongControlRangesAreRejected() {
        for (s in listOf("stickHeight=3", "stickResponse=2", "controls.rev=-1")) {
            val text=SaveCodec.encode(Profile());val key=s.substringBefore('=')
            val body=text.substringBefore("sig=").replace(Regex("(?m)^$key=.*$"),s)
            try { SaveCodec.decode(body+"sig="+Signer.sign(body)+"\n"); fail(s) } catch (_: SaveCodec.Invalid) {}
        }
    }
    @Test fun fixedStickCannotBeStartedByWorldOrRightTouches() {
        val e=Env().run()
        for ((x,y) in listOf(540f to 900f, 850f to 1800f, 250f to 500f)) { e.tap(x,y);assertFalse(e.j.active) }
    }
    @Test fun defaultDoubleTapsDoNotDashAnywhere() {
        val e=Env().run()
        e.tap(e.j.homeX,e.j.homeY);e.step(4);e.tap(e.j.homeX,e.j.homeY)
        e.tap(800f,900f);e.step(4);e.tap(800f,900f)
        assertEquals(2,e.game.world.player.dashCharges)
    }
    @Test fun optionalDoubleTapWorksOnlyOnStick() {
        val e=Env().run();e.game.profile.doubleTapDash=true
        e.tap(600f,900f);e.step(4);e.tap(600f,900f);assertEquals(2,e.game.world.player.dashCharges)
        e.tap(e.j.homeX,e.j.homeY);e.step(4);e.tap(e.j.homeX,e.j.homeY);assertEquals(1,e.game.world.player.dashCharges)
    }
    @Test fun unrelatedTapBreaksTheOptionalDashSequence() {
        val e=Env().run();e.game.profile.doubleTapDash=true
        e.tap(e.j.homeX,e.j.homeY);e.tap(800f,900f);e.tap(e.j.homeX,e.j.homeY)
        assertEquals(2,e.game.world.player.dashCharges)
    }
    @Test fun actionTabsHaveVisibleSelectedStateAndPersistChanges() {
        val e=Env();e.game.openControls();e.step()
        assertTrue(e.app.controls.rows[0].visible);assertFalse(e.app.controls.rows[3].visible)
        val tab=e.app.controls.actionsTab.rect;e.tap(tab.centerX(),tab.centerY())
        assertFalse(e.app.controls.rows[0].visible);assertTrue(e.app.controls.rows[3].visible)
        assertEquals(com.tavernrush.game.ui.Button.GOLD,e.app.controls.actionsTab.style)
        val side=e.app.controls.rows[5].rect;e.tap(side.centerX(),side.centerY())
        assertFalse(SaveCodec.decode(e.store.raw()!!).dashRight)
    }
    @Test fun secondPointerCannotStealStickOrItsTapHistory() {
        val e=Env().run();e.game.profile.doubleTapDash=true
        e.down(4,e.j.homeX,e.j.homeY);e.move(4,e.j.homeX+e.j.radius,e.j.homeY)
        e.down(5,e.j.homeX,e.j.homeY);e.up(5,e.j.homeX,e.j.homeY)
        assertEquals(4,e.j.pointer);assertTrue(e.j.outX>0.9f);assertEquals(2,e.game.world.player.dashCharges)
    }
    @Test fun dashWhileHoldingStickPreservesMovementOwner() {
        val e=Env().run();e.down(3,e.j.homeX,e.j.homeY);e.move(3,e.j.homeX,e.j.homeY-e.j.radius);e.step(3)
        val r=e.app.hud.dashBtn.rect;val y=e.game.world.player.y
        e.down(8,r.centerX(),r.centerY());e.up(8,r.centerX(),r.centerY());e.step(8)
        assertEquals(3,e.j.pointer);assertTrue(e.game.world.player.y<y-60f);assertEquals(1,e.game.world.player.dashCharges)
    }
    @Test fun dashSideNeverMovesMovementToRight() {
        val e=Env().run();val x=e.j.homeX;e.game.profile.dashRight=false;e.app.hud.layoutDash();e.step()
        assertEquals(x,e.j.homeX,0f);assertTrue(e.j.homeX<e.app.ui.cx)
        val r=e.app.hud.dashBtn.rect;assertFalse(e.j.canStart(r.centerX(),r.centerY()))
    }
    @Test fun thumbBaseAndKnobRemainLocalAtFullDiagonal() {
        val e=Env().run();val bx=e.j.homeX;val by=e.j.homeY
        e.down(1,bx,by);e.move(1,2000f,2400f)
        assertEquals(bx,e.j.baseX,0f);assertEquals(by,e.j.baseY,0f)
        val dx=e.j.knobX-bx;val dy=e.j.knobY-by
        assertTrue(sqrt(dx*dx+dy*dy)<=e.j.radius+0.1f)
        assertTrue(sqrt(e.j.outX*e.j.outX+e.j.outY*e.j.outY)<=1.001f)
    }
    @Test fun floatingAlternativeAlsoRejectsWholeScreenAndKeepsBaseLocal() {
        val e=Env().run();e.game.profile.stickMode=0;e.step()
        e.tap(800f,1500f);assertFalse(e.j.active)
        e.down(1,e.j.homeX,e.j.homeY);e.move(1,1600f,200f)
        assertTrue(e.j.baseX<e.app.ui.cx);assertTrue(e.j.touchZone.contains(e.j.baseX,e.j.baseY))
        val dx=e.j.knobX-e.j.baseX;val dy=e.j.knobY-e.j.baseY;assertTrue(sqrt(dx*dx+dy*dy)<=e.j.radius+0.1f)
    }
    @Test fun cancelStopsMovementImmediately() {
        val e=Env().run();e.down(1,e.j.homeX,e.j.homeY);e.move(1,e.j.homeX+e.j.radius,e.j.homeY);e.step()
        e.app.input.push(InputQueue.CANCEL,-1,0f,0f);e.app.pumpInput()
        assertFalse(e.j.active);assertEquals(0f,e.game.world.moveX,0f);assertEquals(0f,e.j.outX,0f)
    }
    @Test fun queueOverflowReturnsCancelInsteadOfLosingFingerRelease() {
        val q=InputQueue(3);q.push(InputQueue.DOWN,1,0f,0f);repeat(8){q.push(InputQueue.MOVE,1,1f,1f)}
        assertEquals(1,q.drain());assertEquals(InputQueue.CANCEL,q.type(0));assertEquals(0,q.drain())
        q.push(InputQueue.DOWN,2,2f,2f);assertEquals(1,q.drain());assertEquals(InputQueue.DOWN,q.type(0))
    }
    @Test fun overflowingGameQueueReleasesHeldStick() {
        val e=Env().run();e.down(1,e.j.homeX,e.j.homeY)
        repeat(300){ e.app.input.push(InputQueue.MOVE,1,e.j.homeX+e.j.radius,e.j.homeY) }
        e.app.pumpInput();assertFalse(e.j.active);assertEquals(0f,e.j.outX,0f)
    }
    @Test fun pauseClearsOptionalTapAndHeldFinger() {
        val e=Env().run();e.game.profile.doubleTapDash=true;e.tap(e.j.homeX,e.j.homeY)
        e.game.pause();e.step();e.game.resume();e.step();e.tap(e.j.homeX,e.j.homeY)
        assertEquals(2,e.game.world.player.dashCharges);assertFalse(e.j.active)
    }
    @Test fun resizeCancelsQueuedAndActiveInput() {
        val e=Env().run();e.down(1,e.j.homeX,e.j.homeY);e.app.input.push(InputQueue.MOVE,1,800f,1800f)
        e.app.resize(720,1280);assertFalse(e.j.active);assertEquals(0,e.app.input.drain())
    }
    @Test fun invalidCoordinatesCannotPoisonMovement() {
        val e=Env().run();e.down(1,Float.NaN,e.j.homeY);assertFalse(e.j.active)
        e.down(1,e.j.homeX,e.j.homeY);e.move(1,Float.POSITIVE_INFINITY,0f)
        assertEquals(0f,e.j.outX,0f);assertEquals(0f,e.j.outY,0f)
    }
    @Test fun allSizesAndHeightsFitPhonesTabletsAndInsets() {
        for ((width,height) in listOf(360 to 640,360 to 780,1080 to 2340,1200 to 1920)) {
            val e=Env(width,height)
            for (size in listOf(70,100,120,140)) for (heightMode in 0..2) {
                e.game.profile.stickSize=size;e.game.profile.stickHeight=heightMode;e.app.hud.layoutDash();e.step()
                val j=e.j;assertTrue(j.homeX-j.radius>=e.app.ui.left);assertTrue(j.homeX+j.radius<e.app.ui.cx)
                assertTrue(j.homeY+j.radius<=e.app.ui.bottom);assertTrue(j.canStart(j.homeX,j.homeY))
                val dash=e.app.hud.dashBtn.rect;val team=e.app.hud.teamHud.button
                assertFalse(android.graphics.RectF.intersects(dash,team))
            }
        }
    }
    @Test fun largeStickAdaptsToAsymmetricCutoutWithoutCrashing() {
        val e=Env(360,780);e.game.profile.stickSize=140
        e.app.setInsets(70,50,25,40);e.app.resize(360,780);e.step()
        assertTrue(e.j.homeX-e.j.radius>=e.app.ui.left)
        assertTrue(e.j.homeX+e.j.radius<e.app.ui.cx)
        assertTrue(e.j.canStart(e.j.homeX,e.j.homeY))
        e.app.joystick.down(1,e.j.homeX,e.j.homeY);e.app.joystick.move(1,1000f,1000f)
        assertTrue(e.j.outX.isFinite());assertTrue(e.j.outY.isFinite())
    }
    @Test fun responseAndHeightAreActuallyApplied() {
        val e=Env().run();val normal=e.j.homeY;e.game.profile.stickHeight=2;e.step();assertTrue(e.j.homeY<normal)
        e.down(1,e.j.homeX,e.j.homeY);e.move(1,e.j.homeX+e.j.radius*0.5f,e.j.homeY);val direct=e.j.outX
        e.j.release();e.game.profile.stickResponse=0;e.step();e.down(2,e.j.homeX,e.j.homeY);e.move(2,e.j.homeX+e.j.radius*0.5f,e.j.homeY)
        assertTrue(e.j.outX<direct)
    }
    @Test fun practiceCannotChangeProgressOrAdvanceCombatWorld() {
        val e=Env();e.game.openPractice();e.step();val before=SaveCodec.encode(e.game.profile);val time=e.game.world.time
        e.down(1,e.j.homeX,e.j.homeY);e.move(1,e.j.homeX+e.j.radius,e.j.homeY);e.step(600)
        assertEquals(before,SaveCodec.encode(e.game.profile));assertEquals(time,e.game.world.time,0f);assertFalse(e.game.inRun)
    }
    @Test fun practiceMovementAndDashAreInteractive() {
        val e=Env();e.game.openPractice();e.step();val x=e.app.practice.heroX
        e.down(1,e.j.homeX,e.j.homeY);e.move(1,e.j.homeX+e.j.radius,e.j.homeY);e.step(12)
        assertTrue(e.app.practice.heroX>x);val r=e.app.practice.dash.rect
        e.down(2,r.centerX(),r.centerY());e.up(2,r.centerX(),r.centerY());assertEquals(1,e.app.practice.charges)
        e.step(160);assertEquals(2,e.app.practice.charges)
    }
    @Test fun controlsPageIsReachableAndResetHasSafeDefaults() {
        val e=Env();e.game.openSettings();e.step();val r=e.app.settings.rows[1].rect;e.tap(r.centerX(),r.centerY())
        assertEquals(ScreenId.CONTROLS,e.game.screen)
        e.game.profile.stickMode=0;e.game.profile.doubleTapDash=true
        val b=e.app.controls.defaults.rect;e.tap(b.centerX(),b.centerY())
        assertEquals(1,e.game.profile.stickMode);assertFalse(e.game.profile.doubleTapDash)
        val p=e.app.controls.practice.rect;e.tap(p.centerX(),p.centerY());assertEquals(ScreenId.PRACTICE,e.game.screen)
        e.game.back();assertEquals(ScreenId.CONTROLS,e.game.screen)
    }
    @Test fun layoutAndNewScreensProduceReviewableFrames() {
        for ((width,height) in listOf(360 to 640,360 to 780,1080 to 2340,1200 to 1920)) {
            val e=Env(width,height)
            for ((id,name) in listOf(ScreenId.CONTROLS to "controls",ScreenId.PRACTICE to "practice",ScreenId.RUN to "fixed-left")) {
                if (id==ScreenId.RUN)e.game.startRun() else e.game.go(id)
                e.step(20)
                val b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);e.app.render(Canvas(b))
                val pixels=IntArray(width*height);b.getPixels(pixels,0,width,0,0,width,height)
                val dir=java.io.File(System.getProperty("screenshots.dir") ?: "screenshots").also { it.mkdirs() }
                PngWriter.write(java.io.File(dir,"r05-${name}-${width}x${height}.png"),width,height,pixels);b.recycle()
                if (id==ScreenId.CONTROLS) {
                    val tab=e.app.controls.actionsTab.rect;e.tap(tab.centerX(),tab.centerY());e.step(20)
                    val other=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);e.app.render(Canvas(other))
                    other.getPixels(pixels,0,width,0,0,width,height)
                    PngWriter.write(java.io.File(dir,"r05-actions-${width}x${height}.png"),width,height,pixels);other.recycle()
                }
            }
        }
    }
}
