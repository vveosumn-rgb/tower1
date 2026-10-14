package com.tavernrush.game.systems

import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.FloatText
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.Pickup
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Лут на земле: магнит (радиус сбора), подбор, слияние самоцветов при переполнении. */
class PickupSystem(private val w: World) {
    private var mergeCursor = 0

    fun spawn(kind: Int, x: Float, y: Float, value: Int) {
        val ps = w.pickups
        if (w.pickupCount >= World.MAX_PICKUPS) {
            when (kind) {
                Pickup.XP -> {
                    // Переполнение: опыт вливается в уже лежащий самоцвет (ничего не теряется).
                    for (k in 0 until 32) {
                        mergeCursor = (mergeCursor + 1) % w.pickupCount
                        val p = ps[mergeCursor]
                        if (p.kind == Pickup.XP) { p.value += value; return }
                    }
                    w.addXp(value.toFloat())
                }
                Pickup.GOLD -> w.run.gold += value * w.stats.goldGain
                Pickup.CHEST -> w.pendingChests++
            }
            return
        }
        val p = ps[w.pickupCount++]
        p.alive = true
        p.kind = kind
        p.x = x; p.y = y
        p.value = value
        p.attracted = false
        p.speed = 0f
        p.age = 0f
        val a = w.rngLoot.angle()
        val s = if (kind == Pickup.CHEST) 0f else w.rngLoot.range(40f, 110f)
        p.vx = cos(a) * s
        p.vy = sin(a) * s
    }

    fun update(dt: Float) {
        val pl = w.player
        val magR = BASE_MAGNET * w.stats.magnet
        val magR2 = magR * magR
        val colR = pl.radius + 14f
        val colR2 = colR * colR
        val decay = 1f - 6f * dt
        val ps = w.pickups
        var i = 0
        while (i < w.pickupCount) {
            val p = ps[i]
            p.age += dt
            val dx = pl.x - p.x
            val dy = pl.y - p.y
            val d2 = dx * dx + dy * dy
            if (!pl.dead && d2 <= colR2 && p.age > 0.1f) {
                collect(p)
                w.pickupCount--
                ps[i] = ps[w.pickupCount]
                ps[w.pickupCount] = p
                p.alive = false
                continue
            }
            if (!p.attracted) {
                p.x += p.vx * dt; p.y += p.vy * dt
                p.vx *= decay; p.vy *= decay
                if (d2 < magR2 && p.age > 0.2f && !pl.dead) p.attracted = true
            } else {
                p.speed += 1700f * dt
                if (p.speed > 1200f) p.speed = 1200f
                val d = sqrt(d2) + 0.001f
                var step = p.speed * dt
                if (step > d) step = d
                p.x += dx / d * step
                p.y += dy / d * step
            }
            i++
        }
    }

    private fun collect(p: Pickup) {
        val pl = w.player
        when (p.kind) {
            Pickup.XP -> {
                w.addXp(p.value.toFloat())
                w.events.sfx(Sfx.XP)
            }
            Pickup.GOLD -> {
                val g = p.value * w.stats.goldGain
                w.run.gold += g
                w.fx.text(pl.x, pl.y - 40f, (g + 0.5f).toInt(), FloatText.GOLD, 0xFFFFD54A.toInt(), big = p.value >= 5)
                w.events.sfx(Sfx.COIN)
            }
            Pickup.HEAL -> {
                val h = pl.maxHp * 0.3f
                w.heal(h)
                w.fx.burst(pl.x, pl.y, 0xFF7CFF8A.toInt(), 12, 180f, 6f, 0.6f, Particle.STAR)
                w.events.sfx(Sfx.LEVEL_UP)
            }
            Pickup.MAGNET -> {
                for (k in 0 until w.pickupCount) {
                    val q = w.pickups[k]
                    if (q.kind == Pickup.XP || q.kind == Pickup.GOLD) q.attracted = true
                }
                w.fx.ring(pl.x, pl.y, 30f, 900f, 0.6f, 0xFF7FD3FF.toInt(), 12f)
                w.events.sfx(Sfx.CHEST)
            }
            Pickup.CHEST -> {
                w.pendingChests++
                w.run.chests++
                w.events.sfx(Sfx.CHEST)
            }
        }
    }

    companion object {
        const val BASE_MAGNET = 75f
    }
}
