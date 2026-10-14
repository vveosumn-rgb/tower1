package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.ui.*
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
class R08VisualTest {
 private val dir=File(System.getProperty("screenshots.dir")?:"screenshots").also{it.mkdirs()}
 @Test fun clueAndSafeReturnUseActualButtonAtThreeSizes(){for((w,h) in listOf(360 to 640,1080 to 2340,1200 to 1920)){
  val r=ScreenshotTest.Rig(w,h);r.advance(2f);val g=r.game;g.profile.home.intent=2;g.startExpedition();g.world.godMode=true;g.world.weaponCount=0;val a=g.world.adventure
  g.world.player.x=a.nodesX[4];g.world.player.y=a.nodesY[4];r.advance(.1f);r.shot(dir,"r08-clue-${w}x$h");r.tap(r.app.hud.contextBtn);assertEquals(11,a.objective)
  g.world.player.x=a.nodesX[11];g.world.player.y=a.nodesY[11];r.advance(.1f);r.tap(r.app.hud.contextBtn);assertTrue(a.nodeDone(11))
  g.world.enemyCount=0;g.world.hazardCount=0;g.world.shotCount=0;g.world.player.x=600f;g.world.player.y=600f;r.advance(.1f);r.shot(dir,"r08-return-ready-${w}x$h");assertEquals("В лагерь",r.app.hud.contextBtn.label);r.tap(r.app.hud.contextBtn);r.advance(.1f);assertEquals(ScreenId.JOURNEY,g.screen)
 }}
 @Test fun rightActionDoesNotStealLeftMovement(){val r=ScreenshotTest.Rig(1080,2340);r.advance(2f);r.game.startExpedition();r.advance(.1f);val j=r.app.joystick;val q=r.app.input
  q.push(InputQueue.DOWN,10,j.homeX,j.homeY);q.push(InputQueue.MOVE,10,j.homeX+40,j.homeY-50);r.app.pumpInput();assertTrue(j.active);val x=j.outX;val y=j.outY
  val b=r.app.hud.actionBtn;q.push(InputQueue.DOWN,11,b.rect.centerX(),b.rect.centerY());q.push(InputQueue.UP,11,b.rect.centerX(),b.rect.centerY());r.app.pumpInput();assertTrue(j.active);assertEquals(10,j.pointer);assertEquals(x,j.outX,0f);assertEquals(y,j.outY,0f);r.shot(dir,"r08-left-thumb-multitouch")
  q.push(InputQueue.UP,10,j.homeX,j.homeY);r.app.pumpInput();assertFalse(j.active)
 }
 @Test fun journalStartsWithLatestMemory(){val r=ScreenshotTest.Rig(360,640);r.advance(2f);val h=r.game.profile.home;h.discovery(0,1,0);h.discovery(0,2,0);h.discovery(0,128,0);r.game.openSanctuary();r.advance(.1f);r.tap(r.app.sanctuary.journal);assertEquals(0,r.app.sanctuary.memoryIndex);r.shot(dir,"r08-latest-memory");r.tap(r.app.sanctuary.journal);assertEquals(1,r.app.sanctuary.memoryIndex)}
}
