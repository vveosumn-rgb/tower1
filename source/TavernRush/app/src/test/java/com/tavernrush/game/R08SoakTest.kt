package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
/** Fixed 60 Hz; accelerated wall clock, never larger physics steps. These are bots, not humans. */
class R08SoakTest {
 @Test fun expeditionMatrix(){
  val rows=ArrayList<String>();rows.add("region,intent,seed,invulnerable,seconds,flags,exit,dead,goal,maxStall,finite,bossHp,damage,px,py")
  for(region in 0..5)for(intent in 0..2)for(safe in listOf(false,true)){
   val seed=101L+region*17+intent;val g=Game();g.fixedSeed=seed;g.profile.home.intent=intent;g.startExpedition(region)
   val w=g.world;w.godMode=safe;val bot=BotPilot(BotPilot.KITE,BotPilot.GREEDY,seed).also{it.activeSkills=true}
   var signature=-1;var stall=0;var longest=0;var ticks=0;var finite=true
   while(ticks<60*180&&g.screen!=ScreenId.JOURNEY){
    if(g.screen==ScreenId.CARD_PICK)g.pickCard(bot.pickCard(w))
    if(g.screen==ScreenId.RUN)bot.steer(w)
    g.tick(1f/60f);ticks++
    val s=w.adventure.flags*100+w.adventure.objective*10+w.adventure.contractStep
    if(s==signature)stall++ else {signature=s;stall=0};longest=maxOf(longest,stall)
    if(ticks%120==0&&!SimRunner.finiteWorld(w)){finite=false;break}
   }
   assertTrue("nonfinite r=$region intent=$intent",finite)
   if(safe)assertTrue("Protected route stalled: r=$region intent=$intent flags=${w.adventure.flags}",w.adventure.escaped)
   if(intent==2)assertTrue("Trace did not finish: r=$region safe=$safe",w.adventure.nodeDone(11)&&w.adventure.escaped)
   val bossHp=(0 until w.enemyCount).filter{w.enemies[it].alive&&w.enemies[it].boss}.sumOf{w.enemies[it].hp.toDouble()}.toInt()
   rows.add("$region,$intent,$seed,$safe,${ticks/60},${w.adventure.flags},${w.adventure.escaped},${w.player.dead},${w.adventure.objective},${longest/60},$finite,$bossHp,${w.run.damageDealt.toInt()},${w.player.x.toInt()},${w.player.y.toInt()}")
  }
  val out=File(System.getProperty("screenshots.dir","screenshots"));out.mkdirs();File(out,"r08-expedition-matrix.csv").writeText(rows.joinToString("\n"));rows.forEach{println("R08-SIM $it")}
 }
}
