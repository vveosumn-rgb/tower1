package com.tavernrush.game.render.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

/**
 * Постобработка пиксельного кадра, только средствами Canvas (одинаково на устройстве и в тестах):
 *  — карта света: заливка окружающим цветом + аддитивные светлые пятна → умножение на кадр;
 *  — свечение (bloom): яркие пиксели → уменьшение → размытие билинейной фильтрацией → добавление;
 *  — подача на экран целым масштабом без сглаживания, цветокоррекция и хроматическая аберрация.
 */
class Postfx {
    private var lw = 0; private var lh = 0
    private var lightBmp: Bitmap? = null
    private var lightCanvas: Canvas? = null
    private var bloomA: Bitmap? = null
    private var bloomB: Bitmap? = null
    private var bcA: Canvas? = null
    private var bcB: Canvas? = null
    private var gradeBmp: Bitmap? = null
    private var gradeCanvas: Canvas? = null
    private val dstFull = Rect()
    private val dstA = Rect()
    private val dstB = Rect()
    private val dstLight = Rect()
    private val srcTmp = Rect()
    private val glowDst = Rect()

    private val addPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); isFilterBitmap = true }
    private val mulSmooth = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY); isFilterBitmap = true }
    private val mulHard = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY); isFilterBitmap = false }
    private val thresholdPaint = Paint().apply {
        isFilterBitmap = true
        val s = 2.6f; val o = -0.58f * 2.6f * 255f
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(s, 0f, 0f, 0f, o, 0f, s, 0f, 0f, o, 0f, 0f, s, 0f, o, 0f, 0f, 0f, 1f, 0f)))
    }
    private val blurPaint = Paint().apply { isFilterBitmap = true }
    private val bloomAdd = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); isFilterBitmap = true; alpha = 150 }
    private val present = Paint().apply { isFilterBitmap = false }
    private val srcPresent = Paint().apply { isFilterBitmap = false; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val gradeArr = ColorMatrix().apply { setSaturation(1.14f) }.also {
        val c = 1.07f; val t = (-0.5f * c + 0.5f) * 255f
        it.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    }.array.copyOf()
    private val gradeFilter = ColorMatrixColorFilter(ColorMatrix(gradeArr))
    /** Строки матрицы коррекции, оставленные только для выбранных каналов: аберрация не сбрасывает цветокоррекцию. */
    private fun gradedChannels(r: Boolean, g: Boolean, b: Boolean): ColorMatrixColorFilter {
        val a = gradeArr.copyOf()
        if (!r) for (i in 0..4) a[i] = 0f
        if (!g) for (i in 5..9) a[i] = 0f
        if (!b) for (i in 10..14) a[i] = 0f
        return ColorMatrixColorFilter(ColorMatrix(a))
    }
    private val gradedGb = gradedChannels(false, true, true)
    private val gradedR = gradedChannels(true, false, false)
    private val rOnly = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    private val gbOnly = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    private val tint = HashMap<Int, PorterDuffColorFilter>()
    private val lightPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); isFilterBitmap = true }
    private val softGlows = HashMap<Int, Bitmap>()

    fun resize(vw: Int, vh: Int) {
        lw = (vw + 3) / 4; lh = (vh + 3) / 4
        lightBmp?.recycle(); bloomA?.recycle(); bloomB?.recycle(); gradeBmp?.recycle()
        gradeBmp = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888).also { gradeCanvas = Canvas(it) }
        lightBmp = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888).also { lightCanvas = Canvas(it) }
        val aw = (vw + 3) / 4; val ah = (vh + 3) / 4
        bloomA = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888).also { bcA = Canvas(it) }
        val bw = (vw + 7) / 8; val bh = (vh + 7) / 8
        bloomB = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bcB = Canvas(it) }
        dstFull.set(0, 0, vw, vh)
        dstA.set(0, 0, aw, ah); dstB.set(0, 0, bw, bh)
        dstLight.set(0, 0, vw, vh)
    }

    private fun filterFor(color: Int): PorterDuffColorFilter =
        tint.getOrPut(color or (0xFF shl 24)) { PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN) }

    private fun glow(radius: Int): Bitmap = softGlows.getOrPut(radius) { PixelAssets.softGlow(radius) }

    // ------------------------------------------------------------ карта света

    fun beginLight(ambient: Int) {
        lightCanvas?.drawColor(ambient or (0xFF shl 24))
    }

    /** Источник света в координатах пикселей кадра; radiusPx — радиус в пикселях кадра. */
    fun light(xPx: Float, yPx: Float, radiusPx: Float, color: Int, intensity: Float) {
        val lc = lightCanvas ?: return
        val r = radiusPx / 4f
        if (r < 1.5f) return
        val g = glow(32)
        lightPaint.colorFilter = filterFor(color)
        lightPaint.alpha = (intensity * 255f).toInt().coerceIn(0, 255)
        val cx = xPx / 4f; val cy = yPx / 4f
        glowDst.set((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt() + 1, (cy + r).toInt() + 1)
        lc.drawBitmap(g, null, glowDst, lightPaint)
    }

    /** Применить карту света: кадр × свет. smooth — билинейно (HIGH+) либо ступенчато (MEDIUM). */
    fun applyLight(buf: Canvas, smooth: Boolean) {
        val lb = lightBmp ?: return
        buf.drawBitmap(lb, null, dstLight, if (smooth) mulSmooth else mulHard)
    }

    /** Мягкое аддитивное пятно прямо на кадре (тёплый ореол вокруг ярких источников). */
    fun glowOn(buf: Canvas, xPx: Float, yPx: Float, radiusPx: Float, color: Int, alpha: Int) {
        val g = glow(32)
        lightPaint.colorFilter = filterFor(color)
        lightPaint.alpha = alpha.coerceIn(0, 255)
        glowDst.set((xPx - radiusPx).toInt(), (yPx - radiusPx).toInt(), (xPx + radiusPx).toInt() + 1, (yPx + radiusPx).toInt() + 1)
        buf.drawBitmap(g, null, glowDst, lightPaint)
    }

    // ------------------------------------------------------------ свечение

    fun bloom(buf: Bitmap, bufCanvas: Canvas, strength: Int) {
        val a = bloomA ?: return; val b = bloomB ?: return
        val ca = bcA ?: return; val cb = bcB ?: return
        ca.drawBitmap(buf, null, dstA, thresholdPaint)
        cb.drawBitmap(a, null, dstB, blurPaint)
        bloomAdd.alpha = strength
        bufCanvas.drawBitmap(b, null, dstFull, bloomAdd)
    }

    // ------------------------------------------------------------ финал кадра

    private val gradePaint = Paint().apply { isFilterBitmap = false; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC); colorFilter = gradeFilter }
    private val abBase = Paint().apply { isFilterBitmap = false; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC); colorFilter = gbOnly }
    private val abRed = Paint().apply { isFilterBitmap = false; xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); colorFilter = rOnly }

    /**
     * Цветокоррекция и хроматическая аберрация — на маленьком буфере (≈280 тыс. пикселей), а не на
     * экране: одинаково дёшево и на GPU, и в программном Canvas. Возвращает кадр для подачи.
     */
    fun finish(buf: Bitmap, grade: Boolean, aberration: Float): Bitmap {
        val gb = gradeBmp ?: return buf
        val gc = gradeCanvas ?: return buf
        if (aberration > 0.04f) {
            abBase.colorFilter = if (grade) gradedGb else gbOnly
            abRed.colorFilter = if (grade) gradedR else rOnly
            gc.drawBitmap(buf, 0f, 0f, abBase)
            // красный канал смещён на 1–2 пикселя арта: на экране это 3–8 пикселей
            val off = if (aberration > 0.5f) 2 else 1
            gc.drawBitmap(buf, off.toFloat(), 0f, abRed)
            // левая кромка, куда сдвинутый красный канал не дотянулся
            srcTmp.set(0, 0, off, buf.height)
            glowDst.set(0, 0, off, buf.height)
            gc.drawBitmap(buf, srcTmp, glowDst, abRed)
            return gb
        }
        if (!grade) return buf
        gc.drawBitmap(buf, 0f, 0f, gradePaint)
        return gb
    }

    /** Целочисленный масштаб без сглаживания (каждый пиксель арта — квадрат k×k). */
    fun present(c: Canvas, shown: Bitmap, k: Int) {
        dstFull.set(0, 0, shown.width * k, shown.height * k)
        c.drawBitmap(shown, null, dstFull, srcPresent)
        dstFull.set(0, 0, shown.width, shown.height)
    }
}
