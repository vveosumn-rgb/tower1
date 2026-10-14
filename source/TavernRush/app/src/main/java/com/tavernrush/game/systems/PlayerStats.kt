package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Stat

/**
 * Итоговые параметры героя: база × (1 + бонусы пассивок, блюд таверны и улучшений хаба).
 * Пересчитывается только при изменении билда (не каждый кадр).
 */
class PlayerStats {
    val bonus = FloatArray(Content.statCount)
    var might = 1f
    var area = 1f
    var cooldown = 1f
    var amount = 0
    var duration = 1f
    var projSpeed = 1f
    var moveSpeed = 1f
    var maxHpAdd = 0f
    var regen = 0f
    var armor = 0f
    var magnet = 1f
    var xpGain = 1f
    var goldGain = 1f
    var critChance = 0f
    var lifeSteal = 0f
    var dodge = 0f

    fun recompute(passiveLevels: IntArray, dishBonus: FloatArray, metaBonus: FloatArray, heroBonus: FloatArray? = null) {
        for (i in bonus.indices) bonus[i] = dishBonus[i] + metaBonus[i] + (heroBonus?.get(i) ?: 0f)
        val ps = Content.passives
        for (i in ps.indices) {
            val lvl = passiveLevels[i]
            if (lvl > 0) bonus[ps[i].stat.ordinal] += ps[i].perLevel * lvl
        }
        might = 1f + bonus[Stat.MIGHT.ordinal]
        area = 1f + bonus[Stat.AREA.ordinal]
        cooldown = maxOf(MIN_COOLDOWN, 1f - bonus[Stat.COOLDOWN.ordinal])
        amount = bonus[Stat.AMOUNT.ordinal].toInt()
        duration = 1f + bonus[Stat.DURATION.ordinal]
        projSpeed = 1f + bonus[Stat.PROJ_SPEED.ordinal]
        moveSpeed = 1f + bonus[Stat.MOVE_SPEED.ordinal]
        maxHpAdd = bonus[Stat.MAX_HP.ordinal]
        regen = bonus[Stat.REGEN.ordinal]
        armor = bonus[Stat.ARMOR.ordinal]
        magnet = 1f + bonus[Stat.MAGNET.ordinal]
        xpGain = 1f + bonus[Stat.XP_GAIN.ordinal]
        goldGain = 1f + bonus[Stat.GOLD_GAIN.ordinal]
        critChance = bonus[Stat.CRIT_CHANCE.ordinal].coerceIn(0f, 0.45f)
        lifeSteal = bonus[Stat.LIFE_STEAL.ordinal].coerceIn(0f, 0.02f)
        dodge = bonus[Stat.DODGE.ordinal].coerceIn(0f, 0.25f)
    }

    companion object {
        /** Перезарядка не быстрее, чем ×0,35 от базовой. */
        const val MIN_COOLDOWN = 0.35f
    }
}
