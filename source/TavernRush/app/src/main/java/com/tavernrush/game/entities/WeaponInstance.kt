package com.tavernrush.game.entities

import com.tavernrush.game.content.WeaponDef
import com.tavernrush.game.content.WeaponKind
import com.tavernrush.game.content.WeaponStats
import com.tavernrush.game.content.Content
import com.tavernrush.game.systems.PlayerStats

/** Оружие в руках героя (слот 0..5) с итоговыми параметрами и состоянием поведения. */
class WeaponInstance(val slot: Int) {
    lateinit var def: WeaponDef
    var level = 0
    /** Итоговые параметры с учётом пассивок, блюд и улучшений таверны. */
    val stats = WeaponStats()
    /** Таймер перезарядки, с. */
    var timer = 0f
    /** Орбиты: оставшееся время кружения (>0 — активны). */
    var active = 0f
    var angle = 0f
    /** Видимость орбит 0..1 (визуал). */
    var fade = 0f
    /** Фаза пульсации (визуал аур). */
    var pulse = 0f
    var damageDealt = 0f
    var kills = 0
    var evolutionIndex = -1
    var synergyActive = false

    fun reset(def: WeaponDef) {
        this.def = def
        level = 1
        timer = 0.3f
        active = 0f; angle = 0f; fade = 0f; pulse = 0f
        damageDealt = 0f; kills = 0
        evolutionIndex = -1; synergyActive = false
    }

    fun recompute(ps: PlayerStats) {
        val b = def.statsByLevel[level - 1]
        stats.damage = b.damage * ps.might
        stats.cooldown = b.cooldown * ps.cooldown
        stats.amount = b.amount + (if (def.kind == WeaponKind.AURA) 0 else ps.amount)
        stats.area = b.area * ps.area
        stats.radius = b.radius * stats.area
        stats.speed = when (def.kind) {
            WeaponKind.PROJECTILE, WeaponKind.BOOMERANG, WeaponKind.SUMMON -> b.speed * ps.projSpeed
            else -> b.speed
        }
        stats.pierce = b.pierce
        stats.duration = b.duration * ps.duration
        stats.range = if (def.kind == WeaponKind.ORBIT) b.range * stats.area else b.range
        stats.knockback = b.knockback
        stats.chains = b.chains
        stats.slowTime = b.slowTime
        stats.slowMul = b.slowMul
        stats.healOnHit = b.healOnHit
        if (synergyActive) stats.damage *= 1.12f
        if (evolutionIndex >= 0) {
            val e = Content.evolutions[evolutionIndex]
            stats.damage *= e.might
            stats.cooldown *= e.cooldown
            stats.amount += e.amount
            stats.radius *= e.area
            stats.duration *= e.duration
            stats.chains += e.chains
            if (def.kind == WeaponKind.ORBIT) stats.range *= e.area
        }
    }
}
