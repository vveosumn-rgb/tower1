package com.tavernrush.game
import android.graphics.RectF
import com.tavernrush.game.core.*
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
class Region11VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots"))
 private fun rig(w:Int=360,h:Int=640)=ScreenshotTest.Rig(w,h).also{it.advance(2f)}
 @Test fun authoredWorldGeometryRenders(){val r=rig();for(region in 0..5){r.game.startExpedition(region);val a=r.game.world.adventure;r.game.world.player.x=a.nodesX[0]+220f;r.game.world.player.y=a.nodesY[0]-120f;r.game.world.camX=r.game.world.player.x;r.game.world.camY=r.game.world.player.y;r.advance(.1f);r.shot(dir,"r11-ground-$region-360x640");r.game.abandonRun()}}
 @Test fun allSixMapsRenderAndFitThreeSizes(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=rig(width,height)
  for(region in 0..5){r.game.startExpedition(region);r.game.pause();r.advance(.1f);assertTrue(r.app.pauseScreen.map.visible);r.tap(r.app.pauseScreen.map);r.advance(.2f);assertEquals(ScreenId.ROUTE_MAP,r.game.screen);val s=r.app.routeMap
   val bs=listOf(s.track,s.clear,s.back);for(b in bs){assertTrue(b.rect.width()>=48f*(width/360f));assertTrue("height $width x $height ${b.label}: ${b.rect.height()}",b.rect.height()>=44f*(width/360f));assertTrue(b.rect.top>=0f&&b.rect.bottom<=height)}
   for(i in bs.indices)for(j in 0 until i)assertFalse(RectF.intersects(bs[i].rect,bs[j].rect))
   assertTrue(s.chart.height()>150f);assertFalse(RectF.intersects(s.chart,s.track.rect));r.shot(dir,"r11-map-$region-${width}x$height");r.game.abandonRun()
  }
 }}
 @Test fun touchPinsVisibleLandmarkWithoutReleasingPause(){val r=rig();r.game.startExpedition(1);r.game.pause();r.advance(.1f);r.tap(r.app.pauseScreen.map);r.advance(.1f);val a=r.game.world.adventure;val s=r.app.routeMap;val x=r.game.world.player.x;val y=r.game.world.player.y;r.tap(s.px(a.nodesX[4]),s.py(a.nodesY[4]));assertEquals(4,s.selected);r.tap(s.track);assertEquals(4,a.objective);assertEquals(x,r.game.world.player.x);assertEquals(y,r.game.world.player.y);assertFalse(r.app.joystick.active);r.tap(s.clear);assertEquals(-1,a.pinned);r.tap(s.back);assertEquals(ScreenId.PAUSE,r.game.screen)}
 @Test fun arenaKeepsOriginalPauseWithoutMap(){val r=rig();r.game.startRun();r.game.pause();r.advance(.1f);assertFalse(r.app.pauseScreen.map.visible)}
 @Test fun pauseButtonsFitSmallScreen(){val r=rig();r.game.startExpedition();r.game.pause();r.advance(.1f);val s=r.app.pauseScreen;val bs=listOf(s.resume,s.map,s.quit);for(b in bs)assertTrue(b.rect.top>=0&&b.rect.bottom<=640);for(i in bs.indices)for(j in 0 until i)assertFalse(RectF.intersects(bs[i].rect,bs[j].rect));r.shot(dir,"r11-pause-360x640")}
 @Test fun mapResizeKeepsSelectionAndTouches(){val r=rig();r.game.startExpedition(3);r.game.pause();r.game.openRouteMap();r.advance(.1f);val a=r.game.world.adventure;val s=r.app.routeMap;r.app.resize(1080,2340);r.tap(s.px(a.nodesX[4]),s.py(a.nodesY[4]));assertEquals(4,s.selected);r.tap(s.track);assertEquals(4,a.pinned)}
 @Test fun localLeftMovementUnaffectedByWorldMap(){val r=rig();r.game.startExpedition();r.advance(.1f);r.tap(180f,320f);assertFalse(r.app.joystick.active);r.game.pause();r.game.openRouteMap();r.advance(.1f);r.game.back();r.game.resume();r.advance(.1f);assertTrue(r.app.joystick.homeX<r.app.ui.cx)}
}
