package com.tavernrush.game
import android.graphics.RectF
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.ui.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdventureVisualTest {
    private val dir=File(System.getProperty("screenshots.dir")?:"build/screenshots").apply{mkdirs()}
    private fun rig(w:Int=360,h:Int=640)=ScreenshotTest.Rig(w,h).also{it.advance(2f)}
    @Test fun menuWorldButtonIsSeparateAndWorks(){val r=rig();assertFalse(RectF.intersects(r.app.menu.friends.rect,r.app.menu.journey.rect));r.tap(r.app.menu.journey);assertEquals(ScreenId.JOURNEY,r.game.screen)}
    @Test fun actualScreensFitAndRenderThreeSizes(){
        for((w,h) in listOf(360 to 640,1080 to 2340,1200 to 1920)){
            val r=rig(w,h);r.game.profile.adventure.materials=12;r.game.profile.adventure.rescued=1;r.game.profile.adventure.adopt(0,1);r.game.profile.adventure.adopt(0,2)
            val screens=r.app.adventureScreens
            val openers=listOf<Pair<String,()->Unit>>("journey" to {r.game.openJourney()},"home" to {r.game.openHome()},"workshop" to {r.game.openWorkshop()},"lineage" to {r.game.openLineage()},"trials" to {r.game.openTrials()},"atlas" to {r.game.openAtlas()})
            for((name,open) in openers){open();r.advance(.3f);val visible=screens.buttons.filter{it.visible};for(b in visible){assertTrue("$name $w button ${b.label}",b.rect.top>=0&&b.rect.bottom<=h&&b.rect.left>=0&&b.rect.right<=w)}
                for(i in visible.indices)for(j in 0 until i)assertFalse("overlap $name",RectF.intersects(visible[i].rect,visible[j].rect))
                r.shot(dir,"r06-$name-${w}x$h")}
            r.game.startExpedition();r.advance(.3f);r.shot(dir,"r06-expedition-${w}x$h");assertTrue(r.app.joystick.homeX<r.app.ui.cx)
        }
    }
    @Test fun worldTouchesNeverStealMovement(){val r=rig();r.game.startExpedition();r.advance(.1f);r.tap(180f,320f);assertFalse(r.app.joystick.active)}
    @Test fun workshopRotationAndSaveAreReal(){val r=rig();r.game.profile.adventure.rescued=1;r.game.profile.adventure.materials=2;r.game.openWorkshop();r.advance(.1f);val s=r.app.adventureScreens;val before=s.board.packed();r.tap(s.buttons[0]);assertNotEquals(before,s.board.packed());s.board.load(0);r.tap(s.buttons[9]);assertEquals(1,r.game.profile.adventure.scheme)}
    @Test fun lineageHasExplicitSecondConfirmation(){val r=rig();val p=r.game.profile.adventure;p.materials=4;p.adopt(0,1);p.adopt(0,2);r.game.openLineage();r.advance(.1f);val s=r.app.adventureScreens;r.tap(s.buttons[3]);r.tap(s.buttons[1]);r.tap(s.buttons[4]);r.tap(s.buttons[6]);assertEquals(2,p.animals.size);r.tap(s.buttons[6]);assertEquals(3,p.animals.size);assertEquals(2,p.materials)}
    @Test fun expeditionContextButtonActuallyReturns(){val r=rig();r.game.startExpedition();r.advance(.1f);r.tap(r.app.hud.contextBtn);r.advance(.1f);assertEquals(ScreenId.JOURNEY,r.game.screen)}
    @Test fun abilityButtonsDoNotOverlapMovementOrDash(){val r=rig();r.game.startRun();r.advance(.1f);val h=r.app.hud;assertFalse(RectF.intersects(h.actionBtn.rect,h.dashBtn.rect));assertTrue(h.actionBtn.rect.left>r.app.joystick.homeX);r.tap(h.actionBtn);assertTrue(r.game.world.heroAction.cooldown>0f)}
    @Test fun climateFramesAndDefenceStates(){val r=rig();for(i in 0..5){r.game.startExpedition(i);r.game.world.player.x=r.game.world.adventure.nodesX[1];r.game.world.player.y=r.game.world.adventure.nodesY[1];r.game.interactWorld();r.advance(.3f);r.shot(dir,"r06-climate-$i");r.game.abandonRun()};r.game.startExpedition();val w=r.game.world;val a=w.adventure;w.player.x=260f;w.player.y=150f;r.game.interactWorld();w.player.x=390f;w.player.y=-420f;r.game.interactWorld();r.advance(.1f);r.shot(dir,"r06-defence-plan");r.game.interactWorld();r.advance(.3f);r.shot(dir,"r06-defence-active")}
}
