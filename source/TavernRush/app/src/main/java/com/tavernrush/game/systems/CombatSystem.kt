package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.EnemyBehavior
import com.tavernrush.game.core.Haptic
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.FloatText
import com.tavernrush.game.entities.Hazard
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.Pickup
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Урон, смерть врагов, лут, контактный урон по герою, награды за боссов и немезиду. */
class CombatSystem(private val w: World) {

    /** Удар по врагу оружием из слота [slot]. Отбрасывание — от точки (srcX, srcY). */
    fun hitEnemy(
        e: Enemy, dmg: Float, srcX: Float, srcY: Float, knock: Float, slot: Int, showNumber: Boolean = true,
        element: Element = Element.NONE, react: Boolean = true,
    ) {
        if (!e.alive || e.spawnFade > 0f || !dmg.isFinite() || dmg <= 0f) return
        if (e.invulnT > 0f) {
            // Неуязвим (вход босса, телепорт, прыжок): удар «звенит» и ничего не делает.
            w.fx.sparks(e.x, e.y - e.radius * 0.3f, 0xFFB8D8FF.toInt(), 2, 160f)
            return
        }
        val critical = w.stats.critChance > 0f && w.rngCombat.chance(w.stats.critChance)
        var actual = if (critical) dmg * 1.65f else dmg
        if (e.stun > 0f || e.frozenT > 0f) actual *= 1.5f
        if (e.affix == World.AFFIX_ARMORED) actual *= 0.6f
        if (e.shield > 0f) {
            val absorb = minOf(e.shield, actual)
            e.shield -= absorb
            actual -= absorb
            e.flash = 0.11f
            if (e.shield <= 0f) {
                w.fx.ring(e.x, e.y, e.radius, e.radius * 2.2f, 0.3f, 0xFF7FD3FF.toInt(), 5f)
                w.fx.sparks(e.x, e.y, 0xFF7FD3FF.toInt(), 10, 280f)
                w.events.sfx(Sfx.SHIELD)
            }
            if (actual <= 0.01f) return
        }
        val dealt = minOf(actual, e.hp)
        e.hp -= actual
        e.flash = 0.11f
        e.squash = SQUASH_TIME
        w.run.addDamage(dealt)
        if (slot >= 0 && slot < w.weaponCount) {
            val wi = w.weapons[slot]
            wi.damageDealt += dealt
            val s = wi.stats
            if (s.slowTime > 0f && !e.boss) {
                e.slowMul = if (e.slowTimer > 0f) minOf(e.slowMul, s.slowMul) else s.slowMul
                e.slowTimer = maxOf(e.slowTimer, s.slowTime)
            }
            if (s.healOnHit > 0f) w.heal(s.healOnHit)
        }
        if (w.stats.lifeSteal > 0f) w.heal(dealt * w.stats.lifeSteal)
        if (knock > 0f) {
            var dx = e.x - srcX
            var dy = e.y - srcY
            val d = sqrt(dx * dx + dy * dy)
            if (d > 0.001f) {
                dx /= d; dy /= d
                var k = knock * (1f - e.def.knockbackResist)
                if (e.elite || e.named) k *= 0.3f
                if (e.boss) k = 0f
                e.kx += dx * k
                e.ky += dy * k
            }
        }
        if (critical || e.elite || e.boss || e.named) w.fx.hitSpark(e.x, e.y - e.radius * 0.3f, if (critical) 0xFFFFE487.toInt() else 0xFFFFFFFF.toInt(), critical)
        else w.fx.sparks(e.x, e.y - e.radius * 0.3f, 0xFFFFF0C8.toInt(), 2, 190f)
        if (showNumber) w.fx.text(e.x, e.y - e.radius, (actual + 0.5f).toInt(), FloatText.DAMAGE, if (critical) 0xFFFFE487.toInt() else -1, big = critical || actual >= 40f)
        w.events.sfx(Sfx.HIT)
        if (react) {
            val el = if (element != Element.NONE) element else if (slot >= 0 && slot < w.weaponCount) w.weapons[slot].def.element else Element.NONE
            if (el != Element.NONE){w.reactions.onHit(e, el, dealt, slot);w.adventure.applyElement(el,e.x,e.y)}
        }
        if (e.hp <= 0f) killEnemy(e, slot)
    }

    private fun killEnemy(e: Enemy, slot: Int) {
        e.alive = false
        e.killed = true
        w.run.addKill()
        if (slot >= 0 && slot < w.weaponCount) w.weapons[slot].kills++
        if (e.def.behavior == EnemyBehavior.BOMBER) w.cancelHazards(e.uid)
        val loot = w.rngLoot
        val ps = w.pickupSystem
        ps.spawn(Pickup.XP, e.x, e.y, e.xp)
        val gRoll = loot.nextFloat()
        val itemRoll = loot.nextFloat()
        when {
            e.boss -> bossReward(e, ps)
            e.named -> nemesisReward(e, ps)
            e.elite -> {
                w.run.elitesKilled++
                ps.spawn(Pickup.CHEST, e.x, e.y, 1)
                ps.spawn(Pickup.GOLD, e.x + 14f, e.y, 10 + 2 * w.director.waveIndex + loot.nextInt(8))
                w.fx.burst(e.x, e.y, 0xFFFFD54A.toInt(), 26, 420f, 9f, 0.8f, Particle.STAR)
                w.fx.ring(e.x, e.y, e.radius, e.radius * 4f, 0.5f, 0xFFFFE08A.toInt(), 10f)
                w.fx.coinSpray(e.x, e.y, 6)
                w.fx.addShake(0.45f)
                w.fx.addAberration(0.3f)
                w.requestHitStop(0.05f)
                w.events.sfx(Sfx.BOOM)
                w.events.haptic(Haptic.BIG)
                if (e.affix == World.AFFIX_VOLATILE) {
                    val k = if (e.def.damage > 0f) e.damage / e.def.damage else 1f
                    w.spawnHazard(Hazard.CIRCLE, e.x, e.y + 4f, 78f, 0.85f, 26f * k, 0xFFFF7A2E.toInt(), ownerDef = e.def.index)
                    w.events.sfx(Sfx.TELEGRAPH)
                }
            }
            e.summoned -> { /* призванные не роняют добычу: убивать призывателя выгоднее */ }
            else -> {
                if (gRoll < e.def.goldChance) ps.spawn(Pickup.GOLD, e.x, e.y, if (e.def.xp >= 10) 1 + loot.nextInt(3) else 1)
                if (itemRoll < HEAL_CHANCE) ps.spawn(Pickup.HEAL, e.x, e.y, 1)
                else if (itemRoll < HEAL_CHANCE + MAGNET_CHANCE) ps.spawn(Pickup.MAGNET, e.x, e.y, 1)
            }
        }
        if (e.def.splitIndex >= 0) split(e)
        // Рассыпание: осколки цвета врага разлетаются дугой и отскакивают; элита — щедрее.
        val big = e.elite || e.boss || e.named
        w.fx.chunks(e.x, e.y - e.radius * 0.2f, e.def.color, if (big) 14 else 5, if (big) 280f else 200f, if (big) 7f else 5f, 0.55f, e.radius * 0.6f)
        w.fx.burst(e.x, e.y, e.def.color, if (big) 10 else 3, 220f, if (big) 8f else 5f, 0.4f)
        if (!big && e.radius > 16f) w.fx.puff(e.x, e.y + e.radius * 0.5f, 0xFFD9D2C0.toInt(), 2, 6f)
        w.events.sfx(Sfx.KILL)
    }

    private fun split(e: Enemy) {
        val def = e.def
        for (i in 0 until def.splitCount) {
            val a = (i + 0.5f) * 6.2831855f / def.splitCount + w.rngAi.nextFloat()
            val s = w.spawnEnemy(def.splitIndex, e.x + cos(a) * 10f, e.y + sin(a) * 8f, false) ?: break
            s.kx = cos(a) * 170f; s.ky = sin(a) * 140f
            s.summoned = e.summoned
            s.ownerUid = e.ownerUid
        }
    }

    private fun bossReward(e: Enemy, ps: PickupSystem) {
        val loot = w.rngLoot
        w.run.bossesKilled++
        val bi = Content.bosses.indexOf(e.def.index)
        if (bi >= 0) w.run.bossMask = w.run.bossMask or (1L shl e.def.index)
        ps.spawn(Pickup.CHEST, e.x - 26f, e.y, 1)
        ps.spawn(Pickup.CHEST, e.x + 26f, e.y + 10f, 1)
        ps.spawn(Pickup.GOLD, e.x, e.y - 10f, 30 + 5 * w.director.waveIndex)
        ps.spawn(Pickup.HEAL, e.x, e.y + 24f, 1)
        for (i in 0 until 8) ps.spawn(Pickup.GOLD, e.x + loot.signed() * 40f, e.y + loot.signed() * 30f, 2 + loot.nextInt(3))
        // зачистка арены от атак босса
        for (i in 0 until w.hazardCount) if (w.hazards[i].ownerDef == e.def.index) w.hazards[i].alive = false
        for (i in 0 until w.shotCount) if (w.shots[i].ownerDef == e.def.index) w.shots[i].life = 0f
        w.fx.ring(e.x, e.y, e.radius, 420f, 0.9f, 0xFFFFE08A.toInt(), 14f)
        w.fx.ring(e.x, e.y, e.radius * 0.5f, 260f, 0.6f, 0xFFFFFFFF.toInt(), 8f)
        w.fx.chunks(e.x, e.y, e.def.color, 36, 420f, 9f, 1.1f, 30f)
        w.fx.coinSpray(e.x, e.y, 24)
        w.fx.burst(e.x, e.y, 0xFFFFD54A.toInt(), 40, 480f, 9f, 1.2f, Particle.STAR)
        w.fx.light(e.x, e.y, 340f, 0xFFFFE08A.toInt(), 1f, 0.9f)
        w.fx.screenFlash(0.55f, 0xFFFFF2C0.toInt())
        w.fx.addShake(1f); w.fx.addAberration(0.9f)
        w.requestHitStop(0.12f)
        w.slowmoT = 1.0f
        w.events.sfx(Sfx.VICTORY)
        w.events.haptic(Haptic.BIG)
    }

    private fun nemesisReward(e: Enemy, ps: PickupSystem) {
        w.run.nemesisSlain = true
        w.run.nemesisSlainRank = e.rank
        w.run.elitesKilled++
        w.revengeTimer = 3.2f
        ps.spawn(Pickup.CHEST, e.x, e.y, 1)
        ps.spawn(Pickup.GOLD, e.x + 12f, e.y, 25 + 12 * e.rank)
        w.fx.ring(e.x, e.y, e.radius, e.radius * 5f, 0.8f, 0xFFD63A4A.toInt(), 12f)
        w.fx.burst(e.x, e.y, 0xFFFFD54A.toInt(), 30, 420f, 8f, 1f, Particle.STAR)
        w.fx.coinSpray(e.x, e.y, 12)
        w.fx.screenFlash(0.3f, 0xFFD63A4A.toInt())
        w.fx.addShake(0.8f); w.fx.addAberration(0.5f)
        w.requestHitStop(0.1f)
        w.events.sfx(Sfx.CHEST)
        w.events.haptic(Haptic.BIG)
    }

    /** Контактный урон: враги, касающиеся героя. Обычный контакт не даёт «идеальный уклон» — только атаки. */
    fun contactDamage() {
        val p = w.player
        if (p.dead) return
        if (p.invuln > 0f && p.dodgeT <= 0f) return
        val n = w.hash.query(p.x, p.y, p.radius + 3f, w.tmpIdx)
        for (k in 0 until n) {
            val e = w.enemies[w.tmpIdx[k]]
            if (e.spawnFade > 0f || e.stun > 0f || e.frozenT > 0f || e.damage <= 0f) continue
            if (e.boss && (e.state == BossSystem.INTRO || e.state == BossSystem.PHASE)) continue
            var dmg = e.damage
            var attack = e.boss
            if (e.def.behavior == EnemyBehavior.CHARGER && e.state == 2) { dmg *= 1.5f; attack = true }
            if (e.boss && e.state == BossSystem.ACT) { dmg *= 1.3f; attack = true }
            val res = hurtPlayer(dmg, e.def.index, e.named, e.rank, e.nameSeed, 0f, 1f, attack)
            if (res == 1 && e.affix == World.AFFIX_VAMPIRIC) {
                e.hp = minOf(e.maxHp, e.hp + e.maxHp * 0.12f)
                w.fx.sparks(e.x, e.y, 0xFFE0447A.toInt(), 5, 200f)
            }
            if (res != 0) return
        }
    }

    fun damagePlayer(amount: Float, source: Enemy?) {
        hurtPlayer(amount, source?.def?.index ?: -1, source?.named ?: false, source?.rank ?: 0, source?.nameSeed ?: 0, 0f, 1f, false, source?.x?:Float.NaN,source?.y?:Float.NaN)
    }

    /**
     * Единая точка урона по герою. 0 — проигнорировано (мёртв, неуязвим), 1 — ранен, 2 — уклонился
     * (рывок или перо удачи). attack=true: удар — «атака» (снаряд, зона, рывок босса), тогда рывок даёт «идеальный уклон».
     */
    fun hurtPlayer(amount: Float, defIndex: Int, named: Boolean, rank: Int, nameSeed: Int, slowTime: Float, slowMul: Float, attack: Boolean, sourceX:Float=Float.NaN,sourceY:Float=Float.NaN, projectile:Boolean=false): Int {
        val p = w.player
        if (p.dead || w.godMode || !amount.isFinite() || amount <= 0f) return 0
        if (p.dodgeT > 0f) {
            if (attack) w.onPerfectDodge()
            return 2
        }
        if (p.invuln > 0f) return 0
        if(if(projectile)w.heroAction.blockProjectile(sourceX,sourceY) else w.heroAction.block(defIndex,sourceX,sourceY))return 2
        if (w.team.absorb()) return 2
        if (w.stats.dodge > 0f && w.rngCombat.chance(w.stats.dodge)) {
            p.invuln = 0.18f
            w.fx.ring(p.x, p.y, 20f, 65f, 0.3f, 0xFF9EEBFF.toInt(), 5f)
            return 2
        }
        var dmg = amount*w.team.protect(p.x,p.y) - w.stats.armor
        if (dmg < 1f) dmg = 1f
        p.hp -= dmg
        p.invuln = INVULN_TIME
        p.hurtFlash = 0.25f
        w.run.damageTaken += dmg
        if (slowTime > 0f) {
            if (slowTime > p.slowT) p.slowT = slowTime
            if (slowMul < p.slowMul) p.slowMul = slowMul
        }
        w.fx.text(p.x, p.y - p.radius - 10f, (dmg + 0.5f).toInt(), FloatText.PLAYER_HURT, 0xFFFF5252.toInt(), big = true)
        w.fx.addShake(0.32f)
        w.fx.screenFlash(0.35f, 0xFFFF2020.toInt())
        w.fx.sparks(p.x, p.y - 8f, 0xFFFF6A5A.toInt(), 8, 260f)
        w.events.sfx(Sfx.HURT)
        w.events.haptic(Haptic.HURT)
        if (p.hp <= 0f) {
            p.hp = 0f
            p.dead = true
            w.run.killerEnemy = defIndex
            w.run.killerNamed = named
            w.run.killerRank = rank
            w.run.killerNameSeed = nameSeed
            w.run.killerBoss = defIndex >= 0 && defIndex < Content.enemies.size && Content.enemies[defIndex].boss
            w.fx.burst(p.x, p.y, 0xFFFFFFFF.toInt(), 30, 380f, 8f, 1.0f, Particle.STAR)
            w.fx.chunks(p.x, p.y, 0xFFE9A67C.toInt(), 14, 300f, 7f, 1.0f, 16f)
            w.fx.addShake(1f)
            w.events.sfx(Sfx.DEATH)
            w.events.haptic(Haptic.BIG)
        }
        return 1
    }

    companion object {
        const val INVULN_TIME = 0.5f
        const val SQUASH_TIME = 0.09f
        const val HEAL_CHANCE = 0.006f
        const val MAGNET_CHANCE = 0.0018f
    }
}
