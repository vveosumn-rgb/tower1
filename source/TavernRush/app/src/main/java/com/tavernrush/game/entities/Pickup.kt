package com.tavernrush.game.entities

/** Подбираемый предмет: самоцвет опыта, монета, курица, магнит, сундук. */
class Pickup {
    var alive = false
    var kind = 0
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var value = 0
    var attracted = false
    var speed = 0f
    var age = 0f

    companion object {
        const val XP = 0
        const val GOLD = 1
        const val HEAL = 2
        const val MAGNET = 3
        const val CHEST = 4
    }
}
