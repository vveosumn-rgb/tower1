package com.tavernrush.game.meta

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.CreatureDef
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.content.TraitDef

/**
 * Постоянный прогресс игрока. Хранится в save/ (подпись, бэкап, миграции).
 * Новые поля: добавить сюда + в SaveCodec (ключ, диапазон) — старые сохранения читаются со значением по умолчанию.
 */
class Profile {
    /** Счётчик записей: при восстановлении берётся самая свежая валидная копия. */
    val morph=MorphProgress()
    val companions=CompanionProgress()
    val home=HomeProgress()
    val adventure=AdventureProgress()
    var seq = 0L
    var gold = 0L
    var lifetimeGold = 0L
    var runs = 0
    var victories = 0
    var bestTime = 0
    var bestKills = 0
    var bestLevel = 0
    var bestWave = 0
    var totalKills = 0L
    var totalTime = 0L
    /** Уровни улучшений таверны по индексу Content.hubUpgrades. */
    val upgrades = IntArray(Content.hubUpgrades.size)
    /** Награда за прерванный забег (сворачивание/убийство процесса) — зачисляется при следующем запуске. */
    var pendingRunGold = 0
    /** Сколько золота зачислено за прерванный забег (показ в меню, не сохраняется). */
    var lastBankedGold = 0
    var installSeed = 0L
    var runCounter = 0L
    var soundOn = true
    var vibrationOn = true
    /** Номер забега, после которого куплено первое улучшение (−1 — ещё нет). Метрика «время до первой разблокировки». */
    var firstPurchaseRun = -1

    // ---- R03: немезида и реванш
    /** id вида, убившего героя в прошлый раз (пусто — немезиды нет), её ранг, волна гибели и сид имени. */
    var nemesisId = ""
    var nemesisRank = 0
    var nemesisWave = 0
    var nemesisSeed = 0
    /** Сколько раз игрок отомстил немезиде. */
    var revenges = 0
    /** Босс, убивший героя: гарантированно вернётся в своём слоте. */
    var rematchBoss = ""

    // ---- R04: друзья-существа и команда
    /** Друзья таверны: не больше одного на вид (Content.creatures). */
    val friends = ArrayList<Friend>()
    /** id ведущего и резервного друга; режим команды (Creatures.MODE_*). */
    var teamLead = ""
    var teamReserve = ""
    var teamMode = 0

    // ---- Настройки графики и управления
    /** GfxPreset.ordinal (0 — авто). */
    var gfxPreset = 0
    /** 0 — плавающий стик, 1 — фиксированный. */
    var stickMode = 1
    /** R05: одноразовая миграция эргономики; новые настройки не перезаписываются при загрузке. */
    var controlsRevision = 5
    /** 0 — ниже, 1 — обычная, 2 — выше. Движение всегда слева. */
    var stickHeight = 1
    /** 0 — мягкий, 1 — прямой аналоговый отклик. */
    var stickResponse = 1
    var stickSize = 100
    /** Кнопка рывка справа (true) или слева. */
    var dashRight = true
    var doubleTapDash = false
    /** Сила тряски экрана, %. */
    var shakePct = 100
    /** Неизвестные ключи из более новых версий — сохраняются как есть. */
    val extra = LinkedHashMap<String, String>()

    fun level(upgradeIndex: Int): Int = upgrades[upgradeIndex]

    fun copyFrom(o: Profile) {
        morph.copyFrom(o.morph)
        companions.copyFrom(o.companions)
        home.copyFrom(o.home)
        adventure.copyFrom(o.adventure)
        seq = o.seq; gold = o.gold; lifetimeGold = o.lifetimeGold; runs = o.runs; victories = o.victories
        bestTime = o.bestTime; bestKills = o.bestKills; bestLevel = o.bestLevel; bestWave = o.bestWave
        totalKills = o.totalKills; totalTime = o.totalTime
        System.arraycopy(o.upgrades, 0, upgrades, 0, upgrades.size)
        pendingRunGold = o.pendingRunGold; lastBankedGold = o.lastBankedGold
        installSeed = o.installSeed; runCounter = o.runCounter
        soundOn = o.soundOn; vibrationOn = o.vibrationOn; firstPurchaseRun = o.firstPurchaseRun
        nemesisId = o.nemesisId; nemesisRank = o.nemesisRank; nemesisWave = o.nemesisWave; nemesisSeed = o.nemesisSeed
        revenges = o.revenges; rematchBoss = o.rematchBoss
        gfxPreset = o.gfxPreset; stickMode = o.stickMode; stickSize = o.stickSize; dashRight = o.dashRight
        doubleTapDash = o.doubleTapDash; shakePct = o.shakePct
        controlsRevision = o.controlsRevision; stickHeight = o.stickHeight; stickResponse = o.stickResponse
        friends.clear(); for (f in o.friends) friends.add(f.copy())
        teamLead = o.teamLead; teamReserve = o.teamReserve; teamMode = o.teamMode
        extra.clear(); extra.putAll(o.extra)
    }

    fun friend(id: String): Friend? { for (f in friends) if (f.id == id) return f; return null }

    /** Назначить ведущего. Если этот друг был резервным — они меняются местами. */
    fun setLead(id: String): Boolean {
        if (friend(id) == null || id == teamLead) return false
        if (id == teamReserve) teamReserve = teamLead
        teamLead = id
        return true
    }

    /** Назначить резерв. Если этот друг был ведущим — они меняются местами. */
    fun setReserve(id: String): Boolean {
        if (friend(id) == null || id == teamReserve) return false
        if (id == teamLead) teamLead = teamReserve
        teamReserve = id
        return true
    }

    fun clearReserve() { teamReserve = "" }

    /** Выбор специализации (боковая ветка, не «лучшая»): после практики и один раз. */
    fun chooseSpec(id: String, spec: Int): Boolean {
        val f = friend(id) ?: return false
        if (spec !in 1..2 || f.spec != 0 || f.practice < Creatures.SPEC_PRACTICE) return false
        f.spec = spec
        return true
    }

    /**
     * Новый друг. Возвращает false, если такой вид уже есть или данные неверны. Первый друг становится
     * ведущим, второй — резервным: команда собирается сама, менять можно на экране друзей.
     */
    fun befriend(f: Friend): Boolean {
        if (Content.creatureIndex(f.id) < 0 || friend(f.id) != null) return false
        friends.add(f)
        if (teamLead.isEmpty()) teamLead = f.id else if (teamReserve.isEmpty()) teamReserve = f.id
        return true
    }
}

/**
 * Личная история одного друга: кто он, как познакомились и что вы делали вместе. Всё из реальных событий
 * забегов, а не из таймеров: отсутствие игрока ничего не отнимает.
 */
class Friend(val id: String) {
    /** Индекс имени в CreatureDef.names, индекс характера в Creatures.traits, специализация (0 — ещё нет). */
    var nameIdx = 0
    var trait = 0
    var spec = 0
    /** Волна и номер забега знакомства. */
    var metWave = 1
    var metRun = 0
    /** Забегов вместе (≥ 45 с в бою), реакций, вызванных его способностью, и сколько раз герой его поднимал. */
    var bond = 0
    var practice = 0
    var revived = 0

    fun copy(): Friend = Friend(id).also {
        it.nameIdx = nameIdx; it.trait = trait; it.spec = spec; it.metWave = metWave; it.metRun = metRun
        it.bond = bond; it.practice = practice; it.revived = revived
    }

    val def: CreatureDef get() = Content.creatures[Content.creatureIndex(id).coerceAtLeast(0)]
    val name: String get() = def.names[nameIdx.coerceIn(0, def.names.size - 1)]
    val traitDef: TraitDef get() = Creatures.traits[trait.coerceIn(0, Creatures.traits.size - 1)]
}
