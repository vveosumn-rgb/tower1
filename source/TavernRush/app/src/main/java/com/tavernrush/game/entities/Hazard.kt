package com.tavernrush.game.entities

/**
 * Опасная зона на земле (телеграф атаки врага): сначала предупреждение, потом удар.
 * CIRCLE — круг, LINE — полоса (луч/рывок), PUDDLE — лужа с уроном по тикам, RING — расходящаяся волна.
 */
class Hazard {
    var alive = false
    var kind = CIRCLE
    var x = 0f
    var y = 0f
    /** Радиус круга / полуширина полосы / толщина волны. */
    var r = 0f
    var len = 0f
    var ang = 0f
    /** Предупреждение (с) и длительность после удара (с). */
    var delay = 0f
    var maxDelay = 0f
    var life = 0f
    var maxLife = 0f
    /** Урон при срабатывании телеграфа; damage — урон за тик, пока зона действует (лужа, волна). */
    var burst = 0f
    var damage = 0f
    var tick = 0.5f
    var tickT = 0f
    /** 0 — предупреждение, 1 — действует. */
    var state = 0
    var color = 0
    var hit = false
    /** Кто создал (по uid врага, для отмены) и индекс вида (для записи «кто убил»). */
    var owner = 0
    var ownerDef = -1
    var slow = 0f
    /** Волна: радиус растёт r0 → r1. */
    var r0 = 0f
    var r1 = 0f
    /** Чисто визуальный (урона нет): руны призыва, метки. */
    var visual = false

    /** A visible safe opening for selected expanding waves; defaults preserve old waves. */
    var gapAngle=0f
    var gapHalf=0f
    fun inGap(px:Float,py:Float,padding:Float=0f):Boolean {
        if(gapHalf<=0f)return false
        val dx=px-x;val dy=py-y;val distance=kotlin.math.sqrt(dx*dx+dy*dy)
        if(distance<=padding)return false
        val angle=kotlin.math.atan2(dy,dx)-gapAngle
        val delta=kotlin.math.abs(kotlin.math.atan2(kotlin.math.sin(angle),kotlin.math.cos(angle)))
        return delta < gapHalf-(padding/distance).coerceIn(0f,gapHalf)
    }
    companion object {
        const val CIRCLE = 0
        const val LINE = 1
        const val PUDDLE = 2
        const val RING = 3
    }
}

/** Снаряд врага: стрела, паутина, тесак, сфера баньши. Попадание — урон герою; рывок даёт неуязвимость. */
class EnemyShot {
    var alive = false
    var kind = ARROW
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var damage = 8f
    var radius = 8f
    var life = 2f
    var maxLife = 2f
    var rot = 0f
    var spin = 0f
    var color = 0
    /** Замедление героя при попадании: время и множитель скорости (паутина). */
    var slowTime = 0f
    var slowMul = 1f
    var ownerDef = -1

    companion object {
        const val ARROW = 0
        const val WEB = 1
        const val CLEAVER = 2
        const val ORB = 3
    }
}
