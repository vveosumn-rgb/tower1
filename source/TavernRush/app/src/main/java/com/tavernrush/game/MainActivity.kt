package com.tavernrush.game

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.InputQueue
import com.tavernrush.game.platform.AndroidFeedback
import com.tavernrush.game.platform.GameView
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.save.SaveManager
import com.tavernrush.game.ui.GameApp
import java.io.File

/**
 * Единственная активность: полноэкранный SurfaceView. Уход в фон → пауза забега и запись
 * страховочной награды (Game.onHostPause), «назад» → игровая логика (пауза/выход из меню).
 */
class MainActivity : ComponentActivity() {
    private lateinit var game: Game
    private lateinit var app: GameApp
    private lateinit var view: GameView
    private lateinit var feedback: AndroidFeedback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        SpriteBank.warmUpAsync()
        game = Game(SaveManager(File(filesDir, "save")))
        feedback = AndroidFeedback(this) { runOnUiThread { finish() } }
        game.platform = feedback
        app = GameApp(game, buildLabel())
        view = GameView(this, app)
        setContentView(view)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            app.setInsets(maxOf(bars.left, gestures.left), bars.top, maxOf(bars.right, gestures.right), maxOf(bars.bottom, gestures.bottom))
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                app.input.push(InputQueue.BACK, 0, 0f, 0f)
            }
        })
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        view.resume()
    }

    override fun onPause() {
        view.pause()
        // Драйвер Canvas может задержать завершение потока; монитор сериализует доступ.
        synchronized(app) { app.cancelInput(); game.onHostPause() }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
        else if (::app.isInitialized) synchronized(app) { app.cancelInput(); game.pause() }
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    private fun hideSystemUi() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun buildLabel(): String {
        val v = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
        return "Tavern Rush v$v"
    }
}
