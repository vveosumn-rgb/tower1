package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class R09SoakTest {
 @Test fun allDirectedEchoesFinishTraceWithoutNonfiniteState(){
  val rows=arrayListOf("receiver,donor,mode,seconds,flags,exit,finite,lessonPreserved")
  for(receiver in 0..3)for(donor in 0..3)if(receiver!=donor)for(mode in 0..2){
   val g=Game();for(i in 0..3)g.profile.adventure.adopt(i,i+1);val p=g.profile;g.selectAnimal(receiver+1);g.chooseReserveAnimal(donor+1);p.teamMode=mode;p.companions.learn(p.adventure,receiver+1,donor+1);g.teachResonance(receiver+1,donor+1);p.home.intent=2;g.fixedSeed=(receiver*31+donor*17+mode+73).toLong();g.startExpedition((receiver+donor)%6);g.world.godMode=true
   val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,43).also{it.activeSkills=true};var t=0
   while(t<60*90&&g.screen!=ScreenId.JOURNEY){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);t++}
   assertTrue(SimRunner.finiteWorld(g.world));assertTrue("route failed $receiver/$donor mode$mode",g.world.adventure.escaped);assertEquals(donor+1,p.companions.lessons[receiver+1]);assertEquals(4,p.adventure.animals.size)
   rows.add("$receiver,$donor,$mode,${t/60},${g.world.adventure.flags},true,true,true")
  }
  File(System.getProperty("screenshots.dir","screenshots"),"r09-resonance-matrix.csv").writeText(rows.joinToString("\n"));println("R09 36 directed-pair/mode routes finished; diagnostic godMode=true, taught pairs are fixtures, no human playtest")
 }
 @Test fun jointLearningIsReachableWithoutTeleporting(){
  for(other in 1..3){val g=Game();for(i in 0..3)g.profile.adventure.adopt(i,i+1);g.chooseReserveAnimal(other+1);g.profile.teamMode=2;g.fixedSeed=120L+other;g.startExpedition();g.world.godMode=true;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,75);var t=0
   while(t<60*40&&!g.profile.companions.knows(1,other+1)){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);t++}
   assertTrue("joint discovery not reachable for species $other",g.profile.companions.knows(1,other+1));g.abandonRun();assertTrue(g.teachResonance(1,other+1));assertTrue(g.saveComposition(0));assertEquals(4,g.profile.adventure.animals.size)
  }
 }
}
