package com.tavernrush.game.systems.weapons

import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World

/** Цепная молния: случайная цель рядом, прыжки к ближайшим ещё не задетым врагам. */
class ChainBehavior(w: World) : WeaponBehavior(w) {
    private val exclude = IntArray(32)

    override fun update(wi: WeaponInstance, dt: Float) {
        wi.timer -= dt
        if (wi.timer > 0f) return
        val s = wi.stats
        val p = w.player
        var fired = false
        for (z in 0 until s.amount) {
            val first = randomEnemyNear(p.x, p.y, s.range)
            if (first < 0) break
            fired = true
            var e = w.enemies[first]
            val bolt = w.fx.bolt(wi.def.color)
            w.fx.addBoltPoint(bolt, p.x, p.y - 26f)
            var exCount = 0
            var dmg = s.damage
            for (j in 0..s.chains) {
                w.fx.addBoltPoint(bolt, e.x, e.y)
                if (exCount < exclude.size) exclude[exCount++] = e.uid
                val ex = e.x
                val ey = e.y
                w.combat.hitEnemy(e, dmg, ex, ey - 1f, s.knockback, wi.slot)
                w.fx.burst(ex, ey, 0xFFBFF4FF.toInt(), 4, 220f, 4f, 0.25f, Particle.SPARK)
                if (j == s.chains) break
                val next = w.hash.nearestExcluding(ex, ey, s.radius, exclude, exCount)
                if (next < 0) break
                e = w.enemies[next]
                dmg *= FALLOFF
            }
        }
        if (fired) {
            wi.timer = s.cooldown
            w.events.sfx(Sfx.ZAP)
        } else wi.timer = 0.15f
    }

    companion object {
        const val FALLOFF = 0.9f
    }
}
