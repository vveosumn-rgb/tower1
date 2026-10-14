package com.tavernrush.game.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.tavernrush.game.core.TextBuf

/**
 * Текст с обводкой (читается на любом фоне) без аллокаций в кадре.
 * Перенос строк кэшируется по ссылке на строку и ширине (вычисляется при первом показе).
 */
class Txt {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isSubpixelText = true
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fill.typeface
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        isSubpixelText = true
    }
    val buf = TextBuf(96)

    fun setup(size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER, outline: Int = Palette.OUTLINE, outlineW: Float = size * 0.16f): Txt {
        fill.textSize = size
        fill.color = color
        fill.textAlign = align
        stroke.textSize = size
        stroke.textAlign = align
        stroke.color = outline
        stroke.strokeWidth = outlineW
        return this
    }

    /** Ширина строки при текущем размере. */
    fun width(s: String): Float = fill.measureText(s)
    fun width(b: TextBuf): Float = fill.measureText(b.chars, 0, b.length)

    fun draw(c: Canvas, s: String, x: Float, y: Float, outline: Boolean = true) {
        if (outline && stroke.strokeWidth > 0f) c.drawText(s, x, y, stroke)
        c.drawText(s, x, y, fill)
    }

    fun draw(c: Canvas, b: TextBuf, x: Float, y: Float, outline: Boolean = true) {
        if (outline && stroke.strokeWidth > 0f) c.drawText(b.chars, 0, b.length, x, y, stroke)
        c.drawText(b.chars, 0, b.length, x, y, fill)
    }

    fun drawRange(c: Canvas, s: String, start: Int, end: Int, x: Float, y: Float, outline: Boolean) {
        if (outline && stroke.strokeWidth > 0f) c.drawText(s, start, end, x, y, stroke)
        c.drawText(s, start, end, x, y, fill)
    }

    /** Уменьшает кегль, чтобы строка влезла в maxW (не обрезаем тексты). */
    fun fit(s: String, maxW: Float, size: Float): Float {
        fill.textSize = size
        val w = fill.measureText(s)
        val k = if (w > maxW && w > 0f) maxW / w else 1f
        val ns = size * k
        fill.textSize = ns; stroke.textSize = ns
        if (k < 1f) stroke.strokeWidth *= k
        return ns
    }

    // ------------------------------------------------------------ перенос строк

    private val cacheKey = arrayOfNulls<String>(CACHE)
    private val cacheW = IntArray(CACHE)
    private val cacheSize = IntArray(CACHE)
    private val cacheBreaks = Array(CACHE) { IntArray(MAX_LINES + 1) }
    private val cacheLines = IntArray(CACHE)
    private var cacheNext = 0

    /** Разбивает s на строки шириной ≤ maxW при текущем кегле; результат — индекс в кэше. */
    private fun wrap(s: String, maxW: Float): Int {
        val wi = maxW.toInt()
        val si = fill.textSize.toInt()
        for (i in 0 until CACHE) if (cacheKey[i] === s && cacheW[i] == wi && cacheSize[i] == si) return i
        val slot = cacheNext
        cacheNext = (cacheNext + 1) % CACHE
        val br = cacheBreaks[slot]
        var lines = 0
        var start = 0
        br[0] = 0
        while (start < s.length && lines < MAX_LINES) {
            val newline=s.indexOf('\n',start)
            val paragraphEnd=if(newline>=0)newline else s.length
            var end = paragraphEnd
            if (fill.measureText(s, start, end) > maxW) {
                // Ищем последний пробел, при котором строка помещается.
                var lastSpace = -1
                var k = start
                while (k < paragraphEnd) {
                    if (s[k] == ' ') {
                        if (fill.measureText(s, start, k) <= maxW) lastSpace = k else break
                    }
                    k++
                }
                end = if (lastSpace > start) lastSpace else {
                    // Одно длинное слово — режем по символам.
                    var e = start + 1
                    while (e < paragraphEnd && fill.measureText(s, start, e + 1) <= maxW) e++
                    e
                }
            }
            if(end==paragraphEnd&&newline>=0)end=newline+1
            lines++
            br[lines] = end
            start = end
            // Пробелы в начале следующей строки пропускаются при отрисовке.
            while (start < s.length && s[start] == ' ') start++
        }
        cacheKey[slot] = s; cacheW[slot] = wi; cacheSize[slot] = si; cacheLines[slot] = lines
        return slot
    }

    /** Число строк, которое займёт текст. */
    fun lineCount(s: String, maxW: Float): Int = cacheLines[wrap(s, maxW)]

    /** Рисует текст с переносом; y — базовая линия первой строки. Возвращает число строк. */
    fun drawWrapped(c: Canvas, s: String, x: Float, y: Float, maxW: Float, lineH: Float, outline: Boolean = true): Int {
        val slot = wrap(s, maxW)
        val br = cacheBreaks[slot]
        val n = cacheLines[slot]
        var start = 0
        for (i in 0 until n) {
            val end = br[i + 1]
            while (start < end && s[start] == ' ') start++
            var visibleEnd=end
            while(visibleEnd>start&&(s[visibleEnd-1]=='\n'||s[visibleEnd-1]=='\r'||s[visibleEnd-1]==' '))visibleEnd--
            if(visibleEnd>start)drawRange(c, s, start, visibleEnd, x, y + i * lineH, outline)
            start = end
        }
        return n
    }

    companion object {
        const val CACHE = 48
        const val MAX_LINES = 32
    }
}
