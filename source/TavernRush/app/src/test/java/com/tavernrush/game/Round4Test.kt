package com.tavernrush.game

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.systems.ReactionSystem
import com.tavernrush.game.systems.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R04, часть 1: реакции стихий. Игровой мир гоняется без Android; врагов режиссёра убираем перед шагом,
 * чтобы измерять реакцию в чистом виде.
 */
class Round4Test {
    private val dt = 1f / 60f

    /** Мир без стартового оружия героя: оно бьёт врагов само и портит измерения. Нужное оружие добавляем явно. */
    private fun world(seed: Long = 404L): World = World().also { it.reset(seed, null); it.godMode = true; it.weaponCount = 0 }

    /** Шаг мира: живы только [keep]; остальных, включая спавн режиссёра, убираем. */
    private fun step(w: World, keep: Collection<Enemy>) {
        for (i in 0 until w.enemyCount) { val e = w.enemies[i]; if (e.alive && e !in keep) e.alive = false }
        w.tick(dt)
    }

    private fun spawn(w: World, id: String, x: Float, y: Float, elite: Boolean = false): Enemy {
        val e = w.spawnEnemy(Content.enemyIndex(id), x, y, elite)!!
        e.speed = 0f
        return e
    }

    /** Мир с цепочкой врагов: после первого тика сетка соседей актуальна. */
    private fun ready(w: World, vararg es: Enemy) { step(w, es.toList()) }

    private fun hit(w: World, e: Enemy, dmg: Float, el: Element, slot: Int = -1) =
        w.combat.hitEnemy(e, dmg, e.x, e.y, 0f, slot, true, el, true)

    private fun wet(e: Enemy) { e.wetT = ReactionSystem.WET_TIME }

    // ------------------------------------------------------------ таблица стихий

    @Test fun weaponsCarryTheirElements() {
        fun el(id: String) = Content.weapons[Content.weaponIndex(id)].element
        assertEquals(Element.NONE, el("knives"))
        assertEquals(Element.WATER, el("mugs"))
        assertEquals(Element.FIRE, el("hearth"))
        assertEquals(Element.SHOCK, el("lightning"))
        assertEquals(Element.HEAVY, el("pan"))
        assertEquals(Element.HEAVY, el("barrel"))
        assertEquals(Element.FROST, el("frost"))
        assertEquals(Element.FIRE, el("sunbeam"))
        assertEquals(Element.SHOCK, el("bell"))
        assertEquals(Element.WATER, el("cauldron"))
        assertEquals(Element.NONE, el("trap"))
        assertEquals(Element.NONE, el("raven"))
    }

    @Test fun elementPairsAndHintsAreConsistent() {
        assertEquals(Reactions.CONDUCT, Reactions.between(Element.WATER, Element.SHOCK))
        assertEquals(Reactions.CONDUCT, Reactions.between(Element.SHOCK, Element.WATER))
        assertEquals(Reactions.FREEZE, Reactions.between(Element.FROST, Element.WATER))
        assertEquals(Reactions.STEAM, Reactions.between(Element.WATER, Element.FIRE))
        assertEquals(Reactions.NONE, Reactions.between(Element.SHOCK, Element.FIRE))
        assertEquals(Reactions.NONE, Reactions.between(Element.WATER, Element.WATER))
        // подсказка карты: новая стихия + уже имеющиеся
        val owned = Reactions.mask(Element.WATER) or Reactions.mask(Element.FROST)
        assertEquals(Reactions.SHATTER, Reactions.hint(Element.HEAVY, owned))
        assertEquals(Reactions.CONDUCT, Reactions.hint(Element.SHOCK, owned))
        assertEquals(Reactions.NONE, Reactions.hint(Element.HEAVY, Reactions.mask(Element.FIRE)))
        assertEquals(Reactions.NONE, Reactions.hint(Element.NONE, owned))
        assertEquals(Reactions.COUNT, Reactions.names.size)
        assertEquals(Reactions.COUNT, Reactions.colors.size)
        assertEquals(Reactions.COUNT, Reactions.hints.size)
    }

    @Test fun cardsExplainTheReactionANewWeaponWouldMake() {
        val w = world()
        w.addWeapon(Content.weaponIndex("mugs"))
        fun offer(id: String) = com.tavernrush.game.systems.CardOffer().also { it.type = com.tavernrush.game.systems.CardSystem.NEW_WEAPON; it.index = Content.weaponIndex(id) }
        assertEquals(Reactions.CONDUCT, w.cards.reactionHint(offer("lightning")))
        assertEquals(Reactions.STEAM, w.cards.reactionHint(offer("hearth")))
        assertEquals(Reactions.FREEZE, w.cards.reactionHint(offer("frost")))
        assertEquals(Reactions.NONE, w.cards.reactionHint(offer("knives")))
        assertEquals(Element.SHOCK, w.cards.element(offer("bell")))
        val heal = com.tavernrush.game.systems.CardOffer().also { it.type = com.tavernrush.game.systems.CardSystem.HEAL }
        assertEquals(Element.NONE, w.cards.element(heal))
        // для имеющегося оружия подсказка не нужна: реакция уже в игре
        val up = com.tavernrush.game.systems.CardOffer().also { it.type = com.tavernrush.game.systems.CardSystem.UP_WEAPON; it.index = Content.weaponIndex("mugs") }
        assertEquals(Reactions.NONE, w.cards.reactionHint(up))
    }

    // ------------------------------------------------------------ проводимость

    @Test fun shockOnWetEnemyConductsToWetNeighboursOnly() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f)
        val n1 = spawn(w, "goblin", 340f, 20f)
        val n2 = spawn(w, "goblin", 300f, 70f)
        val n3 = spawn(w, "goblin", 250f, -60f)
        val n4 = spawn(w, "goblin", 380f, 30f)
        val dry = spawn(w, "goblin", 320f, -40f)
        val far = spawn(w, "goblin", 300f, 400f)
        for (e in listOf(a, n1, n2, n3, n4, far)) wet(e)
        ready(w, a, n1, n2, n3, n4, dry, far)
        val all = listOf(a, n1, n2, n3, n4, dry, far)
        val before = all.map { it.hp }
        hit(w, a, 10f, Element.SHOCK)
        assertEquals("одна реакция", 1, w.run.reactions[Reactions.CONDUCT])
        assertEquals("дуг не больше ${ReactionSystem.ARCS}", ReactionSystem.ARCS, listOf(n1, n2, n3, n4).count { it.hp < before[all.indexOf(it)] })
        assertEquals("сухой сосед не задет", before[5], dry.hp, 0.001f)
        assertEquals("далёкий облитый не задет", before[6], far.hp, 0.001f)
        assertEquals("вода потрачена у цели", 0f, a.wetT, 0.001f)
        for (e in listOf(n1, n2, n3, n4)) if (e.hp < before[all.indexOf(e)]) assertEquals("вода потрачена у задетых", 0f, e.wetT, 0.001f)
    }

    @Test fun shockOnDryEnemyIsJustDamage() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f)
        val n = spawn(w, "goblin", 330f, 0f); wet(n)
        ready(w, a, n)
        val hp = n.hp
        hit(w, a, 10f, Element.SHOCK)
        assertEquals(0, w.run.reactions[Reactions.CONDUCT])
        assertEquals(hp, n.hp, 0.001f)
    }

    @Test fun conductionNeverChainsIntoMoreReactionsAndRespectsTheBudget() {
        val w = world()
        val es = ArrayList<Enemy>()
        for (i in 0 until 60) es.add(spawn(w, "rat", 260f + (i % 10) * 22f, -120f + (i / 10) * 24f).also { wet(it) })
        ready(w, *es.toTypedArray())
        for (e in es) { wet(e); hit(w, e, 5f, Element.SHOCK) }
        assertTrue("реакций за тик не больше бюджета", w.run.reactions[Reactions.CONDUCT] <= ReactionSystem.MAX_PER_TICK)
        assertTrue("часть реакций всё же сработала", w.run.reactions[Reactions.CONDUCT] >= 1)
    }

    @Test fun sameTargetDoesNotReactTwiceWithinTheCooldown() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f)
        val n = spawn(w, "goblin", 330f, 0f)
        wet(a); wet(n)
        ready(w, a, n)
        hit(w, a, 10f, Element.SHOCK)
        wet(a)
        hit(w, a, 10f, Element.SHOCK)
        assertEquals(1, w.run.reactions[Reactions.CONDUCT])
        for (k in 0 until 30) step(w, listOf(a, n))   // 0,5 с > REACT_CD
        wet(a); wet(n)
        hit(w, a, 10f, Element.SHOCK)
        assertEquals(2, w.run.reactions[Reactions.CONDUCT])
    }

    // ------------------------------------------------------------ заморозка и раскол

    @Test fun frostOnWetEnemyFreezesItAndFrozenEnemyStopsAndCannotHurt() {
        val w = world()
        w.godMode = false
        val a = spawn(w, "orc", 40f, 0f)   // у самого героя
        a.speed = 90f
        wet(a)
        ready(w, a)
        hit(w, a, 4f, Element.FROST)
        assertEquals(1, w.run.reactions[Reactions.FREEZE])
        assertTrue(a.frozenT > 1f)
        assertEquals(0f, a.wetT, 0.001f)
        val x = a.x; val y = a.y; val hp = w.player.hp
        for (k in 0 until 60) step(w, listOf(a))
        assertEquals("заморожен: стоит", x, a.x, 0.001f); assertEquals(y, a.y, 0.001f)
        assertEquals("заморожен: не бьёт", hp, w.player.hp, 0.001f)
        for (k in 0 until 60) step(w, listOf(a))
        assertTrue("оттаял", a.frozenT <= 0f)
    }

    @Test fun frozenEnemiesTakeMoreDamage() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f); val b = spawn(w, "goblin", 300f, 200f)
        ready(w, a, b)
        a.frozenT = 2f
        val ha = a.hp; val hb = b.hp
        hit(w, a, 10f, Element.NONE); hit(w, b, 10f, Element.NONE)
        assertEquals(15f, ha - a.hp, 0.01f)
        assertEquals(10f, hb - b.hp, 0.01f)
    }

    @Test fun bossesAreNotFrozenButStillTakeTheReactionHit() {
        val w = world()
        val b = w.spawnBoss(Content.enemyIndex("rat_king"))!!
        b.speed = 0f; b.invulnT = 0f; b.state = 0
        wet(b)
        ready(w, b)
        b.invulnT = 0f
        val hp = b.hp
        hit(w, b, 10f, Element.FROST)
        assertEquals(0f, b.frozenT, 0.001f)
        assertEquals(1, w.run.reactions[Reactions.FREEZE])
        assertTrue(b.hp < hp - 10f)
    }

    @Test fun heavyHitShattersFrozenEnemyAndHurtsNeighbours() {
        val w = world()
        val a = spawn(w, "orc", 300f, 0f)
        val n = spawn(w, "goblin", 340f, 0f)
        val far = spawn(w, "goblin", 300f, 300f)
        wet(a)
        ready(w, a, n, far)
        hit(w, a, 4f, Element.FROST)
        assertTrue(a.frozenT > 0f)
        for (k in 0 until 30) step(w, listOf(a, n, far))
        val hn = n.hp; val hf = far.hp; val ha = a.hp
        hit(w, a, 20f, Element.HEAVY)
        assertEquals(1, w.run.reactions[Reactions.SHATTER])
        assertEquals(0f, a.frozenT, 0.001f)
        assertTrue("сосед ранен осколками", n.hp < hn)
        assertEquals("далёкий цел", hf, far.hp, 0.001f)
        // раскол добавляет урон к самому удару: 20 × 1,5 (заморожен) + столько же сверху
        assertTrue("удар по замороженному сильнее обычного", ha - a.hp >= 20f * 1.5f * 2f - 0.5f)
    }

    @Test fun heavyHitOnUnfrozenEnemyDoesNotShatter() {
        val w = world()
        val a = spawn(w, "orc", 300f, 0f)
        wet(a)
        ready(w, a)
        hit(w, a, 20f, Element.HEAVY)
        assertEquals(0, w.run.reactions[Reactions.SHATTER])
    }

    // ------------------------------------------------------------ пар, ожог, тушение

    @Test fun fireOnWetEnemyMakesSteamThatHurtsSlowsAndDriesTheArea() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f)
        val n = spawn(w, "goblin", 340f, 0f)
        val far = spawn(w, "goblin", 300f, 300f)
        wet(a); wet(n); wet(far)
        ready(w, a, n, far)
        val hn = n.hp; val hf = far.hp
        hit(w, a, 12f, Element.FIRE)
        assertEquals(1, w.run.reactions[Reactions.STEAM])
        assertTrue("пар ранит соседа", n.hp < hn)
        assertTrue("пар замедляет", n.slowTimer > 0f && n.slowMul < 1f)
        assertEquals("пар сушит", 0f, n.wetT, 0.001f)
        assertEquals("далёкий цел и мокр", hf, far.hp, 0.001f)
        assertTrue(far.wetT > 0f)
        assertEquals("огонь не поджёг облитого", 0f, a.burnT, 0.001f)
    }

    @Test fun fireBurnsADryEnemyAndWaterPutsItOut() {
        val w = world()
        val a = spawn(w, "orc", 300f, 0f)
        ready(w, a)
        hit(w, a, 20f, Element.FIRE)
        assertTrue(a.burnT > 0f)
        val hp = a.hp
        for (k in 0 until 90) step(w, listOf(a))
        assertTrue("ожог тикает", a.hp < hp)
        hit(w, a, 1f, Element.WATER)
        assertEquals("вода тушит", 0f, a.burnT, 0.001f)
        assertEquals("и обнуляет силу ожога", 0f, a.burnDmg, 0.001f)
        assertTrue("и мочит", a.wetT > 0f)
        val hp2 = a.hp
        for (k in 0 until 60) step(w, listOf(a))
        assertEquals("после тушения не горит", hp2, a.hp, 0.001f)
    }

    @Test fun aWeakBurnAfterAStrongOneDoesNotInheritItsPower() {
        val w = world()
        val a = spawn(w, "orc", 300f, 0f)
        ready(w, a)
        hit(w, a, 80f, Element.FIRE)
        val strong = a.burnDmg
        for (k in 0 until 60 * 4) step(w, listOf(a))
        assertEquals("ожог прошёл", 0f, a.burnT, 0.001f)
        assertEquals("сила ожога сброшена", 0f, a.burnDmg, 0.001f)
        hit(w, a, 4f, Element.FIRE)
        assertTrue("новый ожог слабее старого: ${a.burnDmg} < $strong", a.burnDmg < strong)
    }

    @Test fun burnCanKillAndCreditsTheKill() {
        val w = world()
        val a = spawn(w, "rat", 300f, 0f)
        ready(w, a)
        a.hp = 3f
        a.burnT = 2f; a.burnDmg = 5f; a.burnTick = 0f
        val kills = w.run.kills
        for (k in 0 until 40) step(w, listOf(a))
        assertEquals("ожог убил и засчитал убийство", kills + 1, w.run.kills)
    }

    @Test fun waterMakesEnemiesWetForALimitedTime() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f)
        ready(w, a)
        hit(w, a, 3f, Element.WATER)
        assertTrue(a.wetT > 3f)
        for (k in 0 until (ReactionSystem.WET_TIME * 60f).toInt() + 30) step(w, listOf(a))
        assertEquals(0f, a.wetT, 0.001f)
    }

    @Test fun statusesDoNotLeakThroughPoolReuse() {
        val w = world()
        val a = spawn(w, "goblin", 300f, 0f)
        a.wetT = 3f; a.burnT = 3f; a.burnDmg = 4f; a.frozenT = 2f; a.reactCd = 1f; a.squash = 1f
        a.alive = false
        w.tick(dt)
        val b = spawn(w, "goblin", 310f, 0f)
        assertEquals(0f, b.wetT, 0f); assertEquals(0f, b.burnT, 0f); assertEquals(0f, b.frozenT, 0f)
        assertEquals(0f, b.reactCd, 0f); assertEquals(0f, b.squash, 0f)
    }

    // ------------------------------------------------------------ сквозной сценарий с настоящими оружиями

    @Test fun mugsPlusLightningMakeConductionWithoutAnyScriptedHit() {
        val w = world(777L)
        w.addWeapon(Content.weaponIndex("mugs"))
        w.addWeapon(Content.weaponIndex("lightning"))
        val es = ArrayList<Enemy>()
        for (i in 0 until 26) {
            val a = i * 0.2415f * 2f
            es.add(spawn(w, "goblin", Math.cos(a.toDouble()).toFloat() * (60f + (i % 4) * 22f), Math.sin(a.toDouble()).toFloat() * (60f + (i % 4) * 22f)))
        }
        for (k in 0 until 60 * 14) {
            for (e in es) if (e.alive) { e.hp = e.maxHp; e.speed = 0f; e.x = e.x; }
            step(w, es)
            if (w.run.reactions[Reactions.CONDUCT] > 0) break
        }
        assertTrue("пивные кружки намочили, молния провела разряд", w.run.reactions[Reactions.CONDUCT] > 0)
    }

    @Test fun reactionsAreDeterministicForTheSameSeed() {
        fun run(): String {
            val w = world(2024L)
            w.addWeapon(Content.weaponIndex("cauldron"))
            w.addWeapon(Content.weaponIndex("frost"))
            w.addWeapon(Content.weaponIndex("barrel"))
            val es = ArrayList<Enemy>()
            for (i in 0 until 20) es.add(spawn(w, "orc", 90f + (i % 5) * 18f, -60f + (i / 5) * 30f))
            for (k in 0 until 60 * 8) { for (e in es) if (e.alive) { e.hp = e.maxHp; e.speed = 0f }; step(w, es) }
            return w.run.reactions.joinToString(",") + "|" + w.run.damageDealt.toInt()
        }
        assertEquals(run(), run())
    }

    @Test fun reactionLabelsAreRateLimited() {
        val w = world()
        val es = ArrayList<Enemy>()
        for (i in 0 until 12) es.add(spawn(w, "rat", 300f + (i % 4) * 90f, -100f + (i / 4) * 90f).also { wet(it) })
        ready(w, *es.toTypedArray())
        for (e in es) { wet(e); hit(w, e, 5f, Element.FROST) }
        var labels = 0
        for (i in 0 until w.fx.textCount) if (w.fx.texts[i].kind == com.tavernrush.game.entities.FloatText.REACTION) labels++
        assertTrue("подписей реакции не больше одной на вид за 0,35 с: $labels", labels <= 1)
        assertNotEquals(0, w.run.reactions[Reactions.FREEZE])
    }

    /**
     * Прокси стоимости логики на JVM (не телефон): шаг мира при 300 врагах без оружия, с оружием и с командой.
     * Числа пишутся в screenshots/logic_timing.txt; порог в 5 мс на тик — только страховка от патологии,
     * реальные значения на порядки меньше.
     */
    @Test fun logicTickCostIsRecordedAndStaysFarBelowABudget() {
        val sb = StringBuilder("шаг логики на JVM (после прогрева), 300 врагов; прокси, а не время на телефоне\n")
        fun scenario(label: String, withWeapons: Boolean, withTeam: Boolean): Double {
            val w = World().also { it.reset(321L, null); it.godMode = true; if (!withWeapons) it.weaponCount = 0 }
            if (withWeapons) for (id in listOf("mugs", "lightning", "hearth", "frost", "barrel")) w.addWeapon(Content.weaponIndex(id))
            if (withTeam) w.team.setup(com.tavernrush.game.meta.Friend("lynx"), com.tavernrush.game.meta.Friend("stream"), com.tavernrush.game.content.Creatures.MODE_DUO)
            val ids = listOf("rat", "goblin", "bat", "slime", "orc", "boar")
            for (i in 0 until 300) {
                val a = i * 2.399963f; val r = 80f + (i % 13) * 28f
                w.spawnEnemy(Content.enemyIndex(ids[i % ids.size]), Math.cos(a.toDouble()).toFloat() * r, Math.sin(a.toDouble()).toFloat() * r * 0.9f, i % 31 == 0)
                    ?.also { it.speed = 0f; it.hp = 1e6f; it.maxHp = 1e6f }
            }
            repeat(300) { w.tick(dt) }
            val n = 900
            val t0 = System.nanoTime()
            repeat(n) { w.tick(dt) }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            sb.append(String.format("%-36s %.3f мс/тик; врагов %d; реакции %s\n", label, ms, w.enemyCount, w.run.reactions.joinToString(",")))
            return ms
        }
        val worst = maxOf(scenario("без оружия, без команды", false, false), scenario("5 оружий, без команды", true, false), scenario("5 оружий и команда из двух", true, true))
        val dir = java.io.File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "logic_timing.txt").writeText(sb.toString())
        assertTrue("шаг логики не должен приближаться к бюджету кадра: $worst мс", worst < 5.0)
    }
}
