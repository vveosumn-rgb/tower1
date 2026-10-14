package com.tavernrush.game.content

import com.tavernrush.game.content.WaveEventType.BOSS
import com.tavernrush.game.content.WaveEventType.ELITE
import com.tavernrush.game.content.WaveEventType.RING
import com.tavernrush.game.content.WaveEventType.SWARM

/**
 * Расписание главного забега: 10 волн по 60 с. Каждая волна — свой состав, темп и события.
 * R03: роли врагов вводятся постепенно (лучники → кабаны → подрывники → шаманы → призраки),
 * на 5-й и 10-й волнах — боссы с фазами. tavernAfter = «Привал в таверне» после волны.
 */
object Waves {
    private fun s(id: String, w: Int) = SpawnEntry(id, w)

    fun all(): Array<WaveDef> = arrayOf(
        WaveDef("Волна 1", "Крысы из подвала", 60f, rateStart = 1.6f, rateEnd = 3.2f, maxAlive = 70,
            hpMul = 1.0f, dmgMul = 1.0f, speedMul = 1.0f,
            pool = arrayOf(s("rat", 80), s("goblin", 20))),
        WaveDef("Волна 2", "Гоблины с тракта", 60f, 3.2f, 4.8f, 120, 1.35f, 1.05f, 1.02f,
            pool = arrayOf(s("rat", 35), s("goblin", 40), s("bat", 10), s("archer", 15)),
            events = arrayOf(WaveEvent(25f, RING, "rat", 16), WaveEvent(40f, ELITE, "goblin"))),
        WaveDef("Волна 3", "Ночной рой", 60f, 4.2f, 6.0f, 150, 1.8f, 1.1f, 1.04f,
            pool = arrayOf(s("rat", 25), s("goblin", 25), s("bat", 35), s("boar", 15)),
            events = arrayOf(WaveEvent(12f, SWARM, "bat", 18), WaveEvent(30f, ELITE, "boar"), WaveEvent(40f, SWARM, "bat", 22)),
            tavernAfter = true),
        WaveDef("Волна 4", "Слизь из колодца", 60f, 5.0f, 7.0f, 180, 2.4f, 1.2f, 1.07f,
            pool = arrayOf(s("goblin", 24), s("slime", 32), s("rat", 14), s("bomber", 18), s("archer", 12)),
            events = arrayOf(WaveEvent(15f, RING, "goblin", 20), WaveEvent(30f, ELITE, "slime"))),
        WaveDef("Волна 5", "Король Крыс", 60f, 3.6f, 4.8f, 150, 3.1f, 1.3f, 1.1f,
            pool = arrayOf(s("goblin", 28), s("slime", 14), s("bat", 18), s("orc", 8), s("spider", 14), s("shaman", 10), s("rat", 20)),
            events = arrayOf(WaveEvent(8f, BOSS, "rat_king"), WaveEvent(45f, ELITE, "orc"))),
        WaveDef("Волна 6", "Орочий дозор", 60f, 7.0f, 9.0f, 240, 4.3f, 1.4f, 1.13f,
            pool = arrayOf(s("orc", 18), s("goblin", 26), s("slime", 15), s("bat", 14), s("shaman", 10), s("boar", 10), s("ghost", 7)),
            events = arrayOf(WaveEvent(25f, ELITE, "orc"), WaveEvent(50f, SWARM, "bat", 26)),
            tavernAfter = true),
        WaveDef("Волна 7", "Великая стая", 60f, 8.0f, 10.0f, 270, 5.5f, 1.5f, 1.16f,
            pool = arrayOf(s("bat", 36), s("rat", 18), s("goblin", 18), s("ghost", 14), s("spider", 10), s("archer", 4)),
            events = arrayOf(WaveEvent(8f, SWARM, "bat", 24), WaveEvent(28f, SWARM, "bat", 28),
                WaveEvent(38f, ELITE, "bat"), WaveEvent(48f, SWARM, "bat", 32))),
        WaveDef("Волна 8", "Осада", 60f, 9.0f, 11.5f, 300, 6.8f, 1.6f, 1.19f,
            pool = arrayOf(s("orc", 20), s("slime", 18), s("goblin", 26), s("boar", 14), s("archer", 12), s("bomber", 10)),
            events = arrayOf(WaveEvent(20f, RING, "slime", 26), WaveEvent(35f, ELITE, "orc"), WaveEvent(50f, ELITE, "goblin"))),
        WaveDef("Волна 9", "Чёрная ночь", 60f, 10.0f, 13.0f, 330, 8.2f, 1.7f, 1.22f,
            pool = arrayOf(s("rat", 14), s("goblin", 18), s("bat", 14), s("slime", 14), s("orc", 12), s("ghost", 10), s("shaman", 8), s("bomber", 10)),
            events = arrayOf(WaveEvent(10f, RING, "goblin", 30), WaveEvent(30f, SWARM, "bat", 30),
                WaveEvent(40f, ELITE, "orc"), WaveEvent(52f, ELITE, "slime")),
            tavernAfter = true),
        WaveDef("Волна 10", "Последний рубеж", 60f, 5.5f, 7.5f, 220, 10.0f, 1.85f, 1.25f,
            pool = arrayOf(s("orc", 30), s("goblin", 28), s("slime", 22), s("archer", 12), s("boar", 8)),
            events = arrayOf(WaveEvent(6f, BOSS, "ogre_chef"), WaveEvent(30f, ELITE, "orc"),
                WaveEvent(40f, RING, "orc", 12), WaveEvent(50f, ELITE, "orc"))),
    )
}
