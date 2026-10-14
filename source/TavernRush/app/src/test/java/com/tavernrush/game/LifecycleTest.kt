package com.tavernrush.game

import android.view.SurfaceHolder
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.platform.GameLoop
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.ui.GameApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LifecycleTest {
    private fun app() = GameApp(Game(MemoryStore())).also { it.resize(360, 780) }

    @Test fun invalidFrameAndStepCannotCorruptUiTime() {
        val app = app()
        app.game.startRun()
        for (dt in floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f)) {
            app.update(dt)
            app.step(dt)
        }
        app.step(10f)
        assertEquals(0f, app.time, 0f)
        assertEquals(0f, app.game.world.time, 0f)
        app.update(0.1f)
        assertEquals(0.1f, app.game.world.time, 0.00001f)
        assertTrue(app.time.isFinite())
    }

    @Test fun hostPauseFreezesRunUntilExplicitResume() {
        val app = app()
        app.game.startRun()
        app.update(0.1f)
        synchronized(app) { app.game.onHostPause() }
        val time = app.game.world.time
        repeat(10) { app.update(0.1f) }
        assertEquals(ScreenId.PAUSE, app.game.screen)
        assertEquals(time, app.game.world.time, 0f)
        app.game.resume()
        app.update(1f / 60f)
        assertTrue(app.game.world.time > time)
    }

    @Test fun blockedCanvasCannotStartASecondWriterOrReviveOldGeneration() {
        val firstEntered = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val secondRelease = CountDownLatch(1)
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        // Моделируем драйвер, который не реагирует на interrupt во время lockCanvas.
        val holder = Proxy.newProxyInstance(
            SurfaceHolder::class.java.classLoader, arrayOf(SurfaceHolder::class.java)
        ) { _, method, _ ->
            if (method.name == "lockHardwareCanvas") {
                val index = calls.incrementAndGet()
                val writers = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, writers) }
                try {
                    when (index) {
                        1 -> { firstEntered.countDown(); awaitUninterruptibly(firstRelease) }
                        2 -> { secondEntered.countDown(); awaitUninterruptibly(secondRelease) }
                    }
                } finally { active.decrementAndGet() }
            }
            null
        } as SurfaceHolder
        val app = app()
        app.game.startRun()
        val loop = GameLoop(holder, app)
        try {
            loop.start()
            assertTrue(firstEntered.await(3, TimeUnit.SECONDS))
            loop.stop() // timeout допускается, но ссылка на живой поток должна сохраниться
            synchronized(app) {
                app.game.onHostPause()
                app.resize(400, 800)
            }
            val pausedTime = app.game.world.time
            loop.start()
            Thread.sleep(60)
            assertEquals("Новый писатель не должен обгонять старый", 1, calls.get())
            firstRelease.countDown()
            assertTrue(secondEntered.await(3, TimeUnit.SECONDS))
            assertEquals(1, peak.get())
            assertEquals(pausedTime, app.game.world.time, 0f)
            assertEquals(ScreenId.PAUSE, app.game.screen)
        } finally {
            firstRelease.countDown()
            secondRelease.countDown()
            loop.stop()
        }
    }

    private fun awaitUninterruptibly(latch: CountDownLatch) {
        while (latch.count > 0) {
            try { latch.await() } catch (_: InterruptedException) { /* имитация драйвера */ }
        }
    }
}