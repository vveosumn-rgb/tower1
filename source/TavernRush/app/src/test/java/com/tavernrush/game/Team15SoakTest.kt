package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.entities.Mate
import com.tavernrush.game.systems.*
import java.io.File
import org.junit.Test
import org.junit.Assert.*
class Team15SoakTest {
 @Test fun waterAndLynxRoutesRemainFiniteWithoutBodyTunnelling(){val rows=arrayListOf("region,mode,protected,seconds,exit,dead,reactions,bodyInStone,finite");val failures=ArrayList<String>()
 for(region in 0..5)for(mode in listOf(0,2))for(safe in listOf(false,true)){val g=Game();g.fixedSeed=1515L+region*23;val p=g.profile.adventure;val a=p.adopt(0,1)!!;val b=p.adopt(1,3)!!;g.selectAnimal(a.uid);g.chooseReserveAnimal(b.uid);g.profile.teamMode=mode;g.startExpedition(region);g.world.godMode=safe;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,18).also{it.activeSkills=true};var ticks=0;var bad=0
 while(ticks<60*300&&g.screen!=ScreenId.JOURNEY&&g.screen!=ScreenId.RESULTS){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);ticks++;val w=g.world;val map=w.adventure.map;for(i in 0 until w.team.count){val m=w.team.mates[i]!!;if(m.active&&m.state!=Mate.DOWNED&&map.blocked(m.x,m.y,17.5f,w.adventure.flags))bad++}}
 val w=g.world;assertTrue(SimRunner.finiteWorld(w));rows.add("$region,$mode,$safe,${ticks/60},${w.adventure.escaped},${w.player.dead},${w.run.reactions.sum()},$bad,true");if(bad>0||safe&&!w.adventure.escaped)failures.add("r$region mode$mode safe$safe bad$bad")
 };File(System.getProperty("screenshots.dir","screenshots"),"r15-team-cover-matrix.csv").writeText(rows.joinToString("\n"));assertTrue(failures.toString(),failures.isEmpty())
 }
}
