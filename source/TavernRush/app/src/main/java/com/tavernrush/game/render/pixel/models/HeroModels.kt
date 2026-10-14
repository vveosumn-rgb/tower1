package com.tavernrush.game.render.pixel.models

import com.tavernrush.game.render.pixel.Anims
import com.tavernrush.game.render.pixel.Mat
import com.tavernrush.game.render.pixel.Model
import com.tavernrush.game.render.pixel.ModelBuilder
import com.tavernrush.game.render.pixel.Pat
import com.tavernrush.game.render.pixel.Shape
import com.tavernrush.game.render.pixel.Tag

/** Четыре героя таверны. Общий каркас «большая голова, мягкие формы» — читается на 30 пикселях. */
object HeroModels {
    private val skin = Mat.of(0xFFE9A67C.toInt(), rim = 1.2f, dither = 0.25f)
    private val eye = Mat.of(0xFF241626.toInt(), shine = 0.3f)
    private val eyeShine = Mat.glow(0xFFFFFFFF.toInt())
    private val boots = Mat.of(0xFF5B3B27.toInt(), shine = 0.15f)
    private val belt = Mat.of(0xFF4A2F20.toInt())
    private val gold = Mat.of(0xFFF2B33D.toInt(), shine = 0.9f)

    /** Общая часть: ноги, торс, руки, голова. Мелочи одежды добавляет каждая модель сама. */
    private fun biped(b: ModelBuilder, pants: Mat, torso: Mat, sleeve: Mat, headW: Float = 6f) {
        b.pair(Shape.ELL, 0f, 2.7f, 4.4f, 2.3f, 2.3f, 4.5f, pants, Tag.LEG_A, Tag.LEG_B, pz = 8.6f)
        b.pair(Shape.ELL, 1.1f, 2.7f, 1.7f, 3.5f, 2.7f, 2.0f, boots, Tag.LEG_A, Tag.LEG_B, pz = 8.6f)
        b.ell(0f, 0f, 13.4f, 4.7f, 5.6f, 5.9f, torso, Tag.BODY)
        b.ell(0f, 0f, 8.7f, 5.0f, 5.8f, 1.2f, belt, Tag.BODY)
        b.pair(Shape.ELL, 0f, 6.3f, 13.6f, 2.1f, 2.1f, 4.4f, sleeve, Tag.ARM_A, Tag.ARM_B, pz = 17.8f)
        b.pair(Shape.ELL, 0.5f, 6.5f, 9.0f, 1.9f, 1.9f, 1.9f, skin, Tag.ARM_A, Tag.ARM_B, pz = 17.8f)
        b.ell(0.4f, 0f, 23.2f, headW, headW - 0.4f, 5.8f, skin, Tag.HEAD)
        b.pair(Shape.ELL, 5.6f, 2.3f, 23.4f, 0.95f, 1.0f, 1.55f, eye, Tag.HEAD, Tag.HEAD)
        b.pair(Shape.ELL, 6.3f, 2.1f, 24.1f, 0.38f, 0.42f, 0.5f, eyeShine, Tag.HEAD, Tag.HEAD)
        b.ell(6.3f, 0f, 22.2f, 1.5f, 1.3f, 1.3f, skin, Tag.HEAD)
    }

    /** Трактирщик: синяя туника, фартук, красная бандана, рыжая борода. */
    fun innkeeper(): Model {
        val tunic = Mat.of(0xFF3F70B4.toInt())
        val pants = Mat.of(0xFF6B4A36.toInt())
        val apron = Mat.of(0xFFEFE4CC.toInt(), dither = 0.1f)
        val band = Mat.of(0xFFB82F2B.toInt(), pattern = Pat.DOTS, patScale = 2.6f)
        val beard = Mat.of(0xFFB8602D.toInt(), pattern = Pat.NOISE, patScale = 1.6f)
        val b = ModelBuilder("hero_innkeeper")
        b.scale = 1.7f; b.footDrop = 20f
        biped(b, pants, tunic, tunic)
        b.box(4.1f, 0f, 11.4f, 1.0f, 3.6f, 4.6f, apron, Tag.BODY)
        b.ell(3.0f, 0f, 19.3f, 3.6f, 4.6f, 3.4f, beard, Tag.HEAD)
        b.ell(5.2f, 0f, 21.0f, 1.7f, 3.3f, 0.9f, beard, Tag.HEAD)
        b.ell(0.2f, 0f, 25.6f, 6.4f, 6.0f, 3.5f, band, Tag.HEAD)
        b.ell(-6.0f, -0.8f, 24.6f, 1.7f, 1.5f, 2.3f, band, Tag.HEAD)
        b.ell(-7.4f, 1.2f, 23.2f, 1.4f, 1.0f, 2.6f, band, Tag.HEAD, ax = 0.3f)
        b.ell(4.9f, 0f, 8.7f, 0.6f, 1.6f, 1.1f, gold, Tag.BODY)
        b.clip(Anims.walk()).clip(Anims.idle())
        return b.build()
    }

    /** Страж очага: сталь, шлем с красным гребнем, круглый щит. */
    fun guardian(): Model {
        val steel = Mat.of(0xFF7E8EA6.toInt(), shine = 0.8f)
        val under = Mat.of(0xFF3E4E78.toInt())
        val plume = Mat.of(0xFFD9443A.toInt())
        val shield = Mat.of(0xFF9B5B2E.toInt(), pattern = Pat.PLANKS, patScale = 2.4f)
        val b = ModelBuilder("hero_guardian")
        b.scale = 1.7f; b.footDrop = 20f
        biped(b, under, steel, steel, headW = 6.1f)
        b.ell(0.2f, 0f, 16.2f, 5.2f, 6.4f, 3.2f, steel, Tag.BODY)
        b.pair(Shape.ELL, 0f, 7.6f, 18.0f, 2.8f, 3.0f, 2.2f, steel, Tag.ARM_A, Tag.ARM_B, pz = 17.8f)
        b.ell(-0.4f, 0f, 25.6f, 6.6f, 6.4f, 4.2f, steel, Tag.HEAD)
        b.box(5.6f, 0f, 24.0f, 0.9f, 0.9f, 3.4f, steel, Tag.HEAD)
        b.ell(-2.0f, 0f, 30.6f, 1.3f, 1.3f, 3.0f, plume, Tag.HEAD)
        b.ell(-4.0f, 0f, 29.4f, 2.4f, 1.3f, 2.4f, plume, Tag.HEAD)
        b.cyl(5.2f, -8.2f, 11.6f, 6.4f, 6.4f, 1.1f, shield, Tag.ARM_B, ax = 1.5708f, ay = 0.35f, pz = 17.8f, py = -6.5f)
        b.cyl(5.2f, -8.9f, 11.6f, 2.4f, 2.4f, 1.2f, steel, Tag.ARM_B, ax = 1.5708f, ay = 0.35f, pz = 17.8f, py = -6.5f)
        b.clip(Anims.walk(swing = 0.7f, arms = 0.5f)).clip(Anims.idle())
        return b.build()
    }

    /** Чародейка: фиолетовая мантия, шляпа с загнутым кончиком, посох с кристаллом. */
    fun sage(): Model {
        val robe = Mat.of(0xFF7A4BC4.toInt(), dither = 0.15f)
        val trim = Mat.of(0xFFF2C94C.toInt(), shine = 0.6f)
        val hat = Mat.of(0xFF5F36A8.toInt())
        val hair = Mat.of(0xFFF4E2B8.toInt(), pattern = Pat.NOISE, patScale = 1.4f)
        val staff = Mat.of(0xFF7A4B2A.toInt())
        val crystal = Mat.glow(0xFF72E8FF.toInt())
        val b = ModelBuilder("hero_sage")
        b.scale = 1.7f; b.footDrop = 20f
        biped(b, robe, robe, robe, headW = 5.9f)
        b.cyl(0f, 0f, 7.6f, 6.6f, 7.4f, 3.8f, robe, Tag.BODY)
        b.ell(0f, 0f, 3.2f, 6.9f, 7.8f, 2.4f, robe, Tag.BODY)
        b.ell(0f, 0f, 9.6f, 6.0f, 6.9f, 1.0f, trim, Tag.BODY)
        b.ell(-2.2f, 0f, 22.6f, 5.6f, 6.6f, 6.2f, hair, Tag.HEAD)
        b.cyl(-0.6f, 0f, 27.0f, 7.4f, 7.4f, 0.8f, hat, Tag.HEAD, ay = -0.12f)
        b.cyl(-1.4f, 0f, 30.6f, 4.4f, 4.4f, 3.4f, hat, Tag.HEAD, ay = -0.12f)
        b.ell(-3.2f, 0f, 35.4f, 2.9f, 2.9f, 3.0f, hat, Tag.HEAD, ay = -0.5f)
        b.ell(-6.0f, 0f, 38.0f, 1.6f, 1.6f, 1.8f, hat, Tag.HEAD)
        b.ell(-0.6f, 0f, 27.6f, 7.6f, 7.6f, 0.45f, trim, Tag.HEAD)
        b.cyl(8.6f, -4.6f, 15.0f, 0.9f, 0.9f, 14.2f, staff, Tag.ARM_B, pz = 17.8f, py = -6.4f)
        b.ell(8.6f, -4.6f, 30.4f, 2.3f, 2.3f, 2.8f, crystal, Tag.ARM_B, pz = 17.8f, py = -6.4f)
        b.clip(Anims.walk(swing = 0.55f, arms = 0.4f, bob = 1.1f)).clip(Anims.idle(breathe = 0.7f))
        return b.build()
    }

    /** Следопыт: зелёный плащ с капюшоном, лук и колчан за спиной. */
    fun ranger(): Model {
        val cloak = Mat.of(0xFF4F9B63.toInt(), dither = 0.15f)
        val leather = Mat.of(0xFF8A5A33.toInt())
        val pants = Mat.of(0xFF5A4630.toInt())
        val hood = Mat.of(0xFF3F8A55.toInt())
        val bow = Mat.of(0xFFB98B4E.toInt(), shine = 0.2f)
        val b = ModelBuilder("hero_ranger")
        b.scale = 1.7f; b.footDrop = 20f
        biped(b, pants, leather, cloak, headW = 5.9f)
        b.ell(-2.4f, 0f, 12.6f, 5.2f, 6.6f, 7.2f, cloak, Tag.CAPE)
        b.ell(-4.0f, 0f, 7.4f, 4.4f, 6.0f, 4.0f, cloak, Tag.CAPE)
        b.ell(-2.4f, 0f, 24.8f, 6.6f, 7.0f, 6.0f, hood, Tag.HEAD)
        b.ell(-2.6f, 0f, 28.4f, 4.6f, 4.8f, 3.0f, hood, Tag.HEAD)
        b.cyl(-5.6f, 3.4f, 14.6f, 2.0f, 2.0f, 5.6f, leather, Tag.BODY, ay = 0.35f)
        b.ell(-6.4f, 3.4f, 20.6f, 0.9f, 0.9f, 2.2f, Mat.of(0xFFE6E0D0.toInt()), Tag.BODY)
        b.cyl(-2.4f, 7.0f, 17.0f, 0.7f, 0.7f, 10.4f, bow, Tag.BODY, ax = 0.15f)
        b.clip(Anims.walk(swing = 0.85f, arms = 0.7f, bob = 1.5f)).clip(Anims.idle())
        return b.build()
    }
}
