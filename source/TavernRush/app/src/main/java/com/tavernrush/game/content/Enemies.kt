package com.tavernrush.game.content

/**
 * Враги. Первые пять — прежние (индексы не менять: сохранения и тесты опираются на id, а рендер — на look).
 * R03 добавляет роли: стрелки, кабан-таран, подрывник, шаман, призрак, паук, а также боссов.
 * Элитная версия любого врага создаётся модификатором (World.ELITE_*) и получает случайный аффикс.
 */
object Enemies {
    fun all(): Array<EnemyDef> = arrayOf(
        EnemyDef("rat", "Подвальная крыса", EnemyLook.RAT, EnemyBehavior.CHASE,
            hp = 10f, speed = 100f, damage = 8f, radius = 15f, xp = 1, goldChance = 0.03f, knockbackResist = 0f, color = 0xFF9A8478.toInt()),
        EnemyDef("goblin", "Гоблин-воришка", EnemyLook.GOBLIN, EnemyBehavior.CHASE,
            hp = 24f, speed = 84f, damage = 12f, radius = 18f, xp = 2, goldChance = 0.06f, knockbackResist = 0.1f, color = 0xFF6DBE45.toInt()),
        EnemyDef("bat", "Летучая мышь", EnemyLook.BAT, EnemyBehavior.WAVY,
            hp = 12f, speed = 135f, damage = 9f, radius = 14f, xp = 1, goldChance = 0.03f, knockbackResist = 0f, color = 0xFF9B6BD6.toInt()),
        EnemyDef("slime", "Колодезный слизень", EnemyLook.SLIME, EnemyBehavior.CHASE,
            hp = 55f, speed = 58f, damage = 16f, radius = 22f, xp = 3, goldChance = 0.07f, knockbackResist = 0.35f, color = 0xFF4FD1A5.toInt(),
            splitId = "slimelet", splitCount = 2),
        EnemyDef("orc", "Орк-громила", EnemyLook.ORC, EnemyBehavior.CHASE,
            hp = 200f, speed = 66f, damage = 22f, radius = 30f, xp = 10, goldChance = 0.35f, knockbackResist = 0.7f, color = 0xFF7F9E4A.toInt()),

        EnemyDef("archer", "Гоблин-лучник", EnemyLook.ARCHER, EnemyBehavior.SHOOTER,
            hp = 22f, speed = 72f, damage = 9f, radius = 16f, xp = 3, goldChance = 0.07f, knockbackResist = 0.05f, color = 0xFF8FCB57.toInt(),
            range = 300f, cooldown = 2.6f, shotSpeed = 330f, shotDamage = 11f),
        EnemyDef("boar", "Дикий кабан", EnemyLook.BOAR, EnemyBehavior.CHARGER,
            hp = 80f, speed = 78f, damage = 18f, radius = 25f, xp = 5, goldChance = 0.12f, knockbackResist = 0.45f, color = 0xFFB07A55.toInt(),
            range = 340f, cooldown = 4.2f, special = 430f),
        EnemyDef("bomber", "Гоблин-подрывник", EnemyLook.BOMBER, EnemyBehavior.BOMBER,
            hp = 14f, speed = 122f, damage = 0f, radius = 15f, xp = 2, goldChance = 0.06f, knockbackResist = 0f, color = 0xFFE0A030.toInt(),
            range = 92f, cooldown = 1.0f, shotDamage = 30f, special = 86f),
        EnemyDef("shaman", "Гоблин-шаман", EnemyLook.SHAMAN, EnemyBehavior.SUMMONER,
            hp = 64f, speed = 56f, damage = 6f, radius = 18f, xp = 6, goldChance = 0.25f, knockbackResist = 0.2f, color = 0xFF9B5BD6.toInt(),
            range = 340f, cooldown = 6.5f, special = 3f, summonId = "rat"),
        EnemyDef("ghost", "Призрак должника", EnemyLook.GHOST, EnemyBehavior.BLINKER,
            hp = 40f, speed = 92f, damage = 13f, radius = 18f, xp = 4, goldChance = 0.1f, knockbackResist = 0f, color = 0xFFB8D8FF.toInt(),
            cooldown = 5.5f, special = 190f),
        EnemyDef("spider", "Подвальный паук", EnemyLook.SPIDER, EnemyBehavior.SHOOTER,
            hp = 34f, speed = 94f, damage = 12f, radius = 17f, xp = 3, goldChance = 0.08f, knockbackResist = 0.1f, color = 0xFF6B4B8E.toInt(),
            range = 260f, cooldown = 3.2f, shotSpeed = 250f, shotDamage = 6f, special = 1f),
        EnemyDef("slimelet", "Слизнёнок", EnemyLook.SLIMELET, EnemyBehavior.CHASE,
            hp = 16f, speed = 82f, damage = 9f, radius = 13f, xp = 1, goldChance = 0.02f, knockbackResist = 0.15f, color = 0xFF4FD1A5.toInt()),

        EnemyDef("rat_king", "Король Крыс", EnemyLook.RAT_KING, EnemyBehavior.BOSS,
            hp = 3400f, speed = 74f, damage = 24f, radius = 50f, xp = 80, goldChance = 1f, knockbackResist = 0.97f, color = 0xFFB89A7A.toInt(),
            title = "Повелитель подвала", boss = true),
        EnemyDef("ogre_chef", "Людоед-повар", EnemyLook.OGRE_CHEF, EnemyBehavior.BOSS,
            hp = 8200f, speed = 52f, damage = 30f, radius = 54f, xp = 160, goldChance = 1f, knockbackResist = 0.99f, color = 0xFFD9A06A.toInt(),
            title = "Хозяин адской кухни", boss = true),
        EnemyDef("banshee", "Банши-хозяйка", EnemyLook.BANSHEE, EnemyBehavior.BOSS,
            hp = 6600f, speed = 66f, damage = 22f, radius = 38f, xp = 140, goldChance = 1f, knockbackResist = 0.99f, color = 0xFFC9B8FF.toInt(),
            title = "Плач заброшенного трактира", boss = true),
        EnemyDef("storm_heron", "Громоклюв", EnemyLook.STORM_HERON, EnemyBehavior.BOSS,
            hp = 4700f, speed = 60f, damage = 20f, radius = 38f, xp = 130, goldChance = 1f, knockbackResist = .98f, color = 0xFFAAA3ED.toInt(),
            title = "Хранитель грозового перевала", boss = true),
        EnemyDef("rootback", "Корнеспин", EnemyLook.ROOTBACK, EnemyBehavior.BOSS,
            hp = 5100f, speed = 46f, damage = 19f, radius = 49f, xp = 140, goldChance = 1f, knockbackResist = .99f, color = 0xFFA8CF80.toInt(),
            title = "Сердце туманной топи", boss = true),
        EnemyDef("prism_crab", "Зеркалорог", EnemyLook.PRISM_CRAB, EnemyBehavior.BOSS,
            hp = 4900f, speed = 54f, damage = 21f, radius = 46f, xp = 140, goldChance = 1f, knockbackResist = .99f, color = 0xFFE7C481.toInt(),
            title = "Страж кристальных руин", boss = true),
    )
}
