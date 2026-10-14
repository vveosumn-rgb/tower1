package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Game
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.systems.CardSystem
import com.tavernrush.game.systems.World
import org.junit.Assert.*
import org.junit.Test

class ArsenalTest {
    private val dt = 1f / 60f
    private fun world() = World().also { it.reset(202L, null); it.godMode = true }
    private fun equip(id: String) = world().also { it.weaponCount = 0; assertTrue(it.addWeapon(Content.weaponIndex(id))) }
    private fun target(w: World, x: Float = 85f, y: Float = 0f, hp: Float = 100000f): Enemy =
        w.spawnEnemy(0, x, y, false)!!.also { it.speed = 0f; it.hp = hp; it.maxHp = hp }
    private fun ready(index: Int): World {
        val e = Content.evolutions[index]
        val w = equip(Content.weapons[e.weaponIndex].id)
        repeat(w.weapons[0].def.maxLevel - 1) { assertTrue(w.upgradeWeapon(0)) }
        repeat(e.passiveLevel) { assertTrue(w.addPassive(e.passiveIndex)) }
        return w
    }

    @Test fun newContentHasTwelveWeaponsFourHeroesAndSixRecipes() {
        assertEquals(12, Content.weapons.size)
        assertEquals(14, Content.passives.size)
        assertEquals(4, Content.heroes.size)
        assertEquals(6, Content.evolutions.size)
        assertEquals(9, Content.weapons.map { it.kind }.toSet().size)
    }
    @Test fun frostBoltsDealDamage() = attack("frost")
    @Test fun sunbeamDealsDamage() = attack("sunbeam")
    @Test fun trapDealsDamage() = attack("trap")
    @Test fun ravenDealsDamage() = attack("raven")
    @Test fun bellDealsDamage() = attack("bell")
    @Test fun cauldronDealsDamage() = attack("cauldron")
    private fun attack(id: String) {
        val w = equip(id)
        val e = target(w, if (id == "trap") -85f else 70f)
        if (id == "trap") e.speed = 45f
        repeat(600) { w.tick(dt) }
        assertTrue("$id: нет реального урона", w.weapons[0].damageDealt > 0f)
        assertTrue(SimRunner.finiteWorld(w))
    }
    @Test fun beamHitsAlignedTargetsButNotEnemiesBesideIt() {
        val w = equip("sunbeam")
        val a = target(w, 90f); val b = target(w, 180f); val side = target(w, 100f, 100f)
        w.weapons[0].timer = 0f
        w.tick(dt)
        assertTrue(a.hp < a.maxHp); assertTrue(b.hp < b.maxHp); assertEquals(side.maxHp, side.hp, 0f)
    }
    @Test fun eachHeroStartsWithOwnWeaponAndTraits() {
        for (i in Content.heroes.indices) {
            val w = World().also { it.reset(203L, null, i) }
            assertEquals(i, w.heroIndex)
            assertEquals(Content.heroes[i].weaponIndex, w.weapons[0].def.index)
            assertTrue(w.player.maxHp >= 100f)
            assertTrue(w.stats.moveSpeed > 0f)
        }
    }
    @Test fun heroChoicePersistsAndCannotChangeMidRun() {
        val store = MemoryStore(); val game = Game(store)
        assertTrue(game.selectHero(3))
        assertEquals(3, Game(store).selectedHero)
        game.startRun()
        assertFalse(game.selectHero(1))
        assertEquals(3, game.world.heroIndex)
        assertFalse(game.selectHero(-1))
    }
    @Test fun unknownHeroFromFutureVersionFallsBackWithoutErasingField() {
        val store = MemoryStore()
        val profile = store.load().also { it.extra["hero"] = "future-hero" }
        store.save(profile)
        val game = Game(store)
        assertEquals(0, game.selectedHero)
        assertEquals("future-hero", game.profile.extra["hero"])
    }
    @Test fun recipesRequireMaximumWeaponAndSpecifiedPassiveLevel() {
        for (i in Content.evolutions.indices) {
            val e = Content.evolutions[i]
            val w = equip(Content.weapons[e.weaponIndex].id)
            assertFalse(w.canEvolve(i))
            repeat(w.weapons[0].def.maxLevel - 1) { w.upgradeWeapon(0) }
            assertFalse(w.canEvolve(i))
            repeat(e.passiveLevel - 1) { w.addPassive(e.passiveIndex) }
            assertFalse(w.canEvolve(i))
            w.addPassive(e.passiveIndex)
            assertTrue(w.canEvolve(i))
        }
    }
    @Test fun everyEvolutionBoostsOnceAndDoesNotStackOnRecompute() {
        for (i in Content.evolutions.indices) {
            val w = ready(i); val old = w.weapons[0].stats.damage
            assertTrue(w.evolve(i))
            assertTrue(w.weapons[0].stats.damage > old)
            val evolved = w.weapons[0].stats.damage
            repeat(10) { w.recomputeStats() }
            assertEquals(evolved, w.weapons[0].stats.damage, 0f)
            assertFalse(w.evolve(i))
            assertFalse(w.canEvolve(i))
        }
    }
    @Test fun readyEvolutionIsGuaranteedInOffers() {
        for (i in Content.evolutions.indices) {
            val w = ready(i); w.cards.roll(CardSystem.LEVEL_UP)
            assertEquals(CardSystem.EVOLVE, w.cards.offers[0].type)
            assertEquals(i, w.cards.offers[0].index)
            assertTrue(w.cards.apply(0))
            assertFalse(w.cards.apply(0))
        }
    }
    @Test fun synergyActivatesWithoutCreatingExtraSlots() {
        val w = equip("knives"); val before = w.weapons[0].stats.damage
        w.addPassive(Content.passiveIndex("fingers"))
        assertTrue(w.weapons[0].synergyActive)
        assertEquals(before * 1.12f, w.weapons[0].stats.damage, 0.00001f)
        assertEquals(1, w.weaponCount)
    }
    @Test fun rerollsChargeTenThenTwentyAndStopAtTwo() {
        val w = world(); w.run.gold = 100f; w.cards.roll(CardSystem.LEVEL_UP)
        assertTrue(w.cards.reroll()); assertEquals(90f, w.run.gold, 0f)
        assertTrue(w.cards.reroll()); assertEquals(70f, w.run.gold, 0f)
        assertFalse(w.cards.reroll()); assertEquals(70f, w.run.gold, 0f)
        assertTrue(w.cards.apply(0)); assertFalse(w.cards.reroll())
    }
    @Test fun poorHeroCannotRerollForFree() {
        val w = world(); w.run.gold = 9f; w.cards.roll(CardSystem.LEVEL_UP)
        assertFalse(w.cards.reroll()); assertEquals(9f, w.run.gold, 0f)
        assertEquals(0, w.cards.rerollsUsed)
    }
    @Test fun rerollsDoNotRerollJackpotOrCountExtraTavernVisits() {
        val w = world(); w.run.gold = 100f; w.cards.roll(CardSystem.CHEST)
        val jackpot = w.cards.jackpot
        w.cards.reroll()
        assertEquals(jackpot, w.cards.jackpot)
        w.cards.roll(CardSystem.TAVERN)
        val visits = w.run.tavernStops
        w.cards.reroll()
        assertEquals(visits, w.run.tavernStops)
    }
    @Test fun jackpotPaysExactlyOnceAfterValidChoice() {
        val w = world()
        var guard = 0
        do { w.cards.roll(CardSystem.CHEST) } while (!w.cards.jackpot && guard++ < 1000)
        assertTrue(w.cards.jackpot)
        assertFalse(w.cards.apply(-1)); assertEquals(0f, w.run.gold, 0f)
        assertTrue(w.cards.apply(0))
        val gold = w.run.gold
        assertEquals(CardSystem.JACKPOT_GOLD * w.stats.goldGain, gold, 0.0001f)
        assertFalse(w.cards.apply(0)); assertEquals(gold, w.run.gold, 0f)
        assertEquals(0.05f, CardSystem.JACKPOT_CHANCE, 0f)
    }
    @Test fun failedChoiceDoesNotConsumeOfferOrRecordPick() {
        val w = world(); w.cards.roll(CardSystem.LEVEL_UP)
        w.cards.offers[0].type = CardSystem.NEW_WEAPON
        w.cards.offers[0].index = w.weapons[0].def.index
        assertFalse(w.cards.apply(0)); assertEquals(3, w.cards.offerCount); assertEquals(0, w.run.pickCount)
    }
    @Test fun lifeStealUsesActualDamageNotOverkill() {
        val w = world(); w.player.hp = 50f; w.stats.lifeSteal = 0.02f
        val e = target(w, hp = 1f)
        w.combat.hitEnemy(e, 1000f, 0f, 0f, 0f, 0)
        assertEquals(50.02f, w.player.hp, 0.0001f)
    }
    @Test fun healingAuraActuallyRestoresHealth() {
        val w = equip("cauldron"); w.player.hp = 50f
        val e = target(w, 65f)
        w.weapons[0].timer = 0f; w.tick(dt)
        assertTrue(e.hp < e.maxHp); assertTrue(w.player.hp > 50f)
    }
    @Test fun weakerAuraDoesNotEraseStrongTrapOrFrostSlow() {
        val w = equip("hearth"); val e = target(w, 65f)
        e.slowTimer = 2f; e.slowMul = 0.12f
        w.weapons[0].timer = 0f; w.tick(dt)
        assertEquals(0.12f, e.slowMul, 0f)
        assertTrue(e.slowTimer > 1.9f)
    }
    @Test fun expiredSlowDoesNotBecomePermanent() {
        val w = world(); w.weaponCount = 0; val e = target(w, 65f)
        e.slowTimer = dt * 0.5f; e.slowMul = 0.12f
        w.tick(dt); w.tick(dt)
        assertEquals(1f, e.slowMul, 0f)
    }
    @Test fun criticalHitUsesExplicitMultiplier() {
        val w = world(); w.stats.critChance = 1f; val e = target(w, hp = 100f)
        w.combat.hitEnemy(e, 10f, 0f, 0f, 0f, 0)
        assertEquals(83.5f, e.hp, 0.00001f)
        assertEquals(16.5f, w.run.damageDealt, 0.00001f)
    }
    @Test fun dodgePreventsDamageAndInvalidDamageCannotPoisonHealth() {
        val w = world(); w.godMode = false; w.stats.dodge = 1f
        val hp = w.player.hp
        w.combat.damagePlayer(10f, null)
        assertEquals(hp, w.player.hp, 0f)
        assertTrue(w.player.invuln > 0f)
        w.player.invuln = 0f; w.stats.dodge = 0f
        w.combat.damagePlayer(Float.NaN, null)
        w.combat.damagePlayer(Float.POSITIVE_INFINITY, null)
        assertEquals(hp, w.player.hp, 0f)
    }
    @Test fun smartBotTakesReadyEvolution() {
        val w = ready(0); w.cards.roll(CardSystem.LEVEL_UP)
        val bot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 7L)
        assertEquals(0, bot.pickCard(w))
    }
    @Test fun smartBotPrefersEmergencyHealingToMorePower() {
        val w = ready(0); w.player.hp = 5f; w.cards.roll(CardSystem.LEVEL_UP)
        assertEquals(CardSystem.HEAL, w.cards.offers[0].type)
        assertEquals(1, (0 until w.cards.offerCount).count { w.cards.offers[it].type == CardSystem.HEAL })
        assertTrue((0 until w.cards.offerCount).any { w.cards.offers[it].type == CardSystem.EVOLVE })
        val bot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 7L)
        assertEquals(0, bot.pickCard(w))
    }
    @Test fun levelUpEmergencyDoesNotSpendRunGoldOnHealingRerolls() {
        val w = world(); w.player.hp = 5f; w.run.gold = 100f
        w.cards.roll(CardSystem.LEVEL_UP)
        val bot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 7L)
        assertEquals(CardSystem.HEAL, w.cards.offers[bot.pickCard(w)].type)
        assertEquals(100f, w.run.gold, 0f)
        assertEquals(0, w.cards.rerollsUsed)
    }
    @Test fun survivalAndModerateKillsFundAnEarlyPermanentUpgrade() {
        val w = world(); w.run.time = 385f; w.run.wave = 7; w.run.kills = 250; w.run.gold = 0f
        val profile = com.tavernrush.game.meta.Profile()
        val result = com.tavernrush.game.meta.RunResult()
        com.tavernrush.game.meta.Meta.computeReward(w, false, profile, result)
        assertTrue(result.total >= 60)
        w.run.time = 20f; w.run.wave = 1; w.run.kills = 18
        com.tavernrush.game.meta.Meta.computeReward(w, false, profile, result)
        assertTrue(result.total < 10)
    }
    @Test fun predictiveSteeringRemainsDeterministicAndFinite() {
        val a = world(); val b = world()
        val ba = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 9L)
        val bb = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 9L)
        repeat(600) {
            ba.steer(a); bb.steer(b)
            assertEquals(a.moveX, b.moveX, 0f); assertEquals(a.moveY, b.moveY, 0f)
            a.tick(dt); b.tick(dt)
        }
        assertTrue(SimRunner.finiteWorld(a)); assertTrue(SimRunner.finiteWorld(b))
    }
}