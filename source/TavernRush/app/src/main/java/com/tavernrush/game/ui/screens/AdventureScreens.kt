package com.tavernrush.game.ui.screens
import android.graphics.Canvas
import android.graphics.Paint
import com.tavernrush.game.core.ScreenId
import com.tavernrush.game.render.Palette
import com.tavernrush.game.systems.AdventureSystem
import com.tavernrush.game.systems.PipeBoard
import com.tavernrush.game.ui.*

/** Прямой доступ к действиям. Подтверждение потомства не меняет родителей. */
class AdventureScreens(app:GameApp):Screen(app){
    val buttons=Array(12){Button("",Button.DARK)}
    val board=PipeBoard()
    var animalIndex=0;var parentA=0;var parentB=0;var choice=0
    var region=0;var page=0
    private var lineagePage=0
    private var confirm=false;private var request=0;private var note=""
    private var title="";private var info=""
    val bodyText:String get()=info
    private val atlasTitles=arrayOf("Бой и движение","Моя команда","Мир помнит помощь","Мастерская и дом","Хранители областей")
    private val atlasTexts=arrayOf(
        "Стик закреплён слева. Рывок — отдельная кнопка. Приём героя справа; вариант меняется вне восстановления. Парирование смотрит в сторону левого движения: ловит летящий спереди снаряд, не положение стрелка. Перехват тратит щит; дальний стрелок не получает магический контрудар. Вариант «Прикрыть» защищает шире. Руна действует в области, крюк выбирает цель впереди: точку или видимого обычного врага; камень не пропускает зацеп и тело. В окно восстановления хранителя направьте движение к нему и нажмите крюк: следопыт сблизится и ударит, не подтягивая босса. Остаточные волны опасны и в это окно. Базовые атаки автоматические.",
        "Приказ — способность ведущего. В питомнике выбирайте конкретную особь. Для прыжка Грозорыса нужна открытая цель; точка посадки фиксируется, уходящий враг может увернуться. Камень задерживает тело, воду и дугу. Две взрослые особи одного вида дают потомка; родители остаются. Рост — через открытия. Пороговик — отдельная фантастическая линия. Fusion и форма героя ещё не реализованы.",
        "Шесть областей имеют разные маршруты и условия. Карта доступна через паузу; отметьте известный ориентир без телепортации. Пройдите вокруг стены или расколите её. Верните воду, спасите мастера, откройте источник. Мир сохраняет результат. Следующая цель видна сверху. Основной путь не требует редкого питомца.",
        "Спасённый мастер помогает сохранить рабочую схему воды. Поворачивайте трубы: вход слева сверху, выход справа снизу. Рецепты открываются за два материала; затем можно менять выбор бесплатно. Дом, декор и испытания доступны без ежедневных обязательств.",
        "Король Крыс: прочитайте полосу тарана, отвечайте после рывка.\nЛюдоед-повар: уйдите из отмеченного удара или прыжка.\nБанши: читайте волны и место появления.\nГромоклюв: между двумя полосами есть проход; метки не следят за вами.\nКорнеспин: зелёные границы показывают разрыв волны; между семенами есть место.\nЗеркалорог: луч фиксируется заранее и упирается в камень; после него — окно ответа.\nНи одному хранителю не нужен обязательный редкий питомец.")
    override fun onEnter(){
        confirm=false;note="";lineagePage=0
        if(game.screen==ScreenId.WORKSHOP){if(game.profile.adventure.scheme!=0)board.load(game.profile.adventure.blueprint)else board.reset()}
        layout()
    }
    private fun row(i:Int,label:String,sub:String?=null){buttons[i].visible=true;buttons[i].label=label;buttons[i].sub=sub;buttons[i].enabled=true}
    override fun layout(){
        buttons.forEach{it.visible=false};val p=game.profile.adventure
        title=when(game.screen){ScreenId.HOME->"Ваше убежище";ScreenId.WORKSHOP->"Живая схема воды";ScreenId.LINEAGE->"Личности и родословная";ScreenId.TRIALS->"Испытания по желанию";ScreenId.ATLAS->atlasTitles[page];else->"Ваш живой мир"}
        info="Материалы: ${p.materials} · Открытий: ${p.regions.sumOf{Integer.bitCount(it)}}"
        when(game.screen){
            ScreenId.JOURNEY->{for(i in 0..5)row(i,world.adventure.names[i],if(p.regions[i]==0)"Исследовать и помочь" else "Изменения сохранены · ${Integer.bitCount(p.regions[i])}/${if(i==0||i==4)8 else 7}")
                row(6,"Убежище");row(7,"Испытания");row(8,"Как играть");row(9,"Намерение: "+arrayOf("помочь долине","найти Мохокрыла","исследовать след")[game.profile.home.intent]);row(10,if(game.postRunHubAvailable)"В таверну" else "В меню")}
            ScreenId.HOME->{row(0,"Походный набор",if(p.recipe==0)"Выбран" else "Открыть / выбрать · 2 материала впервые")
                row(1,"Фляга воды",if(p.recipe==1)"Выбрана: водное оружие" else "Открыть / выбрать · 2 материала впервые")
                row(2,"Тяжёлый инструмент",if(p.recipe==2)"Выбран: тяжёлое оружие" else "Открыть / выбрать · 2 материала впервые")
                row(3,"Мастерская",if(p.rescued==0)"Сначала помогите мастеру" else "Поворот труб · сохранить рабочую схему")
                row(4,"Питомник","${p.animals.size} особей · выбрать, обучить, вывести потомка")
                row(5,"Оформление: ${p.decoration+1}","Без боевых процентов и ежедневного ухода");row(6,"Живое убежище","Сад, характеры питомцев и истории мастера");row(7,"В мир")}
            ScreenId.WORKSHOP->{info=if(board.solve())"Вода дошла до выхода! Можно сохранить." else "Поворачивайте трубы. Вход ↦ верхний левый; выход ↦ нижний правый."
                for(i in 0..8)row(i," ");row(9,"Сохранить схему","2 материала впервые; нужен спасённый мастер");row(10,"Сбросить");row(11,"В убежище")}
            ScreenId.LINEAGE->{val list=p.animals;animalIndex=animalIndex.coerceIn(0,maxOf(0,list.lastIndex));val a=list.getOrNull(animalIndex)
                info=if(a==null)"Помогите существам в экспедиции — они появятся здесь." else "${a.name} · возраст ${a.age}/2 · черта ${a.trait+1} · приём ${a.talent+1} · опыт ${a.practice} · родители ${a.parentA}/${a.parentB}"
                row(0,"← Предыдущий");row(1,"Следующий →");row(2,"Взять в команду",if(a?.uid==p.selected)"Выбран" else "Используется именно эта особь")
                if(lineagePage==1){
                    buttons[2].visible=false
                    row(3,"Родитель А: $parentA","Назначить показанную особь");row(4,"Родитель Б: $parentB","Две разные взрослые особи одного вида")
                    row(5,"Наследовать: ${if(choice==0)"А" else "Б"}","Меняет черту и приём; родителей не расходует")
                    row(6,if(confirm)"Подтвердить потомка" else "Подготовить потомка","2 материала · новая личность · родители остаются")
                    row(7,"Состав и развитие","Вернуться к крупным кнопкам личности");row(8,"В убежище")
                }else{
                    row(3,"Родители и потомство","Открыть отдельную страницу; текущий станет А")
                    row(7,"Тренировать приём","Меняет вариант после двух новых открытий");row(8,"В убежище");row(9,"Составы и резонанс","Индивидуальный резерв · три сохранённых состава");row(10,"Возраст и формы","Сравнить силуэты и боковое развитие");row(11,"Особая линия","Пороговик · ручной гибрид, не fusion")
                }}
            ScreenId.TRIALS->{info=world.adventure.rules[region];row(0,world.adventure.names[region],"Изменить область");row(1,"Следопыт","Посетить три разных места · +2 материала")
                row(2,"Страж источника","Босс после возврата воды · +2 материала");row(3,"Защита мастера","Три конечные волны · +2 материала")
                row(4,"Маршрут дня","Одинаковые точки по дате UTC; без серии посещений");row(5,"В мир");row(6,"Испытание орды","Прежняя арена; отдельный добровольный режим")}
            ScreenId.ATLAS->{info=atlasTexts[page];row(0,"← Назад");row(1,"Далее →");row(2,"В мир")}
            else->{}
        }
        val u=ui.u;val l=ui.cx-ui.contentW/2;val r=ui.cx+ui.contentW/2
        val top=ui.top+290*u;val bottom=ui.bottom-45*u
        if(game.screen==ScreenId.WORKSHOP){val cell=minOf(ui.contentW/3,(bottom-top)*.16f);val gl=ui.cx-cell*1.5f
            for(i in 0..8){val x=gl+(i%3)*cell;val y=top+(i/3)*cell;buttons[i].set(x+5*u,y+5*u,x+cell-5*u,y+cell-5*u)}
            val rest=top+cell*3+35*u;val rh=(bottom-rest)/3
            for(i in 9..11)buttons[i].set(l,rest+(i-9)*rh,r,rest+(i-8)*rh-18*u)
        }else if(game.screen==ScreenId.ATLAS){val y=bottom-270*u;buttons[0].set(l,y,ui.cx-12*u,y+110*u);buttons[1].set(ui.cx+12*u,y,r,y+110*u);buttons[2].set(l,y+140*u,r,bottom)}
        else {val visible=buttons.filter{it.visible};val h=(bottom-top)/visible.size.coerceAtLeast(1)
            visible.forEachIndexed{i,b->b.set(l,top+i*h,r,top+(i+1)*h-18*u)}}
        buttons.forEach{it.textSize=46*u};buttons.lastOrNull{it.visible}?.style=Button.GOLD
    }
    override fun draw(c:Canvas){
        val p=game.profile.adventure;val u=ui.u
        c.drawColor(intArrayOf(0xff172e30.toInt(),0xff30241f.toInt(),0xff142d42.toInt(),0xff213728.toInt())[p.decoration])
        d.txt.setup(76*u,Palette.GOLD_LIGHT);d.txt.fit(title,ui.contentW,76*u);d.txt.draw(c,title,ui.cx,ui.top+100*u)
        d.txt.setup(34*u,Palette.TEXT)
        if(game.screen==ScreenId.ATLAS){d.txt.drawWrapped(c,info,ui.cx,ui.top+310*u,ui.contentW,55*u)}
        else d.txt.drawWrapped(c,info,ui.cx,ui.top+160*u,ui.contentW,42*u)
        buttons.forEach{d.button(c,it,app.time)}
        if(game.screen==ScreenId.WORKSHOP){
            val paint=d.sp;paint.strokeWidth=22*u;paint.strokeCap=Paint.Cap.ROUND
            for(i in 0..8){val b=buttons[i].rect;val x=b.centerX();val y=b.centerY();val len=b.width()*.38f
                paint.color=if(board.reachable and(1 shl i)!=0)0xff71daec.toInt() else 0xffbcaf9c.toInt()
                val m=board.mask(i);if(m and 1!=0)c.drawLine(x,y,x,y-len,paint);if(m and 2!=0)c.drawLine(x,y,x+len,y,paint);if(m and 4!=0)c.drawLine(x,y,x,y+len,paint);if(m and 8!=0)c.drawLine(x,y,x-len,y,paint)
            }
        }
        if(note.isNotEmpty()){d.txt.setup(34*u,Palette.GOLD_LIGHT);d.txt.drawWrapped(c,note,ui.cx,ui.top+215*u,ui.contentW,36*u)}
    }
    override fun onDown(id:Int,x:Float,y:Float){pressButtons(id,x,y,*buttons)}
    override fun onUp(id:Int,x:Float,y:Float){val b=releaseButton(id,x,y)?:return;val i=buttons.indexOf(b);val p=game.profile.adventure
        when(game.screen){
            ScreenId.JOURNEY->when(i){in 0..5->game.startExpedition(i);6->game.openHome();7->game.openTrials();8->game.openAtlas();9->game.changeIntent();10->if(game.postRunHubAvailable)game.openHub()else game.openMenu()}
            ScreenId.HOME->when(i){in 0..2->note=if(game.craftRecipe(i))"Набор выбран" else "Нужно два материала для первого открытия";3->game.openWorkshop();4->game.openLineage();5->game.decorateHome();6->game.openSanctuary();7->game.openJourney()}
            ScreenId.WORKSHOP->when(i){in 0..8->board.rotate(i);9->note=if(game.saveBlueprint(board))"Рабочая схема сохранена" else "Проверьте выход, помощь мастеру и материалы";10->board.reset();11->game.openHome()}
            ScreenId.LINEAGE->{val a=p.animals.getOrNull(animalIndex);when(i){0->{animalIndex--;confirm=false};1->{animalIndex++;confirm=false};2->if(a!=null)game.selectAnimal(a.uid);3->{parentA=a?.uid?:0;confirm=false;lineagePage=1};4->{parentB=a?.uid?:0;confirm=false};5->{choice=1-choice;confirm=false};6->{if(!confirm){confirm=true;request=p.operation+1;note="Ещё одно нажатие подтвердит выбранную черту"}else{val child=game.breedAnimals(parentA,parentB,choice,request);note=if(child!=null)"Родился ${child.name}; родители остались" else "Проверьте вид, возраст, родителей и два материала";confirm=false}};7->if(lineagePage==1){lineagePage=0;confirm=false;note=""}else if(a!=null)note=if(game.trainAnimal(a.uid,(a.talent+1)%3))"Новый приём выбран" else "Нужно два новых открытия";8->game.openHome();9->game.openCompositions();10->game.openEvolution();11->game.openHybrids()}}
            ScreenId.TRIALS->when(i){0->region=(region+1)%6;in 1..3->game.startContract(region,i);4->{val day=System.currentTimeMillis()/86400000L;game.startContract(region,1,day)};5->game.openJourney();6->game.startRun()}
            ScreenId.ATLAS->when(i){0->page=(page+atlasTitles.size-1)%atlasTitles.size;1->page=(page+1)%atlasTitles.size;2->game.openJourney()}
            else->{}
        };layout()
    }
    override fun onCancel()=resetPress()
}
