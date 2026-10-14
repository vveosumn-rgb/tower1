package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import java.io.File
import org.junit.Test
import org.junit.Assert.*
class Boss12DiagnosticTest {
 @Test fun traceFinalRematch(){val rows=arrayListOf("sec,flags,px,py,bx,by,hp,state,stage,clear,damage,escaped,done,screen")
 val g=Game();g.fixedSeed=1305;g.profile.adventure.regions[5]=15;val a=g.profile.adventure.adopt(0,1)!!;val b=g.profile.adventure.adopt(2,3)!!;g.selectAnimal(a.uid);g.chooseReserveAnimal(b.uid);g.profile.teamMode=2;g.startContract(5,2);g.world.godMode=true;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,38).also{it.activeSkills=true}
 repeat(60*360){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);if(it%300==0){val w=g.world;val e=w.activeBoss();val a=w.adventure;rows.add("${it/60},${a.flags},${w.player.x.toInt()},${w.player.y.toInt()},${e?.x?.toInt()},${e?.y?.toInt()},${e?.hp?.toInt()},${e?.state},${e?.stage},${e?.let{a.map.clear(w.player.x,w.player.y,it.x,it.y,5f,a.flags)}},${w.run.damageDealt.toInt()},${a.escaped},${a.contractDone},${g.screen}")}}
 File(System.getProperty("screenshots.dir","screenshots"),"r12-final-rematch-trace.csv").writeText(rows.joinToString("\n"));assertTrue(SimRunner.finiteWorld(g.world))
 }
 @Test fun traceSlowBuildsWithoutForcingOutcome(){val rows=arrayListOf("region,hero,sec,px,py,bx,by,hp,weaponDamage,range,projectiles,birdState,birdLife,birdX,birdY,phases,screen")
  for((region,hero)in listOf(4 to 3,5 to 1,5 to 3)){val g=Game();g.selectHero(hero);g.fixedSeed=1210L+region*23+hero;val p=g.profile.adventure;val a=p.adopt(0,1)!!;val b=p.adopt(2,3)!!;g.selectAnimal(a.uid);g.chooseReserveAnimal(b.uid);g.profile.teamMode=2;g.startExpedition(region);g.world.godMode=true;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,18).also{it.activeSkills=true}
   repeat(60*300){if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(g.world));if(g.screen==ScreenId.RUN)bot.steer(g.world);g.tick(1f/60f);val w=g.world
    if(it%300==0){val e=w.activeBoss();val q=(0 until w.projectileCount).map{w.projectiles[it]}.firstOrNull{it.alive};rows.add("$region,$hero,${it/60},${w.player.x.toInt()},${w.player.y.toInt()},${e?.x?.toInt()},${e?.y?.toInt()},${e?.hp?.toInt()},${w.weapons[0].damageDealt.toInt()},${w.weapons[0].stats.range},${w.projectileCount},${q?.state},${q?.life},${q?.x?.toInt()},${q?.y?.toInt()},${w.run.bossPhases},${g.screen}")}
   };assertTrue(SimRunner.finiteWorld(g.world))
  };File(System.getProperty("screenshots.dir","screenshots"),"r12-slow-builds-trace.csv").writeText(rows.joinToString("\n"))
 }
}
