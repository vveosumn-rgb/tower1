package com.tavernrush.game.content

/**
 * Таблица оружия. Чтобы добавить оружие существующего типа — допишите WeaponDef.
 * Смысл полей WeaponStats по типам:
 *  PROJECTILE: radius — хитбокс снаряда, speed — ед/с, range — дальность поиска цели и полёта, pierce — сквозь скольких.
 *  ORBIT:      radius — размер предмета, range — радиус орбиты, speed — рад/с, duration — время кружения, cooldown — пауза.
 *  AURA:       radius — радиус ауры, cooldown — период «тика» урона, duration — замедление врагов (с).
 *  CHAIN:      range — поиск первой цели, radius — дальность прыжка, chains — доп. прыжки, amount — разрядов за раз.
 *  BOOMERANG:  radius — хитбокс, speed — начальная скорость, range — дальность вылета.
 *  BOMB:       radius — радиус взрыва, range — дальность броска, speed — время полёта (с), duration — фитиль (с).
 */
object Weapons {
    const val START = "knives"

    fun all(): Array<WeaponDef> = arrayOf(
        WeaponDef(
            id = "knives", name = "Метательные ножи", desc = "Летят в ближайшего врага",
            kind = WeaponKind.PROJECTILE, icon = IconId.KNIFE, color = 0xFFE6EEF7.toInt(),
            base = WeaponStats(damage = 10f, cooldown = 0.8f, amount = 1, radius = 10f, speed = 600f, pierce = 1, range = 560f, knockback = 50f),
            levels = arrayOf(
                LevelUp("+1 нож", amount = 1),
                LevelUp("Урон +5", damage = 5f),
                LevelUp("+1 нож и пробивают ещё 1 врага", amount = 1, pierce = 1),
                LevelUp("Перезарядка −15%", cooldownPct = -0.15f),
                LevelUp("+1 нож", amount = 1),
                LevelUp("Урон +6 и пробивают ещё 1 врага", damage = 6f, pierce = 1),
            ),
            element = Element.NONE,
        ),
        WeaponDef(
            id = "mugs", name = "Пивные кружки", desc = "Кружатся вокруг героя и сбивают с ног",
            kind = WeaponKind.ORBIT, icon = IconId.MUG, color = 0xFFF2B640.toInt(),
            base = WeaponStats(damage = 8f, cooldown = 2.4f, amount = 2, radius = 20f, speed = 3.6f, range = 92f, duration = 3.6f, knockback = 140f),
            levels = arrayOf(
                LevelUp("+1 кружка", amount = 1),
                LevelUp("Площадь +25%", areaPct = 0.25f),
                LevelUp("Урон +6", damage = 6f),
                LevelUp("Кружатся на 40% дольше", durationPct = 0.4f),
                LevelUp("+1 кружка", amount = 1),
                LevelUp("Урон +7, вращение быстрее", damage = 7f, speedPct = 0.25f),
            ),
            element = Element.WATER,
        ),
        WeaponDef(
            id = "hearth", name = "Жар очага", desc = "Аура жжёт и замедляет врагов рядом",
            kind = WeaponKind.AURA, icon = IconId.FLAME, color = 0xFFFF7A2E.toInt(),
            base = WeaponStats(damage = 5f, cooldown = 0.5f, radius = 92f, duration = 0.6f, knockback = 0f),
            levels = arrayOf(
                LevelUp("Радиус +20%", areaPct = 0.2f),
                LevelUp("Урон +3", damage = 3f),
                LevelUp("Жжёт на 15% чаще", cooldownPct = -0.15f),
                LevelUp("Радиус +20%", areaPct = 0.2f),
                LevelUp("Урон +4", damage = 4f),
                LevelUp("Радиус +20% и урон +3", areaPct = 0.2f, damage = 3f),
            ),
            element = Element.FIRE,
        ),
        WeaponDef(
            id = "lightning", name = "Цепная молния", desc = "Бьёт врага и перескакивает на соседей",
            kind = WeaponKind.CHAIN, icon = IconId.LIGHTNING, color = 0xFF8FE3FF.toInt(),
            base = WeaponStats(damage = 18f, cooldown = 1.7f, amount = 1, radius = 180f, range = 480f, chains = 2, knockback = 20f),
            levels = arrayOf(
                LevelUp("+1 прыжок", chains = 1),
                LevelUp("Урон +7", damage = 7f),
                LevelUp("Перезарядка −15%", cooldownPct = -0.15f),
                LevelUp("Двойной разряд", amount = 1),
                LevelUp("+2 прыжка", chains = 2),
                LevelUp("Урон +9", damage = 9f),
            ),
            element = Element.SHOCK,
        ),
        WeaponDef(
            id = "pan", name = "Сковорода-бумеранг", desc = "Летит вперёд и возвращается, бьёт насквозь",
            kind = WeaponKind.BOOMERANG, icon = IconId.PAN, color = 0xFFB9C3CF.toInt(),
            base = WeaponStats(damage = 14f, cooldown = 1.5f, amount = 1, radius = 24f, speed = 480f, pierce = 999, range = 320f, knockback = 110f),
            levels = arrayOf(
                LevelUp("Урон +6", damage = 6f),
                LevelUp("+1 сковорода", amount = 1),
                LevelUp("Размер +25%", areaPct = 0.25f),
                LevelUp("Летит дальше и быстрее", rangePct = 0.2f, speedPct = 0.2f),
                LevelUp("+1 сковорода", amount = 1),
                LevelUp("Урон +9", damage = 9f),
            ),
            element = Element.HEAVY,
        ),
        WeaponDef(
            id = "barrel", name = "Пороховая бочка", desc = "Падает в толпу и взрывается",
            kind = WeaponKind.BOMB, icon = IconId.BARREL, color = 0xFFFFB347.toInt(),
            base = WeaponStats(damage = 28f, cooldown = 2.6f, amount = 1, radius = 95f, speed = 0.6f, range = 440f, duration = 0.35f, knockback = 180f),
            levels = arrayOf(
                LevelUp("Радиус взрыва +20%", areaPct = 0.2f),
                LevelUp("Урон +14", damage = 14f),
                LevelUp("+1 бочка", amount = 1),
                LevelUp("Перезарядка −20%", cooldownPct = -0.2f),
                LevelUp("Радиус +20% и урон +12", areaPct = 0.2f, damage = 12f),
                LevelUp("+1 бочка", amount = 1),
            ),
            element = Element.HEAVY,
        ),
    )
}
