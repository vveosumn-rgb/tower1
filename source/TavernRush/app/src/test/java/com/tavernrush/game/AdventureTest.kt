package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.meta.*
import com.tavernrush.game.save.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.platform.MusicSynth
import org.junit.Assert.*
import org.junit.Test

class AdventureTest {
    private fun game(region:Int=0)=Game(MemoryStore()).also{it.fixedSeed=42;it.startExpedition(region);it.world.godMode=true;it.world.weaponCount=0}
    private fun at(g:Game,n:Int){val a=g.world.adventure;g.world.player.x=a.nodesX[n];g.world.player.y=a.nodesY[n];g.world.player.vx=0f;g.world.player.vy=0f}
    private fun tick(g:Game,n:Int=1){repeat(n){if(g.screen==ScreenId.CARD_PICK)g.pickCard(0);g.tick(1f/60f)}}
    private fun roots():AdventureProgress=AdventureProgress().also{it.materials=10;it.adopt(0,1);it.adopt(0,2)}
    @Test fun expeditionStartsAtCamp(){val g=game();assertTrue(g.world.adventure.active);assertEquals(500f,g.world.player.y,0f);assertEquals(1,g.profile.adventure.expeditions)}
    @Test fun waterSavesImmediately(){val g=game();at(g,1);assertTrue(g.interactWorld());assertEquals(1,g.profile.adventure.regions[0]);assertEquals(2,g.profile.adventure.materials)}
    @Test fun openedWaterCannotBeFarmed(){val g=game();at(g,1);g.interactWorld();repeat(10){g.interactWorld()};assertEquals(2,g.profile.adventure.materials)}
    @Test fun regionsKeepIndependentFlags(){val g=game(2);at(g,1);g.interactWorld();assertEquals(1,g.profile.adventure.regions[2]);assertEquals(0,g.profile.adventure.regions[0])}
    @Test fun returnKeepsFlags(){val g=game();at(g,1);g.interactWorld();at(g,5);g.interactWorld();tick(g);assertEquals(ScreenId.JOURNEY,g.screen);g.startExpedition();assertEquals(1,g.world.adventure.flags)}
    @Test fun pauseBlocksWorldInteraction(){val g=game();at(g,1);g.pause();assertFalse(g.interactWorld());assertFalse(g.useHeroAction());assertFalse(g.switchHeroAction());assertEquals(0,g.world.adventure.flags)}
    @Test fun pauseFreezesAllAdventureTimers(){val g=game(3);tick(g,120);g.pause();val t=g.world.adventure.climateT;tick(g,120);assertEquals(t,g.world.adventure.climateT,0f)}
    @Test fun hostPauseHasNoArenaPendingReward(){val g=game();g.world.run.gold=9f;g.onHostPause();assertEquals(0,g.profile.pendingRunGold);assertTrue(g.profile.gold>=9L)}
    @Test fun environmentSharesFiniteReactions(){val g=game();val a=g.world.adventure;assertFalse(a.applyElement(Element.SHOCK,-250f,-200f));assertTrue(a.applyElement(Element.WATER,-250f,-200f));assertTrue(a.applyElement(Element.FROST,-250f,-200f));assertFalse(a.applyElement(Element.SHOCK,-250f,-200f));assertTrue(a.applyElement(Element.FIRE,-250f,-200f));assertEquals(0f,a.frozenT,0f);assertTrue(a.steamT>0f)}
    @Test fun heavyOpensOnlyMarkedWall(){val g=game();val a=g.world.adventure;assertFalse(a.applyElement(Element.HEAVY,500f,400f));assertTrue(a.applyElement(Element.HEAVY,0f,120f));tick(g);assertTrue(g.profile.adventure.regions[0] and 2!=0)}
    @Test fun boundsClampHero(){val g=game();g.world.player.x=2000f;g.world.player.y=-2000f;tick(g);assertTrue(g.world.player.x<=650);assertTrue(g.world.player.y>=-950)}
    @Test fun wallPreventsDirectCrossing(){val g=game();g.world.player.x=0f;g.world.player.y=120f;g.world.player.vy=-100f;tick(g);assertTrue(g.world.player.y>=150f)}
    @Test fun rescueNeedsSafeArea(){val g=game();at(g,2);g.world.spawnEnemy(Content.enemyIndex("rat"),260f,150f,false);assertFalse(g.interactWorld());assertFalse(g.world.adventure.nodeDone(2))}
    @Test fun remoteEnemyDoesNotPreventRescue(){val g=game();at(g,2);g.world.spawnEnemy(Content.enemyIndex("rat"),-500f,-500f,false);assertTrue(g.interactWorld());assertTrue(g.world.adventure.nodeDone(2));assertEquals(1,g.profile.adventure.animals.size)}
    @Test fun lureDoesNotTeleportOrInstantRescue(){val g=game();at(g,2);val e=g.world.spawnEnemy(Content.enemyIndex("rat"),260f,150f,false)!!;g.world.adventure.lure=true;assertTrue(g.interactWorld());assertEquals(260f,e.x,0f);assertFalse(g.world.adventure.nodeDone(2));assertTrue(g.world.adventure.baitT>0f)}
    @Test fun guardianNeedsWaterNotPet(){val g=game();at(g,3);assertFalse(g.interactWorld());at(g,1);g.interactWorld();at(g,3);assertTrue(g.interactWorld());assertTrue(g.world.adventure.bossStarted)}
    @Test fun arenaDoesNotActivateAdventure(){val g=Game();g.startRun();assertFalse(g.world.adventure.active)}
    @Test fun defenceRequiresSavedMaster(){val g=game();at(g,6);assertFalse(g.interactWorld());at(g,2);g.interactWorld();at(g,6);assertTrue(g.interactWorld());assertEquals(1,g.world.adventure.defence)}
    @Test fun sheltersHaveCooldownAndDurability(){val g=game();at(g,2);g.interactWorld();at(g,6);g.interactWorld();at(g,7);assertTrue(g.interactWorld());assertEquals(3,g.world.adventure.fortHp[0]);assertFalse(g.interactWorld());tick(g,121);assertTrue(g.interactWorld())}
    @Test fun defenceIsThreeFiniteWaves(){val g=game();at(g,2);g.interactWorld();at(g,6);g.interactWorld();g.interactWorld();repeat(1500){for(i in 0 until g.world.enemyCount){val e=g.world.enemies[i];if(e.alive&&e.huntSite)g.world.combat.hitEnemy(e,10000f,e.x,e.y,0f,-1,false)};tick(g)};assertEquals(3,g.world.adventure.defenceWave);assertEquals(3,g.world.adventure.defence);assertTrue(g.profile.adventure.regions[0] and 32!=0)}
    @Test fun pipeDefaultSolutionReachesExit(){val b=PipeBoard();b.load(0);assertTrue(b.solve());assertTrue(b.reachable and 256!=0)}
    @Test fun pipeResetIsPuzzle(){val b=PipeBoard();b.reset();assertFalse(b.solve())}
    @Test fun pipeRotationIsCyclic(){val b=PipeBoard();val p=b.packed();repeat(4){b.rotate(2)};assertEquals(p,b.packed())}
    @Test fun pipeRotationBoundsAreSafe(){val b=PipeBoard();b.rotate(-1);b.rotate(100);assertEquals(0,b.packed())}
    @Test fun pipePackedRoundtrip(){val b=PipeBoard();b.reset();b.rotate(4);val c=PipeBoard();c.load(b.packed());assertEquals(b.packed(),c.packed());assertEquals(b.solve(),c.solve())}
    @Test fun blueprintNeedsRescueAndMaterial(){val g=Game();val b=PipeBoard();assertFalse(g.saveBlueprint(b));g.profile.adventure.rescued=1;g.profile.adventure.materials=2;assertTrue(g.saveBlueprint(b));assertEquals(0,g.profile.adventure.materials);assertTrue(g.saveBlueprint(b))}
    @Test fun invalidBlueprintIsNotSaved(){val g=Game();g.profile.adventure.rescued=1;g.profile.adventure.materials=2;val b=PipeBoard();b.reset();assertFalse(g.saveBlueprint(b));assertEquals(2,g.profile.adventure.materials)}
    @Test fun recipeUnlockIsPaidOnce(){val g=Game();g.profile.adventure.materials=2;assertTrue(g.craftRecipe(1));assertEquals(0,g.profile.adventure.materials);assertTrue(g.craftRecipe(1));assertFalse(g.craftRecipe(2))}
    @Test fun craftingBlockedDuringRun(){val g=game();g.profile.adventure.materials=20;assertFalse(g.craftRecipe(1))}
    @Test fun waterRecipeAddsActualWeapon(){val g=Game();g.profile.adventure.materials=2;g.craftRecipe(1);g.startExpedition();assertTrue((0 until g.world.weaponCount).any{g.world.weapons[it].def.element==Element.WATER})}
    @Test fun breedingPreservesParentsAndChosenTrait(){val p=roots();val a=p.animals[0];val b=p.animals[1];val child=p.breed(a.uid,b.uid,1,1)!!;assertEquals(3,p.animals.size);assertSame(a,p.find(a.uid));assertSame(b,p.find(b.uid));assertEquals(b.trait,child.trait);assertEquals(b.talent,child.talent);assertEquals(0,child.age);assertEquals(8,p.materials)}
    @Test fun repeatedOperationDoesNotCharge(){val p=roots();p.breed(1,2,0,1);assertNull(p.breed(1,2,0,1));assertEquals(8,p.materials)}
    @Test fun sameParentNotAllowed(){val p=roots();assertNull(p.breed(1,1,0,1));assertEquals(10,p.materials)}
    @Test fun crossSpeciesNotCalledNaturalBreeding(){val p=roots();p.adopt(1,3);assertNull(p.breed(1,3,0,1))}
    @Test fun youngParentNotAllowed(){val p=roots();val c=p.breed(1,2,0,1)!!;assertNull(p.breed(1,c.uid,0,2))}
    @Test fun growsByDiscoveryNotClock(){val p=roots();val c=p.breed(1,2,0,1)!!;p.choose(c.uid);p.practiceDiscovery();assertEquals(1,c.age);repeat(2){p.practiceDiscovery()};assertEquals(2,c.age)}
    @Test fun adultNeverBecomesYoungAfterDiscovery(){val p=roots();p.choose(1);p.practiceDiscovery();assertEquals(2,p.find(1)!!.age)}
    @Test fun trainingRequiresDiscoveriesAndKeepsIdentity(){val p=roots();assertFalse(p.train(1,2));repeat(2){p.practiceDiscovery()};assertTrue(p.train(1,2));assertEquals(1,p.find(1)!!.uid);assertEquals(2,p.find(1)!!.talent)}
    @Test fun sourceCannotBeCollectedTwice(){val p=roots();assertNull(p.adopt(0,1));assertEquals(2,p.animals.size)}
    @Test fun waitingRescuedAnimalCanBeCollected(){val p=AdventureProgress();p.regions[0]=12;p.collectWaiting();p.collectWaiting();assertEquals(2,p.animals.size)}
    @Test fun capacityDoesNotDeleteOrCharge(){val p=roots();repeat(30){p.animals.add(Animal(p.nextId++,0,2,0,0))};assertNull(p.breed(1,2,0,1));assertEquals(10,p.materials);assertEquals(32,p.animals.size)}
    @Test fun progressCopyIsDeep(){val p=roots();val q=AdventureProgress();q.copyFrom(p);q.find(1)!!.trait=2;assertNotEquals(q.find(1)!!.trait,p.find(1)!!.trait)}
    @Test fun lineageRoundtrip(){val p=roots();p.breed(1,2,0,1);val q=AdventureProgress.decode(p.encode());assertEquals(p.encode(),q.encode())}
    @Test fun profileSavesAdventureAndUnknownFields(){val p=Profile();p.adventure.copyFrom(roots());p.extra["future.test"]="preserved";val q=SaveCodec.decode(SaveCodec.encode(p));assertEquals(p.adventure.encode(),q.adventure.encode());assertEquals("preserved",q.extra["future.test"])}
    @Test fun invalidStateRejected(){for(s in listOf("","-1",roots().encode().replace(";1,0,2", ";1,8,2"))){try{AdventureProgress.decode(s);fail(s)}catch(_:IllegalArgumentException){}}}
    @Test fun contractPaidCannotExceedCounter(){val p=roots();p.contractPaid=1;try{AdventureProgress.decode(p.encode());fail()}catch(_:IllegalArgumentException){}}
    @Test fun solvedSchemeCheckedOnDecode(){val p=roots();p.scheme=1;val b=PipeBoard();b.reset();p.blueprint=b.packed();try{AdventureProgress.decode(p.encode());fail()}catch(_:IllegalArgumentException){}}
    @Test fun scoutRewardsExactlyOnce(){val g=Game();assertTrue(g.startContract(0,1));g.world.godMode=true;val a=g.world.adventure;for(i in 0..2){at(g,a.scoutRoute[i]);tick(g)};assertTrue(a.contractDone);assertEquals(2,g.profile.adventure.materials);tick(g,20);assertEquals(2,g.profile.adventure.materials);assertEquals(a.contractToken,g.profile.adventure.contractPaid)}
    @Test fun dailyRouteIndependentOfOperationCounter(){val a=Game();val b=Game();b.profile.adventure.contractCounter=17;a.startContract(0,1,20000L);b.startContract(0,1,20000L);assertArrayEquals(a.world.adventure.scoutRoute,b.world.adventure.scoutRoute)}
    @Test fun abandonedContractHasNoReward(){val g=Game();g.startContract(0,1);g.abandonRun();assertEquals(0,g.profile.adventure.materials)}
    @Test fun contractRematchPreservesWorld(){val g=Game();g.profile.adventure.regions[0]=15;g.startContract(0,2);at(g,3);assertTrue(g.interactWorld());assertEquals(15,g.world.adventure.flags)}
    @Test fun collectedGoldIsIncrementalAndDurable(){val g=game();val initial=g.profile.gold;g.world.run.gold=7f;tick(g);assertEquals(initial+7,g.profile.gold);tick(g,10);assertEquals(initial+7,g.profile.gold);g.world.run.gold=10f;g.onHostPause();assertEquals(initial+10,g.profile.gold)}
    @Test fun iceIsBounded(){val g=game(2);assertTrue(g.world.adventure.icy(0f,-200f));assertFalse(g.world.adventure.icy(500f,-200f));assertFalse(g.world.adventure.icy(0f,500f))}
    @Test fun stormWarningPrecedesCharge(){val g=game(3);at(g,1);g.interactWorld();tick(g,360);assertTrue(g.world.adventure.dangerT>0f);assertEquals(0f,g.world.adventure.chargedT,0f);tick(g,70);assertTrue(g.world.adventure.chargedT>0f)}
    @Test fun heroSwitchDoesNotCancelCooldown(){val g=game();assertTrue(g.useHeroAction());val cd=g.world.heroAction.cooldown;assertFalse(g.switchHeroAction());assertEquals(cd,g.world.heroAction.cooldown,0f)}
    @Test fun heroActionsResetOnNewRun(){val g=game();g.useHeroAction();g.abandonRun();g.startRun();assertEquals(0f,g.world.heroAction.cooldown,0f)}
    @Test fun runeFreezesActualEnemy(){val w=World();w.reset(2,null,2);w.weaponCount=0;w.moveX=1f;val e=w.spawnEnemy(Content.enemyIndex("rat"),110f,0f,false)!!;e.spawnFade=0f;e.wetT=3f;assertTrue(w.heroAction.use());w.heroAction.update(.1f);assertTrue(e.frozenT>0f)}
    @Test fun musicIsOriginalDeterministicFiniteAndAudible(){for(i in 0..2){val a=MusicSynth.render(i);assertArrayEquals(a,MusicSynth.render(i));assertTrue(a.size>10000);assertTrue(a.any{it.toInt()!=0});assertTrue(a.maxOrNull()!!.toInt()<32767)};assertFalse(MusicSynth.render(0).contentEquals(MusicSynth.render(1)))}
    @Test fun botUsesRealMovementToOpenRoute(){val g=game();val bot=BotPilot(BotPilot.KITE,BotPilot.FIRST,7L);repeat(1400){bot.steer(g.world);if(g.world.adventure.nearNode() in listOf(0,1))g.interactWorld();tick(g)};assertTrue(g.profile.adventure.regions[0] and 1!=0)}

    @Test fun selectedIndividualIsActualMate(){val g=Game();val p=g.profile.adventure;p.copyFrom(roots());val a=p.breed(1,2,1,1)!!;g.selectAnimal(a.uid);g.startExpedition();val m=g.world.team.lead()!!;assertEquals(a.uid,m.animalUid);assertEquals(a.trait,m.trait);assertEquals(a.talent,m.spec);assertEquals(0,m.visualAge);assertTrue(m.name.endsWith(" · ${a.uid}"))}
    @Test fun directionalShieldBlocksFrontButNotBack(){val w=World();w.reset(1,null,1);w.moveX=1f;val e=w.spawnEnemy(Content.enemyIndex("rat"),80f,0f,false)!!;w.heroAction.use();assertFalse(w.heroAction.block(e.def.index,-80f,0f));assertTrue(w.heroAction.block(e.def.index,80f,0f));assertEquals(0f,w.heroAction.shield,0f)}
    @Test fun shieldCannotBlockEnvironmentalDamage(){val w=World();w.reset(1,null,1);w.heroAction.use();assertFalse(w.heroAction.block(-1));assertTrue(w.heroAction.shield>0f)}
    @Test fun successfulEarlyParryCountersEnemy(){val w=World();w.reset(1,null,1);w.moveX=1f;val e=w.spawnEnemy(Content.enemyIndex("orc"),80f,0f,false)!!;val hp=e.hp;w.heroAction.use();assertTrue(w.heroAction.block(e.def.index));assertTrue(e.hp<hp)}
    @Test fun dryIceRuneSlowsWithoutFalseFreeze(){val w=World();w.reset(1,null,2);w.moveX=1f;val e=w.spawnEnemy(Content.enemyIndex("rat"),110f,0f,false)!!;w.heroAction.use();w.heroAction.update(.1f);assertTrue(e.slowTimer>0f);assertEquals(0f,e.frozenT,0f)}
    @Test fun rangerHookDoesNotPullBoss(){val w=World();w.reset(1,null,3);val e=w.spawnBoss(Content.bosses[0])!!;e.x=120f;e.y=0f;assertFalse(w.heroAction.use());assertEquals(120f,e.x,0f)}
    @Test fun rangerTrapActuallySlows(){val w=World();w.reset(1,null,3);val e=w.spawnEnemy(Content.enemyIndex("rat"),50f,0f,false)!!;w.heroAction.switch();w.heroAction.use();w.heroAction.update(.1f);assertEquals(.4f,e.slowMul,0f);assertTrue(e.slowTimer>0f)}
    @Test fun regionalPalettesAreDifferent(){val colors=(0..5).map{com.tavernrush.game.render.pixel.Biomes.region(it).dark};assertEquals(6,colors.toSet().size)}
}
