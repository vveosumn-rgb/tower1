package com.tavernrush.game.render

/** Палитра игры: тёплая «таверна» в UI, приглушённый луг в мире (враги контрастнее фона). */
object Palette {
    const val OUTLINE = 0xFF1E1620.toInt()
    const val SHADOW = 0x55000000

    // Мир
    const val GRASS = 0xFF3F6B3A.toInt()
    const val GRASS_DARK = 0xFF355C33.toInt()
    const val GRASS_LIGHT = 0xFF4C7D42.toInt()
    const val DIRT = 0xFF6B5638.toInt()

    // Герой
    const val SKIN = 0xFFF2C79B.toInt()
    const val TUNIC = 0xFF3E6FB0.toInt()
    const val APRON = 0xFFF3EDE0.toInt()
    const val BANDANA = 0xFFD9443A.toInt()
    const val BOOTS = 0xFF5A3A26.toInt()
    const val BEARD = 0xFFA0522D.toInt()

    // UI
    const val WOOD_DARK = 0xFF2B1D16.toInt()
    const val WOOD = 0xFF4A3226.toInt()
    const val WOOD_LIGHT = 0xFF6B4A36.toInt()
    const val GOLD = 0xFFF2B33D.toInt()
    const val GOLD_LIGHT = 0xFFFFD978.toInt()
    const val GOLD_DARK = 0xFFB07A1E.toInt()
    const val PARCHMENT = 0xFFF3E3C3.toInt()
    const val PARCHMENT_DARK = 0xFFD9C29A.toInt()
    const val INK = 0xFF3A2618.toInt()
    const val TEXT = 0xFFFFF6E6.toInt()
    const val TEXT_DIM = 0xFFCDB89A.toInt()
    const val GREEN = 0xFF4CAF50.toInt()
    const val GREEN_LIGHT = 0xFF7BD67E.toInt()
    const val GREEN_DARK = 0xFF2E7D32.toInt()
    const val RED = 0xFFE53935.toInt()
    const val RED_DARK = 0xFF9E1F1B.toInt()
    const val BLUE = 0xFF4FA3E0.toInt()
    const val XP = 0xFF57C7FF.toInt()
    const val XP_DARK = 0xFF1C5C86.toInt()
    const val HP = 0xFFE8483F.toInt()
    const val HP_BACK = 0xFF4A1512.toInt()
    const val DISABLED = 0xFF6E625A.toInt()

    fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or ((a and 255) shl 24)

    fun mul(color: Int, k: Float): Int {
        val r = ((color shr 16 and 255) * k).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 255) * k).toInt().coerceIn(0, 255)
        val b = ((color and 255) * k).toInt().coerceIn(0, 255)
        return (color and -0x1000000) or (r shl 16) or (g shl 8) or b
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255; val aa = a ushr 24
        val br = b shr 16 and 255; val bg = b shr 8 and 255; val bb = b and 255; val ba = b ushr 24
        val r = (ar + (br - ar) * t).toInt(); val g = (ag + (bg - ag) * t).toInt()
        val bl = (ab + (bb - ab) * t).toInt(); val al = (aa + (ba - aa) * t).toInt()
        return (al shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
