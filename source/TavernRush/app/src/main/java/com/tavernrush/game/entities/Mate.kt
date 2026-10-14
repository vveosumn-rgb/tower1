package com.tavernrush.game.entities

import com.tavernrush.game.content.Creatures

/** Друг в забеге: существо из профиля (или только что спасённое), которое ходит рядом с героем. */
class Mate(val species: Int) {
    var resonance=-1
    var resonanceCd=0f
    var resonanceFlash=0f
    var animalUid=0
    var visualAge=2
    var visualForm=0
    var friendId = ""
    var nameIdx = 0
    var trait = 0
    var spec = 0

    /** В бою (true) или отдыхает в резерве. */
    var active = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var faceLeft = false
    var anim = 0f
    var hp = 60f
    var maxHp = 60f
    var state = FOLLOW
    var stateT = 0f
    var cd = 0f
    /** Заряд Грозорыса, 0..1. */
    var charge = 0f
    /** Щиты Камнерога и таймер восстановления следующего. */
    var guard = 0
    var guardT = 0f
    var invuln = 0f
    /** Прыжок: старт, цель (по uid врага), сила и высота над землёй для отрисовки. */
    var sx = 0f
    var sy = 0f
    var tx = 0f
    var ty = 0f
    var targetUid = 0
    var power = 1f
    var hop = 0f
    /** Накопление «поднимания» в состоянии [DOWNED], с. */
    var reviveT = 0f
    /** Короткая «эмоция» (радость, испуг) для отрисовки, с. */
    var emote = 0f

    // Итоги забега для профиля.
    var activeTime = 0f
    var practice = 0
    var revived = 0

    val cdMul: Float get() = if (trait == 2) 0.9f else 1f
    val rangeMul: Float get() = if (trait == 0) 1.15f else 1f
    val followMul: Float get() = when (trait) { 0 -> 1.35f; 1 -> 0.8f; else -> 1f }
    val reviveMul: Float get() = if (trait == 1) 1.5f else 1f
    val hpMul: Float get() = if (trait == 1) 1.2f else 1f

    val name: String get() = com.tavernrush.game.content.Content.creatures[species].names[nameIdx.coerceIn(0, com.tavernrush.game.content.Content.creatures[species].names.size - 1)] + if(animalUid>0)" · $animalUid" else ""
    val traitName: String get() = Creatures.traits[trait.coerceIn(0, Creatures.traits.size - 1)].name

    companion object {
        const val FOLLOW = 0
        /** Прыжок Грозорыса: летит к цели. */
        const val ACT = 1
        /** Отдышка после приёма: стоит и уязвим. */
        const val RECOVER = 2
        /** Выбит из строя: лежит, пока герой рядом или не пройдёт время. Не умирает. */
        const val DOWNED = 3
        /** Только что вышел в бой: короткая неуязвимость. */
        const val ENTER = 4
    }
}
