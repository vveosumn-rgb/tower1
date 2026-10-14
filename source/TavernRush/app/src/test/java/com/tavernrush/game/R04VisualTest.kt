package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.Mate
import com.tavernrush.game.meta.Friend
import com.tavernrush.game.systems.EncounterSystem
import com.tavernrush.game.systems.ReactionSystem
import com.tavernrush.game.systems.World
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Кадры R04 для глаз: реакции стихий, состояния врагов, существа и команда (по мере добавления). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class R04VisualTest {
    private val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun start(w: Int = 1080, h: Int = 2340, seed: Long = 840L, l: Int = 0, t: Int = 96, r: Int = 0, b: Int = 48): ScreenshotTest.Rig {
        val rig = ScreenshotTest.Rig(w, h, l, t, r, b, seed)
        assertTrue(rig.until(3f) { rig.game.screen == ScreenId.MENU })
        rig.game.startRun(); rig.advance(.02f) // Legacy arena fixture; primary route is the world.
        rig.game.world.godMode = true
        rig.game.world.weaponCount = 0
        return rig
    }

    private val keep = ArrayList<Enemy>()

    /** Держит на арене только [keep]: режиссёр спавнит крыс, которые закрыли бы сцену. */
    private fun hold(rig: ScreenshotTest.Rig, sec: Float) {
        rig.until(sec) {
            val w = rig.game.world
            for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e !in keep) e.alive = false }
            false
        }
    }

    private fun sp(w: World, id: String, x: Float, y: Float, elite: Boolean = false): Enemy {
        val e = w.spawnEnemy(Content.enemyIndex(id), w.player.x + x, w.player.y + y, elite)!!
        e.speed = 0f; e.spawnFade = 0f
        keep.add(e)
        return e
    }

    private fun hit(w: World, e: Enemy, dmg: Float, el: Element) = w.combat.hitEnemy(e, dmg, e.x, e.y, 0f, -1, true, el, true)

    private fun cardsWithElements(r: ScreenshotTest.Rig, name: String) {
        val w = r.game.world
        w.addWeapon(Content.weaponIndex("mugs"))
        w.pendingLevelUps = 1
        assertTrue(r.until(3f) { r.game.screen == ScreenId.CARD_PICK })
        val ids = listOf("lightning", "hearth", "knives")
        for ((i, id) in ids.withIndex()) {
            val o = w.cards.offers[i]
            o.type = com.tavernrush.game.systems.CardSystem.NEW_WEAPON; o.index = Content.weaponIndex(id); o.fromLevel = 0; o.toLevel = 1
        }
        r.advance(0.8f)
        r.shot(dir, name)
    }

    @Test fun cardsShowElementsAndReactions() {
        cardsWithElements(start(seed = 845L), "65a_cards_elements")
        cardsWithElements(start(720, 1280, 846L, 0, 48, 0, 0), "65b_cards_elements_small")
    }

    @Test fun reactionFrames() {
        // Проводимость: облитая группа, разряд по одному
        run {
            keep.clear()
            val r = start(seed = 841L); val w = r.game.world
            val grp = ArrayList<Enemy>()
            for (i in 0 until 8) grp.add(sp(w, "goblin", -170f + (i % 4) * 78f, -150f + (i / 4) * 70f))
            sp(w, "goblin", 150f, 120f)   // сухой — для сравнения
            hold(r, 0.3f)
            for (e in grp) e.wetT = ReactionSystem.WET_TIME
            r.shot(dir, "60a_wet_group")
            hit(w, grp[1], 12f, Element.SHOCK)
            hold(r, 0.09f)
            r.shot(dir, "60b_reaction_conduct")
        }
        // Заморозка и раскол
        run {
            keep.clear()
            val r = start(seed = 842L); val w = r.game.world
            val grp = ArrayList<Enemy>()
            for (i in 0 until 6) grp.add(sp(w, "orc", -160f + (i % 3) * 90f, -170f + (i / 3) * 90f))
            sp(w, "goblin", 140f, 90f); sp(w, "goblin", 190f, 140f)
            hold(r, 0.3f)
            for (e in grp) e.wetT = ReactionSystem.WET_TIME
            for (e in grp) hit(w, e, 4f, Element.FROST)
            hold(r, 0.1f)
            r.shot(dir, "61_reaction_freeze")
            hold(r, 0.5f)
            hit(w, grp[4], 30f, Element.HEAVY)
            hold(r, 0.07f)
            r.shot(dir, "62_reaction_shatter")
        }
        // Пар
        run {
            keep.clear()
            val r = start(seed = 843L); val w = r.game.world
            val grp = ArrayList<Enemy>()
            for (i in 0 until 7) grp.add(sp(w, "slime", -150f + (i % 4) * 66f, -130f + (i / 4) * 66f))
            hold(r, 0.3f)
            for (e in grp) e.wetT = ReactionSystem.WET_TIME
            hit(w, grp[1], 14f, Element.FIRE)
            hold(r, 0.12f)
            r.shot(dir, "63_reaction_steam")
        }
        // Состояния рядом: обычный, облитый, горит, заморожен
        run {
            keep.clear()
            val r = start(seed = 844L); val w = r.game.world
            val a = sp(w, "orc", -190f, -90f); val b = sp(w, "orc", -65f, -90f); val c = sp(w, "orc", 65f, -90f); val d = sp(w, "orc", 190f, -90f)
            hold(r, 0.3f)
            b.wetT = 3f; c.burnT = 3f; c.burnDmg = 0f; d.frozenT = 3f
            hold(r, 0.12f)
            r.shot(dir, "64_status_normal_wet_burn_frozen")
            assertTrue(a.alive)
        }
    }

    // ------------------------------------------------------------ существа и команда

    private fun friend(id: String, nameIdx: Int = 0, trait: Int = 2, practice: Int = 0, spec: Int = 0, wave: Int = 3, bond: Int = 2) =
        Friend(id).also { it.nameIdx = nameIdx; it.trait = trait; it.practice = practice; it.spec = spec; it.metWave = wave; it.bond = bond }

    /** Забег с друзьями в профиле: [mode] — режим команды. Мир чистый: без оружия, режиссёр придерживается. */
    private fun startWith(vararg fr: Friend, mode: Int = Creatures.MODE_SOLO, w: Int = 1080, h: Int = 2340, seed: Long = 860L, l: Int = 0, t: Int = 96, r: Int = 0, b: Int = 48): ScreenshotTest.Rig {
        val rig = ScreenshotTest.Rig(w, h, l, t, r, b, seed)
        for (f in fr) rig.game.profile.befriend(f)
        rig.game.profile.teamMode = mode
        assertTrue(rig.until(3f) { rig.game.screen == ScreenId.MENU })
        rig.game.startRun(); rig.advance(.02f) // Legacy arena fixture; primary route is the world.
        rig.game.world.godMode = true
        rig.game.world.weaponCount = 0
        return rig
    }

    @Test fun mateFrames() {
        // Ручьеглот: струя по сухим врагам, кнопка приказа и плашка
        run {
            keep.clear()
            val r = startWith(friend("stream", 0), seed = 861L); val wd = r.game.world
            for (i in 0 until 7) sp(wd, "goblin", 110f + (i % 4) * 46f, -90f + (i / 4) * 80f)
            hold(r, 0.12f)
            r.shot(dir, "66a_stream_jet")
            hold(r, 0.9f)
            r.shot(dir, "66b_stream_wet_group")
        }
        // Грозорыс: прыжок на облитую группу, потом отдышка
        run {
            keep.clear()
            val r = startWith(friend("lynx", 0), seed = 862L); val wd = r.game.world
            val grp = ArrayList<Enemy>()
            for (i in 0 until 6) grp.add(sp(wd, "goblin", 150f + (i % 3) * 52f, -60f + (i / 3) * 70f))
            for (e in grp) e.wetT = 4f
            val m = wd.team.lead()!!
            m.charge = 1f
            var shot = false
            r.until(1.5f) {
                for (e in grp) if (e.alive && e.wetT < 1f) e.wetT = 1f
                hold(r, 0.0f)
                if (!shot && m.state == Mate.ACT && m.stateT < 0.12f) { r.shot(dir, "66c_lynx_leap"); shot = true }
                false
            }
            assertTrue(shot)
            r.until(1.0f) { m.state == Mate.RECOVER }
            hold(r, 0.1f)
            r.shot(dir, "66d_lynx_recover_after_conduction")
        }
        // Камнерог: щиты и контрудар
        run {
            keep.clear()
            val r = startWith(friend("horn", 0), seed = 863L); val wd = r.game.world
            wd.godMode = false
            val e1 = sp(wd, "orc", 2f, 0f); sp(wd, "orc", 80f, 30f); sp(wd, "goblin", -90f, 60f); sp(wd, "goblin", 120f, -80f)
            hold(r, 0.5f)
            e1.x = wd.player.x + 2f; e1.y = wd.player.y
            r.until(0.5f) { hold(r, 0f); wd.player.invuln > 0.2f }
            r.shot(dir, "66e_horn_bash")
            assertTrue("щит принял удар: остался не весь запас", wd.team.lead()!!.guard < 3)
        }
    }

    @Test fun encounterFrames() {
        keep.clear()
        val r = startWith(seed = 864L)
        val w = r.game.world
        fun clearOthers() { for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false } }
        assertTrue(r.until(400f) { clearOthers(); if (r.game.screen == ScreenId.CARD_PICK) r.game.pickCard(0); w.encounter.state == EncounterSystem.ACTIVE })
        val enc = w.encounter
        r.advance(0.6f)
        r.shot(dir, "67a_encounter_called_far")
        // герой идёт к месту
        w.player.x = enc.siteX - 300f; w.player.y = enc.siteY + 40f
        r.until(1.2f) { clearOthers(); false }
        r.shot(dir, "67b_encounter_hunters")
        // отогнал: убираем обидчиков, герой в кольце
        w.player.x = enc.siteX - 110f; w.player.y = enc.siteY + 10f
        for (i in 0 until w.enemyCount) w.enemies[i].alive = false
        r.until(8f) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; w.player.x = enc.siteX - 110f; w.player.y = enc.siteY + 10f; enc.trust >= 0.45f }
        r.shot(dir, "67c_encounter_trust")
        r.until(10f) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; w.player.x = enc.siteX - 110f; w.player.y = enc.siteY + 10f; enc.state == EncounterSystem.JOINED }
        r.advance(0.7f)
        r.shot(dir, "67d_encounter_joined")
        assertTrue(r.game.profile.friends.size == 1)
    }

    @Test fun encounterOnSmallScreenAndTablet() {
        for ((name, dims) in listOf("69a_encounter_small" to intArrayOf(720, 1280, 0, 48, 0, 0), "69b_encounter_tablet" to intArrayOf(1600, 2560, 0, 50, 0, 40))) {
            keep.clear()
            val r = startWith(w = dims[0], h = dims[1], l = dims[2], t = dims[3], r = dims[4], b = dims[5], seed = 865L)
            val w = r.game.world
            fun clearOthers() { for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false } }
            assertTrue(r.until(400f) { clearOthers(); if (r.game.screen == ScreenId.CARD_PICK) r.game.pickCard(0); w.encounter.state == EncounterSystem.ACTIVE })
            val enc = w.encounter
            w.player.x = enc.siteX - 200f; w.player.y = enc.siteY + 30f
            r.until(1.0f) { clearOthers(); false }
            r.shot(dir, name)
        }
    }

    @Test fun teamHudModes() {
        keep.clear()
        run {
            val r = startWith(friend("stream", 1), friend("horn", 2), mode = Creatures.MODE_SWAP, seed = 866L)
            val w = r.game.world
            for (i in 0 until 5) sp(w, "goblin", 130f + i * 30f, -60f + (i % 2) * 60f)
            hold(r, 1.2f)
            r.shot(dir, "70a_hud_swap_mode")
            r.tap(r.app.hud.teamHud.button.centerX(), r.app.hud.teamHud.button.centerY())
            hold(r, 0.3f)
            r.shot(dir, "70b_after_swap")
            assertTrue(w.team.reserve()!!.active.not() || w.team.lead()!!.active.not())
        }
        keep.clear()
        run {
            val r = startWith(friend("lynx", 1), friend("stream", 2), mode = Creatures.MODE_DUO, seed = 867L, w = 720, h = 1280, t = 48, b = 0)
            val w = r.game.world
            for (i in 0 until 5) sp(w, "goblin", 130f + i * 30f, -60f + (i % 2) * 60f)
            hold(r, 1.2f)
            r.shot(dir, "70c_hud_duo_small")
        }
        keep.clear()
        run {
            val r = startWith(friend("horn", 0), mode = Creatures.MODE_SOLO, seed = 868L)
            val w = r.game.world
            w.team.lead()!!.hp = 20f
            sp(w, "goblin", 150f, 0f)
            hold(r, 0.8f)
            r.shot(dir, "70d_hud_solo_order")
        }
    }

    @Test fun teamAndMenuScreens() {
        keep.clear()
        val r = ScreenshotTest.Rig(1080, 2340, 0, 96, 0, 48, 870L)
        assertTrue(r.until(3f) { r.game.screen == ScreenId.MENU })
        r.advance(0.8f)
        r.shot(dir, "71a_menu_no_friends")
        r.tap(r.app.menu.friends)
        assertTrue(r.game.screen == ScreenId.TEAM)
        r.advance(0.4f)
        r.shot(dir, "71b_team_empty")
        val p = r.game.profile
        p.befriend(friend("stream", 0, trait = 2, practice = 8, wave = 3))
        p.befriend(friend("horn", 4, trait = 1, practice = 3, spec = 0, wave = 2))
        p.teamMode = Creatures.MODE_SWAP
        r.game.openMenu(); r.advance(0.4f)
        r.shot(dir, "71c_menu_friends")
        r.tap(r.app.menu.friends)
        r.advance(0.4f)
        r.shot(dir, "71d_team_two_friends")
        // выбор развития и смена ролей реальными касаниями
        r.tap(r.app.team.specs[0][1])
        assertTrue("развитие выбрано", p.friend("stream")!!.spec == 2)
        r.tap(r.app.team.lead[2])
        assertTrue("ведущий сменился", p.teamLead == "horn" && p.teamReserve == "stream")
        r.tap(r.app.team.modes[2])
        assertTrue(p.teamMode == Creatures.MODE_DUO)
        r.advance(0.3f)
        r.shot(dir, "71e_team_after_choices")
        // малый экран
        val s = ScreenshotTest.Rig(720, 1280, 0, 48, 0, 0, 871L)
        s.game.profile.befriend(friend("lynx", 1, practice = 7))
        s.game.profile.befriend(friend("stream", 2, practice = 1))
        assertTrue(s.until(3f) { s.game.screen == ScreenId.MENU })
        s.advance(0.6f)
        s.shot(dir, "72a_menu_small_friends")
        s.tap(s.app.menu.friends)
        s.advance(0.4f)
        s.shot(dir, "72b_team_small")
    }

    @Test fun resultsShowTheNewFriendAndReactions() {
        for ((name, dims) in listOf("73a_results_friend" to intArrayOf(1080, 2340, 0, 96, 0, 48), "73b_results_friend_small" to intArrayOf(720, 1280, 0, 48, 0, 0))) {
            keep.clear()
            val r = start(dims[0], dims[1], 880L, dims[2], dims[3], dims[4], dims[5])
            val w = r.game.world
            r.advance(3f)
            w.run.reactions[Reactions.CONDUCT] = 5; w.run.reactions[Reactions.STEAM] = 2; w.run.reactions[Reactions.SHATTER] = 1
            r.game.result.newFriendId = "stream"; r.game.result.newFriendName = "Булька"
            r.game.abandonRun()
            r.advance(2.8f)
            r.shot(dir, name)
        }
    }

    @Test fun encounterLeavingSoonWarning() {
        keep.clear()
        val r = startWith(seed = 874L)
        val w = r.game.world
        assertTrue(r.until(400f) { for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && !e.huntSite) e.alive = false }; if (r.game.screen == ScreenId.CARD_PICK) r.game.pickCard(0); w.encounter.state == EncounterSystem.ACTIVE })
        val enc = w.encounter
        // герой рядом, но не в кольце, врагов нет: существо ждёт, пока не наступит время предупреждения
        assertTrue(r.until(130f) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; if (r.game.screen == ScreenId.CARD_PICK) r.game.pickCard(0); w.player.x = enc.siteX - 330f; w.player.y = enc.siteY; enc.remaining < EncounterSystem.WARN_TIME - 3f })
        r.shot(dir, "67e_encounter_leaving_soon")
        assertTrue(r.until(30f) { for (i in 0 until w.enemyCount) w.enemies[i].alive = false; if (r.game.screen == ScreenId.CARD_PICK) r.game.pickCard(0); w.player.x = enc.siteX - 330f; w.player.y = enc.siteY; enc.state == EncounterSystem.LEFT })
        r.advance(0.6f)
        r.shot(dir, "67f_encounter_left")
    }
}
