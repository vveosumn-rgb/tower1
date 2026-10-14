package com.tavernrush.game
import com.tavernrush.game.content.*
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.render.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Hook13VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 @Test fun leftThumbAndExistingActionProduceVisibleCounter(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f);val g=r.game;g.selectHero(3);g.startExpedition(5);val w=g.world;w.godMode=true;w.weaponCount=0;val a=w.adventure;a.applyElement(Element.WATER,a.nodesX[1],a.nodesY[1]);w.player.x=a.nodesX[3];w.player.y=a.nodesY[3];g.interactWorld();val e=w.activeBoss()!!;e.state=BossSystem.RECOVER;e.stateT=1.15f;e.invulnT=0f;e.spawnFade=0f;w.player.x=400f;w.player.y=-450f;w.camX=350f;w.camY=-570f;r.advance(.02f)
 val j=r.app.joystick;val q=r.app.input;q.push(InputQueue.DOWN,10,j.homeX,j.homeY);q.push(InputQueue.MOVE,10,j.homeX-20f,j.homeY-60f);r.app.pumpInput();r.advance(.02f);r.shot(dir,"r13-hook-ready-${width}x$height");assertEquals("Окно ответа",r.app.hud.actionBtn.sub);val hp=e.hp;val btn=r.app.hud.actionBtn;q.push(InputQueue.DOWN,11,btn.rect.centerX(),btn.rect.centerY());q.push(InputQueue.UP,11,btn.rect.centerX(),btn.rect.centerY());r.app.pumpInput();assertTrue(j.active);assertEquals(10,j.pointer);assertEquals(e.uid,w.heroAction.hookTargetUid);r.advance(.08f);assertTrue(w.heroAction.hookT>0f);r.shot(dir,"r13-hook-travel-${width}x$height");q.push(InputQueue.UP,10,j.homeX,j.homeY);r.app.pumpInput();r.advance(.23f);assertTrue(e.hp<hp);assertEquals(1,w.heroAction.hookHits);r.shot(dir,"r13-hook-arrival-${width}x$height")
 }}
 @Test fun actualBattleGuideStillFits(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.openAtlas();r.advance(.05f);val tx=Txt().setup(34*r.app.ui.u,Palette.TEXT);val lines=tx.lineCount(r.app.adventureScreens.bodyText,r.app.ui.contentW);assertTrue(lines<32);assertTrue(r.app.ui.top+310*r.app.ui.u+(lines-1)*55*r.app.ui.u<r.app.adventureScreens.buttons[0].rect.top-25*r.app.ui.u)}}
}
