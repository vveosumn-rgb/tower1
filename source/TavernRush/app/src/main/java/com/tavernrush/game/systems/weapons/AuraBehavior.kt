package com.tavernrush.game.systems.weapons

import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World

/** Жар очага: постоянная аура, «тик» урона и замедление. */
class AuraBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.pulse += dt
        wi.fade = 1f
        wi.timer -= dt
        if (wi.timer > 0f) return
        val s = wi.stats
        wi.timer = s.cooldown
        val p = w.player
        val cnt = w.hash.query(p.x, p.y, s.radius, w.tmpIdx)
        for (q in 0 until cnt) {
            val e = w.enemies[w.tmpIdx[q]]
            if (!hittable(e)) continue
            e.slowMul = if (e.slowTimer > 0f) minOf(e.slowMul, SLOW) else SLOW
            e.slowTimer = maxOf(e.slowTimer, s.duration)
            w.combat.hitEnemy(e, s.damage, p.x, p.y, s.knockback, wi.slot)
        }
        if (cnt > 0) wi.active = 0.18f
    }

    companion object {
        const val SLOW = 0.7f
    }
}
