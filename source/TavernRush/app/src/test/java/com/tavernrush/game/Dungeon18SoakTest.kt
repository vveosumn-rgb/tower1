package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.RegionNavigator
import java.io.File
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test
/** Real simulation movement and weapon combat; protected rows are geometry gates, ordinary rows diagnostics. */
class Dungeon18SoakTest {
 @Test fun continuousRouteAndEncounterMatrix(){
  val rows=arrayListOf("seed,protected,seconds,gate,chest,returned,dead,kills,bodyInWall,finite");val failures=ArrayList<String>()
  for(seed in 1801L..1804L)for(safe in listOf(false,true)){
   val g=Game();g.fixedSeed=seed;g.startExpedition();val w=g.world;w.godMode=safe;val nav=RegionNavigator();val out=FloatArray(2);var phase=0;var ticks=0;var bad=0;var returned=false
   while(ticks<60*150&&g.inRun&&!returned){
    if(g.screen==ScreenId.CARD_PICK){g.pickCard(0);continue}
    val tx:Float;val ty:Float
    when(phase){0->{tx=-480f;ty=290f};1->{tx=-555f;ty=40f};2->{tx=-480f;ty=-205f};3->{tx=-480f;ty=-350f};else->{tx=-480f;ty=290f}}
    val dx=tx-w.player.x;val dy=ty-w.player.y
    if(dx*dx+dy*dy<35f*35f){when(phase){0->phase=1;1->{if(g.interactWorld()&&w.adventure.nodeDone(13))phase=2};2->phase=3;3->{if(g.interactWorld()&&w.adventure.nodeDone(14))phase=4};else->returned=true}}
    nav.next(w.adventure.map,w.adventure.flags,w.player.x,w.player.y,tx,ty,20f,out);var mx=out[0]-w.player.x;var my=out[1]-w.player.y
    // Approach a visible guardian when trying to open the chest, rather than attacking through a wall.
    if(phase==3){var closest:com.tavernrush.game.entities.Enemy?=null;var d=300f*300f
     for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-w.player.x;val y=e.y-w.player.y;val q=x*x+y*y;if(e.alive&&q<d&&w.adventure.map.clear(w.player.x,w.player.y,e.x,e.y,2f,w.adventure.flags)){closest=e;d=q}}
     closest?.let{mx=it.x-w.player.x;my=it.y-w.player.y;if(d<75f*75f){mx=0f;my=0f};if(ticks%20==0)g.useHeroAction()}
    }
    val len=sqrt(mx*mx+my*my);w.moveX=if(len>5)mx/len else 0f;w.moveY=if(len>5)my/len else 0f
    g.tick(1f/60f);ticks++;if(w.adventure.map.blocked(w.player.x,w.player.y,19.5f,w.adventure.flags))bad++
   }
   val finite=SimRunner.finiteWorld(w);rows.add("$seed,$safe,${ticks/60},${w.adventure.nodeDone(13)},${w.adventure.nodeDone(14)},$returned,${w.player.dead},${w.run.kills},$bad,$finite")
   if(!finite||bad>0||safe&&!returned)failures.add("$seed safe=$safe phase=$phase t=${ticks/60} bad=$bad")
  }
  File(System.getProperty("screenshots.dir","screenshots"),"r18-conduit-matrix.csv").writeText(rows.joinToString("\n"));assertTrue(failures.toString(),failures.isEmpty())
 }
}
