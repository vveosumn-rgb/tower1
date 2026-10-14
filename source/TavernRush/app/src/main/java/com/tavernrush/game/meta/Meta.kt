package com.tavernrush.game.meta

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.HubUpgradeDef
import com.tavernrush.game.content.Stat
import com.tavernrush.game.systems.World
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Экономика мета-прогрессии: цены улучшений, бонусы, награда за забег.
 * Любой забег (даже проигранный) приносит золото: собранное + бонусы за время, убийства и волны.
 */
object Meta {
    // Предсказуемый прогресс за выживание: 6+ минут уже дают ощутимую часть покупки.
    const val TIME_BONUS_EVERY = 10f
    const val KILLS_PER_GOLD = 25
    const val GOLD_PER_WAVE = 3
    const val VICTORY_GOLD = 100
    const val BOSS_GOLD = 120
    const val REVENGE_GOLD = 60

    fun upgradeCost(def: HubUpgradeDef, level: Int): Int {
        val v = def.baseCost * def.costGrowth.pow(level)
        val r = (v / 5f).roundToInt() * 5
        return if (r < 5) 5 else r
    }

    /** Цена следующего уровня или −1, если улучшение на максимуме. */
    fun nextCost(p: Profile, i: Int): Int {
        val def = Content.hubUpgrades[i]
        val lvl = p.upgrades[i]
        return if (lvl >= def.maxLevel) -1 else upgradeCost(def, lvl)
    }

    fun canBuy(p: Profile, i: Int): Boolean {
        val c = nextCost(p, i)
        return c > 0 && p.gold >= c
    }

    fun buy(p: Profile, i: Int): Boolean {
        if (!canBuy(p, i)) return false
        p.gold -= nextCost(p, i)
        p.upgrades[i]++
        if (p.firstPurchaseRun < 0) p.firstPurchaseRun = p.runs
        return true
    }

    /** Бонусы улучшений по Stat.ordinal (в out). */
    fun bonuses(p: Profile, out: FloatArray): FloatArray {
        out.fill(0f)
        val ups = Content.hubUpgrades
        for (i in ups.indices) out[ups[i].stat.ordinal] += ups[i].perLevel * p.upgrades[i]
        return out
    }

    fun piggyMultiplier(p: Profile): Float {
        var m = 1f
        val ups = Content.hubUpgrades
        for (i in ups.indices) if (ups[i].stat == Stat.GOLD_GAIN) m += ups[i].perLevel * p.upgrades[i]
        return m
    }

    /** Самое дешёвое не максимальное улучшение (следующая цель) или −1. */
    fun nextGoal(p: Profile): Int {
        var best = -1
        var bestCost = Int.MAX_VALUE
        for (i in Content.hubUpgrades.indices) {
            val c = nextCost(p, i)
            if (c in 1 until bestCost) { bestCost = c; best = i }
        }
        return best
    }

    /** Награда за забег (не меняет профиль). */
    fun computeReward(w: World, victory: Boolean, p: Profile, out: RunResult) {
        val run = w.run
        out.valid = true
        out.applied = false
        out.victory = victory
        out.time = run.time
        out.kills = run.kills
        out.elites = run.elitesKilled
        out.level = w.player.level
        out.wave = run.wave
        out.nearMisses = run.nearMisses
        out.killerEnemy = if (victory) -1 else run.killerEnemy
        out.goldCollected = run.gold.toInt()
        out.timeBonus = (run.time / TIME_BONUS_EVERY).toInt()
        out.killBonus = run.kills / KILLS_PER_GOLD
        val cleared = if (victory) Content.waves.size else run.wave - 1
        out.waveBonus = GOLD_PER_WAVE * cleared
        out.victoryBonus = if (victory) VICTORY_GOLD else 0
        out.bossesKilled = run.bossesKilled
        out.bossBonus = run.bossesKilled * BOSS_GOLD
        out.nemesisSlain = run.nemesisSlain
        out.revengeBonus = if (run.nemesisSlain) REVENGE_GOLD + 40 * run.nemesisSlainRank else 0
        out.perfectDodges = run.perfectDodges
        out.dashes = run.dashes
        val bonusSum = out.timeBonus + out.killBonus + out.waveBonus + out.victoryBonus + out.bossBonus + out.revengeBonus
        out.piggyBonus = (bonusSum * (piggyMultiplier(p) - 1f)).roundToInt()
        out.total = out.goldCollected + bonusSum + out.piggyBonus
        out.weaponCount = w.weaponCount
        for (i in 0 until w.weaponCount) {
            out.weaponIdx[i] = w.weapons[i].def.index
            out.weaponLevel[i] = w.weapons[i].level
            out.weaponEvolution[i] = w.weapons[i].evolutionIndex
            out.weaponDamage[i] = w.weapons[i].damageDealt
        }
        out.prevBestTime = p.bestTime
        out.newBestTime = run.time.toInt() > p.bestTime && p.runs > 0
        out.newBestKills = run.kills > p.bestKills && p.runs > 0
    }

    /** Ограничения согласованы с форматом сохранения: прогресс не становится нечитаемым. */
    private fun addBounded(a: Long, b: Long, cap: Long): Long =
        (a.coerceIn(0L, cap) + b.coerceIn(0L, cap)).coerceAtMost(cap)

    fun credit(p: Profile, amount: Long) {
        p.gold = addBounded(p.gold, amount, com.tavernrush.game.save.SaveCodec.MAX_GOLD)
        p.lifetimeGold = maxOf(p.gold, addBounded(p.lifetimeGold, amount, com.tavernrush.game.save.SaveCodec.MAX_GOLD))
    }

    /** Зачислить итоги в профиль. */
    fun applyResult(p: Profile, r: RunResult) {
        if (!r.valid || r.applied) return
        r.applied = true
        credit(p, r.total.toLong())
        p.runs = (p.runs + 1).coerceAtMost(10_000_000)
        if (r.victory) p.victories = (p.victories + 1).coerceAtMost(p.runs)
        val t = (if (r.time.isFinite()) r.time else 0f).coerceIn(0f, 86400f).toInt()
        if (t > p.bestTime) p.bestTime = t
        if (r.kills > p.bestKills) p.bestKills = r.kills
        if (r.level > p.bestLevel) p.bestLevel = r.level
        if (r.wave > p.bestWave) p.bestWave = r.wave
        p.totalKills = addBounded(p.totalKills, r.kills.toLong(), Long.MAX_VALUE / 4)
        p.totalTime = addBounded(p.totalTime, t.toLong(), Long.MAX_VALUE / 4)
        p.pendingRunGold = 0
        r.goalUpgrade = nextGoal(p)
        r.goalMissing = if (r.goalUpgrade >= 0) (nextCost(p, r.goalUpgrade) - p.gold).toInt().coerceAtLeast(0) else 0
    }
}
