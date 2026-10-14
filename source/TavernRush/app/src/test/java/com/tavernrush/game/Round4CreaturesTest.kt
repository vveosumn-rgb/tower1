package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.core.Game
import com.tavernrush.game.core.GameEvents
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Mate
import com.tavernrush.game.meta.Friend
import com.tavernrush.game.meta.Profile
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.save.SaveCodec
import com.tavernrush.game.save.Signer
import com.tavernrush.game.systems.CompanionSystem
import com.tavernrush.game.systems.EncounterSystem
import com.tavernrush.game.systems.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** R04, часть 2: существа-друзья, команда, встреча, сохранение. Логика без Android. */
class Round4CreaturesTest {
    private val dt = 1f / 60f

    private fun world(seed: Long = 505L): World = World().also { it.reset(seed, null); it.godMode = true; it.weaponCount = 0 }

    private fun step(w: World, keep: Collection<Enemy>) {
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e !in keep) e.alive = false }
        w.tick(dt)
    }

    private fun spawn(w: World, id: String, x: Float, y: Float): Enemy {
        val e = w.spawnEnemy(Content.enemyIndex(id), x, y, false)!!
        e.speed = 0f
        return e
    }

    private fun friend(id: String, trait: Int = 0, spec: Int = 0, practice: Int = if (spec != 0) 6 else 0) =
        Friend(id).also { it.trait = trait; it.spec = spec; it.practice = practice }

    private fun team(w: World, lead: String?, reserve: String? = null, mode: Int = Creatures.MODE_SOLO, trait: Int = 0, spec: Int = 0) {
        w.team.setup(lead?.let { friend(it, trait, spec) }, reserve?.let { friend(it, trait) }, mode)
    }

    private fun run(w: World, sec: Float, keep: Collection<Enemy> = emptyList()) { for (k in 0 until (sec * 60f).toInt()) step(w, keep) }

    private class Counter : GameEvents {
        val sfx = IntArray(Sfx.COUNT)
        override fun sfx(id: Int) { if (id in sfx.indices) sfx[id]++ }
    }

    // ------------------------------------------------------------ данные

    @Test fun creatureContentIsConsistent() {
        assertEquals(5, Content.creatures.size)
        assertEquals(4,Content.creatures.count{it.wild})
        assertFalse(Content.creatures[4].wild)
        assertEquals(CompanionSystem.S_MOSS,Content.creatureIndex("moss"))
        assertEquals(CompanionSystem.S_STREAM, Content.creatureIndex("stream"))
        assertEquals(CompanionSystem.S_LYNX, Content.creatureIndex("lynx"))
        assertEquals(CompanionSystem.S_HORN, Content.creatureIndex("horn"))
        assertEquals(Element.WATER, Content.creatures[0].element)
        assertEquals(Element.SHOCK, Content.creatures[1].element)
        assertEquals(Element.HEAVY, Content.creatures[2].element)
        for (c in Content.creatures) {
            assertTrue(c.names.size >= 4)
            assertEquals(2, c.specs.size)
            assertTrue(c.name.isNotEmpty() && c.meeting.isNotEmpty() && c.orderLabel.isNotEmpty())
            for (t in c.tormentors) assertTrue(Content.enemyIndex(t) >= 0)
        }
        assertEquals(3, Creatures.traits.size)
        assertEquals(Creatures.MODES, Creatures.modeNames.size)
        assertEquals(Creatures.MODES, Creatures.modeHints.size)
    }

    // ------------------------------------------------------------ профиль и сохранение

    @Test fun firstTwoFriendsFormTheTeamAndEachSpeciesJoinsOnce() {
        val p = Profile()
        assertTrue(p.befriend(friend("lynx")))
        assertTrue(p.befriend(friend("horn")))
        assertTrue(p.befriend(friend("stream")))
        assertEquals("lynx", p.teamLead); assertEquals("horn", p.teamReserve)
        assertFalse("тот же вид второй раз", p.befriend(friend("lynx")))
        assertFalse("неизвестный вид", p.befriend(friend("dragon")))
        assertEquals(3, p.friends.size)
    }

    @Test fun friendsAndTeamSurviveASaveRoundTrip() {
        val p = Profile()
        p.befriend(friend("stream", trait = 1).also { it.nameIdx = 3; it.metWave = 4; it.metRun = 9; it.bond = 2; it.practice = 11; it.revived = 1; it.spec = 2 })
        p.befriend(friend("horn", trait = 2))
        p.teamMode = Creatures.MODE_SWAP
        val back = SaveCodec.decode(SaveCodec.encode(p))
        assertEquals(2, back.friends.size)
        val s = back.friend("stream")!!
        assertEquals(3, s.nameIdx); assertEquals(1, s.trait); assertEquals(2, s.spec); assertEquals(4, s.metWave)
        assertEquals(9, s.metRun); assertEquals(2, s.bond); assertEquals(11, s.practice); assertEquals(1, s.revived)
        assertEquals("stream", back.teamLead); assertEquals("horn", back.teamReserve); assertEquals(Creatures.MODE_SWAP, back.teamMode)
        assertEquals("Булька".isNotEmpty(), s.name.isNotEmpty())
    }

    private fun expectInvalid(what: String, p: Profile) {
        try { SaveCodec.decode(SaveCodec.encode(p)); fail("подделка принята: $what") } catch (e: SaveCodec.Invalid) { /* ожидаем */ }
    }

    @Test fun forgedFriendAndTeamFieldsAreRejected() {
        fun base() = Profile().also { it.befriend(friend("stream")); it.befriend(friend("lynx")) }
        expectInvalid("имя вне списка", base().also { it.friend("stream")!!.nameIdx = 99 })
        expectInvalid("характер вне списка", base().also { it.friend("lynx")!!.trait = 7 })
        expectInvalid("специализация без практики", base().also { it.friend("lynx")!!.spec = 1; it.friend("lynx")!!.practice = 2 })
        expectInvalid("специализация 3", base().also { it.friend("lynx")!!.spec = 3; it.friend("lynx")!!.practice = 50 })
        expectInvalid("волна 0", base().also { it.friend("lynx")!!.metWave = 0 })
        expectInvalid("отрицательная связь", base().also { it.friend("lynx")!!.bond = -1 })
        expectInvalid("режим 9", base().also { it.teamMode = 9 })
        expectInvalid("в команде не друг", base().also { it.teamLead = "horn" })
        expectInvalid("друг дважды", base().also { it.teamLead = "lynx"; it.teamReserve = "lynx" })
        expectInvalid("id с мусором", base().also { it.teamReserve = "Lynx!" })
    }

    @Test fun savesWithoutFriendsStillLoadWithDefaults() {
        val full = SaveCodec.encode(Profile())
        val body = full.substringBefore("sig=").lines().filter { !it.startsWith("team.") && !it.startsWith("fr.") }.joinToString("\n")
        val old = body.trimEnd('\n') + "\n"
        val p = SaveCodec.decode(old + "sig=" + Signer.sign(old) + "\n")
        assertEquals(0, p.friends.size); assertEquals("", p.teamLead); assertEquals(Creatures.MODE_SOLO, p.teamMode)
    }

    // ------------------------------------------------------------ команда

    @Test fun modesBuildDifferentTeams() {
        val w = world()
        team(w, "stream", "lynx", Creatures.MODE_SOLO)
        assertEquals(1, w.team.count); assertTrue(w.team.hasOrder); assertFalse(w.team.canSwap)
        team(w, "stream", "lynx", Creatures.MODE_SWAP)
        assertEquals(2, w.team.count); assertTrue(w.team.canSwap); assertFalse(w.team.hasOrder)
        assertTrue(w.team.lead()!!.active); assertFalse(w.team.reserve()!!.active)
        team(w, "stream", "lynx", Creatures.MODE_DUO)
        assertEquals(2, w.team.count); assertTrue(w.team.lead()!!.active); assertTrue(w.team.reserve()!!.active)
        assertFalse(w.team.canSwap); assertFalse(w.team.hasOrder)
        team(w, null)
        assertEquals(0, w.team.count)
        team(w, "stream", "stream", Creatures.MODE_DUO)
        assertEquals("один и тот же друг дважды в команду не попадает", 1, w.team.count)
    }

    @Test fun swapExchangesTheActiveMateWithACooldownAndRefusesADownedReserve() {
        val w = world()
        team(w, "stream", "horn", Creatures.MODE_SWAP)
        val a = w.team.lead()!!; val b = w.team.reserve()!!
        assertTrue(w.team.swapReady())
        assertTrue(w.team.swap())
        assertFalse(a.active); assertTrue(b.active); assertEquals(Mate.ENTER, b.state)
        assertFalse("перезарядка", w.team.swap())
        run(w, CompanionSystem.SWAP_CD + 0.2f)
        b.state = Mate.FOLLOW
        a.state = Mate.DOWNED
        assertFalse("в лежачего не сменить", w.team.swapReady())
        a.state = Mate.FOLLOW
        assertTrue(w.team.swap())
        assertTrue(a.active); assertFalse(b.active)
    }

    @Test fun orderWorksOnlyInSoloAndHasACooldown() {
        val w = world()
        team(w, "stream", "horn", Creatures.MODE_SWAP)
        assertFalse(w.team.order())
        team(w, "stream", null, Creatures.MODE_SOLO)
        val e = spawn(w, "goblin", 90f, 0f)
        run(w, 0.2f, listOf(e))
        assertTrue(w.team.order())
        assertFalse("перезарядка приказа", w.team.order())
        assertTrue(w.team.orderCd > 10f)
        assertTrue("приказ облил врага", e.wetT > 0f)
    }

    @Test fun guestJoinsByModeRules() {
        val w = world()
        team(w, null, null, Creatures.MODE_SOLO)
        assertTrue(w.team.addGuest(friend("stream")))
        assertTrue(w.team.lead()!!.active)
        assertFalse("в режиме «один» второму места нет", w.team.addGuest(friend("lynx")))
        team(w, "stream", null, Creatures.MODE_DUO)
        assertTrue(w.team.addGuest(friend("lynx")))
        assertTrue(w.team.reserve()!!.active)
        assertFalse("третьего нет", w.team.addGuest(friend("horn")))
        team(w, "stream", null, Creatures.MODE_SWAP)
        assertTrue(w.team.addGuest(friend("horn")))
        assertFalse("в «смене» гость — резерв", w.team.reserve()!!.active)
    }

    // ------------------------------------------------------------ Ручьеглот

    @Test fun streamWetsDryEnemiesInALineAndSavesTheShotWhenAllAreWet() {
        val w = world()
        val ev = Counter(); w.events = ev
        team(w, "stream")
        val es = listOf(spawn(w, "goblin", 120f, 0f), spawn(w, "goblin", 150f, 25f), spawn(w, "goblin", 170f, -20f))
        run(w, 0.5f, es)
        assertTrue("струя облила всех на линии", es.all { it.wetT > 0f })
        val shots = ev.sfx[Sfx.SPLASH]
        assertTrue(shots >= 1)
        run(w, 3.0f, es.onEach { it.wetT = 3f })
        assertEquals("все мокрые — не тратит струю", shots, ev.sfx[Sfx.SPLASH])
    }

    @Test fun streamPutsOutABombersFuse() {
        val w = world()
        team(w, "stream")
        val b = spawn(w, "bomber", 120f, 0f)
        b.state = 1; b.windup = 0.6f; b.stateT = 0.5f      // запал уже горит, и бомбы ещё сухая
        run(w, 0.4f, listOf(b))
        assertEquals("запал потушен", 0, b.state)
        assertTrue(b.wetT > 0f)
    }

    @Test fun aWetBomberCannotLightItsFuseUntilItDries() {
        val w = world()
        val b = spawn(w, "bomber", 60f, 0f)
        b.wetT = 3f
        var litAt = -1f
        var t = 0f
        while (t < 6f && litAt < 0f) { step(w, listOf(b)); t += dt; if (b.alive && b.state == 1) litAt = t }
        assertTrue("высох — загорелся: $litAt", litAt > 0f)
        assertTrue("пока был мокрым, не горел: $litAt", litAt >= 2.9f)
    }

    @Test fun streamSpecializationsChangeTheShot() {
        val widths = HashMap<Int, Int>()
        for (spec in 0..2) {
            val w = world()
            team(w, "stream", spec = spec)
            val side = ArrayList<Enemy>()
            for (k in 0 until 8) side.add(spawn(w, "goblin", 130f + k * 6f, -70f + k * 20f))   // «веер» шириной 140
            run(w, 0.4f, side)
            widths[spec] = side.count { it.wetT > 0f }
        }
        assertTrue("широкий поток мочит больше, чем тонкая струя: $widths", widths[1]!! > widths[2]!!)
    }

    // ------------------------------------------------------------ Грозорыс

    @Test fun lynxChargesFromTheHeroTravelNotFromStandingStill() {
        val w = world()
        team(w, "lynx")
        run(w, 2f)
        val idle = w.team.lead()!!.charge
        w.moveX = 1f
        run(w, 2f)
        val moved = w.team.lead()!!.charge
        assertTrue("стоя почти не копит: $idle", idle < 0.15f)
        assertTrue("бегом копит: $moved", moved > idle + 0.25f)
    }

    @Test fun lynxLeapsOnTheWetClusterAndConductionCostsItAWindedPause() {
        val w = world()
        team(w, "lynx")
        val dry = spawn(w, "goblin", 90f, 0f)
        val cluster = ArrayList<Enemy>()
        for (k in 0 until 5) cluster.add(spawn(w, "goblin", 250f + (k % 3) * 24f, -20f + (k / 3) * 30f).also { it.wetT = 4f })
        val all = cluster + dry
        val m = w.team.lead()!!
        m.charge = 1f
        run(w, 0.05f, all)
        var landed = false; var pause = 0f
        for (k in 0 until 120) {
            for (e in cluster) if (e.alive && e.wetT < 1f) e.wetT = 1f
            step(w, all)
            if (m.state == Mate.RECOVER && !landed) { landed = true; pause = m.stateT }
        }
        assertTrue("прыгнул и приземлился", landed)
        assertTrue("разряд прошёл по облитым", w.run.reactions[Reactions.CONDUCT] >= 1)
        assertEquals("сухой сосед не пострадал", dry.maxHp, dry.hp, 0.001f)
        assertTrue("после проводимости отдышка длиннее: $pause", pause > 1.5f)
        assertTrue("практика засчитана", m.practice >= 1)
    }

    @Test fun lynxOnADryTargetRecoversFaster() {
        val w = world()
        team(w, "lynx")
        val t = spawn(w, "goblin", 180f, 0f)
        val m = w.team.lead()!!
        m.charge = 1f
        var pause = 0f
        for (k in 0 until 80) { step(w, listOf(t)); if (m.state == Mate.RECOVER && pause == 0f) pause = m.stateT }
        assertTrue("короткая отдышка: $pause", pause in 0.5f..1.2f)
        assertEquals("заряд потрачен", 0f, m.charge, 0.05f)
    }

    @Test fun lynxOrderNeedsSomeCharge() {
        val w = world()
        team(w, "lynx")
        val t = spawn(w, "goblin", 150f, 0f)
        run(w, 0.1f, listOf(t))
        assertFalse(w.team.orderReady())
        w.team.lead()!!.charge = 0.5f
        assertTrue(w.team.orderReady())
        assertTrue(w.team.order())
        assertEquals(Mate.ACT, w.team.lead()!!.state)
    }

    @Test fun lynxSpecializationsTradeArcsForDamage() {
        fun arcsHit(spec: Int): Int {
            val w = world()
            team(w, "lynx", spec = spec)
            val c = ArrayList<Enemy>()
            c.add(spawn(w, "orc", 200f, 0f))
            for (k in 0 until 6) c.add(spawn(w, "goblin", 215f + (k % 3) * 22f, -45f + (k / 3) * 90f))
            for (e in c) e.wetT = 4f
            val hp = c.map { it.hp }
            w.team.lead()!!.charge = 1f
            for (k in 0 until 70) step(w, c)
            return c.indices.count { c[it].hp < hp[it] }
        }
        val precise = arcsHit(1); val plain = arcsHit(0); val chain = arcsHit(2)
        assertTrue("цепной задевает больше, чем обычный; точный меньше: $precise/$plain/$chain", chain > plain && plain > precise)
    }

    // ------------------------------------------------------------ Камнерог

    @Test fun hornAbsorbsThreeHitsThenTheHeroIsHurt() {
        val w = world()
        w.godMode = false
        team(w, "horn")
        val hp0 = w.player.hp
        val taken = ArrayList<Float>()
        for (hit in 0 until 4) {
            val e = spawn(w, "orc", w.player.x + 2f, w.player.y)
            val before = w.player.hp
            for (k in 0 until 6) step(w, listOf(e))      // один контакт: щит гасит и отбрасывает врага
            taken.add(before - w.player.hp)
            e.alive = false
            for (k in 0 until 40) step(w, emptyList())   // неуязвимость героя проходит
        }
        assertEquals("три удара подряд погашены", 0f, taken[0] + taken[1] + taken[2], 0.001f)
        assertTrue("четвёртый проходит: ${taken[3]}", taken[3] > 0f)
        assertTrue(w.player.hp < hp0)
    }

    @Test fun hornCountersWithAStunningBashAndShieldsComeBackSlowly() {
        val w = world()
        w.godMode = false
        team(w, "horn")
        val m = w.team.lead()!!
        val e = spawn(w, "orc", w.player.x + 2f, w.player.y)
        for (k in 0 until 20) { e.x = w.player.x + 2f; e.y = w.player.y; step(w, listOf(e)) }
        assertEquals(2, m.guard)
        assertTrue("контрудар отбросил и оглушил", e.stun > 0f && e.hp < e.maxHp)
        for (k in 0 until (CompanionSystem.GUARD_REGEN * 60f).toInt() + 30) step(w, emptyList())
        assertEquals("щит вернулся не сразу, а через ${CompanionSystem.GUARD_REGEN} с", 3, m.guard)
    }

    @Test fun hornShieldsIgnoreHitsWhenTheMateIsFarAway() {
        val w = world()
        w.godMode = false
        team(w, "horn")
        val m = w.team.lead()!!
        m.x = w.player.x + 600f; m.y = w.player.y
        val e = spawn(w, "orc", w.player.x + 2f, w.player.y)
        val hp = w.player.hp
        m.vx = 0f
        for (k in 0 until 3) { e.x = w.player.x + 2f; e.y = w.player.y; w.combat.hurtPlayer(10f, 0, false, 0, 0, 0f, 1f, false) }
        assertTrue("далёкий щит не защищает", w.player.hp < hp)
    }

    @Test fun hornSpecializationsChangeShieldsAndBash() {
        val w = world()
        team(w, "horn", spec = 1)
        assertEquals("«Заслон»: четыре щита", 4, w.team.lead()!!.guard)
        team(w, "horn", spec = 0)
        assertEquals(3, w.team.lead()!!.guard)
    }

    @Test fun hornStompsWhenSurrounded() {
        val w = world()
        team(w, "horn")
        val m = w.team.lead()!!
        val es = ArrayList<Enemy>()
        for (k in 0 until 4) es.add(spawn(w, "rat", m.x + 40f * (k % 2 + 1), m.y + 30f * (k / 2) - 15f).also { it.damage = 0f })
        val hp = es.map { it.hp }
        for (k in 0 until 40) step(w, es)
        assertTrue("топот задел окруживших", es.indices.count { es[it].hp < hp[it] } >= 3)
    }

    // ------------------------------------------------------------ потеря сил и подъём

    @Test fun aMateIsKnockedOutNotKilledAndStandsAgainFasterWithTheHeroNear() {
        fun downTime(heroNear: Boolean): Float {
            val w = world()
            team(w, "stream")
            val m = w.team.lead()!!
            m.hp = 1f
            val brute = spawn(w, "orc", m.x, m.y)
            brute.damage = 50f
            step(w, listOf(brute))
            assertEquals(Mate.DOWNED, m.state)
            brute.alive = false
            var t = 0f
            while (m.state == Mate.DOWNED && t < 20f) {
                if (!heroNear) { w.player.x = m.x + 800f; w.player.y = m.y } else { w.player.x = m.x + 30f; w.player.y = m.y }
                step(w, emptyList()); t += dt
                m.hp = 0f
            }
            assertEquals(Mate.FOLLOW, m.state)
            return t
        }
        val near = downTime(true); val far = downTime(false)
        assertTrue("рядом с героем встаёт быстрее: $near vs $far", near < far * 0.6f)
        assertTrue("сам встаёт за ${CompanionSystem.REVIVE_TIME} с", far in 6f..10f)
    }

    @Test fun aRevivedMateIsCountedAndAReserveRestsBackToHealth() {
        val w = world()
        team(w, "stream", "horn", Creatures.MODE_SWAP)
        val r = w.team.reserve()!!
        r.hp = 10f
        run(w, 5f)
        assertTrue("резерв отдыхает и восстанавливается: ${r.hp}", r.hp > 25f)
        val m = w.team.lead()!!
        m.hp = 1f
        val brute = spawn(w, "orc", m.x, m.y); brute.damage = 80f
        step(w, listOf(brute))
        brute.alive = false
        w.player.x = m.x + 20f; w.player.y = m.y
        for (k in 0 until 60 * 4) { w.player.x = m.x + 20f; w.player.y = m.y; step(w, emptyList()); if (m.state != Mate.DOWNED) break }
        assertEquals(1, m.revived)
    }

    @Test fun reactionsOnWaterFromTheStreamEarnItPractice() {
        val w = world()
        team(w, "stream")
        val m = w.team.lead()!!
        val t = spawn(w, "goblin", 120f, 0f)
        run(w, 0.4f, listOf(t))
        assertTrue(t.wetT > 0f)
        assertEquals(CompanionSystem.S_STREAM + 1, t.wetBy)
        // Оружие героя бьёт мокрого разрядом — практика у того, кто его облил
        w.combat.hitEnemy(t, 5f, t.x, t.y, 0f, -1, true, Element.SHOCK, true)
        assertTrue("реакция на воде Ручьеглота — его практика", m.practice >= 1)
    }

    // ------------------------------------------------------------ встреча

    private fun armed(seed: Long, unmet: Int = 7): World = world(seed).also { it.encounter.arm(unmet, seed) }

    /** Идёт игровое время, пока встреча не начнётся: режиссёр сам дойдёт до нужной волны. */
    private fun untilStart(w: World, maxSec: Float = 400f): Boolean {
        var t = 0f
        while (t < maxSec && w.encounter.state == EncounterSystem.NONE) {
            for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false }
            w.tick(dt); t += dt
        }
        return w.encounter.state == EncounterSystem.ACTIVE
    }

    @Test fun noEncounterWhenEveryoneIsAlreadyAFriend() {
        val w = armed(11L, 0)
        assertFalse(untilStart(w, 200f))
        assertEquals(-1, w.encounter.species)
    }

    @Test fun anEncounterPicksOnlyUnmetSpeciesAndIsDeterministicPerSeed() {
        val only = 1 shl CompanionSystem.S_LYNX
        val w = armed(21L, only)
        assertEquals(CompanionSystem.S_LYNX, w.encounter.species)
        val a = armed(33L); val b = armed(33L)
        assertEquals(a.encounter.species, b.encounter.species)
        assertEquals(a.encounter.nameIdx, b.encounter.nameIdx)
        val seen = HashSet<Int>()
        for (s in 1L..40L) seen.add(armed(s).encounter.species)
        assertEquals("разные забеги встречают разные виды", 3, seen.size)
    }

    @Test fun theSiteHasHuntersThatChaseTheCreatureWhileTheHeroIsFar() {
        val w = armed(31L)
        assertTrue(untilStart(w))
        val enc = w.encounter
        var hunters = 0
        for (i in 0 until w.enemyCount) if (w.enemies[i].alive && w.enemies[i].huntSite) hunters++
        assertTrue("обидчики есть: $hunters", hunters >= 7)
        val hp = enc.hp
        val gap = Math.hypot((w.player.x - enc.x).toDouble(), (w.player.y - enc.y).toDouble())
        assertTrue("до места далеко: $gap", gap > 380.0)
        for (k in 0 until 60 * 8) { for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false }; w.tick(dt) }
        assertTrue("пока героя нет, существу достаётся: ${enc.hp} < $hp", enc.hp < hp)
        assertTrue("но не мгновенно: у героя есть время дойти (${enc.hp})", enc.hp > hp * 0.3f)
    }

    @Test fun ifTheHeroNeverComesTheCreatureRunsAwayWithoutLosingTheSpeciesForever() {
        val w = armed(41L)
        assertTrue(untilStart(w))
        var t = 0f
        while (w.encounter.state == EncounterSystem.ACTIVE && t < 90f) { for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false }; w.player.x = 0f; w.player.y = 0f; w.tick(dt); t += dt }
        assertEquals(EncounterSystem.FLED, w.encounter.state)
        assertNull("ничего не записано", w.encounter.joined)
        for (i in 0 until w.enemyCount) assertFalse("обидчики отпущены", w.enemies[i].alive && w.enemies[i].huntSite)
        assertTrue("вид снова может встретиться", armed(41L).encounter.species >= 0)
    }

    @Test fun huntersLeaveTheCreatureAndChaseTheHeroWhoComes() {
        val w = armed(51L)
        assertTrue(untilStart(w))
        val enc = w.encounter
        w.player.x = enc.x - 180f; w.player.y = enc.y
        val far = EncounterSystem.AGGRO + 60f
        var chasing = 0
        for (k in 0 until 60 * 2) { w.tick(dt) }
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e.huntSite) { val d = Math.hypot((e.x - w.player.x).toDouble(), (e.y - w.player.y).toDouble()); if (d < far + 200) chasing++ } }
        assertTrue("обидчики пошли к герою: $chasing", chasing >= 3)
    }

    @Test fun trustGrowsFullSpeedInCalmSlowerWithEnemiesInTheAreaAndSlowestWithOneNextToTheCreature() {
        val w = armed(61L)
        assertTrue(untilStart(w))
        val enc = w.encounter
        fun sit(seconds: Float, enemyAt: Float?): Float {
            val before = enc.trust
            var keepE: Enemy? = null
            for (k in 0 until (seconds * 60f).toInt()) {
                for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e !== keepE) e.alive = false }
                if (enemyAt != null && (keepE == null || !keepE.alive)) {
                    keepE = spawn(w, "rat", enc.x + enemyAt, enc.y); keepE.damage = 0f
                }
                if (keepE != null) { keepE.x = enc.x + enemyAt!!; keepE.y = enc.y; keepE.speed = 0f }
                w.player.x = enc.siteX - 100f; w.player.y = enc.siteY
                w.tick(dt)
            }
            return enc.trust - before
        }
        val withEnemyNext = sit(2f, 50f)
        assertTrue("враг вплотную к существу: растёт медленно, 0,4 ($withEnemyNext)", withEnemyNext in 0.06f..0.1f)
        val withEnemyInArea = sit(2f, 160f)
        assertTrue("враг в области, но не рядом: 0,75 ($withEnemyInArea)", withEnemyInArea in 0.12f..0.18f)
        val calm = sit(2f, null)
        assertTrue("в спокойствии растёт полностью ($calm)", calm in 0.17f..0.23f)
        // Ушёл из кольца — медленно остывает
        val before = enc.trust
        for (k in 0 until 60 * 3) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; w.player.x = enc.siteX - 600f; w.player.y = enc.siteY; w.tick(dt) }
        assertTrue("остывает медленно: $before → ${enc.trust}", enc.trust < before && enc.trust > before - 0.2f)
    }

    @Test fun theRingIsTiedToTheSiteNotToTheCreatureThatWalksToTheHero() {
        val w = armed(62L)
        assertTrue(untilStart(w))
        val enc = w.encounter
        // Доверие набираем до первого шага, и существо подходит к герою; затем герой отходит дальше кольца от места
        for (k in 0 until 60 * 5) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; w.player.x = enc.siteX - 120f; w.player.y = enc.siteY; w.tick(dt) }
        assertTrue(enc.stage >= 1)
        val t0 = enc.trust
        for (k in 0 until 60 * 2) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; w.player.x = enc.siteX - EncounterSystem.RING_R - 60f; w.player.y = enc.siteY; w.tick(dt) }
        assertTrue("вне нарисованного кольца доверие не растёт, даже если существо рядом: $t0 → ${enc.trust}", enc.trust <= t0)
    }

    @Test fun trustMakesTheCreatureApproachInThreeStepsAndThenItJoinsTheTeam() {
        val w = armed(71L, 1 shl CompanionSystem.S_HORN)
        assertTrue(untilStart(w))
        val enc = w.encounter
        val stages = HashSet<Int>()
        val d0 = Math.hypot((w.player.x - enc.x).toDouble(), (w.player.y - enc.y).toDouble())
        var t = 0f
        while (enc.state == EncounterSystem.ACTIVE && t < 40f) {
            for (i in 0 until w.enemyCount) w.enemies[i].alive = false
            w.player.x = enc.siteX - 120f; w.player.y = enc.siteY
            w.tick(dt); t += dt
            stages.add(enc.stage)
        }
        assertEquals("три шага: подошёл ближе, ещё ближе, рядом", setOf(0, 1, 2), stages)
        assertEquals(EncounterSystem.JOINED, enc.state)
        assertTrue("присоединился к команде", w.team.find(CompanionSystem.S_HORN)?.active == true)
        val f = enc.takeJoin()
        assertNotNull(f); assertEquals("horn", f!!.id)
        assertNull("забирается один раз", enc.takeJoin())
        assertTrue("шёл к герою: $d0", d0 > 200.0)
    }

    @Test fun gameRecordsAMetFriendRightAwayAndOnlyOnce() {
        val store = MemoryStore()
        val g = Game(store).also { it.fixedSeed = 99L }
        g.startRun()
        val w = g.world
        w.godMode = true
        var t = 0f
        while (w.encounter.state == EncounterSystem.NONE && t < 400f) {
            for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false }
            if (g.screen == ScreenId.CARD_PICK) g.pickCard(0)
            g.tick(dt); t += dt
        }
        assertEquals(EncounterSystem.ACTIVE, w.encounter.state)
        val species = Content.creatures[w.encounter.species].id
        t = 0f
        while (w.encounter.state == EncounterSystem.ACTIVE && t < 60f) {
            for (i in 0 until w.enemyCount) w.enemies[i].alive = false
            w.player.x = w.encounter.siteX - 100f; w.player.y = w.encounter.siteY
            if (g.screen == ScreenId.CARD_PICK) g.pickCard(0)
            g.tick(dt); t += dt
        }
        val f = g.profile.friend(species)
        assertNotNull("друг записан в профиль сразу, до конца забега", f)
        assertEquals(species, g.result.newFriendId)
        assertEquals(species, g.profile.teamLead)
        assertEquals("записан и в хранилище", species, store.load().teamLead)
        assertEquals(1, g.profile.friends.size)
        assertTrue(f!!.metWave in 2..3)
        // следующий забег этого вида уже не ищет
        g.abandonRun(); g.startRun()
        assertTrue(g.world.encounter.species != Content.creatureIndex(species) || g.world.encounter.species < 0)
        assertEquals("друг уже в команде", 1, g.world.team.count)
    }

    @Test fun finishingARunBanksBondPracticeAndRevivesIntoTheProfile() {
        val g = Game(MemoryStore()).also { it.fixedSeed = 5L }
        g.profile.befriend(friend("stream"))
        g.startRun()
        val m = g.world.team.lead()!!
        m.activeTime = 50f; m.practice = 4; m.revived = 2
        g.abandonRun()
        val f = g.profile.friend("stream")!!
        assertEquals(1, f.bond); assertEquals(4, f.practice); assertEquals(2, f.revived)
        // не засчитывается дважды
        g.openMenu(); g.startRun(); g.abandonRun()
        assertEquals(4, g.profile.friend("stream")!!.practice)
    }

    @Test fun teamRunsAreDeterministic() {
        fun sim(): String {
            val w = world(808L)
            w.team.setup(friend("stream"), friend("lynx"), Creatures.MODE_DUO)
            val es = ArrayList<Enemy>()
            for (i in 0 until 14) es.add(spawn(w, "goblin", 90f + (i % 5) * 26f, -60f + (i / 5) * 40f))
            w.moveX = 1f
            for (k in 0 until 60 * 10) { for (e in es) if (e.alive) { e.hp = e.maxHp; e.speed = 0f }; step(w, es); if (k == 300) w.moveX = 0f }
            return w.run.reactions.joinToString(",") + "|" + w.run.damageDealt.toInt() + "|" + w.team.mates[0]!!.practice + "|" + w.team.mates[1]!!.practice
        }
        assertEquals(sim(), sim())
    }

    // ------------------------------------------------------------ команда: правила и экран

    @Test fun teamRolesSwapPlacesAndNeverDuplicate() {
        val p = Profile()
        p.befriend(friend("stream")); p.befriend(friend("lynx")); p.befriend(friend("horn"))
        assertEquals("stream", p.teamLead); assertEquals("lynx", p.teamReserve)
        assertTrue(p.setLead("lynx"))
        assertEquals("lynx", p.teamLead); assertEquals("stream", p.teamReserve)
        assertFalse("тот же ведущий — ничего", p.setLead("lynx"))
        assertTrue(p.setReserve("horn"))
        assertEquals("horn", p.teamReserve); assertEquals("lynx", p.teamLead)
        assertTrue(p.setReserve("lynx"))
        assertEquals("lynx", p.teamReserve); assertEquals("horn", p.teamLead)
        assertFalse("чужой вид", p.setLead("dragon"))
        p.clearReserve()
        assertEquals("", p.teamReserve)
    }

    @Test fun specializationIsChosenOnceAndOnlyAfterPractice() {
        val p = Profile()
        p.befriend(friend("stream", practice = 2))
        assertFalse(p.chooseSpec("stream", 1))
        p.friend("stream")!!.practice = Creatures.SPEC_PRACTICE
        assertFalse("ветки только 1 и 2", p.chooseSpec("stream", 3))
        assertFalse(p.chooseSpec("stream", 0))
        assertTrue(p.chooseSpec("stream", 2))
        assertFalse("второй раз нельзя", p.chooseSpec("stream", 1))
        assertEquals(2, p.friend("stream")!!.spec)
    }

    @Test fun teamEditsArePossibleOnlyOutsideARunAndArePersisted() {
        val store = MemoryStore()
        val g = Game(store)
        g.profile.befriend(friend("stream")); g.profile.befriend(friend("horn"))
        assertTrue(g.setTeamMode(Creatures.MODE_DUO))
        assertTrue(g.setTeamRole("horn", 0))
        assertEquals("horn", store.load().teamLead)
        assertEquals(Creatures.MODE_DUO, store.load().teamMode)
        assertFalse(g.setTeamMode(7))
        assertFalse("несуществующий друг", g.setTeamRole("lynx", 0))
        g.startRun()
        assertFalse("посреди боя состав не меняют", g.setTeamMode(Creatures.MODE_SOLO))
        assertFalse(g.setTeamRole("stream", 0))
        g.abandonRun(); g.openMenu()
        assertTrue(g.setTeamMode(Creatures.MODE_SOLO))
        // правила кнопки «Резерв»: повторное нажатие снимает
        assertTrue(g.setTeamRole("horn", 1).not() || g.profile.teamReserve.isEmpty() || g.profile.teamReserve == "horn")
    }

    @Test fun duoModeStartsBothMatesInARunAndSwapModeOnlyTheLead() {
        val g = Game(MemoryStore()).also { it.fixedSeed = 3L }
        g.profile.befriend(friend("stream")); g.profile.befriend(friend("lynx"))
        g.profile.teamMode = Creatures.MODE_DUO
        g.startRun()
        assertEquals(2, g.world.team.count)
        assertTrue(g.world.team.mates[0]!!.active && g.world.team.mates[1]!!.active)
        g.abandonRun(); g.openMenu()
        g.profile.teamMode = Creatures.MODE_SWAP
        g.startRun()
        assertTrue(g.world.team.mates[0]!!.active); assertFalse(g.world.team.mates[1]!!.active)
    }

    // ------------------------------------------------------------ бот и друзья

    @Test fun theBotCallsAnOrderInACrowdAndSwapsOutAFallenLead() {
        val w = world()
        team(w, "stream", null, Creatures.MODE_SOLO)
        val bot = com.tavernrush.game.systems.BotPilot(com.tavernrush.game.systems.BotPilot.KITE, com.tavernrush.game.systems.BotPilot.GREEDY, 5L)
        val crowd = ArrayList<Enemy>()
        for (i in 0 until 8) crowd.add(spawn(w, "rat", 70f + (i % 4) * 25f, -40f + (i / 4) * 50f))
        for (k in 0 until 40) { bot.steer(w); step(w, crowd) }
        assertTrue("в толпе бот отдал приказ", w.team.orderCd > 0f)

        val w2 = world()
        team(w2, "stream", "horn", Creatures.MODE_SWAP)
        w2.team.mates[0]!!.state = Mate.DOWNED
        for (k in 0 until 40) { bot.steer(w2); step(w2, emptyList()) }
        assertTrue("выбитого ведущего бот сменил", w2.team.mates[1]!!.active && !w2.team.mates[0]!!.active)
    }

    private fun botMakesAFriend(strategy: Int, seed: Long): Pair<Boolean, Float> {
        val g = Game(MemoryStore()).also { it.fixedSeed = seed }
        g.startRun()
        val w = g.world
        w.godMode = true
        val bot = com.tavernrush.game.systems.BotPilot(strategy, com.tavernrush.game.systems.BotPilot.GREEDY, 9L)
        var t = 0f
        while (g.profile.friends.isEmpty() && t < 300f && w.encounter.state != EncounterSystem.FLED && w.encounter.state != EncounterSystem.LEFT) {
            if (g.screen == ScreenId.CARD_PICK) g.pickCard(bot.pickCard(w)) else bot.steer(w)
            g.tick(dt); t += dt
        }
        return Pair(g.profile.friends.size == 1, t)
    }

    /**
     * Бот-прокси без подсказок: идёт на зов, держится у существа, отбивается и знакомится. Это проверка, что встреча
     * проходима автоматическим игроком, а не оценка сложности для людей (в произвольных сидах бот успевает не всегда).
     */
    @Test fun theBotAnswersTheCallAndMakesAFriendOnItsOwn() {
        val casual = botMakesAFriend(com.tavernrush.game.systems.BotPilot.CASUAL, 1234L)
        assertTrue("беспечный бот подружился (за ${casual.second.toInt()} с)", casual.first)
        val kite = botMakesAFriend(com.tavernrush.game.systems.BotPilot.KITE, 21L)
        assertTrue("осторожный бот подружился (за ${kite.second.toInt()} с)", kite.first)
    }

    @Test fun botRunsWithAFullTeamAreDeterministic() {
        fun sim(): String {
            val g = Game(MemoryStore()).also { it.fixedSeed = 77L }
            g.profile.befriend(friend("lynx")); g.profile.befriend(friend("horn"))
            g.profile.teamMode = Creatures.MODE_DUO
            g.startRun()
            val w = g.world
            val bot = com.tavernrush.game.systems.BotPilot(com.tavernrush.game.systems.BotPilot.KITE, com.tavernrush.game.systems.BotPilot.GREEDY, 4L)
            for (k in 0 until 60 * 90) {
                if (g.screen == ScreenId.CARD_PICK) g.pickCard(bot.pickCard(w)) else if (g.screen == ScreenId.RUN) bot.steer(w)
                g.tick(dt)
                if (g.screen == ScreenId.RESULTS) break
            }
            return "${w.run.kills}|${w.run.damageDealt.toInt()}|${w.run.reactions.joinToString(",")}|${w.team.mates[0]!!.practice}|${w.team.mates[1]!!.guard}"
        }
        assertEquals(sim(), sim())
    }

    @Test fun anIgnoredEncounterExpiresSoftlyAndFreesTheHunters() {
        // нужен забег, где встреча начинается на второй волне: иначе её раньше срока закончит босс пятой
        var w: World? = null
        for (seed in 81L..140L) { val c = armed(seed); if (untilStart(c) && c.director.waveIndex == 1) { w = c; break } }
        w!!
        val enc = w.encounter
        var t = 0f
        while (enc.state == EncounterSystem.ACTIVE && t < 200f) {
            for (i in 0 until w.enemyCount) w.enemies[i].alive = false
            w.player.x = enc.siteX - 900f; w.player.y = enc.siteY
            w.tick(dt); t += dt
        }
        assertEquals(EncounterSystem.LEFT, enc.state)
        assertEquals(EncounterSystem.OUT_LEFT, enc.outcome)
        assertTrue("уходит не раньше срока, но и не висит вечно: $t", t in (EncounterSystem.LIFETIME - 1f)..(EncounterSystem.LIFETIME + 2f))
        assertNull(enc.joined)
        for (i in 0 until w.enemyCount) assertFalse(w.enemies[i].alive && w.enemies[i].huntSite)
    }

    @Test fun theEncounterWarnsBeforeLeavingAndABossEndsItAtOnce() {
        val w = armed(82L)
        assertTrue(untilStart(w))
        val enc = w.encounter
        assertTrue(enc.remaining > EncounterSystem.LIFETIME - 2f)
        for (k in 0 until 60 * 95) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; w.player.x = enc.siteX - 900f; w.player.y = enc.siteY; w.tick(dt) }
        assertTrue("за ${EncounterSystem.WARN_TIME} с до ухода remaining падает ниже порога: ${enc.remaining}", enc.remaining < EncounterSystem.WARN_TIME)
        val w2 = armed(83L)
        assertTrue(untilStart(w2))
        w2.spawnBoss(Content.enemyIndex("rat_king"))
        w2.tick(dt)
        assertEquals(EncounterSystem.LEFT, w2.encounter.state)
    }
}
