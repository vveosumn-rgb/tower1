package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.entities.EnemyShot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Shield16VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 @Test fun leftThumbAimsWhileRightTapParriesIncomingShot(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.selectHero(1);r.game.startExpedition();r.advance(.4f);val w=r.game.world;w.weaponCount=0;w.player.x=0f;w.player.y=300f;w.camX=0f;w.camY=300f
 val j=r.app.joystick;val q=r.app.input;q.push(InputQueue.DOWN,10,j.homeX,j.homeY);q.push(InputQueue.MOVE,10,j.homeX+35f,j.homeY);r.app.pumpInput();r.advance(.02f)
 val btn=r.app.hud.actionBtn;q.push(InputQueue.DOWN,11,btn.rect.centerX(),btn.rect.centerY());q.push(InputQueue.UP,11,btn.rect.centerX(),btn.rect.centerY());r.app.pumpInput();assertTrue(j.active);assertEquals(10,j.pointer);assertTrue(w.heroAction.shield>0f);assertTrue(w.heroAction.sx>.9f)
 val p=w.player;w.spawnShot(EnemyShot.ARROW,p.x+100f,p.y,-600f,0f,10f,7f,2f,0xffe2c98a.toInt(),0);r.shot(dir,"r16-parry-armed-${width}x$height");val hp=p.hp;w.hazardSystem.update(.16f);assertEquals(hp,p.hp);assertEquals(0,w.shotCount);assertEquals(0f,w.heroAction.shield);w.fx.update(.035f);r.shot(dir,"r16-parry-intercepted-${width}x$height")
 w.heroAction.update(3f);w.heroAction.use();w.spawnShot(EnemyShot.ARROW,p.x-22f,p.y,600f,0f,10f,7f,2f,0xffe2c98a.toInt(),0);w.hazardSystem.update(1f/60f);assertTrue(p.hp<hp);assertTrue(w.heroAction.shield>0f);r.shot(dir,"r16-parry-rear-exposed-${width}x$height");q.push(InputQueue.UP,10,j.homeX,j.homeY);r.app.pumpInput();assertFalse(j.active)
 }}
 @Test fun localStoneImpactIsNotAFalsePlayerHit(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.startExpedition();r.advance(.4f);val w=r.game.world;w.weaponCount=0;w.player.x=0f;w.player.y=300f;w.camX=0f;w.camY=150f;w.spawnShot(EnemyShot.ARROW,0f,250f,0f,-600f,10f,7f,2f,0xffe2c98a.toInt(),0);val hp=w.player.hp;w.hazardSystem.update(.2f);assertEquals(hp,w.player.hp);assertEquals(0,w.shotCount);w.fx.update(.035f);r.shot(dir,"r16-stone-impact-${width}x$height")}}
}
