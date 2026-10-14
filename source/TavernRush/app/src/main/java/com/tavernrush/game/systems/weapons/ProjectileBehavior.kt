package com.tavernrush.game.systems.weapons

import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Метательные ножи: веер в ближайшего врага, пробивание. */
class ProjectileBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val s = wi.stats
        val p = w.player
        val target = nearestVisible(p.x,p.y-6f,s.range,s.radius)
        if (target < 0) {
            wi.timer = 0.12f
            return
        }
        val e = w.enemies[target]
        val base = atan2(e.y - (p.y-6f), e.x - p.x)
        val n = s.amount
        for (k in 0 until n) {
            val a = base + (k - (n - 1) * 0.5f) * SPREAD
            val pr = w.spawnProjectile(Projectile.KNIFE, wi.slot, p.x, p.y - 6f) ?: break
            val c = cos(a)
            val sn = sin(a)
            pr.vx = c * s.speed
            pr.vy = sn * s.speed
            pr.x += c * 14f
            pr.y += sn * 14f
            pr.damage = s.damage
            pr.radius = s.radius
            pr.knockback = s.knockback
            pr.pierceLeft = s.pierce
            pr.life = s.range / s.speed
            pr.maxLife = pr.life
            pr.rot = a
        }
        wi.timer = s.cooldown
        w.events.sfx(Sfx.THROW)
    }

    override fun updateProjectile(p: Projectile, dt: Float): Boolean {
        p.life -= dt
        if (p.life <= 0f) return false
        val oldX=p.x;val oldY=p.y
        p.x += p.vx * dt
        p.y += p.vy * dt
        if(w.adventure.active&&!w.adventure.map.clear(oldX,oldY,p.x,p.y,p.radius,w.adventure.flags))return false
        val n = w.hash.query(p.x, p.y, p.radius, w.tmpIdx)
        for (k in 0 until n) {
            val e = w.enemies[w.tmpIdx[k]]
            if (!hittable(e) || p.alreadyHit(e.uid)) continue
            p.rememberHit(e.uid)
            w.combat.hitEnemy(e, p.damage, p.x - p.vx, p.y - p.vy, p.knockback, p.slot)
            p.pierceLeft--
            if (p.pierceLeft <= 0) {
                w.fx.burst(p.x, p.y, 0xFFE6EEF7.toInt(), 3, 160f, 4f, 0.2f, 1)
                return false
            }
        }
        return true
    }

    companion object {
        const val SPREAD = 0.17f
    }
}
