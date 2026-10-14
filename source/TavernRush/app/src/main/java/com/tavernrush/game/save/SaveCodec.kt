package com.tavernrush.game.save

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.meta.Profile

/**
 * Текстовый формат сохранения (человекочитаемый, устойчивый к перестановке контента):
 * ```
 * TAVERNRUSH-SAVE
 * v=1
 * gold=120
 * up.supper=2
 * ...
 * sig=<HMAC-SHA256 всего, что выше>
 * ```
 * Улучшения хранятся по строковому id. Неизвестные ключи сохраняются в Profile.extra (совместимость
 * с будущими версиями). Значения вне допустимых диапазонов = подделка/порча → файл отвергается.
 */
object SaveCodec {
    const val MAGIC = "TAVERNRUSH-SAVE"
    const val VERSION = 1
    const val MAX_GOLD = 1_000_000_000_000L

    class Invalid(msg: String) : Exception(msg)

    private val reservedKeys = setOf(
        "v", "seq", "gold", "lifetimeGold", "runs", "victories", "bestTime", "bestKills", "bestLevel", "bestWave",
        "totalKills", "totalTime", "pendingRunGold", "installSeed", "runCounter", "sound", "vibration", "firstPurchaseRun", "sig",
        "nem.id", "nem.rank", "nem.wave", "nem.seed", "revenges", "rematch", "gfx", "stick", "stickSize", "dashRight", "dtap", "shake",
        "team.lead", "team.reserve", "team.mode", "controls.rev", "stickHeight", "stickResponse", "exp.state", "home.state", "companions.state", "morph.state"
    ) + Content.hubUpgrades.map { "up." + it.id } + Content.creatures.map { "fr." + it.id }

    fun encode(p: Profile): String {
        val sb = StringBuilder(512)
        sb.append(MAGIC).append('\n')
        kv(sb, "v", VERSION.toString())
        kv(sb, "seq", p.seq.toString())
        kv(sb, "gold", p.gold.toString())
        kv(sb, "lifetimeGold", p.lifetimeGold.toString())
        kv(sb, "runs", p.runs.toString())
        kv(sb, "victories", p.victories.toString())
        kv(sb, "bestTime", p.bestTime.toString())
        kv(sb, "bestKills", p.bestKills.toString())
        kv(sb, "bestLevel", p.bestLevel.toString())
        kv(sb, "bestWave", p.bestWave.toString())
        kv(sb, "totalKills", p.totalKills.toString())
        kv(sb, "totalTime", p.totalTime.toString())
        kv(sb, "pendingRunGold", p.pendingRunGold.toString())
        kv(sb, "installSeed", p.installSeed.toString())
        kv(sb, "runCounter", p.runCounter.toString())
        kv(sb, "sound", if (p.soundOn) "1" else "0")
        kv(sb, "vibration", if (p.vibrationOn) "1" else "0")
        kv(sb, "firstPurchaseRun", p.firstPurchaseRun.toString())
        kv(sb, "nem.id", p.nemesisId)
        kv(sb, "nem.rank", p.nemesisRank.toString())
        kv(sb, "nem.wave", p.nemesisWave.toString())
        kv(sb, "nem.seed", p.nemesisSeed.toString())
        kv(sb, "revenges", p.revenges.toString())
        kv(sb, "rematch", p.rematchBoss)
        kv(sb, "gfx", p.gfxPreset.toString())
        kv(sb, "controls.rev", p.controlsRevision.toString())
        kv(sb, "stickHeight", p.stickHeight.toString())
        kv(sb, "stickResponse", p.stickResponse.toString())
        kv(sb, "stick", p.stickMode.toString())
        kv(sb, "stickSize", p.stickSize.toString())
        kv(sb, "dashRight", if (p.dashRight) "1" else "0")
        kv(sb, "dtap", if (p.doubleTapDash) "1" else "0")
        kv(sb, "shake", p.shakePct.toString())
        kv(sb,"morph.state",p.morph.encode());kv(sb,"companions.state",p.companions.encode());kv(sb,"exp.state",p.adventure.encode());kv(sb,"home.state",p.home.encode())
        val ups = Content.hubUpgrades
        for (i in ups.indices) kv(sb, "up." + ups[i].id, p.upgrades[i].toString())
        kv(sb, "team.lead", p.teamLead)
        kv(sb, "team.reserve", p.teamReserve)
        kv(sb, "team.mode", p.teamMode.toString())
        for (f in p.friends) kv(sb, "fr." + f.id, listOf(f.nameIdx, f.trait, f.spec, f.metWave, f.metRun, f.bond, f.practice, f.revived).joinToString(","))
        for ((k, v) in p.extra) {
            if (k.isEmpty() || k in reservedKeys || k.any { it == '\n' || it == '\r' || it == '=' } || v.any { it == '\n' || it == '\r' })
                throw Invalid("недопустимое дополнительное поле")
            kv(sb, k, v)
        }
        val body = sb.toString()
        return body + "sig=" + Signer.sign(body) + "\n"
    }

    private fun kv(sb: StringBuilder, k: String, v: String) {
        sb.append(k).append('=').append(v).append('\n')
    }

    /** Разбор и проверка. Бросает Invalid при любой проблеме (формат, подпись, диапазоны). */
    fun decode(text: String): Profile {
        if (text.length > 262144) throw Invalid("слишком большой файл")
        val sigPos = text.lastIndexOf("\nsig=")
        if (!text.startsWith(MAGIC + "\n") || sigPos < 0) throw Invalid("нет заголовка или подписи")
        val body = text.substring(0, sigPos + 1)
        val sig = text.substring(sigPos + 5).trim()
        if (!Signer.verify(body, sig)) throw Invalid("подпись не совпадает")
        val map = LinkedHashMap<String, String>()
        val lines = body.split('\n')
        for (li in 1 until lines.size) {
            val line = lines[li]
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            if (eq <= 0) throw Invalid("строка без '='")
            val key = line.substring(0, eq)
            if (map.containsKey(key)) throw Invalid("повторяющееся поле")
            map[key] = line.substring(eq + 1)
        }
        val v = (map.remove("v") ?: throw Invalid("нет версии")).toIntOrNull() ?: throw Invalid("версия")
        if (v < 1) throw Invalid("версия $v")
        migrate(v, map)
        val p = Profile()
        p.seq = long(map, "seq", 0, Long.MAX_VALUE, 0)
        p.gold = long(map, "gold", 0, MAX_GOLD, 0)
        p.lifetimeGold = long(map, "lifetimeGold", 0, MAX_GOLD, 0)
        if (p.lifetimeGold < p.gold) throw Invalid("золота больше, чем заработано")
        p.runs = int(map, "runs", 0, 10_000_000, 0)
        p.victories = int(map, "victories", 0, p.runs, 0)
        p.bestTime = int(map, "bestTime", 0, 24 * 3600, 0)
        p.bestKills = int(map, "bestKills", 0, 10_000_000, 0)
        p.bestLevel = int(map, "bestLevel", 0, 10_000, 0)
        p.bestWave = int(map, "bestWave", 0, 10_000, 0)
        p.totalKills = long(map, "totalKills", 0, Long.MAX_VALUE / 4, 0)
        p.totalTime = long(map, "totalTime", 0, Long.MAX_VALUE / 4, 0)
        p.pendingRunGold = int(map, "pendingRunGold", 0, 1_000_000, 0)
        p.installSeed = long(map, "installSeed", Long.MIN_VALUE, Long.MAX_VALUE, 0)
        p.runCounter = long(map, "runCounter", 0, Long.MAX_VALUE / 4, 0)
        p.soundOn = int(map, "sound", 0, 1, 1) == 1
        p.vibrationOn = int(map, "vibration", 0, 1, 1) == 1
        p.firstPurchaseRun = int(map, "firstPurchaseRun", -1, 10_000_000, -1)
        p.nemesisId = id(map, "nem.id")
        p.nemesisRank = int(map, "nem.rank", 0, 9, 0)
        p.nemesisWave = int(map, "nem.wave", 0, 20, 0)
        p.nemesisSeed = int(map, "nem.seed", Int.MIN_VALUE, Int.MAX_VALUE, 0)
        p.revenges = int(map, "revenges", 0, 100_000, 0)
        p.rematchBoss = id(map, "rematch")
        p.gfxPreset = int(map, "gfx", 0, 4, 0)
        p.controlsRevision = int(map, "controls.rev", 0, 5, 0)
        p.stickHeight = int(map, "stickHeight", 0, 2, 1)
        p.stickResponse = int(map, "stickResponse", 0, 1, 1)
        p.stickMode = int(map, "stick", 0, 1, 1)
        p.stickSize = int(map, "stickSize", 70, 140, 100)
        p.dashRight = int(map, "dashRight", 0, 1, 1) == 1
        p.doubleTapDash = int(map, "dtap", 0, 1, 0) == 1
        if (p.controlsRevision < 5) {
            // Запрошенное новое управление применяется один раз, экономика и друзья не меняются.
            p.stickMode = 1; p.doubleTapDash = false; p.controlsRevision = 5
        }
        p.shakePct = int(map, "shake", 0, 100, 100)
        map.remove("exp.state")?.let{try{p.adventure.copyFrom(com.tavernrush.game.meta.AdventureProgress.decode(it))}catch(_:Exception){throw Invalid("exp.state: формат или диапазон")}}
        map.remove("home.state")?.let{try{p.home.copyFrom(com.tavernrush.game.meta.HomeProgress.decode(it));require(p.home.memories.all{m->m.animal==0||p.adventure.find(m.animal)!=null})}catch(_:Exception){throw Invalid("home.state: формат или диапазон")}}
        map.remove("companions.state")?.let{try{p.companions.copyFrom(com.tavernrush.game.meta.CompanionProgress.decode(it,p.adventure))}catch(_:Exception){throw Invalid("companions.state: формат или ссылки")}}
        map.remove("morph.state")?.let{try{p.morph.copyFrom(com.tavernrush.game.meta.MorphProgress.decode(it,p.adventure))}catch(_:Exception){throw Invalid("morph.state: формат или ссылки")}}
        if (p.nemesisId.isEmpty()) { p.nemesisRank = 0; p.nemesisWave = 0 }
        val ups = Content.hubUpgrades
        for (i in ups.indices) p.upgrades[i] = int(map, "up." + ups[i].id, 0, ups[i].maxLevel, 0)
        for (c in Content.creatures) friend(map, c)?.let { p.friends.add(it) }
        p.teamMode = int(map, "team.mode", 0, Creatures.MODES - 1, 0)
        p.teamLead = id(map, "team.lead")
        p.teamReserve = id(map, "team.reserve")
        // В команде могут быть только друзья, и каждый — не больше одного раза.
        for (t in listOf(p.teamLead, p.teamReserve)) if (t.isNotEmpty() && p.friend(t) == null) throw Invalid("team: нет такого друга")
        if (p.teamLead.isNotEmpty() && p.teamLead == p.teamReserve) throw Invalid("team: один друг дважды")
        // Всё, что не разобрали (будущие версии, удалённые улучшения) — бережно храним.
        for ((k, value) in map) if (k.isNotEmpty() && !k.contains('\n')) p.extra[k] = value
        return p
    }

    /** Миграции старых версий формата (v → VERSION). Пока единственная версия — 1. */
    private fun migrate(from: Int, map: MutableMap<String, String>) {
        if (from > VERSION) map["x.fromVersion"] = from.toString()
        // Пример для будущего: if (from < 2) { map["newKey"] = map.remove("oldKey") ?: "0" }
    }

    private fun long(map: MutableMap<String, String>, k: String, lo: Long, hi: Long, def: Long): Long {
        val s = map.remove(k) ?: return def
        val v = s.toLongOrNull() ?: throw Invalid("$k не число")
        if (v < lo || v > hi) throw Invalid("$k вне диапазона")
        return v
    }

    /** Строковый id вида (a–z, цифры, «_», до 24 знаков) или пусто: произвольный текст из подделанного файла отвергается. */
    private fun id(map: MutableMap<String, String>, k: String): String {
        val s = map.remove(k) ?: return ""
        if (s.length > 24 || s.any { !(it in 'a'..'z' || it in '0'..'9' || it == '_') }) throw Invalid("$k: недопустимый id")
        return s
    }

    /** Друг: «имя,характер,специализация,волна,забег,связь,практика,подъёмы», каждое число в своём диапазоне. */
    private fun friend(map: MutableMap<String, String>, c: com.tavernrush.game.content.CreatureDef): com.tavernrush.game.meta.Friend? {
        val s = map.remove("fr." + c.id) ?: return null
        val parts = s.split(',')
        if (parts.size != 8) throw Invalid("fr.${c.id}: формат")
        fun n(i: Int, lo: Int, hi: Int): Int {
            val v = parts[i].toIntOrNull() ?: throw Invalid("fr.${c.id}: не число")
            if (v < lo || v > hi) throw Invalid("fr.${c.id}: вне диапазона")
            return v
        }
        return com.tavernrush.game.meta.Friend(c.id).also {
            it.nameIdx = n(0, 0, c.names.size - 1)
            it.trait = n(1, 0, Creatures.traits.size - 1)
            it.spec = n(2, 0, 2)
            it.metWave = n(3, 1, 20)
            it.metRun = n(4, 0, 10_000_000)
            it.bond = n(5, 0, 10_000_000)
            it.practice = n(6, 0, 10_000_000)
            it.revived = n(7, 0, 10_000_000)
            if (it.spec != 0 && it.practice < Creatures.SPEC_PRACTICE) throw Invalid("fr.${c.id}: специализация без практики")
        }
    }

    private fun int(map: MutableMap<String, String>, k: String, lo: Int, hi: Int, def: Int): Int =
        long(map, k, lo.toLong(), hi.toLong(), def.toLong()).toInt()
}
