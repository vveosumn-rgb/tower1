package com.tavernrush.game.content

/** Меню «Привала в таверне» (между этапами забега): бонус до конца забега. */
object Dishes {
    fun all(): Array<DishDef> = arrayOf(
        DishDef("stew", "Похлёбка трактирщика", "Полное лечение и здоровье +15", IconId.STEW, Stat.MAX_HP, 15f, healPct = 1f),
        DishDef("strong_ale", "Кружка крепкого эля", "Урон +15% до конца забега", IconId.ALE, Stat.MIGHT, 0.15f),
        DishDef("pie", "Вишнёвый пирог", "Перезарядка −10% до конца забега", IconId.PIE, Stat.COOLDOWN, 0.10f),
        DishDef("mead", "Медовуха", "Скорость бега +12% до конца забега", IconId.MEAD, Stat.MOVE_SPEED, 0.12f),
        DishDef("tips", "Мешок чаевых", "+40 золота сразу", IconId.GOLD_BAG, null, 0f, gold = 40),
        DishDef("roast", "Жаркое на вертеле", "Броня +2 и лечение 40%", IconId.ROAST, Stat.ARMOR, 2f, healPct = 0.4f),
        DishDef("herbs", "Травяной сбор", "Лечение +0,8 ОЗ/с до конца забега", IconId.HERBS, Stat.REGEN, 0.8f),
        DishDef("cheese", "Сырная тарелка", "Опыт +20% до конца забега", IconId.CHEESE, Stat.XP_GAIN, 0.20f),
    )
}
