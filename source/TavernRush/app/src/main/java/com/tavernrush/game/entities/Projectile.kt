package com.tavernrush.game.entities

/** Снаряд из пула World.projectiles: нож, сковорода, бочка. */
class Projectile {
    var alive = false
    var kind = 0
    /** Слот оружия-владельца. */
    var slot = 0
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var damage = 0f
    var radius = 8f
    var knockback = 0f
    var pierceLeft = 1
    var life = 0f
    var maxLife = 1f
    /** Поворот (рад) и скорость вращения для отрисовки. */
    var rot = 0f
    var spin = 0f
    /** Бумеранг: 0 — летит вперёд, 1 — возвращается. Бочка: 0 — летит, 1 — фитиль. */
    var state = 0
    var speed = 0f
    var accel = 0f
    /** Старт/цель для бочки, t — прогресс полёта 0..1. */
    var sx = 0f
    var sy = 0f
    var tx = 0f
    var ty = 0f
    var t = 0f
    var timer = 0f
    /** Высота над землёй (визуал броска). */
    var z = 0f
    val hitUids = IntArray(HIT_MEMORY)
    var hitCount = 0

    fun alreadyHit(uid: Int): Boolean {
        for (i in 0 until hitCount) if (hitUids[i] == uid) return true
        return false
    }

    fun rememberHit(uid: Int) {
        if (hitCount < HIT_MEMORY) hitUids[hitCount++] = uid
        else hitUids[(uid and 0x7fffffff) % HIT_MEMORY] = uid
    }

    companion object {
        const val KNIFE = 0
        const val PAN = 1
        const val BARREL = 2
        const val TRAP = 3
        const val RAVEN = 4
        const val HIT_MEMORY = 12
    }
}
