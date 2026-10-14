package com.tavernrush.game.content

object Evolutions {
    fun all(): Array<EvolutionDef> = arrayOf(
        EvolutionDef("knives", "fingers", 3, "Королевский веер", "+3 ножа, урон ×1,6, быстрые залпы", might = 1.6f, cooldown = 0.65f, amount = 3),
        EvolutionDef("mugs", "stew", 3, "Пир без конца", "+2 кружки, почти непрерывная орбита", cooldown = 0.08f, amount = 2, duration = 1.5f),
        EvolutionDef("hearth", "tea", 3, "Сердце таверны", "Аура шире на 60%, урон ×1,8", might = 1.8f, area = 1.6f),
        EvolutionDef("lightning", "gesture", 3, "Гроза трактов", "+5 прыжков и второй разряд", amount = 1, chains = 5),
        EvolutionDef("pan", "horseshoe", 3, "Стальной хоровод", "+2 сковороды, размер и урон выше", might = 1.7f, amount = 2, area = 1.5f),
        EvolutionDef("barrel", "double", 2, "Праздничный салют", "+2 бочки, большие взрывы и урон ×1,7", might = 1.7f, amount = 2, area = 1.4f),
    )
}