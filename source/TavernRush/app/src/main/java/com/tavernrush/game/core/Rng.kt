package com.tavernrush.game.core

/**
 * Детерминированный ГПСЧ: xoroshiro128** с инициализацией через SplitMix64.
 * В игровой логике использовать ТОЛЬКО его (никаких Math.random / Random() без сида):
 * от этого зависят бот-симуляции, воспроизводимость багов и будущий ежедневный забег.
 * Без аллокаций.
 */
class Rng(seed: Long = 1L) {
    private var s0 = 0L
    private var s1 = 0L
    var seed = 0L
        private set

    init {
        reseed(seed)
    }

    fun reseed(seed: Long) {
        this.seed = seed
        var x = seed
        x += GAMMA
        s0 = mix(x)
        x += GAMMA
        s1 = mix(x)
        if (s0 == 0L && s1 == 0L) s1 = 0x2545F4914F6CDD1DL
    }

    fun nextLong(): Long {
        val a = s0
        var b = s1
        val result = java.lang.Long.rotateLeft(a * 5, 7) * 9
        b = b xor a
        s0 = java.lang.Long.rotateLeft(a, 24) xor b xor (b shl 16)
        s1 = java.lang.Long.rotateLeft(b, 37)
        return result
    }

    /** Целое в [0, bound). */
    fun nextInt(bound: Int): Int {
        if (bound <= 1) return 0
        return ((nextLong() ushr 33) % bound).toInt()
    }

    /** Целое в [from, toInclusive]. */
    fun nextInt(from: Int, toInclusive: Int): Int = from + nextInt(toInclusive - from + 1)

    /** Вещественное в [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() * INV_24

    fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()

    /** Вещественное в [-1, 1). */
    fun signed(): Float = nextFloat() * 2f - 1f

    fun chance(p: Float): Boolean = nextFloat() < p

    fun angle(): Float = nextFloat() * TWO_PI

    companion object {
        private const val GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val INV_24 = 1f / (1 shl 24)

        /** Финализатор SplitMix64: хорош для вывода независимых сидов. */
        fun mix(v: Long): Long {
            var z = v
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        /** Сид подпотока: один сид забега → независимые потоки спавна, лута, карт… */
        fun stream(runSeed: Long, streamId: Int): Long = mix(runSeed + GAMMA * (streamId + 1))
    }
}
