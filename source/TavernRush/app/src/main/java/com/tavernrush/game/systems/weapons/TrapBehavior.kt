package com.tavernrush.game.systems.weapons

import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World
import kotlin.math.cos
import kotlin.math.sin

/** Капкан остаётся на земле: вооружение → один удар и задержка → закрытие. */
class TrapBehavior(w: World) : WeaponBehavior(w) {
    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val p = w.player
        val s = wi.stats
        for (k in 0 until s.amount.coerceAtMost(8)) {
            val a = k * 2.399963f
            val r = if (k == 0) 0f else 22f + k * 10f
            val q = w.spawnProjectile(Projectile.TRAP, wi.slot, p.x - p.faceX * 24f + cos(a) * r, p.y - p.faceY * 24f + sin(a) * r) ?: break
            q.damage = s.damage; q.radius = s.radius
            q.life = s.duration; q.maxLife = q.life
            q.timer = 0.3f
        }
        wi.timer = s.cooldown
    }

    override fun updateProjectile(p: Projectile, dt: Float): Boolean {
        p.life -= dt
        if (p.life <= 0f) return false
        if (p.state == 1) return true
        p.timer -= dt
        if (p.timer > 0f) return true
        val n = w.hash.query(p.x, p.y, p.radius, w.tmpIdx)
        for (k in 0 until n) {
            val e = w.enemies[w.tmpIdx[k]]
            if (!hittable(e)) continue
            w.combat.hitEnemy(e, p.damage, p.x, p.y, 0f, p.slot)
            p.state = 1; p.life = 0.35f
            w.fx.ring(p.x, p.y, p.radius, p.radius * 2f, 0.35f, 0xFF9EEBFF.toInt(), 4f)
            return true
        }
        return true
    }
}