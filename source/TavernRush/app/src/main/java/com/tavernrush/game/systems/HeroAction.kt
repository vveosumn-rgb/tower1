package com.tavernrush.game.systems
import com.tavernrush.game.content.Element
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import kotlin.math.sqrt
/** Разные действия и цена выбора; смена не отменяет восстановление. */
class HeroAction(private val w:World) {
    var style=0;private set
    var cooldown=0f;private set
    var shield=0f;private set
    var shieldAge=0f;private set
    var sx=1f;private set
    var sy=0f;private set
    var fieldT=0f;private set
    var fieldX=0f;private set
    var fieldY=0f;private set
    var hookT=0f;private set
    var hookHits=0;private set
    var hookTargetUid=0;private set
    val hookEndX:Float get()=tx
    val hookEndY:Float get()=ty
    private var hx=0f;private var hy=0f;private var tx=0f;private var ty=0f;private var pulse=0f
    fun reset(){style=0;cooldown=0f;shield=0f;shieldAge=0f;fieldT=0f;hookT=0f;hookTargetUid=0;hookHits=0;pulse=0f;sx=1f;sy=0f}
    fun switch():Boolean{if(cooldown>0f||shield>0f||fieldT>0f||w.player.dead||w.victory)return false;style=1-style;return true}
    fun label():String=when(w.heroIndex){0->if(style==0)"Выпад" else "Размах";1->if(style==0)"Парировать" else "Прикрыть";2->if(style==0)"Руна льда" else "Руна грозы";else->if(style==0)"Крюк" else "Ловушка"}
    fun use():Boolean {
        if(cooldown>0f||w.player.dead||w.victory)return false
        val p=w.player;val len=sqrt(w.moveX*w.moveX+w.moveY*w.moveY);sx=if(len>0.01f)w.moveX/len else p.faceX;sy=if(len>0.01f)w.moveY/len else p.faceY
        when(w.heroIndex){
            0->{val range=if(style==0)190f else 145f;for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-p.x;val y=e.y-p.y;val d=sqrt(x*x+y*y).coerceAtLeast(1f);if(e.alive&&d<range&&(style==1||(x*sx+y*sy)/d>0.3f))w.combat.hitEnemy(e,if(style==0)24f else 17f,p.x,p.y,180f,-1,element=Element.HEAVY)};w.adventure.applyElement(Element.HEAVY,p.x+sx*70f,p.y+sy*70f);w.fx.ring(p.x+sx*60f,p.y+sy*60f,8f,range*0.5f,0.25f,0xFFFFDC9B.toInt(),5f);cooldown=if(style==0)1.6f else 2.1f}
            1->{shield=if(style==0)0.45f else 1.2f;shieldAge=0f;cooldown=2.8f;w.events.sfx(Sfx.SHIELD)}
            2->{fieldX=p.x+sx*110f;fieldY=p.y+sy*110f;fieldT=3f;pulse=0f;cooldown=4.5f}
            else->{if(style==0){if(!useHook())return false}else{fieldX=p.x;fieldY=p.y;fieldT=4f;pulse=0f;cooldown=5f}}
        };w.events.sfx(Sfx.CLICK);return true
    }
    /** Query only: does not consume cooldown or change last aim. */
    val counterReady:Boolean get(){
        if(w.heroIndex!=3||style!=0||cooldown>0f||hookT>0f||w.player.dead||w.victory)return false
        val len=sqrt(w.moveX*w.moveX+w.moveY*w.moveY)
        return findHookAnchor(if(len>.01f)w.moveX/len else w.player.faceX,if(len>.01f)w.moveY/len else w.player.faceY)!=null
    }
    private fun findHookAnchor(ax:Float,ay:Float):Enemy? {
        val p=w.player;val a=w.adventure;var boss:Enemy?=null;var best=330f*330f
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-p.x;val y=e.y-p.y;val d=x*x+y*y
            if(!e.alive||!e.boss||e.spawnFade>0f||e.invulnT>0f||e.state!=BossSystem.RECOVER||e.stateT<.3f||d<=140f*140f||d>=best)continue
            val len=sqrt(d);if((x*ax+y*ay)/len<.5f)continue
            val gap=e.radius+p.radius+32f;val ex=e.x-x/len*gap;val ey=e.y-y/len*gap
            if(a.active&&(!a.map.clear(p.x,p.y,ex,ey,20f,a.flags)||!a.map.clear(p.x,p.y,e.x,e.y,5f,a.flags)))continue
            best=d;boss=e
        };return boss
    }
    /** A committed, directional counter. The large enemy is an anchor, never displaced. */
    private fun useHook():Boolean {
        val p=w.player;val a=w.adventure;val boss=findHookAnchor(sx,sy)
        hookTargetUid=0
        if(boss!=null){val x=boss.x-p.x;val y=boss.y-p.y;val len=sqrt(x*x+y*y);val gap=boss.radius+p.radius+32f
            beginHook(boss.x-x/len*gap,boss.y-y/len*gap);hookTargetUid=boss.uid
        }else{
            val e=findPullTarget(sx,sy)
            if(e!=null){
                val ox=e.x;val oy=e.y;val dx=e.x-p.x;val dy=e.y-p.y;val distance=sqrt(dx*dx+dy*dy)
                val remaining=maxOf(distance*.45f,e.radius+p.radius+16f)
                e.x=p.x+dx/distance*remaining;e.y=p.y+dy/distance*remaining
                e.kx=0f;e.ky=0f;e.stun=maxOf(e.stun,.25f)
                val rope=w.fx.bolt(0xFF9CFFD4.toInt(),.18f);rope.straight=true;rope.width=4f
                w.fx.addBoltPoint(rope,p.x,p.y);w.fx.addBoltPoint(rope,ox,oy)
                w.fx.ring(e.x,e.y,8f,e.radius+18f,.18f,0xFF9CFFD4.toInt(),3f)
            }else{
                var target=-1;var best=330f*330f
                if(a.active)for(i in a.nodesX.indices){if(!a.visibleNode(i))continue;val x=a.nodesX[i]-p.x;val y=a.nodesY[i]-p.y;val d=x*x+y*y
                    if(d>150f*150f&&d<best&&(x*sx+y*sy)/sqrt(d)>=.5f&&a.map.clear(p.x,p.y,a.nodesX[i],a.nodesY[i],20f,a.flags)){target=i;best=d}}
                if(target<0)return false
                beginHook(a.nodesX[target],a.nodesY[target])
            }
        }
        w.fx.ring(p.x,p.y,10f,70f,.3f,0xFF9CFFD4.toInt(),4f);cooldown=2.5f;return true
    }
    /** Ordinary targets obey the same directional intent; neither rope nor body crosses stone. */
    private fun findPullTarget(ax:Float,ay:Float):Enemy? {
        val p=w.player;val a=w.adventure;var result:Enemy?=null;var best=330f*330f
        for(i in 0 until w.enemyCount){val e=w.enemies[i];if(!e.alive||e.boss||e.spawnFade>0f||e.invulnT>0f)continue
            val dx=e.x-p.x;val dy=e.y-p.y;val d=dx*dx+dy*dy
            if(!d.isFinite()||d<=0f||d>=best)continue
            val distance=sqrt(d);val minimum=e.radius+p.radius+16f
            if(distance<=minimum+1f||(dx*ax+dy*ay)/distance<.5f)continue
            val remaining=maxOf(distance*.45f,minimum);val ex=p.x+dx/distance*remaining;val ey=p.y+dy/distance*remaining
            if(a.active&&(!a.map.clear(p.x,p.y,e.x,e.y,5f,a.flags)||!a.map.clear(e.x,e.y,ex,ey,e.radius,a.flags)))continue
            best=d;result=e
        };return result
    }
    private fun beginHook(x:Float,y:Float){val p=w.player;hx=p.x;hy=p.y;tx=x;ty=y;hookT=.25f;p.vx=0f;p.vy=0f;p.dodgeT=.18f}
    private fun finishHook(){
        val uid=hookTargetUid;hookTargetUid=0;if(uid==0)return
        val p=w.player;val a=w.adventure
        for(i in 0 until w.enemyCount){val e=w.enemies[i];if(!e.alive||e.uid!=uid||e.state!=BossSystem.RECOVER)continue
            val x=e.x-p.x;val y=e.y-p.y;val range=e.radius+p.radius+60f
            if(x*x+y*y>range*range||a.active&&!a.map.clear(p.x,p.y,e.x,e.y,5f,a.flags))return
            val hp=e.hp;w.combat.hitEnemy(e,20f,p.x,p.y,0f,-1,react=false);if(e.hp<hp)hookHits++
            w.fx.ring(e.x,e.y,10f,e.radius+25f,.2f,0xFF9CFFD4.toInt(),4f);return
        }
    }
    fun block(defIndex:Int,sourceX:Float=Float.NaN,sourceY:Float=Float.NaN):Boolean {
        if(shield<=0f||defIndex<0)return false
        val p=w.player;var e:Enemy?=null;var best=Float.MAX_VALUE
        for(i in 0 until w.enemyCount){val q=w.enemies[i];val x=q.x-p.x;val y=q.y-p.y;val d=x*x+y*y;if(q.alive&&q.def.index==defIndex&&d<best){e=q;best=d}}
        val q=e?:return false;val x=(if(sourceX.isFinite())sourceX else q.x)-p.x;val y=(if(sourceY.isFinite())sourceY else q.y)-p.y;val len=sqrt(x*x+y*y).coerceAtLeast(1f)
        if(style==0&&(x*sx+y*sy)/len<0.2f)return false
        val perfect=style==0&&shieldAge<0.15f;shield=0f
        if(perfect){q.stun=maxOf(q.stun,if(q.boss)0.25f else 0.7f);w.combat.hitEnemy(q,28f,p.x,p.y,200f,-1);w.events.sfx(Sfx.PERFECT)}else w.events.sfx(Sfx.SHIELD)
        w.fx.ring(p.x,p.y,20f,100f,0.25f,0xFFACE8FF.toInt(),8f);return true
    }
    /** Intercept the incoming shot, not the nearest living enemy of its type.
     * A ranged parry never remotely hurts/stuns a shooter; melee counter stays separate. */
    fun blockProjectile(sourceX:Float,sourceY:Float):Boolean {
        if(shield<=0f||!sourceX.isFinite()||!sourceY.isFinite())return false
        val p=w.player;val x=sourceX-p.x;val y=sourceY-p.y;val len=sqrt(x*x+y*y)
        if(style==0&&(len<.001f||(x*sx+y*sy)/len<.2f))return false
        val perfect=style==0&&shieldAge<.15f;shield=0f
        w.events.sfx(if(perfect)Sfx.PERFECT else Sfx.SHIELD)
        w.fx.ring(p.x,p.y,20f,100f,.25f,0xfface8ff.toInt(),8f)
        return true
    }
    fun update(dt:Float){
        if(hookT>0f){val p=w.player;val ox=p.x;val oy=p.y;hookT=(hookT-dt).coerceAtLeast(0f);val k=1f-hookT/.25f
            val ex=hx+(tx-hx)*k;val ey=hy+(ty-hy)*k;p.x=ex;p.y=ey;w.adventure.sweepPlayer(ox,oy);p.vx=0f;p.vy=0f
            if(kotlin.math.abs(p.x-ex)>1f||kotlin.math.abs(p.y-ey)>1f){hookT=0f;hookTargetUid=0}
            if(hookT<=0f)finishHook()
        }
        cooldown=(cooldown-dt).coerceAtLeast(0f);if(shield>0f){shieldAge+=dt;shield=(shield-dt).coerceAtLeast(0f)}
        if(fieldT<=0f)return;fieldT=(fieldT-dt).coerceAtLeast(0f);pulse-=dt;if(pulse>0f)return;pulse=0.5f
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-fieldX;val y=e.y-fieldY;if(!e.alive||x*x+y*y>130f*130f)continue;if(w.heroIndex==3){e.slowTimer=maxOf(e.slowTimer,0.8f);e.slowMul=0.4f}else {if(style==0&&!e.boss){e.slowTimer=maxOf(e.slowTimer,0.7f);e.slowMul=minOf(e.slowMul,0.7f)};w.combat.hitEnemy(e,8f,fieldX,fieldY,0f,-1,element=if(style==0)Element.FROST else Element.SHOCK)}}
        if(w.heroIndex==2)w.adventure.applyElement(if(style==0)Element.FROST else Element.SHOCK,fieldX,fieldY)
    }
}
