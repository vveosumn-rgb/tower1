package com.tavernrush.game.systems.weapons

import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import com.tavernrush.game.systems.World

/** Поведение типа оружия. Без состояния между оружиями: всё хранится в WeaponInstance и Projectile. */
abstract class WeaponBehavior(protected val w: World) {
    abstract fun update(wi: WeaponInstance, dt: Float)
    open fun onEquip(wi: WeaponInstance) {}
    /** false — снаряд закончил жизнь и возвращается в пул. */
    open fun updateProjectile(p: Projectile, dt: Float): Boolean = false

    /** Можно ли бить врага (живой и уже проявился). */
    protected fun hittable(e: Enemy): Boolean = e.alive && e.spawnFade <= 0f

    /** Physical projectiles prefer an exposed target, not a nearer one behind stone. */
    protected fun nearestVisible(x:Float,y:Float,range:Float,radius:Float):Int {
        val a=w.adventure
        if(!a.active)return w.hash.nearest(x,y,range)
        var best=-1;var dist=range*range
        for(i in 0 until w.enemyCount){val e=w.enemies[i];if(!hittable(e))continue;val dx=e.x-x;val dy=e.y-y;val d=dx*dx+dy*dy
            if(d<dist&&a.map.clear(x,y,e.x,e.y,radius,a.flags)){best=i;dist=d}
        };return best
    }

    /** Индекс случайного врага в радиусе r (или -1). Использует w.tmpIdx. */
    protected fun randomEnemyNear(x: Float, y: Float, r: Float): Int {
        val n = w.hash.query(x, y, r, w.tmpIdx)
        if (n == 0) return -1
        var k = w.rngCombat.nextInt(n)
        for (t in 0 until n) {
            val idx = w.tmpIdx[(k + t) % n]
            if (hittable(w.enemies[idx])) return idx
        }
        return -1
    }
}
