package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class Route11DiagnosticTest {
 @Test fun prismGuardianRouteCompletesThroughOrdinaryCombat(){val rows=arrayListOf("seconds,flags,px,py,bossX,bossY,bossHp,lineClear,damage,screen");val g=Game();g.fixedSeed=186;g.startExpedition(5);g.world.godMode=true;val b=BotPilot(BotPilot.KITE,BotPilot.GREEDY,186).also{it.activeSkills=true};repeat(60*180){if(g.screen==ScreenId.CARD_PICK)g.pickCard(b.pickCard(g.world));if(g.screen==ScreenId.RUN)b.steer(g.world);g.tick(1f/60f);if(it%300==0){val w=g.world;val a=w.adventure;val boss=(0 until w.enemyCount).map{w.enemies[it]}.firstOrNull{it.boss&&it.alive};rows.add("${it/60},${a.flags},${w.player.x.toInt()},${w.player.y.toInt()},${boss?.x?.toInt()},${boss?.y?.toInt()},${boss?.hp?.toInt()},${boss?.let{a.map.clear(w.player.x,w.player.y,it.x,it.y,5f,a.flags)}},${w.run.damageDealt.toInt()},${g.screen}")}};File(System.getProperty("screenshots.dir","screenshots"),"r11-guardian-trace.csv").writeText(rows.joinToString("\n"));assertTrue("guardian did not complete in diagnostic budget",g.world.adventure.escaped)}
}
