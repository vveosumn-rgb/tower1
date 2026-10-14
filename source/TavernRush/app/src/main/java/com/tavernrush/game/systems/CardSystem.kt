package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Element
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.content.IconId
import com.tavernrush.game.core.Sfx

/** Одна карта выбора. */
class CardOffer {
    var type = 0
    /** Индекс в Content.weapons / passives / dishes (для HEAL/GOLD не используется). */
    var index = 0
    var fromLevel = 0
    var toLevel = 0
}

/**
 * Выбор карт: новый уровень, сундук, привал в таверне.
 * Взвешенный выбор без повторов и без аллокаций. Все шансы — в константах ниже (честность: R02 покажет их игроку).
 */
class CardSystem(private val w: World) {
    val offers = Array(MAX_OFFERS) { CardOffer() }
    var offerCount = 0
        private set
    var reason = LEVEL_UP
        private set
    var jackpot = false
        private set
    var rerollsUsed = 0
        private set
    val rerollCost: Int get() = REROLL_BASE shl rerollsUsed
    val canReroll: Boolean get() = offerCount > 0 && rerollsUsed < MAX_REROLLS && w.run.gold >= rerollCost

    private val cType = IntArray(MAX_CANDIDATES)
    private val cIndex = IntArray(MAX_CANDIDATES)
    private val cWeight = FloatArray(MAX_CANDIDATES)
    private var cCount = 0

    fun reset() {
        offerCount = 0
        reason = LEVEL_UP
        jackpot = false; rerollsUsed = 0
    }

    fun roll(reason: Int) {
        this.reason = if (reason in LEVEL_UP..TAVERN) reason else LEVEL_UP
        rerollsUsed = 0
        jackpot = this.reason == CHEST && w.rngCards.chance(JACKPOT_CHANCE)
        buildOffers()
        if (this.reason == TAVERN) w.run.tavernStops++
        if (this.reason == CHEST) w.events.sfx(Sfx.CHEST) else w.events.sfx(Sfx.LEVEL_UP)
    }

    /** Переброс оплачивается золотом этого забега; шанс джекпота повторно не бросается. */
    fun reroll(): Boolean {
        if (!canReroll) return false
        w.run.gold -= rerollCost
        rerollsUsed++
        buildOffers()
        w.events.sfx(Sfx.CLICK)
        return true
    }

    private fun buildOffers() {
        val reason = this.reason
        offerCount = 0
        cCount = 0
        if (reason == TAVERN) {
            val ds = Content.dishes
            for (i in ds.indices) {
                // Голодному герою трактирщик чаще предлагает сытное.
                val healing = ds[i].healPct > 0f
                add(DISH, i, if (healing && w.player.hpFrac < 0.5f) 2.5f else 1f)
            }
        } else {
            if (w.player.hpFrac < 0.22f) pushOffer(HEAL, 0)
            else if (w.player.hpFrac < 0.6f) add(HEAL, 0, 0.8f)
            val chest = reason == CHEST
            for (s in 0 until w.weaponCount) {
                val wi = w.weapons[s]
                if (wi.level < wi.def.maxLevel) add(UP_WEAPON, wi.def.index, if (chest) 2.2f else 1.0f)
            }
            if (w.weaponCount < World.MAX_WEAPONS) {
                val bonus = if (w.weaponCount < 3) 1.6f else 1f
                for (i in Content.weapons.indices) {
                    if (w.findWeapon(i) < 0) add(NEW_WEAPON, i, (if (chest) 0.6f else 0.85f) * bonus)
                }
            }
            for (i in Content.passives.indices) {
                val lvl = w.passiveLevels[i]
                val max = Content.passives[i].maxLevel
                if (lvl in 1 until max) add(UP_PASSIVE, i, if (chest) 1.6f else 0.8f)
                else if (lvl == 0 && w.passiveCount < World.MAX_PASSIVES) add(NEW_PASSIVE, i, if (chest) 0.3f else 0.55f)
            }
        }
        // Готовая эволюция гарантированно видна, а не спрятана за удачей.
        if (reason != TAVERN) {
            for (i in Content.evolutions.indices) {
                if (w.canEvolve(i)) { pushOffer(EVOLVE, i); break }
            }
        }
        val rng = w.rngCards
        while (offerCount < MAX_OFFERS && cCount > 0) {
            var total = 0f
            for (k in 0 until cCount) total += cWeight[k]
            var r = rng.nextFloat() * total
            var pick = cCount - 1
            for (k in 0 until cCount) {
                r -= cWeight[k]
                if (r < 0f) { pick = k; break }
            }
            pushOffer(cType[pick], cIndex[pick])
            cCount--
            cType[pick] = cType[cCount]; cIndex[pick] = cIndex[cCount]; cWeight[pick] = cWeight[cCount]
        }
        // Всё собрано до максимума — запасные карты.
        if (offerCount < MAX_OFFERS && (0 until offerCount).none { offers[it].type == HEAL }) pushOffer(HEAL, 0)
        if (offerCount < MAX_OFFERS) pushOffer(GOLD, 0)
    }

    private fun add(type: Int, index: Int, weight: Float) {
        if (cCount >= MAX_CANDIDATES) return
        cType[cCount] = type; cIndex[cCount] = index; cWeight[cCount] = weight
        cCount++
    }

    private fun pushOffer(type: Int, index: Int) {
        val o = offers[offerCount++]
        o.type = type
        o.index = index
        when (type) {
            UP_WEAPON -> {
                val slot = w.findWeapon(index)
                o.fromLevel = w.weapons[slot].level
                o.toLevel = o.fromLevel + 1
            }
            UP_PASSIVE -> {
                o.fromLevel = w.passiveLevels[index]
                o.toLevel = o.fromLevel + 1
            }
            NEW_WEAPON, NEW_PASSIVE -> { o.fromLevel = 0; o.toLevel = 1 }
            else -> { o.fromLevel = 0; o.toLevel = 0 }
        }
    }

    /** Применить карту i. Возвращает false, если индекс неверный. */
    fun apply(i: Int): Boolean {
        if (i < 0 || i >= offerCount) return false
        val o = offers[i]
        val applied = when (o.type) {
            NEW_WEAPON -> w.addWeapon(o.index)
            UP_WEAPON -> w.upgradeWeapon(w.findWeapon(o.index))
            NEW_PASSIVE, UP_PASSIVE -> w.addPassive(o.index)
            HEAL -> { w.heal(w.player.maxHp * HEAL_PCT); true }
            GOLD -> { w.run.gold += GOLD_AMOUNT * w.stats.goldGain; true }
            DISH -> { if (o.index !in Content.dishes.indices) return false; applyDish(o.index); true }
            EVOLVE -> w.evolve(o.index)
            else -> false
        }
        if (!applied) return false
        if (jackpot) {
            w.run.gold += JACKPOT_GOLD * w.stats.goldGain
            w.fx.burst(w.player.x, w.player.y, 0xFFFFE487.toInt(), 65, 420f, 7f, 1.2f, com.tavernrush.game.entities.Particle.STAR)
            w.events.sfx(Sfx.CHEST)
            jackpot = false
        }
        w.run.recordPick(o.type, o.index)
        offerCount = 0
        return true
    }

    private fun applyDish(index: Int) {
        val d = Content.dishes[index]
        val st = d.stat
        if (st != null) {
            w.dishBonus[st.ordinal] += d.amount
            w.recomputeStats()
        }
        if (d.healPct > 0f) w.heal(w.player.maxHp * d.healPct)
        if (d.gold > 0) w.run.gold += d.gold.toFloat()
    }

    // ---------------------------------------------------------------- тексты карт (без аллокаций)

    fun title(o: CardOffer): String = when (o.type) {
        NEW_WEAPON, UP_WEAPON -> Content.weapons[o.index].name
        NEW_PASSIVE, UP_PASSIVE -> Content.passives[o.index].name
        DISH -> Content.dishes[o.index].name
        HEAL -> "Жареная курица"
        EVOLVE -> Content.evolutions[o.index].name
        else -> "Горсть монет"
    }

    fun desc(o: CardOffer): String = when (o.type) {
        NEW_WEAPON -> Content.weapons[o.index].desc
        UP_WEAPON -> Content.weapons[o.index].levels[o.fromLevel - 1].text
        NEW_PASSIVE, UP_PASSIVE -> Content.passives[o.index].desc
        DISH -> Content.dishes[o.index].desc
        HEAL -> "Лечение 50% здоровья"
        EVOLVE -> Content.evolutions[o.index].desc
        else -> "+25 золота в копилку"
    }

    fun icon(o: CardOffer): IconId = when (o.type) {
        NEW_WEAPON, UP_WEAPON -> Content.weapons[o.index].icon
        NEW_PASSIVE, UP_PASSIVE -> Content.passives[o.index].icon
        DISH -> Content.dishes[o.index].icon
        HEAL -> IconId.CHICKEN
        EVOLVE -> Content.weapons[Content.evolutions[o.index].weaponIndex].icon
        else -> IconId.GOLD_BAG
    }

    fun color(o: CardOffer): Int = when (o.type) {
        NEW_WEAPON, UP_WEAPON -> Content.weapons[o.index].color
        NEW_PASSIVE, UP_PASSIVE -> Content.passives[o.index].color
        DISH -> 0xFFFFB86B.toInt()
        HEAL -> 0xFFFF8A65.toInt()
        EVOLVE -> 0xFFFFD54A.toInt()
        else -> 0xFFFFD54A.toInt()
    }

    /** Максимальный уровень предмета карты (0 — без уровней). */
    fun maxLevel(o: CardOffer): Int = when (o.type) {
        NEW_WEAPON, UP_WEAPON -> Content.weapons[o.index].maxLevel
        NEW_PASSIVE, UP_PASSIVE -> Content.passives[o.index].maxLevel
        else -> 0
    }

    /** Стихия карты оружия (нового или уже имеющегося); у остальных карт — NONE. */
    fun element(o: CardOffer): Element =
        if (o.type == NEW_WEAPON || o.type == UP_WEAPON) Content.weapons[o.index].element else Element.NONE

    /** Битовая маска стихий оружия, которое у героя уже есть (см. Reactions.mask). */
    fun ownedElements(): Int {
        var m = 0
        for (i in 0 until w.weaponCount) m = m or Reactions.mask(w.weapons[i].def.element)
        for (i in 0 until w.team.count) m = m or Reactions.mask(Content.creatures[w.team.mates[i]!!.species].element)
        return m
    }

    /** Какая реакция получится у нового оружия с уже взятым; Reactions.NONE, если никакой. */
    fun reactionHint(o: CardOffer): Int =
        if (o.type == NEW_WEAPON) Reactions.hint(element(o), ownedElements()) else Reactions.NONE

    fun isWeapon(o: CardOffer): Boolean = o.type == NEW_WEAPON || o.type == UP_WEAPON || o.type == EVOLVE
    fun isNew(o: CardOffer): Boolean = o.type == NEW_WEAPON || o.type == NEW_PASSIVE

    companion object {
        const val MAX_OFFERS = 3
        const val MAX_CANDIDATES = 64

        const val NEW_WEAPON = 0
        const val UP_WEAPON = 1
        const val NEW_PASSIVE = 2
        const val UP_PASSIVE = 3
        const val HEAL = 4
        const val GOLD = 5
        const val DISH = 6
        const val EVOLVE = 7

        const val LEVEL_UP = 0
        const val CHEST = 1
        const val TAVERN = 2

        const val HEAL_PCT = 0.5f
        const val GOLD_AMOUNT = 25f
        const val JACKPOT_CHANCE = 0.05f
        const val JACKPOT_GOLD = 100f
        const val REROLL_BASE = 10
        const val MAX_REROLLS = 2
    }
}
