package com.tavernrush.game.render.pixel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Модель = набор простых тел (эллипсоиды, ящики, цилиндры) в «пиксельных» единицах.
 * Система координат модели: X — вперёд (лицом вправо), Y — влево от персонажа, Z — вверх.
 * ModelBaker запекает модель в спрайт: ортографическая камера 35° над горизонтом, ключевой
 * свет сверху-слева, рампы с цветовым сдвигом, дизеринг, контур и ambient occlusion.
 * Это и есть «2.5D»: настоящий объём и тени, но на выходе — чистые пиксели.
 */

/** Теги движения: какой клип как двигает деталь. */
object Tag {
    const val NONE = 0
    const val LEG_A = 1
    const val LEG_B = 2
    const val ARM_A = 3
    const val ARM_B = 4
    const val HEAD = 5
    const val BODY = 6
    const val TAIL = 7
    const val WING_A = 8
    const val WING_B = 9
    const val EAR_A = 10
    const val EAR_B = 11
    const val WEAPON = 12
    const val JAW = 13
    const val EXTRA = 14
    const val LEG_C = 15
    const val LEG_D = 16
    const val CAPE = 17
}

object Shape {
    const val ELL = 0
    const val BOX = 1
    const val CYL = 2
}

/** Рисунок поверхности (меняет ступень рампы). */
object Pat {
    const val NONE = 0
    const val STRIPES = 1
    const val CHECK = 2
    const val NOISE = 3
    const val DOTS = 4
    const val PLANKS = 5
}

class Mat(
    val ramp: IntArray,
    val shine: Float,
    val emissive: Boolean,
    val rim: Float,
    val pattern: Int,
    val patScale: Float,
    val outline: Int,
    val dither: Float,
) {
    /** Цвет контура вокруг этого материала: глубокая тень с фиолетовым уклоном. */
    val edge: Int = if (outline != 0) outline else PxColor.mix(ramp[0], PxColor.OUTLINE, 0.62f)

    companion object {
        fun of(
            base: Int, shine: Float = 0f, rim: Float = 1f, pattern: Int = Pat.NONE, patScale: Float = 3f,
            dither: Float = 0.2f, outline: Int = 0, contrast: Float = 1f,
        ): Mat = Mat(PxColor.ramp(base, contrast), shine, false, rim, pattern, patScale, outline, dither)

        /** Самосветящийся материал (глаза, огонь, самоцветы): плоский яркий цвет. */
        fun glow(base: Int, outline: Int = 0): Mat {
            val r = PxColor.ramp(base)
            return Mat(intArrayOf(r[2], r[2], r[3], r[3], r[4]), 0f, true, 0f, Pat.NONE, 3f, outline, 0f)
        }
    }
}

class Part(
    val shape: Int,
    val cx: Float, val cy: Float, val cz: Float,
    val rx: Float, val ry: Float, val rz: Float,
    val mat: Mat,
    val tag: Int,
    val ax: Float, val ay: Float, val az: Float,
    val px: Float, val py: Float, val pz: Float,
)

/** Смещение детали в кадре анимации относительно позы покоя. */
class Pose {
    var rx = 0f; var ry = 0f; var rz = 0f
    var dx = 0f; var dy = 0f; var dz = 0f
    var sx = 1f; var sy = 1f; var sz = 1f

    fun reset() {
        rx = 0f; ry = 0f; rz = 0f; dx = 0f; dy = 0f; dz = 0f; sx = 1f; sy = 1f; sz = 1f
    }
}

/** Клип: frames кадров; fn задаёт позу детали (по тегу) в кадре. t = кадр/число кадров. */
class Clip(val name: String, val frames: Int, val fn: (frame: Int, t: Float, part: Part, pose: Pose) -> Unit)

class Model(
    val id: String,
    val parts: Array<Part>,
    val clips: Map<String, Clip>,
    /** Масштаб запекания по умолчанию. */
    val scale: Float,
    /** Разворот к камере (рад): лицо слегка повёрнуто к зрителю — виден и бок, и лицо. */
    val yaw: Float,
    /** Сколько пикселей опустить спрайт под центр хитбокса (положение ног относительно тела). */
    val footDrop: Float,
) {
    fun clip(name: String): Clip? = clips[name]
}

class ModelBuilder(private val id: String) {
    private val parts = ArrayList<Part>()
    private val clips = LinkedHashMap<String, Clip>()
    var scale = 1f
    var yaw = DEFAULT_YAW
    var footDrop = 10f

    fun ell(
        cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, mat: Mat, tag: Int = Tag.NONE,
        ax: Float = 0f, ay: Float = 0f, az: Float = 0f, pz: Float = cz, px: Float = cx, py: Float = cy,
    ): ModelBuilder = add(Shape.ELL, cx, cy, cz, rx, ry, rz, mat, tag, ax, ay, az, px, py, pz)

    fun box(
        cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, mat: Mat, tag: Int = Tag.NONE,
        ax: Float = 0f, ay: Float = 0f, az: Float = 0f, pz: Float = cz, px: Float = cx, py: Float = cy,
    ): ModelBuilder = add(Shape.BOX, cx, cy, cz, rx, ry, rz, mat, tag, ax, ay, az, px, py, pz)

    /** Цилиндр: ось вдоль локальной Z; rx, ry — радиусы основания, rz — половина высоты. */
    fun cyl(
        cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, mat: Mat, tag: Int = Tag.NONE,
        ax: Float = 0f, ay: Float = 0f, az: Float = 0f, pz: Float = cz, px: Float = cx, py: Float = cy,
    ): ModelBuilder = add(Shape.CYL, cx, cy, cz, rx, ry, rz, mat, tag, ax, ay, az, px, py, pz)

    /**
     * Симметричная пара по оси Y (конечности): верхняя (+Y, дальняя от камеры) получает tagA,
     * нижняя (−Y, ближняя) — tagB. Сдвиг по Y задаёт расстояние от оси тела.
     */
    fun pair(
        shape: Int, cx: Float, y: Float, cz: Float, rx: Float, ry: Float, rz: Float, mat: Mat,
        tagA: Int, tagB: Int, pz: Float = cz, ax: Float = 0f, ay: Float = 0f, az: Float = 0f,
    ): ModelBuilder {
        add(shape, cx, y, cz, rx, ry, rz, mat, tagA, ax, ay, az, cx, y, pz)
        // зеркало по Y: вращение вокруг X и Z меняет знак, вокруг Y — нет
        add(shape, cx, -y, cz, rx, ry, rz, mat, tagB, -ax, ay, -az, cx, -y, pz)
        return this
    }

    private fun add(
        shape: Int, cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, mat: Mat, tag: Int,
        ax: Float, ay: Float, az: Float, px: Float, py: Float, pz: Float,
    ): ModelBuilder {
        parts.add(Part(shape, cx, cy, cz, rx, ry, rz, mat, tag, ax, ay, az, px, py, pz))
        return this
    }

    fun clip(c: Clip): ModelBuilder { clips[c.name] = c; return this }

    fun build(): Model = Model(id, parts.toTypedArray(), clips, scale, yaw, footDrop)

    companion object {
        /** −24°: лицо повёрнуто к камере (зритель видит лицо и правый бок). */
        const val DEFAULT_YAW = -0.42f
    }
}

/** Готовые клипы движения для типовых «скелетов». */
object Anims {
    private fun tau(t: Float) = (2.0 * PI).toFloat() * t

    /** Двуногая ходьба: ноги и руки в противофазе, корпус подпрыгивает и чуть наклоняется. */
    fun walk(frames: Int = 6, swing: Float = 0.8f, arms: Float = 0.65f, bob: Float = 1.3f, lean: Float = 0.07f, name: String = "walk"): Clip =
        Clip(name, frames) { _, t, p, o ->
            val s = sin(tau(t))
            when (p.tag) {
                Tag.LEG_A -> { o.ry = -swing * s; o.dz = maxOf(0f, -s) * bob * 0.6f }
                Tag.LEG_B -> { o.ry = swing * s; o.dz = maxOf(0f, s) * bob * 0.6f }
                Tag.ARM_A -> { o.ry = arms * s; o.dz = -abs(sin(tau(t))) * bob * 0.5f + bob * 0.5f }
                Tag.ARM_B -> { o.ry = -arms * s; o.dz = -abs(sin(tau(t))) * bob * 0.5f + bob * 0.5f }
                Tag.WEAPON -> { o.ry = arms * 0.6f * s; o.dz = bob * 0.5f - abs(sin(tau(t))) * bob * 0.5f }
                Tag.BODY, Tag.HEAD, Tag.EXTRA -> { o.dz = abs(sin(tau(t))) * bob; o.ry = lean }
                Tag.TAIL, Tag.CAPE -> { o.dz = abs(sin(tau(t))) * bob; o.rz = 0.25f * sin(tau(t) + 1f); o.ry = -0.12f * s }
                Tag.EAR_A, Tag.EAR_B -> { o.dz = abs(sin(tau(t))) * bob; o.ry = 0.2f * sin(tau(t) + 2f) }
                else -> {}
            }
        }

    /** Дыхание на месте. */
    fun idle(frames: Int = 4, breathe: Float = 0.55f, name: String = "idle"): Clip =
        Clip(name, frames) { _, t, p, o ->
            val s = sin(tau(t))
            when (p.tag) {
                Tag.BODY, Tag.HEAD, Tag.EXTRA -> o.dz = breathe * (0.5f + 0.5f * s)
                Tag.ARM_A, Tag.ARM_B -> { o.dz = breathe * (0.5f + 0.5f * s); o.ry = 0.05f * s }
                Tag.WEAPON -> o.dz = breathe * (0.5f + 0.5f * s)
                Tag.TAIL, Tag.CAPE -> o.rz = 0.12f * sin(tau(t) + 1f)
                Tag.EAR_A, Tag.EAR_B -> o.ry = 0.1f * sin(tau(t) + 2f)
                else -> {}
            }
        }

    /** Четвероногие (крыса, кабан): пары ног в диагональной фазе, спина изгибается. */
    fun quad(frames: Int = 6, swing: Float = 0.9f, bob: Float = 0.9f, name: String = "walk"): Clip =
        Clip(name, frames) { _, t, p, o ->
            val s = sin(tau(t))
            when (p.tag) {
                Tag.LEG_A, Tag.LEG_D -> { o.ry = -swing * s; o.dz = maxOf(0f, -s) * bob * 0.7f }
                Tag.LEG_B, Tag.LEG_C -> { o.ry = swing * s; o.dz = maxOf(0f, s) * bob * 0.7f }
                Tag.BODY, Tag.HEAD, Tag.EXTRA, Tag.ARM_A, Tag.ARM_B -> { o.dz = abs(sin(tau(t))) * bob; o.ry = 0.05f * s }
                Tag.TAIL -> { o.rz = 0.45f * sin(tau(t) + 1.3f); o.dz = abs(sin(tau(t))) * bob }
                Tag.EAR_A, Tag.EAR_B -> { o.dz = abs(sin(tau(t))) * bob; o.ry = 0.25f * sin(tau(t) + 1f) }
                else -> {}
            }
        }

    /** Слизень: сжатие-растяжение с прыжком. */
    fun hop(frames: Int = 6, squash: Float = 0.2f, jump: Float = 3f, name: String = "walk"): Clip =
        Clip(name, frames) { _, t, p, o ->
            val s = sin(tau(t))
            val up = maxOf(0f, s)
            val k = 1f + squash * s
            when (p.tag) {
                Tag.EXTRA -> { o.dz = up * jump; o.sz = 1f / k; o.sx = k; o.sy = k }
                else -> { o.dz = up * jump; o.sz = 1f / k; o.sx = k; o.sy = k }
            }
        }

    /** Летающие: взмахи крыльев и покачивание в воздухе. */
    fun flap(frames: Int = 6, amp: Float = 0.9f, bob: Float = 1.6f, name: String = "walk"): Clip =
        Clip(name, frames) { _, t, p, o ->
            val s = sin(tau(t))
            when (p.tag) {
                Tag.WING_A -> { o.rx = amp * s; o.dz = s * bob * 0.4f }
                Tag.WING_B -> { o.rx = -amp * s; o.dz = s * bob * 0.4f }
                Tag.EAR_A, Tag.EAR_B -> { o.dz = -s * bob * 0.5f }
                else -> o.dz = -s * bob * 0.5f
            }
        }

    /** Парение призраков: плавное покачивание и дрожание подола. */
    fun hover(frames: Int = 6, bob: Float = 1.8f, name: String = "walk"): Clip =
        Clip(name, frames) { _, t, p, o ->
            val s = sin(tau(t))
            when (p.tag) {
                Tag.TAIL, Tag.CAPE -> { o.dz = s * bob; o.rz = 0.25f * sin(tau(t) + 1f) }
                Tag.ARM_A, Tag.ARM_B -> { o.dz = s * bob; o.ry = 0.25f * s }
                else -> o.dz = s * bob
            }
        }

    /** Замах и удар «руками» (боссы): кадры 0–2 — подъём, 3–4 — удар, 5 — возврат. */
    fun slam(name: String = "attack"): Clip {
        val arm = floatArrayOf(0f, -1.2f, -2.7f, 0.25f, 0.7f, 0.3f)
        val lean = floatArrayOf(0f, -0.06f, -0.14f, 0.12f, 0.16f, 0.05f)
        val rise = floatArrayOf(0f, 1f, 2.4f, -0.6f, -1.4f, 0f)
        return Clip(name, 6) { f, _, p, o ->
            when (p.tag) {
                Tag.ARM_A, Tag.ARM_B, Tag.WEAPON -> { o.ry = arm[f]; o.dz = rise[f] }
                Tag.BODY, Tag.HEAD, Tag.EXTRA, Tag.JAW -> { o.ry = lean[f]; o.dz = rise[f] * 0.5f }
                Tag.CAPE, Tag.TAIL -> { o.rz = 0.2f; o.dz = rise[f] * 0.5f }
                else -> {}
            }
        }
    }

    /** Король Крыс встаёт на дыбы: передние лапы вверх, корпус наклонён назад. */
    fun rear(name: String = "attack"): Clip {
        val lift = floatArrayOf(0f, -0.7f, -1.7f, -0.2f, 0.4f, 0.1f)
        val tilt = floatArrayOf(0f, -0.08f, -0.22f, 0.14f, 0.1f, 0f)
        val up = floatArrayOf(0f, 1.2f, 3.4f, 0f, -1f, 0f)
        return Clip(name, 6) { f, _, p, o ->
            when (p.tag) {
                Tag.LEG_A, Tag.LEG_B -> { o.ry = lift[f]; o.dz = up[f] * 0.6f }
                Tag.BODY, Tag.HEAD, Tag.EAR_A, Tag.EAR_B, Tag.EXTRA, Tag.CAPE -> { o.ry = tilt[f]; o.dz = up[f] }
                Tag.TAIL -> { o.rz = 0.4f; o.dz = up[f] * 0.5f }
                else -> {}
            }
        }
    }
}
