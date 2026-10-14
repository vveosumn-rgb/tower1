package com.tavernrush.game.content

/** Пассивки забега. Эффект складывается: perLevel × уровень. */
object Passives {
    fun all(): Array<PassiveDef> = arrayOf(
        PassiveDef("ale", "Крепкий эль", "Урон +10%", IconId.ALE, Stat.MIGHT, 0.10f, 5, 0xFFE0A030.toInt()),
        PassiveDef("fingers", "Ловкие пальцы", "Перезарядка −8%", IconId.FINGERS, Stat.COOLDOWN, 0.08f, 5, 0xFF7FD3FF.toInt()),
        PassiveDef("boots", "Лёгкие сапоги", "Скорость бега +10%", IconId.BOOT, Stat.MOVE_SPEED, 0.10f, 5, 0xFF9CE07A.toInt()),
        PassiveDef("gesture", "Широкий жест", "Площадь атак +10%", IconId.AREA, Stat.AREA, 0.10f, 5, 0xFFFF9F5A.toInt()),
        PassiveDef("stew", "Сытная похлёбка", "Здоровье +20", IconId.STEW, Stat.MAX_HP, 20f, 5, 0xFFFF6B6B.toInt()),
        PassiveDef("tea", "Травяной чай", "Лечение +0,3 ОЗ/с", IconId.TEA, Stat.REGEN, 0.3f, 5, 0xFF6FD68C.toInt()),
        PassiveDef("shield", "Дубовый щит", "Броня +1", IconId.SHIELD, Stat.ARMOR, 1f, 5, 0xFFC08A55.toInt()),
        PassiveDef("horseshoe", "Подкова-магнит", "Радиус сбора +30%", IconId.HORSESHOE, Stat.MAGNET, 0.30f, 5, 0xFFE05A5A.toInt()),
        PassiveDef("book", "Книга рецептов", "Опыт +8%", IconId.BOOK, Stat.XP_GAIN, 0.08f, 5, 0xFF8FA8FF.toInt()),
        PassiveDef("double", "Двойная порция", "+1 снаряд всему оружию", IconId.DOUBLE, Stat.AMOUNT, 1f, 2, 0xFFFFE066.toInt()),
        PassiveDef("coin", "Счастливая монета", "Золото +15%", IconId.LUCKY_COIN, Stat.GOLD_GAIN, 0.15f, 5, 0xFFFFD24A.toInt()),
        PassiveDef("crit", "Острый взгляд", "Шанс критического урона ×1,65: +4%", IconId.CRIT, Stat.CRIT_CHANCE, 0.04f, 5, 0xFFFFA086.toInt()),
        PassiveDef("vamp", "Рунный кубок", "Возвращает 0,2% нанесённого урона в ОЗ", IconId.VAMP, Stat.LIFE_STEAL, 0.002f, 5, 0xFFE69AC5.toInt()),
        PassiveDef("feather", "Перо удачи", "Шанс уклониться от урона +2%", IconId.FEATHER, Stat.DODGE, 0.02f, 5, 0xFF9ECFFF.toInt()),
    )
}
