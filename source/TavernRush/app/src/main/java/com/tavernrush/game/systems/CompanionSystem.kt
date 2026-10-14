package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Mate
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.meta.Friend
import com.tavernrush.game.meta.Profile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Друзья-существа в забеге. Это не «ещё один автоматический ствол»: у каждого свой глагол и своя цена.
 *  Ручьеглот — обливает сухих врагов (вода открывает реакции) и тушит запал подрывника;
 *  Грозорыс — копит заряд, пока герой бежит, и прыгает на облитых врагов, а после мощного разряда отдыхает уязвимым;
 *  Камнерог — гасит удары, предназначенные герою, и отвечает тараном; щиты восстанавливаются не сразу.
 * Выбитый из строя друг не умирает: лежит и встаёт сам или быстрее, если герой рядом.
 *
 * Режимы команды (сравниваются на плейтестах): [Creatures.MODE_SOLO] — один друг и кнопка приказа;
 * [Creatures.MODE_SWAP] — ведущий в бою, резерв отдыхает, кнопка меняет их; [Creatures.MODE_DUO] — действуют оба.
 */
class CompanionSystem(private val w: World) {
    val mates = arrayOfNulls<Mate>(MAX_MATES)
    var count = 0
        private set
    var mode = Creatures.MODE_SOLO
        private set
    var swapCd = 0f
        private set
    var orderCd = 0f
        private set
    /** Вид, чьё действие выполняется сейчас: ReactionSystem засчитывает ему практику. -1 — никто. */
    var actor = -1
        private set
    /** Прибавка к числу дуг проводимости на время разряда Грозорыса (специализации). */
    var arcBonus = 0
        private set

    private var echoShieldX=0f;private var echoShieldY=0f
    var echoShieldT=0f;private set
    private var wardCredited=false
    var wardT=0f;private set
    var wardX=0f;private set
    var wardY=0f;private set
    var wardRadius=180f;private set
    private val near = IntArray(World.MAX_ENEMIES)
    private val cand = IntArray(CAND)
    private var lastX = 0f
    private var lastY = 0f
    private var threatX = 0f
    private var threatY = 0f

    fun reset() {
        for (i in mates.indices) mates[i] = null
        echoShieldT=0f;wardCredited=false;wardT=0f;wardX=0f;wardY=0f;wardRadius=180f
        count = 0; mode = Creatures.MODE_SOLO
        swapCd = 0f; orderCd = 0f; actor = -1; arcBonus = 0
        lastX = w.player.x; lastY = w.player.y
    }

    /** Собрать команду из профиля: ведущий всегда, резерв — в режимах «смена» и «двое». */
    fun setup(lead: Friend?, reserve: Friend?, mode: Int) {
        reset()
        this.mode = mode.coerceIn(0, Creatures.MODES - 1)
        if (lead != null) add(lead, true)
        if (reserve != null && reserve.id != lead?.id && this.mode != Creatures.MODE_SOLO) add(reserve, this.mode == Creatures.MODE_DUO)
    }

    /** Только что спасённый друг: выходит в бой сразу, если в команде есть место. */
    fun addGuest(f: Friend): Boolean {
        if (count >= MAX_MATES) return false
        for (i in 0 until count) if (mates[i]!!.friendId == f.id) return false
        if (count == 0) return add(f, true) != null
        if (mode == Creatures.MODE_SOLO) return false
        return add(f, mode == Creatures.MODE_DUO) != null
    }

    private fun add(f: Friend, active: Boolean): Mate? {
        val idx = Content.creatureIndex(f.id)
        if (idx < 0 || count >= MAX_MATES) return null
        val m = Mate(idx)
        m.friendId = f.id; m.nameIdx = f.nameIdx; m.trait = f.trait; m.spec = f.spec
        m.maxHp = BASE_HP * m.hpMul; m.hp = m.maxHp
        m.active = active
        m.x = w.player.x - 60f + count * 40f; m.y = w.player.y + 30f
        m.guard = if(m.species==S_CONFLUENCE)0 else maxGuard(m)
        mates[count++] = m
        return m
    }

    fun lead(): Mate? = if (count > 0) mates[0] else null
    fun reserve(): Mate? = if (count > 1) mates[1] else null
    fun find(speciesIdx: Int): Mate? { for (i in 0 until count) if (mates[i]!!.species == speciesIdx) return mates[i]; return null }

    val canSwap: Boolean get() = mode == Creatures.MODE_SWAP && count == 2
    val hasOrder: Boolean get() = mode == Creatures.MODE_SOLO && count > 0

    /** Кнопка приказа готова (для HUD): перезарядка вышла, друг на ногах, у Грозорыса есть заряд. */
    fun orderReady(): Boolean {
        val m = lead() ?: return false
        if (!hasOrder || orderCd > 0f || !m.active || m.state != Mate.FOLLOW) return false
        return when(m.species){S_LYNX->m.charge>=ORDER_CHARGE;S_CONFLUENCE->m.charge>=.6f&&m.cd<=0f;else->true}
    }

    /** Pure landing availability; does not spend charge or order cooldown. */
    val leapTargetReady:Boolean get(){val m=lead()?:return false;return m.species==S_LYNX&&m.state==Mate.FOLLOW&&m.active&&pickLeapTarget(m)!=null}

    fun swapReady(): Boolean {
        if (!canSwap || swapCd > 0f) return false
        val r = if (mates[0]!!.active) mates[1]!! else mates[0]!!
        return r.state != Mate.DOWNED
    }

    // ------------------------------------------------------------ практика (для профиля)

    /** ReactionSystem: реакция сработала. Практика идёт тому, кто облил цель, и тому, кто бьёт сейчас. */
    fun credit(by: Int, wetBy: Int) {
        if (count == 0) return
        if (by >= 0) find(by)?.let { it.practice++ }
        if (wetBy >= 0 && wetBy != by) find(wetBy)?.let { it.practice++ }
    }

    /** Итоги забега в профиль: связь (бой ≥ 45 с), практика, подъёмы. Выбор специализации открывается по практике. */
    fun finish(p: Profile) {
        for (i in 0 until count) {
            val m = mates[i]!!
            if(m.animalUid>0)continue // индивидуальная линия не кредитует одноимённый старый вид
            val f = p.friend(m.friendId) ?: continue
            if (m.activeTime >= BOND_TIME) f.bond++
            f.practice = min(f.practice + m.practice, 10_000_000)
            f.revived = min(f.revived + m.revived, 10_000_000)
            m.practice = 0; m.revived = 0; m.activeTime = 0f
        }
    }

    // ------------------------------------------------------------ шаг

    fun update(dt: Float) {
        echoShieldT=(echoShieldT-dt).coerceAtLeast(0f)
        wardT=(wardT-dt).coerceAtLeast(0f)
        if (count == 0) return
        if (swapCd > 0f) swapCd -= dt
        if (orderCd > 0f) orderCd -= dt
        val p = w.player
        val trav = min(sqrt((p.x - lastX) * (p.x - lastX) + (p.y - lastY) * (p.y - lastY)), 40f)
        lastX = p.x; lastY = p.y
        threatDir()
        for (i in 0 until count) {
            val m = mates[i]!!
            if (!m.active) { rest(m, dt); continue }
            step(m, dt, trav)
        }
    }

    /** Резерв отдыхает: здоровье восстанавливается, лежащий встаёт сам. */
    private fun rest(m: Mate, dt: Float) {
        if (m.state == Mate.DOWNED) {
            m.reviveT += dt * m.reviveMul
            if (m.reviveT >= REVIVE_TIME) { m.state = Mate.FOLLOW; m.hp = m.maxHp * 0.5f; m.reviveT = 0f }
            return
        }
        m.hp = min(m.maxHp, m.hp + m.maxHp * 0.06f * dt)
    }

    private fun step(m: Mate, dt: Float, trav: Float) {
        m.resonanceCd=(m.resonanceCd-dt).coerceAtLeast(0f);m.resonanceFlash=(m.resonanceFlash-dt).coerceAtLeast(0f)
        m.anim += dt
        m.activeTime += dt
        if (m.cd > 0f) m.cd -= dt
        if (m.invuln > 0f) m.invuln -= dt
        if (m.emote > 0f) m.emote -= dt
        when (m.state) {
            Mate.DOWNED -> { downed(m, dt); return }
            Mate.ACT -> { leap(m, dt); return }
            Mate.RECOVER -> { m.stateT -= dt; if (m.stateT <= 0f) m.state = Mate.FOLLOW }
            Mate.ENTER -> { m.stateT -= dt; if (m.stateT <= 0f) m.state = Mate.FOLLOW }
        }
        follow(m, dt)
        val oldCd=m.cd;val oldState=m.state
        if (m.state == Mate.FOLLOW || m.state == Mate.ENTER) {
            when (m.species) {
                S_STREAM -> streamAct(m)
                S_LYNX -> { m.charge = min(1f, m.charge + trav / CHARGE_DIST + dt * CHARGE_IDLE); lynxAct(m) }
                S_HORN -> hornAct(m, dt)
                S_MOSS -> mossAct(m)
                S_CONFLUENCE -> pressureAct(m,dt,trav)
            }
        }
        if(mode==Creatures.MODE_DUO&&(oldCd<=0f&&m.cd>0.5f||oldState!=Mate.ACT&&m.state==Mate.ACT))resonate(m)
        if (m.hp < m.maxHp && m.state != Mate.RECOVER) m.hp = min(m.maxHp, m.hp + m.maxHp * 0.012f * dt)
        hurt(m)
    }

    // ------------------------------------------------------------ движение

    private fun threatDir() {
        val p = w.player
        val n = w.hash.query(p.x, p.y, 220f, near)
        var sx = 0f; var sy = 0f; var c = 0
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f || e.frozenT > 0f) continue
            val dx = e.x - p.x; val dy = e.y - p.y
            val d = max(1f, sqrt(dx * dx + dy * dy))
            val k = 1f / d
            sx += dx * k; sy += dy * k; c++
        }
        if (c == 0) { threatX = 0f; threatY = 0f; return }
        val l = max(0.001f, sqrt(sx * sx + sy * sy))
        threatX = sx / l; threatY = sy / l
    }

    private val route=RegionNavigator()
    private val waypoint=FloatArray(2)
    private fun follow(m: Mate, dt: Float) {
        val p = w.player
        var fx = p.faceX; var fy = p.faceY
        val fl = sqrt(fx * fx + fy * fy)
        if (fl < 0.01f) { fx = 1f; fy = 0f } else { fx /= fl; fy /= fl }
        val side = if (mates[0] === m) 1f else -1f
        val dist = when (m.species) { S_STREAM -> 54f; S_LYNX -> 70f; else -> 52f } * m.followMul
        var tx: Float; var ty: Float
        if (m.species == S_HORN && (threatX != 0f || threatY != 0f)) {
            tx = p.x + threatX * dist; ty = p.y + threatY * dist
        } else {
            tx = p.x - fx * dist - fy * 36f * side; ty = p.y - fy * dist + fx * 36f * side
        }
        if(w.adventure.active){val a=w.adventure
            if(a.map.blocked(tx,ty,18f,a.flags)){tx=p.x;ty=p.y}
            route.next(a.map,a.flags,m.x,m.y,tx,ty,18f,waypoint);tx=waypoint[0];ty=waypoint[1]
        }
        val ax = (tx - m.x) * SPRING - m.vx * DAMP
        val ay = (ty - m.y) * SPRING - m.vy * DAMP
        m.vx += ax * dt; m.vy += ay * dt
        val sp = sqrt(m.vx * m.vx + m.vy * m.vy)
        val cap = MAX_SPEED * (if (m.state == Mate.RECOVER) 0f else 1f)
        if (sp > cap) { val k = if (sp > 0.001f) cap / sp else 0f; m.vx *= k; m.vy *= k }
        val ox=m.x;val oy=m.y
        m.x += m.vx * dt; m.y += m.vy * dt
        if(w.adventure.active){val a=w.adventure;val mask=a.map.sweep(ox,oy,m.x,m.y,18f,a.flags,waypoint);m.x=waypoint[0];m.y=waypoint[1];if(mask and 1!=0)m.vx=0f;if(mask and 2!=0)m.vy=0f}
        if (abs(m.vx) > 10f) m.faceLeft = m.vx < 0f
        val dx = p.x - m.x; val dy = p.y - m.y
        if (dx * dx + dy * dy > SNAP_DIST * SNAP_DIST) { m.x = tx; m.y = ty; m.vx = 0f; m.vy = 0f }
    }

    // ------------------------------------------------------------ урон по другу

    private fun hurt(m: Mate) {
        if (m.invuln > 0f || m.state == Mate.ACT || m.state == Mate.DOWNED) return
        val n = w.hash.query(m.x, m.y, 22f, near)
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f || e.stun > 0f || e.frozenT > 0f || e.damage <= 0f || e.huntSite) continue
            val d = e.damage * 0.5f * (if (m.state == Mate.RECOVER) 1.5f else 1f)
            m.hp -= d
            m.invuln = 0.7f
            w.fx.sparks(m.x, m.y - 8f, 0xFFFFC9A0.toInt(), 4, 180f)
            if (m.hp <= 0f) down(m)
            return
        }
    }

    private fun down(m: Mate) {
        m.hp = 0f; m.state = Mate.DOWNED; m.reviveT = 0f; m.vx = 0f; m.vy = 0f; m.hop = 0f
        w.fx.puff(m.x, m.y, 0xFFD9D2C0.toInt(), 5, 6f)
        w.events.sfx(Sfx.HURT)
    }

    private fun downed(m: Mate, dt: Float) {
        val p = w.player
        val dx = p.x - m.x; val dy = p.y - m.y
        val near = dx * dx + dy * dy <= REVIVE_R * REVIVE_R
        m.reviveT += dt * (if (near) 3f else 1f) * m.reviveMul
        if (w.rngFx.nextFloat() < dt * 1.5f) w.fx.burst(m.x, m.y - 14f, 0xFFB8C4E0.toInt(), 1, 20f, 3f, 0.7f, Particle.SPARKLE)
        if (m.reviveT >= REVIVE_TIME) {
            m.state = Mate.FOLLOW; m.hp = m.maxHp * 0.5f; m.reviveT = 0f; m.invuln = 1.5f; m.emote = 1.2f
            if (near) m.revived++
            w.fx.burst(m.x, m.y - 12f, 0xFFFF9EC0.toInt(), 8, 120f, 4f, 0.8f, Particle.STAR)
            w.events.sfx(Sfx.COIN)
        }
    }

    // ------------------------------------------------------------ приказы и смена

    /** Приказ ведущему (режим «один спутник»). */
    fun order(): Boolean {
        if (!orderReady()) return false
        val m = lead()!!
        val environment=w.adventure.applyElement(Content.creatures[m.species].element,w.player.x,w.player.y)
        val ok = environment || when (m.species) {
            S_STREAM -> tide(m, 160f, 120f)
            S_LYNX -> { val t = pickLeapTarget(m); if (t != null) { startLeap(m, t, 1.6f); true } else false }
            S_MOSS -> mossWard(m,true)
            S_CONFLUENCE -> pressure(m,true)
            else -> wall(m)
        }
        if (ok) { orderCd = ORDER_CD; m.emote = 0.8f;resonate(m) }
        return ok
    }

    /** Смена ведущего и резерва (режим «ведущий и резерв»). */
    fun swap(): Boolean {
        if (!swapReady()) return false
        val a = mates[0]!!; val b = mates[1]!!
        val out = if (a.active) a else b
        val inn = if (a.active) b else a
        out.active = false
        out.vx = 0f; out.vy = 0f
        inn.active = true
        inn.x = w.player.x - 24f; inn.y = w.player.y + 20f; inn.vx = 0f; inn.vy = 0f
        inn.state = Mate.ENTER; inn.stateT = 0.5f; inn.invuln = 0.6f; inn.emote = 0.6f
        swapCd = SWAP_CD
        w.fx.ring(inn.x, inn.y, 10f, 50f, 0.3f, Content.creatures[inn.species].color, 5f)
        entrance(inn);resonate(inn)
        w.events.sfx(Sfx.DASH)
        return true
    }

    private fun entrance(m: Mate) {
        when (m.species) {
            S_STREAM -> tide(m, 110f, 80f)
            S_LYNX -> pickLeapTarget(m)?.let { startLeap(m, it, 0.5f) }
            S_MOSS -> mossWard(m,false)
            S_CONFLUENCE -> pressure(m,false)
            else -> { m.guard = if(m.species==S_CONFLUENCE)0 else maxGuard(m); bash(m, 10f, 100f, 0.6f) }
        }
    }

    fun wardScale(x:Float,y:Float):Float {val dx=x-wardX;val dy=y-wardY;return if(wardT>0f&&dx*dx+dy*dy<=wardRadius*wardRadius)0.6f else 1f}
    fun protect(x:Float,y:Float):Float {
        val k=wardScale(x,y)
        if(k<1f){if(!wardCredited){find(S_MOSS)?.let{it.practice++};wardCredited=true};return k}
        for(i in 0 until count){val m=mates[i]!!
            if(m.species==S_CONFLUENCE&&m.active&&m.state!=Mate.DOWNED&&m.guard>0&&(x-m.x)*(x-m.x)+(y-m.y)*(y-m.y)<100f*100f){m.guard=0;return .65f}
        }
        if(echoShieldT>0f&&(x-echoShieldX)*(x-echoShieldX)+(y-echoShieldY)*(y-echoShieldY)<150f*150f){echoShieldT=0f;return .65f}
        return 1f
    }
    private fun mossAct(m:Mate){
        if(m.cd>0f||wardT>0f)return
        var n=0
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-w.player.x;val y=e.y-w.player.y;if(e.alive&&x*x+y*y<170f*170f)n++}
        if(n>=2)mossWard(m,false)
    }
    private fun mossWard(m:Mate,ordered:Boolean):Boolean {
        wardCredited=false;wardX=w.player.x;wardY=w.player.y;wardRadius=if(m.spec==1)220f else 180f
        wardT=if(ordered)if(m.spec==1)4.0f else 3.0f else 1.7f
        m.cd=12f*m.cdMul;m.emote=.8f
        if(ordered&&w.adventure.reveal(wardX,wardY,if(m.spec==2)380f else 240f))m.practice++
        w.fx.ring(wardX,wardY,10f,wardRadius,.45f,0xFFB5E799.toInt(),7f);w.events.sfx(Sfx.SHIELD)
        return true
    }

    /** One taught echo, max once / 9 s. No copied full donor kit or recursive reaction. */
    private fun resonate(m:Mate):Boolean {
        if(m.resonance !in 0..4||m.resonance==m.species||m.resonanceCd>0f||m.state==Mate.DOWNED)return false
        val p=w.player;var target:Enemy?=null;var best=220f*220f
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-p.x;val y=e.y-p.y;val d=x*x+y*y;if(e.alive&&e.spawnFade<=0f&&d<best&&exposed(p.x,p.y,e)&&(m.resonance!=S_LYNX||e.wetT>0f)){best=d;target=e}}
        when(m.resonance){
            S_CONFLUENCE->{val e=target?:return false;w.combat.hitEnemy(e,2f,p.x,p.y,140f,-1,false,Element.NONE,false)}
            S_STREAM->{val e=target?:return false;e.wetT=maxOf(e.wetT,2f);e.wetBy=m.species;w.fx.ring(e.x,e.y,6f,50f,.35f,0xff8ee6ff.toInt(),5f)}
            S_LYNX->{val e=target?:return false;w.combat.hitEnemy(e,12f,p.x,p.y,0f,-1,false,Element.NONE,false);w.fx.ring(e.x,e.y,6f,65f,.3f,0xffcab2ff.toInt(),6f)}
            S_HORN->{if(target==null)return false;echoShieldT=2f;echoShieldX=p.x;echoShieldY=p.y;w.fx.ring(p.x,p.y,12f,75f,.35f,0xffffdc9b.toInt(),6f)}
            S_MOSS->{if(target==null)return false;if(wardT<=0f){wardX=p.x;wardY=p.y;wardRadius=100f;wardT=1.2f;wardCredited=false};w.fx.ring(p.x,p.y,8f,100f,.35f,0xffb5e799.toInt(),5f)}
        }
        m.resonanceCd=9f;m.resonanceFlash=.9f;return true
    }

    private fun exposed(ax:Float,ay:Float,e:Enemy,radius:Float=5f):Boolean {
        val a=w.adventure;return !a.active||a.map.clear(ax,ay,e.x,e.y,radius,a.flags)
    }
    private fun jetHalf(m:Mate)=24f*(if(m.spec==1)1.8f else if(m.spec==2).6f else 1f)

    private fun pressureAct(m:Mate,dt:Float,trav:Float){
        m.charge=minOf(1f,m.charge+trav/500f+dt*.05f)
        if(m.guard>0){m.guardT-=dt;if(m.guardT<=0f)m.guard=0}
        if(m.charge<1f||m.cd>0f)return
        val n=w.hash.query(m.x,m.y,if(m.spec==1)150f else 110f,near)
        for(q in 0 until n){val e=w.enemies[near[q]];if(e.alive&&e.spawnFade<=0f&&exposed(m.x,m.y,e)){pressure(m,false);return}}
    }
    private fun pressure(m:Mate,shield:Boolean):Boolean {
        if(m.charge<.6f||m.cd>0f)return false
        val radius=if(m.spec==1)150f else if(m.spec==2)95f else 120f
        val n=w.hash.query(m.x,m.y,radius,near);actor=m.species
        for(q in 0 until n){val e=w.enemies[near[q]];if(e.alive&&e.spawnFade<=0f&&exposed(m.x,m.y,e))w.combat.hitEnemy(e,if(m.spec==1)2f else 4f,m.x,m.y,if(m.spec==2)230f else 150f,-1,false,Element.WATER,true)}
        actor=-1;m.charge=0f;m.cd=5f*m.cdMul;m.emote=.7f
        if(shield){m.guard=1;m.guardT=if(m.spec==1)3f else if(m.spec==2)1.2f else 2f}
        w.fx.ring(m.x,m.y,10f,radius,.4f,Content.creatures[S_CONFLUENCE].color,7f);w.events.sfx(Sfx.SHIELD)
        return true
    }
    // ------------------------------------------------------------ Ручьеглот

    private fun streamAct(m: Mate) {
        if (m.cd > 0f) return
        val range = 230f * m.rangeMul
        val n = w.hash.query(m.x, m.y, range, near)
        var best: Enemy? = null; var bd = Float.MAX_VALUE
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f || e.wetT > 0f || e.frozenT > 0f || !exposed(m.x,m.y,e,jetHalf(m))) continue
            val dx = e.x - m.x; val dy = e.y - m.y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = e }
        }
        if (best == null) { m.cd = 0.3f; return }
        jet(m, best, range)
        m.cd = 2.4f * m.cdMul
    }

    private fun jet(m: Mate, target: Enemy, range: Float) {
        var dx = target.x - m.x; var dy = target.y - m.y
        val l = max(1f, sqrt(dx * dx + dy * dy)); dx /= l; dy /= l
        val wide = m.spec == 1; val thin = m.spec == 2
        val half = jetHalf(m)
        var len = range * (if (thin) 1.4f else 1f)
        if(w.adventure.active){val a=w.adventure;len=a.map.rayLength(m.x,m.y,dx,dy,len,half,a.flags)}
        val dmg = 5f * (if (wide) 0.7f else if (thin) 1.5f else 1f)
        val push = 70f * (if (thin) 2f else 1f)
        val n = w.hash.query(m.x + dx * len * 0.5f, m.y + dy * len * 0.5f, len * 0.5f + half + 60f, near)
        actor = m.species
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f) continue
            val ex = e.x - m.x; val ey = e.y - m.y
            val t = ex * dx + ey * dy
            if (t < 0f || t > len) continue
            val perp = abs(-ex * dy + ey * dx)
            if (perp > half + e.radius) continue
            w.combat.hitEnemy(e, dmg, m.x, m.y, push, -1, false, Element.WATER, true)
        }
        actor = -1
        // струя: капли вдоль линии и всплеск на конце
        for (k in 0 until 16) {
            val t = len * (k + 1) / 17f
            w.fx.particle(m.x + dx * t, m.y + dy * t - 10f, dx * 40f, dy * 40f - 10f, 0.32f, 4f, 0xFF8EE6FF.toInt(), Particle.DOT, 2f, z = 10f, vz = 20f, grav = 200f)
        }
        w.fx.ring(m.x + dx * len, m.y + dy * len, 6f, half + 14f, 0.25f, 0xFF8EE6FF.toInt(), 4f)
        w.events.sfx(Sfx.SPLASH)
        m.faceLeft = dx < 0f
        m.stateT = 0f
        m.emote = 0.35f
    }

    private fun tide(m: Mate, radius: Float, push: Float): Boolean {
        val n = w.hash.query(m.x, m.y, radius, near)
        actor = m.species
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f || !exposed(m.x,m.y,e)) continue
            w.combat.hitEnemy(e, 2f, m.x, m.y, push, -1, false, Element.WATER, true)
        }
        actor = -1
        w.fx.ring(m.x, m.y, 12f, radius, 0.4f, 0xFF8EE6FF.toInt(), 8f)
        w.fx.burst(m.x, m.y - 6f, 0xFF8EE6FF.toInt(), 14, 260f, 5f, 0.5f)
        w.events.sfx(Sfx.SPLASH)
        return true
    }

    // ------------------------------------------------------------ Грозорыс

    private fun lynxAct(m: Mate) {
        if (m.charge < 1f || m.cd > 0f || m.state != Mate.FOLLOW) return
        val t = pickLeapTarget(m) ?: return
        startLeap(m, t, 1f)
    }

    /** Цель прыжка: облитый враг в самой плотной группе облитых (разряд проведёт), иначе ближайший. */
    private fun pickLeapTarget(m: Mate): Enemy? {
        val range = LEAP_RANGE * m.rangeMul
        val n = w.hash.query(m.x, m.y, range, near)
        var c = 0
        var nearest: Enemy? = null; var nd = Float.MAX_VALUE
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f || e.invulnT > 0f || !exposed(m.x,m.y,e,18f)) continue
            val dx = e.x - m.x; val dy = e.y - m.y
            val d = dx * dx + dy * dy
            if (d < nd) { nd = d; nearest = e }
            if (e.wetT > 0f && c < CAND) cand[c++] = near[q]
        }
        var best: Enemy? = null; var bs = -1
        for (a in 0 until c) {
            val e = w.enemies[cand[a]]
            var s = 0
            for (b in 0 until c) {
                if (a == b) continue
                val o = w.enemies[cand[b]]
                val dx = o.x - e.x; val dy = o.y - e.y
                if (dx * dx + dy * dy <= ReactionSystem.ARC_R * ReactionSystem.ARC_R && exposed(e.x,e.y,o)) s++
            }
            if (s > bs) { bs = s; best = e }
        }
        return best ?: nearest
    }

    private fun startLeap(m: Mate, e: Enemy, power: Float) {
        m.state = Mate.ACT; m.stateT = LEAP_T; m.sx = m.x; m.sy = m.y
        m.targetUid = e.uid; m.tx = e.x; m.ty = e.y; m.power = power
        m.invuln = LEAP_T + 0.1f; m.faceLeft = e.x < m.x
        w.fx.puff(m.x, m.y, 0xFFE8E0FF.toInt(), 3, 5f)
        w.fx.ring(m.x, m.y, 6f, 34f, 0.25f, Content.creatures[S_LYNX].color, 4f)
        w.events.sfx(Sfx.DASH)
    }

    private fun leap(m: Mate, dt: Float) {
        m.stateT -= dt
        var target: Enemy? = null
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e.uid == m.targetUid) { target = e; break } }
        // Committed landing point: a moving or teleported target may evade the pounce.
        val ox=m.x;val oy=m.y
        val k = (1f - max(0f, m.stateT) / LEAP_T).coerceIn(0f, 1f)
        val ease = k * k * (3f - 2f * k)
        m.x = m.sx + (m.tx - m.sx) * ease
        m.y = m.sy + (m.ty - m.sy) * ease
        if(w.adventure.active){val a=w.adventure;val ex=m.x;val ey=m.y;a.map.sweep(ox,oy,ex,ey,18f,a.flags,waypoint);m.x=waypoint[0];m.y=waypoint[1]
            if(abs(m.x-ex)>1f||abs(m.y-ey)>1f){missLeap(m);return}
        }
        m.hop = sin(k * 3.1415927f) * 30f
        if (k > 0.1f && w.rngFx.nextFloat() < 0.8f) w.fx.burst(m.x, m.y - 10f, 0xFFD8CCFF.toInt(), 1, 30f, 3f, 0.25f, Particle.SPARK)
        if (m.stateT <= 0f){val e=target;val reach=(e?.radius?:0f)+30f
            if(e==null||e.spawnFade>0f||e.invulnT>0f||(e.x-m.x)*(e.x-m.x)+(e.y-m.y)*(e.y-m.y)>reach*reach||!exposed(m.x,m.y,e)){missLeap(m);return}
            land(m,e)
        }
    }
    private fun missLeap(m:Mate){
        m.hop=0f;m.targetUid=0;m.state=Mate.RECOVER;m.stateT=.9f;m.charge=0f;m.cd=.5f;m.vx=0f;m.vy=0f
        w.fx.puff(m.x,m.y,0xFFD8CCFF.toInt(),3,4f)
    }

    private fun land(m: Mate, e: Enemy?) {
        m.hop = 0f;m.targetUid=0;m.vx=0f;m.vy=0f
        val precise = m.spec == 1; val chain = m.spec == 2
        val dmg = 22f * m.power * (if (precise) 1.7f else if (chain) 0.85f else 1f)
        val before = w.run.reactions[Reactions.CONDUCT]
        actor = m.species
        arcBonus = if (precise) -2 else if (chain) 2 else 0
        if (e != null && e.alive) w.combat.hitEnemy(e, dmg, m.x, m.y, 60f, -1, true, Element.SHOCK, true)
        actor = -1; arcBonus = 0
        val conducted = w.run.reactions[Reactions.CONDUCT] > before
        m.state = Mate.RECOVER
        // Цена сильного сочетания: после проводимости друг отдыхает дольше и уязвим.
        m.stateT = if (conducted) 1.7f else 0.9f
        m.charge = 0f
        m.cd = 0.5f
        w.fx.ring(m.x, m.y, 8f, 70f, 0.3f, 0xFFD8CCFF.toInt(), 6f)
        w.fx.burst(m.x, m.y - 8f, 0xFFD8CCFF.toInt(), 8, 280f, 4f, 0.3f, Particle.SPARK)
        w.fx.light(m.x, m.y - 10f, 90f, 0xFFB59CFF.toInt(), 0.8f, 0.2f)
        w.fx.addShake(0.12f)
        w.events.sfx(Sfx.ZAP)
    }

    // ------------------------------------------------------------ Камнерог

    private fun maxGuard(m: Mate): Int = if (m.species == S_HORN && m.spec == 1) 4 else 3

    private fun hornAct(m: Mate, dt: Float) {
        val max = maxGuard(m)
        if (m.guard < max) {
            m.guardT += dt
            if (m.guardT >= GUARD_REGEN) {
                m.guard++; m.guardT = 0f
                w.fx.burst(m.x, m.y - 18f, 0xFF9FD8FF.toInt(), 3, 60f, 3f, 0.4f, Particle.SPARKLE)
            }
        } else m.guardT = 0f
        if (m.cd > 0f) return
        val n = w.hash.query(m.x, m.y, STOMP_R, near)
        var c = 0
        for (q in 0 until n) { val e = w.enemies[near[q]]; if (e.alive && e.spawnFade <= 0f && e.frozenT <= 0f && exposed(m.x,m.y,e)) c++ }
        if (c >= 3) {
            actor = m.species
            for (q in 0 until n) {
                val e = w.enemies[near[q]]
                if (!e.alive || e.spawnFade > 0f || !exposed(m.x,m.y,e)) continue
                w.combat.hitEnemy(e, 12f, m.x, m.y, 160f, -1, false, Element.HEAVY, true)
            }
            actor = -1
            w.fx.ring(m.x, m.y, 10f, STOMP_R, 0.35f, 0xFFE6C9A0.toInt(), 8f)
            w.fx.chunks(m.x, m.y, 0xFFB08D6A.toInt(), 6, 180f, 4f, 0.5f, 8f)
            w.fx.addShake(0.12f)
            w.events.sfx(Sfx.BOOM)
            m.cd = 6f * m.cdMul
        } else m.cd = 0.4f
    }

    /**
     * Удар по герою: Камнерог рядом принимает его на щит и отвечает тараном. true — удар погашен
     * (CombatSystem считает это уклонением). Щитов ограниченное число, и они восстанавливаются не сразу.
     */
    fun absorb(): Boolean {
        for (i in 0 until count) {
            val m = mates[i]!!
            if (!m.active || m.species != S_HORN || m.state == Mate.DOWNED || m.state == Mate.ACT || m.guard <= 0) continue
            val dx = w.player.x - m.x; val dy = w.player.y - m.y
            if (dx * dx + dy * dy > GUARD_R * GUARD_R) continue
            m.guard--
            m.guardT = 0f
            w.player.invuln = max(w.player.invuln, 0.35f)
            val taran = m.spec == 2
            bash(m, 14f, 95f * (if (taran) 1.5f else 1f), if (taran) 1.3f else 0.8f)
            m.emote = 0.5f
            return true
        }
        return false
    }

    private fun wall(m: Mate): Boolean {
        m.guard = if(m.species==S_CONFLUENCE)0 else maxGuard(m)
        bash(m, 18f, 115f, 1.0f)
        return true
    }

    private fun bash(m: Mate, dmg: Float, radius: Float, stun: Float) {
        val n = w.hash.query(m.x, m.y, radius, near)
        actor = m.species
        for (q in 0 until n) {
            val e = w.enemies[near[q]]
            if (!e.alive || e.spawnFade > 0f || !exposed(m.x,m.y,e)) continue
            w.combat.hitEnemy(e, dmg, m.x, m.y, 220f, -1, false, Element.HEAVY, true)
            if (e.alive && !e.boss && e.invulnT <= 0f) e.stun = max(e.stun, stun)
        }
        actor = -1
        w.fx.ring(m.x, m.y, 10f, radius, 0.3f, 0xFF9FD8FF.toInt(), 8f)
        w.fx.ring(w.player.x, w.player.y, 14f, 44f, 0.25f, 0xFF9FD8FF.toInt(), 5f)
        w.fx.sparks(m.x, m.y - 10f, 0xFFBFE6FF.toInt(), 10, 300f)
        w.fx.addShake(0.15f)
        w.events.sfx(Sfx.SHIELD)
    }

    companion object {
        const val MAX_MATES = 2
        const val S_STREAM = 0
        const val S_LYNX = 1
        const val S_HORN = 2
        const val S_MOSS = 3
        const val S_CONFLUENCE = 4
        const val BASE_HP = 60f
        const val BOND_TIME = 45f
        const val REVIVE_TIME = 8f
        const val REVIVE_R = 90f
        const val ORDER_CD = 12f
        const val ORDER_CHARGE = 0.35f
        const val SWAP_CD = 5f
        const val CHARGE_DIST = 420f
        const val CHARGE_IDLE = 0.04f
        const val LEAP_T = 0.28f
        const val LEAP_RANGE = 340f
        const val GUARD_REGEN = 3.5f
        const val GUARD_R = 130f
        const val STOMP_R = 120f
        private const val CAND = 24
        private const val SPRING = 26f
        private const val DAMP = 8f
        private const val MAX_SPEED = 230f
        private const val SNAP_DIST = 650f
    }
}
