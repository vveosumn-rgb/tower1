package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.content.Element
import org.junit.Assert.*
import org.junit.Test
/** Geometry and state invariants, not an aesthetic or human flow judgement. */
class Region11Test {
 private class Aim(w:World):com.tavernrush.game.systems.weapons.WeaponBehavior(w){override fun update(wi:com.tavernrush.game.entities.WeaponInstance,dt:Float){};fun pick(x:Float,y:Float)=nearestVisible(x,y,500f,5f)}
 @Test fun autoAimPrefersExposedTargetRatherThanWastingShots(){val g=Game();g.startExpedition();val w=g.world;val behind=w.spawnEnemy(com.tavernrush.game.content.Content.enemyIndex("rat"),0f,60f,false)!!;behind.spawnFade=0f;val exposed=w.spawnEnemy(com.tavernrush.game.content.Content.enemyIndex("rat"),260f,220f,false)!!;exposed.spawnFade=0f;val index=Aim(w).pick(0f,220f);assertTrue(index>=0);assertEquals(exposed.uid,w.enemies[index].uid)}
 @Test fun navigatorCanRecoverFromMarginContact(){val out=FloatArray(2);val m=RegionMap.all[0];assertTrue(RegionNavigator().next(m,0,0f,165f,0f,-200f,24f,out));assertTrue(out[1]>165f)}
 @Test fun enemyArrowStopsAtBarrier(){val g=Game();g.startExpedition();g.world.player.x=500f;g.world.player.y=500f;g.world.spawnShot(com.tavernrush.game.entities.EnemyShot.ARROW,0f,220f,0f,-2000f,3f,5f,2f,0,0);repeat(8){g.tick(1f/60f)};assertEquals(0,g.world.shotCount)}
 @Test fun knifeStopsAtBarrierBeforeHittingBeyondIt(){val g=Game();g.startExpedition();g.world.player.x=500f;g.world.player.y=500f;val p=g.world.spawnProjectile(com.tavernrush.game.entities.Projectile.KNIFE,0,0f,220f)!!;p.vy=-2000f;p.life=2f;p.radius=5f;p.pierceLeft=8;p.damage=1f;repeat(8){g.tick(1f/60f)};assertFalse(p.alive)}
 @Test fun guardianStartsAtCurrentSource(){for(i in 0..5){val g=Game();g.startExpedition(i);val a=g.world.adventure;a.applyElement(Element.WATER,a.nodesX[1],a.nodesY[1]);g.world.player.x=a.nodesX[3];g.world.player.y=a.nodesY[3];assertTrue(g.interactWorld());val b=(0 until g.world.enemyCount).map{g.world.enemies[it]}.first{it.uid==a.bossUid};assertEquals(a.nodesX[3],b.x);assertEquals(a.nodesY[3]-100f,b.y)}}
 @Test fun sixDistinctLayouts(){assertEquals(6,RegionMap.all.map{it.x.toList() to it.y.toList()}.toSet().size)}
 @Test fun originalLandmarkIdsAndCoordinatesRemain(){val m=RegionMap.all[0];assertEquals(120f,m.y[0]);assertEquals(-250f,m.x[1]);assertEquals(-730f,m.y[11])}
 @Test fun everyNewLayoutHasTwoPermanentWalls(){for(i in 1..5)assertEquals(3,RegionMap.all[i].walls.size)}
 @Test fun gateOpensButPermanentGeometryRemains(){for(m in RegionMap.all){val b=m.walls[0];assertTrue(m.blocked(b.x,b.y,0f,0));assertFalse(m.blocked(b.x,b.y,0f,2));for(w in m.walls.drop(1))assertTrue(m.blocked(w.x,w.y,0f,255))}}
 @Test fun everyLandmarkExceptGateIsOutsideWalls(){for(m in RegionMap.all)for(i in 1..11)assertFalse("landmark $i ${m.x.toList()}",m.blocked(m.x[i],m.y[i],20f,0))}
 @Test fun linesCrossWallsInBothDirections(){val m=RegionMap.all[0];assertFalse(m.clear(0f,300f,0f,0f,20f,0));assertFalse(m.clear(0f,0f,0f,300f,20f,0))}
 @Test fun openedGateAllowsSameLine(){assertTrue(RegionMap.all[0].clear(0f,300f,0f,0f,20f,2))}
 @Test fun lineOutsideBarrierIsClear(){assertTrue(RegionMap.all[0].clear(230f,300f,230f,0f,20f,0))}
 @Test fun stationaryPointInsideWallIsNotClear(){assertFalse(RegionMap.all[0].clear(0f,120f,0f,120f,0f,0))}
 @Test fun diagonalAndParallelLinesRespectWall(){val m=RegionMap.all[0];assertFalse(m.clear(-200f,120f,200f,120f,0f,0));assertFalse(m.clear(-300f,300f,300f,-100f,0f,0));assertTrue(m.clear(-300f,300f,300f,300f,0f,0))}
 @Test fun invalidRayIsRejected(){assertFalse(RegionMap.all[0].clear(Float.NaN,0f,0f,0f,0f,0))}
 @Test fun resolveKeepsActorOutside(){val out=FloatArray(2);for(m in RegionMap.all)for(b in m.walls){m.resolve(b.x,b.y,20f,0,out);assertFalse(m.blocked(out[0],out[1],19.9f,0))}}
 @Test fun longDashCannotTunnelThroughGate(){val out=FloatArray(2);RegionMap.all[0].sweep(0f,300f,0f,-150f,20f,0,out);assertTrue(out[1]>=165f)}
 @Test fun longDashCanPassOpenedGate(){val out=FloatArray(2);RegionMap.all[0].sweep(0f,300f,0f,-150f,20f,2,out);assertEquals(-150f,out[1],.01f)}
 @Test fun verticalWallAlsoBlocksLongSweep(){val m=RegionMap.all[3];val out=FloatArray(2);m.sweep(-400f,120f,100f,120f,20f,0,out);assertTrue(out[0]<=-215f)}
 @Test fun navigationFindsFinitePathToEveryLandmark(){val nav=RegionNavigator();val out=FloatArray(2);for((r,m) in RegionMap.all.withIndex())for(flags in listOf(0,2))for(goal in 1..11){var x=m.x[5];var y=m.y[5];var reached=false
  for(t in 0..1600){assertTrue("unreachable r$r goal$goal flags$flags",nav.next(m,flags,x,y,m.x[goal],m.y[goal],24f,out));val dx=out[0]-x;val dy=out[1]-y;val d=kotlin.math.sqrt(dx*dx+dy*dy);if(d>0f){val k=minOf(8f/d,1f);x+=dx*k;y+=dy*k};assertFalse(m.blocked(x,y,23.9f,flags));if(kotlin.math.abs(x-m.x[goal])+kotlin.math.abs(y-m.y[goal])<1f){reached=true;break}}
  assertTrue("stalled r$r goal$goal flags$flags x$x y$y",reached)
 }}
 @Test fun waterAndElementsUseCurrentRegionNodes(){for(i in 0..5){val g=Game();g.startExpedition(i);val a=g.world.adventure;assertTrue(a.applyElement(Element.WATER,a.nodesX[1],a.nodesY[1]));assertTrue(a.waterAt(a.nodesX[1],a.nodesY[1]));assertTrue(a.waterAt(a.nodesX[3],a.nodesY[3]));assertTrue(a.applyElement(Element.FROST,a.nodesX[3],a.nodesY[3]))}}
 @Test fun heavyActionMatchesVerticalAndHorizontalGate(){for(i in 0..5){val g=Game();g.startExpedition(i);val a=g.world.adventure;assertTrue(a.applyElement(Element.HEAVY,a.nodesX[0],a.nodesY[0]));assertTrue(a.nodeDone(0))}}
 @Test fun eachRegionStartsAtItsOwnCamp(){for(i in 0..5){val g=Game();g.startExpedition(i);val a=g.world.adventure;assertEquals(a.nodesX[5],g.world.player.x);assertEquals(a.nodesY[5],g.world.player.y)}}
 @Test fun teamStartsNearHeroNotOldOrigin(){val g=Game();g.profile.adventure.adopt(0,1);g.startExpedition(1);val m=g.world.team.lead()!!;assertTrue(kotlin.math.abs(m.x-g.world.player.x)<60f);assertTrue(kotlin.math.abs(m.y-g.world.player.y)<60f)}
 @Test fun mapCannotOpenFromArena(){val g=Game();g.startRun();g.pause();g.openRouteMap();assertEquals(ScreenId.PAUSE,g.screen)}
 @Test fun mapRequiresPause(){val g=Game();g.startExpedition();g.openRouteMap();assertEquals(ScreenId.RUN,g.screen);g.pause();g.openRouteMap();assertEquals(ScreenId.ROUTE_MAP,g.screen)}
 @Test fun mapIsInsideRunAndCannotEditCollection(){val g=Game();g.profile.adventure.adopt(0,1);g.startExpedition();g.pause();g.openRouteMap();assertTrue(g.inRun);assertFalse(g.evolveAnimal(1,1));g.openHome();assertEquals(ScreenId.ROUTE_MAP,g.screen)}
 @Test fun mapFreezesWorldAndCooldowns(){val g=Game();g.startExpedition(3);g.useHeroAction();val t=g.world.time;val cd=g.world.heroAction.cooldown;g.pause();g.openRouteMap();repeat(600){g.tick(1f/60f)};assertEquals(t,g.world.time);assertEquals(cd,g.world.heroAction.cooldown);assertEquals(0f,g.world.adventure.climateT)}
 @Test fun backFromMapRestoresPauseThenRun(){val g=Game();g.startExpedition();g.pause();g.openRouteMap();g.back();assertEquals(ScreenId.PAUSE,g.screen);g.back();assertEquals(ScreenId.RUN,g.screen)}
 @Test fun mapDoesNotBypassPendingCard(){val g=Game();g.startExpedition();g.go(ScreenId.CARD_PICK);g.pause();g.openRouteMap();g.back();g.resume();assertEquals(ScreenId.CARD_PICK,g.screen)}
 @Test fun hiddenCacheCannotBePinned(){val g=Game();g.startExpedition();assertFalse(g.world.adventure.pin(11));assertFalse(g.world.adventure.pin(-1));assertFalse(g.world.adventure.pin(40))}
 @Test fun visiblePinChangesDirectionWithoutMovingPlayer(){val g=Game();g.startExpedition();val a=g.world.adventure;val x=g.world.player.x;val y=g.world.player.y;assertTrue(a.pin(4));assertEquals(4,a.objective);assertEquals(x,g.world.player.x);assertEquals(y,g.world.player.y);a.clearPin();assertEquals(1,a.objective)}
 @Test fun completedPinYieldsToStory(){val g=Game();g.startExpedition();val a=g.world.adventure;a.pin(4);g.world.player.x=a.nodesX[4];g.world.player.y=a.nodesY[4];a.interact();assertTrue(a.nodeDone(4));assertEquals(1,a.objective);assertFalse(a.pin(4))}
 @Test fun newExpeditionClearsOldPin(){val g=Game();g.startExpedition();g.world.adventure.pin(4);g.abandonRun();g.startExpedition(1);assertEquals(-1,g.world.adventure.pinned)}
}
