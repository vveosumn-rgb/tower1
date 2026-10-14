package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.entities.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.render.*
import com.tavernrush.game.render.pixel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class Boss12VisualTest {
 private val dir=File(System.getProperty("screenshots.dir","screenshots")).also{it.mkdirs()}
 @Test fun originalModelsAnimateAndAreNotRecolours(){val ids=listOf("storm_heron","rootback","prism_crab");val hashes=HashSet<Int>();for(id in ids){val look=Content.enemies[Content.enemyIndex(id)].look;val m=ModelLibrary.enemy(look);val a=ModelBaker.bake(m,"walk",false);val b=ModelBaker.bake(m,"attack",false);assertTrue(a.frames[0].w>25&&a.frames[0].h>25);assertFalse(a.frames[0].pixels.contentEquals(a.frames[2].pixels));assertFalse(b.frames[0].pixels.contentEquals(b.frames[2].pixels));hashes.add(a.frames[0].pixels.contentHashCode());assertEquals(6,SpriteBank.enemy(look,"attack").count)};assertEquals(3,hashes.size)}
 @Test fun allNewKeepersAndWarningsRenderAtThreeSizes(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f)
  for(region in 3..5){r.game.startExpedition(region);val w=r.game.world;w.godMode=true;val a=w.adventure;a.applyElement(Element.WATER,a.nodesX[1],a.nodesY[1]);w.player.x=a.nodesX[3]+160f;w.player.y=a.nodesY[3]+170f;r.game.interactWorld()
   // Place the source actor explicitly for a render fixture; not a claimed gameplay route.
   if(!a.bossStarted){w.player.x=a.nodesX[3];w.player.y=a.nodesY[3];r.game.interactWorld()}
   val e=(0 until w.enemyCount).map{w.enemies[it]}.first{it.uid==a.bossUid};e.x=a.nodesX[3];e.y=a.nodesY[3];e.spawnFade=0f;e.invulnT=0f;w.player.x=e.x+200f;w.player.y=e.y+100f;w.camX=e.x+80f;w.camY=e.y+70f;r.advance(.02f)
   for(stage in listOf(0,1)){w.hazardCount=0;e.state=BossSystem.IDLE;e.stage=stage;e.hp=e.maxHp*(if(stage==0).9f else .5f);e.cd=0f;e.attack=0;w.boss.update(e,.016f,e.speed);r.shot(dir,"r12-keeper-$region-phase-$stage-${width}x$height")}
   w.boss.update(e,e.stateT+.01f,e.speed);w.hazardSystem.update(e.dashX+.04f);assertEquals(BossSystem.ACT,e.state);r.shot(dir,"r12-strike-$region-${width}x$height")
   if(region==4){w.hazardCount=0;e.state=BossSystem.IDLE;e.stage=0;e.hp=e.maxHp*.9f;e.cd=0f;e.attack=0;w.boss.update(e,.016f,e.speed);val h=w.hazards[0];h.state=1;h.life=h.maxLife*.55f;r.shot(dir,"r12-root-gap-${width}x$height");assertTrue(h.gapHalf>0f)}
   assertTrue(r.app.joystick.homeX<r.app.ui.cx);r.game.abandonRun()
  }
 }}
 @Test fun guideCanBeReachedByNormalPagingAndFits(){for((width,height)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=ScreenshotTest.Rig(width,height);r.advance(2f);r.game.openAtlas();r.advance(.1f);repeat(4){r.tap(r.app.adventureScreens.buttons[1])};assertEquals(4,r.app.adventureScreens.page);r.shot(dir,"r12-guide-${width}x$height");val tx=Txt();tx.setup(34*r.app.ui.u,Palette.TEXT);val text=r.app.adventureScreens.bodyText;val lines=tx.lineCount(text,r.app.ui.contentW);assertTrue("guide too long: $lines",lines<32);assertTrue(r.app.ui.top+310*r.app.ui.u+(lines-1)*55*r.app.ui.u<r.app.adventureScreens.buttons[0].rect.top-25*r.app.ui.u);r.tap(r.app.adventureScreens.buttons[1]);assertEquals(0,r.app.adventureScreens.page)}}
 @Test fun textHonoursHardNewlinesAndBlankLines(){val t=Txt().setup(34f,0);assertEquals(2,t.lineCount("Один\nДва",1000f));assertEquals(3,t.lineCount("Один\n\nТри",1000f));assertEquals(2,t.lineCount("Один\r\nДва",1000f))}
 @Test fun longParagraphStillWrapsAndCaches(){val t=Txt().setup(34f,0);val s="Сначала изучите метку, затем двигайтесь в свободное место.";val n=t.lineCount(s,160f);assertTrue(n>2);assertEquals(n,t.lineCount(s,160f))}
 @Test fun moreThanSixteenExplicitLinesAreNotSilentlyLost(){val t=Txt().setup(20f,0);assertEquals(20,t.lineCount((1..20).joinToString("\n"){"Строка $it"},1000f))}
}
