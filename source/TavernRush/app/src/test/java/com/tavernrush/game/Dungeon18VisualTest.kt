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
class Dungeon18VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 @Test fun entranceRoomsWheelChestAndMapOnThreeSizes(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){
  val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.tap(r.app.menu.play);r.advance(.5f);val w=r.game.world;w.godMode=true;w.weaponCount=0
  fun pose(x:Float,y:Float){w.player.x=x;w.player.y=y;w.camX=x;w.camY=y;r.advance(.1f)}
  pose(-480f,290f);r.shot(dir,"r18-conduit-entry-${width}x$height");pose(-480f,205f);r.shot(dir,"r18-conduit-doorway-visible-${width}x$height")
  pose(-555f,45f);assertEquals("Повернуть шлюз",w.adventure.actionLabel());r.tap(r.app.hud.contextBtn);assertTrue(w.adventure.nodeDone(13));r.shot(dir,"r18-conduit-wheel-${width}x$height")
  pose(-480f,-210f);assertTrue(w.adventure.conduitEncounterStarted);r.advance(1.1f);r.shot(dir,"r18-conduit-guards-${width}x$height")
  for(i in 0 until w.enemyCount)w.enemies[i].alive=false // fixture of completed encounter, not a played victory
  pose(-480f,-350f);r.tap(r.app.hud.contextBtn);assertTrue(w.adventure.nodeDone(14));r.shot(dir,"r18-conduit-chest-fixture-${width}x$height")
  r.game.pause();r.game.openRouteMap();r.advance(.1f);r.shot(dir,"r18-conduit-map-${width}x$height")
 }}
 @Test fun lowPresetStillShowsDoorAndPassage(){
  val bmp=android.graphics.Bitmap.createBitmap(80,80,android.graphics.Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bmp);val art=com.tavernrush.game.render.ConduitArt()
  canvas.drawColor(0xffff00ff.toInt());art.floor(canvas,0f,0f,80f,80f);val tile=bmp.getPixel(5,5)
  art.landmark(canvas,200f,200f,12,false,true);canvas.drawColor(0xff00ff00.toInt());art.floor(canvas,0f,0f,80f,80f);assertEquals("portal alpha must not leak into next floor frame",tile,bmp.getPixel(5,5));bmp.recycle()
  val r=ScreenshotTest.Rig(360,640);r.advance(2f);r.game.startExpedition();r.advance(.5f);val w=r.game.world;w.godMode=true;w.player.x=-480f;w.player.y=285f;w.camX=-480f;w.camY=285f;r.app.worldRenderer.setPreset(GfxPreset.LOW);r.shot(dir,"r18-conduit-low-360x640");assertTrue(w.adventure.map.clear(-480f,280f,-480f,170f,20f,0))}
}
