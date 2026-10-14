package com.tavernrush.game.ui.screens
import android.graphics.Canvas
import android.graphics.RectF
import com.tavernrush.game.content.Content
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.*
import com.tavernrush.game.ui.*
/** One original hand-authored fantasy line. The parents are never consumed. */
class HybridScreen(app:GameApp):Screen(app){
 val first=Button("",Button.DARK);val second=Button("",Button.DARK);val trait=Button("",Button.DARK)
 val create=Button("Подготовить линию",Button.PRIMARY);val take=Button("Взять потомка",Button.DARK);val back=Button("В питомник",Button.GOLD)
 var indexA=0;var indexB=0;var choice=0;private var confirmed=false;private var request=0;var created=0;private set
 var message="";private set;private val scene=RectF()
 fun buttons()=listOf(first,second,trait,create,take,back)
 private fun a()=game.profile.adventure.animals.filter{it.species==0}.getOrNull(indexA)
 private fun b()=game.profile.adventure.animals.filter{it.species==2}.getOrNull(indexB)
 override fun onEnter(){confirmed=false;message="";layout()}
 override fun layout(){val u=ui.u;val l=ui.cx-ui.contentW/2;val r=ui.cx+ui.contentW/2;val bot=ui.bottom;scene.set(l,ui.top+160*u,r,bot-930*u)
  first.set(l,bot-900*u,r,bot-770*u);second.set(l,bot-745*u,r,bot-615*u);trait.set(l,bot-590*u,r,bot-460*u);create.set(l,bot-435*u,r,bot-305*u);take.set(l,bot-280*u,r,bot-150*u);back.set(l,bot-125*u,r,bot-20*u);buttons().forEach{it.textSize=36*u};refresh()}
 private fun refresh(){val aa=a();val bb=b();first.label=aa?.name?:"Найдите Ручьеглота";first.sub="Следующая особь: родитель А";second.label=bb?.name?:"Найдите Камнерога";second.sub="Следующая особь: родитель Б";trait.label="Черта и приём: ${if(choice==0)"А" else "Б"}";trait.sub="Выбор виден заранее; без броска удачи";create.label=if(confirmed)"Подтвердить Пороговика" else "Подготовить линию";create.sub="4 материала · новый ID · оба родителя остаются";take.enabled=game.profile.adventure.find(created)!=null;take.sub=if(take.enabled)"Выбрать именно нового потомка" else "После успешного создания"}
 override fun draw(c:Canvas){val u=ui.u;c.drawColor(0xff182e31.toInt());d.txt.setup(64*u,Palette.GOLD_LIGHT);d.txt.fit("Особая линия",ui.contentW,64*u);d.txt.draw(c,"Особая линия",ui.cx,ui.top+105*u);d.rrect(c,scene.left,scene.top,scene.right,scene.bottom,25*u,0xff304b43.toInt())
  val k=(3*u).toInt().coerceAtLeast(1);val model=SpriteBank.creature(Content.creatures[4].look,"idle",0,0);PixelBlit.sprite(c,model.frame(0,(app.time*3).toInt()),ui.cx,scene.top+175*u,k)
  val text=if(message.isNotEmpty())message else "Пороговик: напор или одноразовая опора.\nДва взрослых родителя разных видов — только Ручьеглот и Камнерог. Сначала пройдите совместное открытие этой парой.\nЭто ручная фантастическая линия, не любое скрещивание и не fusion. Родители, их формы и истории остаются. Детёныш сразу полезен; растёт от новых открытий."
  d.txt.setup(32*u,Palette.TEXT);d.txt.drawWrapped(c,text,ui.cx,scene.top+260*u,ui.contentW-45*u,42*u);buttons().forEach{d.button(c,it,app.time)}
 }
 override fun onDown(id:Int,x:Float,y:Float){pressButtons(id,x,y,*buttons().toTypedArray())}
 override fun onUp(id:Int,x:Float,y:Float){val button=releaseButton(id,x,y)?:return;val p=game.profile.adventure
  when(button){first->{val n=p.animals.count{it.species==0};if(n>0)indexA=(indexA+1)%n;confirmed=false;message=""};second->{val n=p.animals.count{it.species==2};if(n>0)indexB=(indexB+1)%n;confirmed=false;message=""};trait->{choice=1-choice;confirmed=false;message=""};create->{val aa=a();val bb=b();val ready=aa!=null&&bb!=null&&aa.age==2&&bb.age==2&&p.animals.size<32&&p.materials>=4&&game.profile.companions.knows(aa.uid,bb.uid)
   if(!ready){confirmed=false;message="Нужны два взрослых родителя, совместное открытие этой пары, четыре материала и место в питомнике."}else if(!confirmed){request=p.operation+1;confirmed=true;message="Результат: Пороговик; черта и приём от ${if(choice==0)aa!!.name else bb!!.name}. Цена: 4 материала. Оба родителя остаются. Ещё одно нажатие подтвердит."}else{val child=game.createHybrid(aa!!.uid,bb!!.uid,choice,request);confirmed=false;if(child!=null){created=child.uid;message="Появился ${child.name}. Родители ${child.parentA}/${child.parentB} остались. Можно взять его в поход."}else message="Операция не выполнена; ресурсы не списаны. Проверьте условия."}}
   take->{if(game.selectAnimal(created))message="Потомок выбран. Новые открытия помогут ему вырасти."};back->game.openLineage()};refresh()
 }
 override fun onCancel(){confirmed=false;resetPress()}
}
