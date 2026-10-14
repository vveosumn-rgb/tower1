package com.tavernrush.game.systems.weapons

import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Настоящий прострел сегментом: поражает всех пересечённых врагов, не телепортирует снаряд. */
class BeamBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val p = w.player
        val s = wi.stats
        val target = w.hash.nearest(p.x, p.y, s.range)
        if (target < 0) { wi.timer = 0.12f; return }
        val enemy = w.enemies[target]
        val base = atan2(enemy.y - p.y, enemy.x - p.x)
        for (ray in 0 until s.amount.coerceAtMost(12)) {
            val a = base + (ray - (s.amount - 1) * 0.5f) * 0.18f
            val dx = cos(a); val dy = sin(a)
            val b = w.fx.bolt(wi.def.color, 0.22f)
            b.straight = true; b.width = s.radius * 1.4f
            w.fx.addBoltPoint(b, p.x, p.y)
            w.fx.addBoltPoint(b, p.x + dx * s.range, p.y + dy * s.range)
            val n = w.hash.query(p.x + dx * s.range * 0.5f, p.y + dy * s.range * 0.5f, s.range * 0.5f + s.radius, w.tmpIdx)
            for (k in 0 until n) {
                val e = w.enemies[w.tmpIdx[k]]
                if (!hittable(e)) continue
                val ex = e.x - p.x; val ey = e.y - p.y
                val along = ex * dx + ey * dy
                val across = abs(ex * dy - ey * dx)
                if (along >= -e.radius && along <= s.range + e.radius && across <= s.radius + e.radius)
                    w.combat.hitEnemy(e, s.damage, p.x, p.y, s.knockback, wi.slot)
            }
        }
        wi.timer = s.cooldown
        w.events.sfx(Sfx.THROW)
    }
}