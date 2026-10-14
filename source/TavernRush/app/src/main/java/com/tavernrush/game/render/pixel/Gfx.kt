package com.tavernrush.game.render.pixel

/** Пресеты графики: от «слабого телефона» до «всё включено». Подробности — docs/GRAPHICS_PRESETS.md. */
enum class GfxPreset(val label: String, val hint: String) {
    AUTO("Авто", "Подбирает качество по плавности"),
    LOW("Низкое", "Тени-пятна, без освещения"),
    MEDIUM("Среднее", "Тени от фигур, освещение"),
    HIGH("Высокое", "Свечение, тени облаков"),
    ULTRA("Ультра", "Максимум частиц и эффектов");

    companion object {
        fun fromOrdinal(i: Int): GfxPreset = entries.getOrElse(i) { AUTO }
    }
}

/** Что именно включает пресет. Значения — бюджеты, а не пожелания: governor меняет пресет, чтобы уложиться в кадр. */
class GfxConfig(
    val preset: GfxPreset,
    /** 0 — без теней, 1 — пятно, 2 — проекция силуэта + пятно. */
    val shadowMode: Int,
    /** 0 — выкл, 1 — карта света грубо (пиксельная), 2 — гладкая. */
    val lighting: Int,
    val bloom: Boolean,
    val maxParticles: Int,
    val ambientMotes: Int,
    val cloudShadows: Boolean,
    val groundDetail: Float,
    val props: Boolean,
    val aberration: Boolean,
    val grading: Boolean,
    val trails: Boolean,
    val lightCount: Int,
    val damageNumbers: Int,
)

object Gfx {
    val LOW = GfxConfig(GfxPreset.LOW, 1, 0, false, 260, 0, false, 0.5f, false, false, false, false, 0, 10)
    val MEDIUM = GfxConfig(GfxPreset.MEDIUM, 2, 1, false, 520, 10, false, 0.8f, true, false, true, true, 12, 18)
    val HIGH = GfxConfig(GfxPreset.HIGH, 2, 2, true, 800, 18, true, 1f, true, false, true, true, 24, 26)
    val ULTRA = GfxConfig(GfxPreset.ULTRA, 2, 2, true, 1000, 30, true, 1.25f, true, true, true, true, 40, 40)

    /** AUTO стартует с HIGH и опускается по необходимости (см. GfxGovernor). */
    fun config(p: GfxPreset): GfxConfig = when (p) {
        GfxPreset.LOW -> LOW
        GfxPreset.MEDIUM -> MEDIUM
        GfxPreset.HIGH, GfxPreset.AUTO -> HIGH
        GfxPreset.ULTRA -> ULTRA
    }
}

/**
 * Автоподбор качества: смотрит на время отрисовки кадра (скользящее среднее) и опускает пресет,
 * если оно дольше бюджета; никогда не поднимает сам (нет «качелей»). Чистая логика — легко тестируется.
 */
class GfxGovernor(private val budgetMs: Float = 11f) {
    var level = GfxPreset.HIGH
        private set
    private var avg = 0f
    private var over = 0f
    private var frames = 0

    fun reset(start: GfxPreset = GfxPreset.HIGH) { level = start; avg = 0f; over = 0f; frames = 0 }

    /** Возвращает true, если пресет понижен на этом кадре. */
    fun feed(renderMs: Float, dt: Float): Boolean {
        if (!renderMs.isFinite() || renderMs < 0f) return false
        frames++
        avg = if (frames == 1) renderMs else avg * 0.94f + renderMs * 0.06f
        if (frames < 90) return false
        if (avg > budgetMs) over += dt else over = maxOf(0f, over - dt * 0.5f)
        if (over >= 2.5f && level != GfxPreset.LOW) {
            level = when (level) {
                GfxPreset.ULTRA -> GfxPreset.HIGH
                GfxPreset.HIGH -> GfxPreset.MEDIUM
                else -> GfxPreset.LOW
            }
            over = 0f; frames = 0
            return true
        }
        return false
    }
}
