package com.tavernrush.game.entities

import com.tavernrush.game.content.EnemyDef

/** Враг из пула World.enemies. Мёртвые помечаются alive=false и удаляются в конце тика. */
class Enemy {
    var alive = false
    /** Уникальный номер появления (защита от «переиспользованных» слотов пула). */
    var uid = 0
    lateinit var def: EnemyDef
    var x = 0f
    var y = 0f
    /** Скорость отбрасывания (затухает). */
    var kx = 0f
    var ky = 0f
    /** Направление полёта для стаи (SWARM). */
    var dirX = 0f
    var dirY = 0f
    var hp = 1f
    var maxHp = 1f
    var radius = 10f
    var speed = 50f
    var damage = 5f
    var xp = 1
    var elite = false
    /** Стая: летит по прямой (dirX, dirY) и исчезает вдали, не преследует. */
    var swarm = false
    /** Вспышка от удара, с. */
    var flash = 0f
    var slowTimer = 0f
    var slowMul = 1f
    /** > 0: враг проявляется (телеграф), не двигается и не бьёт. */
    var spawnFade = 0f
    var phase = 0f
    var anim = 0f
    var faceLeft = false
    /** Убит оружием (true) или тихо убран (false, без лута). */
    var killed = false
    /** Время последнего удара от оружия в слоте i (для орбит/бумерангов). */
    val lastHit = FloatArray(MAX_SLOTS)

    // ---- R03: роли, боссы, немезида
    /** Состояние конечного автомата роли (см. EnemyAI/BossSystem) и таймер состояния, с. */
    var state = 0
    var stateT = 0f
    /** Перезарядка способности, с. */
    var cd = 0f
    /** Целевая точка (рывок, телепорт, прицел) и направление рывка. */
    var tx = 0f
    var ty = 0f
    var dashX = 0f
    var dashY = 0f
    /** Фаза босса (0..2) и счётчик атак. */
    var stage = 0
    var attack = 0
    /** Текущее действие босса/роли (id внутри BossSystem). */
    var act = 0
    /** Щит (поглощает урон до поломки) и аффикс элиты. */
    var shield = 0f
    var shieldMax = 0f
    var affix = 0
    var boss = false
    /** Немезида: именной мини-босс; rank растёт, если он снова убил героя. */
    var named = false
    var rank = 0
    var nameSeed = 0
    /** Оглушение (получает больше урона) и временная неуязвимость, с. */
    var stun = 0f
    var invulnT = 0f
    /** Призван шаманом (не даёт добычу, ограничение числа призывов). */
    var summoned = false
    var ownerUid = 0
    /** 0..1 — «замах» для анимации (рендер). */
    var windup = 0f
    /** Максимум здоровья на старте фазы (для баров). */
    var startMaxHp = 1f

    // ---- R04: состояния стихий (см. systems/ReactionSystem)
    /** «Облит»: проводит разряд, замерзает от мороза, даёт пар от огня. Секунды до высыхания. */
    var wetT = 0f
    /** Горит: урон [burnDmg] каждые полсекунды, пока [burnT] > 0. */
    var burnT = 0f
    var burnDmg = 0f
    var burnTick = 0f
    /** Заморожен: не двигается и не бьёт, получает больше урона. */
    var frozenT = 0f
    /** Пауза до следующей реакции на этой цели (защита от дребезга у аур). */
    var reactCd = 0f
    /** Отклик на удар для отрисовки: короткое «сплющивание». */
    var squash = 0f
    /** Кто облил: 0 — никто или оружие, иначе индекс вида существа + 1 (практика друга). */
    var wetBy = 0
    /** Обидчик существа во время встречи: идёт к нему, пока герой далеко (см. EncounterSystem). */
    var huntSite = false

    companion object {
        const val MAX_SLOTS = 6
    }
}
