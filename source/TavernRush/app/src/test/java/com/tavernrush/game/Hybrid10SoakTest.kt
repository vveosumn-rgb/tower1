package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class Hybrid10SoakTest {
 private fun travel(g:Game,seed:Int):Int{val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,seed.toLong()).also{it.activeSkills=true};var t=0;while(t<60*120&&g.screen!=ScreenId.JOURNEY){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);t++};assertTrue(g.world.adventure.escaped);assertTrue(SimRunner.finiteWorld(g.world));return t}
 @Test fun earnedDiscoveryToHybridToAdultFormIsReachable(){val g=Game();for(i in 0..3)g.profile.adventure.adopt(i,i+1);g.chooseReserveAnimal(3);g.profile.teamMode=2;g.profile.home.intent=2;g.fixedSeed=177;g.startExpedition();g.world.godMode=true;travel(g,43);assertTrue(g.profile.companions.knows(1,3));assertTrue(g.profile.adventure.materials>=4)
  val child=g.createHybrid(1,3,0,1)!!;assertEquals(0,child.age);g.selectAnimal(child.uid);g.profile.teamMode=0;g.fixedSeed=188;g.startExpedition(3);g.world.godMode=true;travel(g,43);assertEquals(2,child.age);assertTrue(g.evolveAnimal(child.uid,1));assertNotNull(g.profile.adventure.find(1));assertNotNull(g.profile.adventure.find(3))
 }
 @Test fun hybridAllModesFormsAndSixRegionsKeepTraceReachable(){val rows=arrayListOf("region,form,mode,seconds,exit,finite")
  for(region in 0..5)for(form in 0..2)for(mode in 0..2){val g=Game();for(i in 0..3)g.profile.adventure.adopt(i,i+1);g.profile.adventure.materials=4;g.profile.companions.learn(g.profile.adventure,1,3);val a=g.createHybrid(1,3,0,1)!!;a.age=2;a.practice=3;g.evolveAnimal(a.uid,form);g.selectAnimal(a.uid);g.chooseReserveAnimal(2);g.profile.teamMode=mode;g.profile.home.intent=2;g.fixedSeed=(101+region*13+mode*5+form).toLong();g.startExpedition(region);g.world.godMode=true;val t=travel(g,43);assertEquals(form,g.profile.morph.form(a.uid));rows.add("$region,$form,$mode,${t/60},true,true")}
  File(System.getProperty("screenshots.dir","screenshots"),"r10-hybrid-matrix.csv").writeText(rows.joinToString("\n"))
 }
 @Test fun arenaCombatKeepsHybridFormsAndAllModesFinite(){val rows=arrayListOf("form,mode,seconds,kills,finite")
  for(form in 0..2)for(mode in 0..2){val g=Game();for(i in 0..3)g.profile.adventure.adopt(i,i+1);g.profile.adventure.materials=4;g.profile.companions.learn(g.profile.adventure,1,3);val a=g.createHybrid(1,3,0,1)!!;a.age=2;a.practice=3;g.evolveAnimal(a.uid,form);g.selectAnimal(a.uid);g.chooseReserveAnimal(2);g.profile.teamMode=mode;g.fixedSeed=(125+form*13+mode).toLong();g.startRun();g.world.godMode=true;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,43).also{it.activeSkills=true};repeat(60*180){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f)};assertTrue(SimRunner.finiteWorld(g.world));assertEquals(4,g.world.team.lead()!!.species);assertEquals(form,g.profile.morph.form(a.uid));assertTrue(g.world.run.kills>0);rows.add("$form,$mode,180,${g.world.run.kills},true")}
  File(System.getProperty("screenshots.dir","screenshots"),"r10-hybrid-combat.csv").writeText(rows.joinToString("\n"))
 }
}
