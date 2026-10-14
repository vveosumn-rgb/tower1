package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.systems.*
import com.tavernrush.game.meta.*
import com.tavernrush.game.save.*
import org.junit.Assert.*
import org.junit.Test
class Dungeon18Test {
 private fun game(saved:Int=0):Game=Game().also{it.profile.adventure.regions[0]=saved;it.startExpedition();it.world.godMode=true}
 private fun at(g:Game,x:Float,y:Float){g.world.player.x=x;g.world.player.y=y;g.world.moveX=0f;g.world.moveY=0f}
 private fun lever(g:Game){at(g,-555f,45f);assertTrue(g.interactWorld());assertTrue(g.world.adventure.nodeDone(13))}
 @Test fun doorwayIsPhysicalNotMenu(){val g=game();at(g,-480f,270f);assertEquals("Вход в водовод",g.world.adventure.actionLabel());assertTrue(g.interactWorld());assertEquals(ScreenId.RUN,g.screen);assertEquals(-480f,g.world.player.x,0f);assertEquals(270f,g.world.player.y,0f);assertFalse(g.postRunHubAvailable)}
 @Test fun doorFitsHeroAndDoesNotClipWalls(){val m=RegionMap.all[0];assertTrue(m.clear(-480f,280f,-480f,170f,20f,0));assertFalse(m.clear(-570f,280f,-570f,170f,20f,0))}
 @Test fun partitionBlocksBeforeWheel(){val m=RegionMap.all[0];assertFalse(m.clear(-480f,-60f,-480f,-200f,20f,0));assertTrue(m.clear(-480f,-60f,-480f,-200f,20f,256))}
 @Test fun outsideCannotTurnWheelThroughWall(){val g=game();at(g,-645f,35f);assertNotEquals(13,g.world.adventure.nearNode());assertFalse(g.world.adventure.nodeDone(13))}
 @Test fun pinnedWheelDoesNotAllowThroughWall(){val g=game();at(g,-555f,35f);assertTrue(g.world.adventure.pin(13));at(g,-645f,35f);assertNotEquals(13,g.world.adventure.nearNode());g.interactWorld();assertFalse(g.world.adventure.nodeDone(13))}
 @Test fun wheelIsOneTimePersistentOpening(){val g=game();lever(g);assertEquals(256,g.profile.adventure.regions[0]);val materials=g.profile.adventure.materials;assertEquals(2,materials);assertFalse(g.interactWorld());assertEquals(materials,g.profile.adventure.materials)}
 @Test fun oldFlagsAreNotOverwritten(){val g=game(15);lever(g);assertEquals(271,g.profile.adventure.regions[0]);assertEquals(15,g.world.adventure.flags and 255)}
 @Test fun chestCannotBeOpenedThroughPartition(){val g=game(256);at(g,-480f,-210f);g.tick(.016f);assertTrue(g.world.adventure.pin(14));at(g,-480f,-90f);assertNotEquals(14,g.world.adventure.nearNode());assertFalse(g.world.adventure.nodeDone(14))}
 @Test fun upperRoomSpawnsTwoGuardiansOnlyOnce(){val g=game();lever(g);at(g,-480f,-200f);g.tick(.016f);assertTrue(g.world.adventure.conduitEncounterStarted);assertEquals(2,g.world.enemyCount);repeat(60){g.tick(.016f)};assertEquals(2,g.world.enemyCount)}
 @Test fun savedChestDoesNotRespawnRewardGuards(){val g=game(768);at(g,-480f,-200f);g.tick(.016f);assertEquals(0,g.world.enemyCount);assertFalse(g.world.adventure.conduitEncounterStarted)}
 @Test fun rewardNeedsEncounterNotRemoteTap(){val g=game(256);at(g,-480f,-350f);assertFalse(g.interactWorld());assertFalse(g.world.adventure.nodeDone(14))}
 @Test fun aliveNearbyGuardPreventsClaim(){val g=game(256);at(g,-480f,-350f);g.tick(.016f);assertFalse(g.interactWorld());assertFalse(g.world.adventure.nodeDone(14))}
 @Test fun clearedChestPaysOnceAcrossReentry(){val g=game(256);at(g,-480f,-350f);g.tick(.016f);for(i in 0 until g.world.enemyCount)g.world.enemies[i].alive=false;assertTrue(g.interactWorld());assertEquals(768,g.profile.adventure.regions[0]);val mats=g.profile.adventure.materials;assertEquals(2,mats);assertFalse(g.interactWorld());g.abandonRun();g.startExpedition();at(g,-480f,-350f);g.tick(.016f);assertFalse(g.interactWorld());assertEquals(mats,g.profile.adventure.materials)}
 @Test fun saveRoundTripPreservesRoomAndReward(){val p=AdventureProgress();p.regions[0]=768;val next=AdventureProgress.decode(p.encode());assertEquals(768,next.regions[0]);assertEquals(0,next.regions[1])}
 @Test fun invalidBitsRemainRejectedElsewhere(){val p=AdventureProgress();p.regions[1]=256;assertThrows(IllegalArgumentException::class.java){AdventureProgress.decode(p.encode())}}
 @Test fun rewardWithoutOpenedGateIsRejected(){val p=AdventureProgress();p.regions[0]=512;assertThrows(IllegalArgumentException::class.java){AdventureProgress.decode(p.encode())}}
 @Test fun oldEightBitSaveStillDecodes(){val p=AdventureProgress();p.regions.fill(255);assertEquals(255,AdventureProgress.decode(p.encode()).regions[0])}
 @Test fun restartingKeepsOpeningWithoutHealingTransaction(){val s=MemoryStore();val g=Game(s);g.startExpedition();lever(g);val next=Game(s);assertEquals(256,next.profile.adventure.regions[0]);assertFalse(next.postRunHubAvailable);next.startExpedition();assertFalse(next.world.adventure.map.enabled(8,next.world.adventure.flags))}
 @Test fun walkingInsideDoesNotResetHpWeaponsOrCooldown(){val g=game(256);val w=g.world;val hp=w.player.hp*.6f;w.player.hp=hp;val count=w.weaponCount;assertTrue(w.heroAction.use());at(g,-480f,280f);w.moveX=0f;w.moveY=-1f;repeat(80){g.tick(.016f)};assertTrue(w.adventure.insideConduit());assertEquals(hp,w.player.hp,.01f);assertEquals(count,w.weaponCount);assertTrue(w.heroAction.cooldown>.1f);assertFalse(g.postRunHubAvailable)}
 @Test fun interiorDoesNotOfferInstantCampTeleport(){val g=game(271);at(g,-480f,0f);assertTrue(g.world.adventure.intentionDone);assertFalse(g.world.adventure.quickReturnReady());assertFalse(g.world.adventure.escaped)}
 @Test fun mapOnlyExposesConduitInValley(){val g=game();assertTrue(g.world.adventure.visibleNode(12));assertFalse(g.world.adventure.visibleNode(13));assertFalse(g.world.adventure.visibleNode(14));g.abandonRun();g.startExpedition(1);for(i in 12..14)assertFalse(g.world.adventure.visibleNode(i))}
 @Test fun navigatorHandlesMoreThanThreeWallsAndFindsEntrance(){val m=RegionMap.all[0];val n=RegionNavigator();val out=FloatArray(2);assertTrue(n.next(m,256,-200f,80f,-480f,40f,20f,out));assertTrue(m.clear(-200f,80f,out[0],out[1],20f,256));assertTrue(out.all{it.isFinite()})}
 @Test fun completeRouteCanBeWalkedWithContinuousCollision(){val m=RegionMap.all[0];val out=FloatArray(2);var x=0f;var y=500f;for(t in arrayOf(-480f to 290f,-480f to 30f,-555f to 35f,-480f to -60f,-480f to -350f,-480f to 290f,0f to 500f)){val flags=if(t.second< -60f||t.second>=290f)256 else 0;m.sweep(x,y,t.first,t.second,20f,flags,out);assertEquals(t.first,out[0],.01f);assertEquals(t.second,out[1],.01f);x=out[0];y=out[1]}}

 @Test fun outerWallsLeaveLegalSpaceAtWorldBoundary(){val m=RegionMap.all[0];val out=FloatArray(2);for(y in listOf(-470f,-350f,-120f,35f,235f,300f)){m.resolve(-650f,y,20f,0,out);assertTrue(out[0]>= -650f);assertFalse(m.blocked(out[0],out[1],19.5f,0))}}
 @Test fun individualIdentitySurvivesWalkingIntoRoom(){val g=Game();val a=g.profile.adventure.adopt(0,1)!!;g.selectAnimal(a.uid);g.startExpedition();val uid=g.world.team.mates[0]!!.animalUid;at(g,-480f,180f);g.tick(.016f);assertEquals(uid,g.world.team.mates[0]!!.animalUid);assertEquals(a.uid,uid);assertNotNull(g.profile.adventure.find(uid))}
 @Test fun fileStoreKeepsGateAcrossReload(){val dir=kotlin.io.path.createTempDirectory("conduit18").toFile();try{val g=Game(SaveManager(dir));g.startExpedition();lever(g);val next=Game(SaveManager(dir));assertEquals(256,next.profile.adventure.regions[0]);assertEquals(2,next.profile.adventure.materials);assertFalse(next.postRunHubAvailable)}finally{dir.deleteRecursively()}}
 @Test fun pauseFreezesEncounterAndCooldown(){val g=game(256);at(g,-480f,-210f);g.tick(.016f);g.useHeroAction();val time=g.world.time;val cd=g.world.heroAction.cooldown;val x=g.world.enemies[0].x;g.pause();repeat(100){g.tick(.1f)};assertEquals(time,g.world.time,0f);assertEquals(cd,g.world.heroAction.cooldown,0f);assertEquals(x,g.world.enemies[0].x,0f);assertFalse(g.postRunHubAvailable)}
 @Test fun navigatorCanReuseBuffersAcrossDifferentMaps(){val n=RegionNavigator();val out=FloatArray(2);for(m in listOf(RegionMap.all[1],RegionMap.all[0],RegionMap.all[2],RegionMap.all[0])){assertTrue(n.next(m,256,-200f,300f,0f,500f,20f,out));assertTrue(out.all{it.isFinite()})}}

 @Test fun mapDoesNotDiscloseUnvisitedChest(){val g=game();assertFalse(g.world.adventure.pin(14));lever(g);assertTrue(g.world.adventure.visibleNode(13));assertFalse(g.world.adventure.visibleNode(14));at(g,-480f,-210f);g.tick(.016f);assertTrue(g.world.adventure.visibleNode(14))}

 @Test fun interiorObjectiveGuidesWheelThenChestThenExit(){val g=game();at(g,-555f,35f);assertEquals(13,g.world.adventure.objective);assertTrue(g.interactWorld());assertEquals(14,g.world.adventure.objective);at(g,-480f,-350f);g.tick(.016f);for(i in 0 until g.world.enemyCount)g.world.enemies[i].alive=false;assertTrue(g.interactWorld());assertEquals(12,g.world.adventure.objective);at(g,-480f,205f);assertEquals("Выход на юг",g.world.adventure.actionLabel());at(g,-480f,290f);assertEquals(1,g.world.adventure.objective)}
}
