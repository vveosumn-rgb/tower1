package com.tavernrush.game.render.pixel.models
import com.tavernrush.game.render.pixel.*
import kotlin.math.*

/** Original anatomy, not a recoloured rat/ogre/ghost. */
object RegionBossModels {
 fun stormHeron():Model {
  val shell=Mat.of(0xff666fa5.toInt(),shine=.5f);val feather=Mat.of(0xffa4ace0.toInt());val leg=Mat.of(0xffbd9760.toInt());val glow=Mat.glow(0xffffe0a0.toInt())
  val b=ModelBuilder("storm_heron");b.scale=2.5f;b.footDrop=32f
  b.pair(Shape.CYL,0f,4f,7f,1.1f,1.1f,7f,leg,Tag.LEG_A,Tag.LEG_B,pz=14f)
  b.pair(Shape.ELL,2f,4f,1f,4f,1.6f,1.1f,leg,Tag.LEG_A,Tag.LEG_B,pz=14f)
  b.ell(-1f,0f,17f,7f,5.2f,6f,shell,Tag.BODY)
  b.cyl(4f,0f,24f,1.9f,2f,7f,feather,Tag.HEAD,ay=-.25f,pz=18f)
  b.ell(6f,0f,31f,4f,3.1f,3.4f,feather,Tag.HEAD)
  for(i in 0..2)b.box(10f+i*1.8f,0f,30f,2.4f-i*.6f,1.5f-i*.4f,.8f,leg,Tag.HEAD)
  b.pair(Shape.ELL,8f,2.3f,32f,.7f,.6f,.8f,glow,Tag.HEAD,Tag.HEAD)
  for(side in listOf(-1f,1f))for(i in 0..3){val tag=if(side>0)Tag.ARM_A else Tag.ARM_B
   b.ell(-3f-i*1.3f,side*(5.5f+i*1.9f),18f-i*.7f,4.1f,2f,.9f,if(i%2==0)shell else feather,tag,px=-1f,py=side*4,pz=18f,az=side*.18f)
  }
  for(i in -1..1)b.cyl(4f, i*1.5f,36f+if(i==0)1.8f else 0f,.5f,.5f,2.5f,glow,Tag.HEAD)
  b.ell(-8f,0f,18f,5f,2.2f,1.4f,feather,Tag.TAIL,ay=-.3f)
  b.clip(Anims.walk(swing=.5f,arms=.1f,bob=.7f))
  b.clip(Clip("attack",6){f,_,p,o->val lift=floatArrayOf(0f,2f,5f,1f,-1f,0f)[f];when(p.tag){Tag.ARM_A,Tag.ARM_B->{o.dz=lift;o.rz=(if(p.tag==Tag.ARM_A)1f else -1f)*lift*.12f};Tag.HEAD->{o.ry=-lift*.04f};Tag.BODY->o.dz=lift*.2f}})
  return b.build()
 }
 fun rootback():Model {
  val bark=Mat.of(0xff6d6941.toInt(),pattern=Pat.NOISE,patScale=1.5f);val moss=Mat.of(0xff809954.toInt());val petal=Mat.of(0xffcbda8a.toInt());val eye=Mat.glow(0xffffca6c.toInt())
  val b=ModelBuilder("rootback");b.scale=2.6f;b.footDrop=14f
  b.ell(0f,0f,9f,10.4f,9f,8f,bark,Tag.BODY)
  b.ell(-1f,0f,14f,9f,8f,5.5f,moss,Tag.BODY)
  for(i in 0..3){val x=if(i<2)6f else -6f;val y=if(i%2==0)7f else -7f;val tag=intArrayOf(Tag.LEG_A,Tag.LEG_B,Tag.LEG_C,Tag.LEG_D)[i]
   b.ell(x,y,4f,4f,2.4f,3.4f,bark,tag,pz=7f);b.ell(x+2f,y*1.45f,1.3f,4f,1.8f,1.3f,bark,tag,pz=7f)
  }
  b.ell(9f,0f,7f,4f,5f,4f,bark,Tag.HEAD)
  b.pair(Shape.ELL,11.5f,3f,9f,.7f,.7f,1f,eye,Tag.HEAD,Tag.HEAD)
  b.cyl(-1f,0f,20f,2.2f,2.2f,4f,bark,Tag.EXTRA)
  for(i in 0..6){val a=i*6.2831855f/7;b.ell(-1f+cos(a)*5f,sin(a)*5f,23f,4.4f,2.3f,1f,petal,Tag.EXTRA,az=a,px=-1f,py=0f,pz=20f)}
  b.ell(-1f,0f,25f,2.4f,2.4f,2f,eye,Tag.EXTRA)
  for(i in 0..3)b.ell(-10f-i*2.3f,sin(i*1.1f)*2f,4f-i*.5f,2.8f,.8f,.8f,bark,Tag.TAIL,px=-8f,py=0f,pz=5f)
  b.clip(Anims.quad(swing=.35f,bob=.5f))
  b.clip(Clip("attack",6){f,_,p,o->val bloom=floatArrayOf(0f,.1f,.23f,.08f,-.04f,0f)[f];when(p.tag){Tag.EXTRA->{o.sx=1f+bloom;o.sy=1f+bloom;o.dz=bloom*8f};Tag.BODY,Tag.HEAD->o.dz=-bloom*5f;Tag.TAIL->o.rz=bloom}})
  return b.build()
 }
 fun prismCrab():Model {
  val stone=Mat.of(0xff776b75.toInt(),shine=.6f);val crystal=Mat.of(0xffd4c392.toInt(),shine=1f,rim=1.4f);val glass=Mat.of(0xffa3d6db.toInt(),shine=1f);val eye=Mat.glow(0xfffff2bf.toInt())
  val b=ModelBuilder("prism_crab");b.scale=2.55f;b.footDrop=15f
  b.ell(0f,0f,9f,8.5f,8f,4.8f,stone,Tag.BODY)
  b.box(-1f,0f,14f,6.5f,6.5f,3f,crystal,Tag.BODY,az=.4f)
  for(side in listOf(-1f,1f))for(i in 0..2){val tag=if(side>0)Tag.LEG_A else Tag.LEG_B;val x=-6f+i*5f
   b.box(x,side*8f,5f,1.4f,4f,1.4f,stone,tag,ax=side*.4f,pz=8f);b.ell(x+2f,side*12f,2f,2.3f,2f,1.3f,crystal,tag,pz=8f)
  }
  for(side in listOf(-1f,1f)){val tag=if(side>0)Tag.ARM_A else Tag.ARM_B
   b.ell(7f,side*7f,11f,4f,2f,2f,stone,tag,px=4f,py=side*5f,pz=10f)
   b.box(11f,side*9f,14f,3f,1f,6f,glass,tag,ay=.35f,az=side*.5f,px=4f,py=side*5f,pz=10f)
  }
  b.cyl(1f,0f,21f,2.8f,2.8f,7f,glass,Tag.EXTRA,ay=.35f)
  b.box(3f,0f,29f,1.1f,1.1f,3f,eye,Tag.EXTRA,ay=.35f)
  b.ell(7f,0f,9f,3f,4f,2.5f,stone,Tag.HEAD)
  b.pair(Shape.ELL,9f,2.2f,11f,.7f,.7f,1f,eye,Tag.HEAD,Tag.HEAD)
  b.clip(Anims.quad(swing=.45f,bob=.6f))
  b.clip(Clip("attack",6){f,_,p,o->val turn=floatArrayOf(0f,.25f,.6f,-.2f,-.1f,0f)[f];when(p.tag){Tag.ARM_A,Tag.ARM_B->{o.ry=turn;o.rz=turn*(if(p.tag==Tag.ARM_A)1f else -1f)};Tag.EXTRA->{o.ry=turn*.3f;o.dz=turn*2f};Tag.BODY->o.dz=turn*.5f}})
  return b.build()
 }
}
