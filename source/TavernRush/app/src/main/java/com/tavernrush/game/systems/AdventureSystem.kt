package com.tavernrush.game.systems
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.Element
import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.Sfx
import kotlin.math.abs

/** Постоянная карта и добровольные задачи. Не симуляция каждого пикселя. */
class AdventureSystem(private val w:World) {
    var active=false;private set
    /** One continuous authored interior: no reset, heal, teleport or separate run. */
    var conduitEncounterStarted=false;private set
    fun insideConduit(x:Float=w.player.x,y:Float=w.player.y)=active&&region==0&&x> -590f&&x< -350f&&y> -440f&&y<220f
    private fun conduitStep(){
        if(!insideConduit()||w.player.y>= -165f||conduitEncounterStarted||flags and 512!=0)return
        // Reserve both slots before starting; never grant an empty encounter under pool pressure.
        if(w.enemyCount>w.enemies.size-2)return
        conduitEncounterStarted=true
        w.spawnEnemy(Content.enemyIndex("rat"),-550f,-330f,false)?.spawnFade=1f
        w.spawnEnemy(Content.enemyIndex("goblin"),-420f,-365f,false)?.spawnFade=1f
        tell("Старый водовод: охрана у тайника. Прочитайте замахи; выход за спиной остаётся открыт.")
    }
    var region=0;private set
    var flags=0;private set
    var changed=false
    var message="";private set
    var messageT=0f;private set
    var bossStarted=false;private set
    var bossUid=0;private set
    var escaped=false;private set
    var pinned=-1;private set
    fun pin(i:Int):Boolean{if(!active||i !in nodesX.indices||!visibleNode(i)||i in 7..9||nodeDone(i))return false;pinned=i;return true}
    fun clearPin(){pinned=-1}
    var intent=0
    var trailShownT=0f;private set
    var nursery=false;private set
    var trustT=0f;private set
    var nectar=false
    var device=false;var lure=false
    var frozenT=0f;private set
    var chargedT=0f;private set
    var steamT=0f;private set
    var baitT=0f;private set
    var baitX=0f;private set
    var baitY=0f;private set
    var defence=0;private set
    var fortMask=0;private set
    val fortHp=IntArray(3)
    var masterHp=100f;private set
    var defenceT=0f;private set
    var defenceWave=0;private set
    var dangerT=0f;private set
    var climateT=0f;private set
    var contract=0;private set
    var contractToken=0;private set
    var contractDone=false;private set
    var contractStep=0;private set
    val scoutRoute=IntArray(3)
    private var spawnT=0f;private var damageT=0f;private var fortCd=0f;private var attackCd=0f
    private val rng=Rng(8)
    val names=arrayOf("Долина без дождя","Пепельный сад","Ледяной тракт","Грозовой перевал","Туманная топь","Кристальные руины")
    val rules=arrayOf("Вода меняет бой и среду у источника","Огонь тушится восстановленной водой","Лёд меняет разгон и торможение","Разряд предупреждает перед ударом по воде","Туман замедляет до очистки или вентиляции","Настройка света открывает короткий путь")
    val colors=intArrayOf(0xFF4FC3E8.toInt(),0xFFFF9A50.toInt(),0xFF90E4F5.toInt(),0xFFBA9EFF.toInt(),0xFF99C19A.toInt(),0xFFFFDC82.toInt())
    var map=RegionMap.all[0];private set
    val nodesX get()=map.x
    val nodesY get()=map.y
    private val collision=FloatArray(2)
    fun resolvePlayer(){if(!active)return;val p=w.player;val mask=map.resolve(p.x,p.y,20f,flags,collision);p.x=collision[0];p.y=collision[1];if(mask and 1!=0)p.vx=0f;if(mask and 2!=0)p.vy=0f}
    fun sweepPlayer(x:Float,y:Float){if(!active)return;val p=w.player;val mask=map.sweep(x,y,p.x,p.y,20f,flags,collision);p.x=collision[0];p.y=collision[1];if(mask and 1!=0)p.vx=0f;if(mask and 2!=0)p.vy=0f}
    fun resolveEnemies(){if(!active)return;for(i in 0 until w.enemyCount){val e=w.enemies[i];map.resolve(e.x,e.y,e.radius.coerceAtMost(30f),flags,collision);e.x=collision[0];e.y=collision[1]}}
    val labels=arrayOf("Преграда","Водный затвор","Пленный мастер","Источник","Логово","Лагерь","Оборона мастера","Левое укрытие","Правое укрытие","Переднее укрытие","Живые заросли","Тайник по следу","Старый водовод","Шлюз водовода","Тайник водовода")
    fun reset(){conduitEncounterStarted=false;pinned=-1;intent=0;trailShownT=0f;nursery=false;trustT=0f;nectar=false;active=false;flags=0;changed=false;message="";messageT=0f;bossStarted=false;bossUid=0;escaped=false;device=false;lure=false;frozenT=0f;chargedT=0f;steamT=0f;baitT=0f;defence=0;fortMask=0;fortHp.fill(0);masterHp=100f;defenceT=0f;defenceWave=0;dangerT=0f;climateT=0f;contract=0;contractToken=0;contractDone=false;contractStep=0;spawnT=0f;damageT=0f;fortCd=0f;attackCd=0f}
    fun start(i:Int,saved:Int,seed:Long){reset();active=true;region=i.coerceIn(0,5);flags=saved and (if(region==0)1023 else 255);rng.reseed(Rng.stream(seed,8));map=RegionMap.all[region];w.player.x=nodesX[5];w.player.y=nodesY[5];w.camX=w.player.x;w.camY=w.player.y;tell("Найдите затвор и верните воду. Мир запомнит помощь.")}
    fun tell(s:String){message=s;messageT=4f}
    fun beginContract(type:Int,token:Int,routeSeed:Int=token){require(type in 1..3&&token>0);contract=type;contractToken=token;val options=intArrayOf(1,4,2,0);for(i in scoutRoute.indices)scoutRoute[i]=options[Math.floorMod(i+routeSeed,4)];tell(when(type){1->"Разведайте три ориентира: 2 детали и опыт команды.";2->"Испытайте хранителя: 2 детали и опыт команды.";else->"Защитите мастера: три волны, 2 детали и опыт команды."})}
    fun nodeDone(i:Int)=when(i){0->flags and 2!=0;1->flags and 1!=0;2->flags and 4!=0;3->flags and 8!=0;4->flags and 16!=0;6->flags and 32!=0;10->flags and 64!=0;11->flags and 128!=0;13->flags and 256!=0;14->flags and 512!=0;else->false}
    val intentionDone:Boolean get()=when {contract>0->contractDone;intent==1&&visibleNode(10)->nodeDone(10);intent==2->nodeDone(11);else->flags and 13==13}
    val objective:Int get()=when{insideConduit()->if(!nodeDone(13))13 else if(!nodeDone(14))14 else 12;intentionDone->5;pinned>=0&&!nodeDone(pinned)->pinned;contract==0&&intent==1&&visibleNode(10)->if(flags and 1==0)1 else 10;contract==0&&intent==2->if(trailShownT>0f||nodeDone(4))11 else 4;contract==1->scoutRoute[contractStep.coerceIn(0,2)];contract==2->if(flags and 1==0)1 else 3;contract==3->if(flags and 4==0)2 else 6;flags and 1==0->1;flags and 4==0->2;flags and 8==0->3;else->5}
    fun nearNode():Int {
        if(region==0)for(i in 14 downTo 12){val x=w.player.x-nodesX[i];val y=w.player.y-nodesY[i];if(x*x+y*y<90f*90f&&map.clear(w.player.x,w.player.y,nodesX[i],nodesY[i],0f,flags))return i}
        val goal=objective;if(goal<12&&goal!=5&&visibleNode(goal)){val x=w.player.x-nodesX[goal];val y=w.player.y-nodesY[goal];if(x*x+y*y<150f*150f)return goal};var best=-1;var dist=150f*150f;for(i in nodesX.indices){if(i>=12||!visibleNode(i))continue;val x=w.player.x-nodesX[i];val y=w.player.y-nodesY[i];val d=x*x+y*y;if(d<dist){dist=d;best=i}};return best}
    /** Return is earned, explicit and unavailable in danger. Unfinished nearby actions keep priority. */
    fun quickReturnReady():Boolean {
        if(insideConduit()||!active||!intentionDone||w.player.dead||w.victory||defence==2||nursery)return false
        val p=w.player
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-p.x;val y=e.y-p.y;if(e.alive&&(e.boss||x*x+y*y<260f*260f))return false}
        for(i in 0 until w.hazardCount){val h=w.hazards[i];if(h.alive&&!h.visual)return false}
        for(i in 0 until w.shotCount){val h=w.shots[i];val x=h.x-p.x;val y=h.y-p.y;if(h.alive&&x*x+y*y<300f*300f)return false}
        if((chargedT>0f||steamT>0f)&&waterAt(p.x,p.y))return false
        return true
    }
    private fun returnAction():Boolean {val n=nearNode();return quickReturnReady()&&(n<0||n==5||nodeDone(n))}
    fun actionLabel():String=if(returnAction())"В лагерь" else when(val n=nearNode()){
        0->if(nodeDone(0))"Проход открыт" else if(region==5)"Настроить свет" else "Расколоть"
        1->if(nodeDone(1))"Вода течёт" else if(device)"Запустить схему" else "Открыть воду"
        2->if(nodeDone(2))"Мастер спасён" else if(lure)"Отвлечь охрану" else "Освободить"
        3->if(bossStarted&&!contractDone)"Сражайтесь" else if(nodeDone(3)&&contract!=2)"Источник жив" else "Хранитель"
        4->if(nodeDone(4))"Логово изучено" else "Исследовать"
        5->"Вернуться"
        6->if(nodeDone(6)&&contract!=3)"Защита завершена" else when(defence){1->"Начать защиту";2->"Держите линию";3->"Победа";else->"Подготовить"}
        7,8,9->if(fortMask and(1 shl(n-7))==0)"Поставить щит" else "Починить"
        10->if(nodeDone(10))"Заросли живут" else if(nursery)"Наблюдать спокойно" else "Восстановить"
        11->if(nodeDone(11))"Тайник изучен" else "Осмотреть след"
        12->if(insideConduit())"Выход на юг" else "Вход в водовод"
        13->if(nodeDone(13))"Шлюз открыт" else "Повернуть шлюз"
        14->if(nodeDone(14))"Тайник пуст" else "Открыть тайник"
        else->"К ориентиру"
    }
    private fun set(bit:Int,text:String){if(flags and bit!=0)return;flags=flags or bit;changed=true;tell(text);w.events.sfx(Sfx.CHEST);w.fx.ring(w.player.x,w.player.y,10f,160f,0.6f,colors[region],5f)}
    val summonCap:Int get()=if(contract==2)10 else 6
    val sourceHalf:Float get()=if(device)200f else 140f
    /** Renderer, player danger and enemy wetness share the exact same two water areas. */
    fun waterAt(x:Float,y:Float)=active&&flags and 1!=0&&x.isFinite()&&y.isFinite()&&((abs(x-nodesX[1])<140f&&abs(y-nodesY[1])<170f)||(abs(x-nodesX[3])<sourceHalf&&abs(y-nodesY[3])<sourceHalf))
    fun applyElement(el:Element,x:Float,y:Float):Boolean {
        if(!active||!x.isFinite()||!y.isFinite())return false
        if(el==Element.HEAVY&&abs(x-nodesX[0])<map.walls[0].hx+40f&&abs(y-nodesY[0])<map.walls[0].hy+125f&&flags and 2==0){set(2,"Тяжёлый удар открыл короткий путь.");return true}
        val upstream=abs(x-nodesX[1])<=180f&&abs(y-nodesY[1])<=220f
        val source=flags and 1!=0&&abs(x-nodesX[3])<=sourceHalf+40f&&abs(y-nodesY[3])<=sourceHalf+40f
        if(!upstream&&!source)return false
        when(el){
            Element.WATER->{if(flags and 1==0)set(1,"Поток заполнил русло. Теперь можно испытать сочетание.");steamT=0f}
            Element.FROST->{if(flags and 1==0)return false;frozenT=5f;chargedT=0f;tell("Ледяная опора меняет движение обеих сторон.")}
            Element.SHOCK->{if(flags and 1==0||frozenT>0f)return false;chargedT=2f;tell("Разряд по мокрой зоне опасен обеим сторонам.")}
            Element.FIRE->{if(flags and 1==0)return false;frozenT=0f;steamT=3f;tell("Огонь и вода создали пар — опасное укрытие.")}
            else->return false
        };return true
    }
    fun interact():Boolean {
        if(!active||w.player.dead||w.victory)return false
        if(returnAction()){escaped=true;w.moveX=0f;w.moveY=0f;return true}
        when(val n=nearNode()){
            -1->{tell("Подойдите к ориентиру. Стрелка показывает текущую цель.");return false}
            0->{if(nodeDone(n))return false;set(2,"Открыт короткий путь — он останется открытым.")}
            1->{if(nodeDone(n))return false;set(1,"Вода вернулась. Мокрая область проводит разряд.")}
            2->{if(nodeDone(n))return false
                if(lure){baitX=(w.player.x+330f).coerceAtMost(620f);baitY=(w.player.y+240f).coerceAtMost(620f);baitT=7f;lure=false;tell("Приманка брошена. Отведите охрану, затем освободите мастера.");return true}
                for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-nodesX[2];val y=e.y-nodesY[2];if(e.alive&&x*x+y*y<210f*210f){tell("Отведите врагов от мастера или победите их.");return false}}
                set(4,"Мастер свободен. Дома он поможет с устройствами.")}
            3->{if((nodeDone(n)&&contract!=2)||bossStarted||contractDone)return false
                if(flags and 1==0){tell("Сначала откройте затвор — источник пересох.");return false}
                val b=w.spawnBoss(Content.bosses[region%Content.bosses.size])?:return false;b.x=nodesX[3];b.y=nodesY[3]-100f;bossUid=b.uid;bossStarted=true;b.maxHp*=if(contract==2)0.42f else 0.24f;b.hp=b.maxHp;b.startMaxHp=b.maxHp;tell("Хранитель защищает источник. Читайте замахи и используйте воду.")}
            4->{if(nodeDone(n))return false;set(16,"В логове найдена деталь и след к тайнику. Указатель поможет продолжить поиск.");trailShownT=maxOf(trailShownT,40f)}
            5->{escaped=true;w.moveX=0f;w.moveY=0f}
            6->{if((nodeDone(n)&&contract!=3)||defence==2||contractDone)return false
                if(flags and 4==0){tell("Сначала спасите мастера.");return false}
                if(defence==1){defence=2;defenceT=0f;defenceWave=0;masterHp=100f;tell("Три волны. Встаньте между налётчиками и мастером.")}
                else {defence=1;fortMask=0;fortHp.fill(0);tell("Расставьте укрытия, затем начните защиту.")}}
            10->{if((region!=0&&region!=4)||nodeDone(10))return false
                if(flags and 1==0){tell("Сначала верните воду — заросли пересохли.");return false}
                if(!nursery){nursery=true;trustT=0f;tell("Заросли ожили. Отведите врагов; дайте Мохокрылу приблизиться самому.")}else tell("Постойте рядом без угрозы. Доверие растёт через спокойное наблюдение.")}
            11->{if(nodeDone(11)||!visibleNode(11))return false;set(128,"По живому следу найден тайник. Новое открытие запомнится команде.")}
            12->{tell(if(insideConduit())"Пройдите между опорами на юг. Поход продолжится в Долине." else "Войдите между каменными опорами. Слева — шлюз; за ним вторая комната. Назад можно выйти пешком.")}
            13->{if(nodeDone(13))return false;set(256,"Шлюз поднят: открыта вторая комната. Проход сохранится и после похода.")}
            14->{if(nodeDone(14))return false
                if(!conduitEncounterStarted){tell("Сначала войдите в комнату — тайник охраняется.");return false}
                for(i in 0 until w.enemyCount){val e=w.enemies[i];val dx=e.x-nodesX[14];val dy=e.y-nodesY[14];if(e.alive&&dx*dx+dy*dy<300f*300f){tell("Сначала победите или отведите охрану от тайника.");return false}}
                set(512,"Тайник водовода открыт: 2 детали для нового сочетания. Награда однократная. Выход — на юге.")}
            7,8,9->{if(defence !in 1..2||fortCd>0f){tell("Укрытия доступны при подготовке и защите.");return false};val k=n-7;fortMask=fortMask or(1 shl k);fortHp[k]=3;fortCd=2f;w.events.sfx(Sfx.SHIELD);tell("Укрытие выдержит три атаки. Его можно чинить.")}
        };return true
    }
    fun visibleNode(i:Int)=when(i){12->region==0;13->region==0&&(insideConduit()||nodeDone(13));14->region==0&&(conduitEncounterStarted||nodeDone(14));10->region==0||region==4;11->trailShownT>0f||nodeDone(11)||(intent==2&&nodeDone(4));else->true}
    fun markTrail(){if(active){trailShownT=40f;tell("Настой следопыта раскрыл ориентир тайника. Это необязательный путь.")}}
    fun reveal(x:Float,y:Float,radius:Float):Boolean {
        if(!active||nodeDone(11)||!x.isFinite()||!y.isFinite()||!radius.isFinite()||radius<=0f)return false
        val dx=x-nodesX[11];val dy=y-nodesY[11]
        if(dx*dx+dy*dy>radius*radius)return false
        trailShownT=maxOf(trailShownT,12f);tell("След раскрыл тайник поблизости. Можно подойти и осмотреть.");return true
    }
    private fun nurseryStep(dt:Float){
        if(!nursery||nodeDone(10))return
        val p=w.player;val dx=p.x-nodesX[10];val dy=p.y-nodesY[10];var safe=dx*dx+dy*dy<140f*140f
        for(i in 0 until w.enemyCount){val e=w.enemies[i];val x=e.x-nodesX[10];val y=e.y-nodesY[10];if(e.alive&&x*x+y*y<190f*190f)safe=false}
        if(safe&&p.dashT<=0f){trustT+=dt;if(trustT>=(if(nectar)2f else 4f)){set(64,"Мохокрыл подошёл сам. Новая личность ждёт в питомнике.");nursery=false}}
        else trustT=(trustT-dt*.4f).coerceAtLeast(0f)
    }
    fun icy(x:Float,y:Float)=active&&region==2&&abs(x)<350f&&y< -50f&&y> -650f
    private fun defenceStep(dt:Float){
        fortCd=(fortCd-dt).coerceAtLeast(0f);attackCd=(attackCd-dt).coerceAtLeast(0f);if(defence!=2)return
        defenceT+=dt
        if(defenceWave<3&&defenceT>=defenceWave*10f){defenceWave++;repeat(3){w.spawnEnemy(Content.enemyIndex(if(defenceWave==3)"slime" else "rat"),(nodesX[6]+(it-1)*130f).coerceIn(-600f,600f),(nodesY[6]-460f).coerceAtLeast(-900f),false)?.let{it.huntSite=true;it.spawnFade=1f}};w.events.sfx(Sfx.WAVE)}
        var alive=false
        for(i in 0 until w.enemyCount){val e=w.enemies[i];if(!e.alive||!e.huntSite)continue;alive=true;val x=e.x-nodesX[6];val y=e.y-nodesY[6]
            if(x*x+y*y<70f*70f&&e.spawnFade<=0f&&attackCd<=0f){attackCd=0.6f;var shield=-1;for(k in fortHp.indices)if(fortHp[k]>0){shield=k;break}
                if(shield>=0){fortHp[shield]--;if(fortHp[shield]==0)fortMask=fortMask and(1 shl shield).inv();w.events.sfx(Sfx.SHIELD)}else masterHp-=12f*w.team.protect(nodesX[6],nodesY[6])
                e.kx+=x*3f;e.ky-=150f
            }
        }
        if(masterHp<=0f){defence=4;tell("Мастер укрылся. Перестройте план и попробуйте снова.");for(i in 0 until w.enemyCount)if(w.enemies[i].huntSite){w.enemies[i].alive=false;w.enemies[i].killed=false}}
        else if(defenceWave==3&&!alive){defence=3;if(contract==3)contractDone=true;set(32,"Мастер защищён. Осада завершена, ежедневного повторения нет.")}
    }
    fun update(dt:Float){
        if(!active)return
        messageT=(messageT-dt).coerceAtLeast(0f);baitT=(baitT-dt).coerceAtLeast(0f);frozenT=(frozenT-dt).coerceAtLeast(0f);chargedT=(chargedT-dt).coerceAtLeast(0f);steamT=(steamT-dt).coerceAtLeast(0f);damageT=(damageT-dt).coerceAtLeast(0f);climateT+=dt;dangerT=0f
        trailShownT=(trailShownT-dt).coerceAtLeast(0f)
        conduitStep()
        nurseryStep(dt)
        defenceStep(dt);val p=w.player
        val tx=p.x-nodesX[11];val ty=p.y-nodesY[11]
        if(tx*tx+ty*ty<90f*90f&&flags and 128==0)trailShownT=maxOf(trailShownT,2f)
        if(contract==1&&!contractDone){val n=scoutRoute[contractStep];val x=p.x-nodesX[n];val y=p.y-nodesY[n];if(x*x+y*y<120f*120f){contractStep++;w.events.sfx(Sfx.CHEST);if(contractStep==3){contractDone=true;tell("Разведка завершена. Новый опыт команды и 2 детали.")}}}
        resolvePlayer()
        when(region){
            1->{if(flags and 1==0&&abs(p.x-100f)<100f&&abs(p.y+180f)<120f&&damageT<=0f){damageT=0.8f;w.combat.damagePlayer(6f,null)}}
            3->{val phase=climateT%8f;dangerT=if(phase>=5f&&phase<7f)7f-phase else 0f;if(phase>=7f&&flags and 1!=0&&frozenT<=0f)chargedT=maxOf(chargedT,0.2f)}
            4->{if(flags and 1==0&&!device&&abs(p.x)<200f&&p.y<0f&&p.y> -500f){p.slowT=0.15f;p.slowMul=0.65f}}
        }
        spawnT-=dt
        if(!insideConduit()&&spawnT<=0f&&w.enemyCount<7&&!bossStarted&&!nursery&&defence!=2&&flags and 8==0&&p.y<nodesY[5]-100f){spawnT=9f+rng.nextFloat()*4f;val ids=arrayOf("rat","goblin","slime");w.spawnEnemy(Content.enemyIndex(ids[rng.nextInt(ids.size)]),p.x+rng.signed()*200f,p.y-280f,false)?.spawnFade=1f}
        val inWater=waterAt(p.x,p.y)
        if(inWater&&(chargedT>0f||steamT>0f)&&damageT<=0f){damageT=0.8f;w.combat.damagePlayer(5f,null)}
        for(i in 0 until w.enemyCount){val e=w.enemies[i];if(!e.alive)continue
            if(icy(e.x,e.y)){e.slowTimer=maxOf(e.slowTimer,0.12f);e.slowMul=0.8f}
            if(region==1&&flags and 1==0&&abs(e.x-100f)<100f&&abs(e.y+180f)<120f){e.burnT=maxOf(e.burnT,0.5f);e.burnDmg=maxOf(e.burnDmg,3f)}
            val wet=waterAt(e.x,e.y)
            if(wet){if(frozenT>0f){e.slowTimer=maxOf(e.slowTimer,0.2f);e.slowMul=0.6f}else e.wetT=maxOf(e.wetT,2f);if(chargedT>0f||steamT>0f)w.combat.hitEnemy(e,8f*dt,nodesX[1],nodesY[1],0f,-1,false,react=false)}
        }
        if(bossStarted&&(flags and 8==0||contract==2&&!contractDone)){var alive=false;for(i in 0 until w.enemyCount)if(w.enemies[i].uid==bossUid&&w.enemies[i].alive)alive=true;if(!alive){if(contract==2)contractDone=true;set(8,"Хранитель успокоен. Источник жив — вернитесь к лагерю.")}}
    }
}
