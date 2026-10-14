package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.core.Haptic
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.EnemyShot
import com.tavernrush.game.entities.Hazard
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Боссы с фазами и телеграфами (R03). Смена фазы — по доле здоровья: пауза с неподкупной бронёй,
 * рёв, подкрепление и новый набор атак. Каждая атака — предупреждение (круг/полоса/замах),
 * затем удар: герой всегда видит, куда бежать, а рывок через удар даёт «идеальный уклон».
 *  Король Крыс — зов крыс, рывок по красной полосе, «крысиный дождь» из кругов;
 *  Людоед-повар — удар сковородой, веер тесаков, суп-лужи, прыжок с кругом приземления;
 *  Банши — вой кольцом сфер, спираль, телепорт, зов призраков и расходящаяся кольцевая волна.
 */
class BossSystem(private val w: World) {
    private val regional=RegionalBosses(w)

    fun update(e: Enemy, dt: Float, sp0: Float): Boolean {
        val f = e.hp / e.maxHp
        val np = if (e.def.id == "ogre_chef") (if (f > 0.55f) 0 else if (f > 0.25f) 1 else 2) else (if (f > 0.62f) 0 else if (f > 0.30f) 1 else 2)
        if (np > e.stage && e.state != INTRO && e.state != PHASE) onPhase(e, np)
        when (e.def.id) {
            "rat_king" -> ratKing(e, dt, sp0)
            "ogre_chef" -> ogre(e, dt, sp0)
            "banshee" -> banshee(e, dt, sp0)
            else -> regional.update(e,dt,sp0)
        }
        return true
    }

    private fun onPhase(e: Enemy, np: Int) {
        e.stage = np
        e.state = PHASE; e.stateT = 1.15f; e.invulnT = 1.15f; e.cd = 1.5f; e.windup = 0.6f
        w.cancelHazards(e.uid)
        w.bossPhaseTimer = 2.4f
        w.fx.ring(e.x, e.y, e.radius, e.radius * 5f, 0.7f, 0xFFFFE08A.toInt(), 12f)
        w.fx.chunks(e.x, e.y, e.def.color, 16, 320f, 7f, 0.9f, 30f)
        w.fx.addShake(0.8f); w.fx.addAberration(0.6f)
        w.fx.screenFlash(0.35f, 0xFFFFE08A.toInt())
        w.requestHitStop(0.1f)
        w.events.sfx(Sfx.PHASE)
        w.events.haptic(Haptic.BIG)
        w.run.bossPhases++
    }

    // ------------------------------------------------------------ общие помощники

    private fun k(e: Enemy): Float = if (e.def.damage > 0f) e.damage / e.def.damage else 1f

    private fun aimed(e: Enemy): Float = atan2(w.player.y - e.y, w.player.x - e.x)

    private fun summonCount(e: Enemy): Int {
        var n = 0
        for (i in 0 until w.enemyCount) { val q = w.enemies[i]; if (q.alive && q.summoned && q.ownerUid == e.uid) n++ }
        return n
    }

    private fun summonAround(e: Enemy, defId: String, n: Int, ring: Float, cap: Int = 26) {
        val di = Content.enemyIndex(defId)
        if (di < 0) return
        val base = w.rngAi.angle()
        for (i in 0 until n) {
            if (summonCount(e) >= (if(w.adventure.active)minOf(cap,w.adventure.summonCap) else cap)) break
            val a = base + i * 6.2831855f / n
            val r = w.spawnEnemy(di, e.x + cos(a) * ring, e.y + sin(a) * ring * 0.75f, false) ?: break
            r.summoned = true; r.ownerUid = e.uid; r.spawnFade = 0.5f
        }
    }

    private fun runes(e: Enemy, n: Int, ring: Float, delay: Float, color: Int) {
        val base = w.rngAi.angle()
        for (i in 0 until n) {
            val a = base + i * 6.2831855f / n
            w.spawnHazard(Hazard.CIRCLE, e.x + cos(a) * ring, e.y + sin(a) * ring * 0.75f, 18f, delay, 0f, color, owner = e.uid, ownerDef = e.def.index, visual = true)
        }
    }

    private fun fireOrb(e: Enemy, a: Float, speed: Float, dmg: Float) {
        w.spawnShot(EnemyShot.ORB, e.x, e.y - 4f, cos(a) * speed, sin(a) * speed, dmg, 9f, 4.8f, 0xFFD9B8FF.toInt(), e.def.index)
    }

    // ------------------------------------------------------------ Король Крыс

    private fun ratKing(e: Enemy, dt: Float, sp0: Float) {
        val p = w.player
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = dx / d; val ny = dy / d
        val sp = sp0 * (1f + 0.22f * e.stage)
        e.faceLeft = dx < 0f
        when (e.state) {
            INTRO -> { e.stateT -= dt; e.windup = 1f - (e.stateT / 1.9f).coerceIn(0f, 1f); if (e.stateT <= 0f) { e.state = IDLE; e.cd = 1.0f; e.windup = 0f } }
            PHASE -> {
                e.stateT -= dt
                if (e.stateT <= 0f) { e.state = IDLE; e.windup = 0f; summonAround(e, "rat", 4 + 2 * e.stage, e.radius + 70f) }
            }
            IDLE -> {
                if (d > e.radius + p.radius + 46f) { e.x += nx * sp * dt; e.y += ny * sp * dt }
                if (e.cd <= 0f) startRat(e, nx, ny)
            }
            TELE -> {
                e.stateT -= dt
                when (e.act) {
                    A_DASH -> {
                        e.windup = 1f - (e.stateT / DASH_TELE).coerceIn(0f, 1f)
                        if (e.stateT > 0.22f) { e.tx = nx; e.ty = ny }
                        if (e.stateT <= 0f) { e.state = ACT; e.stateT = 0.55f; e.dashX = e.tx; e.dashY = e.ty; e.windup = 1f; w.events.sfx(Sfx.ROAR) }
                    }
                    A_CALL -> {
                        e.windup = 1f - (e.stateT / 0.9f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) { summonAround(e, "rat", 6 + 2 * e.stage, e.radius + 66f); e.state = IDLE; e.windup = 0f; e.cd = 3.2f - 0.5f * e.stage }
                    }
                    else -> { // A_RAIN
                        e.windup = 1f - (e.stateT / 0.5f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) { rain(e); e.state = IDLE; e.windup = 0f; e.cd = 3.0f }
                    }
                }
            }
            ACT -> {
                e.x += e.dashX * 560f * dt; e.y += e.dashY * 560f * dt
                e.stateT -= dt
                if ((w.ticks and 1) == 0) w.fx.puff(e.x, e.y + e.radius * 0.5f, 0xFFC9B28A.toInt(), 2, 9f)
                if (e.stateT <= 0f) {
                    e.windup = 0f
                    if (e.stage == 2 && e.attack % 2 == 0) { e.state = TELE; e.act = A_DASH; e.stateT = 0.55f; e.tx = nx; e.ty = ny }
                    else { e.state = RECOVER; e.stateT = 1.1f; e.stun = 1.1f; w.fx.addShake(0.25f); w.fx.puff(e.x, e.y + 10f, 0xFFC9B28A.toInt(), 8, 10f) }
                }
            }
            else -> { // RECOVER
                e.stateT -= dt
                if (e.stateT <= 0f) { e.state = IDLE; e.cd = 1.6f }
            }
        }
    }

    private fun startRat(e: Enemy, nx: Float, ny: Float) {
        val seq = when (e.stage) {
            0 -> RAT_SEQ0
            1 -> RAT_SEQ1
            else -> RAT_SEQ2
        }
        val a = seq[e.attack % seq.size]
        e.attack++
        e.act = a
        e.state = TELE
        when (a) {
            A_DASH -> { e.stateT = DASH_TELE - 0.1f * e.stage; e.tx = nx; e.ty = ny; w.events.sfx(Sfx.TELEGRAPH) }
            A_CALL -> { e.stateT = 0.9f; runes(e, 6 + 2 * e.stage, e.radius + 66f, 0.9f, 0xFFFFB04A.toInt()); w.events.sfx(Sfx.TELEGRAPH) }
            else -> { e.stateT = 0.5f }
        }
    }

    private fun rain(e: Enemy) {
        val p = w.player
        val kk = k(e)
        for (i in 0 until 4) {
            val x: Float; val y: Float
            if (i == 0) { x = p.x + p.vx * 0.5f; y = p.y + p.vy * 0.5f } else {
                val a = w.rngAi.angle(); val r = 90f + 140f * w.rngAi.nextFloat()
                x = p.x + cos(a) * r; y = p.y + sin(a) * r
            }
            w.spawnHazard(Hazard.CIRCLE, x, y, 64f, 1.0f + 0.14f * i, 22f * kk, 0xFFFF4D4D.toInt(), ownerDef = e.def.index)
        }
        w.events.sfx(Sfx.TELEGRAPH)
    }

    // ------------------------------------------------------------ Людоед-повар

    private fun ogre(e: Enemy, dt: Float, sp0: Float) {
        val p = w.player
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = dx / d; val ny = dy / d
        val sp = sp0 * (if (e.stage >= 2) 1.4f else 1f)
        val tm = if (e.stage >= 2) 0.8f else 1f
        e.faceLeft = dx < 0f
        when (e.state) {
            INTRO -> { e.stateT -= dt; e.windup = 1f - (e.stateT / 1.9f).coerceIn(0f, 1f); if (e.stateT <= 0f) { e.state = IDLE; e.cd = 1.2f; e.windup = 0f } }
            PHASE -> { e.stateT -= dt; if (e.stateT <= 0f) { e.state = IDLE; e.windup = 0f; summonAround(e, "goblin", 3 + e.stage, e.radius + 80f, 12) } }
            IDLE -> {
                if (d > e.radius + p.radius + 90f) { e.x += nx * sp * dt; e.y += ny * sp * dt }
                if (e.cd <= 0f) startOgre(e, nx, ny, tm)
            }
            TELE -> {
                e.stateT -= dt
                val tele = e.stateT
                when (e.act) {
                    A_SLAM -> {
                        e.windup = 1f - (tele / (1.05f * tm)).coerceIn(0f, 1f)
                        if (tele <= 0f) { e.state = ACT; e.stateT = 0.35f; w.fx.addShake(0.5f); w.fx.chunks(e.tx, e.ty, 0xFFB89A7A.toInt(), 14, 320f, 7f, 0.8f, 10f); w.events.sfx(Sfx.BOOM) }
                    }
                    A_CLEAVE -> {
                        e.windup = 1f - (tele / (0.75f * tm)).coerceIn(0f, 1f)
                        if (tele > 0.2f) { e.tx = nx; e.ty = ny }
                        if (tele <= 0f) {
                            val n = if (e.stage >= 2) 5 else 3
                            val base = atan2(e.ty, e.tx)
                            val kk = k(e)
                            for (i in 0 until n) {
                                val a = base + (i - (n - 1) * 0.5f) * 0.26f
                                w.spawnShot(EnemyShot.CLEAVER, e.x, e.y - 10f, cos(a) * 330f, sin(a) * 330f, 18f * kk, 13f, 2.3f, 0xFFDDE3EE.toInt(), e.def.index, spin = 11f)
                            }
                            w.events.sfx(Sfx.SHOT)
                            e.state = RECOVER; e.stateT = 0.45f; e.windup = 0f; e.cd = 2.4f * tm
                        }
                    }
                    A_SOUP -> {
                        e.windup = 1f - (tele / (0.85f * tm)).coerceIn(0f, 1f)
                        if (tele <= 0f) {
                            val n = 3 + (if (e.stage >= 2) 2 else 0)
                            val kk = k(e)
                            for (i in 0 until n) {
                                val a = w.rngAi.angle(); val r = 60f + 150f * w.rngAi.nextFloat()
                                w.spawnHazard(Hazard.CIRCLE, p.x + cos(a) * r, p.y + sin(a) * r, 62f, 0.85f, 0f, 0xFF7ED957.toInt(), life = 6.5f, owner = 0, ownerDef = e.def.index, slow = 0.55f, tick = 0.5f, damage = 7f * kk)
                            }
                            w.events.sfx(Sfx.TELEGRAPH)
                            e.state = RECOVER; e.stateT = 0.5f; e.windup = 0f; e.cd = 3.0f * tm
                        }
                    }
                    else -> { // A_LEAP
                        e.windup = 1f - (tele / (1.35f * tm)).coerceIn(0f, 1f)
                        val jump = 0.45f
                        if (tele < jump) {
                            val t = 1f - tele / jump
                            e.x = e.dashX + (e.tx - e.dashX) * t
                            e.y = e.dashY + (e.ty - e.dashY) * t
                        }
                        if (tele <= 0f) {
                            e.x = e.tx; e.y = e.ty
                            w.fx.addShake(0.8f); w.fx.chunks(e.x, e.y, 0xFFB89A7A.toInt(), 18, 380f, 8f, 0.9f, 12f)
                            w.fx.ring(e.x, e.y, 30f, 280f, 0.5f, 0xFFFFE08A.toInt(), 10f)
                            w.events.sfx(Sfx.BOOM)
                            summonAround(e, if (e.stage >= 2) "archer" else "goblin", 2 + e.stage / 2, e.radius + 70f, 12)
                            e.state = RECOVER; e.stateT = 0.9f; e.stun = 0.9f; e.windup = 0f; e.cd = 2.6f * tm
                        }
                    }
                }
            }
            ACT -> { e.stateT -= dt; if (e.stateT <= 0f) { e.state = IDLE; e.windup = 0f; e.cd = 2.3f * tm } }
            else -> { e.stateT -= dt; if (e.stateT <= 0f) e.state = IDLE }
        }
    }

    private fun startOgre(e: Enemy, nx: Float, ny: Float, tm: Float) {
        val seq = when (e.stage) {
            0 -> OGRE_SEQ0
            1 -> OGRE_SEQ1
            else -> OGRE_SEQ2
        }
        val a = seq[e.attack % seq.size]
        e.attack++
        e.act = a
        e.state = TELE
        val kk = k(e)
        when (a) {
            A_SLAM -> {
                e.stateT = 1.05f * tm
                e.tx = e.x + nx * (e.radius + 70f); e.ty = e.y + ny * (e.radius + 70f)
                w.spawnHazard(Hazard.CIRCLE, e.tx, e.ty + 6f, 125f, e.stateT, 34f * kk, 0xFFFF5A3A.toInt(), owner = e.uid, ownerDef = e.def.index)
                w.events.sfx(Sfx.TELEGRAPH)
            }
            A_CLEAVE -> { e.stateT = 0.75f * tm; e.tx = nx; e.ty = ny; w.events.sfx(Sfx.TELEGRAPH) }
            A_SOUP -> { e.stateT = 0.85f * tm; w.events.sfx(Sfx.TELEGRAPH) }
            else -> {
                e.stateT = 1.35f * tm
                e.tx = w.player.x; e.ty = w.player.y
                e.dashX = e.x; e.dashY = e.y
                e.invulnT = e.stateT
                w.spawnHazard(Hazard.CIRCLE, e.tx, e.ty + 6f, 140f, e.stateT, 38f * kk, 0xFFFF5A3A.toInt(), owner = e.uid, ownerDef = e.def.index)
                w.events.sfx(Sfx.TELEGRAPH)
            }
        }
    }

    // ------------------------------------------------------------ Банши

    private fun banshee(e: Enemy, dt: Float, sp0: Float) {
        val p = w.player
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = dx / d; val ny = dy / d
        val sp = sp0 * (1f + 0.15f * e.stage)
        val kk = k(e)
        e.faceLeft = dx < 0f
        when (e.state) {
            INTRO -> { e.stateT -= dt; e.windup = 1f - (e.stateT / 1.9f).coerceIn(0f, 1f); if (e.stateT <= 0f) { e.state = IDLE; e.cd = 1.0f; e.windup = 0f } }
            PHASE -> { e.stateT -= dt; if (e.stateT <= 0f) { e.state = IDLE; e.windup = 0f; summonAround(e, "ghost", 2 + e.stage, e.radius + 80f, 8) } }
            IDLE -> {
                if (d < 240f) { e.x -= nx * sp * dt; e.y -= ny * sp * dt }
                else if (d > 380f) { e.x += nx * sp * dt; e.y += ny * sp * dt }
                else { e.x += -ny * sp * 0.45f * dt; e.y += nx * sp * 0.45f * dt }
                if (e.cd <= 0f) startBanshee(e)
            }
            TELE -> {
                e.stateT -= dt
                when (e.act) {
                    A_WAIL -> {
                        e.windup = 1f - (e.stateT / 0.7f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) {
                            val n = 14 + 4 * e.stage
                            val off = w.rngAi.angle()
                            for (i in 0 until n) fireOrb(e, off + i * 6.2831855f / n, 190f, 12f * kk)
                            w.events.sfx(Sfx.ROAR); w.fx.addShake(0.3f)
                            e.state = IDLE; e.windup = 0f; e.cd = 1.8f
                        }
                    }
                    A_SPIRAL -> {
                        e.windup = 1f - (e.stateT / 0.5f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) { e.state = ACT; e.stateT = 2.6f; e.dashX = aimed(e); e.attack += 0; e.tx = 0f }
                    }
                    A_BLINK -> {
                        e.windup = 1f - (e.stateT / 0.6f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) {
                            w.fx.puff(e.x, e.y, 0xFFC9B8FF.toInt(), 8, 9f)
                            e.x = e.tx; e.y = e.ty
                            w.fx.puff(e.x, e.y, 0xFFC9B8FF.toInt(), 8, 9f)
                            e.state = ACT; e.stateT = 0.3f; e.invulnT = 0.3f; e.act = A_BLINK_IN
                        }
                    }
                    A_CALL -> {
                        e.windup = 1f - (e.stateT / 0.9f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) { summonAround(e, "ghost", 3, e.radius + 70f, 8); e.state = IDLE; e.windup = 0f; e.cd = 3.0f }
                    }
                    else -> { // A_WAVE
                        e.windup = 1f - (e.stateT / 0.8f).coerceIn(0f, 1f)
                        if (e.stateT <= 0f) {
                            val h = w.spawnHazard(Hazard.RING, e.x, e.y + 6f, 36f, 0.01f, 0f, 0xFFE8D8FF.toInt(), life = 2.7f, ownerDef = e.def.index, r1 = 560f, damage = 22f * kk)
                            if (h != null) { h.r0 = 30f; h.r1 = 560f }
                            w.events.sfx(Sfx.ROAR); w.fx.addShake(0.4f)
                            e.state = IDLE; e.windup = 0f; e.cd = 3.0f
                        }
                    }
                }
            }
            ACT -> {
                e.stateT -= dt
                if (e.act == A_SPIRAL) {
                    e.tx -= dt
                    if (e.tx <= 0f) {
                        e.tx = 0.085f
                        e.dashX += 0.5f
                        fireOrb(e, e.dashX, 205f, 11f * kk)
                        if (e.stage >= 2) fireOrb(e, e.dashX + 3.1416f, 205f, 11f * kk)
                    }
                }
                if (e.stateT <= 0f) { e.state = IDLE; e.windup = 0f; e.cd = if (e.act == A_SPIRAL) 2.2f else 1.2f }
            }
            else -> { e.stateT -= dt; if (e.stateT <= 0f) e.state = IDLE }
        }
    }

    private fun startBanshee(e: Enemy) {
        val seq = when (e.stage) {
            0 -> BAN_SEQ0
            1 -> BAN_SEQ1
            else -> BAN_SEQ2
        }
        val a = seq[e.attack % seq.size]
        e.attack++
        e.act = a
        e.state = TELE
        when (a) {
            A_WAIL -> e.stateT = 0.7f
            A_SPIRAL -> e.stateT = 0.5f
            A_BLINK -> {
                e.stateT = 0.6f; e.invulnT = 0.65f
                val ang = w.rngAi.angle()
                e.tx = w.player.x + cos(ang) * 320f; e.ty = w.player.y + sin(ang) * 320f
                w.spawnHazard(Hazard.CIRCLE, e.tx, e.ty + 4f, 30f, 0.6f, 0f, 0xFFC9B8FF.toInt(), owner = e.uid, ownerDef = e.def.index, visual = true)
            }
            A_CALL -> { e.stateT = 0.9f; runes(e, 3, e.radius + 70f, 0.9f, 0xFFB8D8FF.toInt()) }
            else -> { e.stateT = 0.8f }
        }
        w.events.sfx(Sfx.TELEGRAPH)
    }

    companion object {
        const val IDLE = 0
        const val TELE = 1
        const val ACT = 2
        const val RECOVER = 3
        const val INTRO = 4
        const val PHASE = 5

        const val A_DASH = 1
        const val A_CALL = 2
        const val A_RAIN = 3
        const val A_SLAM = 4
        const val A_CLEAVE = 5
        const val A_SOUP = 6
        const val A_LEAP = 7
        const val A_WAIL = 8
        const val A_SPIRAL = 9
        const val A_BLINK = 10
        const val A_WAVE = 11
        const val A_BLINK_IN = 12

        const val DASH_TELE = 0.9f

        private val RAT_SEQ0 = intArrayOf(A_CALL, A_DASH, A_CALL, A_DASH)
        private val RAT_SEQ1 = intArrayOf(A_RAIN, A_DASH, A_CALL, A_RAIN)
        private val RAT_SEQ2 = intArrayOf(A_DASH, A_RAIN, A_CALL, A_DASH)
        private val OGRE_SEQ0 = intArrayOf(A_SLAM, A_CLEAVE, A_SOUP, A_SLAM)
        private val OGRE_SEQ1 = intArrayOf(A_LEAP, A_CLEAVE, A_SLAM, A_SOUP)
        private val OGRE_SEQ2 = intArrayOf(A_LEAP, A_SLAM, A_CLEAVE, A_SOUP)
        private val BAN_SEQ0 = intArrayOf(A_WAIL, A_BLINK, A_SPIRAL, A_BLINK)
        private val BAN_SEQ1 = intArrayOf(A_WAIL, A_CALL, A_SPIRAL, A_WAVE)
        private val BAN_SEQ2 = intArrayOf(A_SPIRAL, A_WAVE, A_WAIL, A_CALL)

        /** Длина и полуширина красной полосы предупреждения рывка (рендер читает те же числа). */
        const val DASH_LINE_LEN = 540f
        const val DASH_LINE_HALF = 40f
    }
}
