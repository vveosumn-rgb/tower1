package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.save.SaveCodec
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.systems.World
import org.junit.Assert.*
import org.junit.Test

/** Проверяем реальные эффекты атак и конец забега, а не только наличие строк в таблицах. */
class GameplayTest {
    private val dt = 1f / 60f

    @Test fun knivesDealDamage() = assertWeapon("knives")
    @Test fun mugsDealDamage() = assertWeapon("mugs")
    @Test fun hearthDealsDamage() = assertWeapon("hearth")
    @Test fun lightningDealsDamage() = assertWeapon("lightning")
    @Test fun panDealsDamage() = assertWeapon("pan")
    @Test fun barrelDealsDamage() = assertWeapon("barrel")

    private fun assertWeapon(id: String) {
        val w = World().also { it.reset(77L, null); it.godMode = true; it.weaponCount = 0 }
        assertTrue(w.addWeapon(Content.weaponIndex(id)))
        val target = w.spawnEnemy(Content.enemyIndex("rat"), 85f, 0f, true)!!
        target.speed = 0f
        target.hp = 100000f
        target.maxHp = target.hp
        repeat(480) { w.tick(dt) }
        assertTrue("$id должен попадать и учитывать урон", w.weapons[0].damageDealt > 0f)
        assertTrue(SimRunner.finiteWorld(w))
    }

    @Test fun everyWeaponCanReachItsMaximumWithoutInvalidStats() {
        for (def in Content.weapons) {
            val w = World().also { it.reset(78L, null); it.weaponCount = 0 }
            assertTrue(w.addWeapon(def.index))
            repeat(def.maxLevel - 1) { assertTrue(w.upgradeWeapon(0)) }
            assertFalse(w.upgradeWeapon(0))
            val s = w.weapons[0].stats
            assertTrue(s.damage.isFinite() && s.damage > 0f)
            assertTrue(s.cooldown.isFinite() && s.cooldown > 0f)
            assertTrue(s.radius.isFinite() && s.radius > 0f)
            assertTrue(s.amount > 0)
        }
    }

    @Test fun entityPoolsRejectOverflow() {
        val w = World().also { it.reset(79L, null) }
        repeat(World.MAX_ENEMIES) { assertNotNull(w.spawnEnemy(0, 80f + it, 100f, false)) }
        assertNull(w.spawnEnemy(0, 80f, 100f, false))
        repeat(World.MAX_PROJECTILES) { assertNotNull(w.spawnProjectile(Projectile.KNIFE, 0, 0f, 0f)) }
        assertNull(w.spawnProjectile(Projectile.KNIFE, 0, 0f, 0f))
        w.godMode = true
        repeat(60) { w.tick(dt) }
        assertTrue(SimRunner.finiteWorld(w))
        assertTrue(w.enemyCount <= World.MAX_ENEMIES)
        assertTrue(w.projectileCount <= World.MAX_PROJECTILES)
    }

    @Test(timeout = 120000) fun tenWavesReachVictoryAndPersistRewardExactlyOnce() {
        val store = MemoryStore()
        val game = Game(store).also { it.fixedSeed = 80L; it.startRun(); it.world.godMode = true }
        val bot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 800L)
        var guard = 0
        // 10 волн по 60 с + финальный босс держит рассвет не дольше 100 с (World: WaveDirector.FINAL_BOSS_OVERTIME)
        while (game.screen != ScreenId.RESULTS && guard++ < 46000) {
            if (game.screen == ScreenId.CARD_PICK) game.pickCard(bot.pickCard(game.world))
            else { bot.steer(game.world); game.tick(dt) }
        }
        assertEquals(ScreenId.RESULTS, game.screen)
        assertTrue(game.result.victory)
        assertEquals(Content.waves.size, game.result.wave)
        assertTrue("боссы 5-й и 10-й волн появились", game.world.run.bossesSeen >= 2)
        assertEquals(1, game.profile.victories)
        assertTrue(game.result.total > 0)
        val gold = game.profile.gold
        repeat(120) { game.tick(dt) }
        assertEquals(gold, game.profile.gold)
        assertEquals(gold, SaveCodec.decode(store.raw()!!).gold)
        assertEquals(1, Game(store).profile.victories)
        game.back()
        assertEquals(ScreenId.HUB, game.screen)
    }
}