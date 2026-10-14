package com.tavernrush.game.entities

/** Частица (чисто визуальная, логика игры от неё не зависит). */
class Particle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var size = 4f
    var color = 0
    var drag = 3f
    var kind = 0
    /** Высота над землёй (2.5D): экранный y = y − z. Частицы падают и отскакивают. */
    var z = 0f
    var vz = 0f
    var grav = 0f
    var bounce = 0f

    companion object {
        const val DOT = 0
        const val SPARK = 1
        const val SMOKE = 2
        const val STAR = 3
        /** Светящийся уголёк (аддитивно, мерцает, всплывает). */
        const val EMBER = 4
        /** Осколок с гравитацией и отскоком. */
        const val CHUNK = 5
        /** Мерцающий крестик. */
        const val SPARKLE = 6
        /** Монета: золотая пара пикселей с блеском. */
        const val COIN = 7
        /** Лист/пылинка окружения. */
        const val LEAF = 8
        /** Светлячок: аддитивная точка с пульсацией. */
        const val MOTE = 9
    }
}

/** Короткоживущий источник света: вспышка выстрела, взрыв, молния. */
class Light {
    var x = 0f
    var y = 0f
    var radius = 100f
    var color = 0
    var intensity = 1f
    var life = 0f
    var maxLife = 0.2f
}

/** Всплывающее число урона/лечения/золота. */
class FloatText {
    var x = 0f
    var y = 0f
    var life = 0f
    var maxLife = 0.7f
    var value = 0
    var color = 0
    var big = false
    var kind = 0

    companion object {
        const val DAMAGE = 0
        const val HEAL = 1
        const val GOLD = 2
        const val PLAYER_HURT = 3
        /** value — номер реакции (Reactions.*), подпись рисуется словом, а не числом. */
        const val REACTION = 4
    }
}

/** Разряд цепной молнии: ломаная по точкам. */
class Bolt {
    var straight = false
    var width = 11f
    val pts = FloatArray(MAX_POINTS * 2)
    var count = 0
    var life = 0f
    var maxLife = 0.22f
    var color = 0

    companion object {
        const val MAX_POINTS = 16
    }
}

/** Расходящееся кольцо (взрыв, волна, подбор). */
class Ring {
    var x = 0f
    var y = 0f
    var r0 = 0f
    var r1 = 0f
    var life = 0f
    var maxLife = 0.4f
    var color = 0
    var width = 6f
}
