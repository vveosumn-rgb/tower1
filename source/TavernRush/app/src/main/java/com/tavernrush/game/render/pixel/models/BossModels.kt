package com.tavernrush.game.render.pixel.models

import com.tavernrush.game.render.pixel.Anims
import com.tavernrush.game.render.pixel.Mat
import com.tavernrush.game.render.pixel.Model
import com.tavernrush.game.render.pixel.ModelBuilder
import com.tavernrush.game.render.pixel.Pat
import com.tavernrush.game.render.pixel.Shape
import com.tavernrush.game.render.pixel.Tag
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Боссы: огромные модели с клипами ходьбы и «замаха/удара». Силуэт читается с первого взгляда. */
object BossModels {
    private val gold = Mat.of(0xFFF2B33D.toInt(), shine = 1f, rim = 1.4f, dither = 0.1f)
    private val bone = Mat.of(0xFFF2E8C8.toInt(), shine = 0.4f)
    private val steel = Mat.of(0xFFB4C0D2.toInt(), shine = 1f, rim = 1.4f)
    private val wood = Mat.of(0xFF86552F.toInt(), pattern = Pat.NOISE, patScale = 1.6f)

    fun ratKing(): Model {
        val fur = Mat.of(0xFFB89A7A.toInt(), pattern = Pat.NOISE, patScale = 1.4f, dither = 0.22f)
        val pink = Mat.of(0xFFE79AA0.toInt())
        val cape = Mat.of(0xFFC0392B.toInt(), dither = 0.15f)
        val eye = Mat.glow(0xFFFF4A3A.toInt())
        val ruby = Mat.glow(0xFFFF3B5A.toInt())
        val b = ModelBuilder("rat_king")
        b.scale = 2.9f; b.footDrop = 12f
        b.ell(0f, 0f, 4.6f, 7.2f, 4.4f, 4.0f, fur, Tag.BODY)
        b.ell(-4.4f, 0f, 4.8f, 4.4f, 3.9f, 3.7f, fur, Tag.BODY)
        b.ell(7.0f, 0f, 5.6f, 3.9f, 3.1f, 3.1f, fur, Tag.HEAD)
        b.ell(10.2f, 0f, 4.8f, 2.3f, 1.7f, 1.6f, fur, Tag.HEAD)
        b.ell(11.9f, 0f, 4.8f, 0.9f, 0.9f, 0.8f, pink, Tag.HEAD)
        b.pair(Shape.ELL, 11.2f, 0.7f, 3.4f, 0.5f, 0.4f, 1.2f, bone, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 5.4f, 2.7f, 8.8f, 1.5f, 1.2f, 2.2f, pink, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 8.6f, 1.7f, 6.6f, 0.8f, 0.8f, 0.8f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(4.0f, 3.0f, 2.2f, 1.6f, 1.4f, 2.4f, fur, Tag.LEG_A, pz = 4.4f)
        b.ell(4.0f, -3.0f, 2.2f, 1.6f, 1.4f, 2.4f, fur, Tag.LEG_B, pz = 4.4f)
        b.ell(-5.2f, 3.2f, 2.2f, 1.8f, 1.6f, 2.4f, fur, Tag.LEG_C, pz = 4.4f)
        b.ell(-5.2f, -3.2f, 2.2f, 1.8f, 1.6f, 2.4f, fur, Tag.LEG_D, pz = 4.4f)
        for (k in 0 until 5) b.ell(-8.4f - k * 2.5f, k * 0.4f, 4.0f + k * 0.5f, 2.4f - k * 0.2f, 0.85f, 0.85f, pink, Tag.TAIL, px = -7f, py = 0f, pz = 4f)
        // корона
        b.cyl(6.6f, 0f, 9.4f, 2.5f, 2.5f, 0.9f, gold, Tag.HEAD)
        for (k in 0 until 5) {
            val a = (2.0 * PI * k / 5).toFloat()
            b.ell(6.6f + cos(a) * 2.2f, sin(a) * 2.2f, 11.2f, 0.6f, 0.6f, 1.5f, gold, Tag.HEAD)
        }
        b.ell(8.6f, 0f, 9.8f, 0.6f, 0.6f, 0.6f, ruby, Tag.HEAD)
        // плащ
        b.ell(-1.6f, 0f, 6.6f, 5.2f, 5.6f, 1.0f, cape, Tag.CAPE, ay = 0.3f)
        b.ell(-6.6f, 0f, 5.2f, 4.2f, 4.8f, 0.9f, cape, Tag.CAPE, ay = -0.2f)
        b.clip(Anims.quad(swing = 0.9f, bob = 1.3f)).clip(Anims.rear())
        return b.build()
    }

    fun ogreChef(): Model {
        val skin = Mat.of(0xFFD9A06A.toInt(), rim = 1.2f, pattern = Pat.NOISE, patScale = 2.2f, dither = 0.2f)
        val apron = Mat.of(0xFFF1E8D2.toInt(), pattern = Pat.NOISE, patScale = 2.4f, dither = 0.12f)
        val stain = Mat.of(0xFF9B3A2A.toInt())
        val hat = Mat.of(0xFFF6F2FF.toInt(), shine = 0.2f)
        val pants = Mat.of(0xFF4B3A2C.toInt())
        val boots = Mat.of(0xFF2E2018.toInt())
        val brow = Mat.of(0xFF3B2A1E.toInt(), pattern = Pat.NOISE, patScale = 1.4f)
        val eye = Mat.glow(0xFFFF5A3A.toInt())
        val b = ModelBuilder("ogre_chef")
        b.scale = 2.8f; b.footDrop = 38f
        b.pair(Shape.ELL, 0f, 4.4f, 5.0f, 3.5f, 3.5f, 5.3f, pants, Tag.LEG_A, Tag.LEG_B, pz = 10f)
        b.pair(Shape.ELL, 1.6f, 4.4f, 1.8f, 4.8f, 3.7f, 2.3f, boots, Tag.LEG_A, Tag.LEG_B, pz = 10f)
        b.ell(1.8f, 0f, 14.4f, 9.4f, 9.8f, 8.6f, skin, Tag.BODY)
        b.ell(5.0f, 0f, 13.0f, 5.6f, 8.2f, 7.8f, apron, Tag.BODY)
        b.ell(9.6f, 2.0f, 12.4f, 0.8f, 2.6f, 2.0f, stain, Tag.BODY)
        b.ell(9.4f, -3.0f, 15.6f, 0.8f, 1.8f, 1.6f, stain, Tag.BODY)
        b.ell(0.4f, 0f, 20.8f, 7.4f, 9.8f, 5.0f, skin, Tag.BODY)
        b.pair(Shape.ELL, 0.4f, 11.6f, 17.6f, 3.7f, 3.7f, 6.6f, skin, Tag.ARM_A, Tag.ARM_B, pz = 23.4f)
        b.pair(Shape.ELL, 1.2f, 11.8f, 10.6f, 3.3f, 3.3f, 3.3f, skin, Tag.ARM_A, Tag.ARM_B, pz = 23.4f)
        b.cyl(2.4f, -14.0f, 15.0f, 1.0f, 1.0f, 5.0f, wood, Tag.ARM_B, pz = 23.4f, py = -11.6f, px = 0.4f)
        b.box(3.0f, -14.0f, 26.0f, 0.9f, 4.4f, 6.0f, steel, Tag.ARM_B, pz = 23.4f, py = -11.6f, px = 0.4f)
        b.cyl(2.2f, 12.6f, 14.4f, 0.8f, 0.8f, 6.6f, wood, Tag.ARM_A, pz = 23.4f, py = 11.6f, px = 0.4f)
        b.ell(2.2f, 12.6f, 21.6f, 2.8f, 2.8f, 1.5f, steel, Tag.ARM_A, pz = 23.4f, py = 11.6f, px = 0.4f)
        b.ell(4.0f, 0f, 27.0f, 6.0f, 5.8f, 5.2f, skin, Tag.HEAD)
        b.ell(5.2f, 0f, 23.8f, 4.8f, 4.8f, 2.6f, skin, Tag.HEAD)
        b.pair(Shape.ELL, 9.0f, 3.0f, 24.0f, 0.9f, 0.9f, 2.4f, bone, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 9.6f, 2.4f, 28.0f, 1.2f, 1.3f, 1.7f, eye, Tag.HEAD, Tag.HEAD)
        b.box(9.4f, 0f, 29.8f, 1.0f, 4.6f, 0.9f, brow, Tag.HEAD)
        b.ell(10.2f, 0f, 26.0f, 2.1f, 1.9f, 1.9f, skin, Tag.HEAD)
        b.ell(9.0f, 0f, 24.4f, 1.7f, 4.4f, 0.9f, brow, Tag.HEAD)
        b.cyl(-0.4f, 0f, 32.0f, 5.2f, 5.2f, 1.1f, hat, Tag.HEAD, ay = -0.15f)
        b.ell(-0.8f, 0f, 36.4f, 6.4f, 6.4f, 4.4f, hat, Tag.HEAD, ay = -0.15f)
        b.ell(-4.4f, 3.2f, 35.4f, 4.2f, 4.2f, 3.4f, hat, Tag.HEAD)
        b.ell(-4.4f, -3.2f, 35.4f, 4.2f, 4.2f, 3.4f, hat, Tag.HEAD)
        b.ell(0.6f, 0f, 39.6f, 4.6f, 4.6f, 3.0f, hat, Tag.HEAD)
        b.clip(Anims.walk(swing = 0.5f, arms = 0.3f, bob = 1.4f, lean = 0.04f)).clip(Anims.slam())
        return b.build()
    }

    fun banshee(): Model {
        val skin = Mat.of(0xFFD9E4FF.toInt(), rim = 1.6f, contrast = 0.85f, dither = 0.25f)
        val dress = Mat.of(0xFF9FB0E8.toInt(), rim = 1.6f, contrast = 0.85f, dither = 0.3f)
        val dress2 = Mat.of(0xFFB8C6F2.toInt(), rim = 1.6f, contrast = 0.85f, dither = 0.3f)
        val hair = Mat.of(0xFFC9C0F0.toInt(), rim = 1.8f, contrast = 0.9f, pattern = Pat.NOISE, patScale = 1.8f)
        val eye = Mat.glow(0xFF7FF3FF.toInt())
        val mouth = Mat.of(0xFF2A2650.toInt(), dither = 0f)
        val L = 5f
        val b = ModelBuilder("banshee")
        b.scale = 2.7f; b.footDrop = 40f
        b.yaw = -1.15f
        b.ell(0f, 0f, 4.0f + L, 8.0f, 8.4f, 3.6f, dress, Tag.TAIL)
        b.ell(0f, 0f, 9.0f + L, 6.0f, 6.4f, 5.0f, dress, Tag.TAIL)
        b.ell(-5.0f, 0f, 2.6f + L, 4.0f, 5.4f, 2.2f, dress2, Tag.TAIL)
        b.ell(-8.6f, 0f, 1.8f + L, 3.0f, 4.2f, 1.6f, dress, Tag.TAIL)
        for (k in 0 until 6) {
            val a = (2.0 * PI * k / 6).toFloat()
            b.ell(cos(a) * 7.4f, sin(a) * 7.8f, 1.8f + L, 1.6f, 1.6f, 2.8f, dress2, Tag.TAIL, ay = cos(a) * 0.5f, ax = -sin(a) * 0.5f)
        }
        b.ell(0.2f, 0f, 16.4f + L, 4.0f, 4.6f, 4.6f, dress, Tag.BODY)
        b.pair(Shape.ELL, 1.0f, 6.0f, 15.0f + L, 1.5f, 1.5f, 5.2f, skin, Tag.ARM_A, Tag.ARM_B, pz = 19.4f + L, ax = 1.15f)
        b.pair(Shape.ELL, 1.0f, 12.2f, 11.6f + L, 1.2f, 1.3f, 3.0f, skin, Tag.ARM_A, Tag.ARM_B, pz = 19.4f + L, ax = 1.15f)
        b.ell(1.0f, 0f, 23.4f + L, 5.0f, 4.8f, 5.1f, skin, Tag.HEAD)
        b.ell(-2.4f, 0f, 24.4f + L, 3.8f, 7.0f, 5.8f, hair, Tag.CAPE)
        b.pair(Shape.ELL, -1.6f, 5.6f, 17.0f + L, 2.0f, 2.2f, 7.6f, hair, Tag.CAPE, Tag.CAPE, ay = 0.15f)
        b.ell(-5.0f, 0f, 15.0f + L, 2.2f, 4.0f, 8.0f, hair, Tag.CAPE, ay = 0.25f)
        b.pair(Shape.ELL, 5.6f, 2.1f, 24.2f + L, 1.5f, 1.5f, 2.2f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(6.0f, 0f, 20.6f + L, 1.2f, 2.0f, 2.8f, mouth, Tag.HEAD)
        b.clip(Anims.hover(bob = 2.2f)).clip(Anims.slam())
        return b.build()
    }
}
