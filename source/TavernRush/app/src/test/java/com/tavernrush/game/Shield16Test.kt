package com.tavernrush.game
import com.tavernrush.game.content.Content
import com.tavernrush.game.entities.EnemyShot
import com.tavernrush.game.systems.World
import org.junit.Assert.*
import org.junit.Test
class Shield16Test {
 private fun world():World=World().also{it.reset(16,null,1);it.weaponCount=0;it.moveX=1f;it.heroAction.use()}
 private fun shot(w:World,x:Float,vx:Float,owner:Int=Content.enemyIndex("archer"))=w.spawnShot(EnemyShot.ARROW,x,0f,vx,0f,10f,7f,2f,0xffe2c98a.toInt(),owner)!!
 @Test fun frontArrowCanBeParriedEvenIfShooterMovedBehind(){val w=world();val e=w.spawnEnemy(Content.enemyIndex("archer"),-200f,0f,false)!!;shot(w,22f,-600f,e.def.index);val hp=w.player.hp;w.hazardSystem.update(1f/60f);assertEquals(hp,w.player.hp);assertEquals(0f,w.heroAction.shield)}
 @Test fun rearArrowCannotBeParriedBecauseShooterIsInFront(){val w=world();val e=w.spawnEnemy(Content.enemyIndex("archer"),200f,0f,false)!!;shot(w,-22f,600f,e.def.index);val hp=w.player.hp;w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<hp);assertTrue(w.heroAction.shield>0f)}
 @Test fun projectileParryInterceptsWithoutRemoteCounter(){val w=world();val e=w.spawnEnemy(Content.enemyIndex("archer"),300f,0f,false)!!;val hp=e.hp;shot(w,22f,-600f,e.def.index);w.hazardSystem.update(1f/60f);assertEquals(hp,e.hp);assertEquals(0f,e.stun);assertEquals(0,w.shotCount)}

 @Test fun deadShooterDoesNotDisableARealParry(){val w=world();val e=w.spawnEnemy(Content.enemyIndex("archer"),200f,0f,false)!!;e.alive=false;shot(w,22f,-600f,e.def.index);val hp=w.player.hp;w.hazardSystem.update(1f/60f);assertEquals(hp,w.player.hp);assertEquals(0,w.shotCount)}
 @Test fun noShooterObjectIsRequired(){val w=world();shot(w,22f,-600f);w.hazardSystem.update(1f/60f);assertEquals(w.player.maxHp,w.player.hp);assertEquals(0,w.shotCount)}
 @Test fun lateFrontBlockStillConsumesWithoutPerfectCounter(){val w=world();w.heroAction.update(.2f);shot(w,22f,-600f);w.hazardSystem.update(1f/60f);assertEquals(w.player.maxHp,w.player.hp);assertEquals(0,w.shotCount);assertEquals(0f,w.heroAction.shield)}
 @Test fun coveringStyleCanInterceptRearShot(){val w=World();w.reset(16,null,1);w.heroAction.switch();w.heroAction.use();shot(w,-22f,600f);w.hazardSystem.update(1f/60f);assertEquals(w.player.maxHp,w.player.hp);assertEquals(0,w.shotCount)}
 @Test fun sideOfDirectionalShieldRemainsExposed(){val w=world();w.spawnShot(EnemyShot.WEB,0f,22f,0f,-600f,10f,7f,2f,0,0,slowTime=1f,slowMul=.5f);w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<w.player.maxHp);assertTrue(w.player.slowT>0f);assertTrue(w.heroAction.shield>0f)}
 @Test fun parriedWebDoesNotApplySlow(){val w=world();w.spawnShot(EnemyShot.WEB,22f,0f,-600f,0f,10f,7f,2f,0,0,slowTime=1f,slowMul=.5f);w.hazardSystem.update(1f/60f);assertEquals(0f,w.player.slowT);assertEquals(0,w.shotCount)}
 @Test fun everyFlyingKindCanBeIntercepted(){for(kind in 0..3){val w=world();w.spawnShot(kind,22f,0f,-600f,0f,10f,7f,2f,0,0);w.hazardSystem.update(1f/60f);assertEquals(0,w.shotCount);assertEquals(w.player.maxHp,w.player.hp)}}
 @Test fun secondShotInSameFrameCanHurtAfterShieldIsSpent(){val w=world();shot(w,22f,-600f);shot(w,23f,-600f);w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<w.player.maxHp);assertEquals(0,w.shotCount)}
 @Test fun dodgeDoesNotSpendShieldOrDeletePassingShot(){val w=world();w.player.dodgeT=.2f;shot(w,22f,-600f);w.hazardSystem.update(1f/60f);assertEquals(w.player.maxHp,w.player.hp);assertTrue(w.heroAction.shield>0f);assertEquals(1,w.shotCount);assertEquals(1,w.run.perfectDodges)}
 @Test fun godModeDoesNotClaimParryOrSpendShield(){val w=world();w.godMode=true;shot(w,22f,-600f);w.hazardSystem.update(1f/60f);assertTrue(w.heroAction.shield>0f);assertEquals(1,w.shotCount)}
 @Test fun temporaryInvulnerabilityDoesNotSpendShield(){val w=world();w.player.invuln=.2f;shot(w,22f,-600f);w.hazardSystem.update(1f/60f);assertTrue(w.heroAction.shield>0f);assertEquals(1,w.shotCount)}
 @Test fun expiryDoesNotPretendToHitOrParry(){val w=world();shot(w,22f,-600f).life=.001f;w.hazardSystem.update(1f/60f);assertTrue(w.heroAction.shield>0f);assertEquals(w.player.maxHp,w.player.hp);assertEquals(0,w.shotCount)}
 @Test fun stationaryShotOutsideBodyDoesNotHit(){val w=World();w.reset(16,null);shot(w,50f,0f);w.hazardSystem.update(1f/60f);assertEquals(w.player.maxHp,w.player.hp);assertEquals(1,w.shotCount)}
 @Test fun sweptShotCannotTunnelThroughPlayer(){val w=World();w.reset(16,null);shot(w,60f,-7200f);w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<w.player.maxHp);assertEquals(0,w.shotCount)}
 @Test fun segmentHelperFindsFirstContactNotExit(){val w=World();assertEquals(.4f,w.hazardSystem.firstBodyHit(50f,0f,-50f,0f,0f,0f,10f),.001f);assertEquals(-1f,w.hazardSystem.firstBodyHit(50f,30f,-50f,30f,0f,0f,10f));assertEquals(0f,w.hazardSystem.firstBodyHit(0f,0f,50f,0f,0f,0f,10f));assertEquals(-1f,w.hazardSystem.firstBodyHit(50f,0f,50f,0f,0f,0f,10f))}
 @Test fun stoneBeforePlayerStopsHitAndShowsLocalImpact(){val w=World();w.reset(16,null);w.adventure.start(0,0,16);w.player.x=0f;w.player.y=0f;val s=w.spawnShot(EnemyShot.ARROW,0f,300f,0f,-24000f,10f,7f,2f,0,0)!!;w.hazardSystem.update(1f/60f);assertEquals(w.player.maxHp,w.player.hp);assertEquals(0,w.shotCount);assertTrue(s.y>150f);assertTrue(w.fx.particleCount>0);assertTrue(w.fx.particles[0].y>150f)}
 @Test fun playerBeforeStoneCanBeHitEvenIfStepEndsBehindStone(){val w=World();w.reset(16,null);w.adventure.start(0,0,16);w.player.x=0f;w.player.y=210f;w.spawnShot(EnemyShot.ARROW,0f,300f,0f,-24000f,10f,7f,2f,0,0);w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<w.player.maxHp);assertEquals(0,w.shotCount)}
 @Test fun openedGateDoesNotLeaveInvisibleCover(){val w=World();w.reset(16,null);w.adventure.start(0,2,16);w.player.x=0f;w.player.y=0f;w.spawnShot(EnemyShot.ARROW,0f,300f,0f,-24000f,10f,7f,2f,0,0);w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<w.player.maxHp)}
 @Test fun outsideArenaHasNoRegionStone(){val w=World();w.reset(16,null);w.player.x=0f;w.player.y=0f;w.spawnShot(EnemyShot.ARROW,0f,300f,0f,-24000f,10f,7f,2f,0,0);w.hazardSystem.update(1f/60f);assertTrue(w.player.hp<w.player.maxHp)}
 @Test fun meleeCounterStillHurtsActualAttacker(){val w=world();val e=w.spawnEnemy(Content.enemyIndex("orc"),80f,0f,false)!!;val hp=e.hp;w.combat.hurtPlayer(10f,e.def.index,false,0,0,0f,1f,true,e.x,e.y);assertTrue(e.hp<hp);assertEquals(w.player.maxHp,w.player.hp)}
 @Test fun environmentalDamageCannotUseProjectileParry(){val w=world();w.combat.hurtPlayer(10f,-1,false,0,0,0f,1f,true);assertTrue(w.player.hp<w.player.maxHp);assertTrue(w.heroAction.shield>0f)}
 @Test fun pooledShotCannotRetainOldParryContact(){val w=world();val a=shot(w,22f,-600f);w.hazardSystem.update(1f/60f);val b=shot(w,-60f,0f);assertSame(a,b);assertEquals(-60f,b.x);assertTrue(b.alive);w.hazardSystem.update(1f/60f);assertEquals(1,w.shotCount)}
}
