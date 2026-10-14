package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.meta.*
import com.tavernrush.game.save.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
class Round7Test {
    private fun game(region:Int=0)=Game(MemoryStore()).also{it.fixedSeed=42;it.startExpedition(region);it.world.godMode=true;it.world.weaponCount=0}
    private fun tick(g:Game,n:Int=1){repeat(n){if(g.screen==ScreenId.CARD_PICK)g.pickCard(0);g.tick(1f/60f)}}
    private fun at(g:Game,n:Int){val a=g.world.adventure;g.world.player.x=a.nodesX[n];g.world.player.y=a.nodesY[n];g.world.player.vx=0f;g.world.player.vy=0f}
    private fun moss(spec:Int=0)=World().also{it.reset(3,null);it.weaponCount=0;it.team.setup(Friend("moss").also{f->f.spec=spec},null,Creatures.MODE_SOLO)}
    private fun oldSave(p:Profile):String {val body=SaveCodec.encode(p).substringBefore("sig=").lineSequence().filterNot{it.startsWith("home.state=")}.joinToString("\n");return body+"sig="+Signer.sign(body)+"\n"}
    @Test fun fourthSpeciesIsSupportNotElementalReskin(){val c=Content.creatures[3];assertEquals("moss",c.id);assertEquals(Element.NONE,c.element);assertEquals(CreatureLook.MOSS,c.look)}
    @Test fun wardIsAnExplicitSpatialChoice(){val w=moss();assertTrue(w.team.order());assertEquals(.6f,w.team.wardScale(0f,0f),0f);assertEquals(1f,w.team.wardScale(181f,0f),0f)}
    @Test fun wardDoesNotFollowHero(){val w=moss();w.team.order();w.player.x=400f;assertEquals(1f,w.team.wardScale(w.player.x,w.player.y),0f);assertEquals(0f,w.team.wardX,0f)}
    @Test fun wardDoesNotMakeInvulnerable(){val w=moss();w.team.order();val hp=w.player.hp;w.combat.damagePlayer(10f,null);assertTrue(w.player.hp<hp);assertTrue(w.player.hp>hp-10f)}
    @Test fun wardExpiresWithoutPermanentShield(){val w=moss();w.team.order();repeat(241){w.team.update(1f/60f)};assertEquals(0f,w.team.wardT,0f);assertEquals(1f,w.team.wardScale(0f,0f),0f)}
    @Test fun wardHasRealCooldown(){val w=moss();assertTrue(w.team.order());assertFalse(w.team.order());assertTrue(w.team.orderCd>0f)}
    @Test fun protectionDoesNotFarmIdlePractice(){val w=moss();w.team.order();assertEquals(0,w.team.lead()!!.practice);w.team.protect(0f,0f);repeat(10){w.team.protect(0f,0f)};assertEquals(1,w.team.lead()!!.practice)}
    @Test fun automaticSupportNeedsNearbyDanger(){val w=moss();w.team.update(.1f);assertEquals(0f,w.team.wardT,0f);w.spawnEnemy(Content.enemyIndex("rat"),60f,0f,false);w.spawnEnemy(Content.enemyIndex("rat"),-60f,0f,false);w.team.update(.1f);assertTrue(w.team.wardT>0f)}
    @Test fun mossDoesNotAddFreeDamageOnOrder(){val w=moss();val e=w.spawnEnemy(Content.enemyIndex("orc"),50f,0f,false)!!;val hp=e.hp;w.team.order();assertEquals(hp,e.hp,0f)}
    @Test fun wideWardActuallyChangesGeometry(){val normal=moss();normal.team.order();val wide=moss(1);wide.team.order();assertEquals(1f,normal.team.wardScale(200f,0f),0f);assertTrue(wide.team.wardScale(200f,0f)<1f);assertTrue(wide.team.wardT>normal.team.wardT)}
    @Test fun nurseryNeedsWater(){val g=game();at(g,10);assertFalse(g.interactWorld());assertFalse(g.world.adventure.nursery)}
    @Test fun nurseryNeedsSafeObservation(){val g=game();at(g,1);g.interactWorld();at(g,10);g.interactWorld();val e=g.world.spawnEnemy(Content.enemyIndex("rat"),430f,-160f,false)!!;e.speed=0f;tick(g,180);assertFalse(g.world.adventure.nodeDone(10));e.alive=false;tick(g,300);assertTrue(g.world.adventure.nodeDone(10));assertEquals(3,g.profile.adventure.animals.last().species)}
    @Test fun walkingAwayDoesNotCompleteTrust(){val g=game();at(g,1);g.interactWorld();at(g,10);g.interactWorld();at(g,5);tick(g,300);assertFalse(g.world.adventure.nodeDone(10))}
    @Test fun nectarShortensMeetingButIsNotRequired(){val g=game();at(g,1);g.interactWorld();at(g,10);g.world.adventure.nectar=true;g.interactWorld();tick(g,135);assertTrue(g.world.adventure.nodeDone(10))}
    @Test fun nurseryIsOnlyInLivingRegions(){for(i in listOf(1,2,3,5)){val g=game(i);assertFalse(g.world.adventure.visibleNode(10))}}
    @Test fun sameMossCannotBeCollectedRepeatedly(){val g=game();at(g,1);g.interactWorld();at(g,10);g.interactWorld();tick(g,260);val count=g.profile.adventure.animals.size;repeat(8){g.interactWorld()};assertEquals(count,g.profile.adventure.animals.size);g.abandonRun();g.startExpedition();assertTrue(g.world.adventure.nodeDone(10))}
    @Test fun secondHabitatGivesCompatibleMossParent(){val p=AdventureProgress();p.regions[0]=64;p.regions[4]=64;p.collectWaiting();p.materials=2;assertEquals(2,p.animals.size);assertNotNull(p.breed(p.animals[0].uid,p.animals[1].uid,0,1))}
    @Test fun fourthSpeciesLineageSaves(){val p=AdventureProgress();p.adopt(3,13);p.adopt(3,17);p.materials=2;p.breed(1,2,1,1);assertEquals(p.encode(),AdventureProgress.decode(p.encode()).encode())}
    @Test fun cacheIsHiddenBeforeInvestigation(){val g=game();assertFalse(g.world.adventure.visibleNode(11));assertFalse(g.world.adventure.reveal(0f,500f,240f))}
    @Test fun anyoneCanFindCacheByCloseObservation(){val g=game();at(g,11);tick(g);assertTrue(g.world.adventure.visibleNode(11));assertTrue(g.interactWorld());assertTrue(g.profile.adventure.regions[0] and 128!=0)}
    @Test fun revealingFindsButDoesNotLootAutomatically(){val g=game();val a=g.world.adventure;assertTrue(a.reveal(-320f,-650f,240f));assertFalse(a.nodeDone(11));at(g,11);assertTrue(g.interactWorld());assertFalse(a.reveal(-320f,-650f,240f));assertEquals(2,g.profile.adventure.materials)}
    @Test fun mossSpecializationHasLongerTraceRange(){val g=game();val a=g.world.adventure;val x=a.nodesX[11]+300f;val y=a.nodesY[11];assertFalse(a.reveal(x,y,240f));assertTrue(a.reveal(x,y,380f))}
    @Test fun choosingIntentChangesRealGoal(){val g=Game();g.changeIntent();g.startExpedition();assertEquals(1,g.world.adventure.objective);at(g,1);g.interactWorld();assertEquals(10,g.world.adventure.objective)}
    @Test fun scoutContractOverridesPersonalIntent(){val g=Game();g.changeIntent();g.startContract(0,1);assertEquals(g.world.adventure.scoutRoute[0],g.world.adventure.objective)}
    @Test fun plantingConsumesOnceAndRejectsOverwrite(){val g=Game();g.profile.adventure.materials=2;assertTrue(g.plantGarden(0,1));assertFalse(g.plantGarden(0,2));assertEquals(1,g.profile.adventure.materials)}
    @Test fun gardenRequiresTwoDiscoveriesNotClock(){val g=game();val h=g.profile.home;h.plots[0]=1;tick(g,600);assertEquals(0,h.stages[0]);at(g,1);g.interactWorld();assertEquals(1,h.stages[0]);at(g,0);g.interactWorld();assertEquals(2,h.stages[0])}
    @Test fun repeatedWorldActionDoesNotGrowGarden(){val g=game();g.profile.home.plots[0]=1;at(g,1);g.interactWorld();repeat(10){g.interactWorld()};assertEquals(1,g.profile.home.stages[0])}
    @Test fun harvestIsExplicitAndRepeatSafe(){val g=Game();val h=g.profile.home;h.plots[0]=1;h.stages[0]=2;assertTrue(g.harvestGarden(0));assertFalse(g.harvestGarden(0));assertEquals(1,h.nectar);assertEquals(1,h.plots[0]);assertEquals(0,h.stages[0])}
    @Test fun herbsHaveDifferentUseFromNectar(){val h=HomeProgress();h.plots[1]=2;h.stages[1]=2;assertTrue(h.harvest(1));assertEquals(1,h.herbs);assertEquals(0,h.nectar)}
    @Test fun supplySelectionDoesNotConsumeIt(){val g=Game();g.profile.home.nectar=1;assertTrue(g.chooseSupply(1));assertEquals(1,g.profile.home.nectar);g.startExpedition();assertEquals(0,g.profile.home.nectar);assertTrue(g.world.adventure.nectar);g.abandonRun();g.startExpedition();assertFalse(g.world.adventure.nectar)}
    @Test fun herbsOpenOptionalTrace(){val g=Game();g.profile.home.herbs=1;g.chooseSupply(2);g.startExpedition();assertTrue(g.world.adventure.trailShownT>0f);assertEquals(0,g.profile.home.herbs);assertFalse(g.world.adventure.nodeDone(11))}
    @Test fun unavailableSupplyDoesNotBecomeNegative(){val h=HomeProgress();h.supply=1;assertEquals(0,h.takeSupply());assertEquals(0,h.nectar);assertFalse(h.chooseSupply(2))}
    @Test fun plantAndHarvestBlockedDuringAdventure(){val g=game();g.profile.adventure.materials=10;assertFalse(g.plantGarden(0,1));g.profile.home.plots[0]=1;g.profile.home.stages[0]=2;assertFalse(g.harvestGarden(0))}
    @Test fun noMasterDialogueBeforeRescue(){val g=Game();g.profile.adventure.regions[0]=64;val s=g.talkMaster();assertTrue(s.contains("Помогите мастеру"));assertEquals(0,g.profile.home.talked)}
    @Test fun masterDialogueRemembersChapterWithoutCurrency(){val g=Game();g.profile.adventure.rescued=1;val gold=g.profile.gold;val first=g.talkMaster();val again=g.talkMaster();assertNotEquals(first,again);assertEquals(1,g.profile.home.talked);assertEquals(gold,g.profile.gold);g.profile.adventure.regions[0]=8;assertNotEquals(again,g.talkMaster());assertEquals(3,g.profile.home.talked)}
    @Test fun memoryKeepsActualCompanionIdentity(){val g=Game();val a=g.profile.adventure.adopt(3,13)!!;g.selectAnimal(a.uid);g.startExpedition();at(g,1);g.interactWorld();assertEquals(a.uid,g.profile.home.memories.last().animal)}
    @Test fun newlyAdoptedAnimalDoesNotWitnessEarlierRescue(){val g=game();at(g,2);g.interactWorld();assertEquals(0,g.profile.home.memories.last().animal)}
    @Test fun memoryListIsBoundedAndOrdered(){val h=HomeProgress();repeat(30){h.discovery(it%6,1,0)};assertEquals(16,h.memories.size);assertEquals(15,h.memories.first().serial);assertEquals(h.encode(),HomeProgress.decode(h.encode()).encode())}
    @Test fun homeCopyIsIndependent(){val p=HomeProgress();p.plots[0]=1;p.discovery(0,1,0);val q=HomeProgress();q.copyFrom(p);q.plots[0]=2;q.discovery(1,2,0);assertEquals(1,p.plots[0]);assertEquals(1,p.memories.size)}
    @Test fun oldR06SaveKeepsProgressAndGetsEmptyHome(){val p=Profile();p.adventure.regions[0]=15;p.adventure.adopt(2,2);p.gold=123;p.lifetimeGold=123;val q=SaveCodec.decode(oldSave(p));assertEquals(15,q.adventure.regions[0]);assertEquals(1,q.adventure.animals.size);assertEquals(123L,q.gold);assertEquals(0,q.home.count)}
    @Test fun signedHomeAndLineageRoundtrip(){val p=Profile();val a=p.adventure.adopt(3,13)!!;p.home.plots[0]=2;p.home.discovery(0,64,a.uid);p.home.nectar=3;p.home.talked=7;p.home.intent=2;val q=SaveCodec.decode(SaveCodec.encode(p));assertEquals(p.home.encode(),q.home.encode());assertEquals(p.adventure.encode(),q.adventure.encode())}
    @Test fun invalidEmptyPlotGrowthIsRejected(){val h=HomeProgress();h.stages[0]=2;try{HomeProgress.decode(h.encode());fail()}catch(_:IllegalArgumentException){}}
    @Test fun invalidHomeMemoryOrderIsRejected(){val h=HomeProgress();h.count=2;h.memories.add(HomeMemory(0,1,0,2));h.memories.add(HomeMemory(0,1,0,1));try{HomeProgress.decode(h.encode());fail()}catch(_:IllegalArgumentException){}}
    @Test fun memoryCannotReferenceMissingIndividual(){val p=Profile();p.home.discovery(0,1,200);try{SaveCodec.decode(SaveCodec.encode(p));fail()}catch(_:SaveCodec.Invalid){}}
    @Test fun invalidHomeRangesAreRejected(){val h=HomeProgress();for(i in listOf(-1,10000)){h.nectar=i;try{HomeProgress.decode(h.encode());fail()}catch(_:IllegalArgumentException){}}}

    @Test fun joinedMossImmediatelyActsIfTeamWasEmpty(){val g=game();at(g,1);g.interactWorld();at(g,10);g.interactWorld();tick(g,270);assertEquals(3,g.world.team.lead()!!.species);assertTrue(g.world.team.lead()!!.animalUid>0)}
    @Test fun noNewRandomAttackIsScheduledDuringObservation(){val g=game();at(g,1);g.interactWorld();at(g,10);g.interactWorld();tick(g,180);assertEquals(0,g.world.enemyCount);assertTrue(g.world.adventure.trustT>2f)}
    @Test fun botInvestigatesTraceWithSupportInsteadOfSpammingOrder(){val w=moss();w.adventure.start(0,1,42L);w.player.x=-320f;w.player.y=-650f;val bot=BotPilot(BotPilot.KITE,BotPilot.FIRST,9);repeat(10){bot.steer(w)};assertTrue(w.adventure.trailShownT>0f);assertTrue(w.team.orderCd>0f)}
}
