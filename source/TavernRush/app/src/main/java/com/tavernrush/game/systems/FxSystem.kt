package com.tavernrush.game.systems

import com.tavernrush.game.core.Rng
import com.tavernrush.game.entities.Bolt
import com.tavernrush.game.entities.FloatText
import com.tavernrush.game.entities.Light
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.Ring
import kotlin.math.cos
import kotlin.math.sin

/**
 * Визуальные эффекты в координатах мира: частицы, числа урона, молнии, кольца, тряска, вспышка.
 * Использует собственный поток ГПСЧ (rngFx): эффекты не влияют на игровую логику.
 */
class FxSystem(private val rng: Rng) {
    val particles = Array(MAX_PARTICLES) { Particle() }
    var particleCount = 0
        private set
    val texts = Array(MAX_TEXTS) { FloatText() }
    var textCount = 0
        private set
    val bolts = Array(MAX_BOLTS) { Bolt() }
    var boltCount = 0
        private set
    val rings = Array(MAX_RINGS) { Ring() }
    var ringCount = 0
        private set
    val lights = Array(MAX_LIGHTS) { Light() }
    var lightCount = 0
        private set
    /** Потолок частиц (пресет графики); логика игры от него не зависит. */
    var particleCap = MAX_PARTICLES
    /** Мощность тряски (настройка игрока, 0..1). */
    var shakeScale = 1f
    /** «Хлопок» хроматической аберрации на крупных ударах, 0..1. */
    var aberration = 0f

    /** «Травма» камеры 0..1 (тряска ~ квадрат). */
    var shake = 0f
    /** Вспышка экрана 0..1 и её цвет. */
    var flash = 0f
    var flashColor = 0
    private var textCursor = 0
    private var boltCursor = 0

    fun reset() {
        particleCount = 0; textCount = 0; boltCount = 0; ringCount = 0; lightCount = 0
        shake = 0f; flash = 0f; aberration = 0f
    }

    fun particle(
        x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int, kind: Int = Particle.DOT, drag: Float = 3f,
        z: Float = 0f, vz: Float = 0f, grav: Float = 0f, bounce: Float = 0f,
    ) {
        if (particleCount >= particleCap) return
        val p = particles[particleCount++]
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.life = life; p.maxLife = life; p.size = size; p.color = color; p.kind = kind; p.drag = drag
        p.z = z; p.vz = vz; p.grav = grav; p.bounce = bounce
    }

    /** Осколки: подлетают, падают и отскакивают (враг рассыпается, взрыв выбивает щепки). */
    fun chunks(x: Float, y: Float, color: Int, count: Int, speed: Float, size: Float, life: Float, z0: Float = 14f) {
        for (i in 0 until count) {
            val a = rng.angle()
            val s = speed * (0.3f + 0.7f * rng.nextFloat())
            particle(x, y, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s * 0.6f, life * (0.7f + 0.5f * rng.nextFloat()), size * (0.7f + 0.6f * rng.nextFloat()),
                color, Particle.CHUNK, 1.6f, z = z0 * rng.nextFloat(), vz = 90f + 170f * rng.nextFloat(), grav = 520f, bounce = 0.45f)
        }
    }

    /** Искры удара: быстрые чёрточки + блик. */
    fun sparks(x: Float, y: Float, color: Int, count: Int, speed: Float) {
        for (i in 0 until count) {
            val a = rng.angle()
            val s = speed * (0.4f + 0.6f * rng.nextFloat())
            particle(x, y, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s, 0.16f + 0.14f * rng.nextFloat(), 4f, color, Particle.SPARK, 5f)
        }
    }

    /** Брызги монет: дуга в воздух, отскок по земле. */
    fun coinSpray(x: Float, y: Float, count: Int) {
        for (i in 0 until count) {
            val a = rng.angle()
            val s = 60f + 120f * rng.nextFloat()
            particle(x, y, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s * 0.5f, 0.9f + 0.4f * rng.nextFloat(), 3f, 0xFFFFD54A.toInt(), Particle.COIN, 1.2f,
                z = 8f, vz = 220f + 140f * rng.nextFloat(), grav = 620f, bounce = 0.5f)
        }
    }

    /** Облачко пыли/дыма у земли. */
    fun puff(x: Float, y: Float, color: Int, count: Int, size: Float = 6f) {
        for (i in 0 until count) {
            particle(x + rng.signed() * 6f, y + rng.signed() * 3f, rng.signed() * 30f, -10f - 20f * rng.nextFloat(), 0.35f + 0.3f * rng.nextFloat(), size * (0.7f + 0.6f * rng.nextFloat()), color, Particle.SMOKE, 3.5f)
        }
    }

    /** Вспышка света: на карте света это тёплое пятно, на земле — блик. */
    fun light(x: Float, y: Float, radius: Float, color: Int, intensity: Float = 1f, life: Float = 0.2f) {
        val l = if (lightCount < MAX_LIGHTS) lights[lightCount++] else lights[0]
        l.x = x; l.y = y; l.radius = radius; l.color = color; l.intensity = intensity; l.life = life; l.maxLife = life
    }

    /** Звёздный блик попадания: мелкая вспышка с искрами (замена безликому «пуф»). */
    fun hitSpark(x: Float, y: Float, color: Int, big: Boolean = false) {
        sparks(x, y, color, if (big) 7 else 4, if (big) 330f else 240f)
        particle(x, y, 0f, 0f, 0.12f, if (big) 11f else 7f, 0xFFFFFFFF.toInt(), Particle.STAR, 0f)
        light(x, y, if (big) 70f else 44f, color, 0.7f, 0.12f)
    }

    /** Разлёт частиц во все стороны. */
    fun burst(x: Float, y: Float, color: Int, count: Int, speed: Float, size: Float, life: Float, kind: Int = Particle.DOT) {
        for (i in 0 until count) {
            val a = rng.angle()
            val s = speed * (0.35f + 0.65f * rng.nextFloat())
            particle(x, y, cos(a) * s, sin(a) * s, life * (0.6f + 0.4f * rng.nextFloat()), size * (0.6f + 0.6f * rng.nextFloat()), color, kind)
        }
    }

    fun text(x: Float, y: Float, value: Int, kind: Int, color: Int, big: Boolean = false) {
        val t: FloatText
        if (textCount < MAX_TEXTS) {
            t = texts[textCount++]
        } else {
            t = texts[textCursor]
            textCursor = (textCursor + 1) % MAX_TEXTS
        }
        t.x = x + rng.signed() * 8f; t.y = y
        t.value = value; t.kind = kind; t.color = color; t.big = big
        t.maxLife = if (big) 0.95f else 0.65f
        t.life = t.maxLife
    }

    /** Новая молния (точки добавлять через addBoltPoint) или null, если пул занят. */
    fun bolt(color: Int, life: Float = 0.22f): Bolt {
        val b: Bolt
        if (boltCount < MAX_BOLTS) {
            b = bolts[boltCount++]
        } else {
            b = bolts[boltCursor]
            boltCursor = (boltCursor + 1) % MAX_BOLTS
        }
        b.count = 0; b.color = color; b.life = life; b.maxLife = life
        b.straight = false; b.width = 11f
        return b
    }

    fun addBoltPoint(b: Bolt, x: Float, y: Float) {
        if (b.count >= Bolt.MAX_POINTS) return
        b.pts[b.count * 2] = x
        b.pts[b.count * 2 + 1] = y
        b.count++
    }

    fun ring(x: Float, y: Float, r0: Float, r1: Float, life: Float, color: Int, width: Float = 6f) {
        if (ringCount >= MAX_RINGS) return
        val r = rings[ringCount++]
        r.x = x; r.y = y; r.r0 = r0; r.r1 = r1; r.life = life; r.maxLife = life; r.color = color; r.width = width
    }

    fun addShake(v: Float) {
        shake += v * shakeScale
        if (shake > 1f) shake = 1f
    }

    fun addAberration(v: Float) {
        aberration = minOf(1f, aberration + v)
    }

    fun screenFlash(v: Float, color: Int) {
        if (v > flash) { flash = v; flashColor = color }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < particleCount) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particleCount--
                particles[i] = particles[particleCount]
                particles[particleCount] = p
                continue
            }
            var d = 1f - p.drag * dt
            if (d < 0f) d = 0f
            p.vx *= d; p.vy *= d
            if (p.kind == Particle.SMOKE) p.vy -= 30f * dt
            else if (p.kind == Particle.EMBER) { p.vy -= 70f * dt; p.vx += (rng.nextFloat() - 0.5f) * 240f * dt }
            else if (p.kind == Particle.LEAF) p.vx += 40f * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.grav != 0f || p.z != 0f || p.vz != 0f) {
                p.vz -= p.grav * dt
                p.z += p.vz * dt
                if (p.z < 0f) {
                    p.z = 0f
                    if (p.bounce > 0f && p.vz < -40f) { p.vz = -p.vz * p.bounce; p.vx *= 0.6f; p.vy *= 0.6f } else { p.vz = 0f; p.vx *= 0.82f; p.vy *= 0.82f }
                }
            }
            i++
        }
        i = 0
        while (i < textCount) {
            val t = texts[i]
            t.life -= dt
            if (t.life <= 0f) {
                textCount--
                texts[i] = texts[textCount]
                texts[textCount] = t
                continue
            }
            t.y -= (if (t.big) 55f else 70f) * dt
            i++
        }
        i = 0
        while (i < boltCount) {
            val b = bolts[i]
            b.life -= dt
            if (b.life <= 0f) {
                boltCount--
                bolts[i] = bolts[boltCount]
                bolts[boltCount] = b
                continue
            }
            i++
        }
        i = 0
        while (i < ringCount) {
            val r = rings[i]
            r.life -= dt
            if (r.life <= 0f) {
                ringCount--
                rings[i] = rings[ringCount]
                rings[ringCount] = r
                continue
            }
            i++
        }
        i = 0
        while (i < lightCount) {
            val l = lights[i]
            l.life -= dt
            if (l.life <= 0f) {
                lightCount--
                lights[i] = lights[lightCount]
                lights[lightCount] = l
                continue
            }
            i++
        }
        if (textCursor >= textCount) textCursor = 0
        if (boltCursor >= boltCount) boltCursor = 0
        aberration -= dt * 3.2f
        if (aberration < 0f) aberration = 0f
        shake -= dt * 2.4f
        if (shake < 0f) shake = 0f
        flash -= dt * 3.5f
        if (flash < 0f) flash = 0f
    }

    companion object {
        const val MAX_PARTICLES = 1000
        const val MAX_TEXTS = 90
        const val MAX_BOLTS = 16
        const val MAX_RINGS = 32
        const val MAX_LIGHTS = 48
    }
}
