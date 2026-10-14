package com.tavernrush.game.systems

import com.tavernrush.game.entities.Hazard
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Телеграфы и зоны на земле, снаряды врагов. Телеграф сначала предупреждает, потом бьёт один раз;
 * лужа и волна действуют дольше. Попадание в окно рывка не ранит и даёт «идеальный уклон».
 */
class HazardSystem(private val w: World) {

    fun update(dt: Float) {
        updateHazards(dt)
        updateShots(dt)
    }

    private fun removeHazard(i: Int) {
        val h = w.hazards[i]
        h.alive = false
        w.hazardCount--
        w.hazards[i] = w.hazards[w.hazardCount]
        w.hazards[w.hazardCount] = h
    }

    private fun updateHazards(dt: Float) {
        var i = 0
        while (i < w.hazardCount) {
            val h = w.hazards[i]
            if (!h.alive) { removeHazard(i); continue }
            if (h.state == 0) {
                h.delay -= dt
                if (h.delay <= 0f) {
                    detonate(h)
                    if (h.kind == Hazard.RING) {
                        h.state = 1; h.hit = false
                        if (h.life <= 0f) h.life = 2f
                        h.maxLife = h.life
                    } else if (h.life > 0f) {
                        h.state = 1; h.tickT = 0.15f
                    } else { removeHazard(i); continue }
                }
            } else {
                h.life -= dt
                if (h.life <= 0f) { removeHazard(i); continue }
                if (h.kind == Hazard.RING) ring(h) else zone(h, dt)
            }
            i++
        }
    }

    private fun hurt(h: Hazard, amount: Float): Int =
        w.combat.hurtPlayer(amount, h.ownerDef, false, 0, 0, 0f, 1f, true)

    private fun detonate(h: Hazard) {
        val p = w.player
        val fx = w.fx
        when (h.kind) {
            Hazard.LINE -> {
                val dx = kotlin.math.cos(h.ang); val dy = kotlin.math.sin(h.ang)
                val px = p.x - h.x; val py = p.y - h.y
                val along = px * dx + py * dy
                val across = abs(px * dy - py * dx)
                if (!h.visual && h.burst > 0f && along >= -p.radius && along <= h.len + p.radius && across <= h.r + p.radius * 0.5f) hurt(h, h.burst)
                fx.sparks(h.x + dx * h.len * 0.5f, h.y + dy * h.len * 0.5f, h.color, 8, 280f)
            }
            Hazard.RING -> { /* волна начинает расти; урон — по мере прохождения */ }
            else -> {
                val dx = p.x - h.x; val dy = p.y - h.y
                if (!h.visual) {
                    fx.ring(h.x, h.y, h.r * 0.25f, h.r, 0.35f, h.color, 8f)
                    fx.chunks(h.x, h.y, h.color, if (h.r > 90f) 12 else 7, 260f, 6f, 0.7f, 8f)
                    fx.light(h.x, h.y, h.r * 1.7f, h.color, 1f, 0.25f)
                    if (h.r > 100f) fx.addShake(0.3f) else if (h.burst > 0f) fx.addShake(0.15f)
                    w.events.sfx(com.tavernrush.game.core.Sfx.BOOM)
                    if (h.burst > 0f && dx * dx + dy * dy <= (h.r + p.radius * 0.5f) * (h.r + p.radius * 0.5f)) hurt(h, h.burst)
                } else fx.puff(h.x, h.y, h.color, 3, 5f)
            }
        }
    }

    private fun ring(h: Hazard) {
        val p = w.player
        val t = 1f - (h.life / h.maxLife).coerceIn(0f, 1f)
        val rad = h.r0 + (h.r1 - h.r0) * t
        h.r0 = h.r0
        if (h.hit || p.dead || h.inGap(p.x,p.y,p.radius*.4f)) return
        val dx = p.x - h.x; val dy = p.y - h.y
        val d = sqrt(dx * dx + dy * dy)
        if (abs(d - rad) <= h.r + p.radius * 0.4f) {
            val res = hurt(h, h.damage)
            if (res != 0) h.hit = true
        }
    }

    private fun zone(h: Hazard, dt: Float) {
        val p = w.player
        if (p.dead || h.visual) return
        val dx = p.x - h.x; val dy = p.y - h.y
        val inside = dx * dx + dy * dy <= (h.r + p.radius * 0.3f) * (h.r + p.radius * 0.3f)
        if (inside && h.slow > 0f) {
            if (p.slowT < 0.35f) p.slowT = 0.35f
            if (h.slow < p.slowMul) p.slowMul = h.slow
        }
        h.tickT -= dt
        if (h.tickT <= 0f) {
            h.tickT = h.tick
            if (inside && h.damage > 0f) hurt(h, h.damage)
        }
    }

    // ------------------------------------------------------------ снаряды врагов

    /** Earliest contact of a moving circular shot with the current player body.
     * The supplied segment already ends before stone, so a wall cannot skip a nearer hit. */
    internal fun firstBodyHit(ax:Float,ay:Float,bx:Float,by:Float,px:Float,py:Float,r:Float):Float {
        val x=ax-px;val y=ay-py;val c=x*x+y*y-r*r
        if(c<=0f)return 0f
        val dx=bx-ax;val dy=by-ay;val a=dx*dx+dy*dy
        if(a<=.000001f)return -1f
        val b=x*dx+y*dy;val disc=b*b-a*c
        if(disc<0f)return -1f
        val t=(-b-sqrt(disc))/a
        return if(t in 0f..1f)t else -1f
    }

    private fun updateShots(dt: Float) {
        val p = w.player
        var i = 0
        while (i < w.shotCount) {
            val s = w.shots[i]
            val oldX=s.x;val oldY=s.y
            s.life -= dt
            s.x += s.vx * dt; s.y += s.vy * dt
            s.rot += s.spin * dt
            var dead = s.life <= 0f
            var stone=false
            if(!dead&&w.adventure.active&&!w.adventure.map.clear(oldX,oldY,s.x,s.y,s.radius,w.adventure.flags)){
                stone=true
                val dx=s.x-oldX;val dy=s.y-oldY;val length=sqrt(dx*dx+dy*dy)
                val reach=if(length>.001f)w.adventure.map.rayLength(oldX,oldY,dx/length,dy/length,length,s.radius,w.adventure.flags) else 0f
                val t=if(length>.001f)reach/length else 0f;s.x=oldX+dx*t;s.y=oldY+dy*t
            }
            if (!dead && !p.dead) {
                val rr = s.radius + p.radius * .55f
                val hit=firstBodyHit(oldX,oldY,s.x,s.y,p.x,p.y,rr)
                if(hit>=0f){
                    val hx=oldX+(s.x-oldX)*hit;val hy=oldY+(s.y-oldY)*hit
                    val shield=w.heroAction.shield
                    val res=w.combat.hurtPlayer(s.damage,s.ownerDef,false,0,0,s.slowTime,s.slowMul,true,hx,hy,projectile=true)
                    val intercepted=res==2&&shield>0f&&w.heroAction.shield==0f
                    if(res==1||intercepted){
                        dead=true;s.x=hx;s.y=hy
                        w.fx.hitSpark(hx,hy,if(intercepted)0xfface8ff.toInt() else s.color,false)
                    }
                }
            }
            if(!dead&&stone){dead=true;w.fx.hitSpark(s.x,s.y,0xffc5b69a.toInt(),false)}
            if (!dead) {
                val dx = p.x - s.x; val dy = p.y - s.y
                if (dx * dx + dy * dy > 1500f * 1500f) dead = true
            }
            if (dead) {
                s.alive = false
                w.shotCount--
                w.shots[i] = w.shots[w.shotCount]
                w.shots[w.shotCount] = s
                continue
            }
            i++
        }
    }
}
