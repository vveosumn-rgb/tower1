package com.tavernrush.game.systems.weapons

import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Сковорода: летит к врагу, тормозит на дальности range, возвращается к герою; бьёт насквозь. */
class BoomerangBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val s = wi.stats
        val p = w.player
        val target = nearestVisible(p.x,p.y,s.range*1.6f,s.radius)
        val base = if (target >= 0) {
            val e = w.enemies[target]
            atan2(e.y - p.y, e.x - p.x)
        } else atan2(p.faceY, p.faceX)
        val n = s.amount
        for (k in 0 until n) {
            val a = base + (k - (n - 1) * 0.5f) * SPREAD
            val pr = w.spawnProjectile(Projectile.PAN, wi.slot, p.x, p.y) ?: break
            pr.sx = cos(a)
            pr.sy = sin(a)
            pr.speed = s.speed
            pr.accel = s.speed * s.speed / (2f * s.range)
            pr.damage = s.damage
            pr.radius = s.radius
            pr.knockback = s.knockback
            pr.pierceLeft = 9999
            pr.life = 6f
            pr.maxLife = 6f
            pr.spin = 15f
        }
        wi.timer = s.cooldown
        w.events.sfx(Sfx.THROW)
    }

    override fun updateProjectile(p: Projectile, dt: Float): Boolean {
        p.life -= dt
        if (p.life <= 0f) return false
        p.rot += p.spin * dt
        val pl = w.player
        if (p.state == 0) {
            p.speed -= p.accel * dt
            if (p.speed <= 0f) {
                p.speed = 0f
                p.state = 1
            }
            p.vx = p.sx * p.speed
            p.vy = p.sy * p.speed
        } else {
            p.speed += p.accel * dt
            val dx = pl.x - p.x
            val dy = pl.y - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < CATCH_DIST) return false
            p.vx = dx / d * p.speed
            p.vy = dy / d * p.speed
        }
        val oldX=p.x;val oldY=p.y
        p.x += p.vx * dt
        p.y += p.vy * dt
        if(w.adventure.active&&!w.adventure.map.clear(oldX,oldY,p.x,p.y,p.radius,w.adventure.flags))return false
        val now = w.time
        val n = w.hash.query(p.x, p.y, p.radius, w.tmpIdx)
        for (k in 0 until n) {
            val e = w.enemies[w.tmpIdx[k]]
            if (!hittable(e)) continue
            if (now - e.lastHit[p.slot] < HIT_INTERVAL) continue
            e.lastHit[p.slot] = now
            w.combat.hitEnemy(e, p.damage, p.x, p.y, p.knockback, p.slot)
        }
        return true
    }

    companion object {
        const val SPREAD = 0.55f
        const val HIT_INTERVAL = 0.35f
        const val CATCH_DIST = 28f
    }
}
