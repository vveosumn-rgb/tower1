package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.meta.*
import com.tavernrush.game.save.*
import com.tavernrush.game.content.*
import com.tavernrush.game.render.pixel.*
import org.junit.Assert.*
import org.junit.Test
class Round10Test {
 private fun game():Game=Game().also{for(i in 0..3){val a=it.profile.adventure.adopt(i,i+1)!!;a.practice=3}}
 @Test fun missingIdentityCannotEvolve(){assertFalse(game().evolveAnimal(500,1))}
 @Test fun invalidBranchCannotEvolve(){val g=game();assertFalse(g.evolveAnimal(1,-1));assertFalse(g.evolveAnimal(1,3))}
 @Test fun twoDiscoveriesAreNotEnough(){val g=game();g.profile.adventure.find(1)!!.practice=2;assertFalse(g.evolveAnimal(1,1))}
 @Test fun youngCannotEvolveEvenWithPractice(){val g=game();g.profile.adventure.find(1)!!.age=1;assertFalse(g.evolveAnimal(1,1))}
 @Test fun adultCanChooseEitherSideBranch(){val g=game();assertTrue(g.evolveAnimal(1,1));assertEquals(1,g.profile.morph.form(1));assertTrue(g.evolveAnimal(1,2));assertEquals(2,g.profile.morph.form(1))}
 @Test fun chosenFormDoesNotAlterInheritedTalent(){val g=game();val a=g.profile.adventure.find(1)!!;a.talent=0;g.evolveAnimal(1,2);assertEquals(0,a.talent)}
 @Test fun identityAndParentsStayUnchanged(){val g=game();g.profile.adventure.materials=2;val second=g.profile.adventure.adopt(0,5)!!;val child=g.breedAnimals(1,second.uid,0,1)!!;child.age=2;child.practice=3;val before=child.record();val name=child.name;g.evolveAnimal(child.uid,2);assertEquals(before,child.record());assertEquals(name,child.name);assertEquals(6,g.profile.adventure.animals.size)}
 @Test fun selectionDoesNotSpendResources(){val g=game();g.profile.adventure.materials=8;g.profile.gold=99;g.evolveAnimal(1,1);assertEquals(8,g.profile.adventure.materials);assertEquals(99L,g.profile.gold)}
 @Test fun naturalFormRestoresInheritedAbility(){val g=game();g.profile.adventure.find(1)!!.talent=2;g.evolveAnimal(1,1);g.evolveAnimal(1,0);g.startExpedition();assertEquals(2,g.world.team.lead()!!.spec);assertEquals(0,g.world.team.lead()!!.visualForm)}
 @Test fun chosenFormReplacesRatherThanStacksRuntimeBranch(){val g=game();g.profile.adventure.find(1)!!.talent=2;g.evolveAnimal(1,1);g.startExpedition();assertEquals(1,g.world.team.lead()!!.spec);assertEquals(1,g.world.team.lead()!!.visualForm);assertEquals(2,g.profile.adventure.find(1)!!.talent)}
 @Test fun reserveAlsoUsesOwnForm(){val g=game();g.chooseReserveAnimal(2);g.profile.teamMode=2;g.evolveAnimal(2,2);g.startExpedition();assertEquals(2,g.world.team.reserve()!!.visualForm);assertEquals(2,g.world.team.reserve()!!.spec)}
 @Test fun formSelectionBlockedInsideRun(){val g=game();g.startExpedition();assertFalse(g.evolveAnimal(1,1));assertEquals(0,g.profile.morph.form(1))}
 @Test fun sameIdPersistsAcrossReversibleChoices(){val g=game();repeat(15){g.evolveAnimal(1,it%3)};assertEquals(4,g.profile.adventure.animals.size);assertEquals(5,g.profile.adventure.nextId)}
 @Test fun formsAreIndependentByIndividual(){val g=game();g.evolveAnimal(1,1);g.evolveAnimal(2,2);assertEquals(1,g.profile.morph.form(1));assertEquals(2,g.profile.morph.form(2));assertEquals(0,g.profile.morph.form(3))}
 @Test fun formCodecRoundTrips(){val g=game();g.evolveAnimal(1,1);g.evolveAnimal(3,2);val c=MorphProgress.decode(g.profile.morph.encode(),g.profile.adventure);assertEquals(1,c.form(1));assertEquals(2,c.form(3))}
 @Test fun oldMissingStateIsNatural(){val g=game();assertEquals("",g.profile.morph.encode());assertEquals(0,MorphProgress.decode("",g.profile.adventure).form(1))}
 @Test(expected=IllegalArgumentException::class) fun unknownReferenceRejected(){MorphProgress.decode("800:1",game().profile.adventure)}
 @Test(expected=IllegalArgumentException::class) fun invalidBranchRejected(){MorphProgress.decode("1:3",game().profile.adventure)}
 @Test(expected=IllegalArgumentException::class) fun duplicateReferenceRejected(){MorphProgress.decode("1:1,1:2",game().profile.adventure)}
 @Test(expected=IllegalArgumentException::class) fun malformedStateRejected(){MorphProgress.decode("1:1:2",game().profile.adventure)}
 @Test(expected=IllegalArgumentException::class) fun youngSaveCannotClaimEvolution(){val g=game();g.profile.adventure.find(1)!!.age=0;MorphProgress.decode("1:1",g.profile.adventure)}
 @Test fun profileCopyIsDeep(){val g=game();g.evolveAnimal(1,1);val p=Profile();p.copyFrom(g.profile);p.morph.choose(p.adventure,1,2);assertEquals(1,g.profile.morph.form(1));assertEquals(2,p.morph.form(1))}
 @Test fun adultNaturalModelUnchanged(){for(l in CreatureLook.entries){val a=ModelLibrary.creature(l);val b=CreatureForms.model(l,2,0);assertEquals(a.id,b.id);assertEquals(a.parts.size,b.parts.size)}}
 @Test fun childhoodIsNotUniformShrink(){for(l in CreatureLook.entries){val adult=CreatureForms.model(l,2,0);val child=CreatureForms.model(l,0,0);val h=adult.parts.indexOfFirst{it.tag==Tag.HEAD};val b=adult.parts.indexOfFirst{it.tag==Tag.BODY};assertTrue(h>=0&&b>=0);assertTrue(child.parts[h].rx/adult.parts[h].rx>child.parts[b].rx/adult.parts[b].rx)}}
 @Test fun sideBranchesHaveDifferentGeometryNotJustTint(){for(l in CreatureLook.entries){val a=CreatureForms.model(l,2,0);val b=CreatureForms.model(l,2,1);val c=CreatureForms.model(l,2,2);assertTrue(b.parts.size>a.parts.size);assertTrue(c.parts.size>a.parts.size);assertFalse(b.parts.map{listOf(it.cx,it.cy,it.cz,it.rx,it.ry,it.rz)}==c.parts.map{listOf(it.cx,it.cy,it.cz,it.rx,it.ry,it.rz)})}}
 @Test fun everyAgeFormClipHasFinitePositiveGeometry(){for(l in CreatureLook.entries)for(age in 0..2)for(form in 0..2){val m=CreatureForms.model(l,age,form);assertTrue(m.scale>0f);for(p in m.parts){assertTrue(p.rx>0f&&p.ry>0f&&p.rz>0f);assertTrue(p.cx.isFinite()&&p.cz.isFinite())};for(name in arrayOf("walk","idle","act")){val c=m.clip(name)!!;for(f in 0 until c.frames){val pose=Pose();c.fn(f,f.toFloat()/c.frames,m.parts[0],pose);assertTrue(pose.dz.isFinite())}}}}
}
