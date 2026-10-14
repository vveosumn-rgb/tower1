package com.tavernrush.game.content

/*
 * Определения контента (данные). Добавление нового оружия/пассивки/врага/волны —
 * это новая запись в соответствующей таблице (Weapons.kt, Passives.kt, …).
 * Новый ТИП поведения оружия — новое значение WeaponKind + класс в systems/weapons/
 * (компилятор заставит добавить ветку в WeaponSystem.createBehavior).
 * Все тексты — на русском, коротко: они рисуются крупно на карточках.
 */

/** Как оружие атакует. Поведение реализовано в systems/weapons/. */
enum class WeaponKind { PROJECTILE, ORBIT, AURA, CHAIN, BOOMERANG, BOMB, BEAM, TRAP, SUMMON }

/** Параметры игрока, на которые влияют пассивки, блюда таверны и улучшения хаба. */
enum class Stat(val title: String) {
    MIGHT("Урон"),
    AREA("Площадь"),
    COOLDOWN("Перезарядка"),
    AMOUNT("Снаряды"),
    DURATION("Длительность"),
    PROJ_SPEED("Скорость снарядов"),
    MOVE_SPEED("Скорость бега"),
    MAX_HP("Здоровье"),
    REGEN("Лечение"),
    ARMOR("Броня"),
    MAGNET("Радиус сбора"),
    XP_GAIN("Опыт"),
    GOLD_GAIN("Золото"),
    CRIT_CHANCE("Критический удар"),
    LIFE_STEAL("Возврат здоровья"),
    DODGE("Уклонение"),
}

/** Иконки рисуются процедурно (render/Icons.kt). Новая иконка = новое значение + ветка рисования. */
enum class IconId {
    KNIFE, MUG, FLAME, LIGHTNING, PAN, BARREL,
    ALE, FINGERS, BOOT, AREA, STEW, TEA, SHIELD, HORSESHOE, BOOK, DOUBLE, LUCKY_COIN,
    HEART, WHETSTONE, PIGGY, CHICKEN, GOLD_BAG, PIE, MEAD, ROAST, HERBS, CHEESE, CHEST, SKULL, HOURGLASS,
    FROST, SUN, TRAP, RAVEN, BELL, CRIT, VAMP, FEATHER,
}

/** Как враг двигается. */
enum class EnemyBehavior {
    CHASE, WAVY, SWARM,
    /** Держит дистанцию и стреляет с прицелом (лучник, паук). */
    SHOOTER,
    /** Телеграф, рывок, оглушение после каждого рывка (кабан). */
    CHARGER,
    /** Бежит к герою, подрывается с предупреждением; убить до взрыва можно. */
    BOMBER,
    /** Держится вдали и призывает помощников (шаман). */
    SUMMONER,
    /** Дрейфует и телепортируется с предупреждением (призрак). */
    BLINKER,
    /** Босс: фазы и набор атак по id (BossSystem). */
    BOSS,
    /** Только для баннера: пришла немезида (в расписание не ставится). */
    NEMESIS,
}

/** Внешний вид врага: ключ модели в render/pixel/ModelLibrary.kt. */
enum class EnemyLook { RAT, GOBLIN, BAT, SLIME, ORC, ARCHER, BOAR, BOMBER, SHAMAN, GHOST, SPIDER, SLIMELET, RAT_KING, OGRE_CHEF, BANSHEE, STORM_HERON, ROOTBACK, PRISM_CRAB }

/** Параметры оружия на конкретном уровне. Значения «area» и «radius» перемножаются. */
class WeaponStats(
    var damage: Float = 10f,
    var cooldown: Float = 1f,
    var amount: Int = 1,
    var area: Float = 1f,
    var radius: Float = 10f,
    var speed: Float = 0f,
    var pierce: Int = 1,
    var duration: Float = 0f,
    var range: Float = 400f,
    var knockback: Float = 0f,
    var chains: Int = 0,
    var slowTime: Float = 0f,
    var slowMul: Float = 1f,
    var healOnHit: Float = 0f,
) {
    fun copy(): WeaponStats = WeaponStats(damage, cooldown, amount, area, radius, speed, pierce, duration, range, knockback, chains, slowTime, slowMul, healOnHit)

    fun applyLevel(l: LevelUp) {
        damage += l.damage
        cooldown *= 1f + l.cooldownPct
        amount += l.amount
        area += l.areaPct
        speed *= 1f + l.speedPct
        pierce += l.pierce
        duration *= 1f + l.durationPct
        range *= 1f + l.rangePct
        knockback += l.knockback
        chains += l.chains
        healOnHit += l.healOnHit
    }
}

/** Прибавка при повышении уровня оружия. text показывается на карточке («+1 нож»). */
class LevelUp(
    val text: String,
    val damage: Float = 0f,
    val cooldownPct: Float = 0f,
    val amount: Int = 0,
    val areaPct: Float = 0f,
    val speedPct: Float = 0f,
    val pierce: Int = 0,
    val durationPct: Float = 0f,
    val rangePct: Float = 0f,
    val knockback: Float = 0f,
    val chains: Int = 0,
    val healOnHit: Float = 0f,
)

class WeaponDef(
    val id: String,
    val name: String,
    val desc: String,
    val kind: WeaponKind,
    val icon: IconId,
    /** ARGB основного цвета эффекта (частицы, вспышки, цифры). */
    val color: Int,
    val base: WeaponStats,
    /** levels[i] — переход на уровень i+2. Максимальный уровень = levels.size + 1. */
    val levels: Array<LevelUp>,
    /** Стихия ударов: запускает реакции (облит + разряд и т. п.). */
    val element: Element = Element.NONE,
) {
    var index = -1
        internal set
    val maxLevel: Int get() = levels.size + 1

    /** Предрасчитанные параметры: statsByLevel[0] — уровень 1. */
    lateinit var statsByLevel: Array<WeaponStats>
        internal set

    internal fun build() {
        val list = ArrayList<WeaponStats>(maxLevel)
        var cur = base.copy()
        list.add(cur)
        for (l in levels) {
            cur = cur.copy()
            cur.applyLevel(l)
            list.add(cur)
        }
        statsByLevel = list.toTypedArray()
    }
}

/** Пассивка забега: +perLevel к параметру за каждый уровень. */
class PassiveDef(
    val id: String,
    val name: String,
    /** Короткий эффект одного уровня: «Урон +10%». */
    val desc: String,
    val icon: IconId,
    val stat: Stat,
    val perLevel: Float,
    val maxLevel: Int,
    val color: Int,
) {
    var index = -1
        internal set
}

class EnemyDef(
    val id: String,
    val name: String,
    val look: EnemyLook,
    val behavior: EnemyBehavior,
    val hp: Float,
    val speed: Float,
    val damage: Float,
    val radius: Float,
    val xp: Int,
    /** Шанс выронить монету (0..1). */
    val goldChance: Float,
    /** Сопротивление отбрасыванию (0 — летает, 1 — скала). */
    val knockbackResist: Float,
    /** Основной цвет (частицы смерти, вспышки). */
    val color: Int,
    /** Роль: желаемая дистанция (стрелки, шаманы), дистанция рывка (кабан), запал (подрывник). */
    val range: Float = 0f,
    /** Период способности (выстрел, рывок, призыв, телепорт, запал), с. */
    val cooldown: Float = 0f,
    val shotSpeed: Float = 0f,
    val shotDamage: Float = 0f,
    /** Роль-специфичное число: радиус взрыва, число призванных, скорость рывка, радиус телепорта. */
    val special: Float = 0f,
    /** После смерти распадается на splitCount врагов этого вида (слизень). */
    val splitId: String = "",
    val splitCount: Int = 0,
    /** Что призывает шаман. */
    val summonId: String = "",
    /** Подзаголовок босса на плашке. */
    val title: String = "",
    val boss: Boolean = false,
) {
    var index = -1
        internal set
    var splitIndex = -1
        internal set
    var summonIndex = -1
        internal set
}

class SpawnEntry(val enemyId: String, val weight: Int) {
    var enemyIndex = -1
        internal set
}

enum class WaveEventType {
    /** Стая летит сквозь экран по прямой. */
    SWARM,
    /** Кольцо врагов вокруг героя (с телеграфом: проявляются 1 с). */
    RING,
    /** Элитный враг: больше, сильнее, роняет сундук. */
    ELITE,
    /** Босс (enemyId — вид по умолчанию; конкретный выбирает режиссёр по сиду и реваншу). */
    BOSS,
    /** Только для баннера: пришла немезида (в расписание не ставится). */
    NEMESIS,
}

class WaveEvent(val at: Float, val type: WaveEventType, val enemyId: String, val count: Int = 1) {
    var enemyIndex = -1
        internal set
}

class WaveDef(
    val title: String,
    /** Подзаголовок баннера в начале волны. */
    val subtitle: String,
    val duration: Float,
    /** Спавн в секунду в начале и в конце волны (линейно). */
    val rateStart: Float,
    val rateEnd: Float,
    val maxAlive: Int,
    val hpMul: Float,
    val dmgMul: Float,
    val speedMul: Float,
    val pool: Array<SpawnEntry>,
    val events: Array<WaveEvent> = emptyArray(),
    /** После этой волны — «Привал в таверне»: выбор блюда. */
    val tavernAfter: Boolean = false,
) {
    var totalWeight = 0
        internal set
}

/** Постоянное улучшение таверны-хаба (мета-прогрессия). */
class HubUpgradeDef(
    val id: String,
    val name: String,
    /** Эффект одного уровня: «Здоровье +10». */
    val desc: String,
    val icon: IconId,
    val stat: Stat,
    val perLevel: Float,
    val maxLevel: Int,
    val baseCost: Int,
    val costGrowth: Float,
) {
    var index = -1
        internal set
}

/** Блюдо на «Привале в таверне»: бонус до конца забега, лечение или золото. */
class DishDef(
    val id: String,
    val name: String,
    val desc: String,
    val icon: IconId,
    val stat: Stat?,
    val amount: Float,
    /** Доля лечения от максимума здоровья (1 = полное). */
    val healPct: Float = 0f,
    val gold: Int = 0,
) {
    var index = -1
        internal set
}
