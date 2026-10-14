package com.tavernrush.game.render.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.tavernrush.game.core.M

/**
 * Земля арены в пикселях: бесшовная трава 256×256, мелкий декор по хэшу клеток, облачные тени.
 * Всё детерминировано от координат мира — картинка не «плывёт» и не требует хранения карты.
 */
class GroundLayer(private val pal: BiomePalette) {
    private val tile: Bitmap
    private val cloud: Bitmap
    private val tufts: Array<Bitmap>
    private val tiny: Array<PixelAssets.Tiny>
    private val tinyBmp: Array<Bitmap>
    private val paint = Paint()
    private val cloudPaint = Paint()

    init {
        val t = PixelAssets.grassTile(TILE, 7, pal)
        tile = Bitmap.createBitmap(t, TILE, TILE, Bitmap.Config.ARGB_8888)
        val c = PixelAssets.cloudTile(CLOUD, 5)
        cloud = Bitmap.createBitmap(c, CLOUD, CLOUD, Bitmap.Config.ARGB_8888)
        val list = ArrayList<PixelAssets.Tiny>()
        list.add(PixelAssets.tuft(pal, 0)); list.add(PixelAssets.tuft(pal, 1))
        for (a in pal.accents) list.add(PixelAssets.flower(pal, a))
        list.add(PixelAssets.pebble())
        tiny = list.toTypedArray()
        tinyBmp = Array(tiny.size) { Bitmap.createBitmap(tiny[it].px, tiny[it].w, tiny[it].h, Bitmap.Config.ARGB_8888) }
        tufts = emptyArray()
    }

    /** camPx — позиция камеры в пикселях кадра; vw/vh — размер кадра. */
    fun draw(c: Canvas, camPxX: Int, camPxY: Int, vw: Int, vh: Int, detail: Float) {
        val left = camPxX - vw / 2
        val top = camPxY - vh / 2
        val x0 = Math.floorDiv(left, TILE) * TILE
        val y0 = Math.floorDiv(top, TILE) * TILE
        var y = y0
        while (y < top + vh) {
            var x = x0
            while (x < left + vw) {
                c.drawBitmap(tile, (x - left).toFloat(), (y - top).toFloat(), null)
                x += TILE
            }
            y += TILE
        }
        if (detail <= 0f) return
        val cx0 = Math.floorDiv(left - 8, CELL); val cx1 = Math.floorDiv(left + vw + 8, CELL)
        val cy0 = Math.floorDiv(top - 8, CELL); val cy1 = Math.floorDiv(top + vh + 12, CELL)
        for (cy in cy0..cy1) for (cx in cx0..cx1) {
            val h = M.hash(cx, cy, 31)
            val r = (h and 0xFFFF) / 65536f
            if (r > 0.34f * detail) continue
            val kind = (h ushr 16) % 10
            val idx = when {
                kind < 4 -> kind and 1
                kind < 8 -> 2 + ((h ushr 20) and 3)
                else -> tiny.size - 1
            }
            val t = tiny[idx]
            val jx = ((h ushr 8) and 15) + ((h ushr 12) and 7)
            val jy = ((h ushr 11) and 15) + ((h ushr 15) and 7)
            val px = cx * CELL + jx - left - t.ox
            val py = cy * CELL + jy - top - t.oy
            c.drawBitmap(tinyBmp[idx], px.toFloat(), py.toFloat(), null)
        }
    }

    /** Тени облаков: медленно плывут и движутся с параллаксом, чуть быстрее земли (глубина). */
    fun clouds(c: Canvas, camPxX: Int, camPxY: Int, vw: Int, vh: Int, time: Float) {
        val ox = (camPxX * 0.86f + time * 5f).toInt()
        val oy = (camPxY * 0.86f + time * 2f).toInt()
        val left = ox - vw / 2
        val top = oy - vh / 2
        val x0 = Math.floorDiv(left, CLOUD) * CLOUD
        val y0 = Math.floorDiv(top, CLOUD) * CLOUD
        var y = y0
        while (y < top + vh) {
            var x = x0
            while (x < left + vw) {
                c.drawBitmap(cloud, (x - left).toFloat(), (y - top).toFloat(), cloudPaint)
                x += CLOUD
            }
            y += CLOUD
        }
    }

    companion object {
        const val TILE = 256
        const val CLOUD = 192
        const val CELL = 30
    }
}
