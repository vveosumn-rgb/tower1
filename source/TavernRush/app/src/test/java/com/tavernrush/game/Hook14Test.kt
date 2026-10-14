package com.tavernrush.game
import com.tavernrush.game.content.Content
import com.tavernrush.game.systems.*
import org.junit.Assert.*
import org.junit.Test
class Hook14Test {
 private fun rig():World=World().also{it.reset(14,null,3);it.weaponCount=0;it.moveX=1f}
 private fun enemy(w:World,x:Float,y:Float)=w.spawnEnemy(Content.enemyIndex("rat"),x,y,false)!!
 @Test fun intentPullsForwardRatherThanNearestBehind(){val w=rig();val behind=enemy(w,-80f,0f);val front=enemy(w,220f,0f);assertTrue(w.heroAction.use());assertEquals(-80f,behind.x);assertEquals(99f,front.x,.01f)}
 @Test fun perpendicularTargetDoesNotConsumeAction(){val w=rig();val e=enemy(w,0f,200f);assertFalse(w.heroAction.use());assertEquals(0f,w.heroAction.cooldown);assertEquals(200f,e.y)}
 @Test fun facingUsedWhenThumbReleased(){val w=rig();w.moveX=0f;w.player.faceX=-1f;w.player.faceY=0f;val e=enemy(w,-200f,0f);assertTrue(w.heroAction.use());assertEquals(-90f,e.x,.01f)}
 @Test fun manifestationCannotBePulled(){val w=rig();val e=enemy(w,200f,0f);e.spawnFade=.5f;assertFalse(w.heroAction.use());assertEquals(200f,e.x);assertEquals(.5f,e.spawnFade)}
 @Test fun invulnerableTargetCannotBePulled(){val w=rig();val e=enemy(w,200f,0f);e.invulnT=1f;assertFalse(w.heroAction.use());assertEquals(200f,e.x)}
 @Test fun deadAndOutOfRangeTargetsIgnored(){val w=rig();val e=enemy(w,200f,0f);e.alive=false;enemy(w,331f,0f);assertFalse(w.heroAction.use())}
 @Test fun noContactTeleport(){val w=rig();val e=enemy(w,70f,0f);assertTrue(w.heroAction.use());assertTrue(e.x>=e.radius+w.player.radius+16f);assertTrue(e.x<70f)}
 @Test fun overlappingTargetIsNotMovedOrCharged(){val w=rig();val e=enemy(w,10f,0f);assertFalse(w.heroAction.use());assertEquals(10f,e.x);assertEquals(0f,w.heroAction.cooldown)}
 @Test fun pullClearsMomentumWithoutShorteningStun(){val w=rig();val e=enemy(w,200f,0f);e.kx=200f;e.ky=-100f;e.stun=.8f;assertTrue(w.heroAction.use());assertEquals(0f,e.kx);assertEquals(0f,e.ky);assertEquals(.8f,e.stun)}
 @Test fun ropeAndArrivalAreVisibleWithoutDamage(){val w=rig();val e=enemy(w,200f,0f);val hp=e.hp;assertTrue(w.heroAction.use());assertEquals(hp,e.hp);assertEquals(1,w.fx.boltCount);assertTrue(w.fx.bolts[0].straight);assertEquals(2,w.fx.bolts[0].count);assertEquals(200f,w.fx.bolts[0].pts[2]);assertEquals(0f,w.heroAction.hookT)}
 @Test fun noRepeatedPullDuringCooldown(){val w=rig();val e=enemy(w,200f,0f);w.heroAction.use();val x=e.x;assertFalse(w.heroAction.use());assertFalse(w.heroAction.switch());assertEquals(x,e.x)}
 @Test fun closedGateRejectsRopeEvenIfFinalEnemyPointIsClear(){val w=rig();w.adventure.start(0,0,14);w.player.x=0f;w.player.y=300f;w.moveX=0f;w.moveY=-1f;val e=enemy(w,0f,80f);assertTrue(w.adventure.map.clear(0f,201f,0f,201f,e.radius,0));assertFalse(w.heroAction.use());assertEquals(80f,e.y);assertEquals(0f,w.heroAction.cooldown)}
 @Test fun openedGateAllowsSameOrdinaryPull(){val w=rig();w.adventure.start(0,2,14);w.player.x=0f;w.player.y=300f;w.moveX=0f;w.moveY=-1f;val e=enemy(w,0f,80f);assertTrue(w.heroAction.use());assertEquals(201f,e.y,.01f)}
 @Test fun thinClearRopeCannotDragThickBodyThroughCorner(){val w=rig();w.adventure.start(0,0,14);w.player.x=190f;w.player.y=300f;w.moveX=0f;w.moveY=-1f;val e=enemy(w,190f,0f);e.radius=30f;assertTrue(w.adventure.map.clear(190f,300f,190f,0f,5f,0));w.heroAction.use();assertEquals(0f,e.y);assertEquals(0f,e.stun)}
 @Test fun blockedNearTargetDoesNotHideExposedForwardTarget(){val w=rig();w.adventure.start(0,0,14);w.player.x=190f;w.player.y=300f;w.moveX=0f;w.moveY=-1f;val blocked=enemy(w,190f,60f);blocked.radius=30f;val exposed=enemy(w,280f,20f);assertTrue(w.heroAction.use());assertEquals(60f,blocked.y);assertTrue(exposed.y>20f);assertFalse(w.adventure.map.blocked(exposed.x,exposed.y,exposed.radius-.1f,0))}
 @Test fun offAxisLandmarkDoesNotStealForwardEnemy(){val w=rig();w.adventure.start(0,2,14);w.player.x=0f;w.player.y=500f;w.moveX=1f;w.moveY=0f;val e=enemy(w,200f,500f);assertTrue(w.heroAction.use());assertEquals(0f,w.heroAction.hookT);assertEquals(90f,e.x,.01f)}
 @Test fun finitePositionsOnly(){val w=rig();enemy(w,Float.NaN,0f);assertFalse(w.heroAction.use())}
}
