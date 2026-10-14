package com.tavernrush.game.platform

import android.graphics.Canvas
import android.view.SurfaceHolder
import com.tavernrush.game.core.FrameClock
import com.tavernrush.game.core.LoopGeneration
import com.tavernrush.game.ui.GameApp

/**
 * Ввод → логика → Canvas. Логика получает только реально накопленное монотонное время,
 * независимо от аппаратного vsync и скорости программного Canvas.
 * stop отменяет поколение; при заблокированном драйвере новый поток ждёт выхода старого.
 * Доступ к GameApp синхронизирован, чтобы resize не освобождал используемые Bitmap.
 */
class GameLoop(private val holder: SurfaceHolder, private val app: GameApp) {
    private val stateLock = Any()
    private val generation = LoopGeneration()
    /** Частота дисплея, Гц (GameView обновляет при смене поверхности). */
    @Volatile var refreshRate = 60f
    // thread и startRequested защищены stateLock; одновременно работает только один поток.
    private var thread: Thread? = null
    private var startRequested = false
    private var hardware = true

    fun start() {
        synchronized(stateLock) {
            startRequested = true
            if (thread?.isAlive == true) return
            launchLocked()
        }
    }

    fun stop() {
        val t = synchronized(stateLock) {
            startRequested = false
            generation.invalidate()
            thread
        } ?: return
        if (t === Thread.currentThread()) return
        t.interrupt()
        try {
            t.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        synchronized(stateLock) {
            // Не забываем ещё живой поток: start() поставит перезапуск в очередь.
            if (thread === t && !t.isAlive) thread = null
        }
    }

    private fun launchLocked() {
        val token = generation.next()
        hardware = true
        thread = Thread({ run(token) }, "tavern-game").also { it.start() }
    }

    private fun run(token: Long) {
        try {
            loop(token)
        } catch (e: Exception) {
            android.util.Log.e("TavernRush", "Игровой поток остановлен", e)
        } finally {
            synchronized(stateLock) {
                if (thread === Thread.currentThread()) {
                    thread = null
                    // Только запрошенный restart; при обычной ошибке не создаём цикл падений.
                    if (startRequested && !generation.isCurrent(token)) launchLocked()
                }
            }
        }
    }

    private fun loop(token: Long) {
        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY) } catch (e: Exception) { }
        var last = System.nanoTime()
        val clock = FrameClock(GameApp.MAX_FRAME.toDouble())
        while (generation.isCurrent(token)) {
            val start = System.nanoTime()
            val measured = (start - last) / 1_000_000_000.0
            last = start
            val dt = clock.advance(measured, refreshRate)
            if (dt == 0f) {
                sleep(clock.waitMillis(refreshRate))
                continue
            }
            synchronized(app) {
                if (!generation.isCurrent(token)) return
                app.update(dt)
            }
            val c = lock(token)
            if (c == null) {
                sleep(8)
                continue
            }
            try {
                synchronized(app) {
                    if (generation.isCurrent(token)) app.render(c)
                }
            } finally {
                try { holder.unlockCanvasAndPost(c) } catch (e: IllegalStateException) { }
            }
        }
    }

    private fun lock(token: Long): Canvas? {
        if (!generation.isCurrent(token)) return null
        if (hardware) {
            try {
                return holder.lockHardwareCanvas()
            } catch (e: Exception) {
                hardware = false
            }
        }
        if (!generation.isCurrent(token)) return null
        return try { holder.lockCanvas() } catch (e: Exception) { null }
    }

    private fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (e: InterruptedException) { }
    }
}
