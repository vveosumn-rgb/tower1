package com.tavernrush.game
import com.tavernrush.game.content.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.entities.*
import org.junit.Assert.*
import org.junit.Test
class Hook13Test {
 private fun rig():Pair<World,Enemy>{val w=World();w.reset(13,null,3);w.weaponCount=0;w.moveX=1f;val e=w.spawnBoss(Content.enemyIndex("prism_crab"))!!;e.x=260f;e.y=0f;e.spawnFade=0f;e.invulnT=0f;e.state=BossSystem.RECOVER;e.stateT=1.15f;return w to e}
 @Test fun readinessUsesSameWindowAndConsumesNothing(){val(w,e)=rig();assertTrue(w.heroAction.counterReady);assertEquals(0f,w.heroAction.cooldown);assertEquals(0,w.heroAction.hookTargetUid);w.heroAction.use();assertFalse(w.heroAction.counterReady)}
 @Test fun readinessQueryDoesNotOverwritePreviousAim(){val(w,e)=rig();val sx=w.heroAction.sx;val sy=w.heroAction.sy;w.moveX=0f;w.moveY=-1f;assertFalse(w.heroAction.counterReady);assertEquals(sx,w.heroAction.sx);assertEquals(sy,w.heroAction.sy)}
 @Test fun arrivalCannotBypassNewInvulnerability(){val(w,e)=rig();val hp=e.hp;w.heroAction.use();e.invulnT=1f;w.heroAction.update(.3f);assertEquals(hp,e.hp);assertEquals(0,w.heroAction.hookHits)}
 @Test fun fullPauseStopsHookInFlight(){val g=com.tavernrush.game.core.Game();g.selectHero(3);g.startExpedition(5);val w=g.world;val e=w.spawnBoss(Content.enemyIndex("prism_crab"))!!;w.player.x=400f;w.player.y=-450f;w.moveX=0f;w.moveY=-1f;e.x=400f;e.y=-700f;e.spawnFade=0f;e.invulnT=0f;e.state=BossSystem.RECOVER;e.stateT=1.15f;assertTrue(g.useHeroAction());g.pause();val t=w.heroAction.hookT;val x=w.player.x;val y=w.player.y;repeat(120){g.tick(1f/60f)};assertEquals(t,w.heroAction.hookT);assertEquals(x,w.player.x);assertEquals(y,w.player.y);assertEquals(e.uid,w.heroAction.hookTargetUid)}
 @Test fun recoveryAnchorMovesHeroNotBoss(){val(w,e)=rig();assertTrue(w.heroAction.use());assertEquals(e.uid,w.heroAction.hookTargetUid);val x=e.x;w.heroAction.update(.1f);assertTrue(w.player.x>0f);assertEquals(x,e.x);assertEquals(0f,e.kx);assertTrue(w.player.x<e.x-e.radius-w.player.radius)}
 @Test fun noDamageUntilArrival(){val(w,e)=rig();val hp=e.hp;w.heroAction.use();w.heroAction.update(.1f);assertEquals(hp,e.hp);w.heroAction.update(.16f);assertTrue(e.hp<hp);assertEquals(0,w.heroAction.hookTargetUid)}
 @Test fun arrivalStrikesAtMostOnce(){val(w,e)=rig();w.heroAction.use();w.heroAction.update(.26f);val hp=e.hp;repeat(20){w.heroAction.update(.1f)};assertEquals(hp,e.hp)}
 @Test fun activeTelegraphIsNotCounterWindow(){val(w,e)=rig();e.state=BossSystem.TELE;assertFalse(w.heroAction.use());assertEquals(0f,w.heroAction.cooldown)}
 @Test fun phaseInvulnerabilityIsNotCounterWindow(){val(w,e)=rig();e.invulnT=1f;assertFalse(w.heroAction.use())}
 @Test fun tooShortRecoveryCannotPromiseCounter(){val(w,e)=rig();e.stateT=.2f;assertFalse(w.heroAction.use())}
 @Test fun targetBehindDirectionCannotBeHooked(){val(w,e)=rig();w.moveX=-1f;assertFalse(w.heroAction.use())}
 @Test fun largeTargetOutsideRangeCannotBeHooked(){val(w,e)=rig();e.x=360f;assertFalse(w.heroAction.use())}
 @Test fun closeBossIsNotDisplaced(){val(w,e)=rig();e.x=120f;assertFalse(w.heroAction.use());assertEquals(120f,e.x)}
 @Test fun cooldownCannotBeCanceledByStyle(){val(w,e)=rig();w.heroAction.use();val cd=w.heroAction.cooldown;assertFalse(w.heroAction.switch());assertFalse(w.heroAction.use());assertEquals(cd,w.heroAction.cooldown)}
 @Test fun movingAnchorCanMakeCommittedStrikeMiss(){val(w,e)=rig();val hp=e.hp;w.heroAction.use();e.x=600f;w.heroAction.update(.3f);assertEquals(hp,e.hp);assertTrue(w.player.x<260f)}
 @Test fun deadAnchorCannotTransferToAnotherEnemy(){val(w,e)=rig();w.heroAction.use();e.alive=false;val q=w.spawnEnemy(0,260f,0f,false)!!;val hp=q.hp;w.heroAction.update(.3f);assertEquals(hp,q.hp);assertEquals(0,w.heroAction.hookTargetUid)}
 @Test fun phaseChangeBeforeArrivalPreventsStrike(){val(w,e)=rig();val hp=e.hp;w.heroAction.use();e.state=BossSystem.PHASE;w.heroAction.update(.3f);assertEquals(hp,e.hp)}
 @Test fun resetRemovesPendingCounter(){val(w,e)=rig();val hp=e.hp;w.heroAction.use();w.heroAction.reset();w.heroAction.update(.3f);assertEquals(hp,e.hp);assertEquals(0f,w.heroAction.hookT);assertEquals(0,w.heroAction.hookTargetUid)}
 @Test fun oldOrdinaryEnemyPullStillWorks(){val w=World();w.reset(13,null,3);val e=w.spawnEnemy(0,200f,0f,false)!!;assertTrue(w.heroAction.use());assertTrue(e.x<200f);assertEquals(0f,w.heroAction.hookT)}
 @Test fun activePathDoesNotCrossClosedWall(){val(w,e)=rig();w.adventure.start(0,0,13);w.player.x=0f;w.player.y=300f;w.moveX=0f;w.moveY=-1f;e.x=0f;e.y=-100f;w.heroAction.use();assertEquals(0,w.heroAction.hookTargetUid);if(w.heroAction.hookT>0f)assertTrue(w.adventure.map.clear(w.player.x,w.player.y,w.heroAction.hookEndX,w.heroAction.hookEndY,20f,w.adventure.flags))}
 @Test fun anchorBehindWallIsRejectedEvenWhenLandingPointIsClear(){val(w,e)=rig();w.adventure.start(0,0,13);w.player.x=0f;w.player.y=300f;w.moveX=0f;w.moveY=-1f;e.x=0f;e.y=80f;assertTrue(w.adventure.map.clear(0f,300f,0f,178f,20f,0));w.heroAction.use();assertEquals(0,w.heroAction.hookTargetUid)}
 @Test fun openingWallAllowsAnchorPath(){val(w,e)=rig();w.adventure.start(0,2,13);w.player.x=0f;w.player.y=250f;w.moveX=0f;w.moveY=-1f;e.x=0f;e.y=0f;assertTrue(w.heroAction.use());assertEquals(e.uid,w.heroAction.hookTargetUid)}
 @Test fun closingPathMidHookStopsBeforeWall(){val(w,e)=rig();w.adventure.start(0,2,13);w.player.x=0f;w.player.y=250f;w.moveX=0f;w.moveY=-1f;e.x=0f;e.y=0f;w.heroAction.use();w.adventure.start(0,0,13);w.player.x=0f;w.player.y=250f;w.heroAction.update(.26f);assertTrue(w.player.y>=165f);assertEquals(0,w.heroAction.hookTargetUid)}
}
