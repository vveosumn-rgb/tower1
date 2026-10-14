package com.tavernrush.game
import com.tavernrush.game.content.*
import com.tavernrush.game.entities.*
import com.tavernrush.game.meta.Friend
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
class Team15Test {
 private fun rig(id:String,flags:Int=0):Pair<World,Mate>{val w=World();w.reset(15,null);w.weaponCount=0;w.godMode=true;w.adventure.start(0,flags,15);w.player.x=0f;w.player.y=300f;w.team.setup(Friend(id),null,0);val m=w.team.lead()!!;m.x=0f;m.y=300f;return w to m}
 private fun enemy(w:World,x:Float,y:Float)=w.spawnEnemy(Content.enemyIndex("goblin"),x,y,false)!!.also{it.speed=0f}
 private fun ready(w:World)=w.hash.rebuild(w.player.x,w.player.y)
 @Test fun closedGateDoesNotSpendLynxOrderOrCharge(){val(w,m)=rig("lynx");enemy(w,0f,0f).wetT=5f;m.charge=1f;ready(w);assertFalse(w.team.order());assertEquals(1f,m.charge);assertEquals(Mate.FOLLOW,m.state);assertEquals(0f,w.team.orderCd)}
 @Test fun streamDoesNotWetTargetThroughGate(){val(w,m)=rig("stream");val e=enemy(w,0f,80f);ready(w);w.team.update(0f);assertEquals(0f,e.wetT);assertTrue(m.cd<=.3f)}
 @Test fun conductionDoesNotDrainOrDamageWetNeighbourAcrossStone(){val(w,m)=rig("stream");val a=enemy(w,0f,170f);val b=enemy(w,0f,70f);a.wetT=5f;b.wetT=5f;val hp=b.hp;ready(w);assertEquals(Reactions.CONDUCT,w.reactions.onHit(a,Element.SHOCK,10f,-1));assertEquals(hp,b.hp);assertEquals(5f,b.wetT);assertEquals(0f,a.wetT)}

 @Test fun openGateAllowsOneCommittedHit(){val(w,m)=rig("lynx",2);val e=enemy(w,0f,0f);val hp=e.hp;m.charge=1f;ready(w);assertTrue(w.team.order());w.team.update(.29f);assertTrue(e.hp<hp);assertEquals(0,m.targetUid);assertEquals(0f,m.charge);val after=e.hp;repeat(5){w.team.update(.1f)};assertEquals(after,e.hp)}
 @Test fun movingTargetEvadesWithoutDraggingLynxAcrossMap(){val(w,m)=rig("lynx",2);val e=enemy(w,0f,0f);val hp=e.hp;m.charge=1f;ready(w);assertTrue(w.team.order());e.x=600f;w.team.update(.29f);assertEquals(0f,m.x,.01f);assertEquals(0f,m.y,.01f);assertEquals(hp,e.hp);assertEquals(Mate.RECOVER,m.state);assertEquals(0,m.targetUid)}
 @Test fun deadTargetCannotTransferStrikeToReplacement(){val(w,m)=rig("lynx",2);val e=enemy(w,0f,0f);m.charge=1f;ready(w);w.team.order();e.alive=false;val b=enemy(w,0f,0f);val hp=b.hp;w.team.update(.29f);assertEquals(hp,b.hp);assertEquals(0,m.targetUid)}
 @Test fun newInvulnerabilityCancelsLandingStrike(){val(w,m)=rig("lynx",2);val e=enemy(w,0f,0f);val hp=e.hp;m.charge=1f;ready(w);w.team.order();e.invulnT=1f;w.team.update(.29f);assertEquals(hp,e.hp);assertEquals(0,w.run.reactions[Reactions.CONDUCT])}
 @Test fun closingGateCancelsPounceBeforeWall(){val(w,m)=rig("lynx",2);val e=enemy(w,0f,0f);val hp=e.hp;m.charge=1f;ready(w);assertTrue(w.team.order());w.adventure.start(0,0,15);w.team.update(.29f);assertTrue(m.y>=163f);assertEquals(hp,e.hp);assertEquals(0,m.targetUid);assertEquals(Mate.RECOVER,m.state);assertEquals(0f,m.charge)}
 @Test fun thinLineIsNotEnoughForLeapBody(){val(w,m)=rig("lynx");w.player.x=180f;m.x=180f;val e=enemy(w,180f,0f);m.charge=1f;ready(w);assertTrue(w.adventure.map.clear(180f,300f,180f,0f,5f,0));assertFalse(w.team.order());assertEquals(0f,m.stateT);assertEquals(1f,m.charge)}
 @Test fun reachableDryTargetBeatsHiddenWetCluster(){val(w,m)=rig("lynx");enemy(w,0f,0f).wetT=5f;val e=enemy(w,240f,280f);m.charge=1f;ready(w);assertTrue(w.team.order());assertEquals(e.uid,m.targetUid)}
 @Test fun autoLynxKeepsChargeWhenAllTargetsBlocked(){val(w,m)=rig("lynx");enemy(w,0f,0f).wetT=5f;m.charge=1f;ready(w);w.team.update(0f);assertEquals(Mate.FOLLOW,m.state);assertEquals(1f,m.charge)}
 @Test fun streamChoosesOpenAlternativeInsteadOfCoveredNearest(){val(w,m)=rig("stream");val hidden=enemy(w,0f,80f);val open=enemy(w,160f,300f);ready(w);w.team.update(0f);assertEquals(0f,hidden.wetT);assertTrue(open.wetT>0f)}
 @Test fun jetStopsAtWallBeyondVisibleFrontTarget(){val(w,m)=rig("stream");m.y=220f;val front=enemy(w,0f,195f);val hidden=enemy(w,0f,70f);ready(w);w.team.update(0f);assertTrue(front.wetT>0f);assertEquals(0f,hidden.wetT);assertTrue(w.fx.ringCount>0);val end=w.fx.rings[0];assertTrue(end.y>=169f)}
 @Test fun wideJetCannotFitThroughNarrowClearCentreLine(){val(w,m)=rig("stream");w.player.x=200f;m.x=200f;m.spec=1;val e=enemy(w,200f,80f);ready(w);assertTrue(w.adventure.map.clear(200f,300f,200f,80f,5f,0));w.team.update(0f);assertEquals(0f,e.wetT);assertTrue(m.cd<=.3f)}
 @Test fun sameStreamCanWetAfterGateOpens(){val(w,m)=rig("stream",2);val e=enemy(w,0f,80f);ready(w);w.team.update(0f);assertTrue(e.wetT>0f);assertTrue(m.cd>1f)}
 @Test fun tideRespectsGateButRetainsOpenEffect(){for(flags in listOf(0,2)){val(w,m)=rig("stream",flags);m.y=180f;val e=enemy(w,0f,70f);ready(w);assertTrue(w.team.order());assertEquals(flags==2,e.wetT>0f)}}
 @Test fun conductionCanReachNeighbourAfterGateOpens(){val(w,m)=rig("stream",2);val a=enemy(w,0f,170f);val b=enemy(w,0f,70f);a.wetT=5f;b.wetT=5f;val hp=b.hp;ready(w);w.reactions.onHit(a,Element.SHOCK,10f,-1);assertTrue(b.hp<hp);assertEquals(0f,b.wetT)}
 @Test fun invulnerableSecondaryKeepsItsWater(){val(w,m)=rig("stream",2);val a=enemy(w,0f,170f);val b=enemy(w,0f,70f);a.wetT=5f;b.wetT=5f;b.invulnT=1f;val hp=b.hp;ready(w);w.reactions.onHit(a,Element.SHOCK,10f,-1);assertEquals(hp,b.hp);assertEquals(5f,b.wetT)}
 @Test fun followLargeStepCannotTunnelAcrossGate(){val(w,m)=rig("moss");w.player.y=-150f;m.y=300f;m.vy=-10000f;m.cd=10f;ready(w);w.team.update(1f);assertFalse(w.adventure.map.blocked(m.x,m.y,17.9f,0));assertTrue(m.y>=163f||kotlin.math.abs(m.x)>=188f)}
 @Test fun fullPauseFreezesCommittedPounce(){val g=com.tavernrush.game.core.Game();g.startExpedition();val w=g.world;w.weaponCount=0;w.adventure.start(0,2,15);w.player.x=0f;w.player.y=300f;w.team.setup(Friend("lynx"),null,0);val m=w.team.lead()!!;m.x=0f;m.y=300f;m.charge=1f;enemy(w,0f,0f);ready(w);assertTrue(w.team.order());val t=m.stateT;val uid=m.targetUid;g.pause();repeat(120){g.tick(1f/60f)};assertEquals(t,m.stateT);assertEquals(uid,m.targetUid);assertEquals(300f,m.y)}
 @Test fun landingQueryIsPureAndReflectsGate(){val(w,m)=rig("lynx");enemy(w,0f,0f);m.charge=1f;ready(w);repeat(10){assertFalse(w.team.leapTargetReady)};assertEquals(1f,m.charge);assertEquals(0,m.targetUid);assertEquals(0f,w.team.orderCd);w.adventure.start(0,2,15);assertTrue(w.team.leapTargetReady);assertEquals(Mate.FOLLOW,m.state)}
 @Test fun interruptedPounceDoesNotEmitFalseZap(){val(w,m)=rig("lynx",2);val e=enemy(w,0f,0f);val events=object:com.tavernrush.game.core.GameEvents{var zaps=0;override fun sfx(id:Int){if(id==com.tavernrush.game.core.Sfx.ZAP)zaps++}};w.events=events;m.charge=1f;ready(w);w.team.order();e.x=600f;w.team.update(.29f);assertEquals(0,events.zaps);assertEquals(0,w.run.reactions[Reactions.CONDUCT])}
}