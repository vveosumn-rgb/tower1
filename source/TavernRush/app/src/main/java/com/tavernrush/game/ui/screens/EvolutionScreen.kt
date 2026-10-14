package com.tavernrush.game.ui.screens
import android.graphics.Canvas
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.*
import com.tavernrush.game.ui.*
/** Preview is free; explicit choice preserves ID, ancestry and inheritable talent. */
class EvolutionScreen(app:GameApp):Screen(app){
 val previous=Button("← Особь",Button.DARK);val next=Button("Особь →",Button.DARK)
 val branches=Array(3){Button("",Button.DARK)};val choose=Button("Выбрать форму",Button.PRIMARY);val back=Button("В питомник",Button.GOLD)
 var animalIndex=0;var branch=0;var message="";private set
 private val scene=RectF()
 fun buttons()=listOf(previous,next)+branches.toList()+listOf(choose,back)
 private fun animal()=game.profile.adventure.animals.getOrNull(animalIndex)
 override fun onEnter(){message="";branch=game.profile.morph.form(animal()?.uid?:0);layout()}
 override fun layout(){
  val u=ui.u;val l=ui.cx-ui.contentW/2;val r=ui.cx+ui.contentW/2;val b=ui.bottom
  scene.set(l,ui.top+160*u,r,b-920*u)
  previous.set(l,b-890*u,ui.cx-12*u,b-760*u);next.set(ui.cx+12*u,b-890*u,r,b-760*u)
  for(i in 0..2)branches[i].set(l,b-(735-i*145)*u,r,b-(610-i*145)*u)
  choose.set(l,b-285*u,r,b-160*u);back.set(l,b-135*u,r,b-20*u)
  buttons().forEach{it.textSize=36*u};refresh()
 }
 private fun refresh(){
  val p=game.profile;animalIndex=animalIndex.coerceIn(0,maxOf(0,p.adventure.animals.lastIndex));val a=animal();val def=a?.let{Content.creatures[it.species]}
  branches[0].label="Природная форма";branches[0].sub="Вернуть наследуемый приём; без потери опыта"
  for(i in 1..2){branches[i].label=def?.specs?.get(i-1)?.get(0)?:"Ветка $i";branches[i].sub=def?.specs?.get(i-1)?.get(1)}
  for(i in branches.indices)branches[i].style=if(i==branch)Button.GOLD else Button.DARK
  choose.enabled=a!=null&&(branch==0||a.age==2&&a.practice>=3)
  choose.sub=if(choose.enabled)"Бесплатно · та же личность · можно сменить дома" else "Взрослый питомец + три новых открытия"
 }
 override fun draw(c:Canvas){
  val u=ui.u;val a=animal();c.drawColor(0xff182e31.toInt());d.txt.setup(64*u,Palette.GOLD_LIGHT);d.txt.fit("Возраст и формы",ui.contentW,64*u);d.txt.draw(c,"Возраст и формы",ui.cx,ui.top+110*u)
  d.rrect(c,scene.left,scene.top,scene.right,scene.bottom,25*u,0xff2c4645.toInt())
  if(a!=null){val k=(3*u).toInt().coerceAtLeast(1);for(age in 0..2){val x=scene.left+ui.contentW*(.18f+age*.32f);val y=scene.top+190*u;val model=SpriteBank.creature(Content.creatures[a.species].look,"idle",age,if(age==2)branch else 0);PixelBlit.sprite(c,model.shadow(0,0),x,y,k);PixelBlit.sprite(c,model.frame(0,(app.time*3).toInt()),x,y,k);d.txt.setup(28*u,Palette.TEXT);d.txt.draw(c,arrayOf("Детёныш","Молодой","Взрослый")[age],x,y+60*u)}}
  d.txt.setup(32*u,Palette.TEXT);val p=game.profile
  val text=if(message.isNotEmpty())message else if(a==null)"Помогите существу в мире. Здесь можно заранее сравнить развитие." else "${a.name}\nВозраст ${a.age}/2 · открытия ${a.practice}\nСейчас: ${if(p.morph.form(a.uid)==0)"природная форма" else Content.creatures[a.species].specs[p.morph.form(a.uid)-1][0]}\nПредпросмотр не меняет питомца. Выбор заменяет вариант приёма, не складывает все бонусы. Имя, талант родителей и история сохраняются."
  d.txt.drawWrapped(c,text,ui.cx,scene.top+310*u,ui.contentW-45*u,42*u);buttons().forEach{d.button(c,it,app.time)}
 }
 override fun onDown(id:Int,x:Float,y:Float){pressButtons(id,x,y,*buttons().toTypedArray())}
 override fun onUp(id:Int,x:Float,y:Float){val b=releaseButton(id,x,y)?:return
  when(b){previous->{animalIndex--;message=""};next->{animalIndex++;message=""};choose->{message=if(animal()?.let{game.evolveAnimal(it.uid,branch)}==true)"Форма выбрана. Это тот же питомец: родители, имя, опыт и наследуемая черта сохранены." else "Нужен взрослый питомец с тремя открытиями."};back->game.openLineage();else->{val n=branches.indexOf(b);if(n>=0){branch=n;message=""}}};refresh()
 }
 override fun onCancel()=resetPress()
}
