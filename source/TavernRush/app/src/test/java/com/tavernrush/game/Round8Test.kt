package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.entities.*
import com.tavernrush.game.save.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
class Round8Test {
 private fun game(intent:Int=2,region:Int=0)=Game().also{it.fixedSeed=61;it.profile.home.intent=intent;it.startExpedition(region);it.world.godMode=true;it.world.weaponCount=0}
 private fun at(g:Game,n:Int){val w=g.world;w.player.x=w.adventure.nodesX[n];w.player.y=w.adventure.nodesY[n];w.player.vx=0f;w.player.vy=0f}
 private fun cache(g:Game){at(g,4);g.interactWorld();at(g,11);g.interactWorld()}
 private fun away(g:Game){g.world.player.x=600f;g.world.player.y=600f}
 @Test fun overlappingFinishedCaveCannotStealTraceAction(){val g=game();at(g,4);g.interactWorld();g.world.player.x=-400f;g.world.player.y=-700f;assertEquals(11,g.world.adventure.nearNode());assertTrue(g.interactWorld());assertTrue(g.world.adventure.nodeDone(11))}
 @Test fun clueChangesObjectiveInsteadOfLoopingOnFinishedCave(){val g=game();at(g,4);assertTrue(g.interactWorld());assertEquals(11,g.world.adventure.objective);assertTrue(g.world.adventure.visibleNode(11))}
 @Test fun clueRemainsUsableAfterTemporaryGlowExpires(){val g=game();at(g,4);g.interactWorld();repeat(2500){g.world.adventure.update(1f/60f)};assertEquals(0f,g.world.adventure.trailShownT,0f);assertEquals(11,g.world.adventure.objective);at(g,11);assertTrue(g.interactWorld())}
 @Test fun alreadyKnownCaveIsNotADeadEndOnNextVisit(){val g=game();at(g,4);g.interactWorld();g.abandonRun();g.startExpedition();assertEquals(11,g.world.adventure.objective);assertTrue(g.world.adventure.visibleNode(11))}
 @Test fun cacheCompletesPersonalIntentionWithoutMandatoryBoss(){val g=game();cache(g);assertTrue(g.world.adventure.intentionDone);assertEquals(5,g.world.adventure.objective);assertFalse(g.world.adventure.bossStarted)}
 @Test fun mossMeetingCompletesItsOwnIntention(){val g=game(1);at(g,1);g.interactWorld();at(g,10);g.interactWorld();repeat(260){g.tick(1f/60f)};assertEquals(5,g.world.adventure.objective);assertTrue(g.world.adventure.intentionDone)}
 @Test fun nonLivingRegionStillHasMainQuestNotImpossibleMossGoal(){val g=game(1,2);assertFalse(g.world.adventure.visibleNode(10));assertFalse(g.world.adventure.intentionDone);assertEquals(1,g.world.adventure.objective)}
 @Test fun unknownTraceStillStartsAtCave(){assertEquals(4,game().world.adventure.objective)}
 @Test fun nearbyHiddenCacheCanStillBeFoundWithoutClue(){val g=game(0);at(g,11);g.tick(1f/60f);assertTrue(g.interactWorld());assertEquals(1,g.world.adventure.objective)}
 @Test fun cacheDoesNotDisableWaterAction(){val g=game();cache(g);assertTrue(g.world.adventure.applyElement(Element.WATER,-250f,-200f));assertTrue(g.world.adventure.nodeDone(1))}
 @Test fun cacheDoesNotDisableHeavyAction(){val g=game();cache(g);assertTrue(g.world.adventure.applyElement(Element.HEAVY,0f,120f));assertTrue(g.world.adventure.nodeDone(0))}
 @Test fun cacheDoesNotDisableFreezeShockOrSteam(){val g=game();cache(g);val a=g.world.adventure;a.applyElement(Element.WATER,-250f,-200f);assertTrue(a.applyElement(Element.FROST,-250f,-200f));assertTrue(a.frozenT>0f);assertTrue(a.applyElement(Element.FIRE,-250f,-200f));assertTrue(a.steamT>0f);assertTrue(a.applyElement(Element.SHOCK,-250f,-200f));assertTrue(a.chargedT>0f)}
 @Test fun quickReturnRequiresEarnedGoal(){val g=game();away(g);assertFalse(g.world.adventure.quickReturnReady());assertFalse(g.interactWorld());assertFalse(g.world.adventure.escaped)}
 @Test fun safeReturnIsExplicitAndKeepsRewards(){val g=game();cache(g);away(g);val materials=g.profile.adventure.materials;assertTrue(g.world.adventure.quickReturnReady());assertEquals("В лагерь",g.world.adventure.actionLabel());assertTrue(g.interactWorld());g.tick(1f/60f);assertEquals(ScreenId.JOURNEY,g.screen);assertEquals(materials,g.profile.adventure.materials);assertTrue(g.profile.adventure.regions[0] and 128!=0)}
 @Test fun completedGoalDoesNotAutoEscape(){val g=game();cache(g);away(g);g.tick(1f/60f);assertEquals(ScreenId.RUN,g.screen);assertFalse(g.world.adventure.escaped)}
 @Test fun returnNeverOverridesUnfinishedNearbyAction(){val g=game();cache(g);at(g,1);assertEquals("Открыть воду",g.world.adventure.actionLabel());assertTrue(g.interactWorld());assertTrue(g.world.adventure.nodeDone(1));assertFalse(g.world.adventure.escaped)}
 @Test fun nearbyEnemyBlocksQuickReturn(){val g=game();cache(g);away(g);g.world.spawnEnemy(Content.enemyIndex("rat"),550f,600f,false);assertFalse(g.world.adventure.quickReturnReady())}
 @Test fun distantOrdinaryEnemyDoesNotDemandEmptyWorld(){val g=game();cache(g);away(g);g.world.spawnEnemy(Content.enemyIndex("rat"),-500f,-500f,false);assertTrue(g.world.adventure.quickReturnReady())}
 @Test fun livingBossBlocksReturnEvenFarAway(){val g=game();cache(g);away(g);g.world.spawnBoss(Content.bosses[0]);assertFalse(g.world.adventure.quickReturnReady())}
 @Test fun hostileWarningBlocksReturn(){val g=game();cache(g);away(g);g.world.hazardCount=1;g.world.hazards[0].alive=true;assertFalse(g.world.adventure.quickReturnReady())}
 @Test fun visualOnlyWarningDoesNotBlockReturn(){val g=game();cache(g);away(g);g.world.hazardCount=1;g.world.hazards[0].alive=true;g.world.hazards[0].visual=true;assertTrue(g.world.adventure.quickReturnReady())}
 @Test fun nearbyProjectileBlocksReturn(){val g=game();cache(g);away(g);g.world.shotCount=1;g.world.shots[0].alive=true;g.world.shots[0].x=600f;g.world.shots[0].y=600f;assertFalse(g.world.adventure.quickReturnReady())}
 @Test fun pauseCannotBeUsedToTriggerReturn(){val g=game();cache(g);away(g);g.pause();assertFalse(g.interactWorld());assertFalse(g.world.adventure.escaped)}
 @Test fun repeatingSameIntentionDoesNotFarmDiscovery(){val g=game();cache(g);val n=g.profile.home.count;val m=g.profile.adventure.materials;g.abandonRun();g.startExpedition();assertTrue(g.world.adventure.intentionDone);g.interactWorld();g.tick(1f/60f);assertEquals(n,g.profile.home.count);assertEquals(m,g.profile.adventure.materials)}
 @Test fun contractsStillOverrideCompletedPersonalIntention(){val g=Game();g.profile.home.intent=2;g.profile.adventure.regions[0]=128;g.startContract(0,1);assertFalse(g.world.adventure.intentionDone);assertEquals(g.world.adventure.scoutRoute[0],g.world.adventure.objective)}
 @Test fun completedMainQuestCanReturnButWaterAloneCannot(){val g=game(0);at(g,1);g.interactWorld();assertFalse(g.world.adventure.intentionDone);g.abandonRun();g.profile.adventure.regions[0]=13;g.startExpedition();assertTrue(g.world.adventure.intentionDone)}
 @Test fun clueAndCacheSurviveSignedSaveRoundtrip(){val g=game();cache(g);val q=SaveCodec.decode(SaveCodec.encode(g.profile));assertEquals(g.profile.adventure.regions[0],q.adventure.regions[0]);assertEquals(g.profile.home.encode(),q.home.encode())}
 @Test fun restoredWaterReachesSourceWithoutBuyingWorkshop(){val g=game(0);at(g,1);g.interactWorld();assertFalse(g.world.adventure.device);assertTrue(g.world.adventure.waterAt(0f,-520f));assertFalse(g.world.adventure.waterAt(160f,-520f));g.world.adventure.device=true;assertTrue(g.world.adventure.waterAt(160f,-520f))}
 @Test fun elementalActionWorksAtSourceNotOnlyFarUpstream(){val g=game(0);at(g,1);g.interactWorld();assertTrue(g.world.adventure.applyElement(Element.SHOCK,0f,-520f));assertTrue(g.world.adventure.chargedT>0f)}
 @Test fun chargedSourceHasSameRiskForHero(){val g=game(0);at(g,1);g.interactWorld();g.world.godMode=false;g.world.player.x=0f;g.world.player.y=-520f;g.world.adventure.applyElement(Element.SHOCK,0f,-520f);val hp=g.world.player.hp;g.world.adventure.update(1f/60f);assertTrue(g.world.player.hp<hp)}
 @Test fun sourceWaterAffectsEnemyToo(){val g=game(0);at(g,1);g.interactWorld();val e=g.world.spawnEnemy(Content.enemyIndex("orc"),0f,-520f,false)!!;g.world.adventure.update(1f/60f);assertTrue(e.wetT>0f)}
 @Test fun drySourceDoesNotPretendToConduct(){val g=game(0);assertFalse(g.world.adventure.waterAt(0f,-520f));assertFalse(g.world.adventure.applyElement(Element.SHOCK,0f,-520f))}
 @Test fun firstSourceAndVoluntaryTrialHaveDifferentStakes(){val g=game(0);at(g,1);g.interactWorld();at(g,3);g.interactWorld();val hp=g.world.activeBoss()!!.maxHp;val t=Game();t.startContract(0,2);at(t,1);t.interactWorld();at(t,3);t.interactWorld();assertTrue(t.world.activeBoss()!!.maxHp>hp);assertEquals(6,g.world.adventure.summonCap);assertEquals(10,t.world.adventure.summonCap)}
 @Test fun arenaBossHealthHasNotBeenNerfed(){val w=World();w.reset(1,null);val b=w.spawnBoss(Content.bosses[0])!!;assertEquals(b.def.hp,b.maxHp,0f)}
 @Test fun sourceSummonsRemainBoundedDuringLongFight(){val g=game(0);at(g,1);g.interactWorld();at(g,3);g.interactWorld();repeat(5400){g.world.player.x=520f;g.world.player.y=400f;g.tick(1f/60f);assertTrue((0 until g.world.enemyCount).count{g.world.enemies[it].alive&&g.world.enemies[it].summoned}<=6)}}
 @Test fun invalidElementCoordinatesRemainRejectedAfterFix(){val g=game();cache(g);assertFalse(g.world.adventure.applyElement(Element.WATER,Float.NaN,0f));assertFalse(g.world.adventure.applyElement(Element.HEAVY,0f,Float.POSITIVE_INFINITY))}
}
