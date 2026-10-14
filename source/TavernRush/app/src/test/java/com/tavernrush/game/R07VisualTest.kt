package com.tavernrush.game
import android.graphics.RectF
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.content.*
import com.tavernrush.game.meta.Friend
import com.tavernrush.game.render.pixel.*
import com.tavernrush.game.systems.*
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
class R07VisualTest {
    private val dir=File(System.getProperty("screenshots.dir")?:"build/screenshots").apply{mkdirs()}
    private fun rig(w:Int=360,h:Int=640)=ScreenshotTest.Rig(w,h).also{it.advance(2f)}
    private fun populate(r:ScreenshotTest.Rig){val p=r.game.profile;p.adventure.materials=12;p.adventure.rescued=1;p.adventure.regions[0]=255;for(i in 0..3)p.adventure.adopt(i,i+1);p.home.plots[0]=1;p.home.plots[1]=2;p.home.stages[0]=2;p.home.stages[1]=1;p.home.nectar=1;p.home.herbs=1}
    @Test fun sanctuaryWorksByActualTouch(){val r=rig();populate(r);r.game.openHome();r.advance(.1f);r.tap(r.app.adventureScreens.buttons[6]);assertEquals(ScreenId.SANCTUARY,r.game.screen);val s=r.app.sanctuary;r.tap(s.harvest[0]);assertEquals(2,r.game.profile.home.nectar);r.tap(s.plant[2]);assertEquals(1,r.game.profile.home.plots[2]);r.tap(s.back);assertEquals(ScreenId.HOME,r.game.screen)}
    @Test fun sanctuaryButtonsFitAndNeverOverlap(){for((w,h)in listOf(360 to 640,1080 to 2340,1200 to 1920)){val r=rig(w,h);populate(r);r.game.openSanctuary();r.advance(.2f);val s=r.app.sanctuary;val bs=s.plant.toList()+s.harvest.toList()+listOf(s.seed,s.supply,s.pet,s.talk,s.journal,s.back);for(b in bs)assertTrue(b.rect.left>=0&&b.rect.right<=w&&b.rect.top>=0&&b.rect.bottom<=h);for(i in bs.indices)for(j in 0 until i)assertFalse(RectF.intersects(bs[i].rect,bs[j].rect));r.shot(dir,"r07-sanctuary-${w}x$h");r.tap(s.talk);r.shot(dir,"r07-master-${w}x$h");r.tap(s.pet);r.shot(dir,"r07-personality-${w}x$h");r.tap(s.supply);r.shot(dir,"r07-supply-${w}x$h")}}
    @Test fun fourthSpeciesCatalogHasOwnPage(){val r=rig();r.game.profile.befriend(Friend("moss"));r.game.openTeam();r.advance(.1f);assertFalse(r.app.team.cards[3].visible);r.tap(r.app.team.moreBtn);assertTrue(r.app.team.cards[3].visible);assertFalse(r.app.team.cards[0].visible);r.tap(r.app.team.lead[3]);assertEquals("moss",r.game.profile.teamLead);r.shot(dir,"r07-fourth-catalog");r.tap(r.app.team.moreBtn);assertTrue(r.app.team.cards[0].visible)}
    @Test fun mossHasDistinctOriginalPixelModelAndAnimation(){val a=ModelBaker.bake(ModelLibrary.creature(CreatureLook.MOSS),"walk",false);val b=ModelBaker.bake(ModelLibrary.creature(CreatureLook.HORN),"walk",false);assertTrue(a.frames[0].w>15&&a.frames[0].h>15);assertFalse(a.frames[0].pixels.contentEquals(b.frames[0].pixels));assertFalse(a.frames[0].pixels.contentEquals(a.frames[2].pixels));for(clip in listOf("idle","walk","act"))assertTrue(SpriteBank.creature(CreatureLook.MOSS,clip).count>1)}
    @Test fun nurseryAndFieldFrames(){val r=rig();r.game.startExpedition();val w=r.game.world;w.godMode=true;w.weaponCount=0;val a=w.adventure;w.player.x=-250f;w.player.y=-200f;r.game.interactWorld();w.player.x=a.nodesX[10];w.player.y=a.nodesY[10];r.game.interactWorld();r.advance(1f);r.shot(dir,"r07-nursery-trust");r.advance(4.5f);r.shot(dir,"r07-nursery-joined");assertTrue(a.nodeDone(10));r.game.abandonRun();val animal=r.game.profile.adventure.animals.first{it.species==3};r.game.selectAnimal(animal.uid);r.game.startExpedition();assertEquals(3,w.team.lead()!!.species);w.team.order();r.advance(.2f);r.shot(dir,"r07-protective-canopy")}
    @Test fun cacheRevealsOnlyWhenAllowed(){val r=rig();r.game.startExpedition();val a=r.game.world.adventure;r.game.world.player.x=-320f;r.game.world.player.y=-650f;assertFalse(a.visibleNode(11));a.reveal(-320f,-650f,240f);r.advance(.1f);r.shot(dir,"r07-hidden-trace");assertTrue(a.visibleNode(11))}
    @Test fun gardenDoesNotGrowFromLookingAtHome(){val r=rig();populate(r);val h=r.game.profile.home;h.stages[0]=0;r.game.openSanctuary();r.advance(12f);r.tap(r.app.sanctuary.pet);r.tap(r.app.sanctuary.talk);assertEquals(0,h.stages[0])}
    @Test fun suppliesAreSelectedWithoutImmediateSpending(){val r=rig();populate(r);r.game.openSanctuary();r.advance(.1f);r.tap(r.app.sanctuary.supply);assertEquals(1,r.game.profile.home.supply);assertEquals(1,r.game.profile.home.nectar);r.tap(r.app.sanctuary.supply);assertEquals(2,r.game.profile.home.supply);assertEquals(1,r.game.profile.home.herbs)}
    @Test fun intentButtonChangesWorldGoal(){val r=rig();r.game.openJourney();r.advance(.1f);r.tap(r.app.adventureScreens.buttons[9]);assertEquals(1,r.game.profile.home.intent);r.tap(r.app.adventureScreens.buttons[0]);assertEquals(1,r.game.world.adventure.intent)}
    @Test fun masterIsAbsentBeforeRescueAndFramesFitInsets(){val r=ScreenshotTest.Rig(1080,2340,0,96,0,48);r.advance(2f);r.game.openSanctuary();r.advance(.1f);r.tap(r.app.sanctuary.talk);r.shot(dir,"r07-empty-home-safe-insets");assertEquals(0,r.game.profile.home.talked);assertTrue(r.app.sanctuary.back.rect.bottom<=2292)}
}
