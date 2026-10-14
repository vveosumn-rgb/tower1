package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
/** Diagnostic, NOT a 24-victory acceptance gate. Same six protected timeouts reproduced on R15.
 * Finite state is asserted; poor solo survival/tempo remain explicit open findings. */
class Shield16SoakTest {
 @Test fun diagnoseSoloShieldRoutesWithoutClaimingAllVictories(){val rows=arrayListOf("region,rematch,protected,seconds,exit,dead,hp,damageTaken,perfectDodges,finite");val failures=arrayListOf<String>()
 for(region in 0..5)for(rematch in listOf(false,true))for(safe in listOf(false,true)){val g=Game();g.selectHero(1);g.fixedSeed=1616L+region*29+if(rematch)1 else 0;val p=g.profile.adventure;if(rematch)p.regions[region]=15;val a=p.adopt(0,1)!!;g.selectAnimal(a.uid);if(rematch)g.startContract(region,2) else g.startExpedition(region);g.world.godMode=safe;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,28).also{it.activeSkills=true};var ticks=0
 while(ticks<60*360&&g.screen!=ScreenId.JOURNEY&&g.screen!=ScreenId.RESULTS){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);ticks++;if(ticks%120==0)assertTrue(SimRunner.finiteWorld(g.world))}
 val w=g.world;assertTrue(SimRunner.finiteWorld(w));rows.add("$region,$rematch,$safe,${ticks/60},${w.adventure.escaped},${w.player.dead},${w.player.hp.toInt()},${w.run.damageTaken.toInt()},${w.run.perfectDodges},true");if(safe&&!w.adventure.escaped)failures.add("r$region rematch$rematch ${ticks/60}s boss${w.activeBoss()?.hp}")
 };File(System.getProperty("screenshots.dir","screenshots"),"r16-shield-routes.csv").writeText(rows.joinToString("\n"));println("OPEN tempo/survival findings: $failures")
 }
}
