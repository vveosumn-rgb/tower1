package com.tavernrush.game.render.pixel.models

import com.tavernrush.game.render.pixel.Anims
import com.tavernrush.game.render.pixel.Mat
import com.tavernrush.game.render.pixel.Model
import com.tavernrush.game.render.pixel.ModelBuilder
import com.tavernrush.game.render.pixel.Pat
import com.tavernrush.game.render.pixel.Shape
import com.tavernrush.game.render.pixel.Tag

/** Обычные враги. Каждый силуэт отличим в тени за 0,3 с: форма важнее деталей. */
object EnemyModels {
    private val steel = Mat.of(0xFF9AA8BC.toInt(), shine = 0.8f)
    private val bone = Mat.of(0xFFEFE6CC.toInt(), shine = 0.2f)
    private val wood = Mat.of(0xFF86552F.toInt(), pattern = Pat.NOISE, patScale = 1.6f)
    private val white = Mat.of(0xFFF6F2FF.toInt(), shine = 0.5f)
    private val pupil = Mat.of(0xFF1C1424.toInt(), shine = 0.4f)

    fun rat(): Model {
        val fur = Mat.of(0xFF8F7468.toInt(), pattern = Pat.NOISE, patScale = 1.3f, dither = 0.2f)
        val pink = Mat.of(0xFFE79AA0.toInt())
        val eye = Mat.glow(0xFFFF4A3A.toInt())
        val b = ModelBuilder("rat")
        b.scale = 1.05f; b.footDrop = 4f
        b.ell(0f, 0f, 3.8f, 6.3f, 3.5f, 3.3f, fur, Tag.BODY)
        b.ell(-3.4f, 0f, 3.9f, 3.6f, 3.1f, 3.0f, fur, Tag.BODY)
        b.ell(6.0f, 0f, 4.5f, 3.1f, 2.5f, 2.5f, fur, Tag.HEAD)
        b.ell(8.6f, 0f, 3.9f, 1.9f, 1.4f, 1.3f, fur, Tag.HEAD)
        b.ell(10.3f, 0f, 3.9f, 0.8f, 0.8f, 0.7f, pink, Tag.HEAD)
        b.pair(Shape.ELL, 4.6f, 2.0f, 7.0f, 1.2f, 1.0f, 1.7f, pink, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 7.5f, 1.3f, 5.3f, 0.6f, 0.6f, 0.6f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(3.4f, 2.1f, 1.6f, 1.1f, 1.0f, 1.8f, fur, Tag.LEG_A, pz = 3.4f)
        b.ell(3.4f, -2.1f, 1.6f, 1.1f, 1.0f, 1.8f, fur, Tag.LEG_B, pz = 3.4f)
        b.ell(-4.0f, 2.3f, 1.6f, 1.2f, 1.1f, 1.8f, fur, Tag.LEG_C, pz = 3.4f)
        b.ell(-4.0f, -2.3f, 1.6f, 1.2f, 1.1f, 1.8f, fur, Tag.LEG_D, pz = 3.4f)
        for (k in 0 until 3) {
            b.ell(-7.6f - k * 2.4f, k * 0.5f, 3.2f + k * 0.5f, 2.3f - k * 0.3f, 0.75f, 0.75f, pink, Tag.TAIL, px = -5.8f, py = 0f, pz = 3.2f)
        }
        b.clip(Anims.quad(swing = 1.0f, bob = 0.8f))
        return b.build()
    }

    fun goblin(): Model {
        val skin = Mat.of(0xFF6DBE45.toInt(), rim = 1.2f, dither = 0.25f)
        val cloth = Mat.of(0xFF8A5A33.toInt(), pattern = Pat.STRIPES, patScale = 2.2f)
        val leather = Mat.of(0xFF5B3B27.toInt())
        val eye = Mat.glow(0xFFFFE23A.toInt())
        val b = ModelBuilder("goblin")
        b.scale = 1.35f; b.footDrop = 12f
        b.pair(Shape.ELL, 0.2f, 2.3f, 3.6f, 2.0f, 2.0f, 3.7f, skin, Tag.LEG_A, Tag.LEG_B, pz = 7.2f)
        b.pair(Shape.ELL, 1.2f, 2.3f, 1.3f, 3.0f, 2.3f, 1.7f, leather, Tag.LEG_A, Tag.LEG_B, pz = 7.2f)
        b.ell(0.6f, 0f, 10.8f, 4.0f, 4.6f, 4.8f, skin, Tag.BODY)
        b.box(2.0f, 0f, 7.6f, 2.4f, 3.6f, 2.4f, cloth, Tag.BODY)
        b.pair(Shape.ELL, 0.8f, 5.2f, 11.0f, 1.7f, 1.7f, 3.8f, skin, Tag.ARM_A, Tag.ARM_B, pz = 14.2f)
        b.pair(Shape.ELL, 1.2f, 5.4f, 7.2f, 1.6f, 1.6f, 1.6f, skin, Tag.ARM_A, Tag.ARM_B, pz = 14.2f)
        b.box(3.6f, -5.8f, 8.4f, 0.55f, 0.5f, 3.6f, steel, Tag.ARM_B, pz = 14.2f, py = -5.2f, px = 0.8f)
        b.ell(2.2f, 0f, 17.4f, 5.3f, 4.9f, 4.7f, skin, Tag.HEAD)
        b.pair(Shape.ELL, 0.4f, 6.2f, 18.2f, 1.1f, 4.3f, 1.4f, skin, Tag.EAR_A, Tag.EAR_B, az = 0.45f)
        b.pair(Shape.ELL, 6.4f, 2.3f, 18.4f, 1.0f, 1.1f, 1.4f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(7.4f, 0f, 16.4f, 1.7f, 1.2f, 1.9f, skin, Tag.HEAD)
        b.ell(5.0f, 0f, 14.4f, 2.2f, 2.8f, 0.8f, leather, Tag.HEAD)
        b.clip(Anims.walk(swing = 0.9f, arms = 0.75f, bob = 1.1f, lean = 0.12f))
        return b.build()
    }

    fun bat(): Model {
        val fur = Mat.of(0xFF8A5BC4.toInt(), pattern = Pat.NOISE, patScale = 1.4f, dither = 0.2f)
        val wing = Mat.of(0xFF6C3FA8.toInt(), pattern = Pat.STRIPES, patScale = 2.8f, dither = 0.15f, rim = 1.3f)
        val eye = Mat.glow(0xFFFF6A4A.toInt())
        val b = ModelBuilder("bat")
        b.scale = 1.1f; b.footDrop = 12f
        b.yaw = -1.25f
        b.ell(0f, 0f, 13.0f, 3.2f, 2.8f, 3.7f, fur, Tag.BODY)
        b.ell(3.4f, 0f, 14.3f, 2.5f, 2.3f, 2.3f, fur, Tag.HEAD)
        b.pair(Shape.ELL, 3.2f, 1.4f, 16.7f, 0.7f, 0.6f, 1.8f, fur, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 5.3f, 1.0f, 14.8f, 0.6f, 0.6f, 0.6f, eye, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 5.2f, 0.6f, 12.9f, 0.3f, 0.3f, 0.9f, white, Tag.HEAD, Tag.HEAD)
        for (side in 0..1) {
            val sg = if (side == 0) 1f else -1f
            val tag = if (side == 0) Tag.WING_A else Tag.WING_B
            b.ell(-0.6f, sg * 4.4f, 13.4f, 3.4f, 4.2f, 0.8f, wing, tag, px = 0f, py = sg * 1.6f, pz = 13.4f)
            b.ell(-1.4f, sg * 9.4f, 13.9f, 3.0f, 4.0f, 0.7f, wing, tag, px = 0f, py = sg * 1.6f, pz = 13.4f, az = -sg * 0.2f)
            b.ell(-2.6f, sg * 13.8f, 14.5f, 2.2f, 3.2f, 0.6f, wing, tag, px = 0f, py = sg * 1.6f, pz = 13.4f, az = -sg * 0.35f)
        }
        b.pair(Shape.ELL, 0f, 1.2f, 9.2f, 0.6f, 0.6f, 1.1f, fur, Tag.NONE, Tag.NONE)
        b.clip(Anims.flap(amp = 0.7f, bob = 1.8f))
        return b.build()
    }

    fun slime(scale: Float = 1.5f, foot: Float = 7f, id: String = "slime"): Model {
        val jelly = Mat.of(0xFF4FD1A5.toInt(), shine = 0.8f, rim = 1.5f, dither = 0.2f)
        val shine = Mat.of(0xFFC8FFEA.toInt(), shine = 1f)
        val b = ModelBuilder(id)
        b.scale = scale; b.footDrop = foot
        b.ell(0f, 0f, 5.8f, 8.8f, 8.4f, 6.4f, jelly, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(3.6f, -5.2f, 9.6f, 1.5f, 1.2f, 1.1f, shine, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(-4.6f, 3.8f, 8.8f, 1.1f, 1.0f, 0.9f, shine, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(6.9f, 2.8f, 7.6f, 1.6f, 1.8f, 2.3f, white, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(6.9f, -2.8f, 7.6f, 1.6f, 1.8f, 2.3f, white, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(8.1f, 2.8f, 7.0f, 0.9f, 1.1f, 1.6f, pupil, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(8.1f, -2.8f, 7.0f, 0.9f, 1.1f, 1.6f, pupil, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.ell(7.6f, 0f, 4.0f, 1.0f, 2.4f, 0.7f, pupil, Tag.EXTRA, px = 0f, py = 0f, pz = 0f)
        b.clip(Anims.hop(squash = 0.18f, jump = 2.8f))
        return b.build()
    }

    fun orc(): Model {
        val skin = Mat.of(0xFF7F9E4A.toInt(), rim = 1.2f, pattern = Pat.NOISE, patScale = 1.8f, dither = 0.25f)
        val leather = Mat.of(0xFF8A5A33.toInt(), pattern = Pat.NOISE, patScale = 2f)
        val pants = Mat.of(0xFF4B3A2C.toInt())
        val boots = Mat.of(0xFF3B2A20.toInt())
        val belt = Mat.of(0xFF2E2018.toInt())
        val eye = Mat.glow(0xFFFFC23A.toInt())
        val b = ModelBuilder("orc")
        b.scale = 1.35f; b.footDrop = 18f
        b.pair(Shape.ELL, 0f, 3.5f, 4.8f, 3.1f, 3.1f, 5.1f, pants, Tag.LEG_A, Tag.LEG_B, pz = 9.8f)
        b.pair(Shape.ELL, 1.3f, 3.5f, 1.8f, 4.2f, 3.3f, 2.3f, boots, Tag.LEG_A, Tag.LEG_B, pz = 9.8f)
        b.ell(0.6f, 0f, 15.6f, 6.9f, 8.0f, 7.3f, skin, Tag.BODY)
        b.ell(2.4f, 0f, 14.0f, 5.2f, 6.6f, 5.6f, leather, Tag.BODY)
        b.ell(0.4f, 0f, 9.9f, 7.1f, 8.2f, 1.5f, belt, Tag.BODY)
        b.pair(Shape.ELL, 0.4f, 9.4f, 15.6f, 2.9f, 2.9f, 5.6f, skin, Tag.ARM_A, Tag.ARM_B, pz = 21.2f)
        b.pair(Shape.ELL, 1.0f, 9.6f, 9.4f, 2.6f, 2.6f, 2.6f, skin, Tag.ARM_A, Tag.ARM_B, pz = 21.2f)
        b.pair(Shape.ELL, 0.2f, 9.2f, 21.2f, 3.6f, 3.6f, 2.8f, steel, Tag.ARM_A, Tag.ARM_B, pz = 21.2f)
        b.cyl(2.4f, -10.2f, 13.0f, 0.95f, 0.95f, 8.4f, wood, Tag.ARM_B, pz = 21.2f, py = -9.6f, px = 0.4f, ay = 0.25f)
        b.box(3.6f, -10.2f, 21.0f, 0.8f, 4.2f, 3.2f, steel, Tag.ARM_B, pz = 21.2f, py = -9.6f, px = 0.4f, ay = 0.25f)
        b.ell(1.4f, 0f, 25.2f, 5.4f, 5.1f, 4.7f, skin, Tag.HEAD)
        b.ell(3.0f, 0f, 22.0f, 4.3f, 4.5f, 2.5f, skin, Tag.HEAD)
        b.pair(Shape.ELL, 5.8f, 2.7f, 22.8f, 0.9f, 0.9f, 2.0f, bone, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 6.4f, 2.0f, 26.0f, 0.8f, 0.9f, 1.0f, eye, Tag.HEAD, Tag.HEAD)
        b.box(5.8f, 0f, 27.4f, 1.0f, 4.2f, 0.9f, skin, Tag.HEAD)
        b.clip(Anims.walk(swing = 0.7f, arms = 0.55f, bob = 1.0f, lean = 0.1f))
        return b.build()
    }

    fun archer(): Model {
        val skin = Mat.of(0xFF8FCB57.toInt(), rim = 1.2f, dither = 0.25f)
        val tunic = Mat.of(0xFF6B5B3A.toInt(), pattern = Pat.NOISE, patScale = 2f)
        val hood = Mat.of(0xFF3F6B3A.toInt())
        val leather = Mat.of(0xFF5B3B27.toInt())
        val string = Mat.of(0xFFEFE6CC.toInt())
        val eye = Mat.glow(0xFFFFE23A.toInt())
        val b = ModelBuilder("archer")
        b.scale = 1.3f; b.footDrop = 12f
        b.pair(Shape.ELL, 0.2f, 2.2f, 3.6f, 1.9f, 1.9f, 3.7f, leather, Tag.LEG_A, Tag.LEG_B, pz = 7.2f)
        b.pair(Shape.ELL, 1.2f, 2.2f, 1.3f, 2.9f, 2.2f, 1.7f, leather, Tag.LEG_A, Tag.LEG_B, pz = 7.2f)
        b.ell(0.4f, 0f, 10.6f, 3.8f, 4.4f, 4.6f, tunic, Tag.BODY)
        b.pair(Shape.ELL, 0.6f, 5.0f, 10.8f, 1.6f, 1.6f, 3.6f, skin, Tag.ARM_A, Tag.ARM_B, pz = 13.8f)
        b.pair(Shape.ELL, 1.0f, 5.2f, 7.2f, 1.5f, 1.5f, 1.5f, skin, Tag.ARM_A, Tag.ARM_B, pz = 13.8f)
        b.cyl(4.6f, -6.4f, 11.6f, 0.65f, 0.65f, 7.2f, Mat.of(0xFF9B6B3A.toInt()), Tag.ARM_B, pz = 13.8f, py = -5.0f, px = 0.6f, ay = 0.18f)
        b.box(4.0f, -6.4f, 11.6f, 0.15f, 0.15f, 6.8f, string, Tag.ARM_B, pz = 13.8f, py = -5.0f, px = 0.6f, ay = 0.18f)
        b.ell(2.0f, 0f, 17.0f, 5.0f, 4.7f, 4.5f, skin, Tag.HEAD)
        b.ell(-1.2f, 0f, 19.4f, 4.8f, 5.3f, 4.0f, hood, Tag.HEAD)
        b.ell(-4.2f, 0f, 22.4f, 1.9f, 1.9f, 2.5f, hood, Tag.HEAD, ay = -0.6f)
        b.pair(Shape.ELL, 0.2f, 5.8f, 17.4f, 0.9f, 3.2f, 1.2f, skin, Tag.EAR_A, Tag.EAR_B, az = 0.4f)
        b.pair(Shape.ELL, 6.3f, 2.2f, 17.8f, 0.9f, 1.0f, 1.2f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(7.0f, 0f, 16.2f, 1.5f, 1.1f, 1.7f, skin, Tag.HEAD)
        b.cyl(-4.2f, 3.0f, 12.8f, 1.7f, 1.7f, 4.6f, leather, Tag.BODY, ay = 0.35f)
        for (k in -1..1) b.ell(-5.6f, 3.0f + k * 0.8f, 18.4f, 0.55f, 0.55f, 1.6f, string, Tag.BODY, ay = 0.3f)
        b.clip(Anims.walk(swing = 0.85f, arms = 0.5f, bob = 1.1f, lean = 0.1f))
        return b.build()
    }

    fun boar(): Model {
        val fur = Mat.of(0xFF8A5A3A.toInt(), pattern = Pat.NOISE, patScale = 1.6f, dither = 0.22f)
        val mane = Mat.of(0xFF4B2E22.toInt(), pattern = Pat.NOISE, patScale = 1.3f)
        val pink = Mat.of(0xFFD98A8A.toInt())
        val dark = Mat.of(0xFF2E2018.toInt())
        val eye = Mat.glow(0xFFFF6A4A.toInt())
        val b = ModelBuilder("boar")
        b.scale = 1.25f; b.footDrop = 9f
        b.ell(0f, 0f, 7.4f, 9.4f, 5.6f, 5.2f, fur, Tag.BODY)
        b.ell(-1.8f, 0f, 11.2f, 5.0f, 2.6f, 2.0f, mane, Tag.BODY)
        b.ell(-6f, 0f, 7.2f, 5f, 5f, 4.6f, fur, Tag.BODY)
        b.ell(9.4f, 0f, 7.0f, 4.8f, 4.0f, 3.8f, fur, Tag.HEAD)
        b.ell(13.0f, 0f, 5.8f, 3.4f, 2.9f, 2.6f, pink, Tag.HEAD)
        b.ell(15.4f, 0f, 5.8f, 0.9f, 1.6f, 1.6f, dark, Tag.HEAD)
        b.pair(Shape.ELL, 12.8f, 2.4f, 4.6f, 2.8f, 0.9f, 1.0f, bone, Tag.HEAD, Tag.HEAD, ay = -0.7f)
        b.pair(Shape.ELL, 7.2f, 2.8f, 10.4f, 1.2f, 1.0f, 1.8f, fur, Tag.EAR_A, Tag.EAR_B)
        b.pair(Shape.ELL, 11.0f, 2.3f, 8.4f, 0.8f, 0.8f, 0.8f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(5f, 3.2f, 2.8f, 1.6f, 1.5f, 2.8f, fur, Tag.LEG_A, pz = 5.4f)
        b.ell(5f, -3.2f, 2.8f, 1.6f, 1.5f, 2.8f, fur, Tag.LEG_B, pz = 5.4f)
        b.ell(-5f, 3.4f, 2.8f, 1.7f, 1.6f, 2.8f, fur, Tag.LEG_C, pz = 5.4f)
        b.ell(-5f, -3.4f, 2.8f, 1.7f, 1.6f, 2.8f, fur, Tag.LEG_D, pz = 5.4f)
        b.ell(-10.6f, 0f, 8.4f, 1.0f, 1.0f, 2.2f, mane, Tag.TAIL, px = -9f, py = 0f, pz = 8f)
        b.clip(Anims.quad(swing = 0.9f, bob = 0.9f))
        return b.build()
    }

    fun bomber(): Model {
        val skin = Mat.of(0xFF7CC04A.toInt(), rim = 1.2f, dither = 0.25f)
        val vest = Mat.of(0xFFE0A030.toInt(), pattern = Pat.STRIPES, patScale = 2.4f)
        val leather = Mat.of(0xFF5B3B27.toInt())
        val glass = Mat.glow(0xFF8CE9FF.toInt())
        val fuse = Mat.glow(0xFFFFC94D.toInt())
        val b = ModelBuilder("bomber")
        b.scale = 1.25f; b.footDrop = 12f
        b.pair(Shape.ELL, 0.2f, 2.3f, 3.6f, 2.0f, 2.0f, 3.7f, skin, Tag.LEG_A, Tag.LEG_B, pz = 7.2f)
        b.pair(Shape.ELL, 1.2f, 2.3f, 1.3f, 3.0f, 2.3f, 1.7f, leather, Tag.LEG_A, Tag.LEG_B, pz = 7.2f)
        b.ell(0.4f, 0f, 10.8f, 4.0f, 4.6f, 4.8f, vest, Tag.BODY)
        b.pair(Shape.ELL, 0.8f, 5.2f, 11.0f, 1.7f, 1.7f, 3.8f, skin, Tag.ARM_A, Tag.ARM_B, pz = 14.2f)
        b.pair(Shape.ELL, 1.2f, 5.4f, 7.2f, 1.6f, 1.6f, 1.6f, skin, Tag.ARM_A, Tag.ARM_B, pz = 14.2f)
        b.ell(2.2f, 0f, 17.4f, 5.3f, 4.9f, 4.7f, skin, Tag.HEAD)
        b.pair(Shape.ELL, 0.4f, 6.2f, 18.2f, 1.1f, 4.3f, 1.4f, skin, Tag.EAR_A, Tag.EAR_B, az = 0.45f)
        b.pair(Shape.ELL, 6.0f, 2.4f, 18.2f, 1.8f, 1.8f, 1.8f, glass, Tag.HEAD, Tag.HEAD)
        b.box(5.4f, 0f, 18.2f, 0.7f, 3.6f, 0.6f, leather, Tag.HEAD)
        b.ell(7.4f, 0f, 16.2f, 1.6f, 1.2f, 1.8f, skin, Tag.HEAD)
        val keg = Mat.of(0xFF86552F.toInt(), pattern = Pat.PLANKS, patScale = 2f)
        b.cyl(-5.6f, 0f, 12.4f, 3.8f, 3.8f, 4.6f, keg, Tag.BODY)
        b.cyl(-5.6f, 0f, 9.6f, 3.9f, 3.9f, 0.6f, Mat.of(0xFF6B7488.toInt(), shine = 0.8f), Tag.BODY)
        b.cyl(-5.6f, 0f, 15.2f, 3.9f, 3.9f, 0.6f, Mat.of(0xFF6B7488.toInt(), shine = 0.8f), Tag.BODY)
        b.ell(-5.6f, 0f, 18.2f, 1.1f, 1.1f, 1.5f, fuse, Tag.BODY)
        b.clip(Anims.walk(swing = 1.1f, arms = 0.9f, bob = 1.3f, lean = 0.14f))
        return b.build()
    }

    fun shaman(): Model {
        val skin = Mat.of(0xFF7CBF4C.toInt(), rim = 1.2f, dither = 0.25f)
        val robe = Mat.of(0xFF7A4BC4.toInt(), dither = 0.15f)
        val trim = Mat.of(0xFFF2C94C.toInt(), shine = 0.6f)
        val mask = Mat.of(0xFFF2E8C8.toInt(), shine = 0.3f)
        val wood = Mat.of(0xFF7A4B2A.toInt())
        val orb = Mat.glow(0xFFE07AFF.toInt())
        val eye = Mat.glow(0xFFFFE23A.toInt())
        val b = ModelBuilder("shaman")
        b.scale = 1.3f; b.footDrop = 12f
        b.ell(0f, 0f, 3.2f, 6.0f, 6.6f, 3.4f, robe, Tag.BODY)
        b.ell(0.2f, 0f, 7.4f, 4.8f, 5.4f, 4.4f, robe, Tag.BODY)
        b.ell(0.4f, 0f, 11.4f, 3.8f, 4.5f, 4.4f, robe, Tag.BODY)
        b.ell(0f, 0f, 8.4f, 4.8f, 5.4f, 0.8f, trim, Tag.BODY)
        b.pair(Shape.ELL, 0.8f, 5.2f, 11.0f, 1.7f, 1.7f, 3.8f, robe, Tag.ARM_A, Tag.ARM_B, pz = 14.4f)
        b.pair(Shape.ELL, 1.2f, 5.4f, 7.4f, 1.5f, 1.5f, 1.5f, skin, Tag.ARM_A, Tag.ARM_B, pz = 14.4f)
        b.ell(2.0f, 0f, 17.4f, 5.2f, 4.8f, 4.6f, skin, Tag.HEAD)
        b.ell(6.2f, 0f, 17.4f, 1.4f, 3.7f, 3.3f, mask, Tag.HEAD)
        b.pair(Shape.ELL, 0.4f, 6.2f, 18.4f, 1.0f, 4.0f, 1.4f, skin, Tag.EAR_A, Tag.EAR_B, az = 0.45f)
        b.pair(Shape.ELL, 7.0f, 1.8f, 18.0f, 0.5f, 0.9f, 1.0f, eye, Tag.HEAD, Tag.HEAD)
        b.ell(-0.4f, 0f, 22.6f, 0.9f, 0.9f, 3.8f, Mat.of(0xFF4FD1E0.toInt()), Tag.HEAD, ay = 0.15f)
        b.ell(-1.8f, 1.8f, 22.2f, 0.9f, 0.9f, 3.4f, Mat.of(0xFFE0584F.toInt()), Tag.HEAD, ay = 0.35f)
        b.ell(-1.8f, -1.8f, 22.2f, 0.9f, 0.9f, 3.4f, Mat.of(0xFFF2C94C.toInt()), Tag.HEAD, ay = 0.35f)
        b.cyl(3.4f, 6.0f, 12.4f, 0.75f, 0.75f, 9.4f, wood, Tag.ARM_A, pz = 14.4f, py = 5.2f, px = 0.8f)
        b.ell(3.4f, 6.0f, 22.6f, 2.5f, 2.5f, 2.5f, orb, Tag.ARM_A, pz = 14.4f, py = 5.2f, px = 0.8f)
        b.clip(Anims.walk(swing = 0.55f, arms = 0.4f, bob = 1.0f, lean = 0.06f)).clip(Anims.idle())
        return b.build()
    }

    fun ghost(): Model {
        val g = Mat.of(0xFFB8D8FF.toInt(), shine = 0.3f, rim = 1.8f, dither = 0.3f, contrast = 0.8f)
        val g2 = Mat.of(0xFFD4E6FF.toInt(), shine = 0.2f, rim = 1.8f, dither = 0.3f, contrast = 0.8f)
        val hollow = Mat.of(0xFF2A2650.toInt(), dither = 0f)
        val b = ModelBuilder("ghost")
        b.scale = 1.3f; b.footDrop = 12f
        b.yaw = -1.2f
        b.ell(0f, 0f, 13.0f, 6.2f, 5.8f, 8.6f, g, Tag.EXTRA)
        b.ell(0f, 0f, 6.8f, 5.6f, 5.4f, 3.8f, g, Tag.TAIL)
        b.ell(-3.0f, 0f, 3.8f, 3.6f, 3.4f, 2.6f, g2, Tag.TAIL)
        b.ell(-5.6f, 0f, 2.6f, 2.4f, 2.4f, 1.9f, g, Tag.TAIL)
        b.ell(3.0f, 2.2f, 2.8f, 2.2f, 2.0f, 2.0f, g2, Tag.TAIL)
        b.pair(Shape.ELL, 1.0f, 6.6f, 12.0f, 1.8f, 1.8f, 4.6f, g2, Tag.ARM_A, Tag.ARM_B, pz = 16.4f, ay = 0.25f)
        b.pair(Shape.ELL, 5.5f, 2.3f, 15.2f, 1.2f, 1.2f, 2.1f, hollow, Tag.EXTRA, Tag.EXTRA)
        b.ell(6.0f, 0f, 11.2f, 0.9f, 1.7f, 1.6f, hollow, Tag.EXTRA)
        b.clip(Anims.hover())
        return b.build()
    }

    fun spider(): Model {
        val body = Mat.of(0xFF6B4B8E.toInt(), pattern = Pat.DOTS, patScale = 2.6f, dither = 0.2f)
        val legMat = Mat.of(0xFF4B3A66.toInt(), shine = 0.3f)
        val eye = Mat.glow(0xFFFF4A5A.toInt())
        val fang = Mat.of(0xFFF2E8C8.toInt())
        val b = ModelBuilder("spider")
        b.scale = 1.4f; b.footDrop = 7f
        b.ell(-3.4f, 0f, 6.4f, 5.6f, 4.6f, 4.2f, body, Tag.BODY)
        b.ell(2.4f, 0f, 6.0f, 3.6f, 3.4f, 3.0f, body, Tag.BODY)
        b.pair(Shape.ELL, 5.6f, 1.1f, 7.4f, 0.8f, 0.8f, 0.8f, eye, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 5.2f, 2.0f, 6.0f, 0.6f, 0.6f, 0.6f, eye, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 5.8f, 0.9f, 4.4f, 0.4f, 0.4f, 1.1f, fang, Tag.HEAD, Tag.HEAD)
        for (i in 0 until 4) {
            val x = 3.2f - i * 2.1f
            for (side in 0..1) {
                val sg = if (side == 0) 1f else -1f
                val tag = if ((i + side) % 2 == 0) Tag.LEG_A else Tag.LEG_B
                val sp = 0.35f * (i - 1.5f)
                b.ell(x, sg * 5.0f, 7.4f, 1.0f, 3.8f, 1.0f, legMat, tag, az = -sg * (0.25f + sp), pz = 7.4f, px = x, py = sg * 2.6f)
                b.ell(x + sp * 4f, sg * 9.2f, 3.6f, 0.9f, 0.9f, 4.0f, legMat, tag, pz = 7.4f, px = x, py = sg * 2.6f)
            }
        }
        b.clip(Anims.quad(swing = 0.55f, bob = 0.6f))
        return b.build()
    }
}
