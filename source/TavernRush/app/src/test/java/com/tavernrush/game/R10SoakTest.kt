package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class R10SoakTest {
 @Test fun formsModesAndSpeciesKeepTraceCompletable(){val rows=arrayListOf("species,form,mode,seconds,exit,finite")
  for(species in 0..3)for(form in 0..2)for(mode in 0..2){val g=Game();for(i in 0..3)g.profile.adventure.adopt(i,i+1)!!.practice=3;g.selectAnimal(species+1);g.chooseReserveAnimal((species+1)%4+1);g.profile.teamMode=mode;assertTrue(g.evolveAnimal(species+1,form));g.profile.home.intent=2;g.fixedSeed=(93+species*13+form*7+mode).toLong();g.startExpedition(species%6);g.world.godMode=true;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,43).also{it.activeSkills=true};var t=0
   while(t<60*90&&g.screen!=ScreenId.JOURNEY){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);t++};assertTrue(g.world.adventure.escaped);assertTrue(SimRunner.finiteWorld(g.world));assertEquals(form,g.profile.morph.form(species+1));rows.add("$species,$form,$mode,${t/60},true,true")
  };File(System.getProperty("screenshots.dir","screenshots"),"r10-forms-matrix.csv").writeText(rows.joinToString("\n"))
 }
}
