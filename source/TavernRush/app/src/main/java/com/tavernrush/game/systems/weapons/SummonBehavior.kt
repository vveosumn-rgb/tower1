package com.tavernrush.game.systems.weapons

import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Ворон — отдельный движущийся спутник, а не декоративная орбита. */
class SummonBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val s = wi.stats
        var active = 0
        for (i in 0 until w.projectileCount) {
            val q = w.projectiles[i]
            if (q.kind == Projectile.RAVEN && q.slot == wi.slot && q.alive) active++
        }
        val count = s.amount.coerceAtMost(8)
        for (k in active until count) {
            val p = w.spawnProjectile(Projectile.RAVEN, wi.slot, w.player.x, w.player.y - 36f) ?: break
            p.damage = s.damage; p.radius = s.radius; p.speed = s.speed
            p.life = s.duration; p.maxLife = p.life
            p.knockback = s.knockback; p.timer = 0.2f
            p.accel = k * 2.399963f
        }
        wi.timer = 0.18f
    }

    override fun updateProjectile(p: Projectile, dt: Float): Boolean {
        p.life -= dt
        if (p.life <= 0f || p.slot !in 0 until w.weaponCount) return false
        val hero = w.player
        val s = w.weapons[p.slot].stats
        p.timer -= dt
        var target = -1
        if (p.state == 0 && p.timer <= 0f) target = w.hash.nearest(p.x, p.y, s.range)
        if (target >= 0 && !hittable(w.enemies[target])) target = -1
        val orbit = w.time * 2.2f + p.accel
        val tx = if (target >= 0) w.enemies[target].x else hero.x + cos(orbit) * 48f
        val ty = if (target >= 0) w.enemies[target].y else hero.y - 24f + sin(orbit) * 32f
        val dx = tx - p.x; val dy = ty - p.y
        val distance = sqrt(dx * dx + dy * dy)
        val move = minOf(distance, p.speed * dt)
        if (distance > 0.001f) {
            p.x += dx / distance * move; p.y += dy / distance * move
            p.rot = atan2(dy, dx)
        }
        if (target >= 0) {
            val e = w.enemies[target]
            val hx = e.x - p.x; val hy = e.y - p.y
            val radius = e.radius + p.radius
            if (hx * hx + hy * hy <= radius * radius) {
                w.combat.hitEnemy(e, p.damage, p.x, p.y, p.knockback, p.slot)
                p.state = 1; p.timer = s.cooldown
                w.fx.burst(p.x, p.y, 0xFFC6B8FF.toInt(), 5, 120f, 4f, 0.3f)
            }
        } else if (p.state == 1 && p.timer <= 0f) {
            val hx = hero.x - p.x; val hy = hero.y - p.y
            if (hx * hx + hy * hy < 100f * 100f) p.state = 0
        }
        return true
    }
}