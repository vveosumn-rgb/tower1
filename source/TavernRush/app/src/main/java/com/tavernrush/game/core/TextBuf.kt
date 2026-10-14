package com.tavernrush.game.core

/**
 * Буфер символов для текста HUD без аллокаций: canvas.drawText(buf.chars, 0, buf.length, …).
 * Строки (String) в кадре не собирать — только через TextBuf или заранее готовые строки из данных.
 */
class TextBuf(capacity: Int = 64) {
    val chars = CharArray(capacity)
    var length = 0
        private set

    fun clear(): TextBuf {
        length = 0
        return this
    }

    fun add(s: String): TextBuf {
        val n = minOf(s.length, chars.size - length)
        for (i in 0 until n) chars[length + i] = s[i]
        length += n
        return this
    }

    fun add(c: Char): TextBuf {
        if (length < chars.size) chars[length++] = c
        return this
    }

    fun add(v: Int): TextBuf = add(v.toLong())

    fun add(v: Long): TextBuf {
        var x = v
        if (x == 0L) return add('0')
        if (x < 0L) {
            add('-')
            x = if (x == Long.MIN_VALUE) Long.MAX_VALUE else -x
        }
        val start = length
        while (x > 0L && length < chars.size) {
            chars[length++] = '0' + (x % 10L).toInt()
            x /= 10L
        }
        var i = start
        var j = length - 1
        while (i < j) {
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
            i++; j--
        }
        return this
    }

    /** Число с одним знаком после запятой: 12,5. */
    /** Число с разделением разрядов неразрывным пробелом: 136 646. */
    fun addGrouped(v: Long): TextBuf {
        if (v < 0L) { add('-'); return addGrouped(if (v == Long.MIN_VALUE) Long.MAX_VALUE else -v) }
        if (v < 1000L) return add(v)
        addGrouped(v / 1000L)
        add('\u00A0')
        val r = (v % 1000L).toInt()
        if (r < 100) add('0')
        if (r < 10) add('0')
        return add(r)
    }

    fun addGrouped(v: Int): TextBuf = addGrouped(v.toLong())

    fun add1(v: Float): TextBuf {
        val scaled = Math.round(v * 10f)
        val neg = scaled < 0
        val a = if (neg) -scaled else scaled
        if (neg) add('-')
        add(a / 10)
        add(',')
        return add(a % 10)
    }

    /** Время мм:сс. */
    fun addTime(totalSec: Int): TextBuf {
        val t = if (totalSec < 0) 0 else totalSec
        val m = t / 60
        val s = t % 60
        if (m < 10) add('0')
        add(m)
        add(':')
        if (s < 10) add('0')
        return add(s)
    }

    /** Только для тестов и отладки (аллоцирует). */
    override fun toString(): String = String(chars, 0, length)
}
