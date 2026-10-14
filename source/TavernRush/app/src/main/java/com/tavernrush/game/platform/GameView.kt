package com.tavernrush.game.platform

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.tavernrush.game.core.InputQueue
import com.tavernrush.game.ui.GameApp

/** Поверхность игры: касания → InputQueue, жизненный цикл игрового потока. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val app: GameApp) : SurfaceView(context), SurfaceHolder.Callback {
    private val loop = GameLoop(holder, app)
    private var hasSurface = false
    private var sized = false
    private var resumed = false

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun surfaceCreated(h: SurfaceHolder) { hasSurface = true }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        loop.stop()
        loop.refreshRate = display?.refreshRate?.takeIf { it >= 30f } ?: 60f
        app.cancelInput()
        app.resize(width, height)
        sized = true
        if (resumed) loop.start()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        hasSurface = false
        sized = false
        loop.stop()
        app.cancelInput()
    }

    fun resume() {
        resumed = true
        if (hasSurface && sized) loop.start()
    }

    /** Отменяет поколение потока. Прямой доступ к Game из хоста — под synchronized(app). */
    fun pause() {
        resumed = false
        loop.stop()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val q = app.input
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                q.push(InputQueue.DOWN, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) q.push(InputQueue.MOVE, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                q.push(InputQueue.UP, e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> q.push(InputQueue.CANCEL, -1, 0f, 0f)
        }
        return true
    }
}
