package com.tavernrush.game.content

/**
 * Стихия оружия или способности существа. Она «запускает» реакцию, если у цели есть нужное состояние
 * (облита, заморожена): см. systems/ReactionSystem и docs/COMPANIONS_AND_REACTIONS.md.
 */
enum class Element(val label: String, val color: Int) {
    NONE("", 0xFFFFFFFF.toInt()),
    WATER("Вода", 0xFF5CC8FF.toInt()),
    FIRE("Огонь", 0xFFFF8A3D.toInt()),
    FROST("Мороз", 0xFFA8EEFF.toInt()),
    SHOCK("Разряд", 0xFFC0A8FF.toInt()),
    HEAVY("Удар", 0xFFE6C9A0.toInt()),
}

/** Четыре реакции. Каждая тратит состояние цели, поэтому цепочки конечны и не зацикливаются. */
object Reactions {
    const val NONE = 0
    /** Разряд по облитому врагу перескакивает на других облитых. */
    const val CONDUCT = 1
    /** Мороз по облитому врагу сковывает его. */
    const val FREEZE = 2
    /** Огонь по облитому врагу даёт обжигающий пар вокруг. */
    const val STEAM = 3
    /** Тяжёлый удар по замороженному раскалывает его осколками. */
    const val SHATTER = 4
    const val COUNT = 5

    val names = arrayOf("", "Проводимость", "Заморозка", "Пар", "Раскол")
    val colors = intArrayOf(0, 0xFFC0A8FF.toInt(), 0xFFA8EEFF.toInt(), 0xFFFFE9C8.toInt(), 0xFFFFFFFF.toInt())
    val hints = arrayOf(
        "",
        "Разряд по облитому врагу перескакивает на других облитых",
        "Мороз по облитому врагу сковывает его",
        "Огонь по облитому врагу даёт обжигающий пар вокруг",
        "Тяжёлый удар по замороженному раскалывает его",
    )

    /** Прямая реакция пары стихий (как в подсказках на картах); [NONE], если пара не реагирует. */
    fun between(a: Element, b: Element): Int {
        if (a == b) return NONE
        val water = a == Element.WATER || b == Element.WATER
        val other = if (a == Element.WATER) b else a
        if (!water) return NONE
        return when (other) {
            Element.SHOCK -> CONDUCT
            Element.FROST -> FREEZE
            Element.FIRE -> STEAM
            else -> NONE
        }
    }

    /**
     * Какую реакцию даст новая стихия [added] вместе с уже имеющимися [owned] (битовая маска по ordinal).
     * Раскол — трёхсоставный: вода + мороз делают заморозку, тяжёлый удар её раскалывает.
     */
    fun hint(added: Element, owned: Int): Int {
        if (added == Element.NONE) return NONE
        fun has(e: Element) = owned and (1 shl e.ordinal) != 0
        for (e in Element.entries) if (e != Element.NONE && has(e)) {
            val r = between(added, e)
            if (r != NONE) return r
        }
        if (added == Element.HEAVY && has(Element.WATER) && has(Element.FROST)) return SHATTER
        return NONE
    }

    fun mask(e: Element): Int = if (e == Element.NONE) 0 else 1 shl e.ordinal
}
