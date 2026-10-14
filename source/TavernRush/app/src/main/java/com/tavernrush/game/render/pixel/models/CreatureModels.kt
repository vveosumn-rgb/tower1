package com.tavernrush.game.render.pixel.models

import com.tavernrush.game.render.pixel.Anims
import com.tavernrush.game.render.pixel.Mat
import com.tavernrush.game.render.pixel.Model
import com.tavernrush.game.render.pixel.ModelBuilder
import com.tavernrush.game.render.pixel.Pat
import com.tavernrush.game.render.pixel.Shape
import com.tavernrush.game.render.pixel.Tag

/**
 * Существа-друзья. Силуэт читается за 0,3 с: Ручьеглот — круглая широкоротая «капля» с плавником,
 * Грозорыс — длинный лёгкий зверь с кисточками на ушах и светящимся хвостом, Камнерог — приземистая глыба с рогом.
 * Клипы: walk (ход), idle (покой), act (энергичный ход — приём или прыжок).
 */
object CreatureModels {
    private val pupil = Mat.of(0xFF1C1424.toInt(), shine = 0.4f)
    private val white = Mat.of(0xFFFFFFFF.toInt(), shine = 0.6f)

    /** Собственный крылатый силуэт: листья-крылья, усики и маленькое округлое тело. */
    fun moss():Model {
        val leaf=Mat.of(0xFF70AE58.toInt(),rim=1.2f,dither=0.2f)
        val soft=Mat.of(0xFFCAE4A0.toInt());val vein=Mat.of(0xFF47744A.toInt());val glow=Mat.glow(0xFFE2F6BA.toInt())
        val b=ModelBuilder("moss");b.scale=1.5f;b.footDrop=6f
        b.ell(0f,0f,8f,4.2f,3.7f,5.0f,leaf,Tag.BODY)
        b.ell(2.3f,0f,8f,3.2f,3.0f,3.3f,soft,Tag.BODY)
        b.ell(4.5f,0f,12f,3.2f,3.0f,3.0f,leaf,Tag.HEAD)
        b.pair(Shape.ELL,6.6f,1.8f,12.5f,0.9f,0.7f,1.1f,white,Tag.HEAD,Tag.HEAD)
        b.pair(Shape.ELL,7.2f,1.8f,12.5f,0.4f,0.4f,0.6f,pupil,Tag.HEAD,Tag.HEAD)
        b.pair(Shape.ELL,3.9f,2.0f,16f,0.5f,0.5f,2f,vein,Tag.EAR_A,Tag.EAR_B)
        b.pair(Shape.ELL,3.9f,2.0f,18f,0.8f,0.8f,0.8f,glow,Tag.EAR_A,Tag.EAR_B)
        for(k in 0..2){b.pair(Shape.ELL,-1.0f-k*2f,5.0f+k*1.2f,10.5f-k*.7f,3.6f-k*.5f,4.5f-k*.6f,.75f,if(k==1)soft else leaf,Tag.ARM_A,Tag.ARM_B)}
        b.pair(Shape.ELL,1.5f,2f,3.8f,.65f,.6f,1.9f,vein,Tag.LEG_A,Tag.LEG_B)
        for(name in arrayOf("walk","idle","act"))b.clip(com.tavernrush.game.render.pixel.Clip(name,6){_,t,part,pose->
            val k=kotlin.math.sin(t*6.2831853f);pose.dz=1.0f+k*.8f
            if(part.tag==Tag.ARM_A||part.tag==Tag.ARM_B){pose.rx=k*(if(name=="act")1.0f else .45f)*(if(part.tag==Tag.ARM_A)1 else -1)}
            if(part.tag==Tag.EAR_A||part.tag==Tag.EAR_B)pose.ry=k*.16f
        })
        return b.build()
    }

    /** Poro-gate creature: broad segmented shell, water fins and a low scoop-like muzzle. */
    fun confluence():Model {
        val shell=Mat.of(0xff7fa093.toInt(),pattern=Pat.NOISE);val belly=Mat.of(0xffc5ddc9.toInt());val fin=Mat.of(0xff63c7d3.toInt(),shine=.7f);val bone=Mat.of(0xffded6af.toInt())
        val b=ModelBuilder("confluence");b.scale=1.45f;b.footDrop=7f
        b.ell(0f,0f,5f,7f,5f,4f,shell,Tag.BODY);b.ell(3f,0f,3f,5f,3.8f,2f,belly,Tag.BODY)
        for(k in 0..2)b.box(-4f+k*3f,0f,9f,1.6f,4.2f,1.1f,shell,Tag.BODY)
        b.ell(8f,0f,6f,3.8f,3.4f,3f,shell,Tag.HEAD);b.box(11f,0f,4.2f,2.7f,3.4f,1.1f,bone,Tag.HEAD)
        b.pair(Shape.ELL,9f,2.5f,8f,1f,.8f,1.2f,white,Tag.HEAD,Tag.HEAD);b.pair(Shape.ELL,9.7f,2.6f,8.1f,.5f,.5f,.65f,pupil,Tag.HEAD,Tag.HEAD)
        b.ell(5f,0f,10.5f,1f,1f,2.3f,bone,Tag.HEAD)
        b.pair(Shape.ELL,2f,5.4f,5f,3f,1.5f,.7f,fin,Tag.ARM_A,Tag.ARM_B)
        b.pair(Shape.ELL,4f,3.8f,2f,1.7f,1.5f,2f,shell,Tag.LEG_A,Tag.LEG_B)
        b.pair(Shape.ELL,-4f,3.8f,2f,1.7f,1.5f,2f,shell,Tag.LEG_C,Tag.LEG_D)
        b.ell(-8f,0f,4.3f,3f,2f,1.3f,fin,Tag.TAIL)
        b.clip(Anims.quad(swing=.6f,bob=.65f));b.clip(Anims.idle(breathe=.55f));b.clip(Anims.quad(frames=4,swing=.85f,bob=1.6f,name="act"));return b.build()
    }
    fun stream(): Model {
        val skin = Mat.of(0xFF3FB8D8.toInt(), rim = 1.2f, dither = 0.2f)
        val belly = Mat.of(0xFFBDEFF7.toInt(), dither = 0.15f)
        val fin = Mat.of(0xFF86E3F2.toInt(), shine = 0.6f)
        val water = Mat.of(0xFF9FEAFF.toInt(), shine = 1.0f)
        val cheek = Mat.of(0xFFFF9EB5.toInt())
        val b = ModelBuilder("stream")
        b.scale = 1.5f; b.footDrop = 6f
        b.ell(0f, 0f, 4.4f, 6.2f, 4.6f, 4.0f, skin, Tag.BODY)
        b.ell(1.4f, 0f, 3.1f, 4.8f, 3.6f, 2.6f, belly, Tag.BODY)
        b.ell(6.6f, 0f, 5.6f, 4.0f, 3.9f, 3.4f, skin, Tag.HEAD)
        b.ell(9.4f, 0f, 4.3f, 2.0f, 2.8f, 1.3f, belly, Tag.HEAD)
        b.pair(Shape.ELL, 7.4f, 2.6f, 8.3f, 1.5f, 1.3f, 1.6f, white, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 8.7f, 2.7f, 8.4f, 0.7f, 0.6f, 0.9f, pupil, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 7.4f, 3.5f, 5.2f, 0.9f, 0.5f, 0.8f, cheek, Tag.HEAD, Tag.HEAD)
        b.ell(5.6f, 0f, 9.5f, 1.1f, 1.1f, 1.9f, water, Tag.EXTRA)
        b.ell(5.6f, 0f, 11.3f, 0.5f, 0.5f, 0.8f, water, Tag.EXTRA)
        for (k in 0 until 4) b.ell(1.8f - k * 2.4f, 0f, 8.8f - k * 0.25f, 1.1f, 0.5f, 1.7f - k * 0.1f, fin, Tag.EXTRA)
        b.ell(3.6f, 3.3f, 1.7f, 1.4f, 1.2f, 1.9f, skin, Tag.LEG_A, pz = 3.6f)
        b.ell(3.6f, -3.3f, 1.7f, 1.4f, 1.2f, 1.9f, skin, Tag.LEG_B, pz = 3.6f)
        b.ell(-3.4f, 3.5f, 1.7f, 1.5f, 1.3f, 1.9f, skin, Tag.LEG_C, pz = 3.6f)
        b.ell(-3.4f, -3.5f, 1.7f, 1.5f, 1.3f, 1.9f, skin, Tag.LEG_D, pz = 3.6f)
        for (k in 0 until 3) {
            b.ell(-6.8f - k * 2.6f, 0f, 3.8f + k * 0.3f, 2.6f - k * 0.4f, 1.2f - k * 0.15f, 2.0f - k * 0.2f, if (k == 2) fin else skin, Tag.TAIL, px = -5.4f, py = 0f, pz = 3.8f)
        }
        b.clip(Anims.quad(swing = 0.9f, bob = 0.8f))
        b.clip(Anims.idle(breathe = 0.7f))
        b.clip(Anims.quad(frames = 4, swing = 1.3f, bob = 1.8f, name = "act"))
        return b.build()
    }

    fun lynx(): Model {
        val fur = Mat.of(0xFFA08CE6.toInt(), pattern = Pat.STRIPES, patScale = 1.8f, rim = 1.1f, dither = 0.25f)
        val light = Mat.of(0xFFE6DCFF.toInt(), dither = 0.2f)
        val dark = Mat.of(0xFF2E2552.toInt())
        val spark = Mat.glow(0xFFFFE87A.toInt())
        val eye = Mat.glow(0xFF7FF5FF.toInt())
        val b = ModelBuilder("lynx")
        b.scale = 1.4f; b.footDrop = 8f
        b.ell(0f, 0f, 6.4f, 7.2f, 3.4f, 3.6f, fur, Tag.BODY)
        b.ell(4.6f, 0f, 6.8f, 3.6f, 3.3f, 3.7f, fur, Tag.BODY)
        b.ell(5.9f, 0f, 5.1f, 1.9f, 2.3f, 2.2f, light, Tag.BODY)
        b.ell(8.6f, 0f, 9.0f, 3.7f, 3.3f, 3.3f, fur, Tag.HEAD)
        b.pair(Shape.ELL, 7.9f, 3.0f, 8.0f, 1.8f, 1.5f, 1.7f, light, Tag.HEAD, Tag.HEAD)
        b.ell(11.6f, 0f, 8.2f, 1.6f, 1.6f, 1.3f, light, Tag.HEAD)
        b.ell(12.9f, 0f, 8.6f, 0.6f, 0.5f, 0.5f, dark, Tag.HEAD)
        b.pair(Shape.ELL, 7.4f, 2.1f, 12.0f, 1.0f, 0.9f, 2.3f, fur, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 7.4f, 2.1f, 14.3f, 0.45f, 0.45f, 1.1f, spark, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 11.0f, 1.9f, 9.7f, 0.8f, 0.6f, 0.9f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(5.0f, 2.4f, 2.8f, 1.3f, 1.2f, 3.0f, fur, Tag.LEG_A, pz = 6.0f)
        b.ell(5.0f, -2.4f, 2.8f, 1.3f, 1.2f, 3.0f, fur, Tag.LEG_B, pz = 6.0f)
        b.ell(-4.8f, 2.5f, 2.8f, 1.4f, 1.3f, 3.0f, fur, Tag.LEG_C, pz = 6.0f)
        b.ell(-4.8f, -2.5f, 2.8f, 1.4f, 1.3f, 3.0f, fur, Tag.LEG_D, pz = 6.0f)
        for (k in 0 until 4) {
            b.ell(-8.4f - k * 2.0f, 0f, 7.0f + k * 1.1f, 2.4f - k * 0.25f, 1.5f - k * 0.2f, 1.5f - k * 0.2f, if (k == 3) spark else fur, Tag.TAIL, px = -7f, py = 0f, pz = 6.6f)
        }
        b.clip(Anims.quad(swing = 1.1f, bob = 0.7f))
        b.clip(Anims.idle(breathe = 0.6f))
        b.clip(Anims.quad(frames = 4, swing = 1.5f, bob = 2.2f, name = "act"))
        return b.build()
    }

    fun horn(): Model {
        val stone = Mat.of(0xFF9C8470.toInt(), pattern = Pat.NOISE, patScale = 1.5f, rim = 0.9f, dither = 0.3f)
        val slate = Mat.of(0xFF6E6178.toInt(), pattern = Pat.NOISE, patScale = 1.2f)
        val moss = Mat.of(0xFF6FAE4E.toInt(), pattern = Pat.NOISE, patScale = 1.4f)
        val bone = Mat.of(0xFFF1E6C8.toInt(), shine = 0.5f)
        val eye = Mat.glow(0xFFFFB347.toInt())
        val b = ModelBuilder("horn")
        b.scale = 1.45f; b.footDrop = 9f
        b.ell(0f, 0f, 6.4f, 7.6f, 5.0f, 4.8f, stone, Tag.BODY)
        b.ell(3.6f, 0f, 8.4f, 3.8f, 4.2f, 3.4f, stone, Tag.BODY)
        for (k in 0 until 3) b.ell(-1.0f - k * 2.8f, 0f, 11.2f - k * 0.3f, 1.9f, 2.8f, 1.0f, slate, Tag.EXTRA)
        b.ell(-3.0f, 1.6f, 10.8f, 2.0f, 1.4f, 0.7f, moss, Tag.EXTRA)
        b.ell(9.2f, 0f, 6.6f, 4.2f, 3.8f, 3.6f, stone, Tag.HEAD)
        b.ell(12.6f, 0f, 5.4f, 2.6f, 2.8f, 2.2f, stone, Tag.HEAD)
        b.ell(12.6f, 0f, 9.8f, 1.6f, 1.3f, 2.7f, bone, Tag.HEAD)
        b.ell(13.8f, 0f, 12.4f, 1.0f, 0.9f, 1.9f, bone, Tag.HEAD)
        b.ell(10.2f, 0f, 10.4f, 1.0f, 0.9f, 1.6f, bone, Tag.HEAD)
        b.pair(Shape.ELL, 8.2f, 3.7f, 9.0f, 0.9f, 0.7f, 1.3f, stone, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 11.6f, 2.3f, 7.7f, 0.6f, 0.5f, 0.6f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(5.2f, 3.8f, 2.8f, 2.0f, 1.9f, 2.9f, stone, Tag.LEG_A, pz = 5.6f)
        b.ell(5.2f, -3.8f, 2.8f, 2.0f, 1.9f, 2.9f, stone, Tag.LEG_B, pz = 5.6f)
        b.ell(-5.0f, 3.8f, 2.8f, 2.1f, 2.0f, 2.9f, stone, Tag.LEG_C, pz = 5.6f)
        b.ell(-5.0f, -3.8f, 2.8f, 2.1f, 2.0f, 2.9f, stone, Tag.LEG_D, pz = 5.6f)
        b.ell(-8.6f, 0f, 5.8f, 1.8f, 0.9f, 0.9f, stone, Tag.TAIL, px = -7.4f, py = 0f, pz = 5.8f)
        b.clip(Anims.quad(swing = 0.7f, bob = 0.5f))
        b.clip(Anims.idle(breathe = 0.5f))
        b.clip(Anims.quad(frames = 4, swing = 1.0f, bob = 1.6f, name = "act"))
        return b.build()
    }
}
