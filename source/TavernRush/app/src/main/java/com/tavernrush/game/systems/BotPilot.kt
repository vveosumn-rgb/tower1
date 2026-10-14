package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.entities.Mate
import com.tavernrush.game.content.Stat
import com.tavernrush.game.content.WeaponKind
import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.TWO_PI
import com.tavernrush.game.content.EnemyBehavior
import com.tavernrush.game.entities.Hazard
import com.tavernrush.game.entities.Pickup
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Бот для симуляций и стресс-тестов: «руки» (движение) и выбор карт. Чистый Kotlin, детерминирован от сида.
 *  KITE    — «эксперт»: уходит от плотной толпы, подбирает самоцветы, реакция 300 мс;
 *  CASUAL  — «обычный игрок»: то же, но реакция 400 мс, внимание ближе, неточный палец;
 *  PASSIVE — стоит на месте (AFK, должен умирать);
 *  WANDER  — бродит случайно.
 */
class BotPilot(val strategy: Int, val cardMode: Int, seed: Long) {
    var activeSkills=false
    private val rng = Rng(seed)
    private var decideIn = 0
    private var dirX = 0f
    private var dirY = 0f
    private val dx = FloatArray(DIRS)
    private val dy = FloatArray(DIRS)
    private val futureX = FloatArray(World.MAX_ENEMIES)
    private val futureY = FloatArray(World.MAX_ENEMIES)
    /** Угроза «вот-вот ударит» (телеграф дозревает, снаряд на подлёте) — повод для рывка. */
    private var imminent = false
    private var dashCool = 0f
    private var threatTick = 0

    init {
        for (k in 0 until DIRS) {
            val a = k * TWO_PI / DIRS
            dx[k] = cos(a); dy[k] = sin(a)
        }
    }

    fun steer(w: World) {
        if(w.adventure.active&&strategy!=PASSIVE){useTeam(w);steerAdventure(w);if(activeSkills&&teamTick==0&&w.enemyCount>0)useAdventureSkill(w);return}
        if (strategy == KITE || strategy == CASUAL) useTeam(w)
        when (strategy) {
            PASSIVE -> { w.moveX = 0f; w.moveY = 0f }
            WANDER -> {
                if (--decideIn <= 0) {
                    decideIn = 60 + rng.nextInt(90)
                    val a = rng.angle()
                    dirX = cos(a); dirY = sin(a)
                }
                w.moveX = dirX; w.moveY = dirY
            }
            CASUAL -> kite(w, 24, 180f, 0.8f)
            else -> kite(w, 12, 240f, NOISE)
        }
    }

    private val route=RegionNavigator()
    private val waypoint=FloatArray(2)
    private fun steerAdventure(w:World){
        val a=w.adventure;if(a.objective==5&&a.quickReturnReady()&&(a.nearNode()<0||a.nodeDone(a.nearNode())||a.nearNode()==5)){w.moveX=0f;w.moveY=0f;a.interact();return};if(a.bossStarted&&!a.contractDone&&(a.flags and 8==0||a.contract==2)){kite(w,12,240f,NOISE)
            var boss:com.tavernrush.game.entities.Enemy?=null
            for(i in 0 until w.enemyCount)if(w.enemies[i].uid==a.bossUid&&w.enemies[i].alive){boss=w.enemies[i];break}
            val bx=(boss?.x?:w.player.x)-w.player.x;val by=(boss?.y?:w.player.y)-w.player.y
            val reach=if(w.weaponCount>0){val weapon=w.weapons[0];when(weapon.def.kind){
                WeaponKind.ORBIT->(weapon.stats.range+weapon.stats.radius+(boss?.radius?:30f))*.8f
                WeaponKind.AURA->(weapon.stats.radius+(boss?.radius?:30f))*.8f
                WeaponKind.SUMMON->200f
                else->(weapon.stats.range*.8f).coerceIn(180f,300f)
            }}else 220f
            if(boss!=null&&(bx*bx+by*by>reach*reach||!hasAttackLine(w,boss))){
                route.next(a.map,a.flags,w.player.x,w.player.y,boss.x,boss.y,24f,waypoint)
                val x=waypoint[0]-w.player.x;val y=waypoint[1]-w.player.y;val d=sqrt(x*x+y*y).coerceAtLeast(1f);w.moveX=x/d;w.moveY=y/d
            }else if(boss!=null&&w.enemyCount<=3&&threatAt(w,w.player.x,w.player.y,false)==0f&&bx*bx+by*by>100f*100f){
                // Once in usable range, do not flee forever from a stationary recovering boss.
                w.moveX=0f;w.moveY=0f
            };return}
        val n=if(!a.nodeDone(0))0 else a.objective
        var tx=a.nodesX[n];var ty=a.nodesY[n]
        val p=w.player
        val directX=tx-p.x;val directY=ty-p.y
        if(directX*directX+directY*directY<90f*90f){w.moveX=0f;w.moveY=0f;a.interact();return}
        if(n==0){val b=a.map.walls[0];if(b.hx>b.hy)ty=b.y+(if(p.y<b.y)-1 else 1)*(b.hy+52f) else tx=b.x+(if(p.x<b.x)-1 else 1)*(b.hx+52f)}
        route.next(a.map,a.flags,p.x,p.y,tx,ty,24f,waypoint)
        val x=waypoint[0]-p.x;val y=waypoint[1]-p.y;val d=sqrt(x*x+y*y).coerceAtLeast(1f);w.moveX=x/d;w.moveY=y/d
    }
    internal fun hasAttackLine(w:World,boss:com.tavernrush.game.entities.Enemy):Boolean {
        val a=w.adventure;val weapon=if(w.weaponCount>0)w.weapons[0]else null
        val physical=weapon?.def?.kind==WeaponKind.PROJECTILE||weapon?.def?.kind==WeaponKind.BOOMERANG
        val originY=w.player.y-if(weapon?.def?.kind==WeaponKind.PROJECTILE)6f else 0f
        return a.map.clear(w.player.x,originY,boss.x,boss.y,if(physical)weapon!!.stats.radius else 5f,a.flags)
    }
    private fun useAdventureSkill(w:World){
        var target:com.tavernrush.game.entities.Enemy?=null;var best=if(w.heroIndex==3)330f*330f else 190f*190f
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-w.player.x;val y=e.y-w.player.y;val d=x*x+y*y;if(e.alive&&d<best){best=d;target=e}}
        val e=target?:return;if(w.heroIndex==3&&e.boss&&(e.state!=BossSystem.RECOVER||e.stateT<.3f))return;val x=e.x-w.player.x;val y=e.y-w.player.y;val d=sqrt(x*x+y*y).coerceAtLeast(1f)
        val mx=w.moveX;val my=w.moveY;w.moveX=x/d;w.moveY=y/d;w.heroAction.use();w.moveX=mx;w.moveY=my
    }
    /**
     * Опасность в точке: активные зоны, телеграфы, снаряды врагов, полосы рывков. 0 — безопасно.
     * При record=true выставляет imminent. Бот не «читает» чужие намерения — только то, что игрок видит на экране.
     */
    private fun threatAt(w: World, x: Float, y: Float, record: Boolean): Float {
        var t = 0f
        val pr = w.player.radius + 12f
        for (i in 0 until w.hazardCount) {
            val h = w.hazards[i]
            if (!h.alive || h.visual) continue
            val dx = x - h.x; val dy = y - h.y
            val d = sqrt(dx * dx + dy * dy)
            when (h.kind) {
                Hazard.RING -> {
                    if (h.state != 1 || h.inGap(x,y,pr)) continue
                    val rad = h.r0 + (h.r1 - h.r0) * (1f - h.life / h.maxLife)
                    val gap = abs(d - rad) - h.r - pr
                    if (gap < 60f) { t += 6f; if (record && gap < 70f) imminent = true }
                }
                Hazard.LINE -> {
                    if(h.state!=0)continue
                    val nx=cos(h.ang);val ny=sin(h.ang);val along=dx*nx+dy*ny;val across=abs(dx*ny-dy*nx)
                    if(along>=-pr&&along<=h.len+pr&&across<=h.r+pr){t+=8f;if(record&&h.delay<.45f)imminent=true}
                }
                else -> {
                    val active = h.state == 0 || h.damage > 0f
                    if (!active) continue
                    if (d < h.r + pr) {
                        t += if (h.state == 0) 8f else 3f
                        if (record && h.state == 0 && h.delay < 0.45f) imminent = true
                    } else if (d < h.r + pr + 40f) t += 1.5f
                }
            }
        }
        for (i in 0 until w.shotCount) {
            val s = w.shots[i]
            val v2 = s.vx * s.vx + s.vy * s.vy
            if (v2 < 1f) continue
            val rx = x - s.x; val ry = y - s.y
            val tt = ((rx * s.vx + ry * s.vy) / v2).coerceIn(0f, 0.7f)
            val cx = s.x + s.vx * tt - x; val cy = s.y + s.vy * tt - y
            if (sqrt(cx * cx + cy * cy) < s.radius + pr + 6f) { t += 5f; if (record && tt < 0.3f) imminent = true }
        }
        for (i in 0 until w.enemyCount) {
            val e = w.enemies[i]
            if (!e.alive || e.state != 1) continue
            val len: Float; val half: Float
            if (e.def.behavior == EnemyBehavior.CHARGER) { len = e.def.special * EnemyAI.CHARGE_TIME; half = e.radius + 12f }
            else if (e.boss && e.act == BossSystem.A_DASH) { len = BossSystem.DASH_LINE_LEN; half = BossSystem.DASH_LINE_HALF }
            else continue
            val ex = x - e.x; val ey = y - e.y
            val along = ex * e.tx + ey * e.ty
            val across = abs(ex * e.ty - ey * e.tx)
            if (along > -20f && along < len + 20f && across < half + pr) { t += 6f; if (record && e.windup > 0.55f) imminent = true }
        }
        return t
    }

    private fun kite(w: World, interval: Int, attention: Float, noise: Float) {
        if (dashCool > 0f) dashCool -= 1f / 60f
        if (strategy == KITE && ++threatTick >= 3) {
            threatTick = 0
            imminent = false
            val here = threatAt(w, w.player.x, w.player.y, true)
            val crowd = w.player.hpFrac < 0.4f && w.enemyCount > 14 && w.hash.query(w.player.x, w.player.y, 60f, w.tmpIdx) >= 4
            if ((imminent || crowd && here > 0f) && w.player.dashCharges > 0 && dashCool <= 0f && w.player.dashT <= 0f) {
                // Рывок — туда, где безопаснее всего; решение принимаем сразу, не дожидаясь периода выбора.
                var bestK = 0; var bestV = Float.MAX_VALUE
                for (k in 0 until DIRS) {
                    val v = threatAt(w, w.player.x + dx[k] * 150f, w.player.y + dy[k] * 150f, false) + rng.nextFloat() * 0.3f -
                        0.15f * (dx[k] * dirX + dy[k] * dirY)
                    if (v < bestV) { bestV = v; bestK = k }
                }
                if (bestV < here || imminent) {
                    w.dash(dx[bestK], dy[bestK])
                    dashCool = 0.7f
                }
            }
        }
        if (w.hazardCount > 0 || w.shotCount > 0) decideIn = minOf(decideIn, 4)
        if (--decideIn > 0) {
            w.moveX = dirX; w.moveY = dirY
            return
        }
        decideIn = interval
        val p = w.player
        val n = w.hash.query(p.x, p.y, attention, w.tmpIdx)
        val horizon = if (strategy == CASUAL) 0.25f else 0.35f
        for (t in 0 until n) {
            val e = w.enemies[w.tmpIdx[t]]
            val ex = p.x - e.x; val ey = p.y - e.y
            val distance = sqrt(ex * ex + ey * ey).coerceAtLeast(1f)
            val speed = e.speed * if (e.slowTimer > 0f) e.slowMul else 1f
            futureX[t] = e.x + (if (e.swarm) e.dirX else ex / distance) * speed * horizon
            futureY[t] = e.y + (if (e.swarm) e.dirY else ey / distance) * speed * horizon
        }
        // Ближайший самоцвет/монета/курица — цель сбора.
        var gx = 0f
        var gy = 0f
        var gBest = 420f * 420f
        var hasGoal = false
        for (i in 0 until w.pickupCount) {
            val q = w.pickups[i]
            val ex = q.x - p.x
            val ey = q.y - p.y
            val d2 = ex * ex + ey * ey
            var weight = d2
            if (q.kind == Pickup.HEAL && p.hpFrac < 0.6f) weight *= 0.25f
            if (q.kind == Pickup.CHEST) weight *= 0.3f
            if (weight < gBest) { gBest = weight; gx = ex; gy = ey; hasGoal = true }
        }
        if (hasGoal) {
            val gl = sqrt(gx * gx + gy * gy)
            if (gl > 0.01f) { gx /= gl; gy /= gl } else hasGoal = false
        }
        // R04: на зов о помощи идём, пока здоровье в порядке; внутри кольца стоим ближе к центру;
        // лежащего друга поднимаем, если рядом с ним спокойно.
        var goalWeight = if (p.hpFrac < 0.35f) 1.0f else 1.8f
        var holdRing = false
        val enc = w.encounter
        if (enc.active && p.hpFrac > 0.45f) {
            val ex = enc.siteX - p.x; val ey = enc.siteY - p.y
            val el = sqrt(ex * ex + ey * ey)
            if (el > EncounterSystem.RING_R * 0.8f) { gx = ex / el; gy = ey / el; hasGoal = true; goalWeight = 2.8f }
            else {
                // В кольце: держимся рядом с существом и отбиваемся, а не убегаем (иначе его никто не защитит)
                holdRing = true
                val cx = enc.x - p.x; val cy = enc.y - p.y
                val cl = sqrt(cx * cx + cy * cy)
                if (cl > 80f) { gx = cx / cl; gy = cy / cl; hasGoal = true; goalWeight = 1.6f } else hasGoal = false
            }
        }
        if (p.hpFrac > 0.5f && n <= 3) {
            for (i in 0 until w.team.count) {
                val m = w.team.mates[i]!!
                if (!m.active || m.state != Mate.DOWNED) continue
                val mx = m.x - p.x; val my = m.y - p.y
                val ml = sqrt(mx * mx + my * my)
                if (ml in 60f..520f) { gx = mx / ml; gy = my / ml; hasGoal = true; goalWeight = 2.2f }
                else if (ml < 60f) hasGoal = false
            }
        }
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (k in 0 until DIRS) {
            val qx = p.x + dx[k] * LOOK
            val qy = p.y + dy[k] * LOOK
            var danger = 0f
            for (t in 0 until n) {
                val e = w.enemies[w.tmpIdx[t]]
                if (!e.alive) continue
                val ex = e.x - qx
                val ey = e.y - qy
                var d2 = ex * ex + ey * ey
                val rr = e.radius + p.radius + 30f
                if (d2 < 1f) d2 = 1f
                val fx = futureX[t] - qx; val fy = futureY[t] - qy
                val predicted = maxOf(1f, fx * fx + fy * fy)
                val near = minOf(d2, predicted)
                // Дальние угрозы не должны вытеснять сбор опыта: считаем только ближнюю опасность.
                danger += maxOf(0f, rr * rr / near - rr * rr / (attention * attention)) * (if (e.elite) 3f else 1f)
            }
            if (w.hazardCount > 0 || w.shotCount > 0 || w.enemyCount > 0) danger += threatAt(w, p.x + dx[k] * 120f, p.y + dy[k] * 120f, false) * 0.9f
            if (holdRing) danger *= 0.4f
            var score = danger * if (p.hpFrac < 0.35f) 2.2f else 1f
            if (hasGoal) score -= goalWeight * (dx[k] * gx + dy[k] * gy)
            score -= 0.12f * (dx[k] * dirX + dy[k] * dirY)
            // Неточность «пальца»: человек не выбирает идеальное направление.
            score += rng.nextFloat() * noise
            if (score < bestScore) { bestScore = score; best = k }
        }
        if (best < 0) return
        // Если вокруг спокойно и цели нет — постоять (как реальный игрок), иначе двигаться.
        if (n == 0 && !hasGoal) { dirX = 0f; dirY = 0f } else { dirX = dx[best]; dirY = dy[best] }
        w.moveX = dirX; w.moveY = dirY
    }

    private var teamTick = 0

    /** Друзья: приказ, когда вокруг толпа; смена, когда ведущий выбит из строя или на последнем издыхании. */
    private fun useTeam(w: World) {
        if (++teamTick < 10) return
        teamTick = 0
        val t = w.team
        if (t.count == 0) return
        val p = w.player
        if (t.canSwap) {
            val lead = if (t.mates[0]!!.active) t.mates[0]!! else t.mates[1]!!
            if ((lead.state == Mate.DOWNED || lead.hp < lead.maxHp * 0.3f) && t.swapReady()) t.swap()
        } else if (t.hasOrder && t.orderReady()) {
            val n = w.hash.query(p.x, p.y, 210f, w.tmpIdx)
            if(t.lead()?.species==CompanionSystem.S_MOSS){
                val a=w.adventure;val trace=a.active&&!a.nodeDone(11)&&a.trailShownT<=0f&&kotlin.math.abs(p.x-a.nodesX[11])<220f&&kotlin.math.abs(p.y-a.nodesY[11])<220f
                if(trace||n>=2&&t.wardT<=0f)t.order()
                return
            }
            if (n >= 5 || (p.hpFrac < 0.45f && n >= 3)) t.order()
        }
    }

    /** Индекс выбранной карты из w.cards.offers. */
    fun pickCard(w: World): Int {
        val c = w.cards
        var n = c.offerCount
        if (n <= 0) return 0
        if (cardMode == GREEDY && w.player.hpFrac < 0.22f && c.reason == CardSystem.TAVERN && c.rerollsUsed == 0 && c.canReroll) {
            var hasHeal = false
            for (i in 0 until n) {
                val o = c.offers[i]
                if (o.type == CardSystem.HEAL || (o.type == CardSystem.DISH && Content.dishes[o.index].healPct > 0f)) hasHeal = true
            }
            if (!hasHeal && c.reroll()) n = c.offerCount
        }
        return when (cardMode) {
            FIRST -> 0
            RANDOM -> rng.nextInt(n)
            else -> {
                var best = 0
                var bestS = -1f
                for (i in 0 until n) {
                    val s = greedyScore(w, c.offers[i])
                    if (s > bestS) { bestS = s; best = i }
                }
                best
            }
        }
    }

    private fun greedyScore(w: World, o: CardOffer): Float {
        val hp = w.player.hpFrac
        return when (o.type) {
            CardSystem.EVOLVE -> 20f
            CardSystem.NEW_WEAPON -> {
                val kind = Content.weapons[o.index].kind
                var ownedRole = false
                for (i in 0 until w.weaponCount) if (w.weapons[i].def.kind == kind) ownedRole = true
                (if (w.weaponCount < 4) 6f else 2f) + (if (!ownedRole && kind != WeaponKind.PROJECTILE) 1.5f else 0f) +
                    (if (w.cards.reactionHint(o) != Reactions.NONE) 3.5f else 0f)
            }
            CardSystem.UP_WEAPON -> 4f + o.fromLevel * 0.3f
            CardSystem.NEW_PASSIVE, CardSystem.UP_PASSIVE -> {
                var score = statScore(Content.passives[o.index].stat, hp)
                for (e in Content.evolutions) {
                    if (e.passiveIndex != o.index || w.passiveLevels[o.index] >= e.passiveLevel) continue
                    val slot = w.findWeapon(e.weaponIndex)
                    if (slot >= 0 && w.weapons[slot].evolutionIndex < 0)
                        score += if (w.weapons[slot].level >= w.weapons[slot].def.maxLevel - 2) 6f else 2f
                }
                score
            }
            CardSystem.DISH -> {
                val d = Content.dishes[o.index]
                val st = d.stat
                if (d.healPct > 0f && hp < 0.5f) (if (hp < 0.15f) 99f else 9f) else if (st != null) statScore(st, hp) else 1.5f
            }
            CardSystem.HEAL -> if (hp < 0.15f) 99f else if (hp < 0.6f) 9f else 0.5f
            else -> 1f
        }
    }

    private fun statScore(s: Stat, hp: Float): Float = when (s) {
        Stat.MIGHT, Stat.COOLDOWN, Stat.AMOUNT -> 4.5f
        Stat.AREA, Stat.MAX_HP, Stat.ARMOR -> 3.5f
        Stat.REGEN -> if (hp < 0.6f) 4f else 2.5f
        Stat.LIFE_STEAL -> if (hp < 0.6f) 4.5f else 3f
        Stat.CRIT_CHANCE -> 4f
        Stat.DODGE -> if (hp < 0.5f) 4f else 3f
        Stat.MOVE_SPEED, Stat.MAGNET, Stat.XP_GAIN -> 2.5f
        else -> 1.5f
    }

    companion object {
        const val KITE = 0
        const val PASSIVE = 1
        const val WANDER = 2
        const val CASUAL = 3

        const val GREEDY = 0
        const val RANDOM = 1
        const val FIRST = 2

        private const val DIRS = 16
        private const val LOOK = 70f
        private const val NOISE = 0.2f

        fun strategyName(s: Int): String = when (s) { KITE -> "KITE"; PASSIVE -> "PASSIVE"; CASUAL -> "CASUAL"; else -> "WANDER" }
        fun cardName(c: Int): String = when (c) { GREEDY -> "GREEDY"; RANDOM -> "RANDOM"; else -> "FIRST" }
    }
}
