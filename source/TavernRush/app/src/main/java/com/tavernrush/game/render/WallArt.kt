package com.tavernrush.game.render

import android.graphics.Canvas
import android.graphics.Paint
import com.tavernrush.game.systems.RegionMap
import kotlin.math.ceil
import kotlin.math.max

/** Authored collision footprint, projected height only. Reused paint/bounds; no texture RNG stream. */
class WallArt {
 private val p=Paint().apply{isAntiAlias=false}
 private fun ink(color:Int,alpha:Int){p.color=(alpha shl 24)or(color and 0xffffff)}
 fun shadow(c:Canvas,l:Float,t:Float,r:Float,b:Float){ink(0x18141e,52);c.drawRect(l+3f,t+4f,r+6f,b+7f,p);ink(0x18141e,40);c.drawRect(l,t,r+2f,b+3f,p)}
 fun tile(c:Canvas,l:Float,t:Float,r:Float,b:Float,region:Int,seed:Int,alpha:Int=255){
  val palette=region.coerceIn(0,5);val top=t-HEIGHT;val front=b-HEIGHT
  ink(FACE[palette],alpha);c.drawRect(l,front,r,b,p)
  // Staggered masonry on the upright face: the pattern follows the tile, not camera/time.
  var y=front+1f;var row=0
  while(y<b-1f){var x=l-if(row and 1==0)0f else 5f;var col=0
   while(x<r){val left=max(l+1f,x+1f);val right=minOf(r-1f,x+10f);val bottom=minOf(b-1f,y+6f)
    if(right>left&&bottom>y){ink(if((seed+row*3+col*7)and 3==0)STONE[palette] else FACE[palette],alpha);c.drawRect(left,y,right,bottom,p);ink(DARK[palette],alpha);c.drawRect(left,bottom,right,minOf(b,bottom+1f),p)};x+=11f;col++
   };y+=7f;row++
  }
  ink(TOP[palette],alpha);c.drawRect(l,top,r,front,p)
  ink(STONE[palette],alpha);c.drawRect(l+1f,top+2f,r-2f,front-1f,p)
  ink(LIGHT[palette],alpha);c.drawRect(l,top,r-1f,top+1f,p);c.drawRect(l,top,l+1f,front,p)
  ink(DARK[palette],alpha);c.drawRect(r-2f,top+2f,r,b,p);c.drawRect(l,b-1f,r,b,p)
  // Cap seams, chipped corners and subdued deposits differ by material.
  val mid=(l+r)*.5f;ink(DARK[palette],alpha);c.drawRect(mid,top+3f,mid+1f,front-1f,p)
  ink(LIGHT[palette],alpha);c.drawRect(l+3f,front-2f,minOf(l+8f,r-2f),front-1f,p)
  if(r-l>8f&&front-top>4f){ink(ACCENT[palette],alpha);val x=l+3f+(seed and 3);c.drawRect(x,top+3f,minOf(x+4f,r-2f),minOf(top+5f,front-1f),p)}
 }
 companion object {
  const val HEIGHT=22f // pixels in the world buffer, not a change to collision height
  const val TILE=56f
  const val STRIDE=256
  private val TOP=intArrayOf(0xffa7997a.toInt(),0xffb77d60.toInt(),0xffb5d6db.toInt(),0xff9892af.toInt(),0xff8b9e8c.toInt(),0xffb5a7c5.toInt())
  private val STONE=intArrayOf(0xff948972.toInt(),0xff97664f.toInt(),0xff8bb8c5.toInt(),0xff78798f.toInt(),0xff6e867c.toInt(),0xff968ba9.toInt())
  private val FACE=intArrayOf(0xff6d6a5d.toInt(),0xff6e4c42.toInt(),0xff5f8eab.toInt(),0xff53566e.toInt(),0xff4e6a61.toInt(),0xff655e80.toInt())
  private val DARK=intArrayOf(0xff393d39.toInt(),0xff3b3031.toInt(),0xff3d607c.toInt(),0xff33394c.toInt(),0xff2e4643.toInt(),0xff3d3957.toInt())
  private val LIGHT=intArrayOf(0xffd6c9a4.toInt(),0xffdcaa80.toInt(),0xffdfefea.toInt(),0xffc1bfd1.toInt(),0xffb6c7ab.toInt(),0xffe4d5ea.toInt())
  private val ACCENT=intArrayOf(0xff657e58.toInt(),0xffb65e3c.toInt(),0xffb9eced.toInt(),0xffa39dbf.toInt(),0xff92aa62.toInt(),0xffc8a8db.toInt())
  fun cols(b:RegionMap.Wall)=ceil(b.hx*2f/TILE).toInt().coerceAtLeast(1)
  fun rows(b:RegionMap.Wall)=ceil(b.hy*2f/TILE).toInt().coerceAtLeast(1)
  fun count(b:RegionMap.Wall)=cols(b)*rows(b)
  fun bounds(b:RegionMap.Wall,index:Int,out:FloatArray){val nx=cols(b);val ny=rows(b);val x=index%nx;val y=index/nx;val dx=b.hx*2f/nx;val dy=b.hy*2f/ny
   out[0]=b.x-b.hx+x*dx;out[1]=b.y-b.hy+y*dy;out[2]=out[0]+dx;out[3]=out[1]+dy
  }
  /** Fade only the segment whose projected upper silhouette covers the hero's feet/body. */
  fun hidesHero(l:Float,t:Float,r:Float,b:Float,hx:Float,hy:Float)=hx>=l-9f&&hx<=r+9f&&hy>=t-HEIGHT&&hy<b
 }
}
