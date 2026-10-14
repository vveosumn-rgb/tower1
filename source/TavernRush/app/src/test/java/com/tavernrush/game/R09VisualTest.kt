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
class R09VisualTest {
 private val dir=File(System.getProperty("screenshots.dir")?:"screenshots").also{it.mkdirs()}
 private fun rig(w:Int,h:Int)=ScreenshotTest.Rig(w,h).also{r->r.advance(2f);for(i in 0..3)r.game.profile.adventure.adopt(i,i+1)}
 @Test fun compositionsAccessibleByNormalMenusAndFitThreeSizes(){for((w,h)in listOf(360 to 640,1080 to 2340,1200 to 1920)){
  val r=rig(w,h);r.game.openLineage();r.advance(.1f);r.tap(r.app.adventureScreens.buttons[9]);assertEquals(ScreenId.COMPOSITIONS,r.game.screen);r.advance(.1f);val s=r.app.compositions;val bs=s.buttons();for(b in bs)assertTrue(b.rect.left>=0&&b.rect.right<=w&&b.rect.top>=0&&b.rect.bottom<=h);for(i in bs.indices)for(j in 0 until i)assertFalse(RectF.intersects(bs[i].rect,bs[j].rect));r.shot(dir,"r09-composition-${w}x$h");r.tap(s.back);assertEquals(ScreenId.LINEAGE,r.game.screen)
 }}
 @Test fun teachingAndPresetRestoreWorkByActualTouch(){for((w,h)in listOf(360 to 640,1080 to 2340,1200 to 1920)){
  val r=rig(w,h);val g=r.game;g.profile.companions.learn(g.profile.adventure,1,2);g.openCompositions();r.advance(.1f);val s=r.app.compositions;r.tap(s.teach);assertEquals(2,g.profile.companions.lessons[1]);r.tap(s.save);g.teachResonance(1,0);r.tap(s.load);assertEquals(2,g.profile.companions.lessons[1]);r.shot(dir,"r09-taught-${w}x$h")
 }}
 @Test fun individualDuoAndEchoAreRendered(){for((w,h)in listOf(360 to 640,1080 to 2340)){
  val r=rig(w,h);val g=r.game;val p=g.profile;p.companions.learn(p.adventure,1,2);g.teachResonance(1,2);g.chooseReserveAnimal(2);p.teamMode=2;g.startExpedition();g.world.godMode=true;r.advance(.1f);assertEquals(2,g.world.team.reserve()!!.animalUid);r.shot(dir,"r09-duo-team-${w}x$h");g.world.team.lead()!!.resonanceFlash=.9f;g.world.team.lead()!!.resonanceCd=9f;r.shot(dir,"r09-resonance-flash-${w}x$h")
 }}
 @Test fun emptyCompositionsAndBackFitSafeInsets(){val r=ScreenshotTest.Rig(1080,2340,0,96,0,48);r.advance(2f);r.game.openCompositions();r.advance(.1f);val s=r.app.compositions;assertFalse(s.lead.enabled);assertFalse(s.save.enabled);assertTrue(s.back.rect.bottom<=2292f);r.shot(dir,"r09-empty-compositions");r.back();assertEquals(ScreenId.LINEAGE,r.game.screen)}
}
