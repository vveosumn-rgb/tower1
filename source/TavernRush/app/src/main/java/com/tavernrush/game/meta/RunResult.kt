package com.tavernrush.game.meta

import com.tavernrush.game.systems.World

/** Итоги забега для экрана результатов (переиспользуется, без аллокаций в игре). */
class RunResult {
    var valid = false
    /** Защита от повторного зачисления одного результата. */
    var applied = false
    var victory = false
    var time = 0f
    var kills = 0
    var elites = 0
    var level = 1
    var wave = 1
    var nearMisses = 0
    var killerEnemy = -1

    var goldCollected = 0
    var timeBonus = 0
    var killBonus = 0
    var waveBonus = 0
    var victoryBonus = 0
    /** Надбавка «Копилки трактирщика» к бонусам. */
    var piggyBonus = 0
    /** R03: награда за побеждённых боссов и за реванш над немезидой. */
    var bossBonus = 0
    var revengeBonus = 0
    var bossesKilled = 0
    var nemesisSlain = false
    var perfectDodges = 0
    var dashes = 0
    /** Имя новой/усиленной немезиды после этой гибели (пусто — нет) и название убийцы для экрана итогов. */
    var newNemesis = ""
    var killerLabel = ""
    /** R04: новый друг, найденный в этом забеге (id вида и имя; пусто — не было), реакции по видам. */
    var newFriendId = ""
    var newFriendName = ""
    val reactions = IntArray(com.tavernrush.game.content.Reactions.COUNT)
    var total = 0

    var newBestTime = false
    var newBestKills = false
    var prevBestTime = 0

    val weaponIdx = IntArray(World.MAX_WEAPONS)
    val weaponLevel = IntArray(World.MAX_WEAPONS)
    val weaponEvolution = IntArray(World.MAX_WEAPONS) { -1 }
    val weaponDamage = FloatArray(World.MAX_WEAPONS)
    var weaponCount = 0

    /** Ближайшая цель в таверне после зачисления награды: индекс улучшения и сколько не хватает. */
    var goalUpgrade = -1
    var goalMissing = 0
}
