package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.content.*
import com.tavernrush.game.meta.*
import com.tavernrush.game.entities.*
import com.tavernrush.game.save.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
class Round9Test {
 private fun game():Game=Game().also{g->for(i in 0..3)g.profile.adventure.adopt(i,i+1)}
 private fun ready(mode:Int=Creatures.MODE_DUO):Game=game().also{it.chooseReserveAnimal(2);it.profile.teamMode=mode;it.fixedSeed=31;it.startExpedition();it.world.godMode=true;it.world.weaponCount=0}
 private fun at(g:Game,n:Int){g.world.player.x=g.world.adventure.nodesX[n];g.world.player.y=g.world.adventure.nodesY[n]}
 private fun tick(g:Game,n:Int){repeat(n){if(g.screen==ScreenId.CARD_PICK)g.pickCard(0);g.tick(1f/60f)}}
 private fun echo(receiver:Int,donor:Int):World {val w=World();w.reset(31,null);w.weaponCount=0;w.team.setup(Friend(Content.creatures[receiver].id),null,0);w.team.lead()!!.resonance=donor;return w}
 @Test fun individualReserveCarriesUidAgeTraitAndTalent(){val g=game();val a=g.profile.adventure.animals[1];a.age=0;a.trait=2;a.talent=2;g.chooseReserveAnimal(a.uid);g.profile.teamMode=2;g.startExpedition();val m=g.world.team.reserve()!!;assertEquals(a.uid,m.animalUid);assertEquals(0,m.visualAge);assertEquals(2,m.trait);assertEquals(2,m.spec)}
 @Test fun sameIndividualCannotBeReserve(){val g=game();assertFalse(g.chooseReserveAnimal(1));assertEquals(0,g.profile.companions.reserve)}
 @Test fun sameSpeciesCannotOccupyBothSlots(){val g=game();val a=g.profile.adventure.adopt(0,5)!!;assertFalse(g.chooseReserveAnimal(a.uid))}
 @Test fun selectingReserveAsLeadClearsConflict(){val g=game();g.chooseReserveAnimal(2);g.selectAnimal(2);assertEquals(0,g.profile.companions.reserve)}
 @Test fun emptyReserveCanBeCleared(){val g=game();g.chooseReserveAnimal(2);assertTrue(g.chooseReserveAnimal(0));assertEquals(0,g.profile.companions.reserve)}
 @Test fun reserveChangesBlockedDuringRun(){val g=ready();assertFalse(g.chooseReserveAnimal(3));assertEquals(2,g.profile.companions.reserve)}
 @Test fun soloDoesNotSpawnHiddenSecondIndividual(){val g=ready(0);assertEquals(1,g.world.team.count);assertTrue(g.world.team.hasOrder)}
 @Test fun swapKeepsDistinctIndividualIdentity(){val g=ready(1);assertTrue(g.world.team.swap());assertFalse(g.world.team.lead()!!.active);assertTrue(g.world.team.reserve()!!.active);assertEquals(2,g.world.team.reserve()!!.animalUid)}
 @Test fun duoActsWithBothIdentities(){val g=ready(2);assertEquals(2,g.world.team.count);assertTrue(g.world.team.lead()!!.active);assertTrue(g.world.team.reserve()!!.active)}
 @Test fun jointActionUnlocksPairNotFreeIdleTime(){val g=ready();tick(g,420);assertFalse(g.profile.companions.knows(1,2));at(g,1);g.interactWorld();assertTrue(g.profile.companions.knows(1,2))}
 @Test fun newPairIsNotUnlockedByInstantClick(){val g=ready();at(g,1);g.interactWorld();assertFalse(g.profile.companions.knows(1,2))}
 @Test fun soloCannotTrainWithAnAbsentReserve(){val g=ready(0);tick(g,420);at(g,1);g.interactWorld();assertFalse(g.profile.companions.knows(1,2))}
 @Test fun swapNeedsBothToActuallyParticipate(){val g=ready(1);tick(g,420);at(g,1);g.interactWorld();assertFalse(g.profile.companions.knows(1,2));g.world.team.swap();tick(g,420);at(g,0);g.interactWorld();assertTrue(g.profile.companions.knows(1,2))}
 @Test fun reserveGainsDiscoveryPracticeWhenParticipating(){val g=ready();tick(g,420);at(g,1);g.interactWorld();assertEquals(1,g.profile.adventure.find(2)!!.practice)}
 @Test fun witnessFollowsActuallyActiveSwappedIndividual(){val g=ready(1);g.world.team.swap();at(g,1);g.interactWorld();assertEquals(2,g.profile.home.memories.last().animal)}
 @Test fun teachRequiresLearnedPairAndKeepsBothAnimals(){val g=game();val p=g.profile.adventure;val before=p.animals.map{it.record()};assertFalse(g.teachResonance(1,2));assertTrue(g.profile.companions.learn(p,1,2));assertTrue(g.teachResonance(1,2));assertEquals(before,p.animals.map{it.record()});assertEquals(4,p.animals.size)}
 @Test fun lessonIsReversibleOutsideFight(){val g=game();g.profile.companions.learn(g.profile.adventure,1,2);g.teachResonance(1,2);assertTrue(g.teachResonance(1,0));assertFalse(g.profile.companions.lessons.containsKey(1))}
 @Test fun teachingAndPresetsBlockedInsideRun(){val g=ready();assertFalse(g.teachResonance(1,0));assertFalse(g.saveComposition(0));assertFalse(g.loadComposition(0))}
 @Test fun oneReceiverHasOnlyOneDonorLesson(){val g=game();val c=g.profile.companions;val p=g.profile.adventure;c.learn(p,1,2);c.learn(p,1,3);g.teachResonance(1,2);g.teachResonance(1,3);assertEquals(1,c.lessons.size);assertEquals(3,c.lessons[1])}
 @Test fun identityLearningIsSymmetricAndDeduplicated(){val g=game();val c=g.profile.companions;assertTrue(c.learn(g.profile.adventure,1,2));assertFalse(c.learn(g.profile.adventure,2,1));assertTrue(c.knows(2,1))}
 @Test fun cannotLearnFromSelfOrMissingId(){val g=game();val c=g.profile.companions;assertFalse(c.learn(g.profile.adventure,1,1));assertFalse(c.learn(g.profile.adventure,1,300))}
 @Test fun allThreeCompositionsRestoreIdentitiesAndModes(){val g=game();for(i in 0..2){g.selectAnimal(i+1);g.chooseReserveAnimal(if(i==0)2 else 1);g.profile.teamMode=i;assertTrue(g.saveComposition(i))};for(i in 0..2){assertTrue(g.loadComposition(i));assertEquals(i+1,g.profile.adventure.selected);assertEquals(i,g.profile.teamMode)}}
 @Test fun presetRestoresTaughtChoiceNotCurrentChoice(){val g=game();val c=g.profile.companions;c.learn(g.profile.adventure,1,2);g.teachResonance(1,2);g.saveComposition(0);g.teachResonance(1,0);g.loadComposition(0);assertEquals(2,c.lessons[1])}
 @Test fun emptyOrInvalidSlotDoesNothing(){val g=game();assertFalse(g.loadComposition(0));assertFalse(g.saveComposition(-1));assertFalse(g.loadComposition(3));assertEquals(1,g.profile.adventure.selected)}
 @Test fun teamPresetNeedsRealReserveInTeamModes(){val g=game();g.profile.teamMode=2;assertFalse(g.saveComposition(0));g.chooseReserveAnimal(2);assertTrue(g.saveComposition(0))}
 @Test fun resonanceRuntimeComesFromActualDonorSpecies(){val g=game();g.profile.companions.learn(g.profile.adventure,1,3);g.teachResonance(1,3);g.startRun();assertEquals(2,g.world.team.lead()!!.resonance)}
 @Test fun waterEchoCreatesConditionWithoutExtraDamage(){val w=echo(3,0);val e=w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false)!!;val hp=e.hp;assertTrue(w.team.order());assertTrue(e.wetT>0f);assertEquals(hp,e.hp,0f);assertEquals(9f,w.team.lead()!!.resonanceCd,0f)}
 @Test fun lightningEchoOnlyAddsSingleFiniteHit(){val w=echo(0,1);val e=w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false)!!;w.hash.rebuild(w.player.x,w.player.y);val hp=e.hp;assertTrue(w.team.order());assertTrue(e.wetT>0f);assertTrue(e.hp<=hp-12f);assertTrue(e.hp>hp-20f);assertTrue(w.team.lead()!!.resonanceCd>0f)}
 @Test fun lightningEchoCannotFireAtDryTargetWhenMainDoesNotWet(){val w=echo(3,1);val e=w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false)!!;val hp=e.hp;w.team.order();assertEquals(hp,e.hp,0f);assertEquals(0f,w.team.lead()!!.resonanceCd,0f)}
 @Test fun noEndlessEchoOnIdlePress(){val w=echo(3,0);w.team.order();assertEquals(0f,w.team.lead()!!.resonanceCd,0f);assertEquals(0f,w.team.lead()!!.resonanceFlash,0f)}
 @Test fun hornEchoProtectsOneHitAndDoesNotStackWard(){val w=echo(0,2);w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false);w.team.order();assertTrue(w.team.echoShieldT>0f);assertEquals(.65f,w.team.protect(0f,0f),0f);assertEquals(1f,w.team.protect(0f,0f),0f)}
 @Test fun echoShieldCannotProtectRemoteMaster(){val w=echo(0,2);w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false);w.team.order();assertEquals(1f,w.team.protect(390f,-420f),0f);assertTrue(w.team.echoShieldT>0f)}
 @Test fun mossEchoIsSmallerAndShorterThanNativeWard(){val w=echo(0,3);w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false);w.team.order();assertEquals(100f,w.team.wardRadius,0f);assertEquals(1.2f,w.team.wardT,0f)}
 @Test fun resonanceDoesNotSurviveWorldResetAsFreeEffect(){val w=echo(0,2);w.spawnEnemy(Content.enemyIndex("orc"),65f,0f,false);w.team.order();w.reset(5,null);assertEquals(0f,w.team.echoShieldT,0f);assertEquals(0,w.team.count)}
 @Test fun signedSavePreservesEveryReference(){val g=game();val p=g.profile;val c=p.companions;c.learn(p.adventure,1,2);g.chooseReserveAnimal(2);g.teachResonance(1,2);g.saveComposition(0);val q=SaveCodec.decode(SaveCodec.encode(p));assertEquals(c.encode(),q.companions.encode());assertEquals(p.adventure.encode(),q.adventure.encode())}
 @Test fun oldR08SaveWithoutNewFieldLoadsEmptyCompanionProgress(){val g=game();val encoded=SaveCodec.encode(g.profile);val body=encoded.substringBefore("sig=").lineSequence().filterNot{it.startsWith("companions.state=")}.joinToString("\n");val q=SaveCodec.decode(body+"sig="+Signer.sign(body)+"\n");assertEquals(4,q.adventure.animals.size);assertEquals(0,q.companions.reserve);assertTrue(q.companions.known.isEmpty())}
 @Test fun copyOfCompanionProgressIsIndependent(){val g=game();val c=g.profile.companions;c.learn(g.profile.adventure,1,2);val p=Profile();p.copyFrom(g.profile);p.companions.known.clear();assertEquals(1,c.known.size)}
 @Test fun duplicateKnownPairIsRejected(){val g=game();try{CompanionProgress.decode("0;1:2,1:2;;-,-,-",g.profile.adventure);fail()}catch(_:IllegalArgumentException){}}
 @Test fun missingDonorIsRejected(){val g=game();try{CompanionProgress.decode("0;1:99;;-,-,-",g.profile.adventure);fail()}catch(_:IllegalArgumentException){}}
 @Test fun lessonWithoutJointHistoryIsRejected(){val g=game();try{CompanionProgress.decode("0;;1:2;-,-,-",g.profile.adventure);fail()}catch(_:IllegalArgumentException){}}
 @Test fun invalidModeInPresetIsRejected(){val g=game();try{CompanionProgress.decode("0;;;1:2:9:0,-,-",g.profile.adventure);fail()}catch(_:IllegalArgumentException){}}
 @Test fun learnedRelationDoesNotChangeBreedingCompatibility(){val g=game();val p=g.profile.adventure;g.profile.companions.learn(p,1,2);p.materials=10;assertNull(p.breed(1,2,0,1));assertEquals(10,p.materials)}
}
