package com.tavernrush.game.core

import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Creatures
import com.tavernrush.game.meta.Meta
import com.tavernrush.game.meta.Profile
import com.tavernrush.game.meta.RunResult
import com.tavernrush.game.save.MemoryStore
import com.tavernrush.game.save.ProfileStore
import com.tavernrush.game.systems.CardSystem
import com.tavernrush.game.systems.Nemesis
import com.tavernrush.game.systems.World

/**
 * Машина состояний игры (чистый Kotlin): заставка → меню → забег ⇄ выбор карт / пауза → итоги → таверна.
 * Вызывается только из игрового потока (GameLoop) или из UI-потока, когда игровой поток остановлен.
 */
class Game(private val store: ProfileStore = MemoryStore()) {
    var screen = ScreenId.SPLASH
        private set
    /** Время на текущем экране, с (анимации UI). */
    var screenTime = 0f
        private set
    var prevScreen = ScreenId.SPLASH
        private set

    val profile: Profile = store.load()
    val world = World()
    val result = RunResult()
    /** Причина текущего выбора карт (CardSystem.LEVEL_UP/CHEST/TAVERN). */
    var cardReason = CardSystem.LEVEL_UP
        private set
    /** Session-only post-match access; old lifetime counters do not grant a pre-run shop. */
    var postRunHubAvailable=false;private set
    private var expeditionGoldBanked=0
    private var pauseReturn = ScreenId.RUN
    /** Фиксированный сид (тесты, скриншоты, будущий «ежедневный забег»). */
    var fixedSeed: Long? = null
    var selectedHero = Content.heroIndex(profile.extra["hero"] ?: "innkeeper").coerceAtLeast(0)
        private set
    /** Последнее купленное улучшение (подсветка в хабе) и его таймер. */
    var lastBought = -1
        private set
    var lastBoughtTime = 0f
        private set
    private val metaBuf = FloatArray(Content.statCount)

    /** Платформенные звук/вибрация. Game фильтрует по настройкам профиля. */
    var platform: GameEvents = GameEvents.None
    private val filtered = object : GameEvents {
        override fun sfx(id: Int) { if (profile.soundOn) platform.sfx(id) }
        override fun music(theme:Int){platform.music(if(profile.soundOn)theme else -1)}
        override fun haptic(kind: Int) { if (profile.vibrationOn) platform.haptic(kind) }
        override fun exitRequested() { platform.exitRequested() }
    }
    val events: GameEvents get() = filtered

    init {
        if (profile.installSeed == 0L) profile.installSeed = Rng.mix(System.nanoTime() xor 0x5DEECE66DL)
        bankPending()
        world.events = filtered
        world.reset(1L, null)
    }

    /** Зачислить награду за прерванный (не завершённый) забег из прошлого сеанса. */
    private fun bankPending() {
        val g = profile.pendingRunGold
        if (g > 0) {
            Meta.credit(profile, g.toLong())
            profile.lastBankedGold = g
            profile.pendingRunGold = 0
            store.save(profile)
        }
    }

    fun go(s: ScreenId) {
        if(s==ScreenId.HUB&&(!postRunHubAvailable||inRun))return
        filtered.music(-1)
        prevScreen = screen
        screen = s
        screenTime = 0f
    }

    fun tick(dt: Float) {
        if (!dt.isFinite() || dt <= 0f || dt > 0.25f) return
        filtered.music(when(screen){ScreenId.RUN->if(world.adventure.active)2 else 1;ScreenId.ROUTE_MAP,ScreenId.PAUSE,ScreenId.CARD_PICK,ScreenId.SETTINGS,ScreenId.CONTROLS,ScreenId.PRACTICE,ScreenId.SPLASH->-1;else->0})
        screenTime += dt
        if (lastBoughtTime > 0f) lastBoughtTime -= dt
        when (screen) {
            ScreenId.SPLASH -> if (screenTime >= SPLASH_TIME) go(ScreenId.MENU)
            ScreenId.RUN -> tickRun(dt)
            else -> {}
        }
    }

    private fun tickRun(dt: Float) {
        val w = world
        w.tick(dt)
        if(w.adventure.active){flushAdventure();if(w.adventure.escaped||(w.player.dead&&w.deathTimer>=DEATH_DELAY)){finishExpedition();return}}
        w.encounter.takeJoin()?.let { f ->
            // Новый друг записывается сразу: если приложение убьют посреди забега, знакомство не пропадёт.
            f.metRun = profile.runCounter.coerceIn(0L, 10_000_000L).toInt()
            if (profile.befriend(f)) {
                result.newFriendId = f.id; result.newFriendName = f.name
                store.save(profile)
            }
        }
        when {
            w.player.dead -> if (w.deathTimer >= DEATH_DELAY) finishRun(false)
            w.victory -> if (w.victoryTimer >= VICTORY_DELAY) finishRun(true)
            w.pendingTavern > 0 -> { w.pendingTavern--; openCards(CardSystem.TAVERN) }
            w.pendingChests > 0 -> { w.pendingChests--; openCards(CardSystem.CHEST) }
            w.pendingLevelUps > 0 -> { w.pendingLevelUps--; openCards(CardSystem.LEVEL_UP) }
        }
    }

    private fun openCards(reason: Int) {
        cardReason = reason
        world.cards.roll(reason)
        world.moveX = 0f; world.moveY = 0f
        go(ScreenId.CARD_PICK)
    }

    fun openJourney(){if(!inRun)go(ScreenId.JOURNEY)}
    fun openSanctuary(){if(!inRun)go(ScreenId.SANCTUARY)}
    fun changeIntent(){if(!inRun){profile.home.intent=(profile.home.intent+1)%3;store.save(profile)}}
    fun plantGarden(i:Int,kind:Int):Boolean {if(inRun||!profile.home.plant(i,kind,profile.adventure))return false;store.save(profile);events.sfx(Sfx.BUY);return true}
    fun harvestGarden(i:Int):Boolean {if(inRun||!profile.home.harvest(i))return false;store.save(profile);events.sfx(Sfx.CHEST);return true}
    fun chooseSupply(i:Int):Boolean {if(inRun||!profile.home.chooseSupply(i))return false;store.save(profile);return true}
    fun talkMaster():String {
        if(inRun)return "Сначала вернитесь домой."
        val p=profile.adventure;val h=profile.home
        if(p.rescued==0)return "Здесь пока тихо. Помогите мастеру в экспедиции — он придёт в убежище."
        val chapter=when{p.regions.any{it and 64!=0}->2;p.regions.any{it and 8!=0}->1;p.rescued!=0->0;else->-1}
        if(chapter<0)return "Здесь пока тихо. Помогите мастеру в экспедиции — он придёт в убежище."
        val first=h.talked and(1 shl chapter)==0;h.talked=h.talked or(1 shl chapter);store.save(profile)
        return when(chapter){0->if(first)"Вы вернули меня домой. Вместе мы сможем пустить воду туда, где её давно ждут. В мастерской я помогу сохранить вашу схему." else "Рабочую схему всегда можно улучшить. Я сохраню то, что уже удалось, — начинать с нуля не нужно.";1->if(first)"Источник снова жив. Хранитель не был просто врагом: теперь у воды снова появились тихие тропы. Осмотрите заросли." else "Вода вернулась, но мир ещё хранит тайны. Иногда след видно только рядом или под пологом Мохокрыла.";else->if(first)"Он подошёл к вам сам? Значит, заросли действительно ожили. У каждого зверя свой характер — не меняйте любимца ради одной цифры." else "Новые открытия выращивают ваш сад. Нет нужды следить за ним по часам: я присмотрю, пока вас нет."}
    }
    fun openHome(){if(!inRun)go(ScreenId.HOME)}
    fun openWorkshop(){if(!inRun)go(ScreenId.WORKSHOP)}
    fun openLineage(){if(!inRun){profile.adventure.collectWaiting();store.save(profile);go(ScreenId.LINEAGE)}}
    fun openTrials(){if(!inRun)go(ScreenId.TRIALS)}
    fun openAtlas(){if(!inRun)go(ScreenId.ATLAS)}
    fun openEvolution(){if(!inRun){profile.adventure.collectWaiting();store.save(profile);go(ScreenId.EVOLUTION)}}
    fun evolveAnimal(id:Int,branch:Int):Boolean{if(inRun||!profile.morph.choose(profile.adventure,id,branch))return false;store.save(profile);events.sfx(Sfx.JOIN);return true}
    fun openCompositions(){if(!inRun){profile.adventure.collectWaiting();store.save(profile);go(ScreenId.COMPOSITIONS)}}
    fun chooseReserveAnimal(id:Int):Boolean {if(inRun||!profile.companions.setReserve(profile.adventure,id))return false;store.save(profile);return true}
    fun teachResonance(receiver:Int,donor:Int):Boolean {if(inRun||!profile.companions.teach(profile.adventure,receiver,donor))return false;store.save(profile);return true}
    fun saveComposition(slot:Int):Boolean {if(inRun||!profile.companions.savePreset(profile.adventure,slot,profile.teamMode))return false;store.save(profile);return true}
    fun loadComposition(slot:Int):Boolean {if(inRun)return false;val s=profile.companions.loadPreset(profile.adventure,slot)?:return false;profile.teamMode=s.mode;store.save(profile);return true}
    fun cycleIndividualMode(){if(!inRun){profile.teamMode=(profile.teamMode+1)%com.tavernrush.game.content.Creatures.MODES;store.save(profile)}}
    private fun configureIndividuals(){
        val p=profile.adventure;val c=profile.companions;val a=p.find(p.selected)?:return
        fun friend(x:com.tavernrush.game.meta.Animal)=com.tavernrush.game.meta.Friend(Content.creatures[x.species].id).also{it.nameIdx=(x.uid-1)%Content.creatures[x.species].names.size;it.trait=x.trait;it.spec=x.talent}
        val b=p.find(c.reserve)?.takeIf{it.uid!=a.uid&&it.species!=a.species}
        val legacy=profile.friend(profile.teamLead)?.takeIf{it.id!=Content.creatures[a.species].id}?:profile.friend(profile.teamReserve)?.takeIf{it.id!=Content.creatures[a.species].id}
        world.team.setup(friend(a),b?.let{friend(it)}?:legacy,profile.teamMode)
        for(i in 0 until world.team.count){val m=world.team.mates[i]!!;val x=if(i==0)a else b?:continue;m.animalUid=x.uid;m.visualAge=x.age;m.visualForm=profile.morph.form(x.uid);if(m.visualForm!=0)m.spec=m.visualForm;val donor=p.find(c.lessons[x.uid]?:0);m.resonance=donor?.species?:-1}
    }
    private fun practiceIndividuals(){
        val p=profile.adventure;p.practiceDiscovery()
        for(i in 0 until world.team.count){val m=world.team.mates[i]!!;if(m.animalUid>0&&m.animalUid!=p.selected&&m.activeTime>=6f)p.find(m.animalUid)?.let{a->a.practice=(a.practice+1).coerceAtMost(1000000);a.age=maxOf(a.age,if(a.practice>=3)2 else if(a.practice>=1)1 else 0)}}
    }
    private fun learnJointDiscovery():Boolean {
        val t=world.team;if(t.count!=2)return false
        val a=t.mates[0]!!;val b=t.mates[1]!!
        if(a.animalUid<=0||b.animalUid<=0||a.activeTime<6f||b.activeTime<6f)return false
        val learned=profile.companions.learn(profile.adventure,a.animalUid,b.animalUid)
        if(learned)world.adventure.tell("Совместное открытие: пара научилась резонансу. Настройте один приём дома.")
        return learned
    }
    fun startExpedition(region:Int=0){
        if(inRun||region !in 0..5)return
        startRun();expeditionGoldBanked=0;world.encounter.reset()
        val p=profile.adventure;world.adventure.start(region,p.regions[region],world.seed)
        for(i in 0 until world.team.count){val m=world.team.mates[i]!!;m.x=world.player.x-45f+i*90f;m.y=world.player.y+30f;m.vx=0f;m.vy=0f}
        world.adventure.intent=profile.home.intent;val supply=profile.home.takeSupply();world.adventure.nectar=supply==1;if(supply==2)world.adventure.markTrail()
        world.adventure.device=p.scheme!=0;world.adventure.lure=p.recipes and 1!=0&&p.recipe==0
        if(p.recipes and(1 shl p.recipe)!=0){when(p.recipe){1->world.addWeapon(Content.weapons.indexOfFirst{it.element==com.tavernrush.game.content.Element.WATER});2->world.addWeapon(Content.weapons.indexOfFirst{it.element==com.tavernrush.game.content.Element.HEAVY})}}
        p.expeditions=(p.expeditions+1).coerceAtMost(1000000);store.save(profile)
    }
    fun startContract(region:Int,type:Int,dailySeed:Long?=null):Boolean{
        val p=profile.adventure;if(inRun||region !in 0..5||type !in 1..3||p.contractCounter>=1000000)return false
        val old=fixedSeed;if(dailySeed!=null)fixedSeed=dailySeed;startExpedition(region);fixedSeed=old
        p.contractCounter++;world.adventure.beginContract(type,p.contractCounter,dailySeed?.let{Math.floorMod(it,100000L).toInt()}?:p.contractCounter);store.save(profile);return true
    }
    fun interactWorld():Boolean{if(screen!=ScreenId.RUN)return false;val ok=world.adventure.interact();flushAdventure();return ok}
    fun useHeroAction()=screen==ScreenId.RUN&&world.heroAction.use()
    fun switchHeroAction()=screen==ScreenId.RUN&&world.heroAction.switch()
    private fun flushAdventure(){
        val a=world.adventure;if(!a.active)return
        val p=profile.adventure;var dirty=false
        if(a.changed){val witnessed=(0 until world.team.count).mapNotNull{world.team.mates[it]}.firstOrNull{it.active}?.animalUid?:0;val added=a.flags and p.regions[a.region].inv();p.regions[a.region]=a.flags
            if(added and 4!=0){p.rescued=p.rescued or(1 shl a.region);p.adopt(a.region%3,a.region*2+1)}
            if(added and 8!=0)p.adopt(a.region%3,a.region*2+2)
            if(added and 64!=0){val animal=p.adopt(3,13+a.region)
                if(animal!=null&&world.team.count==0){val f=com.tavernrush.game.meta.Friend("moss").also{it.nameIdx=(animal.uid-1)%com.tavernrush.game.content.Content.creatures[3].names.size;it.trait=animal.trait;it.spec=animal.talent};world.team.setup(f,null,profile.teamMode);world.team.lead()?.let{it.animalUid=animal.uid;it.visualAge=animal.age};a.tell("Мохокрыл подошёл сам и теперь идёт с вами.")}
                else a.tell(if(animal==null)"Знакомство сохранено. В питомнике пока нет свободного места." else "Мохокрыл подошёл сам. Он ждёт в вашем питомнике.")
            }
            if(added!=0){learnJointDiscovery();if(added and 255!=0)profile.home.discovery(a.region,added and 255,witnessed);practiceIndividuals();p.materials=(p.materials+Integer.bitCount(added)*2).coerceAtMost(100000)}
            p.story=a.region+1;a.changed=false;dirty=true}
        if(a.contractDone&&a.contractToken>p.contractPaid){p.contractPaid=a.contractToken;learnJointDiscovery();p.materials=(p.materials+2).coerceAtMost(100000);practiceIndividuals();dirty=true}
        val gold=world.run.gold.toInt().coerceIn(0,1000000)
        if(gold>expeditionGoldBanked){Meta.credit(profile,(gold-expeditionGoldBanked).toLong());expeditionGoldBanked=gold;dirty=true}
        if(dirty)store.save(profile)
    }
    private fun finishExpedition(){postRunHubAvailable=true;flushAdventure();world.team.finish(profile);profile.pendingRunGold=0;store.save(profile);go(ScreenId.JOURNEY)}
    fun craftRecipe(i:Int):Boolean{if(inRun||i !in 0..2)return false;val p=profile.adventure;val bit=1 shl i;if(p.recipes and bit==0){if(p.materials<2)return false;p.materials-=2;p.recipes=p.recipes or bit};p.recipe=i;store.save(profile);events.sfx(Sfx.BUY);return true}
    fun decorateHome(){if(!inRun){profile.adventure.decoration=(profile.adventure.decoration+1)%4;store.save(profile)}}
    fun saveBlueprint(b:com.tavernrush.game.systems.PipeBoard):Boolean{if(inRun||!b.solve())return false;val p=profile.adventure;if(p.scheme==0){if(p.rescued==0||p.materials<2)return false;p.materials-=2};p.scheme=1;p.blueprint=b.packed();store.save(profile);events.sfx(Sfx.BUY);return true}
    fun createHybrid(a:Int,b:Int,choice:Int,request:Int):com.tavernrush.game.meta.Animal?{if(inRun)return null;val child=profile.adventure.hybrid(a,b,choice,request,profile.companions.knows(a,b))?:return null;store.save(profile);events.sfx(Sfx.JOIN);return child}
    fun openHybrids(){if(!inRun)go(ScreenId.HYBRIDS)}
    fun breedAnimals(a:Int,b:Int,choice:Int,request:Int):com.tavernrush.game.meta.Animal?{if(inRun)return null;val child=profile.adventure.breed(a,b,choice,request)?:return null;store.save(profile);events.sfx(Sfx.JOIN);return child}
    fun selectAnimal(id:Int):Boolean{if(inRun||!profile.adventure.choose(id))return false;val c=profile.companions;if(c.reserve!=0&&!c.compatible(profile.adventure,id,c.reserve))c.reserve=0;store.save(profile);return true}
    fun trainAnimal(id:Int,talent:Int):Boolean{if(inRun||!profile.adventure.train(id,talent))return false;store.save(profile);return true}

    fun startRun() {
        postRunHubAvailable=false
        bankPending()
        profile.lastBankedGold = 0
        val seed = fixedSeed ?: Rng.mix(profile.installSeed + profile.runCounter * -0x61c8864680b583ebL)
        profile.runCounter++
        world.events = filtered
        world.reset(seed, Meta.bonuses(profile, metaBuf), selectedHero)
        world.setNemesis(profile.nemesisId, profile.nemesisRank, profile.nemesisWave, profile.nemesisSeed)
        world.rematchBoss = profile.rematchBoss
        world.fx.shakeScale = profile.shakePct / 100f
        world.team.setup(profile.friend(profile.teamLead), profile.friend(profile.teamReserve), profile.teamMode)
        configureIndividuals()

        var unmet = 0
        for (i in Content.creatures.indices) if (profile.friend(Content.creatures[i].id) == null) unmet = unmet or (1 shl i)
        world.encounter.arm(unmet, seed)
        result.valid = false
        result.newFriendId = ""; result.newFriendName = ""
        events.sfx(Sfx.CLICK)
        go(ScreenId.RUN)
    }

    fun pickCard(i: Int) {
        if (screen != ScreenId.CARD_PICK) return
        if (!world.cards.apply(i)) return
        events.sfx(Sfx.BUY)
        events.haptic(Haptic.TICK)
        go(ScreenId.RUN)
    }

    val inRun: Boolean get() = screen == ScreenId.RUN || screen == ScreenId.CARD_PICK || screen == ScreenId.PAUSE || screen == ScreenId.ROUTE_MAP

    fun pause() {
        if (screen == ScreenId.RUN || screen == ScreenId.CARD_PICK) {
            pauseReturn = screen
            world.moveX = 0f; world.moveY = 0f
            go(ScreenId.PAUSE)
        }
    }

    fun openRouteMap(){if(screen==ScreenId.PAUSE&&world.adventure.active)go(ScreenId.ROUTE_MAP)}
    fun closeRouteMap(){if(screen==ScreenId.ROUTE_MAP)go(ScreenId.PAUSE)}

    fun resume() {
        if (screen == ScreenId.PAUSE) go(pauseReturn)
    }

    /** «Завершить забег» из паузы: засчитывается как поражение с наградой за прогресс. */
    fun abandonRun() {
        if (!inRun) return
        if(world.adventure.active)finishExpedition()else finishRun(false)
    }

    private fun finishRun(victory: Boolean) {
        postRunHubAvailable=true
        Meta.computeReward(world, victory, profile, result)
        Meta.applyResult(profile, result)
        world.team.finish(profile)
        System.arraycopy(world.run.reactions, 0, result.reactions, 0, result.reactions.size)
        val before = profile.nemesisId
        Nemesis.onRunEnd(profile, world.run, victory, world.run.wave, world.seed)
        result.newNemesis = if (!victory && profile.nemesisId.isNotEmpty()) Nemesis.fullName(profile.nemesisId, profile.nemesisSeed, profile.nemesisRank) else ""
        result.killerLabel = killerLabel(victory)
        if (before.isNotEmpty() && profile.nemesisId.isEmpty()) result.nemesisSlain = true
        store.save(profile)
        events.sfx(if (victory) Sfx.VICTORY else Sfx.WAVE)
        go(ScreenId.RESULTS)
    }

    private fun killerLabel(victory: Boolean): String {
        if (victory) return ""
        val run = world.run
        if (run.killerEnemy < 0 || run.killerEnemy >= Content.enemies.size) return ""
        val def = Content.enemies[run.killerEnemy]
        return if (run.killerNamed) Nemesis.name(run.killerNameSeed, run.killerRank) + " · " + def.name else def.name
    }

    fun openHub() { if(!postRunHubAvailable||inRun)return;events.sfx(Sfx.CLICK); go(ScreenId.HUB) }
    fun openMenu() { events.sfx(Sfx.CLICK); go(ScreenId.MENU) }
    fun openHeroes() { events.sfx(Sfx.CLICK); go(ScreenId.HEROES) }
    fun openSettings() { events.sfx(Sfx.CLICK); go(ScreenId.SETTINGS) }
    fun openTeam() { events.sfx(Sfx.CLICK); go(ScreenId.TEAM) }
    fun openControls() { if (!inRun) { events.sfx(Sfx.CLICK); go(ScreenId.CONTROLS) } }
    fun openPractice() { if (!inRun) { events.sfx(Sfx.CLICK); go(ScreenId.PRACTICE) } }

    /** Правки команды допустимы только вне забега: ни режим, ни состав не меняются посреди боя. */
    fun setTeamMode(mode: Int): Boolean {
        if (inRun || mode !in 0 until Creatures.MODES) return false
        profile.teamMode = mode
        store.save(profile); events.sfx(Sfx.CLICK)
        return true
    }

    fun setTeamRole(id: String, role: Int): Boolean {
        if (inRun) return false
        val ok = when (role) {
            0 -> profile.setLead(id)
            1 -> if (id == profile.teamReserve) { profile.clearReserve(); true } else profile.setReserve(id)
            else -> false
        }
        if (ok) { store.save(profile); events.sfx(Sfx.CLICK) }
        return ok
    }

    fun chooseFriendSpec(id: String, spec: Int): Boolean {
        if (inRun || !profile.chooseSpec(id, spec)) return false
        store.save(profile); events.sfx(Sfx.BUY); events.haptic(Haptic.TICK)
        return true
    }
    /** Сохранить настройки (графика, управление): применяются GameApp на следующем кадре. */
    fun saveSettings() { store.save(profile) }
    fun selectHero(index: Int): Boolean {
        if (inRun || index !in Content.heroes.indices) return false
        selectedHero = index
        profile.extra["hero"] = Content.heroes[index].id
        store.save(profile)
        events.sfx(Sfx.CLICK)
        return true
    }

    fun buyUpgrade(i: Int): Boolean {
        if(screen!=ScreenId.HUB||!postRunHubAvailable||inRun)return false
        if (i < 0 || i >= Content.hubUpgrades.size) return false
        if (!Meta.buy(profile, i)) {
            events.sfx(Sfx.HURT)
            return false
        }
        lastBought = i
        lastBoughtTime = 0.8f
        store.save(profile)
        events.sfx(Sfx.BUY)
        events.haptic(Haptic.TICK)
        return true
    }

    fun toggleSound() { profile.soundOn = !profile.soundOn; store.save(profile) }
    fun toggleVibration() { profile.vibrationOn = !profile.vibrationOn; store.save(profile); events.haptic(Haptic.TICK) }

    /** Системная кнопка «назад». */
    fun back() {
        when (screen) {
            ScreenId.MENU -> filtered.exitRequested()
            ScreenId.RUN, ScreenId.CARD_PICK -> pause()
            ScreenId.ROUTE_MAP -> closeRouteMap()
            ScreenId.PAUSE -> resume()
            ScreenId.RESULTS -> openHub()
            ScreenId.HUB -> openMenu()
            ScreenId.HEROES -> openMenu()
            ScreenId.SETTINGS -> openMenu()
            ScreenId.TEAM -> openMenu()
            ScreenId.CONTROLS -> openSettings()
            ScreenId.PRACTICE -> openControls()
            ScreenId.JOURNEY -> openMenu()
            ScreenId.HOME,ScreenId.TRIALS,ScreenId.ATLAS -> openJourney()
            ScreenId.COMPOSITIONS,ScreenId.EVOLUTION,ScreenId.HYBRIDS -> openLineage()
            ScreenId.WORKSHOP,ScreenId.LINEAGE,ScreenId.SANCTUARY -> openHome()
            ScreenId.SPLASH -> go(ScreenId.MENU)
        }
    }

    /**
     * Приложение уходит в фон (вызывается, когда игровой поток уже остановлен): пауза
     * и страховочная запись награды «как если бы забег закончился сейчас» — прогресс не теряется
     * даже если система убьёт процесс. Завершение забега обнуляет страховку.
     */
    fun onHostPause() {
        pause();flushAdventure()
        if (inRun && !world.adventure.active && !world.player.dead && !world.victory) {
            Meta.computeReward(world, false, profile, tmpResult)
            profile.pendingRunGold = tmpResult.total
        }
        store.save(profile)
    }

    private val tmpResult = RunResult()

    companion object {
        const val SPLASH_TIME = 1.6f
        const val DEATH_DELAY = 1.6f
        const val VICTORY_DELAY = 3.2f
    }
}
