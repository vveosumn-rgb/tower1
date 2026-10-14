package com.tavernrush.game.meta
import com.tavernrush.game.content.Creatures
/** Identity references, not replacement creatures. No parent/donor is consumed. */
data class TeamPreset(val lead:Int,val reserve:Int,val mode:Int,val donor:Int)
class CompanionProgress {
 var reserve=0
 val known=ArrayList<Pair<Int,Int>>()
 val lessons=LinkedHashMap<Int,Int>()
 val presets=arrayOfNulls<TeamPreset>(3)
 fun copyFrom(o:CompanionProgress){reserve=o.reserve;known.clear();known.addAll(o.known);lessons.clear();lessons.putAll(o.lessons);for(i in presets.indices)presets[i]=o.presets[i]?.copy()}
 private fun pair(a:Int,b:Int)=minOf(a,b) to maxOf(a,b)
 fun compatible(p:AdventureProgress,a:Int,b:Int):Boolean {val x=p.find(a)?:return false;val y=p.find(b)?:return false;return a!=b&&x.species!=y.species}
 fun knows(a:Int,b:Int)=known.contains(pair(a,b))
 fun learn(p:AdventureProgress,a:Int,b:Int):Boolean {if(!compatible(p,a,b)||known.size>=64||knows(a,b))return false;known.add(pair(a,b));return true}
 fun teach(p:AdventureProgress,a:Int,b:Int):Boolean {if(b==0){if(p.find(a)==null)return false;lessons.remove(a);return true};if(!compatible(p,a,b)||!knows(a,b))return false;lessons[a]=b;return true}
 fun setReserve(p:AdventureProgress,id:Int):Boolean {if(id==0){reserve=0;return true};if(!compatible(p,p.selected,id))return false;reserve=id;return true}
 fun savePreset(p:AdventureProgress,slot:Int,mode:Int):Boolean {if(slot !in 0..2||mode !in 0 until Creatures.MODES||p.find(p.selected)==null||reserve!=0&&!compatible(p,p.selected,reserve)||mode!=Creatures.MODE_SOLO&&reserve==0)return false;presets[slot]=TeamPreset(p.selected,reserve,mode,lessons[p.selected]?:0);return true}
 fun loadPreset(p:AdventureProgress,slot:Int):TeamPreset? {val s=presets.getOrNull(slot)?:return null;if(s.mode !in 0 until Creatures.MODES||p.find(s.lead)==null||s.reserve!=0&&!compatible(p,s.lead,s.reserve)||s.mode!=Creatures.MODE_SOLO&&s.reserve==0||s.donor!=0&&(!compatible(p,s.lead,s.donor)||!knows(s.lead,s.donor)))return null;p.selected=s.lead;reserve=s.reserve;teach(p,s.lead,s.donor);return s}
 fun encode():String = reserve.toString()+";"+known.joinToString(","){"${it.first}:${it.second}"}+";"+lessons.entries.joinToString(","){"${it.key}:${it.value}"}+";"+presets.joinToString(","){s->if(s==null)"-" else "${s.lead}:${s.reserve}:${s.mode}:${s.donor}"}
 companion object {
  fun decode(s:String,p:AdventureProgress):CompanionProgress {
   require(s.length<=5000);val rows=s.split(';');require(rows.size==4);val c=CompanionProgress();c.reserve=rows[0].toInt();require(c.reserve==0||p.find(c.reserve)!=null)
   if(rows[1].isNotEmpty())for(v in rows[1].split(',')){val n=v.split(':').map{it.toInt()};require(n.size==2&&n[0]<n[1]&&c.learn(p,n[0],n[1]))}
   if(rows[2].isNotEmpty())for(v in rows[2].split(',')){val n=v.split(':').map{it.toInt()};require(n.size==2&&n[1]!=0&&!c.lessons.containsKey(n[0])&&c.teach(p,n[0],n[1]))}
   val slots=rows[3].split(',');require(slots.size==3)
   for(i in slots.indices)if(slots[i]!="-"){val n=slots[i].split(':').map{it.toInt()};require(n.size==4&&p.find(n[0])!=null&&n[2] in 0 until Creatures.MODES&&(n[1]==0||c.compatible(p,n[0],n[1]))&&(n[2]==Creatures.MODE_SOLO||n[1]!=0)&&(n[3]==0||c.compatible(p,n[0],n[3])&&c.knows(n[0],n[3])));c.presets[i]=TeamPreset(n[0],n[1],n[2],n[3])}
   return c
  }
 }
}
