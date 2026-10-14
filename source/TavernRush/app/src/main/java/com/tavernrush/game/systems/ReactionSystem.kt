package com.tavernrush.game.systems

import com.tavernrush.game.content.Element
import com.tavernrush.game.content.EnemyBehavior
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.core.Haptic
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.FloatText
import com.tavernrush.game.entities.Particle

/**
 * Реакции стихий. Состояния живут на враге: «облит», «горит», «заморожен». Стихия удара (оружие или
 * способность существа) либо накладывает состояние, либо запускает реакцию:
 *
 *  разряд  × облит      → Проводимость: дуги на соседей, у которых тоже есть вода
 *  мороз   × облит      → Заморозка: цель стоит и получает больше урона
 *  огонь   × облит      → Пар: ожог по площади и замедление
 *  удар    × заморожен  → Раскол: удвоенный удар и осколки вокруг
 *
 * Каждая реакция тратит состояние, у цели есть пауза [REACT_CD], а число реакций за тик ограничено:
 * цепочки сильные, но конечные, а толпа не превращается в сплошной шум. Урон реакций сам реакций не
 * запускает (react = false), поэтому зацикливания нет.
 */
class ReactionSystem(private val w: World) {
    private var budget = MAX_PER_TICK
    private val labelT = FloatArray(Reactions.COUNT)
    private val near = IntArray(World.MAX_ENEMIES)
    private val arc = IntArray(ARCS + 2)
    private val arcD = FloatArray(ARCS + 2)

    fun reset() {
        budget = MAX_PER_TICK
        labelT.fill(0f)
    }

    /** Таймеры состояний и ожог. Вызывается каждый тик после движения врагов. */
    fun update(dt: Float) {
        budget = MAX_PER_TICK
        for (k in labelT.indices) if (labelT[k] > 0f) labelT[k] -= dt
        val n = w.enemyCount
        for (i in 0 until n) {
            val e = w.enemies[i]
            if (!e.alive) continue
            if (e.reactCd > 0f) e.reactCd = maxOf(0f, e.reactCd - dt)
            if (e.squash > 0f) e.squash = maxOf(0f, e.squash - dt)
            if (e.wetT > 0f) {
                e.wetT = maxOf(0f, e.wetT - dt)
                if (w.rngFx.nextFloat() < dt * 2.2f) {
                    w.fx.particle(e.x + (w.rngFx.nextFloat() - 0.5f) * e.radius, e.y, 0f, 0f, 0.45f, 3f, 0xFF7FD6FF.toInt(), Particle.DOT, 0f,
                        z = e.radius * 0.8f, vz = 0f, grav = 420f)
                }
            }
            if (e.frozenT > 0f) {
                e.frozenT = maxOf(0f, e.frozenT - dt)
                if (e.frozenT <= 0f) w.fx.chunks(e.x, e.y, 0xFFCFF3FF.toInt(), 3, 110f, 3f, 0.4f, e.radius * 0.5f)
            }
            if (e.burnT > 0f) {
                e.burnT = maxOf(0f, e.burnT - dt)
                if (e.burnT <= 0f) e.burnDmg = 0f
                e.burnTick -= dt
                if (w.rngFx.nextFloat() < dt * 9f) {
                    w.fx.burst(e.x + (w.rngFx.nextFloat() - 0.5f) * e.radius, e.y - e.radius * 0.5f, 0xFFFFA24A.toInt(), 1, 40f, 4f, 0.45f, Particle.EMBER)
                }
                if (e.burnTick <= 0f) {
                    e.burnTick += BURN_STEP
                    w.combat.hitEnemy(e, e.burnDmg, e.x, e.y, 0f, -1, showNumber = false, element = Element.NONE, react = false)
                }
            }
        }
    }

    /**
     * Удар стихии [el] нанёс [dealt] урона цели [e]. [slot] — слот оружия (кредит урона и убийств) или -1.
     * Возвращает номер сработавшей реакции или [Reactions.NONE].
     */
    fun onHit(e: Enemy, el: Element, dealt: Float, slot: Int): Int {
        if (el == Element.NONE || dealt <= 0f) return Reactions.NONE
        when (el) {
            Element.WATER -> {
                e.wetT = WET_TIME
                e.wetBy = w.team.actor + 1
                if (e.def.behavior == EnemyBehavior.BOMBER && e.state == 1) {
                    w.cancelHazards(e.uid); e.state = 0; e.stateT = 0f; e.windup = 0f
                    w.fx.puff(e.x, e.y - e.radius * 0.5f, 0xFFE8F4FF.toInt(), 4, 6f)
                }
                if (e.burnT > 0f) { extinguish(e); w.fx.puff(e.x, e.y - e.radius * 0.4f, 0xFFE8F4FF.toInt(), 2, 5f) }
            }
            Element.FIRE -> {
                if (e.wetT > 0f) return react(e, Reactions.STEAM, dealt, slot)
                if (e.frozenT > 0f) { e.frozenT = 0f; w.fx.puff(e.x, e.y, 0xFFE8F4FF.toInt(), 3, 5f) }
                e.burnT = BURN_TIME
                e.burnDmg = maxOf(e.burnDmg, maxOf(1f, dealt * 0.15f))
                if (e.burnTick <= 0f) e.burnTick = BURN_STEP
            }
            Element.FROST -> {
                if (e.burnT > 0f) extinguish(e)
                if (e.wetT > 0f) return react(e, Reactions.FREEZE, dealt, slot)
            }
            Element.SHOCK -> if (e.wetT > 0f) return react(e, Reactions.CONDUCT, dealt, slot)
            Element.HEAVY -> if (e.frozenT > 0f) return react(e, Reactions.SHATTER, dealt, slot)
            else -> {}
        }
        return Reactions.NONE
    }

    private fun extinguish(e: Enemy) { e.burnT = 0f; e.burnDmg = 0f }

    private fun react(e: Enemy, id: Int, dealt: Float, slot: Int): Int {
        if (e.reactCd > 0f || budget <= 0) return Reactions.NONE
        budget--
        e.reactCd = REACT_CD
        w.run.reactions[id]++
        w.team.credit(w.team.actor, e.wetBy - 1)
        val c = w.combat
        val fx = w.fx
        when (id) {
            Reactions.CONDUCT -> {
                e.wetT = 0f
                val k = nearestWet(e, (ARCS + w.team.arcBonus).coerceIn(1, ARCS + 2))
                val col = Reactions.colors[id]
                for (j in 0 until k) {
                    val t = w.enemies[arc[j]]
                    val bolt = fx.bolt(0xFFB59CFF.toInt(), 0.2f)
                    fx.addBoltPoint(bolt, e.x, e.y - e.radius * 0.4f)
                    fx.addBoltPoint(bolt, (e.x + t.x) * 0.5f + (w.rngFx.nextFloat() - 0.5f) * 14f, (e.y + t.y) * 0.5f - 10f)
                    fx.addBoltPoint(bolt, t.x, t.y - t.radius * 0.4f)
                    t.wetT = 0f
                    c.hitEnemy(t, maxOf(3f, dealt * ARC_MUL), e.x, e.y, 30f, slot, true, Element.NONE, false)
                    fx.burst(t.x, t.y - t.radius * 0.4f, col, 4, 200f, 4f, 0.25f, Particle.SPARK)
                }
                if (e.hp > 0f) c.hitEnemy(e, maxOf(2f, dealt * 0.4f), e.x, e.y, 0f, slot, false, Element.NONE, false)
                fx.light(e.x, e.y - 10f, 90f, col, 0.8f, 0.18f)
                fx.burst(e.x, e.y - e.radius * 0.4f, col, 6, 240f, 4f, 0.3f, Particle.SPARK)
                w.events.sfx(Sfx.CONDUCT)
            }
            Reactions.FREEZE -> {
                e.wetT = 0f
                if (e.boss) {
                    if (e.hp > 0f) c.hitEnemy(e, maxOf(2f, dealt * 0.5f), e.x, e.y, 0f, slot, false, Element.NONE, false)
                } else {
                    e.frozenT = if (e.elite || e.named) FREEZE_ELITE else FREEZE_TIME
                    e.kx = 0f; e.ky = 0f
                }
                fx.ring(e.x, e.y, e.radius, e.radius * 2.4f, 0.32f, Reactions.colors[id], 5f)
                fx.chunks(e.x, e.y - e.radius * 0.3f, 0xFFCFF3FF.toInt(), 7, 170f, 4f, 0.5f, e.radius * 0.6f)
                w.events.sfx(Sfx.FREEZE)
            }
            Reactions.STEAM -> {
                e.wetT = 0f
                val n = w.hash.query(e.x, e.y, STEAM_R, near)
                val dmg = maxOf(2f, dealt * STEAM_MUL)
                for (q in 0 until n) {
                    val t = w.enemies[near[q]]
                    if (!t.alive || t.spawnFade > 0f) continue
                    if (t !== e) t.wetT = 0f
                    if (!t.boss) { t.slowMul = if (t.slowTimer > 0f) minOf(t.slowMul, 0.6f) else 0.6f; t.slowTimer = maxOf(t.slowTimer, 1.1f) }
                    if (t.hp > 0f) c.hitEnemy(t, dmg, e.x, e.y, 40f, slot, true, Element.NONE, false)
                }
                fx.ring(e.x, e.y, 10f, STEAM_R, 0.35f, Reactions.colors[id], 6f)
                fx.puff(e.x, e.y - 6f, 0xFFF2F6FF.toInt(), 7, 8f)
                fx.light(e.x, e.y - 8f, 80f, 0xFFFFD9A0.toInt(), 0.6f, 0.2f)
                w.events.sfx(Sfx.STEAM)
            }
            Reactions.SHATTER -> {
                e.frozenT = 0f
                if (e.hp > 0f) c.hitEnemy(e, maxOf(2f, dealt), e.x, e.y, 0f, slot, true, Element.NONE, false)
                val n = w.hash.query(e.x, e.y, SHATTER_R, near)
                for (q in 0 until n) {
                    val t = w.enemies[near[q]]
                    if (t === e || !t.alive || t.spawnFade > 0f) continue
                    c.hitEnemy(t, maxOf(2f, dealt * 0.5f), e.x, e.y, 60f, slot, true, Element.NONE, false)
                }
                fx.ring(e.x, e.y, 8f, SHATTER_R, 0.3f, 0xFFFFFFFF.toInt(), 6f)
                fx.chunks(e.x, e.y - e.radius * 0.3f, 0xFFE4F8FF.toInt(), 12, 300f, 5f, 0.6f, e.radius * 0.7f)
                fx.addShake(0.22f)
                w.requestHitStop(0.03f)
                w.events.sfx(Sfx.SHATTER)
                w.events.haptic(Haptic.TICK)
            }
        }
        label(id, e.x, e.y - e.radius - 6f)
        return id
    }

    /** До [max] ближайших к [e] облитых врагов в радиусе дуги; индексы в [arc], по возрастанию расстояния. */
    private fun nearestWet(e: Enemy, max: Int): Int {
        val n = w.hash.query(e.x, e.y, ARC_R, near)
        var k = 0
        for (q in 0 until n) {
            val i = near[q]
            val t = w.enemies[i]
            if (t === e || !t.alive || t.spawnFade > 0f || t.invulnT>0f || t.wetT <= 0f) continue
            if(w.adventure.active&&!w.adventure.map.clear(e.x,e.y,t.x,t.y,5f,w.adventure.flags))continue
            val dx = t.x - e.x; val dy = t.y - e.y
            val d = dx * dx + dy * dy
            if (k == max && d >= arcD[k - 1]) continue
            var pos = if (k < max) k else max - 1
            while (pos > 0 && arcD[pos - 1] > d) { arc[pos] = arc[pos - 1]; arcD[pos] = arcD[pos - 1]; pos-- }
            arc[pos] = i; arcD[pos] = d
            if (k < max) k++
        }
        return k
    }

    /** Подпись реакции над целью; не чаще раза в [LABEL_GAP] секунд на каждую реакцию (предел шума). */
    private fun label(id: Int, x: Float, y: Float) {
        if (labelT[id] > 0f) return
        labelT[id] = LABEL_GAP
        w.fx.text(x, y, id, FloatText.REACTION, Reactions.colors[id], big = true)
    }

    companion object {
        const val WET_TIME = 4.0f
        const val BURN_TIME = 2.2f
        const val BURN_STEP = 0.5f
        const val FREEZE_TIME = 1.4f
        const val FREEZE_ELITE = 0.7f
        const val REACT_CD = 0.4f
        const val MAX_PER_TICK = 6
        const val ARCS = 3
        const val ARC_R = 150f
        const val ARC_MUL = 0.7f
        const val STEAM_R = 72f
        const val STEAM_MUL = 0.6f
        const val SHATTER_R = 58f
        const val LABEL_GAP = 0.35f
    }
}
