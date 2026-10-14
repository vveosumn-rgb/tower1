package com.tavernrush.game.systems

/** Статистика забега: для итогов, наград, метрик бот-симуляции и будущей валидации (античит). */
class RunStats {
    var time = 0f
    var kills = 0
    var elitesKilled = 0
    val killsPerMinute = IntArray(MAX_MINUTES)
    val damagePerMinute = FloatArray(MAX_MINUTES)
    var damageDealt = 0f
    var damageTaken = 0f
    /** Собранное золото (уже с бонусами). */
    var gold = 0f
    var xp = 0f
    var chests = 0
    var tavernStops = 0
    var maxAlive = 0
    var nearMisses = 0
    /** Момент, когда здоровье упало ниже порога near-miss (или -1). */
    var lowHpSince = -1f
    var minHpFrac = 1f
    var wave = 1
    var killerEnemy = -1
    var healed = 0f
    var dashes = 0
    var perfectDodges = 0
    var bossesSeen = 0
    var bossesKilled = 0
    var bossPhases = 0
    /** Побеждённые боссы по индексу Content.enemies (битовая маска, для коллекции и реванша). */
    var bossMask = 0L
    var nemesisSlain = false
    var nemesisSlainRank = 0
    /** Кто убил героя: именной ли враг, его ранг и сид имени, босс ли. */
    var killerNamed = false
    var killerRank = 0
    var killerNameSeed = 0
    var killerBoss = false
    val picks = IntArray(MAX_PICKS)
    var pickCount = 0
    /** Сколько раз сработала каждая реакция (индекс — Reactions.*): для итогов и проверок. */
    val reactions = IntArray(com.tavernrush.game.content.Reactions.COUNT)

    fun minute(): Int {
        val m = (time / 60f).toInt()
        return if (m >= MAX_MINUTES) MAX_MINUTES - 1 else if (m < 0) 0 else m
    }

    fun addDamage(v: Float) {
        damageDealt += v
        damagePerMinute[minute()] += v
    }

    fun addKill() {
        kills++
        killsPerMinute[minute()]++
    }

    fun recordPick(type: Int, index: Int) {
        if (pickCount < MAX_PICKS) picks[pickCount++] = type * 1000 + index
    }

    fun reset() {
        time = 0f; kills = 0; elitesKilled = 0
        killsPerMinute.fill(0); damagePerMinute.fill(0f)
        damageDealt = 0f; damageTaken = 0f; gold = 0f; xp = 0f
        chests = 0; tavernStops = 0; maxAlive = 0; nearMisses = 0
        lowHpSince = -1f; minHpFrac = 1f; wave = 1; killerEnemy = -1; healed = 0f
        dashes = 0; perfectDodges = 0; bossesSeen = 0; bossesKilled = 0; bossPhases = 0; bossMask = 0L
        nemesisSlain = false; nemesisSlainRank = 0; killerNamed = false; killerRank = 0; killerNameSeed = 0; killerBoss = false
        pickCount = 0
        reactions.fill(0)
    }

    companion object {
        const val MAX_MINUTES = 20
        const val MAX_PICKS = 200
    }
}
