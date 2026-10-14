package com.tavernrush.game.ui.screens
import android.graphics.Canvas
import android.graphics.RectF
import com.tavernrush.game.ui.*
import com.tavernrush.game.render.Palette
/** Full simulation pause. Only visible landmarks, no teleport or hidden loot disclosure. */
class RouteMapScreen(app:GameApp):Screen(app){
 val back=Button("К паузе",Button.GOLD);val track=Button("Отметить",Button.PRIMARY);val clear=Button("Снять метку",Button.DARK)
 val chart=RectF();var selected=-1;private set
 private var scale=1f;private var ox=0f;private var oy=0f;private var down=-1;private var pointer=-1
 fun px(x:Float)=ox+x*scale
 fun py(y:Float)=oy+y*scale
 private fun shown(i:Int)=world.adventure.visibleNode(i)&&i !in 7..9
 override fun layout(){val u=ui.u;val l=ui.cx-ui.contentW/2;val r=ui.cx+ui.contentW/2
  chart.set(l,ui.top+190*u,r,ui.bottom-490*u);scale=minOf(chart.width()/1420f,chart.height()/1720f);ox=ui.cx;oy=chart.centerY()+150f*scale
  track.set(l,ui.bottom-350*u,ui.cx-12*u,ui.bottom-200*u);clear.set(ui.cx+12*u,ui.bottom-350*u,r,ui.bottom-200*u);back.set(l,ui.bottom-180*u,r,ui.bottom-30*u)
  listOf(track,clear,back).forEach{it.textSize=40*u}
 }
 override fun onEnter(){selected=world.adventure.objective;pointer=-1;down=-1;layout();refresh()}
 private fun refresh(){val a=world.adventure;track.enabled=selected>=0&&shown(selected)&&!a.nodeDone(selected)&&!a.intentionDone;clear.enabled=a.pinned>=0}
 private fun hit(x:Float,y:Float):Int{val a=world.adventure;var best=-1;var d=(72*ui.u)*(72*ui.u);for(i in a.nodesX.indices){if(!shown(i))continue;val dx=x-px(a.nodesX[i]);val dy=y-py(a.nodesY[i]);val q=dx*dx+dy*dy;if(q<d){d=q;best=i}};return best}
 override fun onDown(id:Int,x:Float,y:Float){if(pressButtons(id,x,y,back,track,clear))return;if(pointer<0){pointer=id;down=hit(x,y)}}
 override fun onUp(id:Int,x:Float,y:Float){when(releaseButton(id,x,y)){back->game.closeRouteMap();track->{world.adventure.pin(selected);refresh()};clear->{world.adventure.clearPin();refresh()};else->{if(pointer==id&&down>=0&&down==hit(x,y)){selected=down;refresh()}}};if(pointer==id){pointer=-1;down=-1}}
 override fun onCancel(){resetPress();pointer=-1;down=-1}
 override fun draw(c:Canvas){val u=ui.u;val a=world.adventure;c.drawColor(0xff14292b.toInt());d.txt.setup(60*u,Palette.GOLD_LIGHT);d.txt.fit(a.names[a.region],ui.contentW,60*u);d.txt.draw(c,a.names[a.region],ui.cx,ui.top+100*u)
  d.txt.setup(32*u,Palette.TEXT);d.txt.draw(c,"Полная пауза · выберите ориентир",ui.cx,ui.top+155*u)
  d.rrect(c,chart.left,chart.top,chart.right,chart.bottom,22*u,0xff213f3a.toInt())
  val save=c.save();c.clipRect(chart)
  for(i in a.map.walls.indices){if(!a.map.enabled(i,a.flags))continue;val b=a.map.walls[i];d.fp.color=if(i==0)0xffc99d58.toInt() else 0xff758179.toInt();c.drawRect(px(b.x-b.hx),py(b.y-b.hy),px(b.x+b.hx),py(b.y+b.hy),d.fp)}
  for(i in a.nodesX.indices){if(!shown(i))continue;val x=px(a.nodesX[i]);val y=py(a.nodesY[i]);val color=if(a.nodeDone(i))0xff76b987.toInt() else a.colors[a.region];d.fp.color=color;c.drawCircle(x,y,17*u,d.fp)
   if(i==selected||i==a.pinned){d.sp.color=Palette.GOLD_LIGHT;d.sp.strokeWidth=4*u;c.drawCircle(x,y,27*u,d.sp)}
   d.txt.setup(28*u,0xff102526.toInt(),outlineW=0f);d.txt.draw(c,(i+1).toString(),x,y+10*u,false)
  }
  val p=world.player;d.fp.color=0xffffffff.toInt();c.drawCircle(px(p.x),py(p.y),13*u,d.fp);d.sp.color=0xff152b31.toInt();d.sp.strokeWidth=4*u;c.drawCircle(px(p.x),py(p.y),13*u,d.sp)
  c.restoreToCount(save)
  d.txt.setup(34*u,Palette.TEXT);val label=if(selected in a.nodesX.indices&&shown(selected))"${selected+1}. ${a.labels[selected]}" else "Выберите известный ориентир";d.txt.fit(label,ui.contentW,40*u);d.txt.draw(c,label,ui.cx,ui.bottom-425*u)
  d.txt.setup(29*u,Palette.TEXT_DIM);d.txt.draw(c,"Белый — вы · зелёный — выполнено",ui.cx,ui.bottom-375*u)
  listOf(track,clear,back).forEach{d.button(c,it,app.time)}
 }
}
