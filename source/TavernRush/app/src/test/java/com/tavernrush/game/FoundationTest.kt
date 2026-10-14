package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.meta.Meta
import com.tavernrush.game.meta.Profile
import com.tavernrush.game.meta.RunResult
import com.tavernrush.game.save.*
import com.tavernrush.game.systems.CardSystem
import com.tavernrush.game.systems.World
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Регрессии восстановления: бой, детерминизм, прогресс и повреждённые сохранения. */
class FoundationTest {
    @get:Rule val temp = TemporaryFolder()
    private val dt = 1f / 60f
    private fun fresh() = World().also { it.reset(42L, null) }
    private fun profile() = Profile().also { it.gold = 40; it.lifetimeGold = 40; it.extra["future.quest"] = "7" }

    @Test fun rngIsReproducible() {
        val a = Rng(987L); val b = Rng(987L)
        repeat(1000) { assertEquals(a.nextLong(), b.nextLong()) }
    }
    @Test fun rngStreamsAreDistinct() { assertNotEquals(Rng.stream(9L, 1), Rng.stream(9L, 2)) }
    @Test fun contentHasNineDistinctAttackKinds() {
        assertEquals(9, Content.weapons.map { it.kind }.toSet().size)
        for (w in Content.weapons) { assertTrue(w.base.damage > 0); assertTrue(w.base.cooldown > 0); assertEquals(w.maxLevel, w.statsByLevel.size) }
    }
    @Test fun stationaryEnemyActuallyDamagesPlayer() {
        val w = fresh(); w.weaponCount = 0
        val e = w.spawnEnemy(Content.enemyIndex("rat"), 0f, 0f, false)!!
        e.x = w.player.radius + e.radius + 2f
        val hp = w.player.hp
        w.tick(dt)
        assertTrue("Контакт не должен давать вечную неуязвимость", w.player.hp < hp)
    }
    @Test fun damageRespectsInvulnerabilityWindow() {
        val w = fresh(); w.weaponCount = 0
        w.spawnEnemy(Content.enemyIndex("rat"), 30f, 0f, false)
        w.tick(dt); val hp = w.player.hp
        repeat(5) { w.tick(dt) }
        assertEquals(hp, w.player.hp, 0f)
    }
    @Test fun invalidTimeIsIgnored() {
        val w = fresh()
        for (v in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 10f)) w.tick(v)
        assertEquals(0f, w.time, 0f); assertEquals(0, w.ticks)
    }
    @Test fun invalidMovementCannotPoisonTheWorld() {
        val w = fresh(); w.moveX = Float.NaN; w.moveY = Float.POSITIVE_INFINITY; w.tick(dt)
        assertTrue(w.player.x.isFinite()); assertTrue(w.player.y.isFinite()); assertTrue(w.player.faceX.isFinite())
    }
    @Test fun invalidXpCannotCreateInfiniteLevels() {
        val w = fresh(); w.addXp(Float.POSITIVE_INFINITY); w.addXp(Float.NaN); w.addXp(-10f)
        assertEquals(1, w.player.level); assertEquals(0, w.pendingLevelUps)
    }
    @Test fun invalidContentIndexesAreRejected() {
        val w = fresh(); assertFalse(w.addWeapon(-1)); assertFalse(w.addWeapon(Int.MAX_VALUE))
        assertFalse(w.addPassive(-1)); assertFalse(w.addPassive(Int.MAX_VALUE))
    }
    @Test fun cardsAreDistinctAndCannotBeAppliedTwice() {
        val w = fresh(); w.cards.roll(CardSystem.LEVEL_UP)
        val keys = (0 until w.cards.offerCount).map { w.cards.offers[it].type to w.cards.offers[it].index }
        assertEquals(keys.size, keys.toSet().size)
        assertFalse(w.cards.apply(-1)); assertTrue(w.cards.apply(0)); assertFalse(w.cards.apply(0))
    }
    @Test fun pauseFreezesTheRunAndResumesIt() {
        val g = Game(); g.startRun(); repeat(10) { g.tick(dt) }; val t = g.world.time
        g.pause(); assertEquals(ScreenId.PAUSE, g.screen); repeat(60) { g.tick(dt) }
        assertEquals(t, g.world.time, 0f); g.resume(); assertEquals(ScreenId.RUN, g.screen); g.tick(dt); assertTrue(g.world.time > t)
    }
    @Test fun codecRoundTripPreservesProgressAndUnknownFields() {
        val p = profile(); p.upgrades[0] = 1
        val copy = SaveCodec.decode(SaveCodec.encode(p))
        assertEquals(p.gold, copy.gold); assertEquals(1, copy.upgrades[0]); assertEquals("7", copy.extra["future.quest"])
    }
    @Test fun tamperedGoldIsRejected() {
        val data = SaveCodec.encode(profile()).replace("gold=40\n", "gold=999\n")
        assertThrows(SaveCodec.Invalid::class.java) { SaveCodec.decode(data) }
    }
    @Test fun signedButInvalidValuesAreRejected() {
        val p = profile(); p.gold = -1
        assertThrows(SaveCodec.Invalid::class.java) { SaveCodec.decode(SaveCodec.encode(p)) }
    }
    @Test fun duplicateSignedKeysAreRejected() {
        val body = SaveCodec.encode(profile()).substringBefore("sig=") + "gold=20\n"
        assertThrows(SaveCodec.Invalid::class.java) { SaveCodec.decode(body + "sig=" + Signer.sign(body) + "\n") }
    }
    @Test fun extraFieldsCannotOverrideCurrency() {
        val p = profile(); p.extra["gold"] = "999"
        assertThrows(SaveCodec.Invalid::class.java) { SaveCodec.encode(p) }
    }
    @Test fun backupRestoresAfterCorruption() {
        val dir = temp.newFolder(); val s = SaveManager(dir); val p = profile()
        assertTrue(s.save(p)); p.gold = 80; p.lifetimeGold = 80; assertTrue(s.save(p))
        File(dir, "profile.sav").writeText("corrupted")
        val recovered = SaveManager(dir); val restored = recovered.load()
        assertEquals(40L, restored.gold); assertEquals(SaveStatus.RESTORED, recovered.status)
        assertEquals(40L, SaveManager(dir).load().gold)
    }
    @Test fun interruptedWriteRecoversNewestSignedTemporaryFile() {
        val dir = temp.newFolder(); val p = profile(); val s = SaveManager(dir); assertTrue(s.save(p))
        p.seq++; p.gold = 90; p.lifetimeGold = 90
        File(dir, "profile.sav.tmp").writeText(SaveCodec.encode(p))
        assertEquals(90L, SaveManager(dir).load().gold)
    }
    @Test fun failedSaveDoesNotAdvanceSequence() {
        val blocked = temp.newFile(); val p = profile(); val seq = p.seq
        assertFalse(SaveManager(blocked).save(p)); assertEquals(seq, p.seq)
    }
    @Test fun rewardCanOnlyBeCreditedOnce() {
        val p = Profile(); val r = RunResult(); r.valid = true; r.total = 25; r.time = 60f
        Meta.applyResult(p, r); Meta.applyResult(p, r)
        assertEquals(25L, p.gold); assertEquals(1, p.runs)
    }
    @Test fun currencyLimitNeverMakesProgressUnreadable() {
        val p = Profile(); p.gold = SaveCodec.MAX_GOLD - 2; p.lifetimeGold = p.gold
        Meta.credit(p, 500); assertEquals(SaveCodec.MAX_GOLD, p.gold)
        assertEquals(p.gold, SaveCodec.decode(SaveCodec.encode(p)).gold)
    }
    @Test fun pendingProgressIsBankedOnlyOnceAfterRestart() {
        val store = MemoryStore(); val g = Game(store); g.startRun(); g.world.run.gold = 15f; g.onHostPause()
        val restarted = Game(store); val gold = restarted.profile.gold
        assertTrue(gold > 0); assertEquals(gold, Game(store).profile.gold)
    }
    @Test fun lowHealthCannotContinuouslyFarmNearMissGold() {
        val w = fresh(); w.godMode = true; w.weaponCount = 0
        w.player.hp = w.player.maxHp * 0.2f
        repeat(1800) { w.tick(dt) }
        assertEquals("Один непрерывный эпизод опасности — одна награда", 1, w.run.nearMisses)
        w.player.hp = w.player.maxHp
        w.tick(dt)
        w.player.hp = w.player.maxHp * 0.2f
        repeat(600) { w.tick(dt) }
        assertEquals(2, w.run.nearMisses)
    }
    @Test fun failedRecoveryKeepsNewestValidTemporarySave() {
        val dir = temp.newFolder(); val s = SaveManager(dir); val p = profile()
        assertTrue(s.save(p))
        p.seq++; p.gold = 90; p.lifetimeGold = 90
        val temporary = File(dir, "profile.sav.tmp")
        temporary.writeText(SaveCodec.encode(p))
        // Непустая директория на месте bak моделирует ошибку rename/copy при восстановлении.
        val blocker = File(dir, "profile.sav.bak").apply { mkdir() }
        File(blocker, "block").writeText("keep")
        assertEquals(90L, SaveManager(dir).load().gold)
        assertTrue("Единственная свежая копия не должна удаляться", temporary.isFile)
        assertEquals(90L, SaveManager(dir).load().gold)
    }
}
