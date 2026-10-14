package com.tavernrush.game
import com.tavernrush.game.core.*
import com.tavernrush.game.render.WallArt
import com.tavernrush.game.systems.RegionMap
import com.tavernrush.game.save.*
import com.tavernrush.game.meta.Profile
import org.junit.Assert.*
import org.junit.Test
class World17Test {
 private fun ended():Game=Game().also{it.startExpedition();it.abandonRun()}
 @Test fun freshGameCannotEnterTavern(){val g=Game();g.openMenu();g.openHub();assertEquals(ScreenId.MENU,g.screen);assertFalse(g.postRunHubAvailable)}
 @Test fun directScreenJumpCannotBypassTavernGate(){val g=Game();g.go(ScreenId.HUB);assertEquals(ScreenId.SPLASH,g.screen)}
 @Test fun lifetimeRunsDoNotGrantCurrentWindow(){val s=MemoryStore();val p=Profile();p.runs=30;s.save(p);val g=Game(s);g.openMenu();g.openHub();assertEquals(ScreenId.MENU,g.screen)}
 @Test fun expeditionFinishOpensPostMatchWindow(){val g=ended();assertEquals(ScreenId.JOURNEY,g.screen);assertTrue(g.postRunHubAvailable);g.openHub();assertEquals(ScreenId.HUB,g.screen)}
 @Test fun arenaFinishOpensPostMatchWindow(){val g=Game();g.startRun();g.abandonRun();assertEquals(ScreenId.RESULTS,g.screen);g.openHub();assertEquals(ScreenId.HUB,g.screen)}
 @Test fun nextExpeditionClosesWindow(){val g=ended();g.openHub();g.startExpedition();assertFalse(g.postRunHubAvailable);g.openHub();assertEquals(ScreenId.RUN,g.screen)}
 @Test fun nextArenaClosesWindow(){val g=ended();g.startRun();g.pause();g.openHub();assertFalse(g.postRunHubAvailable);assertEquals(ScreenId.PAUSE,g.screen)}
 @Test fun pauseDoesNotFinishMatch(){val g=Game();g.startExpedition();g.pause();g.openHub();assertEquals(ScreenId.PAUSE,g.screen);assertFalse(g.postRunHubAvailable)}
 @Test fun routeMapCannotOpenTavern(){val g=Game();g.startExpedition();g.pause();g.openRouteMap();g.openHub();assertEquals(ScreenId.ROUTE_MAP,g.screen);assertFalse(g.postRunHubAvailable)}
 @Test fun returnToMenuDoesNotErasePostMatchAccess(){val g=ended();g.openHub();g.openMenu();g.openHub();assertEquals(ScreenId.HUB,g.screen)}
 @Test fun shopCannotBuyBeforeMatchEvenWithGold(){val g=Game();com.tavernrush.game.meta.Meta.credit(g.profile,1000L);assertFalse(g.buyUpgrade(0));assertEquals(1000L,g.profile.gold);assertEquals(0,g.profile.upgrades[0])}
 @Test fun shopCannotBuyDuringRun(){val g=Game();com.tavernrush.game.meta.Meta.credit(g.profile,1000L);g.startExpedition();assertFalse(g.buyUpgrade(0));assertEquals(1000L,g.profile.gold)}
 @Test fun postMatchPurchaseRequiresActualHub(){val g=ended();com.tavernrush.game.meta.Meta.credit(g.profile,1000L);assertFalse(g.buyUpgrade(0));g.openHub();assertTrue(g.buyUpgrade(0));assertEquals(1,g.profile.upgrades[0])}
 @Test fun restartKeepsProgressNotStaleWindow(){val s=MemoryStore();val g=Game(s);com.tavernrush.game.meta.Meta.credit(g.profile,1000L);g.startExpedition();g.abandonRun();g.openHub();assertTrue(g.buyUpgrade(0));val next=Game(s);next.openMenu();next.openHub();assertEquals(ScreenId.MENU,next.screen);assertEquals(1,next.profile.upgrades[0]);assertEquals(g.profile.gold,next.profile.gold)}
 @Test fun backgroundIsNotMatchFinish(){val g=Game();g.startExpedition();g.onHostPause();assertFalse(g.postRunHubAvailable);g.openHub();assertEquals(ScreenId.PAUSE,g.screen)}
 @Test fun invalidExpeditionDoesNotConsumeEarnedWindow(){val g=ended();g.startExpedition(99);assertTrue(g.postRunHubAvailable);assertEquals(ScreenId.JOURNEY,g.screen)}
 @Test fun invalidPurchaseDoesNotSpendAnything(){val g=ended();g.openHub();com.tavernrush.game.meta.Meta.credit(g.profile,1000L);assertFalse(g.buyUpgrade(-1));assertFalse(g.buyUpgrade(999));assertEquals(1000L,g.profile.gold)}
 @Test fun repeatedFinishDoesNotAddRunOrGold(){val g=ended();val runs=g.profile.runs;val gold=g.profile.gold;g.abandonRun();assertEquals(runs,g.profile.runs);assertEquals(gold,g.profile.gold)}
 @Test fun abandoningOrdinaryRunDoesNotPretendVictory(){val g=Game();g.startRun();g.abandonRun();assertFalse(g.result.victory);assertTrue(g.postRunHubAvailable)}
 @Test fun wallTilesExactlyCoverCollisionFootprint(){val box=FloatArray(4);for(m in RegionMap.all)for(w in m.walls){var area=0f;for(i in 0 until WallArt.count(w)){WallArt.bounds(w,i,box);assertTrue(box[0]>=w.x-w.hx-.01f);assertTrue(box[2]<=w.x+w.hx+.01f);assertTrue(box[1]>=w.y-w.hy-.01f);assertTrue(box[3]<=w.y+w.hy+.01f);area+=(box[2]-box[0])*(box[3]-box[1])};assertEquals(4*w.hx*w.hy,area,.1f)}}
 @Test fun wallTilesFitDepthEncoding(){for(m in RegionMap.all)for(w in m.walls)assertTrue(WallArt.count(w) in 1 until WallArt.STRIDE)}
 @Test fun topProjectionDoesNotInventPhysicalCollision(){val m=RegionMap.all[0];assertFalse(m.blocked(0f,60f,20f,0));assertTrue(WallArt.hidesHero(-85f,47.5f,85f,72.5f,0f,30f))}
 @Test fun frontHeroDoesNotFadeWall(){assertFalse(WallArt.hidesHero(-85f,47.5f,85f,72.5f,0f,100f));assertFalse(WallArt.hidesHero(-85f,47.5f,85f,72.5f,200f,60f))}
 @Test fun openingGateStillRemovesSameFootprint(){val m=RegionMap.all[0];assertTrue(m.blocked(0f,120f,20f,0));assertFalse(m.blocked(0f,120f,20f,2));assertFalse(m.enabled(0,2))}
 @Test fun materialLayoutHasNoGameplayRandomness(){val w=RegionMap.all[2].walls[1];val a=FloatArray(4);val b=FloatArray(4);repeat(30){WallArt.bounds(w,it%WallArt.count(w),a);WallArt.bounds(w,it%WallArt.count(w),b);assertArrayEquals(a,b,0f)}}
}
