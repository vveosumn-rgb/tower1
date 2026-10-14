package com.tavernrush.game.content

/**
 * Реестр всего контента. Индексы (def.index) назначаются здесь и используются в горячем цикле.
 * Ссылки по строковым id разрешаются один раз при загрузке; ошибки данных падают сразу (ContentTest).
 * Сохранения хранят строковые id, поэтому порядок записей можно менять.
 */
object Content {
    val weapons: Array<WeaponDef> = Weapons.all() + Arsenal.all()
    val passives: Array<PassiveDef> = Passives.all()
    val enemies: Array<EnemyDef> = Enemies.all()
    val waves: Array<WaveDef> = Waves.all()
    val hubUpgrades: Array<HubUpgradeDef> = HubUpgrades.all()
    val dishes: Array<DishDef> = Dishes.all()
    val heroes: Array<HeroDef> = Heroes.all()
    val evolutions: Array<EvolutionDef> = Evolutions.all()
    val creatures: Array<CreatureDef> = Creatures.all()

    val statCount: Int = Stat.entries.size

    /** Длительность главного забега (сумма волн), с. */
    val runDuration: Float

    init {
        for (i in weapons.indices) { weapons[i].index = i; weapons[i].build() }
        for (i in passives.indices) passives[i].index = i
        for (i in enemies.indices) enemies[i].index = i
        for (e in enemies) {
            if (e.splitId.isNotEmpty()) { e.splitIndex = enemyIndex(e.splitId); require(e.splitIndex >= 0 && e.splitCount > 0) { "Враг ${e.id}: неверный распад" } }
            if (e.summonId.isNotEmpty()) { e.summonIndex = enemyIndex(e.summonId); require(e.summonIndex >= 0) { "Враг ${e.id}: неверный призыв" } }
            require(e.radius <= 56f) { "Враг ${e.id}: радиус больше 56 ломает пространственную сетку (макс. 62 с учётом элит и немезиды)" }
        }
        for (i in hubUpgrades.indices) hubUpgrades[i].index = i
        for (i in dishes.indices) dishes[i].index = i
        for (i in heroes.indices) {
            heroes[i].index = i
            heroes[i].weaponIndex = weaponIndex(heroes[i].weaponId)
            require(heroes[i].weaponIndex >= 0) { "Неизвестное оружие героя" }
        }
        for (c in creatures) {
            require(c.names.isNotEmpty() && c.specs.size == 2 && c.tormentors.all { enemyIndex(it) >= 0 }) { "Существо ${c.id}: неверные данные" }
        }
        for (e in evolutions) {
            e.weaponIndex = weaponIndex(e.weaponId)
            e.passiveIndex = passiveIndex(e.passiveId)
            require(e.weaponIndex >= 0 && e.passiveIndex >= 0) { "Неизвестный рецепт эволюции" }
            require(e.passiveLevel in 1..passives[e.passiveIndex].maxLevel)
        }
        var total = 0f
        for (w in waves) {
            var tw = 0
            for (e in w.pool) {
                e.enemyIndex = enemyIndex(e.enemyId)
                require(e.enemyIndex >= 0) { "Волна «${w.title}»: неизвестный враг ${e.enemyId}" }
                require(e.weight > 0) { "Волна «${w.title}»: вес должен быть > 0" }
                tw += e.weight
            }
            w.totalWeight = tw
            for (ev in w.events) {
                ev.enemyIndex = enemyIndex(ev.enemyId)
                require(ev.enemyIndex >= 0) { "Волна «${w.title}»: событие с неизвестным врагом ${ev.enemyId}" }
                require(ev.at in 0f..w.duration) { "Волна «${w.title}»: событие вне волны" }
            }
            for (k in 1 until w.events.size) require(w.events[k].at >= w.events[k - 1].at) { "Волна «${w.title}»: события не по порядку" }
            total += w.duration
        }
        runDuration = total
        requireUnique(weapons.map { it.id }, "оружие")
        requireUnique(passives.map { it.id }, "пассивки")
        requireUnique(enemies.map { it.id }, "враги")
        requireUnique(hubUpgrades.map { it.id }, "улучшения таверны")
        requireUnique(dishes.map { it.id }, "блюда")
        requireUnique(heroes.map { it.id }, "герои")
        require(weaponIndex(Weapons.START) >= 0) { "Нет стартового оружия ${Weapons.START}" }
    }

    private fun requireUnique(ids: List<String>, what: String) {
        require(ids.size == ids.toSet().size) { "Повторяющиеся id: $what" }
    }

    fun weaponIndex(id: String): Int { for (i in weapons.indices) if (weapons[i].id == id) return i; return -1 }
    fun passiveIndex(id: String): Int { for (i in passives.indices) if (passives[i].id == id) return i; return -1 }
    fun enemyIndex(id: String): Int { for (i in enemies.indices) if (enemies[i].id == id) return i; return -1 }
    /** Индексы боссов (в порядке таблицы). */
    val bosses: IntArray get() = enemies.indices.filter { enemies[it].boss }.toIntArray()
    fun upgradeIndex(id: String): Int { for (i in hubUpgrades.indices) if (hubUpgrades[i].id == id) return i; return -1 }
    fun dishIndex(id: String): Int { for (i in dishes.indices) if (dishes[i].id == id) return i; return -1 }
    fun creatureIndex(id: String): Int { for (i in creatures.indices) if (creatures[i].id == id) return i; return -1 }
    fun heroIndex(id: String): Int { for (i in heroes.indices) if (heroes[i].id == id) return i; return -1 }
    fun evolutionForWeapon(index: Int): Int { for (i in evolutions.indices) if (evolutions[i].weaponIndex == index) return i; return -1 }
}
