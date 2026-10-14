package com.tavernrush.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import com.tavernrush.game.content.IconId

/**
 * Процедурные иконки (карты, HUD, таверна). Рисуются в квадрате 100×100 условных единиц,
 * запекаются в Bitmap нужного размера и кэшируются по (иконка, размер).
 */
class Icons {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    // Кэш без боксинга: для каждой иконки до SLOTS размеров (линейный поиск).
    private val sizes = Array(IconId.values().size) { IntArray(SLOTS) }
    private val bitmaps = Array(IconId.values().size) { arrayOfNulls<Bitmap>(SLOTS) }
    private val next = IntArray(IconId.values().size)

    /** Иконка размером sizePx (кэш; создание — только при первом запросе размера). */
    fun get(id: IconId, sizePx: Int): Bitmap {
        val px = if (sizePx < 4) 4 else sizePx
        val o = id.ordinal
        val sz = sizes[o]
        val bm = bitmaps[o]
        for (i in 0 until SLOTS) {
            val b = bm[i]
            if (b != null && sz[i] == px) return b
        }
        val nb = render(id, px)
        val slot = next[o]
        next[o] = (slot + 1) % SLOTS
        // вытесненную картинку не recycle(): на неё может ссылаться кнопка
        bm[slot] = nb
        sz[slot] = px
        return nb
    }

    fun clear() {
        for (o in bitmaps.indices) {
            val bm = bitmaps[o]
            for (i in 0 until SLOTS) bm[i] = null
            next[o] = 0
        }
    }

    private fun render(id: IconId, size: Int): Bitmap {
        val s = if (size < 8) 8 else size
        val raw = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        val k = s / 116f
        c.translate(s / 2f, s / 2f)
        c.scale(k, k)
        draw(c, id)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val sil = Paint(Paint.ANTI_ALIAS_FLAG)
        sil.colorFilter = PorterDuffColorFilter(Palette.OUTLINE, PorterDuff.Mode.SRC_IN)
        val o = maxOf(1.2f, s * 0.035f)
        for (i in 0 until 12) {
            val a = i * Math.PI * 2.0 / 12
            oc.drawBitmap(raw, (Math.cos(a) * o).toFloat(), (Math.sin(a) * o).toFloat(), sil)
        }
        oc.drawBitmap(raw, 0f, 0f, null)
        raw.recycle()
        return out
    }

    private fun fill(color: Int): Paint { p.style = Paint.Style.FILL; p.color = color; p.shader = null; return p }
    private fun line(color: Int, w: Float): Paint { p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; return p }
    private fun circle(c: Canvas, x: Float, y: Float, r: Float, col: Int) = c.drawCircle(x, y, r, fill(col))
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, col: Int) = c.drawOval(x - rx, y - ry, x + rx, y + ry, fill(col))
    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, col: Int) = c.drawRoundRect(l, t, r, b, rad, rad, fill(col))
    private fun poly(c: Canvas, col: Int, vararg xy: Float) {
        path.reset(); path.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
        path.close(); c.drawPath(path, fill(col))
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int) {
        p.style = Paint.Style.FILL; p.color = col; p.textSize = size; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(s, x, y, p)
    }

    private fun draw(c: Canvas, id: IconId) {
        when (id) {
            IconId.KNIFE -> {
                c.rotate(-40f)
                poly(c, 0xFFE6EEF7.toInt(), -8f, -10f, 46f, 0f, -8f, 10f)
                poly(c, 0xFFB0BEC5.toInt(), -8f, 0f, 46f, 0f, -8f, 10f)
                rr(c, -10f, -14f, -4f, 14f, 2f, 0xFFB08D57.toInt())
                rr(c, -42f, -7f, -9f, 7f, 6f, 0xFF6D4C41.toInt())
            }
            IconId.MUG -> mug(c)
            IconId.FLAME -> flame(c)
            IconId.LIGHTNING -> {
                poly(c, 0xFFFFE14D.toInt(), 8f, -48f, -26f, 6f, -2f, 6f, -14f, 48f, 26f, -10f, 2f, -10f)
                poly(c, 0xFFFFF7C2.toInt(), 4f, -36f, -16f, 2f, -4f, 2f)
            }
            IconId.PAN -> {
                c.rotate(-30f)
                c.drawLine(20f, 0f, 50f, 0f, line(0xFF4E342E.toInt(), 12f))
                circle(c, -10f, 0f, 34f, 0xFF4A5560.toInt())
                circle(c, -10f, 0f, 26f, 0xFF2F3740.toInt())
                oval(c, -20f, -10f, 10f, 6f, 0x70FFFFFF)
            }
            IconId.BARREL -> {
                rr(c, -30f, -36f, 30f, 38f, 20f, 0xFF9C6232.toInt())
                c.drawRect(-30f, -22f, 30f, -15f, fill(0xFF5D4037.toInt()))
                c.drawRect(-30f, 16f, 30f, 23f, fill(0xFF5D4037.toInt()))
                circle(c, 0f, 0f, 9f, 0xFF212121.toInt())
                c.drawLine(4f, -36f, 18f, -48f, line(0xFF3E2723.toInt(), 4f))
                circle(c, 20f, -50f, 7f, 0xFFFFC107.toInt())
                circle(c, 20f, -50f, 3.5f, Color.WHITE)
            }
            IconId.ALE -> {
                rr(c, -14f, -14f, 14f, 44f, 10f, 0xFF8D5524.toInt())
                rr(c, -7f, -46f, 7f, -10f, 4f, 0xFF8D5524.toInt())
                rr(c, -9f, -50f, 9f, -42f, 2f, 0xFFE0C068.toInt())
                rr(c, -14f, 4f, 14f, 26f, 2f, 0xFFF3E3C3.toInt())
                text(c, "XXX", 0f, 21f, 16f, 0xFF8D2B1F.toInt())
                oval(c, -7f, -2f, 3f, 10f, 0x60FFFFFF)
            }
            IconId.FINGERS -> {
                rr(c, -26f, -2f, 22f, 40f, 16f, Palette.SKIN)
                rr(c, -26f, -36f, -14f, 10f, 6f, Palette.SKIN)
                rr(c, -12f, -44f, 0f, 6f, 6f, Palette.SKIN)
                rr(c, 2f, -40f, 14f, 6f, 6f, Palette.SKIN)
                rr(c, 16f, -10f, 40f, 4f, 7f, Palette.SKIN)
                c.drawLine(30f, -40f, 46f, -46f, line(0xFF7FD3FF.toInt(), 5f))
                c.drawLine(32f, -26f, 50f, -28f, p)
            }
            IconId.BOOT -> {
                poly(c, 0xFF8D5A35.toInt(), -24f, -44f, 8f, -44f, 8f, 14f, 40f, 22f, 40f, 40f, -26f, 40f)
                rr(c, -28f, 34f, 42f, 44f, 4f, 0xFF4E342E.toInt())
                rr(c, -27f, -48f, 11f, -36f, 3f, 0xFFA87444.toInt())
                c.drawLine(-36f, -10f, -50f, -10f, line(0xFF9CE07A.toInt(), 5f))
                c.drawLine(-38f, 6f, -52f, 6f, p)
            }
            IconId.AREA -> {
                c.drawCircle(0f, 0f, 40f, line(0xFFFF9F5A.toInt(), 7f))
                c.drawCircle(0f, 0f, 24f, line(0xFFFFC38A.toInt(), 6f))
                circle(c, 0f, 0f, 9f, 0xFFFFE0B2.toInt())
                for (i in 0 until 4) {
                    c.save(); c.rotate(45f + i * 90f)
                    poly(c, 0xFFFFE0B2.toInt(), 44f, -8f, 56f, 0f, 44f, 8f)
                    c.restore()
                }
            }
            IconId.STEW -> {
                c.drawArc(-44f, -20f, 44f, 44f, 0f, 180f, true, fill(0xFF8D5A35.toInt()))
                oval(c, 0f, 12f, 44f, 10f, 0xFFD9822B.toInt())
                circle(c, -14f, 10f, 6f, 0xFFF0B060.toInt())
                circle(c, 12f, 13f, 5f, 0xFF7CB342.toInt())
                steam(c, 0f, -14f)
            }
            IconId.TEA -> {
                rr(c, -32f, -8f, 22f, 40f, 12f, 0xFFF5F0E6.toInt())
                c.drawArc(14f, -2f, 40f, 26f, -90f, 180f, false, line(0xFFF5F0E6.toInt(), 7f))
                oval(c, -5f, -6f, 26f, 6f, 0xFF8BC34A.toInt())
                oval(c, -6f, 16f, 10f, 14f, 0xFF6FD68C.toInt())
                steam(c, -5f, -22f)
            }
            IconId.SHIELD -> {
                circle(c, 0f, 0f, 44f, 0xFF8D5A35.toInt())
                circle(c, 0f, 0f, 44f, 0xFF8D5A35.toInt())
                c.drawCircle(0f, 0f, 40f, line(0xFFB0BEC5.toInt(), 7f))
                c.drawLine(-20f, -36f, -20f, 36f, line(0xFF6D4223.toInt(), 3f))
                c.drawLine(20f, -36f, 20f, 36f, p)
                circle(c, 0f, 0f, 13f, 0xFFCFD8DC.toInt())
                circle(c, -4f, -4f, 4f, Color.WHITE)
            }
            IconId.HORSESHOE -> {
                p.strokeCap = Paint.Cap.BUTT
                c.drawArc(-36f, -40f, 36f, 32f, 160f, 220f, false, line(0xFFB0BEC5.toInt(), 18f))
                c.drawArc(-36f, -40f, 36f, 32f, 160f, 220f, false, line(0xFFE05A5A.toInt(), 8f))
                circle(c, -34f, 22f, 9f, 0xFFECEFF1.toInt())
                circle(c, 34f, 22f, 9f, 0xFFECEFF1.toInt())
            }
            IconId.BOOK -> {
                rr(c, -34f, -42f, 34f, 42f, 6f, 0xFF8E2C2C.toInt())
                rr(c, -26f, -42f, 34f, 34f, 4f, 0xFFF3E3C3.toInt())
                rr(c, -34f, -42f, 26f, 34f, 6f, 0xFFB23A3A.toInt())
                c.drawRect(-30f, -42f, -24f, 34f, fill(0xFF7A2222.toInt()))
                circle(c, 2f, -6f, 13f, Palette.GOLD)
                circle(c, 2f, -6f, 6f, 0xFFB23A3A.toInt())
            }
            IconId.DOUBLE -> {
                oval(c, -14f, 10f, 34f, 14f, 0xFFECEFF1.toInt())
                oval(c, 14f, -8f, 34f, 14f, 0xFFF5F5F5.toInt())
                oval(c, 14f, -10f, 20f, 7f, 0xFFD9822B.toInt())
                text(c, "×2", 0f, 50f, 34f, 0xFFFFE066.toInt())
            }
            IconId.LUCKY_COIN -> {
                circle(c, 0f, 0f, 42f, Palette.GOLD_DARK)
                circle(c, -2f, -2f, 38f, Palette.GOLD)
                for (i in 0 until 4) {
                    c.save(); c.rotate(i * 90f)
                    circle(c, -2f, -14f, 10f, 0xFF4CAF50.toInt())
                    c.restore()
                }
                circle(c, -2f, -2f, 5f, 0xFF2E7D32.toInt())
                circle(c, -18f, -20f, 5f, Color.WHITE)
            }
            IconId.HEART -> {
                path.reset()
                path.moveTo(0f, 40f)
                path.cubicTo(-60f, 0f, -36f, -54f, 0f, -24f)
                path.cubicTo(36f, -54f, 60f, 0f, 0f, 40f)
                path.close()
                c.drawPath(path, fill(0xFFE53950.toInt()))
                oval(c, -18f, -20f, 9f, 6f, 0x90FFFFFF.toInt())
            }
            IconId.WHETSTONE -> {
                rr(c, -44f, 6f, 44f, 34f, 8f, 0xFF90A4AE.toInt())
                rr(c, -44f, 6f, 44f, 14f, 6f, 0xFFB0BEC5.toInt())
                c.rotate(-25f)
                poly(c, 0xFFE6EEF7.toInt(), -40f, -14f, 30f, -24f, 40f, -16f, -40f, -6f)
                circle(c, 30f, -30f, 5f, 0xFFFFE14D.toInt())
            }
            IconId.PIGGY -> {
                oval(c, 0f, 6f, 40f, 30f, 0xFFF48FB1.toInt())
                circle(c, -26f, -18f, 10f, 0xFFF48FB1.toInt())
                oval(c, 38f, 4f, 9f, 11f, 0xFFF06292.toInt())
                circle(c, 36f, 1f, 2.5f, 0xFF880E4F.toInt())
                circle(c, 36f, 8f, 2.5f, 0xFF880E4F.toInt())
                circle(c, 18f, -6f, 4f, Color.BLACK)
                rr(c, -16f, -24f, 10f, -18f, 3f, 0xFF880E4F.toInt())
                rr(c, -24f, 28f, -12f, 42f, 4f, 0xFFF06292.toInt())
                rr(c, 10f, 28f, 22f, 42f, 4f, 0xFFF06292.toInt())
                circle(c, -4f, -44f, 10f, Palette.GOLD)
            }
            IconId.CHICKEN -> {
                c.drawLine(10f, 10f, 34f, 34f, line(0xFFF5F0E6.toInt(), 12f))
                circle(c, 38f, 30f, 8f, 0xFFF5F0E6.toInt())
                circle(c, 30f, 40f, 8f, 0xFFF5F0E6.toInt())
                oval(c, -10f, -10f, 32f, 26f, 0xFFC8742F.toInt())
                oval(c, -16f, -18f, 16f, 10f, 0xFFE6A15A.toInt())
            }
            IconId.GOLD_BAG -> {
                path.reset()
                path.moveTo(-14f, -30f); path.cubicTo(-56f, 0f, -44f, 44f, 0f, 44f)
                path.cubicTo(44f, 44f, 56f, 0f, 14f, -30f); path.close()
                c.drawPath(path, fill(0xFFA0692F.toInt()))
                rr(c, -16f, -38f, 16f, -28f, 4f, 0xFF6D4223.toInt())
                poly(c, 0xFFA0692F.toInt(), -14f, -38f, -24f, -52f, 0f, -44f, 24f, -52f, 14f, -38f)
                circle(c, 0f, 12f, 16f, Palette.GOLD)
                text(c, "$", 0f, 22f, 28f, Palette.GOLD_DARK)
            }
            IconId.PIE -> {
                poly(c, 0xFFD9822B.toInt(), -44f, 26f, 40f, 26f, 30f, -30f)
                poly(c, 0xFFB71C3C.toInt(), -32f, 20f, 32f, 20f, 26f, -18f)
                c.drawLine(-44f, 26f, 30f, -30f, line(0xFFF0B060.toInt(), 8f))
                circle(c, 4f, 6f, 5f, 0xFFE53950.toInt())
            }
            IconId.MEAD -> {
                c.rotate(20f)
                path.reset()
                path.moveTo(-40f, -24f); path.quadTo(-20f, 50f, 30f, 36f); path.lineTo(40f, 20f); path.quadTo(0f, 20f, -16f, -30f); path.close()
                c.drawPath(path, fill(0xFFF3E3C3.toInt()))
                oval(c, -28f, -27f, 14f, 6f, 0xFFFFC107.toInt())
                c.drawLine(-22f, 6f, 10f, 22f, line(Palette.GOLD_DARK, 4f))
            }
            IconId.ROAST -> {
                c.drawLine(-50f, -6f, 50f, -6f, line(0xFF6D4C41.toInt(), 6f))
                oval(c, 0f, 2f, 34f, 24f, 0xFFA0522D.toInt())
                oval(c, -8f, -6f, 18f, 9f, 0xFFC8742F.toInt())
                circle(c, 40f, -6f, 6f, 0xFFF5F0E6.toInt())
                flameSmall(c, -12f, 40f); flameSmall(c, 12f, 40f)
            }
            IconId.HERBS -> {
                for (i in -2..2) {
                    c.save(); c.rotate(i * 18f, 0f, 40f)
                    c.drawLine(0f, 40f, 0f, -26f, line(0xFF558B2F.toInt(), 4f))
                    oval(c, 0f, -30f, 9f, 18f, if (i % 2 == 0) 0xFF7CB342.toInt() else 0xFF9CCC65.toInt())
                    c.restore()
                }
                rr(c, -14f, 18f, 14f, 28f, 3f, 0xFFB08D57.toInt())
            }
            IconId.CHEESE -> {
                poly(c, 0xFFFFD54F.toInt(), -44f, 30f, 44f, 30f, 44f, -8f, -24f, -30f)
                poly(c, 0xFFFFE082.toInt(), -44f, 30f, -24f, -30f, -44f, -10f)
                circle(c, 10f, 10f, 7f, 0xFFF9A825.toInt())
                circle(c, 28f, -4f, 5f, 0xFFF9A825.toInt())
                circle(c, -10f, 18f, 4f, 0xFFF9A825.toInt())
            }
            IconId.CHEST -> {
                rr(c, -44f, -10f, 44f, 38f, 6f, 0xFF8B5A2B.toInt())
                rr(c, -44f, -38f, 44f, -6f, 18f, 0xFFA0692F.toInt())
                c.drawRect(-44f, -12f, 44f, -4f, fill(Palette.GOLD))
                c.drawRect(-6f, -38f, 6f, 38f, fill(Palette.GOLD))
                rr(c, -10f, -12f, 10f, 8f, 3f, Palette.GOLD_LIGHT)
                circle(c, 0f, -2f, 3.5f, Palette.INK)
            }
            IconId.SKULL -> {
                circle(c, 0f, -8f, 36f, 0xFFF5F0E6.toInt())
                rr(c, -20f, 14f, 20f, 40f, 8f, 0xFFF5F0E6.toInt())
                circle(c, -14f, -6f, 10f, Palette.INK)
                circle(c, 14f, -6f, 10f, Palette.INK)
                poly(c, Palette.INK, 0f, 6f, -5f, 16f, 5f, 16f)
                c.drawLine(-8f, 30f, -8f, 40f, line(Palette.INK, 3f)); c.drawLine(0f, 30f, 0f, 40f, p); c.drawLine(8f, 30f, 8f, 40f, p)
            }
            IconId.HOURGLASS -> {
                rr(c, -32f, -46f, 32f, -36f, 3f, 0xFF8D5A35.toInt())
                rr(c, -32f, 36f, 32f, 46f, 3f, 0xFF8D5A35.toInt())
                poly(c, 0x90B3E5FC.toInt(), -26f, -36f, 26f, -36f, 4f, 0f, 26f, 36f, -26f, 36f, -4f, 0f)
                poly(c, 0xFFFFD54F.toInt(), -16f, -26f, 16f, -26f, 0f, -6f)
                poly(c, 0xFFFFD54F.toInt(), -20f, 34f, 20f, 34f, 0f, 16f)
            }
            IconId.FROST -> {
                for (k in 0 until 6) {
                    val a = k * Math.PI / 3.0
                    val x = (kotlin.math.cos(a) * 43f).toFloat()
                    val y = (kotlin.math.sin(a) * 43f).toFloat()
                    c.drawLine(0f, 0f, x, y, line(0xFF65CFF0.toInt(), 9f))
                    c.drawLine(x * 0.64f, y * 0.64f, x * 0.83f - y * 0.18f, y * 0.83f + x * 0.18f, p)
                    c.drawLine(x * 0.64f, y * 0.64f, x * 0.83f + y * 0.18f, y * 0.83f - x * 0.18f, p)
                }
                circle(c, 0f, 0f, 10f, Color.WHITE)
            }
            IconId.SUN -> {
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4.0
                    val x = kotlin.math.cos(a).toFloat(); val y = kotlin.math.sin(a).toFloat()
                    c.drawLine(x * 30f, y * 30f, x * 47f, y * 47f, line(0xFFE8A83B.toInt(), 7f))
                }
                circle(c, 0f, 0f, 25f, 0xFFFFD462.toInt())
                circle(c, -6f, -6f, 15f, 0xFFFFEFA8.toInt())
            }
            IconId.TRAP -> {
                oval(c, 0f, 17f, 43f, 20f, 0xFF5F7183.toInt())
                oval(c, 0f, 13f, 35f, 12f, 0xFFBFD5E2.toInt())
                for (k in -2..2) poly(c, 0xFFEAF3F9.toInt(), k * 15f - 7f, 8f, k * 15f, -32f, k * 15f + 7f, 8f)
                circle(c, 0f, 17f, 12f, 0xFFC99050.toInt())
            }
            IconId.RAVEN -> {
                poly(c, 0xFF68518D.toInt(), -10f, 5f, -49f, -33f, -38f, 21f, -4f, 26f)
                poly(c, 0xFF8E78B1.toInt(), 0f, 5f, 10f, -37f, 27f, 19f, 5f, 28f)
                oval(c, 0f, 16f, 27f, 21f, 0xFF3E3855.toInt())
                circle(c, 22f, -3f, 20f, 0xFF4D4667.toInt())
                poly(c, 0xFFFFC36C.toInt(), 34f, -3f, 51f, 7f, 34f, 12f)
                circle(c, 26f, -6f, 6f, Color.WHITE)
                circle(c, 28f, -6f, 3f, Palette.INK)
            }
            IconId.BELL -> {
                poly(c, 0xFFD9B270.toInt(), -35f, 29f, -23f, -27f, 0f, -41f, 23f, -27f, 35f, 29f)
                rr(c, -40f, 24f, 40f, 35f, 5f, 0xFFF9D992.toInt())
                circle(c, 0f, 38f, 10f, 0xFF917350.toInt())
                poly(c, 0xFFF2E9FF.toInt(), 5f, -29f, -11f, 1f, 2f, 1f, -5f, 24f, 18f, -10f, 4f, -10f)
            }
            IconId.CRIT -> {
                circle(c, 0f, 0f, 41f, 0xFFCD6858.toInt())
                circle(c, 0f, 0f, 30f, 0xFFFFE4C6.toInt())
                circle(c, 0f, 0f, 17f, 0xFFCD6858.toInt())
                c.drawLine(-37f, 37f, 16f, -16f, line(0xFF4A4252.toInt(), 9f))
                poly(c, 0xFFEAF1F9.toInt(), 8f, -15f, 26f, -26f, 15f, -8f)
            }
            IconId.VAMP -> {
                poly(c, 0xFFE9C574.toInt(), -31f, -34f, 31f, -34f, 21f, 1f, 0f, 13f, -21f, 1f)
                c.drawLine(0f, 10f, 0f, 35f, line(0xFFB99253.toInt(), 8f))
                rr(c, -22f, 33f, 22f, 41f, 5f, 0xFFE9C574.toInt())
                circle(c, -6f, -17f, 10f, 0xFFD96C9C.toInt())
                circle(c, 7f, -17f, 10f, 0xFFD96C9C.toInt())
                poly(c, 0xFFD96C9C.toInt(), -16f, -16f, 17f, -16f, 0f, 4f)
            }
            IconId.FEATHER -> {
                oval(c, 6f, -8f, 22f, 43f, 0xFF8ACDEB.toInt())
                poly(c, 0xFFCEEFFE.toInt(), -5f, 25f, 10f, -43f, 24f, -7f)
                c.drawLine(-24f, 44f, 12f, -32f, line(0xFF4B86A3.toInt(), 5f))
            }
        }
    }

    private fun mug(c: Canvas) {
        c.drawArc(16f, -22f, 46f, 22f, -90f, 180f, false, line(0xFFB07A3B.toInt(), 9f))
        rr(c, -32f, -28f, 24f, 42f, 8f, 0xFFC98A44.toInt())
        c.drawRect(-32f, -14f, 24f, -6f, fill(0xFF8D5A2B.toInt()))
        c.drawRect(-32f, 26f, 24f, 34f, fill(0xFF8D5A2B.toInt()))
        c.drawRect(-24f, -2f, -14f, 20f, fill(0xFFE8B36A.toInt()))
        circle(c, -20f, -32f, 14f, Color.WHITE)
        circle(c, 0f, -38f, 16f, Color.WHITE)
        circle(c, 16f, -30f, 12f, 0xFFF5F5F5.toInt())
    }

    private fun flame(c: Canvas) {
        path.reset()
        path.moveTo(0f, -50f); path.cubicTo(30f, -20f, 44f, 10f, 30f, 32f)
        path.cubicTo(18f, 50f, -18f, 50f, -30f, 32f); path.cubicTo(-42f, 10f, -20f, -10f, 0f, -50f); path.close()
        c.drawPath(path, fill(0xFFFF6D00.toInt()))
        path.reset()
        path.moveTo(0f, -20f); path.cubicTo(18f, 0f, 24f, 18f, 14f, 32f)
        path.cubicTo(6f, 42f, -10f, 42f, -16f, 32f); path.cubicTo(-24f, 16f, -8f, 0f, 0f, -20f); path.close()
        c.drawPath(path, fill(0xFFFFD54F.toInt()))
    }

    private fun flameSmall(c: Canvas, x: Float, y: Float) {
        poly(c, 0xFFFF9800.toInt(), x - 8f, y, x, y - 18f, x + 8f, y)
    }

    private fun steam(c: Canvas, x: Float, y: Float) {
        line(0xC0FFFFFF.toInt(), 4f)
        for (i in -1..1) {
            path.reset()
            path.moveTo(x + i * 14f, y)
            path.cubicTo(x + i * 14f - 8f, y - 10f, x + i * 14f + 8f, y - 18f, x + i * 14f, y - 30f)
            c.drawPath(path, p)
        }
    }

    private companion object {
        const val SLOTS = 6
    }
}
