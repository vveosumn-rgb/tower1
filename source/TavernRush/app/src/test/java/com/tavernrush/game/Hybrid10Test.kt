package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.meta.*
import com.tavernrush.game.save.*
import com.tavernrush.game.content.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
class Hybrid10Test {
 private fun game():Game=Game().also{g->for(i in 0..3)g.profile.adventure.adopt(i,i+1);g.profile.adventure.materials=8;g.profile.companions.learn(g.profile.adventure,1,3)}
 private fun world():World=World().also{it.reset(31,null);it.weaponCount=0;it.team.setup(Friend("confluence"),null,0)}
 @Test fun explicitLineCreatesFifthOriginalSpecies(){val g=game();val a=g.createHybrid(1,3,0,1)!!;assertEquals(4,a.species);assertEquals(CreatureLook.CONFLUENCE,Content.creatures[a.species].look);assertEquals(0,a.age)}
 @Test fun parentsRemainByteForByte(){val g=game();val p=g.profile.adventure;val x=p.find(1)!!.record();val y=p.find(3)!!.record();g.createHybrid(1,3,0,1);assertEquals(x,p.find(1)!!.record());assertEquals(y,p.find(3)!!.record())}
 @Test fun costAndNonceAreAtomic(){val g=game();val p=g.profile.adventure;g.createHybrid(1,3,0,1);assertEquals(4,p.materials);assertEquals(1,p.operation);assertNull(g.createHybrid(1,3,0,1));assertEquals(4,p.materials);assertEquals(5,p.animals.size)}
 @Test fun lockedPairCannotCreate(){val g=game();g.profile.companions.known.clear();assertNull(g.createHybrid(1,3,0,1));assertEquals(8,g.profile.adventure.materials)}
 @Test fun wrongPairCannotCreate(){val g=game();g.profile.companions.learn(g.profile.adventure,1,2);assertNull(g.createHybrid(1,2,0,1));assertEquals(8,g.profile.adventure.materials)}
 @Test fun youngParentCannotCreate(){val g=game();g.profile.adventure.find(3)!!.age=1;assertNull(g.createHybrid(1,3,0,1))}
 @Test fun reversedParentsStillUseChosenIdentity(){val g=game();val a=g.createHybrid(3,1,0,1)!!;assertEquals(g.profile.adventure.find(3)!!.trait,a.trait);assertEquals(3,a.parentA);assertEquals(1,a.parentB)}
 @Test fun missingMaterialsDoNotConsumeAnything(){val g=game();val p=g.profile.adventure;p.materials=3;val before=p.encode();assertNull(g.createHybrid(1,3,0,1));assertEquals(before,p.encode())}
 @Test fun invalidRequestDoesNotAllocateId(){val g=game();val p=g.profile.adventure;val next=p.nextId;assertNull(g.createHybrid(1,3,0,5));assertEquals(next,p.nextId)}
 @Test fun invalidChoiceRejected(){assertNull(game().createHybrid(1,3,2,1))}
 @Test fun creationBlockedInsideRun(){val g=game();g.startExpedition();assertNull(g.createHybrid(1,3,0,1))}
 @Test fun hybridCannotBeWildlyAdopted(){assertNull(game().profile.adventure.adopt(4,7))}
 @Test fun wildEncounterNeverChoosesHybrid(){val w=world();for(i in 0..20){w.encounter.arm(1 shl 4,i.toLong());assertEquals(-1,w.encounter.species)}}
 @Test fun naturalCrossBreedRemainsForbidden(){val g=game();assertNull(g.breedAnimals(1,3,0,1))}
 @Test fun ancestryRoundTripsNewLine(){val g=game();val a=g.createHybrid(1,3,1,1)!!;val q=AdventureProgress.decode(g.profile.adventure.encode());assertEquals(a.record(),q.find(a.uid)!!.record())}
 @Test fun signedSavePreservesMorphAndHybrid(){val g=game();val a=g.createHybrid(1,3,0,1)!!;a.age=2;a.practice=3;g.evolveAnimal(a.uid,2);val q=SaveCodec.decode(SaveCodec.encode(g.profile));assertEquals(2,q.morph.form(a.uid));assertEquals(a.record(),q.adventure.find(a.uid)!!.record())}
 @Test fun oldProfileWithoutMorphLoadsNatural(){val g=game();val e=SaveCodec.encode(g.profile);val body=e.substringBefore("sig=").lineSequence().filterNot{it.startsWith("morph.state=")}.joinToString("\n");val q=SaveCodec.decode(body+"sig="+Signer.sign(body)+"\n");assertEquals("",q.morph.encode());assertEquals(4,q.adventure.animals.size)}
 @Test(expected=IllegalArgumentException::class) fun fakeUnparentedHybridSaveRejected(){val g=game();g.profile.adventure.animals.add(Animal(5,4,0,0,0));g.profile.adventure.nextId=6;AdventureProgress.decode(g.profile.adventure.encode())}
 @Test fun growingHybridIsUsableImmediately(){val g=game();val a=g.createHybrid(1,3,0,1)!!;g.selectAnimal(a.uid);g.startExpedition();assertEquals(4,g.world.team.lead()!!.species);assertEquals(0,g.world.team.lead()!!.visualAge)}
 @Test fun pressureCannotBePressedWithoutCharge(){val w=world();assertFalse(w.team.orderReady());assertFalse(w.team.order());assertEquals(0,w.team.lead()!!.guard)}
 @Test fun chargedOrderCreatesOneLocalProtection(){val w=world();val m=w.team.lead()!!;m.charge=.6f;assertTrue(w.team.orderReady());assertTrue(w.team.order());assertEquals(0f,m.charge,0f);assertEquals(1,m.guard);assertEquals(.65f,w.team.protect(m.x,m.y),0f);assertEquals(1f,w.team.protect(m.x,m.y),0f)}
 @Test fun pressureProtectionDoesNotReachAcrossMap(){val w=world();val m=w.team.lead()!!;m.charge=1f;w.team.order();assertEquals(1f,w.team.protect(700f,700f),0f);assertEquals(1,m.guard)}
 @Test fun pressureWaveWetsAndMovesInsteadOfLargeDamage(){val w=world();val m=w.team.lead()!!;val e=w.spawnEnemy(Content.enemyIndex("orc"),m.x+45f,m.y,false)!!;w.hash.rebuild(0f,0f);val hp=e.hp;m.charge=1f;w.team.order();assertTrue(e.wetT>0f);assertTrue(e.hp>=hp-7f);assertTrue(e.kx!=0f||e.ky!=0f);assertTrue(m.cd>0f)}
 @Test fun hybridCanTeachCompatibleFiniteEcho(){val g=game();val child=g.createHybrid(1,3,0,1)!!;assertTrue(g.profile.companions.learn(g.profile.adventure,2,child.uid));assertTrue(g.teachResonance(2,child.uid));g.selectAnimal(2);g.startRun();assertEquals(4,g.world.team.lead()!!.resonance)}
 @Test fun fullCollectionBlocksBeforeCharging(){val g=game();val p=g.profile.adventure;for(i in 5..32)p.animals.add(Animal(i,0,2,0,0));p.nextId=33;val before=p.encode();assertNull(g.createHybrid(1,3,0,1));assertEquals(before,p.encode())}
 @Test fun generationLimitBlocksBeforeCharging(){val g=game();val p=g.profile.adventure;val a=p.find(1)!!;val b=p.find(3)!!;p.animals.clear();p.animals.add(a.copy(generation=8));p.animals.add(b);assertNull(g.createHybrid(1,3,0,1));assertEquals(8,p.materials)}
 @Test fun pressureProtectionExpiresInsteadOfBeingPermanent(){val w=world();val m=w.team.lead()!!;m.charge=1f;w.team.order();repeat(190){w.team.update(1f/60f)};assertEquals(0,m.guard);assertEquals(1f,w.team.protect(m.x,m.y),0f)}
 @Test fun strongerMossWardDoesNotWasteHybridGuard(){val w=world();w.team.setup(Friend("moss"),Friend("confluence"),2);w.spawnEnemy(Content.enemyIndex("orc"),50f,0f,false);w.spawnEnemy(Content.enemyIndex("orc"),-50f,0f,false);w.hash.rebuild(0f,0f);w.team.update(.05f);assertTrue(w.team.wardT>0f);val m=w.team.reserve()!!;m.x=0f;m.y=0f;m.guard=1;assertEquals(.6f,w.team.protect(0f,0f),0f);assertEquals(1,m.guard)}
}
