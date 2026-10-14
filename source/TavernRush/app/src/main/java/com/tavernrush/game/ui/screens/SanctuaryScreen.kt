package com.tavernrush.game.ui.screens
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.PixelBlit
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.ui.*
import kotlin.math.sin
import kotlin.math.cos
/** Жизнь дома не продвигает валюту/рост по времени UI. Все хозяйственные операции явные. */
class SanctuaryScreen(app:GameApp):Screen(app){
    val plant=Array(3){Button("Посадить",Button.DARK)}
    val harvest=Array(3){Button("Собрать",Button.PRIMARY)}
    val seed=Button("",Button.DARK);val supply=Button("",Button.DARK)
    val pet=Button("",Button.GOLD);val talk=Button("Мастер",Button.DARK)
    val journal=Button("Истории",Button.DARK);val back=Button("В убежище",Button.GOLD)
    var seedKind=1;var shownAnimal=0;var memoryIndex=0
    private var note="";private var mode=0
    private val scene=RectF()
    private val plots=Array(3){RectF()}
    private fun all()=plant.toList()+harvest.toList()+listOf(seed,supply,pet,talk,journal,back)
    override fun onEnter(){note="";mode=0;memoryIndex=-1;layout()}
    override fun layout(){
        val u=ui.u;val l=ui.cx-ui.contentW/2;val r=ui.cx+ui.contentW/2;val b=ui.bottom
        scene.set(l,ui.top+270*u,r,b-860*u)
        val w=(ui.contentW-36*u)/3
        for(i in 0..2){val x=l+i*(w+18*u);plots[i].set(x+8*u,scene.bottom-180*u,x+w-8*u,scene.bottom-30*u)
            plant[i].set(x,b-790*u,x+w,b-675*u);harvest[i].set(x,b-650*u,x+w,b-535*u)}
        seed.set(l,b-510*u,ui.cx-12*u,b-395*u);supply.set(ui.cx+12*u,b-510*u,r,b-395*u)
        pet.set(l,b-370*u,ui.cx-12*u,b-255*u);talk.set(ui.cx+12*u,b-370*u,r,b-255*u)
        journal.set(l,b-230*u,l+ui.contentW*.30f,b-75*u);back.set(l+ui.contentW*.33f,b-230*u,r,b-75*u)
        all().forEach{it.textSize=36*u};refresh()
    }
    private fun refresh(){
        val h=game.profile.home
        seed.label=if(seedKind==1)"Семена: цветы" else "Семена: травы";seed.sub="Изменить · посадка за 1 материал"
        supply.label=when(h.supply){1->"В поход: нектар";2->"В поход: след";else->"Без припаса"};supply.sub="Нектар ${h.nectar} · настой ${h.herbs}"
        for(i in 0..2){plant[i].label=if(h.plots[i]==0)"Посадить ${i+1}" else if(h.plots[i]==1)"Цветы ${i+1}" else "Травы ${i+1}";plant[i].enabled=h.plots[i]==0;plant[i].sub=if(h.plots[i]==0)"1 материал" else "Рост ${h.stages[i]}/2 открытий";harvest[i].enabled=h.plots[i]!=0&&h.stages[i]==2;harvest[i].sub=if(h.plots[i]==1)"Нектар для знакомства" else "Настой для следа"}
        val a=game.profile.adventure.animals.getOrNull(shownAnimal.coerceIn(0,maxOf(0,game.profile.adventure.animals.lastIndex)))
        pet.label=a?.name?:"Пока нет друга";pet.sub="Познакомиться / следующий"
    }
    override fun draw(c:Canvas){
        val u=ui.u;val p=game.profile;val h=p.home
        c.drawColor(0xff162c29.toInt())
        d.txt.setup(76*u,Palette.GOLD_LIGHT);d.txt.fit("Живое убежище",ui.contentW,76*u);d.txt.draw(c,"Живое убежище",ui.cx,ui.top+110*u)
        d.txt.setup(32*u,Palette.TEXT);d.txt.draw(c,"Материалы ${p.adventure.materials} · сад растёт от новых открытий",ui.cx,ui.top+172*u)
        d.txt.draw(c,"Без полива по часам и наказания за отсутствие",ui.cx,ui.top+220*u)
        d.rrect(c,scene.left,scene.top,scene.right,scene.bottom,30*u,0xff466846.toInt())
        val fp=d.fp;fp.color=0xff365039.toInt();c.drawRect(scene.left+24*u,scene.top+38*u,scene.left+180*u,scene.bottom-200*u,fp)
        val pond=RectF(scene.left+35*u,scene.top+40*u,scene.left+240*u,scene.top+210*u)
        fp.color=if(p.adventure.regions.any{it and 1!=0})0xff60aaba.toInt() else 0xff786b4e.toInt();c.drawOval(pond,fp)
        val houseX=scene.right-220*u
        fp.color=0xff876648.toInt();c.drawRect(houseX,scene.top+55*u,scene.right-35*u,scene.top+225*u,fp)
        fp.color=0xffb99966.toInt();c.drawRect(houseX-12*u,scene.top+38*u,scene.right-20*u,scene.top+70*u,fp)
        val k=(2*u).toInt().coerceAtLeast(1)
        if(p.adventure.rescued!=0){val a=SpriteBank.heroIdle[1];PixelBlit.sprite(c,a.frame(0,(app.time*3).toInt()),houseX+95*u,scene.top+250*u,k)}
        for(i in 0..2){val r=plots[i];d.rrect(c,r.left,r.top,r.right,r.bottom,10*u,0xff6f523e.toInt());d.sp.color=0xffa18659.toInt();d.sp.strokeWidth=4*u;c.drawRoundRect(r,10*u,10*u,d.sp)
            if(h.plots[i]!=0){val x=r.centerX();val y=r.bottom-24*u;val stage=h.stages[i];fp.color=0xff91b668.toInt();c.drawRect(x-4*u,y-(25+stage*30)*u,x+4*u,y,fp)
                for(j in 0..stage+1){c.drawOval(x-28*u,y-(j*20+18)*u,x-4*u,y-j*20*u,fp);c.drawOval(x+4*u,y-(j*20+30)*u,x+28*u,y-(j*20+12)*u,fp)}
                if(stage==2){fp.color=if(h.plots[i]==1)0xffffd291.toInt() else 0xffc8e8a8.toInt();c.drawCircle(x,y-90*u,14*u,fp)}
            }
        }
        val roster=p.adventure.animals;val focus=roster.getOrNull(shownAnimal.coerceIn(0,maxOf(0,roster.lastIndex)));val animals=if(focus==null)emptyList()else listOf(focus)+roster.filter{it.uid!=focus.uid}.take(3)
        for((i,a) in animals.withIndex()){
            val model=SpriteBank.creature(Content.creatures[a.species].look,if(mode==1&&i==0)"act" else "idle",a.age,p.morph.form(a.uid))
            val x=scene.left+ui.contentW*(.24f+i*.16f)+sin(app.time*.75f+i)*14*u
            val y=(scene.top+scene.bottom)*.5f+cos(app.time*.65f+i)*18*u
            c.save();val size=1f;c.scale(size,size,x,y)
            PixelBlit.sprite(c,model.shadow(0,0),x,y,k);PixelBlit.sprite(c,model.frame(0,(app.time*4).toInt()),x,y,k);c.restore()
        }
        if(note.isNotEmpty()){
            // История — читаемый оверлей, а не текст поверх мелких кнопок.
            val top=scene.top+25*u;d.rrect(c,scene.left+18*u,top,scene.right-18*u,scene.bottom-205*u,20*u,0xe6243930.toInt())
            d.txt.setup(35*u,Palette.TEXT);d.txt.drawWrapped(c,note,ui.cx,top+55*u,ui.contentW-85*u,43*u)
        }
        all().forEach{d.button(c,it,app.time)}
    }
    override fun onDown(id:Int,x:Float,y:Float){pressButtons(id,x,y,*all().toTypedArray())}
    override fun onUp(id:Int,x:Float,y:Float){val b=releaseButton(id,x,y)?:return
        when(b){seed->seedKind=3-seedKind;supply->{val h=game.profile.home;var next=(h.supply+1)%3;for(k in 0..2){if(game.chooseSupply(next))break;next=(next+1)%3};note=when(h.supply){1->"Нектар будет потрачен в следующем походе: Мохокрыл приблизится быстрее.";2->"Настой будет потрачен в следующем походе: на короткое время раскроется тайник.";else->"Поход без припаса. Главный маршрут доступен и так."}}
            pet->{val list=game.profile.adventure.animals;if(list.isEmpty())note="Помогите существам в мире. Они смогут жить здесь без ежедневного ухода." else{shownAnimal=(shownAnimal+1)%list.size;val a=list[shownAnimal];mode=1;note="${a.name}: "+when(a.species){0->"играет у воды и приносит плавающую мелочь.";1->"выбрал высокую полку и внимательно следит за движением.";2->"устроился у прочной стены и не торопится уходить.";4->"подпирает маленький затвор и играет с потоком в пруду.";else->"расправляет листья-крылья рядом с вашим садом."}+when(a.trait){0->" Сегодня первым зовёт исследовать новое.";1->" Держится рядом и спокойно узнаёт ваш голос.";else->" Радуется знакомству и снова зовёт играть."}}}
            talk->{mode=2;note=game.talkMaster()}
            journal->{mode=3;val list=game.profile.home.memories;if(list.isEmpty())note="Новые открытия станут историями здесь." else{memoryIndex=(memoryIndex+1)%list.size;val m=list[list.lastIndex-memoryIndex];val who=game.profile.adventure.find(m.animal)?.name;note="История ${m.serial}: ${world.adventure.names[m.region]}. "+when{m.bits and 64!=0->"Заросли ожили, Мохокрыл подошёл сам.";m.bits and 128!=0->"По следу нашли тайник.";m.bits and 32!=0->"Мастер пережил нападение.";m.bits and 8!=0->"Источник снова жив.";m.bits and 4!=0->"Мастер вернулся домой.";m.bits and 1!=0->"Вернули воду в пересохшее русло.";else->"Открыли новый путь."}+(if(who!=null)" С вами был $who." else "")}}
            back->game.openHome()
            else->{val i=plant.indexOf(b);if(i>=0)note=if(game.plantGarden(i,seedKind))"Посадка завершена. Ещё два новых открытия — и можно собирать." else "Проверьте свободную грядку и один материал.";val j=harvest.indexOf(b);if(j>=0)note=if(game.harvestGarden(j))"Припас собран. Растение остаётся; продолжайте приключение." else "Растению нужны два новых открытия."}
        };refresh()
    }
    override fun onCancel()=resetPress()
}
