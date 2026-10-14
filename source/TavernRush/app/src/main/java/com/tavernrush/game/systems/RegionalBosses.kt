package com.tavernrush.game.systems
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Hazard
import com.tavernrush.game.core.Sfx
import kotlin.math.*

/** Three original encounters: corridor, safe wave opening, cover and geometry. */
class RegionalBosses(private val w:World){
 private val route=RegionNavigator();private val point=FloatArray(2)
 fun update(e:Enemy,dt:Float,speed:Float){
  val p=w.player;val dx=p.x-e.x;val dy=p.y-e.y;val distance=sqrt(dx*dx+dy*dy).coerceAtLeast(.01f);e.faceLeft=dx<0f
  when(e.state){
   BossSystem.INTRO,BossSystem.PHASE->{e.stateT-=dt;if(e.stateT<=0f){e.state=BossSystem.IDLE;e.windup=0f;e.cd=.8f}}
   BossSystem.IDLE->{
    val desired=if(e.def.id=="rootback")215f else 270f
    if(distance>desired){var tx=p.x;var ty=p.y;val a=w.adventure
     if(a.active){route.next(a.map,a.flags,e.x,e.y,tx,ty,30f,point);tx=point[0];ty=point[1]}
     val x=tx-e.x;val y=ty-e.y;val len=sqrt(x*x+y*y).coerceAtLeast(.01f);val step=minOf(speed*dt,len);e.x+=x/len*step;e.y+=y/len*step
    }
    if(e.cd<=0f)start(e)
   }
   BossSystem.TELE->{e.stateT-=dt;e.windup=1f-(e.stateT/e.dashX.coerceAtLeast(.01f)).coerceIn(0f,1f)
    if(e.stateT<=0f){e.state=BossSystem.ACT;e.stateT=.3f;e.windup=1f;w.events.sfx(Sfx.ROAR)}
   }
   BossSystem.ACT->{e.stateT-=dt;if(e.stateT<=0f){e.state=BossSystem.RECOVER;e.stateT=if(e.def.id=="prism_crab")1.15f else .9f;e.windup=0f}}
   else->{e.stateT-=dt;if(e.stateT<=0f){e.state=BossSystem.IDLE;e.cd=if(e.stage==2).85f else 1.3f;e.windup=0f}}
  }
 }
 private fun line(e:Enemy,x:Float,y:Float,angle:Float,half:Float,delay:Float,length:Float,damage:Float){
  val a=w.adventure;val len=if(a.active)a.map.rayLength(x,y,cos(angle),sin(angle),length,half,a.flags) else length
  if(len<35f)return
  w.spawnHazard(Hazard.LINE,x,y,half,delay,damage,e.def.color,life=.18f,ang=angle,len=len,owner=e.uid,ownerDef=e.def.index)
 }
 private fun circle(e:Enemy,x:Float,y:Float,r:Float,delay:Float,damage:Float){
  w.spawnHazard(Hazard.CIRCLE,if(w.adventure.active)x.coerceIn(-610f,610f)else x,if(w.adventure.active)y.coerceIn(-910f,610f)else y,r,delay,damage,e.def.color,owner=e.uid,ownerDef=e.def.index)
 }
 private fun start(e:Enemy){
  val p=w.player;val angle=atan2(p.y-e.y,p.x-e.x);val nx=cos(angle);val ny=sin(angle);val k=if(e.def.damage>0f)e.damage/e.def.damage else 1f
  val seq=when(e.def.id){"storm_heron"->if(e.stage==0)STORM0 else STORM1;"rootback"->if(e.stage==0)ROOT0 else ROOT1;else->if(e.stage==0)PRISM0 else PRISM1}
  e.act=seq[e.attack%seq.size];e.attack++;e.state=BossSystem.TELE;e.windup=0f;e.tx=p.x;e.ty=p.y
  var duration=1.1f
  when(e.act){
   FORK->{ // Two parallel lanes, an intentional central passage.
    for(sign in intArrayOf(-1,1))line(e,e.x-ny*90f*sign,e.y+nx*90f*sign,angle,25f,1.1f,520f,18f*k)
   }
   STORM_MARK->{
    val n=if(e.stage==2)3 else 2
    for(i in 0 until n){val off=(i-(n-1)*.5f)*140f;circle(e,p.x-ny*off,p.y+nx*off,58f,1.0f+i*.3f,19f*k)};duration=1f+(n-1)*.3f
   }
   CROSS->{line(e,e.x,e.y,angle,27f,1.15f,540f,20f*k);line(e,e.x,e.y,angle+PI.toFloat()/2,27f,1.55f,400f,20f*k);duration=1.55f}
   ROOT_RING->{
    val h=w.spawnHazard(Hazard.RING,e.x,e.y,18f,1.05f,0f,e.def.color,life=2.5f,owner=e.uid,ownerDef=e.def.index,r1=if(e.stage==2)560f else 470f,damage=18f*k)
    h?.let{it.r0=45f;it.gapAngle=angle;it.gapHalf=.62f};duration=1.05f
   }
   SEEDS->{
    val n=if(e.stage==2)5 else 3
    for(i in 0 until n){val ang=angle+i*6.2831855f/n;circle(e,p.x+cos(ang)*155f,p.y+sin(ang)*155f,65f,1.2f,18f*k)};duration=1.2f
   }
   VINE->{for(i in -1..1)circle(e,p.x+nx*i*140f,p.y+ny*i*140f,52f,1.1f+(i+1)*.22f,18f*k);duration=1.54f}
   LANCE->{line(e,e.x,e.y,angle,30f,1.15f,600f,22f*k);duration=1.15f}
   TRIAD->{for(i in 0..2){val ang=angle+i*2.0943952f;val x=p.x+cos(ang)*175f;val y=p.y+sin(ang)*175f;circle(e,x,y,54f,1.2f,16f*k)};duration=1.2f}
   FAN->{for(i in -1..1)line(e,e.x,e.y,angle+i*.42f,20f,1.0f+(i+1)*.25f,560f,18f*k);duration=1.5f;if(e.stage==2){line(e,e.x-ny*120f,e.y+nx*120f,angle,20f,1.9f,560f,18f*k);duration=1.9f}}
  }
  e.dashX=duration;e.stateT=duration+.02f;w.events.sfx(Sfx.TELEGRAPH)
 }
 companion object {
  fun hint(e:Enemy):String {
   if(e.def.id!="storm_heron"&&e.def.id!="rootback"&&e.def.id!="prism_crab")return ""
   if(e.state==BossSystem.RECOVER)return "Окно для ответного удара"
   if(e.state!=BossSystem.TELE)return ""
   return when(e.act){FORK->"Между полосами есть проход";STORM_MARK->"Метки закреплены — смените место";CROSS->"Уйдите за границу полосы";ROOT_RING->"Ищите разрыв между зелёными границами";SEEDS,TRIAD->"Ищите место между кругами";VINE->"Корни идут по отмеченной линии";LANCE,FAN->"Луч закреплён; камень прикрывает";else->""}
  }
  const val FORK=20;const val STORM_MARK=21;const val CROSS=22;const val ROOT_RING=23;const val SEEDS=24;const val VINE=25;const val LANCE=26;const val TRIAD=27;const val FAN=28
  private val STORM0=intArrayOf(FORK,STORM_MARK);private val STORM1=intArrayOf(CROSS,STORM_MARK,FORK)
  private val ROOT0=intArrayOf(ROOT_RING,SEEDS);private val ROOT1=intArrayOf(VINE,ROOT_RING,SEEDS)
  private val PRISM0=intArrayOf(LANCE,TRIAD);private val PRISM1=intArrayOf(FAN,LANCE,TRIAD)
 }
}
