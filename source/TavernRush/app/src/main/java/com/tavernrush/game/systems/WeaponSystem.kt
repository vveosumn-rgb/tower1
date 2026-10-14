package com.tavernrush.game.systems

import com.tavernrush.game.content.WeaponKind
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.weapons.AuraBehavior
import com.tavernrush.game.systems.weapons.BombBehavior
import com.tavernrush.game.systems.weapons.BoomerangBehavior
import com.tavernrush.game.systems.weapons.ChainBehavior
import com.tavernrush.game.systems.weapons.OrbitBehavior
import com.tavernrush.game.systems.weapons.ProjectileBehavior
import com.tavernrush.game.systems.weapons.WeaponBehavior
import com.tavernrush.game.systems.weapons.BeamBehavior
import com.tavernrush.game.systems.weapons.TrapBehavior
import com.tavernrush.game.systems.weapons.SummonBehavior

/**
 * Оружие героя: одно поведение на тип (WeaponKind), состояние — в WeaponInstance/Projectile.
 * Новый тип: значение в WeaponKind + класс в systems/weapons/ + ветка в createBehavior (when без else).
 */
class WeaponSystem(private val w: World) {
    private val byKind: Array<WeaponBehavior> = Array(WeaponKind.entries.size) { createBehavior(WeaponKind.entries[it]) }

    private fun createBehavior(kind: WeaponKind): WeaponBehavior = when (kind) {
        WeaponKind.PROJECTILE -> ProjectileBehavior(w)
        WeaponKind.ORBIT -> OrbitBehavior(w)
        WeaponKind.AURA -> AuraBehavior(w)
        WeaponKind.CHAIN -> ChainBehavior(w)
        WeaponKind.BOOMERANG -> BoomerangBehavior(w)
        WeaponKind.BOMB -> BombBehavior(w)
        WeaponKind.BEAM -> BeamBehavior(w)
        WeaponKind.TRAP -> TrapBehavior(w)
        WeaponKind.SUMMON -> SummonBehavior(w)
    }

    /** Поведение, которое ведёт снаряд данного вида. */
    private fun projectileOwner(kind: Int): WeaponBehavior = when (kind) {
        Projectile.KNIFE -> byKind[WeaponKind.PROJECTILE.ordinal]
        Projectile.PAN -> byKind[WeaponKind.BOOMERANG.ordinal]
        Projectile.TRAP -> byKind[WeaponKind.TRAP.ordinal]
        Projectile.RAVEN -> byKind[WeaponKind.SUMMON.ordinal]
        else -> byKind[WeaponKind.BOMB.ordinal]
    }

    fun onEquip(wi: WeaponInstance) {
        byKind[wi.def.kind.ordinal].onEquip(wi)
    }

    fun update(dt: Float) {
        for (i in 0 until w.weaponCount) {
            val wi = w.weapons[i]
            byKind[wi.def.kind.ordinal].update(wi, dt)
        }
        val ps = w.projectiles
        var i = 0
        while (i < w.projectileCount) {
            val p = ps[i]
            if (!projectileOwner(p.kind).updateProjectile(p, dt)) {
                p.alive = false
                w.projectileCount--
                ps[i] = ps[w.projectileCount]
                ps[w.projectileCount] = p
            } else i++
        }
    }
}
