package com.tavernrush.game.systems

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.EnemyBehavior
import com.tavernrush.game.content.Weapons
import com.tavernrush.game.core.GameEvents
import com.tavernrush.game.core.Haptic
import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.Sfx
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.EnemyShot
import com.tavernrush.game.entities.Hazard
import com.tavernrush.game.entities.FloatText
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.Pickup
import com.tavernrush.game.entities.Player
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.entities.WeaponInstance
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Мир одного забега: чистый Kotlin без android.* (гоняется в JVM-тестах и бот-симуляции).
 * Все сущности — в заранее выделенных пулах; tick() не создаёт объектов.
 * Порядок тика: волны → герой → враги → сетка → расталкивание → оружие → контакт → уборка → лут → эффекты → камера.
 */
class World {
    /** Звук/вибрация (Game подставляет фильтр по настройкам). */
    var events: GameEvents = GameEvents.None

    // Независимые потоки ГПСЧ: эффекты и выбор карт не сдвигают спавн (детерминизм симуляций).
    val rngSpawn = Rng(1)
    val rngLoot = Rng(2)
    val rngCards = Rng(3)
    val rngCombat = Rng(4)
    val rngFx = Rng(5)
    /** Отдельный поток для решений ИИ врагов и боссов: не сдвигает спавн, лут и бой. */
    val rngAi = Rng(6)

    val adventure=AdventureSystem(this)
    val heroAction=HeroAction(this)
    val player = Player()
    val stats = PlayerStats()
    val run = RunStats()

    val enemies = Array(MAX_ENEMIES) { Enemy() }
    var enemyCount = 0
    val projectiles = Array(MAX_PROJECTILES) { Projectile() }
    var projectileCount = 0
    val pickups = Array(MAX_PICKUPS) { Pickup() }
    var pickupCount = 0
    val weapons = Array(MAX_WEAPONS) { WeaponInstance(it) }
    var weaponCount = 0
    /** Телеграфы атак врагов и зоны на земле; снаряды врагов. */
    val hazards = Array(MAX_HAZARDS) { Hazard() }
    var hazardCount = 0
    val shots = Array(MAX_SHOTS) { EnemyShot() }
    var shotCount = 0

    /** Уровни пассивок по индексу Content.passives (0 — нет). */
    val passiveLevels = IntArray(Content.passives.size)
    /** Порядок получения пассивок (для HUD). */
    val passiveOrder = IntArray(MAX_PASSIVES)
    var passiveCount = 0
    /** Бонусы блюд таверны (до конца забега) и улучшений хаба, по Stat.ordinal. */
    val dishBonus = FloatArray(Content.statCount)
    val metaBonus = FloatArray(Content.statCount)

    val fx = FxSystem(rngFx)
    val hash = SpatialHash(this)
    val director = WaveDirector(this)
    val combat = CombatSystem(this)
    val pickupSystem = PickupSystem(this)
    val weaponSystem = WeaponSystem(this)
    val cards = CardSystem(this)
    val ai = EnemyAI(this)
    val boss = BossSystem(this)
    val hazardSystem = HazardSystem(this)
    val reactions = ReactionSystem(this)
    val team = CompanionSystem(this)
    val encounter = EncounterSystem(this)

    var seed = 0L
        private set
    var time = 0f
        private set
    var ticks = 0
        private set

    /** Ввод движения −1..1 (длина ≤ 1), задаётся стиком или ботом перед tick(). */
    var moveX = 0f
    var moveY = 0f
    var camX = 0f
    var camY = 0f

    var pendingLevelUps = 0
    var pendingChests = 0
    var pendingTavern = 0
    var deathTimer = 0f
        private set
    var victory = false
        private set
    var victoryTimer = 0f
        private set
    /** Таймер баннера «На волосок!» и его награда. */
    var nearMissTimer = 0f
    var nearMissGold = 0
        private set
    /** Награда одна на опасный эпизод; для нового нужно сначала восстановить здоровье. */
    private var nearMissArmed = true
    /** Таймер вспышки «Новый уровень» над героем (визуал). */
    var levelFlash = 0f
    /** Неуязвимость (сценарии скриншотов и стресс-тесты). */
    var godMode = false
    var heroIndex = 0
        private set
    private val heroBonus = FloatArray(Content.statCount)
    var evolutionTimer = 0f
    var lastEvolution = -1
        private set

    // ---- R03: время, рывок, босс и немезида
    /** Хит-стоп (с): мир замирает на крупных ударах, эффекты доигрывают замедленно. */
    var hitStop = 0f
        private set
    /** Замедление времени после идеального уклона (с) и текущий множитель. */
    var slowmoT = 0f
    var timeScale = 1f
        private set
    /** Таймер баннера «Идеально!» и баннера смены фазы босса. */
    var perfectTimer = 0f
    var bossPhaseTimer = 0f
    var revengeTimer = 0f
    /** Забег с подготовленной немезидой: вид, ранг, волна смерти и сид имени. */
    var nemIndex = -1
        private set
    var nemRank = 0
        private set
    var nemWave = 0
        private set
    var nemSeed = 0
        private set
    var nemSpawned = false
    /** Предложенный реванш: id босса, который должен появиться в первом подходящем слоте. */
    var rematchBoss = ""
    /** Опережение камеры по направлению бега. */
    private var leadX = 0f
    private var leadY = 0f
    /** Остаточные образы рывка: кольцевой буфер (x, y, возраст, направление). */
    val trailX = FloatArray(TRAIL)
    val trailY = FloatArray(TRAIL)
    val trailAge = FloatArray(TRAIL)
    val trailDir = IntArray(TRAIL)
    private var trailHead = 0

    /** Общий буфер индексов для запросов к сетке (используется последовательно, не вкладывать). */
    val tmpIdx = IntArray(MAX_ENEMIES)
    private val sepIdx = IntArray(12)
    private var nextUid = 1

    fun reset(seed: Long, meta: FloatArray?, hero: Int = 0) {
        this.seed = seed
        heroIndex = if (hero in Content.heroes.indices) hero else 0
        System.arraycopy(Content.heroes[heroIndex].bonuses, 0, heroBonus, 0, heroBonus.size)
        rngSpawn.reseed(Rng.stream(seed, 1))
        rngLoot.reseed(Rng.stream(seed, 2))
        rngCards.reseed(Rng.stream(seed, 3))
        rngCombat.reseed(Rng.stream(seed, 4))
        rngFx.reseed(Rng.stream(seed, 5))
        rngAi.reseed(Rng.stream(seed, 6))
        player.reset()
        for (i in 0 until enemyCount) enemies[i].alive = false
        enemyCount = 0
        for (i in 0 until projectileCount) projectiles[i].alive = false
        projectileCount = 0
        for (i in 0 until pickupCount) pickups[i].alive = false
        pickupCount = 0
        weaponCount = 0
        passiveLevels.fill(0)
        passiveCount = 0
        dishBonus.fill(0f)
        metaBonus.fill(0f)
        if (meta != null) System.arraycopy(meta, 0, metaBonus, 0, minOf(meta.size, metaBonus.size))
        fx.reset()
        reactions.reset()
        team.reset()
        encounter.reset()
        adventure.reset();heroAction.reset()
        director.reset()
        run.reset()
        cards.reset()
        time = 0f; ticks = 0
        moveX = 0f; moveY = 0f; camX = 0f; camY = 0f
        pendingLevelUps = 0; pendingChests = 0; pendingTavern = 0
        deathTimer = 0f; victory = false; victoryTimer = 0f
        nearMissTimer = 0f; nearMissGold = 0; nearMissArmed = true; levelFlash = 0f
        evolutionTimer = 0f; lastEvolution = -1
        nextUid = 1
        for (i in 0 until hazardCount) hazards[i].alive = false
        hazardCount = 0
        for (i in 0 until shotCount) shots[i].alive = false
        shotCount = 0
        hitStop = 0f; slowmoT = 0f; timeScale = 1f; perfectTimer = 0f; bossPhaseTimer = 0f; revengeTimer = 0f
        nemIndex = -1; nemRank = 0; nemWave = 0; nemSeed = 0; nemSpawned = false; rematchBoss = ""
        leadX = 0f; leadY = 0f
        trailAge.fill(99f)
        recomputeStats()
        player.hp = player.maxHp
        addWeapon(Content.heroes[heroIndex].weaponIndex)
    }

    /** Подготовить немезиду для этого забега (вызывает Game после reset). */
    fun setNemesis(defId: String, rank: Int, wave: Int, seed: Int) {
        val i = Content.enemyIndex(defId)
        if (i < 0 || Content.enemies[i].boss || rank < 1) { nemIndex = -1; return }
        nemIndex = i; nemRank = rank.coerceIn(1, 9); nemWave = wave.coerceIn(1, Content.waves.size); nemSeed = seed; nemSpawned = false
    }

    fun tick(dtReal: Float) {
        if (!dtReal.isFinite() || dtReal <= 0f || dtReal > 0.25f) return
        ticks++
        if (nearMissTimer > 0f) nearMissTimer -= dtReal
        if (levelFlash > 0f) levelFlash -= dtReal
        if (evolutionTimer > 0f) evolutionTimer -= dtReal
        if (perfectTimer > 0f) perfectTimer -= dtReal
        if (bossPhaseTimer > 0f) bossPhaseTimer -= dtReal
        if (revengeTimer > 0f) revengeTimer -= dtReal
        for (i in 0 until TRAIL) trailAge[i] += dtReal
        val p = player
        if (hitStop > 0f && !p.dead) {
            // Хит-стоп: логика стоит, эффекты доигрывают в замедлении.
            hitStop -= dtReal
            fx.update(dtReal * 0.25f)
            updateCamera(dtReal)
            return
        }
        var dt = dtReal
        if (slowmoT > 0f) { slowmoT -= dtReal; timeScale = SLOWMO_SCALE; dt = dtReal * SLOWMO_SCALE } else timeScale = 1f
        if (p.dead) {
            // Замедленная «смерть»: мир доигрывает в slow-mo, пока Game не покажет итоги.
            deathTimer += dt
            val sdt = dt * 0.3f
            updateEnemies(sdt)
            fx.update(sdt)
            return
        }
        time += dt
        run.time = time
        if (victory) {
            victoryTimer += dt
            updatePlayer(dt)
            pickupSystem.update(dt)
            fx.update(dt)
            updateCamera(dt)
            return
        }
        if(!adventure.active)director.update(dt)
        if (victory) return
        if(!adventure.active)encounter.update(dt)
        val oldX=p.x;val oldY=p.y
        updatePlayer(dt)
        adventure.sweepPlayer(oldX,oldY)
        adventure.update(dt)
        updateEnemies(dt)
        if(adventure.active)for(i in 0 until enemyCount){val e=enemies[i];e.x=e.x.coerceIn(-640f,640f);e.y=e.y.coerceIn(-940f,640f)}
        hash.rebuild(p.x, p.y)
        separate()
        adventure.resolveEnemies()
        heroAction.update(dt)
        adventure.resolvePlayer()
        reactions.update(dt)
        weaponSystem.update(dt)
        team.update(dt)
        combat.contactDamage()
        hazardSystem.update(dt)
        compactEnemies()
        pickupSystem.update(dt)
        fx.update(dt)
        updateCamera(dt)
        trackNearMiss()
        if (enemyCount > run.maxAlive) run.maxAlive = enemyCount
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        var mx = if (moveX.isFinite()) moveX.coerceIn(-1f, 1f) else 0f
        var my = if (moveY.isFinite()) moveY.coerceIn(-1f, 1f) else 0f
        val m2 = mx * mx + my * my
        if (m2 > 1f) {
            val inv = 1f / sqrt(m2)
            mx *= inv; my *= inv
        }
        if (p.dodgeT > 0f) p.dodgeT -= dt
        if (p.slowT > 0f) p.slowT -= dt else p.slowMul = 1f
        if (p.dashCharges < Player.MAX_DASH) {
            p.dashRecharge -= dt
            if (p.dashRecharge <= 0f) {
                p.dashCharges++
                p.dashRecharge = DASH_RECHARGE
            }
        }
        if (p.dashT > 0f) {
            p.dashT -= dt
            p.vx = p.dashX * DASH_SPEED
            p.vy = p.dashY * DASH_SPEED
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.moving = true
            p.faceX = p.dashX; p.faceY = p.dashY
            p.walk += dt * 16f
            val h = trailHead
            trailHead = (trailHead + 1) % TRAIL
            trailX[h] = p.x; trailY[h] = p.y; trailAge[h] = 0f; trailDir[h] = if (p.dashX < -0.1f) 1 else 0
            if ((ticks and 1) == 0) fx.puff(p.x, p.y + 10f, 0xFFD9D2C0.toInt(), 1, 4f)
        } else {
            val sp = BASE_SPEED * stats.moveSpeed * (if (p.slowT > 0f) p.slowMul else 1f)
            val tvx = mx * sp
            val tvy = my * sp
            // Разгон и торможение: движение ощущается весомым, но откликается за ~1/20 с.
            val accel = if (m2 > 0.0025f) ACCEL else DECEL
            val dvx = tvx - p.vx
            val dvy = tvy - p.vy
            val dl = sqrt(dvx * dvx + dvy * dvy)
            val step = accel * dt * (if(adventure.icy(p.x,p.y))0.18f else 1f)
            if (dl <= step) { p.vx = tvx; p.vy = tvy } else { p.vx += dvx / dl * step; p.vy += dvy / dl * step }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.moving = m2 > 0.0025f
            if (p.moving) {
                val l = sqrt(mx * mx + my * my)
                p.faceX = mx / l
                p.faceY = my / l
                p.walk += dt * (5f + 7f * l)
                if ((ticks and 7) == 0 && fx.particleCount < 700) fx.puff(p.x, p.y + 12f, 0xFFB8C49A.toInt(), 1, 3f)
            }
        }
        if (p.invuln > 0f) p.invuln -= dt
        if (p.hurtFlash > 0f) p.hurtFlash -= dt
        if (stats.regen > 0f && p.hp < p.maxHp) {
            val before = p.hp
            p.hp += stats.regen * dt
            if (p.hp > p.maxHp) p.hp = p.maxHp
            run.healed += p.hp - before
        }
    }

    /**
     * Рывок героя в направлении (dx, dy) (если нулевое — по взгляду). Даёт окно неуязвимости;
     * удар в нём превращается в «идеальный уклон». false — нет зарядов, рывок идёт или герой мёртв.
     */
    fun dash(dx: Float, dy: Float): Boolean {
        val p = player
        if (p.dead || victory || p.dashT > 0f || p.dashCharges <= 0 || !dx.isFinite() || !dy.isFinite()) return false
        var ddx = dx; var ddy = dy
        var l = sqrt(ddx * ddx + ddy * ddy)
        if (l < 0.12f) { ddx = p.faceX; ddy = p.faceY; l = sqrt(ddx * ddx + ddy * ddy) }
        if (l < 0.001f) { ddx = 1f; ddy = 0f; l = 1f }
        p.dashX = ddx / l; p.dashY = ddy / l
        p.dashT = DASH_TIME
        p.dodgeT = DASH_IFRAMES
        p.dashCharges--
        if (p.dashRecharge <= 0f || p.dashCharges == Player.MAX_DASH - 1) p.dashRecharge = DASH_RECHARGE
        run.dashes++
        fx.ring(p.x, p.y + 10f, 10f, 70f, 0.28f, 0xFFD9F2FF.toInt(), 4f)
        fx.puff(p.x, p.y + 10f, 0xFFD9D2C0.toInt(), 5, 6f)
        events.sfx(Sfx.DASH)
        events.haptic(Haptic.DASH)
        return true
    }

    /** Удар пришёлся в окно рывка: награда за мастерство — замедление, возврат заряда, опыт. */
    fun onPerfectDodge() {
        val p = player
        if (time - p.lastPerfect < 1.1f) return
        p.lastPerfect = time
        run.perfectDodges++
        slowmoT = 0.6f
        if (p.dashCharges < Player.MAX_DASH) p.dashCharges++
        perfectTimer = 1.3f
        addXp(2f + director.waveIndex * 0.6f)
        fx.ring(p.x, p.y + 6f, 14f, 150f, 0.5f, 0xFF9EEBFF.toInt(), 6f)
        fx.sparks(p.x, p.y - 8f, 0xFF9EEBFF.toInt(), 10, 340f)
        fx.addAberration(0.7f)
        fx.light(p.x, p.y, 120f, 0xFF9EEBFF.toInt(), 0.9f, 0.3f)
        events.sfx(Sfx.PERFECT)
        events.haptic(Haptic.PERFECT)
    }

    fun requestHitStop(sec: Float) {
        if (sec > hitStop) hitStop = sec.coerceAtMost(0.12f)
    }

    private val enemyRoute=RegionNavigator()
    private val enemyWaypoint=FloatArray(2)
    private fun updateEnemies(dt: Float) {
        val p = player
        val es = enemies
        var decay = 1f - KNOCK_DECAY * dt
        if (decay < 0f) decay = 0f
        val keep = p.radius + 2f
        for (i in 0 until enemyCount) {
            val e = es[i]
            if (!e.alive) continue
            if (e.frozenT > 0f) {
                // Заморожен: стоит как статуя (кадр анимации, перезарядка и отбрасывание замирают).
                if (e.flash > 0f) e.flash -= dt
                e.kx = 0f; e.ky = 0f
                continue
            }
            e.anim += dt
            if (e.flash > 0f) e.flash -= dt
            if (e.invulnT > 0f) e.invulnT -= dt
            if (e.stun > 0f) e.stun -= dt
            if (e.cd > 0f) e.cd -= dt
            if (e.spawnFade > 0f) {
                e.spawnFade -= dt
                continue
            }
            var sp = e.speed
            if (e.slowTimer > 0f) {
                e.slowTimer -= dt
                sp *= e.slowMul
            } else e.slowMul = 1f
            var aimX = p.x
            var aimY = p.y
            if(e.huntSite&&adventure.active&&adventure.defence==2){val x=p.x-e.x;val y=p.y-e.y;if(x*x+y*y>160f*160f){aimX=adventure.nodesX[6];aimY=adventure.nodesY[6]}}
            if(adventure.active&&adventure.baitT>0f&&!e.boss&&!e.named){aimX=adventure.baitX;aimY=adventure.baitY}
            if (e.huntSite && encounter.active) {
                // Обидчик существа: пока герой далеко — идёт к существу, как только герой рядом — за героем.
                val hx = p.x - e.x; val hy = p.y - e.y
                if (hx * hx + hy * hy > EncounterSystem.AGGRO * EncounterSystem.AGGRO) { aimX = encounter.x; aimY = encounter.y }
            }
            val targetX=aimX;val targetY=aimY
            if(adventure.active&&!e.boss&&!e.swarm){enemyRoute.next(adventure.map,adventure.flags,e.x,e.y,aimX,aimY,e.radius.coerceAtMost(30f),enemyWaypoint);aimX=enemyWaypoint[0];aimY=enemyWaypoint[1]}
            val dx = aimX - e.x
            val dy = aimY - e.y
            val d2 = dx * dx + dy * dy
            val beh = e.def.behavior
            var handled = false
            if (beh != EnemyBehavior.CHASE && beh != EnemyBehavior.WAVY && beh != EnemyBehavior.SWARM) {
                if (!e.boss && !e.named && d2 > DESPAWN_DIST * DESPAWN_DIST) {
                    director.pickSpawnPoint()
                    e.x = director.sx; e.y = director.sy
                    e.kx = 0f; e.ky = 0f
                    e.state = 0
                    continue
                }
                handled = ai.update(e, dt, sp)
            }
            if (handled) {
                // роль сама двигает врага
            } else if (e.swarm) {
                e.x += e.dirX * sp * dt
                e.y += e.dirY * sp * dt
                // Стая пролетела мимо и ушла далеко — тихо убираем (без лута).
                if (d2 > SWARM_DESPAWN * SWARM_DESPAWN && dx * e.dirX + dy * e.dirY < 0f) {
                    e.alive = false; e.killed = false
                    continue
                }
            } else {
                if (d2 > DESPAWN_DIST * DESPAWN_DIST && !e.named) {
                    // Отставшие враги переносятся на край обзора впереди героя (давление не пропадает).
                    director.pickSpawnPoint()
                    e.x = director.sx; e.y = director.sy
                    e.kx = 0f; e.ky = 0f
                    continue
                }
                val d = sqrt(d2)
                // Отставшие догоняют быстрее: от толпы нельзя просто убежать.
                if (d > CATCH_UP_DIST) sp *= CATCH_UP_MUL
                if (d > 0.001f) {
                    var nx = dx / d
                    var ny = dy / d
                    if (beh == EnemyBehavior.WAVY) {
                        // Летучие мыши подлетают зигзагом.
                        e.phase += dt * 5f
                        val s = sin(e.phase) * 0.85f
                        val wx = nx - ny * s
                        val wy = ny + nx * s
                        val wl = sqrt(wx * wx + wy * wy)
                        nx = wx / wl; ny = wy / wl
                    }
                    val contact = if(aimX!=targetX||aimY!=targetY)0f else e.radius + keep
                    if (d > contact) {
                        var step = sp * dt
                        if (step > d - contact) step = d - contact
                        e.x += nx * step
                        e.y += ny * step
                    } else {
                        // Не залезать внутрь героя: выталкиваем на контактное расстояние.
                        val push = contact - d
                        e.x -= nx * push
                        e.y -= ny * push
                    }
                }
            }
            e.x += e.kx * dt
            e.y += e.ky * dt
            e.kx *= decay
            e.ky *= decay
            if (!handled) {
                if (dx < -2f) e.faceLeft = true else if (dx > 2f) e.faceLeft = false
            }
        }
    }

    /** Мягкое расталкивание врагов (половина врагов за тик — экономия CPU). */
    private fun separate() {
        val es = enemies
        val parity = ticks and 1
        for (i in 0 until enemyCount) {
            if ((i and 1) != parity) continue
            val e = es[i]
            if (!e.alive || e.swarm || e.spawnFade > 0f) continue
            val n = hash.query(e.x, e.y, e.radius * 0.9f, sepIdx)
            for (k in 0 until n) {
                val j = sepIdx[k]
                if (j == i) continue
                val o = es[j]
                if (o.swarm) continue
                var dx = e.x - o.x
                var dy = e.y - o.y
                val rr = (e.radius + o.radius) * 0.9f
                val d2 = dx * dx + dy * dy
                if (d2 >= rr * rr) continue
                var d = sqrt(d2)
                if (d < 0.01f) { dx = 1f; dy = 0f; d = 1f }
                val push = (rr - d) * 0.5f
                // Тяжёлые сдвигаются меньше лёгких.
                val me = e.radius * e.radius
                val mo = o.radius * o.radius
                val ke = mo / (me + mo)
                e.x += dx / d * push * ke
                e.y += dy / d * push * ke
                o.x -= dx / d * push * (1f - ke)
                o.y -= dy / d * push * (1f - ke)
            }
        }
    }

    private fun compactEnemies() {
        val es = enemies
        var i = 0
        while (i < enemyCount) {
            if (!es[i].alive) {
                enemyCount--
                val t = es[i]
                es[i] = es[enemyCount]
                es[enemyCount] = t
            } else i++
        }
    }

    private fun updateCamera(dt: Float) {
        val p = player
        // Камера заглядывает вперёд по бегу — видно, куда идёшь; на рывке уходит дальше.
        var k = 4f * dt
        if (k > 1f) k = 1f
        val boost = if (p.dashT > 0f) 1.5f else 1f
        leadX += (p.vx * LEAD * boost - leadX) * k
        leadY += (p.vy * LEAD * boost - leadY) * k
        camX = M2.follow(camX, p.x + leadX, dt)
        camY = M2.follow(camY, p.y + leadY, dt)
    }

    private fun trackNearMiss() {
        val p = player
        val f = p.hpFrac
        if (f < run.minHpFrac) run.minHpFrac = f
        if (p.dead) return
        if (!nearMissArmed) {
            if (f >= NEAR_MISS_RECOVER) nearMissArmed = true
            return
        }
        if (run.lowHpSince < 0f) {
            if (f < NEAR_MISS_LOW) run.lowHpSince = time
        } else if (f >= NEAR_MISS_RECOVER || time - run.lowHpSince >= NEAR_MISS_HOLD) {
            run.lowHpSince = -1f
            nearMissArmed = false
            run.nearMisses++
            nearMissGold = 5 + 2 * director.waveIndex
            run.gold += nearMissGold * stats.goldGain
            nearMissTimer = 2.6f
            fx.burst(p.x, p.y, 0xFFFFE066.toInt(), 22, 320f, 7f, 0.8f, Particle.STAR)
            events.sfx(Sfx.CHEST)
            events.haptic(Haptic.TICK)
        }
    }

    fun spawnEnemy(defIndex: Int, x: Float, y: Float, elite: Boolean): Enemy? {
        if (enemyCount >= MAX_ENEMIES) return null
        val def = Content.enemies[defIndex]
        val wv = director.wave
        val e = enemies[enemyCount++]
        e.alive = true
        e.uid = nextUid++
        e.def = def
        e.x = x; e.y = y
        e.kx = 0f; e.ky = 0f; e.dirX = 0f; e.dirY = 0f
        e.elite = elite
        e.maxHp = def.hp * wv.hpMul * (if (elite) ELITE_HP else 1f)
        e.hp = e.maxHp
        e.radius = def.radius * (if (elite) ELITE_SIZE else 1f)
        e.speed = def.speed * wv.speedMul * (0.92f + 0.16f * rngSpawn.nextFloat())
        e.damage = def.damage * wv.dmgMul * (if (elite) ELITE_DMG else 1f)
        e.xp = if (elite) def.xp * ELITE_XP else def.xp
        e.swarm = false
        e.flash = 0f
        e.slowTimer = 0f; e.slowMul = 1f
        e.spawnFade = 0f
        e.phase = rngSpawn.angle()
        e.anim = rngSpawn.nextFloat() * 4f
        e.faceLeft = x > player.x
        e.killed = false
        e.lastHit.fill(-100f)
        e.state = 0; e.stateT = 0f
        e.cd = if (def.cooldown > 0f) def.cooldown * (0.35f + 0.75f * rngSpawn.nextFloat()) else 0f
        e.tx = 0f; e.ty = 0f; e.dashX = 0f; e.dashY = 0f
        e.stage = 0
        e.attack = 0
        e.shield = 0f; e.shieldMax = 0f; e.affix = 0
        e.boss = false; e.named = false; e.rank = 0; e.nameSeed = 0
        e.stun = 0f; e.invulnT = 0f; e.summoned = false; e.ownerUid = 0; e.windup = 0f
        e.startMaxHp = e.maxHp
        e.wetT = 0f; e.burnT = 0f; e.burnDmg = 0f; e.burnTick = 0f; e.frozenT = 0f; e.reactCd = 0f; e.squash = 0f
        e.wetBy = 0; e.huntSite = false
        if (elite && !def.boss) applyAffix(e, 1 + rngSpawn.nextInt(AFFIX_COUNT))
        return e
    }

    private fun applyAffix(e: Enemy, affix: Int) {
        e.affix = affix
        when (affix) {
            AFFIX_FAST -> e.speed *= 1.45f
            AFFIX_SHIELDED -> { e.shieldMax = e.maxHp * 0.3f; e.shield = e.shieldMax }
        }
    }

    /** Освободить слот пула: убрать самого далёкого обычного врага (боссу и немезиде место нужно всегда). */
    private fun evictFarthest() {
        var best = -1
        var bd = -1f
        for (i in 0 until enemyCount) {
            val q = enemies[i]
            if (!q.alive || q.boss || q.named) continue
            val dx = q.x - player.x; val dy = q.y - player.y
            val d = dx * dx + dy * dy
            if (d > bd) { bd = d; best = i }
        }
        if (best >= 0) { enemies[best].alive = false; enemies[best].killed = false; compactEnemies() }
    }

    /** Босс: фиксированное здоровье вида, вход с замедлением и неуязвимостью на время представления. */
    fun spawnBoss(defIndex: Int): Enemy? {
        if (defIndex !in Content.enemies.indices || !Content.enemies[defIndex].boss) return null
        if (enemyCount >= MAX_ENEMIES) evictFarthest()
        val def = Content.enemies[defIndex]
        val e = spawnEnemy(defIndex, player.x + rngSpawn.signed() * 140f, player.y - 540f, false) ?: return null
        e.boss = true
        e.maxHp = def.hp; e.hp = def.hp; e.startMaxHp = def.hp
        e.radius = def.radius; e.speed = def.speed
        e.damage = def.damage * director.wave.dmgMul
        e.xp = def.xp
        e.state = BossSystem.INTRO; e.stateT = 1.9f; e.invulnT = 1.9f
        e.cd = 2.2f
        fx.ring(e.x, e.y, 30f, 360f, 0.9f, 0xFFFFB347.toInt(), 12f)
        fx.addShake(0.7f)
        fx.addAberration(0.5f)
        fx.light(e.x, e.y, 260f, 0xFFFFB347.toInt(), 1f, 0.8f)
        events.sfx(Sfx.ROAR)
        events.haptic(Haptic.BIG)
        run.bossesSeen++
        return e
    }

    /** Немезида — именной мини-босс из вида, убившего героя в прошлый раз; ранг делает её сильнее. */
    fun spawnNemesis(): Enemy? {
        if (nemIndex < 0 || nemSpawned) return null
        if (enemyCount >= MAX_ENEMIES) evictFarthest()
        val def = Content.enemies[nemIndex]
        val a = rngSpawn.angle()
        val e = spawnEnemy(nemIndex, player.x + cos(a) * 560f, player.y + sin(a) * 640f, false) ?: return null
        val wv = director.wave
        val rk = 1f + 0.3f * (nemRank - 1)
        e.named = true; e.rank = nemRank; e.nameSeed = nemSeed
        e.maxHp = def.hp * wv.hpMul * 14f * rk; e.hp = e.maxHp; e.startMaxHp = e.maxHp
        e.radius = def.radius * NAMED_SIZE
        e.damage = def.damage * wv.dmgMul * 1.7f * (1f + 0.1f * (nemRank - 1))
        e.speed *= 1.1f
        e.xp = def.xp * 20
        nemSpawned = true
        fx.ring(e.x, e.y, 20f, 260f, 0.8f, 0xFFD63A4A.toInt(), 10f)
        fx.addShake(0.45f)
        events.sfx(Sfx.ROAR)
        events.haptic(Haptic.BIG)
        return e
    }

    /** Активный босс (первый живой) или null. */
    fun activeBoss(): Enemy? {
        for (i in 0 until enemyCount) { val e = enemies[i]; if (e.alive && e.boss) return e }
        return null
    }

    fun activeNemesis(): Enemy? {
        for (i in 0 until enemyCount) { val e = enemies[i]; if (e.alive && e.named) return e }
        return null
    }

    fun spawnHazard(
        kind: Int, x: Float, y: Float, r: Float, delay: Float, burst: Float, color: Int,
        life: Float = 0f, ang: Float = 0f, len: Float = 0f, owner: Int = 0, ownerDef: Int = -1,
        visual: Boolean = false, slow: Float = 0f, tick: Float = 0.5f, r1: Float = 0f, damage: Float = 0f,
    ): Hazard? {
        if (hazardCount >= MAX_HAZARDS) return null
        val h = hazards[hazardCount++]
        h.alive = true; h.kind = kind
        h.x = x; h.y = y; h.r = r; h.len = len; h.ang = ang
        h.delay = delay; h.maxDelay = delay
        h.life = life; h.maxLife = life
        h.burst = burst; h.damage = damage; h.tick = tick; h.tickT = 0f
        h.state = 0; h.color = color; h.hit = false
        h.owner = owner; h.ownerDef = ownerDef
        h.slow = slow; h.visual = visual
        h.gapAngle=0f;h.gapHalf=0f
        h.r0 = r; h.r1 = if (r1 > 0f) r1 else r
        return h
    }

    /** Отменить телеграфы, созданные врагом (подрывника убили до взрыва). */
    fun cancelHazards(owner: Int) {
        for (i in 0 until hazardCount) { val h = hazards[i]; if (h.owner == owner && h.state == 0) h.alive = false }
    }

    fun spawnShot(kind: Int, x: Float, y: Float, vx: Float, vy: Float, damage: Float, radius: Float, life: Float, color: Int, ownerDef: Int, slowTime: Float = 0f, slowMul: Float = 1f, spin: Float = 0f): EnemyShot? {
        if (shotCount >= MAX_SHOTS) return null
        val s = shots[shotCount++]
        s.alive = true; s.kind = kind
        s.x = x; s.y = y; s.vx = vx; s.vy = vy
        s.damage = damage; s.radius = radius
        s.life = life; s.maxLife = life
        s.rot = kotlin.math.atan2(vy, vx); s.spin = spin
        s.color = color; s.ownerDef = ownerDef
        s.slowTime = slowTime; s.slowMul = slowMul
        return s
    }

    fun spawnProjectile(kind: Int, slot: Int, x: Float, y: Float): Projectile? {
        if (projectileCount >= MAX_PROJECTILES) return null
        val p = projectiles[projectileCount++]
        p.alive = true
        p.kind = kind; p.slot = slot
        p.x = x; p.y = y; p.vx = 0f; p.vy = 0f
        p.damage = 0f; p.radius = 8f; p.knockback = 0f; p.pierceLeft = 1
        p.life = 1f; p.maxLife = 1f; p.rot = 0f; p.spin = 0f
        p.state = 0; p.speed = 0f; p.accel = 0f
        p.sx = x; p.sy = y; p.tx = x; p.ty = y; p.t = 0f; p.timer = 0f; p.z = 0f
        p.hitCount = 0
        return p
    }

    // ---------------------------------------------------------------- билд

    fun findWeapon(defIndex: Int): Int {
        for (i in 0 until weaponCount) if (weapons[i].def.index == defIndex) return i
        return -1
    }

    fun addWeapon(defIndex: Int): Boolean {
        if (weaponCount >= MAX_WEAPONS || defIndex !in Content.weapons.indices || findWeapon(defIndex) >= 0) return false
        val wi = weapons[weaponCount]
        wi.reset(Content.weapons[defIndex])
        weaponCount++
        val recipe = Content.evolutionForWeapon(defIndex)
        wi.synergyActive = recipe >= 0 && passiveLevels[Content.evolutions[recipe].passiveIndex] > 0
        wi.recompute(stats)
        weaponSystem.onEquip(wi)
        return true
    }

    fun upgradeWeapon(slot: Int): Boolean {
        if (slot < 0 || slot >= weaponCount) return false
        val wi = weapons[slot]
        if (wi.level >= wi.def.maxLevel) return false
        wi.level++
        wi.recompute(stats)
        return true
    }

    fun addPassive(index: Int): Boolean {
        if (index !in Content.passives.indices) return false
        val def = Content.passives[index]
        val lvl = passiveLevels[index]
        if (lvl >= def.maxLevel) return false
        if (lvl == 0) {
            if (passiveCount >= MAX_PASSIVES) return false
            passiveOrder[passiveCount++] = index
        }
        passiveLevels[index] = lvl + 1
        recomputeStats()
        return true
    }

    fun recomputeStats() {
        stats.recompute(passiveLevels, dishBonus, metaBonus, heroBonus)
        val p = player
        val oldMax = p.maxHp
        p.maxHp = BASE_HP + stats.maxHpAdd
        // Прибавка к максимуму сразу наполняет и текущее здоровье.
        if (p.maxHp > oldMax) p.hp += p.maxHp - oldMax
        if (p.hp > p.maxHp) p.hp = p.maxHp
        for (i in 0 until weaponCount) {
            val wi = weapons[i]
            val recipe = Content.evolutionForWeapon(wi.def.index)
            wi.synergyActive = recipe >= 0 && passiveLevels[Content.evolutions[recipe].passiveIndex] > 0
            wi.recompute(stats)
        }
    }

    fun canEvolve(index: Int): Boolean {
        if (index !in Content.evolutions.indices) return false
        val e = Content.evolutions[index]
        val slot = findWeapon(e.weaponIndex)
        if (slot < 0) return false
        val wi = weapons[slot]
        return wi.evolutionIndex < 0 && wi.level == wi.def.maxLevel && passiveLevels[e.passiveIndex] >= e.passiveLevel
    }

    fun evolve(index: Int): Boolean {
        if (!canEvolve(index)) return false
        val e = Content.evolutions[index]
        val wi = weapons[findWeapon(e.weaponIndex)]
        wi.evolutionIndex = index
        wi.recompute(stats)
        lastEvolution = index
        evolutionTimer = 3f
        fx.burst(player.x, player.y, 0xFFFFE487.toInt(), 50, 380f, 8f, 1.1f, Particle.STAR)
        fx.ring(player.x, player.y, 20f, 180f, 0.8f, 0xFFFFE487.toInt(), 9f)
        fx.screenFlash(0.3f, 0xFFFFE487.toInt())
        events.sfx(Sfx.CHEST)
        events.haptic(Haptic.BIG)
        return true
    }

    fun xpForLevel(level: Int): Float = XP_BASE + (level - 1) * XP_STEP + (if (level > 20) (level - 20) * XP_STEP_LATE else 0f)

    fun addXp(v: Float) {
        if (!v.isFinite() || v <= 0f) return
        val p = player
        if (p.dead || victory) return
        val g = v * stats.xpGain
        run.xp += g
        p.xp += g
        while (p.xp >= p.xpNeed) {
            p.xp -= p.xpNeed
            p.level++
            p.xpNeed = xpForLevel(p.level)
            pendingLevelUps++
            levelFlash = 1f
        }
    }

    fun heal(amount: Float) {
        val p = player
        if (p.dead || !amount.isFinite() || amount <= 0f) return
        val before = p.hp
        p.hp += amount
        if (p.hp > p.maxHp) p.hp = p.maxHp
        val h = p.hp - before
        run.healed += h
        if (h >= 1f) fx.text(p.x, p.y - p.radius - 16f, (h + 0.5f).toInt(), FloatText.HEAL, 0xFF7CFF8A.toInt(), big = true)
    }

    /** Победа: все враги рассыпаются в искры и монеты, весь лут слетается к герою. */
    fun onVictory() {
        if (victory) return
        victory = true
        victoryTimer = 0f
        val p = player
        for (i in 0 until enemyCount) {
            val e = enemies[i]
            if (!e.alive) continue
            fx.burst(e.x, e.y, e.def.color, 6, 240f, 6f, 0.6f)
            if (rngLoot.chance(0.3f)) pickupSystem.spawn(Pickup.GOLD, e.x, e.y, 1)
            e.alive = false; e.killed = false
        }
        compactEnemies()
        for (i in 0 until pickupCount) {
            val q = pickups[i]
            if (q.kind != Pickup.CHEST) q.attracted = true
        }
        pendingLevelUps = 0; pendingChests = 0; pendingTavern = 0
        hazardCount = 0; shotCount = 0
        fx.ring(p.x, p.y, 20f, 1100f, 1.0f, 0xFFFFE08A.toInt(), 16f)
        fx.burst(p.x, p.y, 0xFFFFD54A.toInt(), 60, 520f, 9f, 1.4f, Particle.STAR)
        fx.screenFlash(0.6f, 0xFFFFF2C0.toInt())
        events.sfx(Sfx.VICTORY)
        events.haptic(Haptic.BIG)
    }

    companion object {
        const val MAX_ENEMIES = 400
        const val MAX_PROJECTILES = 512
        const val MAX_PICKUPS = 600
        const val MAX_WEAPONS = 6
        const val MAX_PASSIVES = 6

        /** Полуразмеры прямоугольника спавна вокруг героя (видимая область ≤ 380×850, см. WorldRenderer). */
        const val SPAWN_HALF_W = 430f
        const val SPAWN_HALF_H = 900f
        const val DESPAWN_DIST = 950f
        const val CATCH_UP_DIST = 600f
        const val CATCH_UP_MUL = 1.3f
        const val SWARM_DESPAWN = 1100f

        const val BASE_HP = 100f
        const val BASE_SPEED = 140f
        const val KNOCK_DECAY = 9f

        const val ELITE_HP = 10f
        const val ELITE_DMG = 1.5f
        const val ELITE_SIZE = 1.45f
        const val ELITE_XP = 6

        const val XP_BASE = 5f
        const val XP_STEP = 8f
        const val XP_STEP_LATE = 4f

        const val MAX_HAZARDS = 56
        const val MAX_SHOTS = 180
        const val TRAIL = 10

        const val DASH_TIME = 0.18f
        const val DASH_SPEED = 760f
        const val DASH_IFRAMES = 0.32f
        const val DASH_RECHARGE = 2.4f
        const val ACCEL = 2600f
        const val DECEL = 3400f
        const val LEAD = 0.16f
        const val SLOWMO_SCALE = 0.45f
        const val NAMED_SIZE = 1.6f

        const val AFFIX_FAST = 1
        const val AFFIX_ARMORED = 2
        const val AFFIX_VAMPIRIC = 3
        const val AFFIX_VOLATILE = 4
        const val AFFIX_SHIELDED = 5
        const val AFFIX_COUNT = 5
        val AFFIX_NAMES = arrayOf("", "Быстрый", "Бронированный", "Вампирный", "Взрывной", "Щитовой")

        const val NEAR_MISS_LOW = 0.3f
        const val NEAR_MISS_RECOVER = 0.5f
        const val NEAR_MISS_HOLD = 8f
    }
}

/** Мелкая математика камеры. */
internal object M2 {
    fun follow(cur: Float, target: Float, dt: Float): Float {
        var k = 12f * dt
        if (k > 1f) k = 1f
        return cur + (target - cur) * k
    }
}
