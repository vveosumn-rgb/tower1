package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.EnemyLook
import com.tavernrush.game.core.Game
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Hazard
import com.tavernrush.game.entities.Pickup
import com.tavernrush.game.entities.Player
import com.tavernrush.game.meta.Profile
import com.tavernrush.game.render.pixel.BakeOpts
import com.tavernrush.game.render.pixel.GfxGovernor
import com.tavernrush.game.render.pixel.GfxPreset
import com.tavernrush.game.render.pixel.ModelBaker
import com.tavernrush.game.render.pixel.ModelLibrary
import com.tavernrush.game.render.pixel.models.HeroModels
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.save.SaveCodec
import com.tavernrush.game.save.Signer
import com.tavernrush.game.systems.BossSystem
import com.tavernrush.game.systems.BotPilot
import com.tavernrush.game.systems.EnemyAI
import com.tavernrush.game.systems.Nemesis
import com.tavernrush.game.systems.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R03: роли врагов, телеграфы, боссы с фазами, немезида, рывок и «идеальный уклон», сохранения, бот и графика.
 * Мир гоняется без Android: врагов режиссёра убираем перед каждым шагом, чтобы проверять роль в чистом виде.
 */
class Round3Test {
    private val dt = 1f / 60f

    private fun world(seed: Long = 303L): World = World().also { it.reset(seed, null) }

    /** Шаг мира, в котором живёт только [keep] (режиссёр спавнит крыс — они мешали бы измерениям). */
    private fun step(w: World, keep: Enemy?) {
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e !== keep && !e.summoned && e.def.id != "slimelet") e.alive = false }
        w.tick(dt)
    }

    private fun spawn(w: World, id: String, x: Float, y: Float, elite: Boolean = false): Enemy {
        val e = w.spawnEnemy(Content.enemyIndex(id), x, y, elite)!!
        return e
    }

    // ------------------------------------------------------------ роли врагов

    @Test fun archerAimsThenFiresAnArrowThatHurts() {
        val w = world()
        val a = spawn(w, "archer", 250f, 0f)
        a.cd = 0f
        var aimed = false; var shot = false
        var guard = 0
        while (w.player.hp >= w.player.maxHp && guard++ < 900) {
            step(w, a)
            if (a.state == 1) aimed = true
            if (w.shotCount > 0) shot = true
        }
        assertTrue("лучник сначала прицеливается", aimed)
        assertTrue("лучник выпускает снаряд", shot)
        assertTrue("стрела попадает в стоящего героя", w.player.hp < w.player.maxHp)
    }

    @Test fun spiderWebSlowsTheHero() {
        val w = world()
        val s = spawn(w, "spider", 230f, 0f)
        s.cd = 0f
        var guard = 0
        while (w.player.slowT <= 0f && guard++ < 900) step(w, s)
        assertTrue("паутина замедляет героя", w.player.slowT > 0f && w.player.slowMul < 1f)
        val before = w.player.x
        w.moveX = 1f
        repeat(30) { step(w, null) }
        assertTrue("замедленный герой бежит медленнее", w.player.x - before < World.BASE_SPEED * 0.5f * 0.62f + 8f)
    }

    @Test fun boarTelegraphsChargesAndIsStunnedAndTakesMoreDamage() {
        val w = world()
        w.godMode = true
        val b = spawn(w, "boar", 320f, 0f)
        b.cd = 0f
        val seen = arrayListOf(b.state)
        var guard = 0
        while (guard++ < 1200 && seen.last() != 3) {
            step(w, b)
            if (seen.last() != b.state) seen.add(b.state)
        }
        assertEquals("замах → рывок → оглушение", listOf(0, 1, 2, 3), seen.takeLast(4))
        assertTrue(b.stun > 0f)
        val hp0 = b.hp
        w.combat.hitEnemy(b, 10f, 0f, 0f, 0f, -1)
        assertEquals("по оглушённому урон ×1,5", 15f, hp0 - b.hp, 0.01f)
    }

    @Test fun bomberCanBeDefusedBeforeTheBlast() {
        val w = world()
        val b = spawn(w, "bomber", 70f, 0f)
        var guard = 0
        while (b.state != 1 && guard++ < 300) step(w, b)
        assertEquals(1, b.state)
        assertTrue("есть круг поражения", w.hazardCount > 0)
        val hp = w.player.hp
        w.combat.hitEnemy(b, 9999f, 0f, 0f, 0f, -1)
        repeat(150) { step(w, null) }
        assertEquals("убитый до взрыва подрывник безвреден", hp, w.player.hp, 0.001f)
        assertEquals(0, w.hazardCount)
    }

    @Test fun bomberDetonatesAndHurtsAnInactiveHero() {
        val w = world()
        val b = spawn(w, "bomber", 60f, 0f)
        val uid0 = b.uid
        val hp = w.player.hp
        var guard = 0
        while (w.player.hp >= hp && guard++ < 400) step(w, b)
        assertTrue("взрыв ранит героя в круге", w.player.hp < hp)
        repeat(3) { step(w, b) }
        // слот пула могли занять новым врагом — сверяем по uid
        assertTrue("подрывник исчезает после взрыва", !b.alive || b.uid != uid0)
    }

    @Test fun slimeSplitsAndSummonerKeepsAtMostSixRats() {
        val w = world()
        val s = spawn(w, "slime", 200f, 200f)
        w.combat.hitEnemy(s, 9999f, 0f, 0f, 0f, -1)
        var kids = 0
        for (i in 0 until w.enemyCount) if (w.enemies[i].alive && w.enemies[i].def.id == "slimelet") kids++
        assertEquals(2, kids)

        val w2 = world()
        val sh = spawn(w2, "shaman", 300f, 0f)
        w2.godMode = true
        repeat(60 * 40) {
            sh.cd = minOf(sh.cd, 0.05f)
            step(w2, sh)
            var n = 0
            for (i in 0 until w2.enemyCount) { val q = w2.enemies[i]; if (q.alive && q.summoned) n++ }
            assertTrue("призывов не больше ${EnemyAI.MAX_SUMMONS}: $n", n <= EnemyAI.MAX_SUMMONS)
        }
        var rats = 0
        for (i in 0 until w2.enemyCount) if (w2.enemies[i].alive && w2.enemies[i].summoned) rats++
        assertTrue("шаман призывает крыс", rats > 0)
    }

    @Test fun ghostVanishesWithAWarningAndReappearsElsewhere() {
        val w = world()
        w.godMode = true
        val g = spawn(w, "ghost", 220f, 0f)
        g.cd = 0f
        var vanished = false; var warned = false
        var x0 = 0f; var y0 = 0f
        var guard = 0
        while (guard++ < 400) {
            val ox = g.x; val oy = g.y
            step(w, g)
            if (g.state == 1 && !vanished) {
                vanished = true; x0 = ox; y0 = oy
                warned = w.hazardCount > 0
                assertTrue("пока исчезает — неуязвим", g.invulnT > 0f)
            }
            if (vanished && g.state == 2) break
        }
        assertTrue(vanished); assertTrue("место появления помечено", warned)
        assertEquals("появился ровно в помеченной точке", g.tx, g.x, 0.5f)
        assertEquals(g.ty, g.y, 0.5f)
        assertTrue("и это не то место, где исчез", Math.hypot((g.x - x0).toDouble(), (g.y - y0).toDouble()) > 5.0)
    }

    @Test fun eliteAffixesApply() {
        val w = world()
        val seen = HashSet<Int>()
        for (i in 0 until 120) {
            val e = w.spawnEnemy(Content.enemyIndex("goblin"), 800f, 800f + i, true) ?: break
            seen.add(e.affix)
            if (e.affix == World.AFFIX_SHIELDED) assertTrue(e.shield > 0f)
            e.alive = false
        }
        assertTrue("все пять аффиксов встречаются: $seen", seen.containsAll(listOf(1, 2, 3, 4, 5)))
    }

    // ------------------------------------------------------------ рывок

    @Test fun dashSpendsAndRestoresChargesAndRejectsBadInput() {
        val w = world()
        assertFalse(w.dash(Float.NaN, 0f))
        assertEquals(Player.MAX_DASH, w.player.dashCharges)
        assertTrue(w.dash(1f, 0f))
        assertEquals(Player.MAX_DASH - 1, w.player.dashCharges)
        assertFalse("нельзя во время рывка", w.dash(1f, 0f))
        val x0 = w.player.x
        repeat(20) { step(w, null) }
        assertTrue("рывок смещает героя (${w.player.x - x0})", w.player.x - x0 > 100f)
        assertTrue(w.dash(0f, 1f))
        repeat(20) { step(w, null) }
        assertFalse("зарядов нет", w.dash(1f, 0f))
        repeat((World.DASH_RECHARGE * 60f).toInt() + 5) { step(w, null) }
        assertTrue("заряд восстановился", w.player.dashCharges >= 1)
        repeat(60 * 6) { step(w, null) }
        assertEquals("не больше максимума", Player.MAX_DASH, w.player.dashCharges)
    }

    @Test fun dashGivesInvulnerabilityWindowAndPerfectDodgeRewardsOnce() {
        val w = world()
        val hp = w.player.hp
        assertTrue(w.dash(1f, 0f))
        val res = w.combat.hurtPlayer(20f, 0, false, 0, 0, 0f, 1f, true)
        assertEquals("в окне рывка не ранит", 2, res)
        assertEquals(hp, w.player.hp, 0.001f)
        assertEquals(1, w.run.perfectDodges)
        assertTrue("замедление времени", w.slowmoT > 0f)
        assertEquals("заряд возвращён", Player.MAX_DASH, w.player.dashCharges)
        w.combat.hurtPlayer(20f, 0, false, 0, 0, 0f, 1f, true)
        assertEquals("повторно в течение 1,1 с не засчитывается", 1, w.run.perfectDodges)
        repeat(70) { step(w, null) }
        val res2 = w.combat.hurtPlayer(20f, 0, false, 0, 0, 0f, 1f, true)
        assertEquals("после окна — обычный урон", 1, res2)
        assertTrue(w.player.hp < hp)
    }

    @Test fun ordinaryContactDuringDashIsNotAPerfectDodge() {
        val w = world()
        w.dash(1f, 0f)
        w.combat.hurtPlayer(10f, 0, false, 0, 0, 0f, 1f, false)
        assertEquals(0, w.run.perfectDodges)
    }

    @Test fun hitStopFreezesLogicButNeverForever() {
        val w = world()
        val e = spawn(w, "rat", 400f, 0f)
        w.requestHitStop(5f)
        val t0 = w.time
        repeat(4) { step(w, e) }
        assertEquals("мир замер", t0, w.time, 0f)
        repeat(30) { step(w, e) }
        assertTrue("хит-стоп ограничен", w.time > t0)
    }

    // ------------------------------------------------------------ боссы

    private fun bossWorld(id: String): Pair<World, Enemy> {
        val w = world(909L)
        w.godMode = true
        val b = w.spawnBoss(Content.enemyIndex(id))!!
        return Pair(w, b)
    }

    @Test fun everyBossIsInvulnerableOnEntranceThenChangesPhasesAtItsThresholds() {
        for (id in listOf("rat_king", "ogre_chef", "banshee")) {
            val (w, b) = bossWorld(id)
            val hp0 = b.hp
            w.combat.hitEnemy(b, 500f, 0f, 0f, 0f, -1)
            assertEquals("$id: на входе неуязвим", hp0, b.hp, 0.001f)
            var guard = 0
            while (b.state == BossSystem.INTRO && guard++ < 300) step(w, b)
            assertEquals(0, b.stage)
            b.hp = b.maxHp * 0.5f
            repeat(3) { step(w, b) }
            if (id == "ogre_chef") assertEquals("$id: фаза 2 на 55%", 1, b.stage) else assertEquals("$id: фаза 2 на 62%", 1, b.stage)
            assertTrue("смена фазы даёт паузу с бронёй", b.invulnT > 0f || b.state == BossSystem.PHASE)
            repeat(90) { step(w, b) }
            b.hp = b.maxHp * 0.2f
            repeat(3) { step(w, b) }
            assertEquals("$id: фаза 3", 2, b.stage)
            assertTrue("фазы засчитаны", w.run.bossPhases >= 2)
        }
    }

    @Test fun bossesUseDifferentAttackKitsAndTheyAllTelegraph() {
        val kits = HashMap<String, MutableSet<Int>>()
        for (id in listOf("rat_king", "ogre_chef", "banshee")) {
            val (w, b) = bossWorld(id)
            val acts = HashSet<Int>()
            var telegraphs = 0
            for (t in 0 until 60 * 70) {
                b.hp = b.maxHp * (if (t < 60 * 22) 0.99f else if (t < 60 * 40) 0.5f else 0.2f)
                step(w, b)
                if (b.state == BossSystem.TELE) acts.add(b.act)
                if (w.hazardCount > 0 || w.shotCount > 0 || b.state == BossSystem.TELE) telegraphs++
            }
            kits[id] = acts
            assertTrue("$id: хотя бы 3 разные атаки, было $acts", acts.size >= 3)
            assertTrue("$id: атаки видны (телеграф/снаряды)", telegraphs > 60)
        }
        assertNotEquals("наборы атак боссов различаются", kits["rat_king"], kits["ogre_chef"])
        assertNotEquals(kits["ogre_chef"], kits["banshee"])
    }

    @Test fun killingABossPaysOutAndClearsItsAttacks() {
        val (w, b) = bossWorld("ogre_chef")
        var guard = 0
        while (b.state == BossSystem.INTRO && guard++ < 300) step(w, b)
        repeat(60 * 9) { step(w, b); if (b.state != BossSystem.TELE) b.cd = 0f }
        w.spawnHazard(Hazard.CIRCLE, 50f, 50f, 40f, 5f, 10f, 0xFFFF0000.toInt(), ownerDef = b.def.index)
        w.combat.hitEnemy(b, 1e9f, 0f, 0f, 0f, -1)
        assertFalse(b.alive)
        assertEquals(1, w.run.bossesKilled)
        var chests = 0
        for (i in 0 until w.pickupCount) if (w.pickups[i].kind == Pickup.CHEST) chests++
        assertTrue("два сундука", chests >= 2)
        repeat(12) { step(w, null) }   // хит-стоп (≤ 0,12 с) не даёт убрать зоны мгновенно
        assertEquals("атаки убитого босса сняты", 0, w.hazardCount)
        assertTrue("слоу-мо на смерти босса", w.slowmoT > 0f || w.hitStop > 0f)
    }

    // ------------------------------------------------------------ немезида и реванш

    @Test fun nemesisNamesAreDeterministicAndEscalateWithRank() {
        assertEquals(Nemesis.name(1234, 1), Nemesis.name(1234, 1))
        assertNotEquals(Nemesis.name(1234, 1), Nemesis.name(1234, 3))
        assertTrue(Nemesis.fullName("rat", 7, 2).contains("Подвальная крыса"))
        val names = HashSet<String>()
        for (s in 0 until 64) names.add(Nemesis.name(s * 7919, 1).substringBefore(' '))
        assertTrue("разных имён достаточно", names.size >= 8)
    }

    @Test fun killerBecomesNemesisAndEscalatesAndRevengeResetsIt() {
        val p = Profile()
        val w = world()
        w.godMode = false
        val g = spawn(w, "goblin", 20f, 0f)
        g.damage = 9999f
        w.combat.damagePlayer(9999f, g)
        assertTrue(w.player.dead)
        Nemesis.onRunEnd(p, w.run, false, 6, 555L)
        assertEquals("goblin", p.nemesisId); assertEquals(1, p.nemesisRank); assertEquals(6, p.nemesisWave)
        val seed1 = p.nemesisSeed

        // герой снова погиб от неё — ранг растёт, имя прежнее
        val w2 = world(); w2.setNemesis(p.nemesisId, p.nemesisRank, p.nemesisWave, p.nemesisSeed)
        val n = w2.spawnNemesis()!!
        assertTrue(n.named); assertEquals(1, n.rank)
        assertTrue("именная крупнее и сильнее", n.radius > Content.enemies[n.def.index].radius * 1.5f && n.maxHp > Content.enemies[n.def.index].hp * 10f)
        w2.combat.damagePlayer(9999f, n)
        Nemesis.onRunEnd(p, w2.run, false, 7, 556L)
        assertEquals(2, p.nemesisRank); assertEquals(seed1, p.nemesisSeed)

        // реванш: убил именную — немезида исчезает, счётчик растёт
        val w3 = world(); w3.godMode = true; w3.setNemesis(p.nemesisId, p.nemesisRank, p.nemesisWave, p.nemesisSeed)
        val n3 = w3.spawnNemesis()!!
        w3.combat.hitEnemy(n3, 1e9f, 0f, 0f, 0f, -1)
        assertTrue(w3.run.nemesisSlain)
        Nemesis.onRunEnd(p, w3.run, true, 10, 557L)
        assertEquals("", p.nemesisId); assertEquals(1, p.revenges)
    }

    @Test fun bossKillerReturnsAsRematchAndIsClearedWhenDefeated() {
        val p = Profile()
        val w = world(); w.godMode = false
        val b = w.spawnBoss(Content.enemyIndex("banshee"))!!
        b.invulnT = 0f
        w.combat.damagePlayer(9999f, b)
        Nemesis.onRunEnd(p, w.run, false, 5, 1L)
        assertEquals("banshee", p.rematchBoss)
        assertEquals("босс не становится немезидой", "", p.nemesisId)
        val w2 = world(); w2.godMode = true
        val b2 = w2.spawnBoss(Content.enemyIndex("banshee"))!!
        b2.invulnT = 0f
        w2.combat.hitEnemy(b2, 1e9f, 0f, 0f, 0f, -1)
        Nemesis.onRunEnd(p, w2.run, true, 10, 2L)
        assertEquals("", p.rematchBoss)
    }

    @Test fun nemesisAndSettingsSurviveSaveAndOldSavesGetDefaults() {
        val p = Profile()
        p.nemesisId = "rat"; p.nemesisRank = 3; p.nemesisWave = 8; p.nemesisSeed = -12345; p.revenges = 4; p.rematchBoss = "ogre_chef"
        p.gfxPreset = 2; p.stickMode = 1; p.stickSize = 120; p.dashRight = false; p.doubleTapDash = false; p.shakePct = 40
        val q = SaveCodec.decode(SaveCodec.encode(p))
        assertEquals("rat", q.nemesisId); assertEquals(3, q.nemesisRank); assertEquals(8, q.nemesisWave); assertEquals(-12345, q.nemesisSeed)
        assertEquals(4, q.revenges); assertEquals("ogre_chef", q.rematchBoss)
        assertEquals(2, q.gfxPreset); assertEquals(1, q.stickMode); assertEquals(120, q.stickSize); assertFalse(q.dashRight); assertFalse(q.doubleTapDash); assertEquals(40, q.shakePct)

        // сохранение «до R03»: без новых ключей — значения по умолчанию, ничего не теряется
        val body = StringBuilder("TAVERNRUSH-SAVE\nv=1\nseq=3\ngold=50\nlifetimeGold=80\nruns=2\nvictories=0\n")
        val signed = body.toString() + "sig=" + Signer.sign(body.toString()) + "\n"
        val old = SaveCodec.decode(signed)
        assertEquals(50L, old.gold); assertEquals("", old.nemesisId); assertEquals(0, old.gfxPreset); assertTrue(old.dashRight); assertEquals(100, old.shakePct)
    }

    @Test fun forgedNemesisFieldsAreRejected() {
        fun signed(extra: String): String {
            val b = "TAVERNRUSH-SAVE\nv=1\nseq=1\ngold=0\nlifetimeGold=0\nruns=0\n$extra"
            return b + "sig=" + Signer.sign(b) + "\n"
        }
        for (bad in listOf("nem.id=Rat King\n", "nem.id=../../etc\n", "nem.rank=99\n", "gfx=9\n", "stick=2\n", "shake=500\n", "stickSize=5\n", "nem.id=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n")) {
            try { SaveCodec.decode(signed(bad)); fail("подделка принята: $bad") } catch (_: SaveCodec.Invalid) { }
        }
    }

    // ------------------------------------------------------------ бот

    @Test fun botSurvivesAnArcherNestByDodgingAndDashing() {
        val w = world(2024L)
        val bot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 1L)
        for (k in 0 until 4) spawn(w, "archer", -260f + k * 170f, -330f)
        var hits = 0
        var lastHp = w.player.hp
        repeat(60 * 12) {
            for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e.def.id != "archer") e.alive = false; if (e.def.id == "archer") e.hp = e.maxHp }
            bot.steer(w); w.tick(dt)
            if (w.player.hp < lastHp - 0.1f) hits++
            lastHp = w.player.hp
        }
        val naive = world(2024L)
        for (k in 0 until 4) spawn(naive, "archer", -260f + k * 170f, -330f)
        var naiveHits = 0; var nh = naive.player.hp
        repeat(60 * 12) {
            for (i in 0 until naive.enemyCount) { val e = naive.enemies[i]; if (e.alive && e.def.id != "archer") e.alive = false; if (e.def.id == "archer") e.hp = e.maxHp }
            naive.moveX = 0f; naive.moveY = 0f; naive.tick(dt)
            if (naive.player.hp < nh - 0.1f) naiveHits++
            nh = naive.player.hp
        }
        assertTrue("умный бот получает меньше попаданий, чем стоящий герой ($hits против $naiveHits)", hits < naiveHits)
    }

    @Test fun botIsDeterministicWithTheNewSystems() {
        fun run(): Float {
            val w = world(31L); val bot = BotPilot(BotPilot.KITE, BotPilot.GREEDY, 5L)
            w.godMode = true
            repeat(60 * 90) { bot.steer(w); w.tick(dt); if (w.pendingLevelUps > 0) { w.pendingLevelUps = 0 } }
            return w.player.x * 31f + w.player.y + w.run.kills
        }
        assertEquals(run(), run(), 0f)
    }

    // ------------------------------------------------------------ графика

    @Test fun modelsBakeDeterministicallyWithVolumeOutlineAndShadow() {
        val m = HeroModels.innkeeper()
        val a = ModelBaker.bake(m, "walk", false)
        val b = ModelBaker.bake(m, "walk", false)
        assertEquals(6, a.frames.size)
        for (i in a.frames.indices) assertTrue("кадр $i воспроизводим", a.frames[i].pixels.contentEquals(b.frames[i].pixels))
        val filled = a.frames[0].pixels.count { (it ushr 24) != 0 }
        assertTrue("герой занимает достаточно пикселей ($filled)", filled in 300..4000)
        val colors = a.frames[0].pixels.filter { (it ushr 24) != 0 }.toSet().size
        assertTrue("палитра богата, но ограничена ($colors)", colors in 12..160)
        assertTrue("тень есть", a.shadows[0].pixels.any { (it ushr 24) != 0 })
        assertFalse("кадры ходьбы различаются", a.frames[0].pixels.contentEquals(a.frames[2].pixels))
        val l = ModelBaker.bake(m, "walk", true)
        assertFalse("зеркальная версия — отдельная отрисовка", a.frames[0].pixels.contentEquals(l.frames[0].pixels))
        val gold = ModelBaker.bake(m, "idle", false, opts = BakeOpts(outline = 0xFFE8B23A.toInt()))
        assertTrue("золотой контур элиты", gold.frames[0].pixels.any { it == 0xFFE8B23A.toInt() })
    }

    @Test fun everyEnemyLookHasAWalkingModelThatBakes() {
        for (look in EnemyLook.entries) {
            val m = ModelLibrary.enemy(look)
            assertTrue("$look: клип ходьбы", m.clip("walk") != null)
            val s = ModelBaker.bake(m, "walk", false)
            assertTrue("$look: непустой кадр", s.frames[0].pixels.count { (it ushr 24) != 0 } > 60)
        }
        for (id in listOf("rat_king", "ogre_chef", "banshee")) {
            val look = Content.enemies[Content.enemyIndex(id)].look
            assertTrue("$id: клип атаки", ModelLibrary.enemy(look).clip("attack") != null)
        }
    }

    @Test fun contentIsConsistentForTheNewRoster() {
        assertTrue("в таблице не меньше 18 видов (12 обычных, включая слизнёнка, и 6 боссов)", Content.enemies.size >= 18)
        assertEquals(6, Content.bosses.size)
        for (e in Content.enemies) assertTrue("${e.id}: радиус влезает в сетку", e.radius <= 56f)
        for (w in Content.waves) for (ev in w.events) assertTrue(ev.enemyIndex >= 0)
        val bossWaves = Content.waves.withIndex().filter { (_, w) -> w.events.any { it.type == com.tavernrush.game.content.WaveEventType.BOSS } }.map { it.index }
        assertEquals("боссы на 5-й и 10-й волнах", listOf(4, 9), bossWaves)
    }

    @Test fun governorOnlyDowngradesAfterSustainedSlowFrames() {
        val g = GfxGovernor(11f)
        repeat(400) { g.feed(6f, 1f / 60f) }
        assertEquals("быстрые кадры — уровень прежний", GfxPreset.HIGH, g.level)
        val s = GfxGovernor(11f)
        var downgrades = 0
        repeat(60 * 30) { if (s.feed(22f, 1f / 60f)) downgrades++ }
        assertTrue("медленные кадры понижают качество", downgrades >= 2)
        assertEquals(GfxPreset.LOW, s.level)
        repeat(60 * 30) { s.feed(2f, 1f / 60f) }
        assertEquals("сам не повышает (нет «качелей»)", GfxPreset.LOW, s.level)
        val b = GfxGovernor(11f)
        assertFalse(b.feed(Float.NaN, 0.016f))
        assertFalse(b.feed(-5f, 0.016f))
    }
}
