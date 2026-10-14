package com.tavernrush.game.core

/**
 * Обратная связь из игровой логики во внешний мир: звук, вибрация, выход из приложения.
 * Реализует платформа (platform/AndroidFeedback), в тестах — None.
 * Вызывается из игрового потока: реализация обязана быть быстрой и неблокирующей.
 */
interface GameEvents {
    fun sfx(id: Int) {}
    fun haptic(kind: Int) {}
    fun music(theme:Int) {}
    fun exitRequested() {}

    object None : GameEvents
}

/** Идентификаторы процедурных звуков (см. platform/SfxSynth). */
object Sfx {
    const val HIT = 0
    const val KILL = 1
    const val XP = 2
    const val COIN = 3
    const val LEVEL_UP = 4
    const val HURT = 5
    const val CLICK = 6
    const val DEATH = 7
    const val BOOM = 8
    const val ZAP = 9
    const val CHEST = 10
    const val WAVE = 11
    const val BUY = 12
    const val THROW = 13
    const val VICTORY = 14
    const val DASH = 15
    const val TELEGRAPH = 16
    const val SHOT = 17
    const val ROAR = 18
    const val PERFECT = 19
    const val SHIELD = 20
    const val PHASE = 21
    const val CONDUCT = 22
    const val FREEZE = 23
    const val STEAM = 24
    const val SHATTER = 25
    const val SPLASH = 26
    const val CRY = 27
    const val JOIN = 28
    const val COUNT = 29
}

object Haptic {
    const val TICK = 0
    const val HURT = 1
    const val BIG = 2
    const val DASH = 3
    const val PERFECT = 4
}
