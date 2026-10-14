package com.tavernrush.game.render.pixel.models

import com.tavernrush.game.render.pixel.Clip
import com.tavernrush.game.render.pixel.Anims
import com.tavernrush.game.render.pixel.Mat
import com.tavernrush.game.render.pixel.Model
import com.tavernrush.game.render.pixel.ModelBuilder
import com.tavernrush.game.render.pixel.Pat
import com.tavernrush.game.render.pixel.Shape
import com.tavernrush.game.render.pixel.Tag
import kotlin.math.PI
import kotlin.math.sin

/** Предметы на земле, снаряды оружия и реквизит арены. Всё запекается тем же движком, что и персонажи. */
object PropModels {
    private fun tau(t: Float) = (2.0 * PI).toFloat() * t

    private val steel = Mat.of(0xFFB4C0D2.toInt(), shine = 1f, rim = 1.4f)
    private val darkSteel = Mat.of(0xFF6B7488.toInt(), shine = 0.8f)
    private val gold = Mat.of(0xFFF2B33D.toInt(), shine = 1f, rim = 1.4f)
    private val wood = Mat.of(0xFF8A5A33.toInt(), pattern = Pat.PLANKS, patScale = 2.2f, dither = 0.15f)
    private val woodDark = Mat.of(0xFF5B3B27.toInt())
    private val white = Mat.of(0xFFF6F2FF.toInt(), shine = 0.4f)

    /** Вращение вокруг вертикали (монета, сковорода) или кувырок вокруг оси Y (бочка). */
    private fun spin(frames: Int, vertical: Boolean, name: String = "spin"): Clip =
        Clip(name, frames) { _, t, _, o -> if (vertical) o.rz = tau(t) else o.ry = tau(t) }

    // ------------------------------------------------------------ лут

    /** Самоцвет опыта: граненый ромб (куб, поставленный на угол). */
    fun gem(color: Int, size: Float): Model {
        val m = Mat.of(color, shine = 1f, rim = 1.6f, dither = 0.1f)
        val core = Mat.glow(color)
        val b = ModelBuilder("gem")
        b.scale = 1f; b.yaw = 0.6f; b.footDrop = 4f
        b.box(0f, 0f, size * 0.95f, size * 0.52f, size * 0.52f, size * 0.52f, m, ax = 0.7854f, ay = 0.6155f, az = 0.3f, pz = size)
        b.ell(0.3f, -size * 0.2f, size * 1.0f, size * 0.16f, size * 0.12f, size * 0.16f, core, pz = size)
        b.clip(spin(8, true, "walk"))
        return b.build()
    }

    fun coin(): Model {
        val rim = Mat.of(0xFFF2B33D.toInt(), shine = 1f, rim = 1.5f, dither = 0.1f)
        val face = Mat.of(0xFFFFD866.toInt(), shine = 1f, dither = 0.1f)
        val b = ModelBuilder("coin")
        b.scale = 1f; b.yaw = 0f; b.footDrop = 5f
        b.cyl(0f, 0f, 5.2f, 4.4f, 4.4f, 0.9f, rim, ax = 1.5708f, px = 0f, py = 0f, pz = 5.2f)
        b.cyl(0f, -0.2f, 5.2f, 3.0f, 3.0f, 1.05f, face, ax = 1.5708f, px = 0f, py = 0f, pz = 5.2f)
        b.clip(spin(8, true, "walk"))
        return b.build()
    }

    /** Куриная ножка: лечение. */
    fun chicken(): Model {
        val meat = Mat.of(0xFFC9692E.toInt(), shine = 0.5f, rim = 1.3f, pattern = Pat.NOISE, patScale = 1.6f)
        val b = ModelBuilder("chicken")
        b.scale = 1.1f; b.yaw = -0.5f; b.footDrop = 5f
        b.ell(-1.4f, 0f, 6.0f, 5.6f, 4.4f, 4.4f, meat, ay = -0.3f, pz = 6f)
        b.cyl(5.4f, 0f, 5.2f, 1.1f, 1.1f, 4.0f, white, ay = 1.35f, pz = 6f)
        b.ell(9.4f, 1.0f, 5.4f, 1.7f, 1.7f, 1.7f, white, pz = 6f)
        b.ell(9.4f, -1.0f, 5.4f, 1.7f, 1.7f, 1.7f, white, pz = 6f)
        b.clip(Anims.idle(4, 0.4f, "walk"))
        return b.build()
    }

    /** Подкова-магнит: красная скоба с белыми концами. */
    fun magnet(): Model {
        val red = Mat.of(0xFFE05A5A.toInt(), shine = 0.9f, rim = 1.4f)
        val b = ModelBuilder("magnet")
        b.scale = 1f; b.yaw = -0.3f; b.footDrop = 5f
        b.box(0f, 3.6f, 6.0f, 1.8f, 1.8f, 5.0f, red)
        b.box(0f, -3.6f, 6.0f, 1.8f, 1.8f, 5.0f, red)
        b.box(0f, 0f, 10.4f, 1.8f, 5.4f, 1.8f, red)
        b.box(0f, 3.6f, 1.6f, 1.9f, 1.9f, 1.7f, white)
        b.box(0f, -3.6f, 1.6f, 1.9f, 1.9f, 1.7f, white)
        b.clip(Anims.idle(4, 0.5f, "walk"))
        return b.build()
    }

    fun chest(open: Boolean = false): Model {
        val gold = Mat.of(0xFFF2B33D.toInt(), shine = 1f, rim = 1.4f)
        val band = Mat.of(0xFF8C98AC.toInt(), shine = 0.9f)
        val wd = Mat.of(0xFF9B6234.toInt(), pattern = Pat.PLANKS, patScale = 2.4f, dither = 0.12f)
        val inner = Mat.glow(0xFFFFE066.toInt())
        val b = ModelBuilder(if (open) "chest_open" else "chest")
        b.scale = 1.25f; b.yaw = -0.35f; b.footDrop = 6f
        b.box(0f, 0f, 3.8f, 7.4f, 5.4f, 3.8f, wd)
        b.box(0f, 0f, 3.8f, 7.6f, 1.0f, 3.9f, band)
        b.box(5.2f, 0f, 3.8f, 0.9f, 5.6f, 3.9f, band)
        b.box(-5.2f, 0f, 3.8f, 0.9f, 5.6f, 3.9f, band)
        if (!open) {
            b.cyl(0f, 0f, 7.6f, 3.6f, 5.5f, 7.4f, wd, ay = 1.5708f)
            b.cyl(0f, 0f, 7.6f, 3.7f, 5.6f, 1.0f, band, ay = 1.5708f)
            b.box(0f, -5.6f, 6.2f, 1.6f, 0.8f, 1.7f, gold)
        } else {
            b.ell(0f, 0f, 7.8f, 6.6f, 4.4f, 1.4f, inner)
            b.box(0f, 6.2f, 11.2f, 7.4f, 0.9f, 4.2f, wd, ax = -0.45f)
        }
        b.clip(Anims.idle(4, 0.3f, "walk"))
        return b.build()
    }

    // ------------------------------------------------------------ снаряды

    fun knife(): Model {
        val grip = Mat.of(0xFF6B3F25.toInt())
        val b = ModelBuilder("knife")
        b.scale = 1f; b.yaw = 0f; b.footDrop = 6f
        b.box(1.8f, 0f, 8f, 5.0f, 0.55f, 1.5f, steel)
        b.ell(7.1f, 0f, 8f, 1.5f, 0.55f, 1.5f, steel)
        b.box(-3.4f, 0f, 8f, 1.3f, 0.7f, 0.9f, gold)
        b.box(-5.8f, 0f, 8f, 1.4f, 0.8f, 1.0f, grip)
        return b.build()
    }

    fun frostBolt(): Model {
        val ice = Mat.of(0xFF8CE9FF.toInt(), shine = 1f, rim = 1.8f, dither = 0.1f)
        val core = Mat.glow(0xFFE6FBFF.toInt())
        val b = ModelBuilder("frost")
        b.scale = 1f; b.yaw = 0f; b.footDrop = 6f
        b.ell(1.0f, 0f, 8f, 5.4f, 1.8f, 1.8f, ice)
        b.ell(6.2f, 0f, 8f, 2.2f, 1.1f, 1.1f, ice)
        b.ell(0.6f, 0f, 8f, 3.0f, 1.0f, 1.0f, core)
        b.ell(-3.4f, 1.3f, 8f, 1.6f, 0.8f, 0.8f, ice, az = 0.5f)
        b.ell(-3.4f, -1.3f, 8f, 1.6f, 0.8f, 0.8f, ice, az = -0.5f)
        return b.build()
    }

    /** Сковорода: плоский диск с ручкой, наклонён, вращается. */
    fun pan(): Model {
        val b = ModelBuilder("pan")
        b.scale = 1.1f; b.yaw = 0f; b.footDrop = 6f
        b.cyl(0f, 0f, 7f, 7.0f, 7.0f, 0.9f, darkSteel, ax = 0.35f, px = 0f, py = 0f, pz = 7f)
        b.cyl(0f, 0f, 7.5f, 5.6f, 5.6f, 0.6f, steel, ax = 0.35f, px = 0f, py = 0f, pz = 7f)
        b.box(10.4f, 0f, 7.2f, 4.6f, 1.0f, 0.8f, woodDark, ax = 0.35f, px = 0f, py = 0f, pz = 7f)
        b.clip(spin(12, true))
        return b.build()
    }

    fun barrel(): Model {
        val band = Mat.of(0xFF6B7488.toInt(), shine = 0.8f)
        val fuse = Mat.glow(0xFFFFC94D.toInt())
        val b = ModelBuilder("barrel")
        b.scale = 1.1f; b.yaw = -0.2f; b.footDrop = 6f
        b.cyl(0f, 0f, 7f, 5.6f, 5.6f, 6.2f, wood, ay = 0f, px = 0f, py = 0f, pz = 7f)
        b.ell(0f, 0f, 7f, 6.2f, 6.2f, 4.6f, wood, px = 0f, py = 0f, pz = 7f)
        b.cyl(0f, 0f, 3.6f, 6.1f, 6.1f, 0.8f, band, px = 0f, py = 0f, pz = 7f)
        b.cyl(0f, 0f, 10.4f, 6.1f, 6.1f, 0.8f, band, px = 0f, py = 0f, pz = 7f)
        b.cyl(0f, 0f, 13.2f, 4.2f, 4.2f, 0.6f, woodDark, px = 0f, py = 0f, pz = 7f)
        b.ell(0.8f, 0f, 14.6f, 1.1f, 1.1f, 1.4f, fuse, px = 0f, py = 0f, pz = 7f)
        b.clip(spin(12, false))
        return b.build()
    }

    fun mug(): Model {
        val amber = Mat.of(0xFFF2B640.toInt(), shine = 0.7f, rim = 1.3f)
        val foam = Mat.of(0xFFFFF6DC.toInt(), pattern = Pat.NOISE, patScale = 1.2f)
        val b = ModelBuilder("mug")
        b.scale = 1.1f; b.yaw = -0.4f; b.footDrop = 6f
        b.cyl(0f, 0f, 5.2f, 3.6f, 3.6f, 3.6f, amber, px = 0f, py = 0f, pz = 5f)
        b.cyl(0f, 0f, 5.2f, 3.7f, 3.7f, 0.5f, woodDark, px = 0f, py = 0f, pz = 5f)
        b.ell(0f, 0f, 9.0f, 3.8f, 3.8f, 1.5f, foam, px = 0f, py = 0f, pz = 5f)
        b.ell(1.4f, 0.6f, 10.2f, 1.6f, 1.6f, 1.2f, foam, px = 0f, py = 0f, pz = 5f)
        b.box(0f, 4.8f, 5.4f, 0.8f, 1.7f, 2.4f, amber, px = 0f, py = 0f, pz = 5f)
        b.clip(spin(8, true))
        return b.build()
    }

    fun trap(closed: Boolean): Model {
        val rust = Mat.of(0xFF9AA4B8.toInt(), shine = 0.9f, rim = 1.4f)
        val spike = Mat.of(0xFFDCE4F2.toInt(), shine = 1f)
        val b = ModelBuilder(if (closed) "trap_closed" else "trap_open")
        b.scale = 1.15f; b.yaw = 0f; b.footDrop = 3f
        b.cyl(0f, 0f, 1.2f, 7.4f, 5.2f, 1.0f, darkSteel)
        val lift = if (closed) 0.05f else 0.95f
        for (side in 0..1) {
            val sg = if (side == 0) 1f else -1f
            b.ell(0f, sg * 2.6f * (if (closed) 0.5f else 1f), 2.2f + (if (closed) 1.6f else 0f), 6.6f, 2.4f, 0.8f, rust, ax = sg * lift, px = 0f, py = 0f, pz = 1.6f)
            for (k in -2..2) b.box(k * 2.7f, sg * (if (closed) 0.6f else 3.7f), 3.5f + (if (closed) 1.8f else 1.4f), 0.55f, 0.55f, 1.3f, spike, ax = sg * lift * 0.6f, px = 0f, py = 0f, pz = 1.6f)
        }
        return b.build()
    }

    fun raven(): Model {
        val feather = Mat.of(0xFF4A3F66.toInt(), shine = 0.5f, rim = 1.5f, pattern = Pat.NOISE, patScale = 1.4f)
        val beak = Mat.of(0xFFE9B04A.toInt())
        val eye = Mat.glow(0xFFFFE066.toInt())
        val b = ModelBuilder("raven")
        b.scale = 1.1f; b.yaw = -0.6f; b.footDrop = 2f
        b.ell(0f, 0f, 8f, 5.0f, 3.0f, 2.8f, feather, Tag.BODY)
        b.ell(5.0f, 0f, 9.4f, 2.6f, 2.2f, 2.2f, feather, Tag.HEAD)
        b.ell(8.0f, 0f, 9.0f, 1.8f, 0.9f, 0.7f, beak, Tag.HEAD)
        b.ell(6.4f, 1.5f, 10.0f, 0.5f, 0.4f, 0.4f, eye, Tag.HEAD)
        b.ell(-6.4f, 0f, 7.8f, 3.6f, 1.8f, 0.8f, feather, Tag.TAIL)
        for (side in 0..1) {
            val sg = if (side == 0) 1f else -1f
            val tag = if (side == 0) Tag.WING_A else Tag.WING_B
            b.ell(-0.4f, sg * 5.0f, 8.6f, 3.4f, 4.4f, 0.7f, feather, tag, px = 0f, py = sg * 1.2f, pz = 8.6f)
            b.ell(-1.4f, sg * 9.6f, 9.0f, 2.6f, 3.6f, 0.6f, feather, tag, px = 0f, py = sg * 1.2f, pz = 8.6f, az = -sg * 0.25f)
        }
        b.clip(Anims.flap(4, 0.85f, 1.0f))
        return b.build()
    }

    // ------------------------------------------------------------ реквизит арены

    fun treeTrunk(): Model {
        val bark = Mat.of(0xFF6B4528.toInt(), pattern = Pat.NOISE, patScale = 1.8f, dither = 0.2f)
        val b = ModelBuilder("tree_trunk")
        b.scale = 1.5f; b.yaw = 0f; b.footDrop = 0f
        b.cyl(0f, 0f, 9f, 3.4f, 3.4f, 9f, bark)
        b.ell(0f, 0f, 1.6f, 6.2f, 6.0f, 2.2f, bark)
        b.ell(-3.2f, 3.8f, 1.2f, 3.0f, 1.8f, 1.4f, bark)
        return b.build()
    }

    fun treeCanopy(): Model {
        val g1 = Mat.of(0xFF3F8A47.toInt(), pattern = Pat.NOISE, patScale = 2.2f, dither = 0.3f)
        val g2 = Mat.of(0xFF4C9B4F.toInt(), pattern = Pat.NOISE, patScale = 2.2f, dither = 0.3f)
        val b = ModelBuilder("tree_canopy")
        b.scale = 1.5f; b.yaw = 0f; b.footDrop = 0f
        b.ell(0f, 0f, 20f, 12.0f, 12.0f, 8.0f, g1)
        b.ell(-6f, 3f, 17f, 8.0f, 8.0f, 6.0f, g2)
        b.ell(6.5f, -2f, 18f, 8.0f, 8.0f, 6.5f, g1)
        b.ell(1f, 5f, 25f, 8.5f, 8.0f, 6.0f, g2)
        b.ell(-2f, -5f, 23f, 7.0f, 6.0f, 5.0f, g1)
        return b.build()
    }

    fun barrelProp(): Model {
        val band = Mat.of(0xFF6B7488.toInt(), shine = 0.8f)
        val b = ModelBuilder("barrel_prop")
        b.scale = 1.3f; b.yaw = -0.3f; b.footDrop = 0f
        b.cyl(0f, 0f, 6.5f, 5.2f, 5.2f, 6.0f, wood)
        b.ell(0f, 0f, 6.5f, 5.9f, 5.9f, 4.4f, wood)
        b.cyl(0f, 0f, 3.2f, 5.7f, 5.7f, 0.7f, band)
        b.cyl(0f, 0f, 9.8f, 5.7f, 5.7f, 0.7f, band)
        b.ell(0f, 0f, 12.6f, 4.2f, 4.2f, 0.7f, woodDark)
        return b.build()
    }

    fun crate(): Model {
        val b = ModelBuilder("crate")
        b.scale = 1.3f; b.yaw = -0.5f; b.footDrop = 0f
        b.box(0f, 0f, 5.2f, 6.2f, 6.2f, 5.2f, wood)
        b.box(0f, 0f, 5.2f, 6.4f, 6.4f, 0.9f, woodDark)
        b.box(0f, 0f, 5.2f, 0.9f, 6.4f, 5.4f, woodDark)
        b.box(0f, 0f, 5.2f, 6.4f, 0.9f, 5.4f, woodDark)
        return b.build()
    }

    fun haystack(): Model {
        val hay = Mat.of(0xFFD9B04A.toInt(), pattern = Pat.NOISE, patScale = 1.5f, dither = 0.3f)
        val b = ModelBuilder("haystack")
        b.scale = 1.4f; b.yaw = 0f; b.footDrop = 0f
        b.ell(0f, 0f, 6f, 9.0f, 7.5f, 6.0f, hay)
        b.ell(0f, 0f, 11.5f, 6.4f, 5.4f, 4.4f, hay)
        return b.build()
    }

    fun lanternPost(): Model {
        val glow = Mat.glow(0xFFFFC26A.toInt())
        val iron = Mat.of(0xFF3E3A4A.toInt(), shine = 0.5f)
        val b = ModelBuilder("lantern")
        b.scale = 1.3f; b.yaw = 0f; b.footDrop = 0f
        b.cyl(0f, 0f, 9f, 1.2f, 1.2f, 9f, wood)
        b.ell(0f, 0f, 0.8f, 3.2f, 3.2f, 1.0f, woodDark)
        b.box(0f, 0f, 19.2f, 3.0f, 3.0f, 0.8f, iron)
        b.ell(0f, 0f, 21.8f, 2.4f, 2.4f, 2.8f, glow)
        b.box(0f, 0f, 24.8f, 3.0f, 3.0f, 0.7f, iron)
        return b.build()
    }

    fun rock(): Model {
        val stone = Mat.of(0xFF8A8FA0.toInt(), pattern = Pat.NOISE, patScale = 1.6f, dither = 0.25f)
        val stone2 = Mat.of(0xFF767C90.toInt(), pattern = Pat.NOISE, patScale = 1.6f, dither = 0.25f)
        val b = ModelBuilder("rock")
        b.scale = 1.3f; b.yaw = 0f; b.footDrop = 0f
        b.ell(0f, 0f, 3.6f, 7.2f, 5.6f, 3.8f, stone)
        b.ell(-3.8f, 2.4f, 2.8f, 4.0f, 3.2f, 2.8f, stone2)
        b.ell(3.6f, -2.0f, 2.2f, 3.2f, 2.6f, 2.2f, stone2)
        return b.build()
    }

    fun mushroom(): Model {
        val cap = Mat.of(0xFFD9443A.toInt(), pattern = Pat.DOTS, patScale = 2.4f, dither = 0.15f)
        val stem = Mat.of(0xFFF1E8D2.toInt())
        val b = ModelBuilder("mushroom")
        b.scale = 1.2f; b.yaw = 0f; b.footDrop = 0f
        b.cyl(0f, 0f, 3f, 1.8f, 1.8f, 3f, stem)
        b.ell(0f, 0f, 6.6f, 4.6f, 4.6f, 2.6f, cap)
        return b.build()
    }

    fun bush(): Model {
        val g1 = Mat.of(0xFF3F8A47.toInt(), pattern = Pat.NOISE, patScale = 1.8f, dither = 0.3f)
        val g2 = Mat.of(0xFF50A055.toInt(), pattern = Pat.NOISE, patScale = 1.8f, dither = 0.3f)
        val b = ModelBuilder("bush")
        b.scale = 1.2f; b.yaw = 0f; b.footDrop = 0f
        b.ell(0f, 0f, 4f, 6.4f, 5.6f, 4.0f, g1)
        b.ell(-3.4f, 2f, 3.6f, 4.0f, 3.6f, 3.2f, g2)
        b.ell(3.8f, -1.6f, 3.2f, 3.8f, 3.4f, 3.0f, g2)
        return b.build()
    }

    fun fence(): Model {
        val b = ModelBuilder("fence")
        b.scale = 1.3f; b.yaw = 0f; b.footDrop = 0f
        b.box(-8f, 0f, 4.5f, 1.0f, 1.0f, 4.5f, woodDark)
        b.box(8f, 0f, 4.5f, 1.0f, 1.0f, 4.5f, woodDark)
        b.box(0f, 0f, 6.8f, 9.0f, 0.6f, 0.9f, wood)
        b.box(0f, 0f, 3.4f, 9.0f, 0.6f, 0.9f, wood)
        return b.build()
    }

    fun sinSwing(frames: Int, amp: Float): Clip = Clip("sway", frames) { _, t, _, o -> o.rz = amp * sin(tau(t)) }
}
