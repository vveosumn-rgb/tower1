package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
/** Accelerated wall clock, fixed 60 Hz. Prepared teams; never a human/physical playtest. */
class Boss12SoakTest {
 @Test fun newKeeperRoutesAcrossFourHeroes(){val rows=arrayListOf("region,hero,protected,seconds,exit,dead,phases,damage,bossHp,finite,unfinishedAt180")
  val failures=ArrayList<String>()
  for(region in 3..5)for(hero in 0..3)for(safe in listOf(false,true)){val g=Game();g.selectHero(hero);g.fixedSeed=1210L+region*23+hero;val p=g.profile.adventure;val first=p.adopt(0,1)!!;val second=p.adopt(2,3)!!;g.selectAnimal(first.uid);g.chooseReserveAnimal(second.uid);g.profile.teamMode=2;g.startExpedition(region);g.world.godMode=safe;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,18).also{it.activeSkills=true};var ticks=0;var late=false
   while(ticks<60*300&&g.screen!=ScreenId.JOURNEY&&g.screen!=ScreenId.RESULTS){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);ticks++;if(ticks==60*180)late=!g.world.adventure.escaped;if(ticks%120==0)assertTrue(SimRunner.finiteWorld(g.world))}
   val w=g.world;val boss=(0 until w.enemyCount).map{w.enemies[it]}.firstOrNull{it.boss&&it.alive};rows.add("$region,$hero,$safe,${ticks/60},${w.adventure.escaped},${w.player.dead},${w.run.bossPhases},${w.run.damageDealt.toInt()},${boss?.hp?.toInt()?:0},${SimRunner.finiteWorld(w)},$late")
   if(safe&&!w.adventure.escaped)failures.add("r$region hero$hero hp${boss?.hp} state${boss?.state} p${w.player.x},${w.player.y} boss${boss?.x},${boss?.y}")
  };File(System.getProperty("screenshots.dir","screenshots"),"r12-keepers-matrix.csv").writeText(rows.joinToString("\n"));assertTrue(failures.joinToString("; "),failures.isEmpty())
 }
 @Test fun sixRematchesRetainPersistentChanges(){val rows=arrayListOf("region,seconds,exit,contractPaid,worldFlags,finite,unfinishedAt240")
  for(region in 0..5){val g=Game();g.fixedSeed=1300L+region;g.profile.adventure.regions[region]=15;val a=g.profile.adventure.adopt(0,1)!!;val b=g.profile.adventure.adopt(2,3)!!;g.selectAnimal(a.uid);g.chooseReserveAnimal(b.uid);g.profile.teamMode=2;g.startContract(region,2);g.world.godMode=true;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,38).also{it.activeSkills=true};var ticks=0;var late=false
   while(ticks<60*360&&g.screen!=ScreenId.JOURNEY){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);ticks++;if(ticks==60*240)late=!g.world.adventure.escaped}
   assertTrue("rematch$region flags${g.world.adventure.flags} p${g.world.player.x},${g.world.player.y} boss${g.world.activeBoss()?.hp} at${g.world.activeBoss()?.x},${g.world.activeBoss()?.y}",g.world.adventure.escaped);assertEquals(1,g.profile.adventure.contractPaid);assertEquals(15,g.profile.adventure.regions[region] and 15);assertTrue(SimRunner.finiteWorld(g.world));rows.add("$region,${ticks/60},true,1,${g.profile.adventure.regions[region]},true,$late")
  };File(System.getProperty("screenshots.dir","screenshots"),"r12-rematches-matrix.csv").writeText(rows.joinToString("\n"))
 }
}
