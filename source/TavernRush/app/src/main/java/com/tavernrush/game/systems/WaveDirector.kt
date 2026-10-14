package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.WaveDef
import com.tavernrush.game.content.WaveEvent
import com.tavernrush.game.content.WaveEventType
import com.tavernrush.game.core.M
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.core.TWO_PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Режиссёр волн: поток врагов по расписанию Content.waves, события (стая, кольцо, элита),
 * нарастание сложности. Враги появляются ЗА краем эталонной области обзора вокруг героя
 * (World.SPAWN_HALF_W/H), поэтому на любом устройстве не возникают на экране.
 */
class WaveDirector(private val w: World) {
    var waveIndex = 0
        private set
    var waveTime = 0f
        private set
    private var acc = 0f
    private var nextEvent = 0
    var finished = false
        private set

    /** Таймеры баннеров для HUD. */
    var bannerTimer = 0f
    var eventBannerTimer = 0f
    var eventBannerType = WaveEventType.ELITE
        private set
    var eventBannerEnemy = 0
        private set
    /** Секунды сверх 60-й секунды финальной волны, пока жив босс (с потолком, чтобы забег не зависал). */
    var overtime = 0f
        private set

    val wave: WaveDef get() = Content.waves[waveIndex]
    val waveCount: Int get() = Content.waves.size

    /** Результат pickSpawnPoint(). */
    var sx = 0f
        private set
    var sy = 0f
        private set

    fun reset() {
        overtime = 0f
        waveIndex = 0; waveTime = 0f; acc = 0.6f; nextEvent = 0; finished = false
        bannerTimer = 3f; eventBannerTimer = 0f
    }

    fun update(dt: Float) {
        if (bannerTimer > 0f) bannerTimer -= dt
        if (eventBannerTimer > 0f) eventBannerTimer -= dt
        if (finished) return
        val wv = wave
        waveTime += dt
        val evs = wv.events
        while (nextEvent < evs.size && evs[nextEvent].at <= waveTime) {
            fire(evs[nextEvent])
            nextEvent++
        }
        // Немезида приходит в 18-ю секунду волны с индексом nemWave − 2 (индекс 0-based, nemWave 1-based): для игрока это обычно
        // волна перед волной гибели, не раньше 2-й и не позже 8-й.
        if (w.nemIndex >= 0 && !w.nemSpawned && waveIndex >= (w.nemWave - 2).coerceIn(1, 7) && waveTime >= 18f) {
            if (w.spawnNemesis() != null) {
                eventBannerType = WaveEventType.NEMESIS
                eventBannerEnemy = w.nemIndex
                eventBannerTimer = 3.4f
            }
        }
        val t = M.clamp(waveTime / wv.duration, 0f, 1f)
        acc += M.lerp(wv.rateStart, wv.rateEnd, t) * dt
        while (acc >= 1f) {
            if (w.enemyCount >= wv.maxAlive) {
                acc = 1f
                break
            }
            spawnFromPool(wv)
            acc -= 1f
        }
        if (waveTime >= wv.duration) {
            if (waveIndex + 1 < Content.waves.size) {
                if (wv.tavernAfter) w.pendingTavern++
                waveIndex++
                waveTime -= wv.duration
                nextEvent = 0
                bannerTimer = 3f
                w.run.wave = waveIndex + 1
                w.events.sfx(Sfx.WAVE)
            } else if (w.activeBoss() != null && overtime < FINAL_BOSS_OVERTIME) {
                // Рассвет не наступит, пока жив финальный босс (потолок — защита от зависания забега).
                overtime += dt
                waveTime = wv.duration
            } else {
                finished = true
                w.onVictory()
            }
        }
    }

    private fun spawnFromPool(wv: WaveDef) {
        val rng = w.rngSpawn
        var r = rng.nextInt(wv.totalWeight)
        var idx = wv.pool[0].enemyIndex
        for (e in wv.pool) {
            if (r < e.weight) { idx = e.enemyIndex; break }
            r -= e.weight
        }
        pickSpawnPoint()
        w.spawnEnemy(idx, sx, sy, elite = false)
    }

    /**
     * Точка на периметре прямоугольника спавна вокруг героя. Из двух случайных точек с вероятностью FORWARD_BIAS
     * берётся та, что ближе к направлению движения (враги встречают бегущего героя, а не только догоняют).
     */
    fun pickSpawnPoint() {
        perimeterPoint()
        val p = w.player
        if (p.moving && w.rngSpawn.chance(FORWARD_BIAS)) {
            val ax = sx - p.x; val ay = sy - p.y
            val bx0 = sx; val by0 = sy
            perimeterPoint()
            val bx = sx - p.x; val by = sy - p.y
            if (ax * p.faceX + ay * p.faceY > bx * p.faceX + by * p.faceY) { sx = bx0; sy = by0 }
        }
    }

    private fun perimeterPoint() {
        val hw = World.SPAWN_HALF_W
        val hh = World.SPAWN_HALF_H
        val rng = w.rngSpawn
        var r = rng.nextFloat() * (4f * hw + 4f * hh)
        val ox: Float
        val oy: Float
        if (r < 2f * hw) { ox = -hw + r; oy = -hh } else {
            r -= 2f * hw
            if (r < 2f * hh) { ox = hw; oy = -hh + r } else {
                r -= 2f * hh
                if (r < 2f * hw) { ox = hw - r; oy = hh } else {
                    r -= 2f * hw
                    ox = -hw; oy = hh - r
                }
            }
        }
        sx = w.player.x + ox
        sy = w.player.y + oy
    }

    private fun fire(ev: WaveEvent) {
        val rng = w.rngSpawn
        val p = w.player
        when (ev.type) {
            WaveEventType.ELITE -> {
                pickSpawnPoint()
                w.spawnEnemy(ev.enemyIndex, sx, sy, elite = true)
            }
            WaveEventType.BOSS -> {
                w.spawnBoss(pickBoss())
                eventBannerTimer = 0f
            }
            WaveEventType.NEMESIS -> {}
            WaveEventType.SWARM -> {
                // Стая заходит сбоку и пролетает сквозь место, где сейчас стоит герой.
                val fromLeft = rng.chance(0.5f)
                val cx = p.x + if (fromLeft) -World.SPAWN_HALF_W - 80f else World.SPAWN_HALF_W + 80f
                val cy = p.y + rng.range(-260f, 260f)
                var dx = p.x - cx
                var dy = p.y - cy
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                dx /= d; dy /= d
                for (i in 0 until ev.count) {
                    val a = rng.angle()
                    val rr = 110f * sqrt(rng.nextFloat())
                    val e = w.spawnEnemy(ev.enemyIndex, cx + cos(a) * rr - dx * i * 6f, cy + sin(a) * rr - dy * i * 6f, elite = false) ?: break
                    e.swarm = true
                    e.dirX = dx; e.dirY = dy
                    e.speed *= 1.3f
                }
            }
            WaveEventType.RING -> {
                val n = ev.count
                val base = rng.angle()
                for (i in 0 until n) {
                    val a = base + i * TWO_PI / n
                    val rr = RING_RADIUS + rng.range(-12f, 12f)
                    val e = w.spawnEnemy(ev.enemyIndex, p.x + cos(a) * rr, p.y + sin(a) * rr, elite = false) ?: break
                    e.spawnFade = RING_TELEGRAPH
                }
            }
        }
        if (ev.type != WaveEventType.BOSS) {
            eventBannerType = ev.type
            eventBannerEnemy = ev.enemyIndex
            eventBannerTimer = 2.4f
        }
    }

    /** Слот босса: 5-я волна — Король Крыс или Банши, 10-я — Людоед или Банши; реванш приоритетнее. */
    private fun pickBoss(): Int {
        val ids = if (waveIndex >= 7) FINAL_BOSSES else MID_BOSSES
        val rematch = w.rematchBoss
        if (rematch.isNotEmpty() && ids.contains(rematch)) {
            w.rematchBoss = ""
            return Content.enemyIndex(rematch)
        }
        return Content.enemyIndex(ids[w.rngAi.nextInt(ids.size)])
    }

    companion object {
        const val FINAL_BOSS_OVERTIME = 100f
        private val MID_BOSSES = arrayOf("rat_king", "banshee")
        private val FINAL_BOSSES = arrayOf("ogre_chef", "banshee")
        /** Доля спавнов, выбирающих точку ближе к направлению бега (встречная волна). */
        const val FORWARD_BIAS = 0.7f
        const val RING_RADIUS = 340f
        const val RING_TELEGRAPH = 1.1f
    }
}
