package com.tavernrush.game.ui.screens
import android.graphics.Canvas
import android.graphics.RectF
import com.tavernrush.game.content.*
import com.tavernrush.game.render.Palette
import com.tavernrush.game.render.pixel.*
import com.tavernrush.game.ui.*
/** Three identity-based presets. Teaching is not birth, fusion or consumption. */
class CompositionScreen(app:GameApp):Screen(app){
 val previous=Button("← Особь",Button.DARK);val next=Button("Особь →",Button.DARK)
 val lead=Button("Ведущий",Button.GOLD);val reserve=Button("В резерв",Button.DARK)
 val mode=Button("",Button.DARK);val donor=Button("",Button.DARK)
 val teach=Button("",Button.PRIMARY);val slot=Button("",Button.DARK)
 val save=Button("Сохранить",Button.GOLD);val load=Button("Применить",Button.PRIMARY)
 val back=Button("В питомник",Button.GOLD)
 private val scene=RectF();var animalIndex=0;var donorIndex=0;var slotIndex=0;var message="";private set
 fun buttons()=listOf(previous,next,lead,reserve,mode,donor,teach,slot,save,load,back)
 override fun onEnter(){message="";layout()}
 private fun shown()=game.profile.adventure.animals.getOrNull(animalIndex)
 private fun donors()=game.profile.adventure.animals.filter{it.uid!=shown()?.uid&&it.species!=shown()?.species}
 override fun layout(){
  val u=ui.u;val l=ui.cx-ui.contentW/2;val r=ui.cx+ui.contentW/2
  scene.set(l,ui.top+165*u,r,ui.bottom-1020*u)
  val top=ui.bottom-990*u;val h=135*u
  val pairs=listOf(previous to next,lead to reserve,mode to donor,teach to slot,save to load)
  for((i,p)in pairs.withIndex()){val y=top+i*(h+20*u);p.first.set(l,y,ui.cx-12*u,y+h);p.second.set(ui.cx+12*u,y,r,y+h)}
  back.set(l,ui.bottom-180*u,r,ui.bottom-45*u);buttons().forEach{it.textSize=35*u};refresh()
 }
 private fun refresh(){
  val p=game.profile;val a=p.adventure;animalIndex=animalIndex.coerceIn(0,maxOf(0,a.animals.lastIndex));val x=shown();val ds=donors();donorIndex=donorIndex.coerceIn(0,maxOf(0,ds.lastIndex));val d=ds.getOrNull(donorIndex)
  lead.sub=if(x?.uid==a.selected)"Выбран" else "Именно показанная особь";reserve.sub=if(x?.uid==p.companions.reserve)"Выбран" else "Другого вида, не ведущий"
  mode.label="Режим: "+arrayOf("один","смена","двое")[p.teamMode];mode.sub="Переключить · без новых кнопок"
  donor.label=d?.name?:"Нет наставника";donor.sub="Следующий наставник"
  teach.label=if(x!=null&&p.companions.lessons[x.uid]==d?.uid)"Убрать резонанс" else "Обучить резонансу"
  teach.sub=if(x!=null&&d!=null&&p.companions.knows(x.uid,d.uid))"Совместное открытие пройдено" else "Вместе ≥6 с + новое открытие"
  slot.label="Состав ${slotIndex+1}/3";slot.sub=if(p.companions.presets[slotIndex]==null)"Пустой · сменить слот" else "Сохранён · сменить слот"
  load.enabled=p.companions.presets[slotIndex]!=null;lead.enabled=x!=null;reserve.enabled=x!=null;teach.enabled=x!=null&&d!=null;save.enabled=a.find(a.selected)!=null
 }
 override fun draw(c:Canvas){
  val u=ui.u;val p=game.profile;val a=shown();val donor=donors().getOrNull(donorIndex);c.drawColor(0xff172e30.toInt())
  d.txt.setup(66*u,Palette.GOLD_LIGHT);d.txt.fit("Составы и резонанс",ui.contentW,66*u);d.txt.draw(c,"Составы и резонанс",ui.cx,ui.top+110*u)
  d.rrect(c,scene.left,scene.top,scene.right,scene.bottom,25*u,0xff253c39.toInt())
  val k=(2*u).toInt().coerceAtLeast(1)
  if(a!=null){val model=SpriteBank.creature(Content.creatures[a.species].look,"idle",a.age,p.morph.form(a.uid));PixelBlit.sprite(c,model.frame(0,(app.time*3).toInt()),scene.left+ui.contentW*.23f,scene.top+175*u,k)
   if(donor!=null){val b=SpriteBank.creature(Content.creatures[donor.species].look,"idle",donor.age,p.morph.form(donor.uid));PixelBlit.sprite(c,b.frame(0,(app.time*3).toInt()),scene.left+ui.contentW*.77f,scene.top+175*u,k);d.sp.color=Content.creatures[donor.species].color;d.sp.strokeWidth=4*u;val x=scene.left+ui.contentW*.38f;val y=scene.top+120*u;c.drawLine(x,y,scene.left+ui.contentW*.62f,y,d.sp)}
  }
  val selected=p.adventure.find(p.adventure.selected)?.name?:"не выбран";val rest=p.adventure.find(p.companions.reserve)?.name?:"нет"
  val effect=when(donor?.species){0->"Эхо воды: одна близкая цель намокает после приёма.";1->"Эхо разряда: 12 урона одной мокрой цели, без новой цепи.";2->"Эхо опоры: короткая защита от одного удара рядом.";3->"Эхо листа: короткий неподвижный полог радиусом 100.";4->"Эхо напора: слабый толчок одной близкой цели.";else->"Найдите две особи разных видов."}
  d.txt.setup(32*u,Palette.TEXT);val body=(a?.name?:"Питомник пока пуст")+"\nВедущий: $selected\nРезерв: $rest\n$effect\nНе чаще 9 с. Один приём, не новый питомец. Обе особи остаются."
  d.txt.drawWrapped(c,if(message.isEmpty())body else message,ui.cx,scene.top+240*u,ui.contentW-45*u,40*u)
  buttons().forEach{d.button(c,it,app.time)}
 }
 override fun onDown(id:Int,x:Float,y:Float){pressButtons(id,x,y,*buttons().toTypedArray())}
 override fun onUp(id:Int,x:Float,y:Float){val b=releaseButton(id,x,y)?:return;val a=shown();val ds=donors();val donor=ds.getOrNull(donorIndex)
  when(b){previous->{animalIndex--;message=""};next->{animalIndex++;message=""};lead->{message=if(a!=null&&game.selectAnimal(a.uid))"Ведущий: ${a.name}. При совпадении резерв очищен." else "Выберите существующую особь."};reserve->{message=if(a!=null&&game.chooseReserveAnimal(a.uid))"Резерв: ${a.name}. Для совместного открытия выберите режим «двое»." else "Нужна особь другого вида, не ведущий."};mode->{game.cycleIndividualMode();message=""};this.donor->{if(ds.isNotEmpty())donorIndex=(donorIndex+1)%ds.size;message=""};teach->{val remove=a!=null&&game.profile.companions.lessons[a.uid]==donor?.uid;message=if(a!=null&&donor!=null&&game.teachResonance(a.uid,if(remove)0 else donor.uid))"Настройка сохранена. Имена, тела и родители не меняются." else "Сначала возьмите эту пару вместе: обе участвуют не меньше 6 секунд, затем помогите миру или завершите контракт."};slot->{slotIndex=(slotIndex+1)%3;message=""};save->{message=if(game.saveComposition(slotIndex))"Состав ${slotIndex+1} сохранён: личности, режим и резонанс ведущего." else "Проверьте ведущего и резерв другого вида для режима команды."};load->{message=if(game.loadComposition(slotIndex))"Состав ${slotIndex+1} применён без повторной настройки." else "В этом слоте нет допустимого состава."};back->game.openLineage()};refresh()
 }
 override fun onCancel()=resetPress()
}
