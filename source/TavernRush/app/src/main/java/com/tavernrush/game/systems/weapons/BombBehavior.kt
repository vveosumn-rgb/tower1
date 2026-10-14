package com.tavernrush.game.systems.weapons

import com.tavernrush.game.core.Haptic
import com.tavernrush.game.core.PI_F
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.cos
import kotlin.math.sin

/** Пороховая бочка: бросок по дуге в толпу, фитиль, взрыв по площади. */
class BombBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val s = wi.stats
        val p = w.player
        var any = false
        for (k in 0 until s.amount) {
            val idx = randomEnemyNear(p.x, p.y, s.range)
            var tx: Float
            var ty: Float
            if (idx >= 0) {
                val e = w.enemies[idx]
                // Упреждение: бочка летит туда, где враг окажется к взрыву.
                tx = e.x + (p.x - e.x) * 0.15f
                ty = e.y + (p.y - e.y) * 0.15f
            } else {
                if (k > 0 || w.enemyCount == 0) break
                val a = w.rngCombat.angle()
                tx = p.x + cos(a) * s.range * 0.5f
                ty = p.y + sin(a) * s.range * 0.5f
            }
            val pr = w.spawnProjectile(Projectile.BARREL, wi.slot, p.x, p.y) ?: break
            any = true
            pr.sx = p.x; pr.sy = p.y
            pr.tx = tx + w.rngCombat.range(-18f, 18f)
            pr.ty = ty + w.rngCombat.range(-18f, 18f)
            pr.speed = s.speed
            pr.timer = s.duration
            pr.damage = s.damage
            pr.radius = s.radius
            pr.knockback = s.knockback
            pr.spin = 7f
            pr.life = 10f
        }
        wi.timer = if (any) s.cooldown else 0.25f
        if (any) w.events.sfx(Sfx.THROW)
    }

    override fun updateProjectile(p: Projectile, dt: Float): Boolean {
        if (p.state == 0) {
            p.t += dt / p.speed
            if (p.t >= 1f) {
                p.t = 1f
                p.state = 1
            }
            p.x = p.sx + (p.tx - p.sx) * p.t
            p.y = p.sy + (p.ty - p.sy) * p.t
            p.z = sin(p.t * PI_F) * ARC_HEIGHT
            p.rot += p.spin * dt
            return true
        }
        p.z = 0f
        p.timer -= dt
        if (p.timer > 0f) return true
        explode(p)
        return false
    }

    private fun explode(p: Projectile) {
        val n = w.hash.query(p.x, p.y, p.radius, w.tmpIdx)
        for (k in 0 until n) {
            val e = w.enemies[w.tmpIdx[k]]
            if (!hittable(e)) continue
            w.combat.hitEnemy(e, p.damage, p.x, p.y, p.knockback, p.slot)
        }
        val fx = w.fx
        fx.ring(p.x, p.y, p.radius * 0.3f, p.radius * 1.05f, 0.35f, 0xFFFFC14D.toInt(), 12f)
        fx.burst(p.x, p.y, 0xFFFF8A2B.toInt(), 22, p.radius * 4.5f, 10f, 0.45f, Particle.SPARK)
        fx.burst(p.x, p.y, 0xFFFFE08A.toInt(), 10, p.radius * 3f, 8f, 0.3f)
        fx.burst(p.x, p.y, 0xFF5A4A40.toInt(), 8, p.radius * 1.5f, 22f, 0.9f, Particle.SMOKE)
        fx.addShake(0.22f)
        w.events.sfx(Sfx.BOOM)
        w.events.haptic(Haptic.TICK)
    }

    companion object {
        const val ARC_HEIGHT = 130f
    }
}
