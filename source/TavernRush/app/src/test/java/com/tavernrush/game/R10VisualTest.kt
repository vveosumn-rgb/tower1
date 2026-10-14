package com.tavernrush.game
import android.graphics.RectF
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.render.pixel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class R10VisualTest {
 private val dir=File(System.getProperty("screenshots.dir")?:"screenshots").also{it.mkdirs()}
 private fun rig(w:Int,h:Int)=ScreenshotTest.Rig(w,h).also{r->r.advance(2f);for(i in 0..3)r.game.profile.adventure.adopt(i,i+1)!!.practice=3}
 @Test fun formsScreenReachableAndNonOverlapping(){for((w,h)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=rig(w,h);r.game.openLineage();r.advance(.1f);r.tap(r.app.adventureScreens.buttons[10]);assertEquals(ScreenId.EVOLUTION,r.game.screen);r.advance(.1f);val s=r.app.evolution;for(b in s.buttons()){assertTrue(b.rect.left>=0&&b.rect.right<=w&&b.rect.top>=0&&b.rect.bottom<=h)};for((i,b)in s.buttons().withIndex())for(c in s.buttons().take(i))assertFalse(RectF.intersects(b.rect,c.rect));r.shot(dir,"r10-forms-${w}x$h");r.back();assertEquals(ScreenId.LINEAGE,r.game.screen)}}
 @Test fun actualTouchesPreviewThenChooseWithoutMutatingGenetics(){val r=rig(360,640);r.game.openEvolution();r.advance(.1f);val s=r.app.evolution;val a=r.game.profile.adventure.find(1)!!;val before=a.record();r.tap(s.branches[2]);assertEquals(0,r.game.profile.morph.form(1));r.shot(dir,"r10-preview-360x640");r.tap(s.choose);assertEquals(2,r.game.profile.morph.form(1));assertEquals(before,a.record());r.shot(dir,"r10-chosen-360x640")}
 @Test fun everySpeciesHasVisibleForms(){val r=rig(1080,2340);r.game.openEvolution();r.advance(.1f);for(i in 0..3){r.app.evolution.animalIndex=i;r.app.evolution.branch=1;r.app.evolution.layout();r.shot(dir,"r10-species-$i-1080x2340")}}
 @Test fun ageFormSpritesDifferAndAreCached(){for(l in CreatureLook.entries){val a=SpriteBank.creature(l,"idle",2,0);val child=SpriteBank.creature(l,"idle",0,0);val b=SpriteBank.creature(l,"idle",2,1);val c=SpriteBank.creature(l,"idle",2,2);assertSame(child,SpriteBank.creature(l,"idle",0,0));assertFalse(a.frame(0,0).bmp.sameAs(child.frame(0,0).bmp));assertFalse(b.frame(0,0).bmp.sameAs(c.frame(0,0).bmp))}}
 @Test fun emptyAndYoungStatesFitInsets(){val r=ScreenshotTest.Rig(1080,2340,0,96,0,48);r.advance(2f);r.game.openEvolution();r.advance(.1f);assertFalse(r.app.evolution.choose.enabled);assertTrue(r.app.evolution.back.rect.bottom<=2292f);r.shot(dir,"r10-empty-forms");val a=r.game.profile.adventure.adopt(0,1)!!;a.age=0;r.app.evolution.branch=1;r.app.evolution.layout();assertFalse(r.app.evolution.choose.enabled);r.shot(dir,"r10-young-forms")}
 @Test fun hybridMenusAndConfirmationFitThreeSizes(){for((w,h)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=rig(w,h);val p=r.game.profile;p.adventure.materials=8;p.companions.learn(p.adventure,1,3);r.game.openLineage();r.advance(.1f);r.tap(r.app.adventureScreens.buttons[11]);assertEquals(ScreenId.HYBRIDS,r.game.screen);r.advance(.1f);val s=r.app.hybrids;for(b in s.buttons())assertTrue(b.rect.bottom<=h&&b.rect.top>=0);r.tap(s.create);assertEquals(4,p.adventure.animals.size);r.shot(dir,"r10-hybrid-confirm-${w}x$h");r.tap(s.create);assertEquals(5,p.adventure.animals.size);r.shot(dir,"r10-hybrid-created-${w}x$h");r.tap(s.take);assertEquals(s.created,p.adventure.selected)}}
 @Test fun hybridBattleUsesDistinctOriginalModel(){val r=rig(360,640);val p=r.game.profile;p.adventure.materials=8;p.companions.learn(p.adventure,1,3);val a=r.game.createHybrid(1,3,0,1)!!;r.game.selectAnimal(a.uid);r.game.startExpedition();r.game.world.godMode=true;r.advance(.1f);r.shot(dir,"r10-hybrid-child-battle");assertEquals(4,r.game.world.team.lead()!!.species)}
 @Test fun cancelNeverCreatesOrChargesHybrid(){val r=rig(360,640);val p=r.game.profile;p.adventure.materials=8;p.companions.learn(p.adventure,1,3);r.game.openHybrids();r.advance(.1f);r.tap(r.app.hybrids.create);r.back();assertEquals(ScreenId.LINEAGE,r.game.screen);assertEquals(4,p.adventure.animals.size);assertEquals(8,p.adventure.materials)}
 @Test fun lineageOperationsAreSeparatedWithThumbSizedTargets(){val r=rig(360,640);r.game.openLineage();r.advance(.1f);val s=r.app.adventureScreens;for(b in s.buttons.filter{it.visible})assertTrue("main button too small: ${b.label}",b.rect.height()>=48f);assertTrue(s.buttons[11].visible);assertFalse(s.buttons[6].visible);r.shot(dir,"r10-lineage-large-targets");r.tap(s.buttons[3]);assertTrue(s.buttons[6].visible);assertFalse(s.buttons[11].visible);for(b in s.buttons.filter{it.visible})assertTrue(b.rect.height()>=48f);r.shot(dir,"r10-lineage-parent-page");r.tap(s.buttons[7]);assertTrue(s.buttons[11].visible)}
 @Test fun formAndHybridLayoutUpdatesAfterResize(){val r=rig(360,640);r.game.openEvolution();r.advance(.1f);r.app.resize(1080,2340);assertTrue(r.app.evolution.back.rect.bottom>2000);r.game.openHybrids();r.advance(.1f);r.app.resize(360,640);assertTrue(r.app.hybrids.back.rect.bottom<=640);r.app.hybrids.buttons().forEach{assertTrue(it.rect.right<=360)}}
}
