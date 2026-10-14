package com.tavernrush.game.systems

import com.tavernrush.game.content.EnemyBehavior
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.EnemyShot
import com.tavernrush.game.entities.Hazard
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Роли врагов (R03). Каждая роль — маленький конечный автомат с видимым предупреждением:
 *  SHOOTER — держит дистанцию, «прицеливается» 0,55 с (виден луч), стреляет; паук замедляет;
 *  CHARGER — замахивается, бросается по прямой и оглушается (получает больше урона);
 *  BOMBER  — подбегает, запал с кругом поражения; убить до взрыва — безопасно; вода гасит запал и не даёт поджечь новый;
 *  SUMMONER — призывает помощников из рун; BLINKER — исчезает и появляется «за спиной».
 * update() возвращает true, если роль сама двигает врага (общий «беги к герою» пропускается).
 */
class EnemyAI(private val w: World) {

    private val route=RegionNavigator()
    private val waypoint=FloatArray(2)
    fun update(e: Enemy, dt: Float, sp: Float): Boolean {
        val a=w.adventure;val p=w.player
        // Roles approach around occlusion before aiming/arming; a charge already begun stays committed.
        if(a.active&&!e.boss&&e.state==0&&!a.map.clear(e.x,e.y,p.x,p.y,e.radius.coerceAtMost(30f),a.flags)){
            if(route.next(a.map,a.flags,e.x,e.y,p.x,p.y,e.radius.coerceAtMost(30f),waypoint)){
                val dx=waypoint[0]-e.x;val dy=waypoint[1]-e.y;val len=sqrt(dx*dx+dy*dy).coerceAtLeast(.001f);val step=minOf(sp*dt,len);e.x+=dx/len*step;e.y+=dy/len*step;e.faceLeft=dx<0f
            };return true
        }
        return when (e.def.behavior) {
        EnemyBehavior.SHOOTER -> shooter(e, dt, sp)
        EnemyBehavior.CHARGER -> charger(e, dt, sp)
        EnemyBehavior.BOMBER -> bomber(e, dt, sp)
        EnemyBehavior.SUMMONER -> summoner(e, dt, sp)
        EnemyBehavior.BLINKER -> blinker(e, dt, sp)
        EnemyBehavior.BOSS -> w.boss.update(e, dt, sp)
        else -> false
    }

    }

    private fun scale(e: Enemy): Float = if (e.def.damage > 0f) e.damage / e.def.damage else 1f

    private fun clampOut(e: Enemy) {
        val p = w.player
        val dx = e.x - p.x; val dy = e.y - p.y
        val d = sqrt(dx * dx + dy * dy)
        val contact = e.radius + p.radius + 2f
        if (d < contact && d > 0.001f) { e.x = p.x + dx / d * contact; e.y = p.y + dy / d * contact }
    }

    // ------------------------------------------------------------ стрелки

    private fun shooter(e: Enemy, dt: Float, sp: Float): Boolean {
        val p = w.player
        val def = e.def
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = dx / d; val ny = dy / d
        e.faceLeft = dx < 0f
        if (e.state == 0) {
            if (d > def.range) { e.x += nx * sp * dt; e.y += ny * sp * dt }
            else if (d < def.range * 0.62f) { e.x -= nx * sp * 0.85f * dt; e.y -= ny * sp * 0.85f * dt }
            else {
                val side = if ((e.uid and 1) == 0) 1f else -1f
                e.x += -ny * side * sp * 0.4f * dt; e.y += nx * side * sp * 0.4f * dt
            }
            clampOut(e)
            if (e.cd <= 0f && d < def.range * 1.35f) {
                e.state = 1; e.stateT = AIM_TIME; e.tx = nx; e.ty = ny
                if (e.elite) w.events.sfx(Sfx.TELEGRAPH)
            }
        } else {
            e.stateT -= dt
            e.windup = 1f - (e.stateT / AIM_TIME).coerceIn(0f, 1f)
            if (e.stateT > 0.2f) { e.tx = nx; e.ty = ny }
            if (e.stateT <= 0f) {
                fire(e)
                e.state = 0; e.windup = 0f
                e.cd = def.cooldown * (0.85f + 0.3f * w.rngAi.nextFloat())
            }
        }
        return true
    }

    private fun fire(e: Enemy) {
        val def = e.def
        val k = scale(e)
        val n = if (e.elite) 3 else 1
        val web = def.special >= 1f
        val base = atan2(e.ty, e.tx)
        for (i in 0 until n) {
            val a = base + (i - (n - 1) * 0.5f) * 0.2f
            val vx = cos(a) * def.shotSpeed; val vy = sin(a) * def.shotSpeed
            if (web) w.spawnShot(EnemyShot.WEB, e.x, e.y, vx, vy, def.shotDamage * k, 9f, 2.6f, 0xFFE8E4F2.toInt(), def.index, slowTime = 1.7f, slowMul = 0.5f)
            else w.spawnShot(EnemyShot.ARROW, e.x, e.y, vx, vy, def.shotDamage * k, 7f, def.range * 1.8f / def.shotSpeed, 0xFFE2C98A.toInt(), def.index)
        }
        w.events.sfx(Sfx.SHOT)
    }

    // ------------------------------------------------------------ кабан-таран

    private fun charger(e: Enemy, dt: Float, sp: Float): Boolean {
        val p = w.player
        val def = e.def
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = dx / d; val ny = dy / d
        when (e.state) {
            0 -> {
                e.faceLeft = dx < 0f
                e.x += nx * sp * dt; e.y += ny * sp * dt
                clampOut(e)
                if (e.cd <= 0f && d < def.range && d > 140f) {
                    e.state = 1; e.stateT = CHARGE_TELE; e.tx = nx; e.ty = ny
                    w.events.sfx(Sfx.TELEGRAPH)
                }
            }
            1 -> {
                e.stateT -= dt
                e.windup = 1f - (e.stateT / CHARGE_TELE).coerceIn(0f, 1f)
                if (e.stateT > 0.25f) { e.tx = nx; e.ty = ny }
                e.faceLeft = e.tx < 0f
                if (e.stateT <= 0f) { e.state = 2; e.stateT = CHARGE_TIME; e.dashX = e.tx; e.dashY = e.ty; e.windup = 1f }
            }
            2 -> {
                e.x += e.dashX * def.special * dt; e.y += e.dashY * def.special * dt
                e.stateT -= dt
                if ((w.ticks and 1) == 0) w.fx.puff(e.x, e.y + e.radius * 0.6f, 0xFFC9B28A.toInt(), 1, 6f)
                if (e.stateT <= 0f) {
                    e.state = 3; e.stateT = 1.1f; e.stun = 1.1f; e.windup = 0f
                    w.fx.puff(e.x, e.y + e.radius * 0.6f, 0xFFC9B28A.toInt(), 6, 8f)
                    w.fx.addShake(0.12f)
                }
            }
            else -> {
                e.stateT -= dt
                if (e.stateT <= 0f) { e.state = 0; e.cd = def.cooldown * (0.9f + 0.3f * w.rngAi.nextFloat()) }
            }
        }
        return true
    }

    // ------------------------------------------------------------ подрывник

    private fun bomber(e: Enemy, dt: Float, sp: Float): Boolean {
        val p = w.player
        val def = e.def
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        e.faceLeft = dx < 0f
        if (e.state == 0) {
            e.x += dx / d * sp * dt; e.y += dy / d * sp * dt
            clampOut(e)
            // Мокрый запал не горит: подрывник бежит рядом с героем, пока не высохнет (вода — ответ на бомбу).
            if (d < def.range && e.wetT <= 0f) {
                e.state = 1; e.stateT = def.cooldown
                w.spawnHazard(Hazard.CIRCLE, e.x, e.y + 4f, def.special, def.cooldown, def.shotDamage * scale(e), 0xFFFF7A2E.toInt(), owner = e.uid, ownerDef = def.index)
                w.events.sfx(Sfx.TELEGRAPH)
            }
        } else {
            e.stateT -= dt
            e.windup = 1f - (e.stateT / def.cooldown).coerceIn(0f, 1f)
            if (e.stateT <= 0f) { e.alive = false; e.killed = false }
        }
        return true
    }

    // ------------------------------------------------------------ шаман

    private fun summons(owner: Int): Int {
        var n = 0
        for (i in 0 until w.enemyCount) { val q = w.enemies[i]; if (q.alive && q.summoned && q.ownerUid == owner) n++ }
        return n
    }

    private fun summoner(e: Enemy, dt: Float, sp: Float): Boolean {
        val p = w.player
        val def = e.def
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = dx / d; val ny = dy / d
        e.faceLeft = dx < 0f
        if (e.state == 0) {
            if (d > def.range) { e.x += nx * sp * dt; e.y += ny * sp * dt }
            else if (d < def.range * 0.7f) { e.x -= nx * sp * 0.9f * dt; e.y -= ny * sp * 0.9f * dt }
            clampOut(e)
            if (e.cd <= 0f && summons(e.uid) < MAX_SUMMONS) {
                e.state = 1; e.stateT = 0.95f; e.dashX = w.rngAi.angle()
                val n = def.special.toInt()
                for (k in 0 until n) {
                    val a = e.dashX + k * 6.2831855f / n
                    w.spawnHazard(Hazard.CIRCLE, e.x + cos(a) * 86f, e.y + sin(a) * 60f, 20f, 0.95f, 0f, 0xFFB57CFF.toInt(), owner = e.uid, ownerDef = def.index, visual = true)
                }
                w.events.sfx(Sfx.TELEGRAPH)
            }
        } else {
            e.stateT -= dt
            e.windup = 1f - (e.stateT / 0.95f).coerceIn(0f, 1f)
            if (e.stateT <= 0f) {
                val n = def.special.toInt()
                // лимит проверяем и здесь: пока шёл замах, часть призывов уже могла появиться
                val room = MAX_SUMMONS - summons(e.uid)
                for (k in 0 until minOf(n, room)) {
                    val a = e.dashX + k * 6.2831855f / n
                    val r = w.spawnEnemy(def.summonIndex, e.x + cos(a) * 86f, e.y + sin(a) * 60f, false) ?: break
                    r.summoned = true; r.ownerUid = e.uid; r.spawnFade = 0.45f
                }
                e.state = 0; e.windup = 0f
                e.cd = def.cooldown * (0.9f + 0.2f * w.rngAi.nextFloat())
            }
        }
        return true
    }

    // ------------------------------------------------------------ призрак

    private fun blinker(e: Enemy, dt: Float, sp: Float): Boolean {
        val p = w.player
        val def = e.def
        val dx = p.x - e.x; val dy = p.y - e.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        e.faceLeft = dx < 0f
        when (e.state) {
            0 -> {
                e.x += dx / d * sp * 0.9f * dt; e.y += dy / d * sp * 0.9f * dt
                clampOut(e)
                if (e.cd <= 0f && d > 110f) {
                    e.state = 1; e.stateT = BLINK_OUT; e.invulnT = BLINK_OUT + 0.05f
                    val away = if (p.moving) atan2(-p.vy, -p.vx) else w.rngAi.angle()
                    val a = away + w.rngAi.signed() * 0.9f
                    val dist = 150f + w.rngAi.nextFloat() * (def.special - 150f).coerceAtLeast(10f)
                    e.tx = p.x + cos(a) * dist; e.ty = p.y + sin(a) * dist
                    w.spawnHazard(Hazard.CIRCLE, e.tx, e.ty + 4f, 26f, BLINK_OUT, 0f, 0xFFB8D8FF.toInt(), owner = e.uid, ownerDef = def.index, visual = true)
                }
            }
            1 -> {
                e.stateT -= dt
                e.windup = 1f - (e.stateT / BLINK_OUT).coerceIn(0f, 1f)
                if (e.stateT <= 0f) {
                    w.fx.puff(e.x, e.y, 0xFFB8D8FF.toInt(), 5, 7f)
                    e.x = e.tx; e.y = e.ty
                    w.fx.puff(e.x, e.y, 0xFFB8D8FF.toInt(), 5, 7f)
                    e.state = 2; e.stateT = 0.35f; e.invulnT = 0.35f
                }
            }
            else -> {
                e.stateT -= dt
                e.windup = (e.stateT / 0.35f).coerceIn(0f, 1f)
                if (e.stateT <= 0f) { e.state = 0; e.windup = 0f; e.cd = def.cooldown * (0.8f + 0.4f * w.rngAi.nextFloat()) }
            }
        }
        return true
    }

    companion object {
        const val AIM_TIME = 0.55f
        const val CHARGE_TELE = 0.8f
        const val CHARGE_TIME = 0.62f
        const val BLINK_OUT = 0.55f
        const val MAX_SUMMONS = 6
    }
}
