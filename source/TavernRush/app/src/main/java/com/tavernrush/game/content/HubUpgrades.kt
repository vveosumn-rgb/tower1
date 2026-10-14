package com.tavernrush.game.content

/**
 * Постоянные улучшения таверны (каркас гринда, R05 расширит до зданий).
 * Цена уровня L (0..max-1) = baseCost × costGrowth^L, округление до 5.
 */
object HubUpgrades {
    fun all(): Array<HubUpgradeDef> = arrayOf(
        HubUpgradeDef("supper", "Сытный ужин", "Здоровье +10", IconId.HEART, Stat.MAX_HP, 10f, 5, baseCost = 60, costGrowth = 1.6f),
        HubUpgradeDef("whetstone", "Точильный камень", "Урон +6%", IconId.WHETSTONE, Stat.MIGHT, 0.06f, 5, baseCost = 90, costGrowth = 1.65f),
        HubUpgradeDef("boots", "Удобные сапоги", "Скорость бега +5%", IconId.BOOT, Stat.MOVE_SPEED, 0.05f, 5, baseCost = 75, costGrowth = 1.6f),
        HubUpgradeDef("piggy", "Копилка трактирщика", "Золото +10%", IconId.PIGGY, Stat.GOLD_GAIN, 0.10f, 5, baseCost = 120, costGrowth = 1.7f),
    )
}
