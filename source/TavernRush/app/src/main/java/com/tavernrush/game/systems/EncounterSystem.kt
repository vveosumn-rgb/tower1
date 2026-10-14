package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.meta.Friend
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Встреча с существом: на одной из первых волн недалеко от героя кого-то обижают. Герой должен
 * подойти, прогнать обидчиков и спокойно постоять рядом — тогда существо подходит само (три шага доверия),
 * присоединяется и записывается в друзья. Это маленькая «эмоционально значимая встреча» из дизайна v3:
 *  — нет таймера «успей»: пока герой далеко, доверие лишь медленно остывает; чистить всю арену не нужно —
 *    достаточно, чтобы рядом с существом не было врагов;
 *  — неудача мягкая: если существо вынуждено убежать, оно может встретиться в другом забеге;
 *  — знакомство даёт сразу действие: друг помогает уже в этом бою, а не «потом».
 * Случайность — отдельный поток [Rng.stream] № 7: другие потоки забега она не сдвигает.
 */
class EncounterSystem(private val w: World) {
    var state = NONE
        private set
    var species = -1
        private set
    var nameIdx = 0
        private set
    var trait = 0
        private set
    var x = 0f
        private set
    var y = 0f
        private set
    var siteX = 0f
        private set
    var siteY = 0f
        private set
    var hp = 0f
        private set
    var trust = 0f
        private set
    /** 0..2: сколько раз существо уже подошло ближе. */
    var stage = 0
        private set
    var faceLeft = false
        private set
    /** Таймер подсказки «кто-то зовёт на помощь» и «эмоция» существа (радость, испуг). */
    var hint = 0f
        private set
    var emote = 0f
        private set
    /** Друг, которого Game ещё не записал в профиль (null — нет). */
    var joined: Friend? = null
        private set
    /** Присоединился как спутник (true) или ушёл домой, потому что в команде нет места (false). */
    var joinedAsMate = false
        private set
    var anim = 0f
        private set
    /** Чем закончилась встреча (для баннера и итогов): [OUT_NONE], [OUT_JOINED], [OUT_FLED], [OUT_LEFT]. */
    var outcome = OUT_NONE
        private set

    /** Сколько секунд встреча уже идёт (для мягкого срока жизни). */
    var age = 0f
        private set

    /** Секунд до ухода существа, если встречу не решить. */
    val remaining: Float get() = LIFETIME - age

    private var armed = false
    private var plannedWave = 1
    private var plannedTime = 14f
    private var planAngle = 0f
    private var timer = 0f
    private var awayT = 0f
    private var rng = Rng(7)

    val active: Boolean get() = state == ACTIVE
    val maxHp: Float get() = MAX_HP

    fun reset() {
        state = NONE; species = -1; armed = false; joined = null; joinedAsMate = false; outcome = OUT_NONE
        trust = 0f; stage = 0; hint = 0f; emote = 0f; timer = 0f; awayT = 0f; hp = 0f; age = 0f
    }

    /**
     * Подготовить встречу. [unmetMask] — биты видов, с которыми герой ещё не знаком (по индексу в Content.creatures).
     * Если знакомы все — встречи не будет.
     */
    fun arm(unmetMask: Int, seed: Long) {
        reset()
        rng = Rng(Rng.stream(seed, 7))
        var n = 0
        for (i in Content.creatures.indices) if (Content.creatures[i].wild && unmetMask and (1 shl i) != 0) n++
        if (n == 0) return
        var pick = rng.nextInt(n)
        for (i in Content.creatures.indices) if (Content.creatures[i].wild && unmetMask and (1 shl i) != 0) { if (pick == 0) { species = i; break }; pick-- }
        val def = Content.creatures[species]
        nameIdx = rng.nextInt(def.names.size)
        trait = rng.nextInt(Creatures.traits.size)
        plannedWave = 1 + rng.nextInt(2)
        plannedTime = 10f + rng.nextFloat() * 20f
        planAngle = rng.angle()
        armed = true
    }

    /** Game забирает друга, чтобы записать в профиль; один раз. */
    fun takeJoin(): Friend? { val f = joined; joined = null; return f }

    fun update(dt: Float) {
        anim += dt
        if (hint > 0f) hint -= dt
        if (emote > 0f) emote -= dt
        when (state) {
            NONE -> if (armed && !w.player.dead && w.director.waveIndex == plannedWave && w.director.waveTime >= plannedTime && w.activeBoss() == null) start()
            ACTIVE -> active(dt)
            JOINED, FLED, LEFT -> { timer -= dt; if (state == FLED || (state == JOINED && !joinedAsMate)) walkAway(dt); if (timer <= 0f) state = DONE }
        }
    }

    private fun start() {
        val p = w.player
        siteX = p.x + cos(planAngle) * SITE_DIST
        siteY = p.y + sin(planAngle) * SITE_DIST
        x = siteX; y = siteY; hp = MAX_HP; trust = 0f; stage = 0; age = 0f
        state = ACTIVE; hint = 4.5f; emote = 1f
        val def = Content.creatures[species]
        val count = 5 + 2 * w.director.waveIndex
        for (k in 0 until count) {
            val a = rng.angle()
            val r = 110f + 60f * rng.nextFloat()
            val id = def.tormentors[k % def.tormentors.size]
            val e = w.spawnEnemy(Content.enemyIndex(id), siteX + cos(a) * r, siteY + sin(a) * r * 0.8f, false) ?: break
            e.huntSite = true
            e.speed *= 0.9f
        }
        w.events.sfx(Sfx.CRY)
    }

    private fun active(dt: Float) {
        age += dt
        // Мягкий срок: если герою некогда (или пришёл босс), существо уходит и может встретиться в другом забеге.
        if (age >= LIFETIME || w.activeBoss() != null) { leave(); return }
        val p = w.player
        val hx = p.x - x; val hy = p.y - y
        val heroD = sqrt(hx * hx + hy * hy)
        faceLeft = hx < 0f
        // Обидчики: те, кто ещё охотится за существом (герой далеко), и те, кто уже рядом с ним.
        var hunters = 0
        var attackers = 0
        var inArea = 0   // враги где-то в области встречи
        var close = 0    // враги вплотную к самому существу
        for (i in 0 until w.enemyCount) {
            val e = w.enemies[i]
            if (!e.alive || e.spawnFade > 0f) continue
            val dx = e.x - siteX; val dy = e.y - siteY
            if (dx * dx + dy * dy <= (RING_R + 40f) * (RING_R + 40f)) inArea++
            val cx = e.x - x; val cy = e.y - y
            if (cx * cx + cy * cy <= SAFE_R * SAFE_R) close++
            if (!e.huntSite) continue
            hunters++
            val ex = e.x - x; val ey = e.y - y
            if (ex * ex + ey * ey <= ATTACK_R * ATTACK_R && e.frozenT <= 0f && e.stun <= 0f) attackers++
        }
        // Урон по существу не зависит от силы врага: герой должен успеть дойти (≥ 13 с даже при шести обидчиках).
        if (hunters > 0) hp -= minOf(attackers, MAX_ATTACKERS) * CREATURE_DPS * dt
        if (hp <= 0f) { flee(); return }
        // Кольцо привязано к месту встречи (оно нарисовано там), а не к существу, которое подходит к герою.
        val sx = p.x - siteX; val sy = p.y - siteY
        val inRing = sx * sx + sy * sy <= RING_R * RING_R
        // Доверие: полностью растёт в спокойствии, чуть медленнее — пока враги в области, но не рядом с существом,
        // и вдвое с лишним медленнее, пока вы отбиваетесь рядом с ним. Чистить всю арену не требуется.
        if (inRing) {
            val rate = if (close > 0) CLOSE_RATE else if (inArea > 0) AREA_RATE else 1f
            trust += rate * dt / TRUST_TIME
        } else trust -= dt / 45f
        trust = min(1f, max(0f, trust))
        val st = min(2, (trust * 3f).toInt())
        if (st > stage) {
            stage = st; emote = 1.1f
            w.fx.burst(x, y - 22f, 0xFFFF9EC0.toInt(), 5, 90f, 4f, 0.8f, Particle.STAR)
            w.events.sfx(Sfx.COIN)
        }
        // подходит сам: чем больше доверие, тем ближе
        if (inRing && heroD > 1f) {
            val want = APPROACH[stage]
            if (heroD > want) {
                val step = min(heroD - want, 48f * dt)
                x += hx / heroD * step; y += hy / heroD * step
            }
        }
        if (heroD > LEAVE_DIST) awayT += dt else awayT = 0f
        if (awayT > LEAVE_TIME) { leave(); return }
        if (trust >= 1f) join()
    }

    private fun join() {
        val def = Content.creatures[species]
        val f = Friend(def.id)
        f.nameIdx = nameIdx; f.trait = trait; f.metWave = (w.director.waveIndex + 1).coerceIn(1, 20); f.metRun = 0
        joined = f
        joinedAsMate = w.team.addGuest(f)
        if (joinedAsMate) w.team.find(species)?.let { it.x = x; it.y = y; it.emote = 1.5f }
        release()
        state = JOINED; outcome = OUT_JOINED; timer = if (joinedAsMate) 0.1f else 3f
        hint = 4f
        w.fx.burst(x, y - 18f, 0xFFFF9EC0.toInt(), 14, 200f, 5f, 1.0f, Particle.STAR)
        w.fx.ring(x, y, 10f, 90f, 0.5f, def.color, 7f)
        w.fx.light(x, y - 12f, 100f, def.color, 0.8f, 0.4f)
        w.events.sfx(Sfx.JOIN)
    }

    private fun flee() {
        release()
        state = FLED; outcome = OUT_FLED; timer = 3.2f; emote = 1.5f
        hint = 3.5f
        w.events.sfx(Sfx.HURT)
    }

    private fun leave() {
        release()
        state = LEFT; outcome = OUT_LEFT; timer = 0.1f; hint = 3.5f
    }

    private fun walkAway(dt: Float) {
        val p = w.player
        var dx = x - p.x; var dy = y - p.y
        val d = max(1f, sqrt(dx * dx + dy * dy)); dx /= d; dy /= d
        x += dx * 120f * dt; y += dy * 120f * dt
        faceLeft = dx < 0f
    }

    /** Обидчики больше не охотятся за существом: переключаются на героя. */
    private fun release() {
        for (i in 0 until w.enemyCount) w.enemies[i].huntSite = false
    }

    companion object {
        const val NONE = 0
        const val ACTIVE = 1
        const val JOINED = 2
        const val FLED = 3
        const val LEFT = 4
        const val DONE = 5
        const val OUT_NONE = 0
        const val OUT_JOINED = 1
        const val OUT_FLED = 2
        const val OUT_LEFT = 3

        const val SITE_DIST = 460f
        const val RING_R = 170f
        const val ATTACK_R = 56f
        /** Здоровья в секунду отнимает у существа каждый обидчик рядом (но не больше [MAX_ATTACKERS] сразу). */
        const val CREATURE_DPS = 1.1f
        const val MAX_ATTACKERS = 6
        /** Ближе этого расстояния до героя обидчики бросают существо и бегут за героем. */
        const val AGGRO = 240f
        const val MAX_HP = 90f
        const val TRUST_TIME = 10f
        /** Враг ближе этого к существу сильно замедляет рост доверия; враги дальше, но в области, замедляют слабее. */
        const val SAFE_R = 110f
        const val CLOSE_RATE = 0.4f
        const val AREA_RATE = 0.75f
        /** Через сколько секунд существо уходит, если встречу не решили. */
        const val LIFETIME = 110f
        /** За сколько секунд до ухода HUD предупреждает. */
        const val WARN_TIME = 20f
        const val LEAVE_DIST = 1300f
        const val LEAVE_TIME = 25f
        private val APPROACH = floatArrayOf(125f, 90f, 55f)
    }
}
