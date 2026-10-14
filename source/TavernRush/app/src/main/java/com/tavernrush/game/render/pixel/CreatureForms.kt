package com.tavernrush.game.render.pixel
import com.tavernrush.game.content.CreatureLook
/** Original geometry variants. Age changes proportions, branches add different physical structures. */
object CreatureForms {
 fun model(look:CreatureLook,age:Int,form:Int):Model {
  require(age in 0..2&&form in 0..2)
  val base=ModelLibrary.creature(look)
  if(age==2&&form==0)return base
  val head=when(age){0->1.3f;1->1.12f;else->1f}
  val limb=when(age){0->.72f;1->.9f;else->1f}
  val body=when(age){0->.88f;1->.95f;else->1f}
  val parts=base.parts.map { p->
   val isHead=p.tag==Tag.HEAD||p.tag==Tag.EAR_A||p.tag==Tag.EAR_B
   val legs=p.tag in intArrayOf(Tag.LEG_A,Tag.LEG_B,Tag.LEG_C,Tag.LEG_D)
   var sx=if(isHead)head else if(legs)limb else body
   var sy=if(isHead)head else if(legs)limb else 1f
   var sz=if(isHead)head else if(legs)limb else body
   if(form==1){if(p.tag==Tag.BODY){sx*=.95f;sy*=1.17f};if(p.tag==Tag.ARM_A||p.tag==Tag.ARM_B)sy*=1.25f}
   if(form==2){if(p.tag==Tag.TAIL||p.tag==Tag.EAR_A||p.tag==Tag.EAR_B)sz*=1.3f;if(p.tag==Tag.BODY){sx*=1.13f;sy*=.9f}}
   Part(p.shape,p.cx*body,p.cy,p.cz*(if(legs)limb else body),p.rx*sx,p.ry*sy,p.rz*sz,p.mat,p.tag,p.ax,p.ay,p.az,p.px*body,p.py,p.pz*body)
  }.toMutableList()
  val b=ModelBuilder("form-parts");val light=Mat.of(when(look){CreatureLook.STREAM->0xffa2e8f3.toInt();CreatureLook.LYNX->0xffeee4ff.toInt();CreatureLook.HORN->0xffc3b39b.toInt();CreatureLook.MOSS->0xffc8e7a1.toInt();CreatureLook.CONFLUENCE->0xffd3e8cc.toInt()})
  if(form!=0)when(look){
   CreatureLook.STREAM->if(form==1){for(i in -2..2)b.ell(-3f,i*2f,11f,2f,1.5f,3f,light,Tag.EXTRA)}else{b.ell(5f,0f,14f,1f,1.2f,4f,light,Tag.EXTRA);b.ell(-12f,0f,5f,4f,.8f,1f,light,Tag.TAIL)}
   CreatureLook.LYNX->if(form==1){b.pair(Shape.ELL,-8f,3.2f,8f,3.5f,1.3f,2.5f,light,Tag.TAIL,Tag.TAIL)}else{b.ell(7.5f,0f,15f,2.3f,.9f,3.4f,light,Tag.EXTRA);b.ell(-14f,0f,10f,3f,.9f,1.4f,light,Tag.TAIL)}
   CreatureLook.HORN->if(form==1){b.pair(Shape.BOX,0f,5.6f,9f,6f,1.6f,3f,light,Tag.BODY,Tag.BODY)}else{b.ell(15f,0f,10f,4f,1.1f,2f,light,Tag.HEAD);b.ell(17.5f,0f,11f,2.5f,.65f,.9f,light,Tag.HEAD)}
   CreatureLook.CONFLUENCE->if(form==1){b.pair(Shape.ELL,-2f,8f,10f,5f,3f,1f,light,Tag.BODY,Tag.BODY)}else{b.ell(13f,0f,12f,4f,1f,1.5f,light,Tag.HEAD)}
   CreatureLook.MOSS->if(form==1){b.pair(Shape.ELL,-4f,11f,11f,4.5f,5f,.75f,light,Tag.ARM_A,Tag.ARM_B)}else{b.pair(Shape.ELL,4f,2f,21f,.6f,.6f,4f,light,Tag.EAR_A,Tag.EAR_B);b.ell(-7f,0f,7f,3f,2f,1f,light,Tag.TAIL)}
  }
  parts.addAll(b.build().parts)
  val clips=base.clips.mapValues{(_,c)->Clip(c.name,c.frames){f,t,p,o->c.fn(f,t,p,o);if(age==0){o.dz*=.65f;o.ry*=.8f}}}
  val size=when(age){0->.78f;1->.9f;else->1f}
  return Model("${base.id}-age$age-form$form",parts.toTypedArray(),clips,base.scale*size,base.yaw,base.footDrop*size)
 }
}
