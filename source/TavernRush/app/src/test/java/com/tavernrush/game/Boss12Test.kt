package com.tavernrush.game
import com.tavernrush.game.content.*
import com.tavernrush.game.core.*
import com.tavernrush.game.entities.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.render.pixel.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
class Boss12Test {
 private val ids=listOf("storm_heron","rootback","prism_crab")
 private fun rig(id:String):Pair<World,Enemy>{val w=World();w.reset(12,null);val e=w.spawnBoss(Content.enemyIndex(id))!!;e.x=0f;e.y=0f;e.spawnFade=0f;e.invulnT=0f;w.player.x=250f;w.player.y=0f;return w to e}
 private fun start(w:World,e:Enemy,stage:Int=0,attack:Int=0){w.hazardCount=0;e.stage=stage;e.hp=e.maxHp*when(stage){0->.9f;1->.5f;else->.2f};e.attack=attack;e.state=BossSystem.IDLE;e.cd=0f;w.boss.update(e,1f/60f,e.speed)}
 private fun ringWorld(gap:Boolean):World{val w=World();w.reset(1,null);w.player.x=100f;w.player.y=0f;val h=w.spawnHazard(Hazard.RING,0f,0f,18f,.01f,0f,0,life=2f,damage=12f)!!;h.state=1;h.r0=100f;h.r1=100f;h.gapHalf=if(gap).6f else 0f;return w}
 @Test fun visibleWaveOpeningActuallyPreventsDamage(){val w=ringWorld(true);val hp=w.player.hp;w.hazardSystem.update(.016f);assertEquals(hp,w.player.hp)}
 @Test fun solidPartOfWaveActuallyHurts(){val w=ringWorld(false);val hp=w.player.hp;w.hazardSystem.update(.016f);assertTrue(w.player.hp<hp)}
 @Test fun leavingOpeningCanStillCauseDamage(){val w=ringWorld(true);w.hazardSystem.update(.016f);w.player.x=-100f;w.hazardSystem.update(.016f);assertTrue(w.player.hp<w.player.maxHp)}
 @Test fun forkCorridorDoesNotHitButLaneDoes(){val(w,e)=rig("storm_heron");start(w,e);val hp=w.player.hp;w.hazardSystem.update(1.2f);assertEquals(hp,w.player.hp);start(w,e);w.player.y=90f;w.hazardSystem.update(1.2f);assertTrue(w.player.hp<hp)}
 @Test fun clippedBeamCannotHurtBehindStone(){val(w,e)=rig("prism_crab");w.adventure.start(0,0,12);e.x=0f;e.y=300f;w.player.x=0f;w.player.y=-200f;start(w,e);val hp=w.player.hp;w.hazardSystem.update(1.3f);assertEquals(hp,w.player.hp)}
 @Test fun narrowCenterLineIsNotEnoughForKnife(){val(w,e)=rig("prism_crab");w.adventure.start(5,15,12);w.player.x=51f;w.player.y=-438f;e.x=282f;e.y=-599f;assertTrue(w.adventure.map.clear(51f,-438f,282f,-599f,5f,15));assertFalse(BotPilot(BotPilot.KITE,BotPilot.GREEDY,1).hasAttackLine(w,e))}
 @Test fun openKnifeLineStillAllowsAttack(){val(w,e)=rig("prism_crab");w.adventure.start(5,15,12);w.player.x=400f;w.player.y=-550f;e.x=320f;e.y=-700f;assertTrue(BotPilot(BotPilot.KITE,BotPilot.GREEDY,1).hasAttackLine(w,e))}
 @Test fun knifeAimsFromActualLaunchOrigin(){val w=World();w.reset(1,null);w.player.x=0f;w.player.y=0f;val e=w.spawnEnemy(0,200f,120f,false)!!;e.spawnFade=0f;w.hash.rebuild(0f,0f);w.weapons[0].timer=0f;com.tavernrush.game.systems.weapons.ProjectileBehavior(w).update(w.weapons[0],.016f);assertTrue(w.projectileCount>0);val p=w.projectiles[0];val cross=(e.x-p.x)*p.vy-(e.y-p.y)*p.vx;assertEquals(0f,cross,.02f)}
 @Test fun recoveryHintDoesNotPromiseInvulnerability(){val(w,e)=rig("prism_crab");e.state=BossSystem.RECOVER;assertEquals("Окно для ответного удара",RegionalBosses.hint(e));assertEquals(0f,e.invulnT)}
 @Test fun oldBossDoesNotInheritNewHint(){val(w,e)=rig("rat_king");e.state=BossSystem.TELE;e.act=RegionalBosses.FORK;assertEquals("",RegionalBosses.hint(e))}
 @Test fun hintsFollowActualLockedAttack(){for(id in ids){val(w,e)=rig(id);start(w,e);assertTrue(RegionalBosses.hint(e).isNotEmpty());e.state=BossSystem.IDLE;assertEquals("",RegionalBosses.hint(e))}}
 @Test fun sixStableBossIds(){assertEquals(listOf("rat_king","ogre_chef","banshee")+ids,Content.bosses.map{Content.enemies[it].id})}
 @Test fun originalEnemyIndicesUnchanged(){assertEquals(12,Content.enemyIndex("rat_king"));assertEquals(13,Content.enemyIndex("ogre_chef"));assertEquals(14,Content.enemyIndex("banshee"))}
 @Test fun regionsHaveSixDifferentKeepers(){val seen=HashSet<String>();for(i in 0..5){val g=Game();g.startExpedition(i);val a=g.world.adventure;a.applyElement(Element.WATER,a.nodesX[1],a.nodesY[1]);g.world.player.x=a.nodesX[3];g.world.player.y=a.nodesY[3];g.interactWorld();val e=(0 until g.world.enemyCount).map{g.world.enemies[it]}.first{it.uid==a.bossUid};seen.add(e.def.id)};assertEquals(6,seen.size)}
 @Test fun modelsHaveDistinctAnatomy(){val models=ids.map{ModelLibrary.enemy(Content.enemies[Content.enemyIndex(it)].look)};assertEquals(3,models.map{it.id}.toSet().size);assertEquals(3,models.map{it.parts.map{p->listOf(p.shape,p.cx,p.cy,p.cz,p.rx,p.ry,p.rz)}}.toSet().size);for(m in models){assertTrue(m.parts.size>=20);assertNotNull(m.clip("walk"));assertNotNull(m.clip("attack"))}}
 @Test fun allAnatomyAndAttackPosesAreFinite(){for(id in ids){val m=ModelLibrary.enemy(Content.enemies[Content.enemyIndex(id)].look);for(p in m.parts){assertTrue(p.rx>0f&&p.ry>0f&&p.rz>0f);for(f in 0..5){val o=Pose();m.clip("attack")!!.fn(f,f/6f,p,o);assertTrue(o.dz.isFinite()&&o.rz.isFinite()&&o.ry.isFinite())}}}}
 @Test fun everyPatternWarnsBeforeDamage(){for(id in ids)for(stage in 0..2)for(index in 0..2){val(w,e)=rig(id);start(w,e,stage,index);assertTrue("$id $stage $index",w.hazardCount>0);for(i in 0 until w.hazardCount){val h=w.hazards[i];assertEquals(0,h.state);assertTrue(h.delay>=1f);assertEquals(e.uid,h.owner);assertEquals(e.def.index,h.ownerDef)}}}
 @Test fun threeInitialPatternsAreNotTheSame(){val kinds=ids.map{val(w,e)=rig(it);start(w,e);e.act};assertEquals(3,kinds.toSet().size)}
 @Test fun lastPrismPhaseAddsOffsetLane(){val(w,e)=rig("prism_crab");start(w,e,1);assertEquals(3,w.hazardCount);start(w,e,2);assertEquals(4,w.hazardCount);assertEquals(1.9f,w.hazards[3].delay,.01f);assertTrue(abs(w.hazards[3].y-e.y)>100f)}
 @Test fun phaseUnlocksNewActionNotJustHealth(){for(id in ids){val(w,e)=rig(id);start(w,e,0);val initial=e.act;start(w,e,1);assertNotEquals(initial,e.act)}}
 @Test fun phaseChangePausesAndCancelsPendingPattern(){for(id in ids){val(w,e)=rig(id);start(w,e);e.hp=e.maxHp*.5f;w.boss.update(e,.01f,e.speed);assertEquals(BossSystem.PHASE,e.state);assertTrue(e.invulnT>0f);assertEquals(1,e.stage);for(i in 0 until w.hazardCount)assertFalse(w.hazards[i].alive)}}
 @Test fun finalPhaseRequiresDifferentSequence(){for(id in ids){val(w,e)=rig(id);start(w,e);e.hp=e.maxHp*.2f;w.boss.update(e,.01f,e.speed);assertEquals(2,e.stage);assertEquals(BossSystem.PHASE,e.state)}}
 @Test fun newAttackHasImpactPoseBeforeRecovery(){for(id in ids){val(w,e)=rig(id);start(w,e);w.boss.update(e,e.stateT+.01f,e.speed);assertEquals(BossSystem.ACT,e.state);assertTrue(e.stateT>.2f);assertEquals(0f,e.invulnT);val n=e.attack;w.boss.update(e,.1f,e.speed);assertEquals(n,e.attack);assertEquals(BossSystem.ACT,e.state)}}
 @Test fun actionEntersRecoveryWithoutSecondAttack(){val(w,e)=rig("prism_crab");start(w,e);w.boss.update(e,e.stateT+.01f,e.speed);val n=e.attack;w.boss.update(e,.31f,e.speed);assertEquals(BossSystem.RECOVER,e.state);assertEquals(n,e.attack);assertEquals("Окно для ответного удара",RegionalBosses.hint(e))}
 @Test fun recoveryWindowExistsAfterWarning(){for(id in ids){val(w,e)=rig(id);start(w,e);repeat(120){if(e.state==BossSystem.TELE)w.boss.update(e,1f/60f,e.speed)};assertEquals(BossSystem.ACT,e.state);w.boss.update(e,.31f,e.speed);assertEquals(BossSystem.RECOVER,e.state);assertTrue(e.stateT>=.9f);assertEquals(0f,e.invulnT)}}
 @Test fun telegraphsDoNotFollowPlayerAfterCasting(){for(id in ids){val(w,e)=rig(id);start(w,e);val before=(0 until w.hazardCount).map{w.hazards[it].let{listOf(it.x,it.y,it.ang,it.len)}};w.player.x=-600f;w.player.y=400f;w.boss.update(e,.3f,e.speed);assertEquals(before,(0 until w.hazardCount).map{w.hazards[it].let{listOf(it.x,it.y,it.ang,it.len)}})}}
 @Test fun forkLeavesCentralCorridor(){val(w,e)=rig("storm_heron");start(w,e);assertEquals(2,w.hazardCount);val a=w.hazards[0];val b=w.hazards[1];assertTrue(abs(a.y-b.y)>a.r+b.r+w.player.radius*2);assertEquals(0f,a.ang,.01f);assertEquals(0f,b.ang,.01f)}
 @Test fun rootWaveHasWideVisibleOpening(){val(w,e)=rig("rootback");start(w,e);val h=w.hazards[0];assertEquals(Hazard.RING,h.kind);assertTrue(h.gapHalf>.5f);assertTrue(h.inGap(w.player.x,w.player.y,10f));assertFalse(h.inGap(-250f,0f,10f))}
 @Test fun gapWorksAcrossAngleWrap(){val h=Hazard();h.gapAngle=3.13f;h.gapHalf=.6f;assertTrue(h.inGap(-100f,-1f,4f));assertFalse(h.inGap(100f,0f,4f))}
 @Test fun gapEdgeAccountsForPlayerBody(){val h=Hazard();h.gapHalf=.6f;val a=.58f;assertTrue(h.inGap(cos(a)*100f,sin(a)*100f));assertFalse(h.inGap(cos(a)*100f,sin(a)*100f,10f))}
 @Test fun oldRingHasNoSafeOpening(){val h=Hazard();assertFalse(h.inGap(100f,0f))}
 @Test fun bodyAtRingCenterIsNotFalselySafe(){val h=Hazard();h.gapHalf=.6f;assertFalse(h.inGap(0f,0f,8f))}
 @Test fun pooledHazardCannotInheritOldGap(){val w=World();w.reset(3,null);val a=w.spawnHazard(Hazard.RING,0f,0f,18f,1f,0f,0)!!;a.gapHalf=.6f;a.gapAngle=2f;w.hazardCount=0;val b=w.spawnHazard(Hazard.RING,0f,0f,18f,1f,0f,0)!!;assertSame(a,b);assertEquals(0f,b.gapHalf);assertEquals(0f,b.gapAngle)}
 @Test fun finiteBeamEndsBeforeWall(){val m=RegionMap.all[0];val len=m.rayLength(0f,300f,0f,-1f,600f,25f,0);assertTrue(len in 120f..130f);assertTrue(m.clear(0f,300f,0f,300f-len,25f,0))}
 @Test fun openingGateRestoresLongBeam(){val m=RegionMap.all[0];assertEquals(600f,m.rayLength(0f,300f,0f,-1f,600f,25f,2),.01f)}
 @Test fun rayInsideWallCannotMakeInvisibleDamage(){assertEquals(0f,RegionMap.all[0].rayLength(0f,120f,0f,-1f,500f,25f,0))}
 @Test fun invalidRayCannotMakeHazard(){assertEquals(0f,RegionMap.all[0].rayLength(Float.NaN,0f,1f,0f,500f,25f,0))}
 @Test fun prismLanceClipsItsActualTelegraph(){val(w,e)=rig("prism_crab");w.adventure.start(0,0,12);e.x=0f;e.y=300f;w.player.x=0f;w.player.y=-200f;start(w,e);val h=w.hazards[0];assertEquals(Hazard.LINE,h.kind);assertTrue(h.len<150f);assertTrue(w.adventure.map.clear(h.x,h.y,h.x+cos(h.ang)*h.len,h.y+sin(h.ang)*h.len,h.r,w.adventure.flags))}
 @Test fun triadDoesNotCoverCenterInOpenField(){val(w,e)=rig("prism_crab");start(w,e,0,1);assertEquals(3,w.hazardCount);for(i in 0 until w.hazardCount){val h=w.hazards[i];val dx=w.player.x-h.x;val dy=w.player.y-h.y;assertTrue(dx*dx+dy*dy>(h.r+w.player.radius)*(h.r+w.player.radius))}}
 @Test fun everySequenceHasBoundedHazardCount(){for(id in ids)for(stage in 0..2)for(index in 0..5){val(w,e)=rig(id);start(w,e,stage,index);assertTrue(w.hazardCount in 1..5)}}
 @Test fun noNewKeeperSummonsEndlessAdds(){for(id in ids){val(w,e)=rig(id);repeat(6){start(w,e,2,it)};assertEquals(1,w.enemyCount)}}
 @Test fun arenaHealthIsNotReduced(){for(id in ids){val(w,e)=rig(id);assertEquals(e.def.hp,e.maxHp);assertFalse(w.adventure.active)}}
 @Test fun victoryCancelsOwnedHazards(){val(w,e)=rig("rootback");start(w,e);w.combat.hitEnemy(e,100000f,e.x,e.y,0f,-1,false);assertFalse(e.alive);for(i in 0 until w.hazardCount)assertFalse(w.hazards[i].alive)}
 @Test fun mapPauseStillStopsNewBossTimers(){val g=Game();g.startExpedition(4);val a=g.world.adventure;a.applyElement(Element.WATER,a.nodesX[1],a.nodesY[1]);g.world.player.x=a.nodesX[3];g.world.player.y=a.nodesY[3];g.interactWorld();val e=(0 until g.world.enemyCount).map{g.world.enemies[it]}.first{it.boss};val before=e.stateT;g.pause();g.openRouteMap();repeat(600){g.tick(1f/60f)};assertEquals(before,e.stateT)}
}
