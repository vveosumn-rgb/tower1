package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.systems.CardSystem
import com.tavernrush.game.systems.World

/** Итог одного бот-забега. */
class SimResult(
    val strategy: Int,
    val cards: Int,
    val seed: Long,
    val time: Float,
    val victory: Boolean,
    val wave: Int,
    val level: Int,
    val kills: Int,
    val elites: Int,
    val goldCollected: Float,
    val nearMisses: Int,
    val firstNearMiss: Float,
    val minHp: Float,
    val maxAlive: Int,
    val damagePerMinute: FloatArray,
    val killsPerMinute: IntArray,
    val weapons: String,
    val passives: String,
    val chests: Int,
    val taverns: Int,
    val picks: Int,
    val finite: Boolean,
)

/**
 * Прогон забега ботом на чистом World (без Android): та же очередь карт, что и в Game
 * (привал > сундук > уровень). Используется SimulationTest и стресс-тестами.
 */
object SimRunner {
    const val DT = 1f / 60f

    fun run(strategy: Int, cards: Int, seed: Long, meta: FloatArray? = null, maxTime: Float = Content.runDuration + 5f, world: World = World()): SimResult {
        val w = world
        w.reset(seed, meta)
        val bot = BotPilot(strategy, cards, seed * 31 + 7)
        var firstNear = -1f
        var finite = true
        val maxTicks = (maxTime / DT).toInt()
        var t = 0
        while (t < maxTicks) {
            bot.steer(w)
            w.tick(DT)
            t++
            if (firstNear < 0f && w.run.nearMisses > 0) firstNear = w.time
            if (w.player.dead || w.victory) break
            drainCards(w, bot)
            if ((t and 255) == 0 && !finiteWorld(w)) { finite = false; break }
        }
        val sbW = StringBuilder()
        for (i in 0 until w.weaponCount) {
            if (i > 0) sbW.append(", ")
            sbW.append(w.weapons[i].def.id).append(' ').append(w.weapons[i].level)
        }
        val sbP = StringBuilder()
        for (i in 0 until w.passiveCount) {
            if (i > 0) sbP.append(", ")
            val idx = w.passiveOrder[i]
            sbP.append(Content.passives[idx].id).append(' ').append(w.passiveLevels[idx])
        }
        return SimResult(
            strategy, cards, seed, w.time, w.victory, w.run.wave, w.player.level, w.run.kills, w.run.elitesKilled,
            w.run.gold, w.run.nearMisses, firstNear, w.run.minHpFrac, w.run.maxAlive,
            w.run.damagePerMinute.copyOf(), w.run.killsPerMinute.copyOf(), sbW.toString(), sbP.toString(),
            w.run.chests, w.run.tavernStops, w.run.pickCount, finite && finiteWorld(w),
        )
    }

    fun drainCards(w: World, bot: BotPilot) {
        var guard = 0
        while (guard++ < 50) {
            val reason = when {
                w.pendingTavern > 0 -> { w.pendingTavern--; CardSystem.TAVERN }
                w.pendingChests > 0 -> { w.pendingChests--; CardSystem.CHEST }
                w.pendingLevelUps > 0 -> { w.pendingLevelUps--; CardSystem.LEVEL_UP }
                else -> return
            }
            w.cards.roll(reason)
            w.cards.apply(bot.pickCard(w))
        }
    }

    fun finiteWorld(w: World): Boolean {
        val p = w.player
        if (!p.x.isFinite() || !p.y.isFinite() || !p.hp.isFinite()) return false
        for (i in 0 until w.enemyCount) {
            val e = w.enemies[i]
            if (!e.x.isFinite() || !e.y.isFinite() || !e.hp.isFinite()) return false
        }
        for (i in 0 until w.projectileCount) {
            val q = w.projectiles[i]
            if (!q.x.isFinite() || !q.y.isFinite()) return false
        }
        return true
    }
}
