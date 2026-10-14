package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.entities.*
import com.tavernrush.game.meta.Friend
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Team15VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 private fun rig(width:Int,height:Int,id:String):ScreenshotTest.Rig {val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.startExpedition();r.advance(.4f);val w=r.game.world;w.weaponCount=0;w.godMode=true;w.player.x=0f;w.player.y=300f;w.camX=0f;w.camY=150f;w.team.setup(Friend(id),null,0);w.team.lead()!!.let{it.x=0f;it.y=240f};return r}
 @Test fun blockedOrderExplainsTargetAndOpeningPermitsSameButton(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=rig(width,height,"lynx");val w=r.game.world;val m=w.team.lead()!!;m.charge=1f;val e=w.spawnEnemy(Content.enemyIndex("goblin"),0f,0f,false)!!.also{it.speed=0f;it.wetT=5f};w.hash.rebuild(w.player.x,w.player.y);r.shot(dir,"r15-order-covered-${width}x$height");assertEquals("Прыжок: нужна цель",r.app.hud.teamHud.statusText(w))
 val j=r.app.joystick;val q=r.app.input;q.push(InputQueue.DOWN,10,j.homeX,j.homeY);r.app.pumpInput();val b=r.app.hud.teamHud.button;q.push(InputQueue.DOWN,11,b.centerX(),b.centerY());q.push(InputQueue.UP,11,b.centerX(),b.centerY());r.app.pumpInput();assertTrue(j.active);assertEquals(10,j.pointer);assertEquals(0f,w.team.orderCd);assertEquals(1f,m.charge)
 w.adventure.start(0,2,15);w.player.x=0f;w.player.y=300f;w.camX=0f;w.camY=150f;w.hash.rebuild(w.player.x,w.player.y);val hp=e.hp;q.push(InputQueue.DOWN,11,b.centerX(),b.centerY());q.push(InputQueue.UP,11,b.centerX(),b.centerY());r.app.pumpInput();assertEquals(e.uid,m.targetUid);w.team.update(.12f);r.shot(dir,"r15-pounce-flight-${width}x$height");w.team.update(.18f);assertTrue(e.hp<hp);assertEquals(0,m.targetUid);r.shot(dir,"r15-pounce-landed-${width}x$height");q.push(InputQueue.UP,10,j.homeX,j.homeY);r.app.pumpInput();assertFalse(j.active)
 }}
 @Test fun waterStopsAtVisibleStoneAndStatusDistinguishesRest(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=rig(width,height,"stream");val w=r.game.world;val m=w.team.lead()!!;m.y=220f;val a=w.spawnEnemy(Content.enemyIndex("goblin"),0f,195f,false)!!;val b=w.spawnEnemy(Content.enemyIndex("goblin"),0f,70f,false)!!;w.hash.rebuild(w.player.x,w.player.y);w.team.update(0f);assertTrue(a.wetT>0f);assertEquals(0f,b.wetT);r.shot(dir,"r15-water-cover-${width}x$height");m.state=Mate.RECOVER;assertEquals("Передышка",r.app.hud.teamHud.statusText(w));m.state=Mate.DOWNED;assertEquals("Помогите другу",r.app.hud.teamHud.statusText(w))}}
}
