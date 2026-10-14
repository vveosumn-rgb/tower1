package com.tavernrush.game.content

/** Новые роли: контроль, прострел, ловушки, спутник, массовый контроль, поддержка. */
object Arsenal {
    private fun levels(noun: String): Array<LevelUp> = arrayOf(
        LevelUp("Урон +5", damage = 5f),
        LevelUp("Перезарядка −12%", cooldownPct = -0.12f),
        LevelUp("Площадь +20%", areaPct = 0.20f),
        LevelUp("+1 $noun", amount = 1),
        LevelUp("Урон +8", damage = 8f),
        LevelUp("Перезарядка −15%", cooldownPct = -0.15f),
    )

    fun all(): Array<WeaponDef> = arrayOf(
        WeaponDef("frost", "Ледяные болты", "Пробивают и замедляют врагов на 55%",
            WeaponKind.PROJECTILE, IconId.FROST, 0xFF8CE9FF.toInt(),
            WeaponStats(damage = 8f, cooldown = 1f, amount = 2, radius = 11f, speed = 460f, pierce = 2, range = 540f, slowTime = 1.1f, slowMul = 0.45f),
            levels("болт"), Element.FROST),
        WeaponDef("sunbeam", "Солнечный луч", "Прожигает всех врагов на прямой",
            WeaponKind.BEAM, IconId.SUN, 0xFFFFE487.toInt(),
            WeaponStats(damage = 20f, cooldown = 1.8f, radius = 15f, range = 560f, knockback = 25f),
            levels("луч"), Element.FIRE),
        WeaponDef("trap", "Капкан охотника", "Остаётся на пути и задерживает врага",
            WeaponKind.TRAP, IconId.TRAP, 0xFFFFBD77.toInt(),
            WeaponStats(damage = 26f, cooldown = 2.4f, radius = 28f, duration = 7f, range = 40f, slowTime = 1.5f, slowMul = 0.12f),
            levels("капкан")),
        WeaponDef("raven", "Верный ворон", "Спутник пикирует на врагов и возвращается",
            WeaponKind.SUMMON, IconId.RAVEN, 0xFFC6B8FF.toInt(),
            WeaponStats(damage = 13f, cooldown = 1.8f, radius = 15f, speed = 330f, duration = 9f, range = 520f, knockback = 35f),
            levels("ворон")),
        WeaponDef("bell", "Грозовой колокол", "Замедляет и отбрасывает цепной волной",
            WeaponKind.CHAIN, IconId.BELL, 0xFFD9C7FF.toInt(),
            WeaponStats(damage = 10f, cooldown = 2.5f, radius = 190f, range = 480f, chains = 5, knockback = 80f, slowTime = 0.65f, slowMul = 0.15f),
            levels("разряд"), Element.SHOCK),
        WeaponDef("cauldron", "Суповой котёл", "Каждое попадание ауры лечит на 0,2 ОЗ",
            WeaponKind.AURA, IconId.STEW, 0xFF8EDDA9.toInt(),
            WeaponStats(damage = 4f, cooldown = 0.8f, radius = 80f, duration = 0.3f, healOnHit = 0.2f),
            arrayOf(
                LevelUp("Урон +4", damage = 4f),
                LevelUp("Перезарядка −12%", cooldownPct = -0.12f),
                LevelUp("Площадь +20%", areaPct = 0.2f),
                LevelUp("Лечение за удар +0,1 ОЗ", healOnHit = 0.1f),
                LevelUp("Урон +6", damage = 6f),
                LevelUp("Перезарядка −15%", cooldownPct = -0.15f),
            ), Element.WATER),
    )
}