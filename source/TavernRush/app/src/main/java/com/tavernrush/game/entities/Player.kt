package com.tavernrush.game.entities

/** Герой. Все поля — плоские значения, объект один на мир (без аллокаций). */
class Player {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    /** Последнее ненулевое направление движения (для оружия без цели и анимации). */
    var faceX = 1f
    var faceY = 0f
    var hp = 100f
    var maxHp = 100f
    val radius = 20f
    /** Неуязвимость после удара, с. */
    var invuln = 0f
    /** Вспышка получения урона (визуал), с. */
    var hurtFlash = 0f
    var level = 1
    var xp = 0f
    var xpNeed = 5f
    var dead = false
    /** Фаза шага (анимация). */
    var walk = 0f
    var moving = false

    // ---- R03: рывок и состояния
    /** Остаток рывка, с (> 0 — герой летит по dashX/dashY). */
    var dashT = 0f
    var dashX = 0f
    var dashY = 0f
    /** Окно неуязвимости рывка, с: удар в нём — «идеальный уклон». */
    var dodgeT = 0f
    /** Заряды рывка и таймер восстановления очередного заряда. */
    var dashCharges = MAX_DASH
    var dashRecharge = 0f
    /** Замедление героя (паутина), с и множитель скорости. */
    var slowT = 0f
    var slowMul = 1f
    /** Время последнего «идеального уклона» (защита от повторов), с мира. */
    var lastPerfect = -10f

    fun reset() {
        x = 0f; y = 0f; vx = 0f; vy = 0f
        faceX = 1f; faceY = 0f
        hp = 100f; maxHp = 100f
        invuln = 0f; hurtFlash = 0f
        level = 1; xp = 0f; xpNeed = 5f
        dead = false; walk = 0f; moving = false
        dashT = 0f; dashX = 0f; dashY = 0f; dodgeT = 0f
        dashCharges = MAX_DASH; dashRecharge = 0f
        slowT = 0f; slowMul = 1f; lastPerfect = -10f
    }

    companion object {
        const val MAX_DASH = 2
    }

    val hpFrac: Float get() = if (maxHp <= 0f) 0f else (hp / maxHp).coerceIn(0f, 1f)
}
