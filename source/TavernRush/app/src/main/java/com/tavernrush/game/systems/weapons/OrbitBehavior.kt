package com.tavernrush.game.systems.weapons

import com.tavernrush.game.core.Sfx
import com.tavernrush.game.core.TWO_PI
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.cos
import kotlin.math.sin

/** Пивные кружки: кружат вокруг героя duration секунд, затем пауза cooldown. */
class OrbitBehavior(w: World) : WeaponBehavior(w) {
    override fun onEquip(wi: WeaponInstance) {
        wi.timer = 0.2f
    }

    override fun update(wi: WeaponInstance, dt: Float) {
        val s = wi.stats
        wi.angle += s.speed * dt
        if (wi.angle > TWO_PI) wi.angle -= TWO_PI
        if (wi.active > 0f) {
            wi.active -= dt
            wi.fade += dt * 5f
            if (wi.fade > 1f) wi.fade = 1f
            if (wi.active <= 0f) wi.timer = s.cooldown
        } else {
            wi.fade -= dt * 4f
            if (wi.fade < 0f) wi.fade = 0f
            wi.timer -= dt
            if (wi.timer <= 0f) {
                wi.active = s.duration
                w.events.sfx(Sfx.THROW)
            }
            return
        }
        val p = w.player
        val n = s.amount
        val now = w.time
        for (k in 0 until n) {
            val a = wi.angle + k * TWO_PI / n
            val ox = p.x + cos(a) * s.range
            val oy = p.y + sin(a) * s.range
            val cnt = w.hash.query(ox, oy, s.radius, w.tmpIdx)
            for (q in 0 until cnt) {
                val e = w.enemies[w.tmpIdx[q]]
                if (!hittable(e)) continue
                if (now - e.lastHit[wi.slot] < HIT_INTERVAL) continue
                e.lastHit[wi.slot] = now
                w.combat.hitEnemy(e, s.damage, p.x, p.y, s.knockback, wi.slot)
            }
        }
    }

    companion object {
        const val HIT_INTERVAL = 0.5f
    }
}
