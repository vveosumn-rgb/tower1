package com.tavernrush.game.meta
/** Same identity, chosen lateral form. It does not mutate inheritable talent. */
class MorphProgress {
 private val forms=LinkedHashMap<Int,Int>()
 fun form(id:Int)=forms[id]?:0
 fun copyFrom(o:MorphProgress){forms.clear();forms.putAll(o.forms)}
 fun choose(p:AdventureProgress,id:Int,branch:Int):Boolean {
  val a=p.find(id)?:return false
  if(branch !in 0..2||branch!=0&&(a.age!=2||a.practice<3))return false
  if(branch==0)forms.remove(id) else forms[id]=branch
  return true
 }
 fun encode()=forms.entries.joinToString(","){"${it.key}:${it.value}"}
 companion object {
  fun decode(s:String,p:AdventureProgress):MorphProgress {
   require(s.length<=1000);val m=MorphProgress();val ids=HashSet<Int>()
   if(s.isNotEmpty())for(row in s.split(',')){val n=row.split(':').map{it.toInt()};require(n.size==2&&n[1] in 1..2&&ids.add(n[0])&&m.choose(p,n[0],n[1]))}
   return m
  }
 }
}
