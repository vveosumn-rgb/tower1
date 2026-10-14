package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import java.io.File
import org.junit.Test
import org.junit.Assert.*
class Hook13SoakTest {
 @Test fun rangerActiveCounterIsComparedRatherThanAssumed(){val rows=arrayListOf("region,activeSkills,protected,seconds,exit,dead,hookHits,damage,finite");val failures=ArrayList<String>()
 for(region in 3..5)for(active in listOf(false,true))for(safe in listOf(false,true)){val g=Game();g.selectHero(3);g.fixedSeed=1213L+region*23;val p=g.profile.adventure;val a=p.adopt(0,1)!!;val b=p.adopt(2,3)!!;g.selectAnimal(a.uid);g.chooseReserveAnimal(b.uid);g.profile.teamMode=2;g.startExpedition(region);g.world.godMode=safe;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,18).also{it.activeSkills=active};var ticks=0
 while(ticks<60*300&&g.screen!=ScreenId.JOURNEY&&g.screen!=ScreenId.RESULTS){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);ticks++}
 val w=g.world;assertTrue(SimRunner.finiteWorld(w));rows.add("$region,$active,$safe,${ticks/60},${w.adventure.escaped},${w.player.dead},${w.heroAction.hookHits},${w.run.damageDealt.toInt()},true");if(safe&&!w.adventure.escaped)failures.add("r$region skills$active")
 };File(System.getProperty("screenshots.dir","screenshots"),"r13-ranger-counter-matrix.csv").writeText(rows.joinToString("\n"));assertTrue(failures.toString(),failures.isEmpty())
 }
}
