package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Rng
import com.tavernrush.game.meta.Profile

/**
 * Немезида: враг, убивший героя, возвращается именным мини-боссом. Имя строится из сида
 * (одно и то же имя на всех экранах), ранг растёт, если враг убьёт снова. Убийство даёт «реванш».
 * Босс, убивший героя, не становится немезидой — он гарантированно возвращается («реванш босса»).
 */
object Nemesis {
    private val FIRST = arrayOf(
        "Грызлав", "Хвостокрад", "Зубощёлк", "Пискун", "Ржавоус", "Кривоклык", "Громобой", "Булькун",
        "Мракобес", "Щипач", "Гнилозуб", "Костолом", "Лютоглаз", "Шмыг", "Дурнозуб", "Вонючка",
    )
    private val EPITHET = arrayOf("Ржавый", "Свирепый", "Кровавый", "Неумолимый", "Проклятый")

    fun name(seed: Int, rank: Int): String {
        val f = FIRST[Math.floorMod(Rng.mix(seed.toLong()).toInt(), FIRST.size)]
        val e = EPITHET[(rank - 1).coerceIn(0, EPITHET.size - 1)]
        return "$f $e"
    }

    fun seedFor(runSeed: Long, defIndex: Int): Int = Rng.mix(runSeed * 31L + defIndex * 7919L + 17L).toInt()

    /** Название вида и имя: «Грызлав Свирепый · Подвальная крыса». */
    fun fullName(defId: String, seed: Int, rank: Int): String {
        val i = Content.enemyIndex(defId)
        val kind = if (i >= 0) Content.enemies[i].name else ""
        return if (kind.isEmpty()) name(seed, rank) else name(seed, rank) + " · " + kind
    }

    /**
     * Обновляет профиль по итогам забега: убитая немезида сбрасывается (+реванш), побеждённый босс снимает
     * «реванш босса», смерть создаёт или усиливает немезиду. Смерть от босса запоминает босса для реванша.
     */
    fun onRunEnd(p: Profile, run: RunStats, victory: Boolean, waveReached: Int, runSeed: Long) {
        if (run.nemesisSlain) {
            p.nemesisId = ""; p.nemesisRank = 0
            p.revenges = (p.revenges + 1).coerceAtMost(100_000)
        }
        if (p.rematchBoss.isNotEmpty()) {
            val bi = Content.enemyIndex(p.rematchBoss)
            if (bi < 0 || (run.bossMask and (1L shl bi)) != 0L) p.rematchBoss = ""
        }
        if (!victory && run.killerEnemy >= 0 && run.killerEnemy < Content.enemies.size) {
            val def = Content.enemies[run.killerEnemy]
            if (def.boss) {
                p.rematchBoss = def.id
            } else {
                val rank = if (run.killerNamed) (run.killerRank + 1).coerceAtMost(9) else 1
                p.nemesisId = def.id
                p.nemesisRank = rank
                p.nemesisWave = waveReached.coerceIn(1, 20)
                p.nemesisSeed = if (run.killerNamed) run.killerNameSeed else seedFor(runSeed, def.index)
            }
        }
    }
}
