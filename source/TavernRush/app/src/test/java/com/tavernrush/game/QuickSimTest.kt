package com.tavernrush.game

import com.tavernrush.game.systems.BotPilot
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSimTest {
    @Test
    fun quick() {
        for (st in intArrayOf(BotPilot.KITE, BotPilot.CASUAL, BotPilot.PASSIVE, BotPilot.WANDER)) {
            for (seed in 1L..2L) {
                val t0 = System.nanoTime()
                val r = SimRunner.run(st, BotPilot.GREEDY, seed)
                val ms = (System.nanoTime() - t0) / 1e6
                assertTrue("Нефинитное состояние: стратегия=$st, seed=$seed", r.finite)
                assertTrue(r.time.isFinite() && r.time > 0f)
                assertTrue(r.goldCollected.isFinite() && r.goldCollected >= 0f)
                assertTrue(r.wave in 1..com.tavernrush.game.content.Content.waves.size)
                assertTrue(r.kills >= 0 && r.level >= 1)
                println("SIM ${BotPilot.strategyName(st)} seed=$seed time=${r.time.toInt()} win=${r.victory} wave=${r.wave} lvl=${r.level} kills=${r.kills} elites=${r.elites} gold=${r.goldCollected.toInt()} near=${r.nearMisses}@${r.firstNearMiss.toInt()} minHp=${"%.2f".format(r.minHp)} maxAlive=${r.maxAlive} chests=${r.chests} tav=${r.taverns} finite=${r.finite} ms=${ms.toInt()}")
                println("    W: ${r.weapons} | P: ${r.passives} | picks=${r.picks}")
                println("    dpm: " + r.damagePerMinute.take(11).joinToString(" ") { it.toInt().toString() })
            }
        }
    }
}
