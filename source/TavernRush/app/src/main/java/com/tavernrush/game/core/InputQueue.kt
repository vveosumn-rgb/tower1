package com.tavernrush.game.core

/**
 * Очередь касаний из UI-потока в игровой поток. Кольцевой буфер на массивах, без аллокаций.
 * UI-поток вызывает push(); игровой поток раз в кадр вызывает drain() и читает события по индексу.
 */
class InputQueue(private val capacity: Int = 256) {
    init { require(capacity >= 2) }
    private var overflowed = false
    private val types = IntArray(capacity)
    private val ids = IntArray(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private var head = 0
    private var size = 0
    private val lock = Any()

    // Снимок для потребителя (читается только игровым потоком).
    private val cTypes = IntArray(capacity)
    private val cIds = IntArray(capacity)
    private val cXs = FloatArray(capacity)
    private val cYs = FloatArray(capacity)

    fun push(type: Int, id: Int, x: Float, y: Float) {
        synchronized(lock) {
            if (overflowed) return
            if (size == capacity) {
                // Пропущенный UP опаснее пропущенного MOVE. До следующего drain отменяем весь жест.
                head = 0; size = 0; overflowed = true
            }
            val i = (head + size) % capacity
            types[i] = type; ids[i] = id; xs[i] = x; ys[i] = y
            size++
        }
    }

    /** Переносит накопленные события в снимок и возвращает их количество. */
    fun drain(): Int {
        synchronized(lock) {
            if (overflowed) {
                overflowed = false; head = 0; size = 0
                cTypes[0] = CANCEL; cIds[0] = -1; cXs[0] = 0f; cYs[0] = 0f
                return 1
            }
            val n = size
            for (k in 0 until n) {
                val i = (head + k) % capacity
                cTypes[k] = types[i]; cIds[k] = ids[i]; cXs[k] = xs[i]; cYs[k] = ys[i]
            }
            head = 0
            size = 0
            return n
        }
    }

    fun clear() {
        synchronized(lock) { head = 0; size = 0; overflowed = false }
    }

    fun type(k: Int) = cTypes[k]
    fun id(k: Int) = cIds[k]
    fun x(k: Int) = cXs[k]
    fun y(k: Int) = cYs[k]

    companion object {
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
        const val CANCEL = 3
        const val BACK = 4
    }
}
