package com.tavernrush.game.render.pixel

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Готовое изображение: пиксели ARGB и точка привязки (ox, oy) — где в нём «земля под ногами». */
class Baked(val w: Int, val h: Int, val ox: Int, val oy: Int, val pixels: IntArray)

/** Запечённый клип: кадры спрайта и их проекционные тени (общий размер и привязка у всех кадров). */
class BakedSet(val frames: Array<Baked>, val shadows: Array<Baked>)

class BakeOpts(
    /** Единый цвет контура (0 — по материалу). Элитам ставится золотой. */
    val outline: Int = 0,
    /** Подвыборка на пиксель по стороне (2 — чёткие формы, меньше «дыр»). */
    val ss: Int = 2,
    val shadows: Boolean = true,
    /** Дополнительный поворот модели вокруг Z (рад) — снаряды в 16 направлениях. */
    val spin: Float = 0f,
    /** Усиление контраста света (боссы — резче). */
    val lightBoost: Float = 1f,
)

/**
 * Запекание моделей в спрайты: растеризация лучами (ортографическая камера), z-буфер, свет в ступени
 * рампы с упорядоченным дизерингом, подсветка по краю, блик, окклюзия, контур и тень на земле.
 * Всё в чистом Kotlin; Bitmap из пикселей собирает SpriteBank.
 */
object ModelBaker {
    /** Наклон камеры 30° над горизонтом (классическая «2:1» проекция). */
    const val SIN = 0.5f
    const val COS = 0.8660254f

    // Ключевой свет (на свет): сверху-слева-спереди; контровой — справа-сзади-сверху.
    private const val LX = -0.55f
    private const val LY = -0.12f
    private const val LZ = 0.83f
    private const val RX = 0.74f
    private const val RY = 0.34f
    private const val RZ = 0.58f
    private const val SHADOW_X = 0.5f
    private const val SHADOW_Y = -0.2f
    private val SHADOW_COLOR = PxColor.argb(104, 22, 10, 42)

    private val BAYER = floatArrayOf(
        0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f,
    )

    private class TP(
        val shape: Int, val mat: Mat, val tag: Int,
        val cx: Float, val cy: Float, val cz: Float,
        val rx: Float, val ry: Float, val rz: Float,
        val r: FloatArray,
    ) {
        /** Радиус описанной сферы (для ограничивающего прямоугольника на экране). */
        val rb: Float = when (shape) {
            Shape.ELL -> max(rx, max(ry, rz))
            else -> sqrt(rx * rx + ry * ry + rz * rz)
        }
    }

    // ------------------------------------------------------------ матрицы 3×3 (по строкам)

    private fun euler(ax: Float, ay: Float, az: Float, o: FloatArray) {
        val sx = sin(ax); val cx = cos(ax)
        val sy = sin(ay); val cy = cos(ay)
        val sz = sin(az); val cz = cos(az)
        // R = Rz · Ry · Rx
        o[0] = cz * cy; o[1] = cz * sy * sx - sz * cx; o[2] = cz * sy * cx + sz * sx
        o[3] = sz * cy; o[4] = sz * sy * sx + cz * cx; o[5] = sz * sy * cx - cz * sx
        o[6] = -sy; o[7] = cy * sx; o[8] = cy * cx
    }

    private fun mul(a: FloatArray, b: FloatArray, o: FloatArray) {
        for (i in 0 until 3) for (j in 0 until 3)
            o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }

    private fun transform(model: Model, clip: Clip?, frame: Int, mirror: Boolean, scale: Float, yaw: Float): Array<TP> {
        val pose = Pose()
        val rb = FloatArray(9); val rp = FloatArray(9); val r1 = FloatArray(9); val ryaw = FloatArray(9); val r3 = FloatArray(9)
        euler(0f, 0f, yaw, ryaw)
        val t = if (clip == null) 0f else frame.toFloat() / clip.frames
        return Array(model.parts.size) { i ->
            val p = model.parts[i]
            pose.reset()
            clip?.fn?.invoke(frame, t, p, pose)
            euler(p.ax, p.ay, p.az, rb)
            euler(pose.rx, pose.ry, pose.rz, rp)
            // Масштаб позы (сжатие слизня) действует и на смещение от точки опоры — тело сжимается целиком.
            val vx = (p.cx - p.px) * pose.sx; val vy = (p.cy - p.py) * pose.sy; val vz = (p.cz - p.pz) * pose.sz
            var cx = p.px + rp[0] * vx + rp[1] * vy + rp[2] * vz + pose.dx
            var cy = p.py + rp[3] * vx + rp[4] * vy + rp[5] * vz + pose.dy
            var cz = p.pz + rp[6] * vx + rp[7] * vy + rp[8] * vz + pose.dz
            mul(rp, rb, r1)
            cx *= scale; cy *= scale; cz *= scale
            val wx = ryaw[0] * cx + ryaw[1] * cy
            val wy = ryaw[3] * cx + ryaw[4] * cy
            mul(ryaw, r1, r3)
            var fx = wx
            if (mirror) {
                fx = -fx
                r3[1] = -r3[1]; r3[2] = -r3[2]; r3[3] = -r3[3]; r3[6] = -r3[6]
            }
            TP(p.shape, p.mat, p.tag, fx, wy, cz, p.rx * pose.sx * scale, p.ry * pose.sy * scale, p.rz * pose.sz * scale, r3.copyOf())
        }
    }

    // ------------------------------------------------------------ запекание

    /** Запекает клип (или одну позу покоя, если clipName == null) со всеми кадрами. */
    fun bake(model: Model, clipName: String?, mirror: Boolean, scale: Float = model.scale, opts: BakeOpts = BakeOpts()): BakedSet {
        val clip = if (clipName == null) null else model.clip(clipName)
        val frames = clip?.frames ?: 1
        val yaw = model.yaw + opts.spin
        val tps = Array(frames) { transform(model, clip, it, mirror, scale, yaw) }

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE
        var sMinX = Float.MAX_VALUE; var sMaxX = -Float.MAX_VALUE
        var sMinU = Float.MAX_VALUE; var sMaxU = -Float.MAX_VALUE
        for (f in 0 until frames) for (p in tps[f]) {
            val u = p.cy * SIN + p.cz * COS
            minX = min(minX, p.cx - p.rb); maxX = max(maxX, p.cx + p.rb)
            minU = min(minU, u - p.rb); maxU = max(maxU, u + p.rb)
            for (k in 0 until 8) {
                val x = p.cx + (if (k and 1 != 0) p.rb else -p.rb)
                val y = p.cy + (if (k and 2 != 0) p.rb else -p.rb)
                val z = max(0f, p.cz + (if (k and 4 != 0) p.rb else -p.rb))
                val gx = x + z * SHADOW_X
                val gu = (y + z * SHADOW_Y) * SIN
                sMinX = min(sMinX, gx); sMaxX = max(sMaxX, gx)
                sMinU = min(sMinU, gu); sMaxU = max(sMaxU, gu)
            }
        }
        val margin = 3
        val ox = (-floor(minX)).toInt() + margin
        val top = ceil(maxU).toInt() + margin
        val w = ceil(maxX).toInt() + ox + margin
        val h = top + (-floor(minU)).toInt() + margin
        val oy = top
        val sox = (-floor(sMinX)).toInt() + 2
        val stop = ceil(sMaxU).toInt() + 2
        val sw = ceil(sMaxX).toInt() + sox + 3
        val sh = stop + (-floor(sMinU)).toInt() + 3
        val soy = stop

        val out = arrayOfNulls<Baked>(frames)
        val shadows = arrayOfNulls<Baked>(frames)
        val ss = opts.ss
        val raster = Raster(w, h, ss)
        for (f in 0 until frames) {
            raster.clear()
            for (p in tps[f]) raster.part(p, ox, oy)
            val img = raster.resolve(w, h, ox, oy, opts)
            out[f] = Baked(w, h, ox, oy, img)
            shadows[f] = if (opts.shadows) shadow(raster, w, h, ox, oy, sw, sh, sox, soy) else Baked(1, 1, 0, 0, IntArray(1))
        }
        @Suppress("UNCHECKED_CAST")
        return BakedSet(out as Array<Baked>, shadows as Array<Baked>)
    }

    private fun shadow(r: Raster, w: Int, h: Int, ox: Int, oy: Int, sw: Int, sh: Int, sox: Int, soy: Int): Baked {
        val px = IntArray(sw * sh)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (r.fPart[i] < 0) continue
            val t = r.fDepth[i]
            val sx = x + 0.5f - ox
            val su = oy - (y + 0.5f)
            val wy = su * SIN + t * COS
            val wz = su * COS - t * SIN
            if (wz < -0.5f) continue
            val z = max(0f, wz)
            val gx = sx + z * SHADOW_X
            val gu = (wy + z * SHADOW_Y) * SIN
            val tx = floor(gx + sox).toInt()
            val ty = floor(soy - gu).toInt()
            for (dy in 0..1) for (dx in 0..1) {
                val xx = tx + dx; val yy = ty + dy
                if (xx in 0 until sw && yy in 0 until sh) px[yy * sw + xx] = SHADOW_COLOR
            }
        }
        return Baked(sw, sh, sox, soy, px)
    }

    /** Буферы подвыборок и итогового пикселя одного кадра. */
    private class Raster(val w: Int, val h: Int, val ss: Int) {
        val sw = w * ss
        val sh = h * ss
        val sDepth = FloatArray(sw * sh)
        val sLum = FloatArray(sw * sh)
        val sPart = IntArray(sw * sh)
        val sShift = ByteArray(sw * sh)
        val sSpec = BooleanArray(sw * sh)
        val fDepth = FloatArray(w * h)
        val fLum = FloatArray(w * h)
        val fPart = IntArray(w * h)
        val fShift = ByteArray(w * h)
        val fSpec = BooleanArray(w * h)
        val mats = ArrayList<Mat>()

        // временные для теста лучом
        private val o3 = FloatArray(3)
        private val d3 = FloatArray(3)
        private val ux = FloatArray(3)
        private val uu = FloatArray(3)
        private val cc = FloatArray(3)
        private val nW = FloatArray(3)

        fun clear() {
            java.util.Arrays.fill(sDepth, Float.MAX_VALUE)
            java.util.Arrays.fill(sPart, -1)
            mats.clear()
        }

        private fun shade(p: TP, idx: Int, partIdx: Int, nx: Float, ny: Float, nz: Float, hx: Float, hy: Float, hz: Float) {
            var n0 = nx; var n1 = ny; var n2 = nz
            val l = sqrt(n0 * n0 + n1 * n1 + n2 * n2)
            if (l < 1e-6f) return
            n0 /= l; n1 /= l; n2 /= l
            val m = p.mat
            val diff = max(0f, n0 * LX + n1 * LY + n2 * LZ)
            var lum = 0.36f + 0.10f * n2 + 0.55f * diff
            // подсветка по краю с контровой стороны
            val ndv = n1 * -COS + n2 * SIN
            val edge = (1f - max(0f, ndv))
            val rimL = max(0f, n0 * RX + n1 * RY + n2 * RZ)
            if (m.rim > 0f && edge > 0.6f) lum += m.rim * (edge - 0.6f) * 0.9f * rimL
            var spec = false
            if (m.shine > 0f) {
                val hxv = LX; val hyv = LY - COS; val hzv = LZ + SIN
                val hl = sqrt(hxv * hxv + hyv * hyv + hzv * hzv)
                val s = max(0f, (n0 * hxv + n1 * hyv + n2 * hzv) / hl)
                if (s.pow(18f + 40f * m.shine) > 0.55f) spec = true
            }
            var shift = 0
            if (m.pattern != Pat.NONE) shift = pattern(m, hx, hy, hz)
            sLum[idx] = if (m.emissive) 2f else lum
            sPart[idx] = partIdx
            sShift[idx] = shift.toByte()
            sSpec[idx] = spec
        }

        private fun pattern(m: Mat, x: Float, y: Float, z: Float): Int {
            val ps = m.patScale
            return when (m.pattern) {
                Pat.STRIPES -> if ((floor(z / ps).toInt() and 1) == 0) -1 else 0
                Pat.CHECK -> if (((floor(x / ps).toInt() + floor(y / ps).toInt() + floor(z / ps).toInt()) and 1) == 0) -1 else 0
                Pat.NOISE -> {
                    val h = hash(floor(x / ps * 1.7f).toInt(), floor(y / ps * 1.7f).toInt(), floor(z / ps * 1.7f).toInt())
                    if (h % 5 == 0) -1 else if (h % 7 == 0) 1 else 0
                }
                Pat.DOTS -> {
                    val fx = x / ps - floor(x / ps) - 0.5f
                    val fz = z / ps - floor(z / ps) - 0.5f
                    if (fx * fx + fz * fz < 0.09f) 1 else 0
                }
                Pat.PLANKS -> {
                    val k = floor(x / ps).toInt()
                    if (abs(x / ps - floor(x / ps)) < 0.1f) -1 else if ((k and 1) == 0) 0 else 1
                }
                else -> 0
            }
        }

        private fun hash(a: Int, b: Int, c: Int): Int {
            var h = a * 374761393 + b * 668265263 + c * 1274126177
            h = (h xor (h ushr 13)) * 1274126177
            return (h xor (h ushr 16)) and 0x7fffffff
        }

        fun part(p: TP, ox: Int, oy: Int) {
            val partIdx = mats.size
            mats.add(p.mat)
            val ucy = p.cy * SIN + p.cz * COS
            val x0 = max(0, floor((ox + p.cx - p.rb - 1f) * ss).toInt())
            val x1 = min(sw - 1, ceil((ox + p.cx + p.rb + 1f) * ss).toInt())
            val y0 = max(0, floor((oy - ucy - p.rb - 1f) * ss).toInt())
            val y1 = min(sh - 1, ceil((oy - ucy + p.rb + 1f) * ss).toInt())
            if (x0 > x1 || y0 > y1) return
            val r = p.r
            val inv = 1f / ss
            // направление луча и опорные векторы в локальных осях детали
            for (i in 0 until 3) {
                ux[i] = r[i]
                uu[i] = SIN * r[3 + i] + COS * r[6 + i]
                d3[i] = COS * r[3 + i] - SIN * r[6 + i]
                cc[i] = -(r[i] * p.cx + r[3 + i] * p.cy + r[6 + i] * p.cz)
            }
            val rad0 = p.rx; val rad1 = p.ry; val rad2 = p.rz
            for (y in y0..y1) {
                val su = oy - (y + 0.5f) * inv
                for (x in x0..x1) {
                    val sx = (x + 0.5f) * inv - ox
                    val o0 = cc[0] + ux[0] * sx + uu[0] * su
                    val o1 = cc[1] + ux[1] * sx + uu[1] * su
                    val o2 = cc[2] + ux[2] * sx + uu[2] * su
                    val idx = y * sw + x
                    when (p.shape) {
                        Shape.ELL -> {
                            val a0 = o0 / rad0; val a1 = o1 / rad1; val a2 = o2 / rad2
                            val b0 = d3[0] / rad0; val b1 = d3[1] / rad1; val b2 = d3[2] / rad2
                            val qa = b0 * b0 + b1 * b1 + b2 * b2
                            val qb = a0 * b0 + a1 * b1 + a2 * b2
                            val qc = a0 * a0 + a1 * a1 + a2 * a2 - 1f
                            val disc = qb * qb - qa * qc
                            if (disc < 0f) continue
                            val t = (-qb - sqrt(disc)) / qa
                            if (t >= sDepth[idx]) continue
                            sDepth[idx] = t
                            val p0 = a0 + t * b0; val p1 = a1 + t * b1; val p2 = a2 + t * b2
                            val l0 = p0 / rad0; val l1 = p1 / rad1; val l2 = p2 / rad2
                            val nx = r[0] * l0 + r[1] * l1 + r[2] * l2
                            val ny = r[3] * l0 + r[4] * l1 + r[5] * l2
                            val nz = r[6] * l0 + r[7] * l1 + r[8] * l2
                            shade(p, idx, partIdx, nx, ny, nz, p0 * rad0, p1 * rad1, p2 * rad2)
                        }
                        Shape.BOX -> {
                            var tmin = -1e9f; var tmax = 1e9f
                            var axis = 0; var sign = 1f
                            var miss = false
                            for (i in 0 until 3) {
                                val oo = if (i == 0) o0 else if (i == 1) o1 else o2
                                val dd = d3[i]
                                val hh = if (i == 0) rad0 else if (i == 1) rad1 else rad2
                                if (abs(dd) < 1e-6f) {
                                    if (abs(oo) > hh) { miss = true; break }
                                } else {
                                    var t1 = (-hh - oo) / dd
                                    var t2 = (hh - oo) / dd
                                    var sg = if (dd > 0f) -1f else 1f
                                    if (t1 > t2) { val s = t1; t1 = t2; t2 = s }
                                    if (t1 > tmin) { tmin = t1; axis = i; sign = sg }
                                    if (t2 < tmax) tmax = t2
                                }
                            }
                            if (miss || tmax < tmin) continue
                            if (tmin >= sDepth[idx]) continue
                            sDepth[idx] = tmin
                            val ln0 = if (axis == 0) sign else 0f
                            val ln1 = if (axis == 1) sign else 0f
                            val ln2 = if (axis == 2) sign else 0f
                            val nx = r[0] * ln0 + r[1] * ln1 + r[2] * ln2
                            val ny = r[3] * ln0 + r[4] * ln1 + r[5] * ln2
                            val nz = r[6] * ln0 + r[7] * ln1 + r[8] * ln2
                            shade(p, idx, partIdx, nx, ny, nz, o0 + tmin * d3[0], o1 + tmin * d3[1], o2 + tmin * d3[2])
                        }
                        else -> {
                            // цилиндр: ось — локальная Z
                            val dx = d3[0]; val dy = d3[1]; val dz = d3[2]
                            var best = Float.MAX_VALUE
                            var bn0 = 0f; var bn1 = 0f; var bn2 = 0f
                            val qa = (dx / rad0) * (dx / rad0) + (dy / rad1) * (dy / rad1)
                            if (qa > 1e-9f) {
                                val qb = o0 * dx / (rad0 * rad0) + o1 * dy / (rad1 * rad1)
                                val qc = (o0 / rad0) * (o0 / rad0) + (o1 / rad1) * (o1 / rad1) - 1f
                                val disc = qb * qb - qa * qc
                                if (disc >= 0f) {
                                    val t = (-qb - sqrt(disc)) / qa
                                    val z = o2 + t * dz
                                    if (abs(z) <= rad2) {
                                        best = t
                                        bn0 = (o0 + t * dx) / (rad0 * rad0); bn1 = (o1 + t * dy) / (rad1 * rad1); bn2 = 0f
                                    }
                                }
                            }
                            if (abs(dz) > 1e-6f) {
                                for (cap in 0..1) {
                                    val zc = if (cap == 0) rad2 else -rad2
                                    val t = (zc - o2) / dz
                                    val qx = (o0 + t * dx) / rad0; val qy = (o1 + t * dy) / rad1
                                    if (qx * qx + qy * qy <= 1f && t < best) {
                                        best = t; bn0 = 0f; bn1 = 0f; bn2 = if (cap == 0) 1f else -1f
                                    }
                                }
                            }
                            if (best == Float.MAX_VALUE || best >= sDepth[idx]) continue
                            sDepth[idx] = best
                            val nx = r[0] * bn0 + r[1] * bn1 + r[2] * bn2
                            val ny = r[3] * bn0 + r[4] * bn1 + r[5] * bn2
                            val nz = r[6] * bn0 + r[7] * bn1 + r[8] * bn2
                            shade(p, idx, partIdx, nx, ny, nz, o0 + best * dx, o1 + best * dy, o2 + best * dz)
                        }
                    }
                }
            }
        }

        /** Сводим подвыборки в пиксели, затем свет → ступени рампы, контур и линии. */
        fun resolve(w: Int, h: Int, ox: Int, oy: Int, opts: BakeOpts): IntArray {
            val need = (ss * ss + 1) / 2
            for (y in 0 until h) for (x in 0 until w) {
                var cnt = 0
                var best = Float.MAX_VALUE
                var bi = -1
                for (j in 0 until ss) for (i in 0 until ss) {
                    val k = (y * ss + j) * sw + x * ss + i
                    if (sPart[k] >= 0) {
                        cnt++
                        if (sDepth[k] < best) { best = sDepth[k]; bi = k }
                    }
                }
                val o = y * w + x
                if (cnt >= need && bi >= 0) {
                    fDepth[o] = best; fLum[o] = sLum[bi]; fPart[o] = sPart[bi]; fShift[o] = sShift[bi]; fSpec[o] = sSpec[bi]
                } else {
                    fPart[o] = -1; fDepth[o] = Float.MAX_VALUE
                }
            }
            val px = IntArray(w * h)
            val line = BooleanArray(w * h)
            // линии между «разными телами» на разной глубине
            for (y in 0 until h) for (x in 0 until w) {
                val o = y * w + x
                val a = fPart[o]
                if (a < 0) continue
                if (x + 1 < w) mark(o, o + 1, line)
                if (y + 1 < h) mark(o, o + w, line)
            }
            for (y in 0 until h) for (x in 0 until w) {
                val o = y * w + x
                val pi = fPart[o]
                if (pi < 0) continue
                val m = mats[pi]
                var lum = fLum[o]
                if (!m.emissive) {
                    // окклюзия: соседи заметно ближе к камере → складка темнее
                    var occ = 0
                    for (dy in -2..2 step 2) for (dx in -2..2 step 2) {
                        if (dx == 0 && dy == 0) continue
                        val xx = x + dx; val yy = y + dy
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                        val q = yy * w + xx
                        if (fPart[q] >= 0 && fDepth[q] < fDepth[o] - 3.2f) occ++
                    }
                    lum -= 0.032f * occ
                    val z = (oy - (y + 0.5f)) * COS - fDepth[o] * SIN
                    if (z < 3f) lum -= 0.1f * (3f - max(0f, z)) / 3f
                    lum = (lum - 0.5f) * opts.lightBoost + 0.5f
                }
                val v = ((lum - 0.30f) * 5.4f).coerceIn(0f, 4f)
                val bay = BAYER[(y and 3) * 4 + (x and 3)] / 16f - 0.46f
                var lv = floor(v + 0.5f + bay * m.dither).toInt() + fShift[o]
                if (m.emissive) lv = 3 + (if (lum > 1.5f) 1 else 0)
                if (fSpec[o]) lv = 4
                lv = lv.coerceIn(0, 4)
                var c = m.ramp[lv]
                if (line[o]) c = PxColor.mix(c, m.edge, 0.72f)
                px[o] = c
            }
            // внешний контур (4-связность)
            val res = px.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                val o = y * w + x
                if (fPart[o] >= 0) continue
                var nb = -1
                if (x > 0 && fPart[o - 1] >= 0) nb = fPart[o - 1]
                else if (x + 1 < w && fPart[o + 1] >= 0) nb = fPart[o + 1]
                else if (y > 0 && fPart[o - w] >= 0) nb = fPart[o - w]
                else if (y + 1 < h && fPart[o + w] >= 0) nb = fPart[o + w]
                if (nb >= 0) res[o] = if (opts.outline != 0) opts.outline else mats[nb].edge
            }
            return res
        }

        private fun mark(a: Int, b: Int, line: BooleanArray) {
            val pa = fPart[a]; val pb = fPart[b]
            if (pb < 0 || pa == pb) return
            val dd = fDepth[a] - fDepth[b]
            if (abs(dd) < 3f) return
            // линия — на дальнем пикселе, чтобы передняя форма сохраняла цвет
            if (dd > 0f) line[a] = true else line[b] = true
        }
    }
}
