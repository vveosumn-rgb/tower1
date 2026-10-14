package com.tavernrush.game.content

object Heroes {
    private fun bonuses(a: Stat, av: Float, b: Stat, bv: Float): FloatArray =
        FloatArray(Stat.entries.size).also { it[a.ordinal] = av; it[b.ordinal] = bv }

    fun all(): Array<HeroDef> = arrayOf(
        HeroDef("innkeeper", "Трактирщик", "knives", "Опыт +8% · здоровье +5", 0xFFFF665C.toInt(), bonuses(Stat.XP_GAIN, 0.08f, Stat.MAX_HP, 5f)),
        HeroDef("guardian", "Страж очага", "mugs", "Здоровье +20 · броня +1", 0xFF71B7E7.toInt(), bonuses(Stat.MAX_HP, 20f, Stat.ARMOR, 1f)),
        HeroDef("sage", "Чародейка", "sunbeam", "Перезарядка −8% · крит +4%", 0xFFBF8FFF.toInt(), bonuses(Stat.COOLDOWN, 0.08f, Stat.CRIT_CHANCE, 0.04f)),
        HeroDef("ranger", "Следопыт", "raven", "Скорость +10% · сбор +15%", 0xFF79D093.toInt(), bonuses(Stat.MOVE_SPEED, 0.10f, Stat.MAGNET, 0.15f)),
    )
}