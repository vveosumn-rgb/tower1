package com.tavernrush.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.InputQueue
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.render.Backdrop
import com.tavernrush.game.render.Icons
import com.tavernrush.game.render.WorldRenderer
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.ui.screens.CardScreen
import com.tavernrush.game.ui.screens.HubScreen
import com.tavernrush.game.ui.screens.HudScreen
import com.tavernrush.game.ui.screens.MenuScreen
import com.tavernrush.game.ui.screens.PauseScreen
import com.tavernrush.game.ui.screens.ResultsScreen
import com.tavernrush.game.ui.screens.SplashScreen
import com.tavernrush.game.ui.screens.HeroScreen
import com.tavernrush.game.ui.screens.SettingsScreen
import com.tavernrush.game.ui.screens.ControlsScreen
import com.tavernrush.game.ui.screens.PracticeScreen
import com.tavernrush.game.ui.screens.TeamScreen
import com.tavernrush.game.render.pixel.GfxPreset
import kotlin.math.ceil

/**
 * Связка «логика ↔ экраны ↔ отрисовка».
 * Потоки: [input] — отдельная безопасная очередь; изменения и отрисовка защищены монитором
 * GameApp. Хост использует тот же монитор для сохранения при уходе в фон.
 *
 * Шаг логики: GameLoop подаёт время кадра, кратное периоду дисплея; здесь оно делится на равные
 * подшаги не длиннее 1/60 с (≤ [MAX_STEPS]) — на 90/120 Гц движение ровное без интерполяции,
 * а физика не видит шагов крупнее, чем в тестах. Тесты и симуляции гоняют мир строго по 1/60 с.
 */
class GameApp(val game: Game, val buildLabel: String = "dev") {
    val input = InputQueue()
    val ui = UiMetrics()
    val icons = Icons()
    val draw = UiDraw(icons, ui)
    val worldRenderer = WorldRenderer()
    val joystick = Joystick(ui)

    /** Автопилот вместо джойстика (скриншот-тесты, демо). */
    var autopilot: BotPilot? = null

    var menuBackdrop: Bitmap? = null
        private set
    var hubBackdrop: Bitmap? = null
        private set

    /** Время UI (анимации), с. */
    var time = 0f
        private set
    /** Длительность последнего кадра, с (для анимаций, не влияющих на логику). */
    var frameDt = 1f / 60f
        private set

    val splash = SplashScreen(this)
    val menu = MenuScreen(this)
    val hud = HudScreen(this)
    val cards = CardScreen(this)
    val routeMap = com.tavernrush.game.ui.screens.RouteMapScreen(this)
    val pauseScreen = PauseScreen(this)
    val results = ResultsScreen(this)
    val hub = HubScreen(this)
    val heroes = HeroScreen(this)
    val settings = SettingsScreen(this)
    val team = TeamScreen(this)
    val controls = ControlsScreen(this)
    val hybrids = com.tavernrush.game.ui.screens.HybridScreen(this)
    val evolution = com.tavernrush.game.ui.screens.EvolutionScreen(this)
    val compositions = com.tavernrush.game.ui.screens.CompositionScreen(this)
    val sanctuary = com.tavernrush.game.ui.screens.SanctuaryScreen(this)
    val adventureScreens = com.tavernrush.game.ui.screens.AdventureScreens(this)
    val practice = PracticeScreen(this)
    private var appliedGfx = -1

    private var shown: ScreenId? = null
    private var fade = 0f
    private var sized = false

    @Volatile private var insL = 0
    @Volatile private var insT = 0
    @Volatile private var insR = 0
    @Volatile private var insB = 0
    @Volatile private var insetsDirty = false

    fun screenFor(id: ScreenId): Screen = when (id) {
        ScreenId.SPLASH -> splash
        ScreenId.MENU -> menu
        ScreenId.RUN -> hud
        ScreenId.CARD_PICK -> cards
        ScreenId.ROUTE_MAP -> routeMap
        ScreenId.PAUSE -> pauseScreen
        ScreenId.RESULTS -> results
        ScreenId.HUB -> hub
        ScreenId.HEROES -> heroes
        ScreenId.SETTINGS -> settings
        ScreenId.TEAM -> team
        ScreenId.CONTROLS -> controls
        ScreenId.HYBRIDS -> hybrids
        ScreenId.EVOLUTION -> evolution
        ScreenId.COMPOSITIONS -> compositions
        ScreenId.SANCTUARY -> sanctuary
        ScreenId.PRACTICE -> practice
        ScreenId.JOURNEY, ScreenId.HOME, ScreenId.WORKSHOP, ScreenId.LINEAGE, ScreenId.TRIALS, ScreenId.ATLAS -> adventureScreens
    }

    /** Безопасные отступы (вырез камеры, системные панели), px. Любой поток. */
    @Synchronized fun setInsets(l: Int, t: Int, r: Int, b: Int) {
        insL = l; insT = t; insR = r; insB = b
        insetsDirty = true
    }

    /** Новый размер поверхности; не пересекается с использованием Bitmap при render. */
    @Synchronized fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        cancelInput()
        val sizeChanged = !sized || w != ui.w || h != ui.h
        insetsDirty = false
        ui.set(w, h, insL, insT, insR, insB)
        if (sizeChanged) {
            worldRenderer.resize(w, h)
            icons.clear()
            menuBackdrop?.recycle()
            hubBackdrop?.recycle()
            menuBackdrop = Backdrop.menu(w, h, ui.u)
            hubBackdrop = Backdrop.hub(w, h, ui.u)
        }
        sized = true
        splash.layout(); menu.layout(); hud.layout(); cards.layout()
        routeMap.layout(); pauseScreen.layout(); results.layout(); hub.layout()
        heroes.layout(); settings.layout(); team.layout(); controls.layout()
        practice.layout(); adventureScreens.layout();sanctuary.layout();compositions.layout();evolution.layout();hybrids.layout()
        syncSettings()
    }

    /** Применить настройки профиля: пресет графики, стик, сила тряски. Дёшево — можно каждый кадр. */
    private fun syncSettings() {
        val p = game.profile
        if (p.gfxPreset != appliedGfx) { appliedGfx = p.gfxPreset; worldRenderer.setPreset(GfxPreset.fromOrdinal(p.gfxPreset)) }
        ControlLayout.apply(ui, p, joystick)
        game.world.fx.shakeScale = p.shakePct / 100f
    }

    /** Кадр логики: ввод, затем подшаги. */
    @Synchronized fun update(frameSeconds: Float) {
        if (insetsDirty) resize(ui.w, ui.h)
        syncSettings()
        val dt = if (!frameSeconds.isFinite()) 0f else frameSeconds.coerceIn(0f, MAX_FRAME)
        frameDt = dt
        processInput()
        val n = ceil(dt / STEP).toInt().coerceIn(1, MAX_STEPS)
        val sd = dt / n
        for (i in 0 until n) step(sd)
    }

    /** Один шаг логики (тесты вызывают напрямую с 1/60 с). */
    @Synchronized fun step(dt: Float) {
        if (!dt.isFinite() || dt < 0f || dt > MAX_FRAME) return
        syncSettings()
        syncScreen()
        screenFor(game.screen).update(dt)
        game.tick(dt)
        syncScreen()
        time += dt
        if (fade > 0f) fade = maxOf(0f, fade - dt / FADE_TIME)
    }

    /** Обработать накопленный ввод без шага логики (тесты). */
    @Synchronized fun pumpInput() { syncSettings(); processInput() }

    /** Очередь не должна оживить прежнее движение после смены Surface/ухода в фон. */
    @Synchronized fun cancelInput() {
        input.clear()
        screenFor(game.screen).onCancel()
        joystick.release()
        game.world.moveX = 0f; game.world.moveY = 0f
    }

    @Synchronized fun render(c: Canvas) {
        syncScreen()
        screenFor(game.screen).draw(c)
        if (fade > 0.004f) draw.dim(c, (255f * fade * fade).toInt())
    }

    /** Мир (для экранов поверх забега). */
    @Synchronized fun drawWorld(c: Canvas) = worldRenderer.draw(c, game.world, frameDt)

    private fun processInput() {
        val n = input.drain()
        for (k in 0 until n) {
            val type = input.type(k)
            if (type == InputQueue.BACK) {
                game.back()
                syncScreen()
                continue
            }
            val id = input.id(k)
            val x = input.x(k)
            val y = input.y(k)
            val s = screenFor(game.screen)
            when (type) {
                InputQueue.DOWN -> s.onDown(id, x, y)
                InputQueue.MOVE -> s.onMove(id, x, y)
                InputQueue.UP -> s.onUp(id, x, y)
                InputQueue.CANCEL -> s.onCancel()
            }
            syncScreen()
        }
    }

    private fun syncScreen() {
        val cur = game.screen
        val prev = shown
        if (prev == cur) return
        shown = cur
        screenFor(prev ?: cur).onCancel()
        joystick.release()
        game.world.moveX = 0f; game.world.moveY = 0f
        if (prev != null) screenFor(prev).resetPress()
        val s = screenFor(cur)
        s.resetPress()
        s.onEnter()
        fade = if (prev != null && needsFade(prev, cur)) 1f else 0f
    }

    private fun needsFade(a: ScreenId, b: ScreenId): Boolean = when (b) {
        ScreenId.ROUTE_MAP, ScreenId.CARD_PICK, ScreenId.PAUSE, ScreenId.RESULTS -> false
        ScreenId.RUN -> a != ScreenId.CARD_PICK && a != ScreenId.PAUSE
        else -> true
    }

    companion object {
        const val STEP = 1f / 60f
        const val MAX_STEPS = 6
        const val MAX_FRAME = 0.1f
        const val FADE_TIME = 0.28f
    }
}
