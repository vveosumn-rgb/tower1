package com.tavernrush.game.render
import android.graphics.Canvas
import android.graphics.Paint
/** Authored material pass, not a final artist acceptance. All sizes are buffer pixels. */
class ConduitArt {
 private var opacity=255
 private val p=Paint();private fun rect(c:Canvas,l:Float,t:Float,r:Float,b:Float,color:Int){p.color=color;p.alpha=opacity;c.drawRect(l,t,r,b,p)}
 fun floor(c:Canvas,l:Float,t:Float,r:Float,b:Float){
  opacity=255 // A faded foreground portal must not leak paint state into the next frame.
  rect(c,l,t,r,b,0xff263c3d.toInt());var y=t;var row=0
  while(y<b){var x=l;var col=0;while(x<r){val rr=minOf(x+24f,r);val bb=minOf(y+22f,b);val tone=if((row*7+col*3)%4==0)0xff435453.toInt() else 0xff354746.toInt();rect(c,x+1,y+1,rr-1,bb-1,tone);rect(c,x+2,y+1,rr-2,y+2,0xff5c6760.toInt());x+=24;col++};y+=22;row++}
  // Slim authored channel, separate from combat water surfaces: dry until future water puzzle.
  val mid=(l+r)*.5f;rect(c,mid-6,t+8,mid+6,b-12,0xff172d32.toInt());rect(c,mid-7,t+8,mid-6,b-12,0xff697673.toInt())
 }
 fun landmark(c:Canvas,x:Float,y:Float,node:Int,done:Boolean,fade:Boolean=false){
  opacity=if(fade)110 else 255
  p.color=0x66000000;c.drawOval(x-28,y+2,x+28,y+13,p)
  when(node){
   12->{rect(c,x-38,y-46,x-26,y+7,0xff68715f.toInt());rect(c,x+26,y-46,x+38,y+7,0xff68715f.toInt());rect(c,x-36,y-52,x+36,y-40,0xff89907a.toInt());rect(c,x-36,y-53,x+36,y-50,0xffb3b295.toInt());rect(c,x-24,y-3,x+24,y+7,0xff747c6d.toInt());rect(c,x-29,y+7,x+29,y+11,0xff525f57.toInt());p.color=0xffd8ad59.toInt();c.drawCircle(x-32,y-24,3f,p);c.drawCircle(x+32,y-24,3f,p)}
   13->{rect(c,x-14,y-15,x+14,y+4,0xff647367.toInt());rect(c,x-14,y-20,x+14,y-15,0xff9d9d7c.toInt());p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.color=if(done)0xff8ec49a.toInt() else 0xffd9b768.toInt();c.drawCircle(x,y-30,10f,p);c.drawLine(x-8,y-38,x+8,y-22,p);c.drawLine(x+8,y-38,x-8,y-22,p);p.style=Paint.Style.FILL;rect(c,x-2,y-26,x+2,y-18,0xffa7b3a4.toInt())}
   14->{rect(c,x-22,y-25,x+22,y+4,0xff563d2a.toInt());rect(c,x-22,y-31,x+22,y-23,if(done)0xff342c26.toInt() else 0xff9f764b.toInt());rect(c,x-22,y-32,x+22,y-30,0xffc29962.toInt());rect(c,x-15,y-25,x-11,y+4,0xffaeb5a0.toInt());rect(c,x+11,y-25,x+15,y+4,0xffaeb5a0.toInt());if(!done)rect(c,x-3,y-18,x+3,y-10,0xffe1c16f.toInt())}
  }
 }
}
