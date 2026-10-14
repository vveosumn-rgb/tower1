package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class Region11SoakTest {
 @Test fun authoredRoutesBothDirectionsAndThreeTeamModes(){val rows=arrayListOf("region,mode,protected,seconds,exit,dead,flags,finite")
  for(region in 0..5)for(mode in 0..2)for(safe in listOf(false,true)){val g=Game();g.fixedSeed=1100L+region*31+mode;val p=g.profile.adventure;val first=p.adopt(0,1)!!;val second=p.adopt(2,3)!!;g.chooseReserveAnimal(second.uid);g.selectAnimal(first.uid);g.profile.teamMode=mode;g.profile.home.intent=2;g.startExpedition(region);g.world.godMode=safe;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,27).also{it.activeSkills=true};var n=0
   while(n<60*120&&g.screen!=ScreenId.JOURNEY&&g.screen!=ScreenId.RESULTS){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);n++;if(n%120==0)assertTrue(SimRunner.finiteWorld(g.world))}
   assertTrue("region$region mode$mode safe$safe flags${g.world.adventure.flags} pos${g.world.player.x},${g.world.player.y}",g.world.adventure.escaped);assertTrue(g.world.adventure.nodeDone(0));assertTrue(g.world.adventure.nodeDone(4));assertTrue(g.world.adventure.nodeDone(11));rows.add("$region,$mode,$safe,${n/60},true,${g.world.player.dead},${g.world.adventure.flags},true")
  };File(System.getProperty("screenshots.dir","screenshots"),"r11-routes-matrix.csv").writeText(rows.joinToString("\n"))
 }
}
