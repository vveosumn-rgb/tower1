package com.tavernrush.game.core

import java.util.concurrent.atomic.AtomicLong

/** Старый поток не оживает после stop → start, даже если Canvas долго блокировался. */
class LoopGeneration {
    private val value = AtomicLong()
    fun next(): Long = value.incrementAndGet()
    fun invalidate() { value.incrementAndGet() }
    fun isCurrent(token: Long): Boolean = value.get() == token
}