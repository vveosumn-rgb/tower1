package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.render.pixel.GfxPreset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class World17VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 private val sizes=listOf(360 to 640,1080 to 2340,1200 to 1920)
 @Test fun realPrimaryButtonEntersWorldAndHubOnlyAfterItEnds(){for((width,height)in sizes){val r=ScreenshotTest.Rig(width,height);r.advance(2f);assertFalse(r.app.menu.tavern.enabled);r.tap(r.app.menu.tavern);assertEquals(ScreenId.MENU,r.game.screen);r.shot(dir,"r17-start-world-${width}x$height");r.tap(r.app.menu.play);assertTrue(r.game.world.adventure.active);assertEquals(ScreenId.RUN,r.game.screen);val j=r.app.joystick;val q=r.app.input;q.push(InputQueue.DOWN,10,j.homeX,j.homeY);q.push(InputQueue.MOVE,10,j.homeX+25f,j.homeY);r.app.pumpInput();assertEquals(10,j.pointer);assertTrue(j.active);r.game.pause();r.game.openHub();assertEquals(ScreenId.PAUSE,r.game.screen);r.game.abandonRun();r.advance(.1f);r.game.openHub();r.advance(.1f);assertEquals(ScreenId.HUB,r.game.screen);assertTrue(r.game.postRunHubAvailable);r.shot(dir,"r17-after-match-tavern-${width}x$height");r.tap(r.app.hub.fight);assertEquals(ScreenId.RUN,r.game.screen);assertTrue(r.game.world.adventure.active);assertFalse(r.game.postRunHubAvailable)}}
 @Test fun heightDepthFadeAndOpenPassageOnThreeSizes(){for((width,height)in sizes){val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.startExpedition();r.advance(.4f);val w=r.game.world;w.weaponCount=0;w.godMode=true;w.camX=0f;w.camY=125f;w.player.x=0f;w.player.y=210f;r.shot(dir,"r17-wall-front-${width}x$height");w.player.y=60f;r.shot(dir,"r17-wall-behind-fade-${width}x$height");w.adventure.start(0,2,17);w.camX=0f;w.camY=125f;w.player.x=0f;w.player.y=120f;r.shot(dir,"r17-wall-open-${width}x$height");w.adventure.start(0,0,17);w.camX=0f;w.camY=125f;w.player.x=0f;w.player.y=210f;r.app.worldRenderer.setPreset(GfxPreset.LOW);r.shot(dir,"r17-wall-low-${width}x$height");assertTrue(w.adventure.map.blocked(0f,120f,20f,0))}}
 @Test fun sixRegionMaterialsAreRenderedNotRenamed(){for(region in 0..5){val r=ScreenshotTest.Rig(360,640);r.advance(2f);r.game.startExpedition(region);r.advance(.4f);val w=r.game.world;w.weaponCount=0;w.godMode=true;val b=w.adventure.map.walls[0];w.camX=b.x;w.camY=b.y+40f;w.player.x=b.x+b.hx+60f;w.player.y=b.y+80f;r.shot(dir,"r17-wall-material-$region-360x640")}}
}
