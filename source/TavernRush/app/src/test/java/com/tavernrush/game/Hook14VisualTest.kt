package com.tavernrush.game
import com.tavernrush.game.content.Content
import com.tavernrush.game.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Hook14VisualTest {
 @Test fun localLeftIntentAndRightActionKeepIndependentPointers(){val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.selectHero(3);r.game.startExpedition(0);r.advance(.4f);val w=r.game.world;w.godMode=true;w.weaponCount=0;w.adventure.start(0,2,14);w.player.x=0f;w.player.y=500f;w.camX=80f;w.camY=500f
 val behind=w.spawnEnemy(Content.enemyIndex("rat"),-80f,500f,false)!!;val front=w.spawnEnemy(Content.enemyIndex("rat"),200f,500f,false)!!
 r.advance(.02f);val j=r.app.joystick;val q=r.app.input;q.push(InputQueue.DOWN,10,j.homeX,j.homeY);q.push(InputQueue.MOVE,10,j.homeX+45f,j.homeY);r.app.pumpInput();r.advance(.02f);r.shot(dir,"r14-directed-pull-before-${width}x$height")
 val bx=behind.x;val fx=front.x;val btn=r.app.hud.actionBtn;q.push(InputQueue.DOWN,11,btn.rect.centerX(),btn.rect.centerY());q.push(InputQueue.UP,11,btn.rect.centerX(),btn.rect.centerY());r.app.pumpInput()
 assertTrue("left pointer lost on $width",j.active);assertEquals(10,j.pointer);assertEquals(bx,behind.x);assertTrue("forward enemy not pulled on $width: ${front.x} >= $fx",front.x<fx);assertEquals(0f,w.heroAction.hookT);assertTrue("no rope on $width",w.fx.boltCount>0)
 r.shot(dir,"r14-directed-pull-after-${width}x$height");q.push(InputQueue.UP,10,j.homeX,j.homeY);r.app.pumpInput();assertFalse(j.active)
 }}
}
