package com.tavernrush.game.meta
/** Рост по открытиям, не по часам. История хранит события, а не награды за вход. */
data class HomeMemory(val region:Int,val bits:Int,val animal:Int,val serial:Int)
class HomeProgress {
    val plots=IntArray(3);val stages=IntArray(3)
    var nectar=0;var herbs=0;var supply=0;var talked=0;var count=0;var intent=0
    val memories=ArrayList<HomeMemory>()
    fun copyFrom(o:HomeProgress){o.plots.copyInto(plots);o.stages.copyInto(stages);nectar=o.nectar;herbs=o.herbs;supply=o.supply;talked=o.talked;count=o.count;intent=o.intent;memories.clear();memories.addAll(o.memories)}
    fun plant(i:Int,kind:Int,p:AdventureProgress):Boolean{if(i !in 0..2||kind !in 1..2||plots[i]!=0||p.materials<1)return false;p.materials--;plots[i]=kind;stages[i]=0;return true}
    fun harvest(i:Int):Boolean{if(i !in 0..2||plots[i]==0||stages[i]!=2)return false;if(plots[i]==1){if(nectar>=9999)return false;nectar++}else{if(herbs>=9999)return false;herbs++};stages[i]=0;return true}
    fun chooseSupply(i:Int):Boolean{if(i !in 0..2||i==1&&nectar==0||i==2&&herbs==0)return false;supply=i;return true}
    fun takeSupply():Int {val i=supply;if(i==1&&nectar>0){nectar--;return 1};if(i==2&&herbs>0){herbs--;return 2};return 0}
    fun discovery(region:Int,bits:Int,animal:Int){
        if(region !in 0..5||bits !in 1..255||count>=1000000)return
        for(i in plots.indices)if(plots[i]!=0)stages[i]=(stages[i]+1).coerceAtMost(2)
        memories.add(HomeMemory(region,bits,animal,count+1));count++;if(memories.size>16)memories.removeAt(0)
    }
    fun encode()=(plots.toList()+stages.toList()+listOf(nectar,herbs,supply,talked,count,intent)).joinToString(",")+memories.joinToString(";",prefix=";"){"${it.region},${it.bits},${it.animal},${it.serial}"}
    companion object {
        fun decode(s:String):HomeProgress{
            require(s.length<2000);val rows=s.split(';');val n=rows[0].split(',').map{it.toInt()};require(n.size==12)
            val limits=List(3){0..2}+List(3){0..2}+listOf(0..9999,0..9999,0..2,0..7,0..1000000,0..2)
            for(i in n.indices)require(n[i] in limits[i])
            val p=HomeProgress();for(i in 0..2){p.plots[i]=n[i];p.stages[i]=n[i+3];require(p.plots[i]!=0||p.stages[i]==0)}
            p.nectar=n[6];p.herbs=n[7];p.supply=n[8];p.talked=n[9];p.count=n[10];p.intent=n[11]
            val events=rows.drop(1).filter{it.isNotEmpty()};require(events.size<=16);var last=0
            for(row in events){val v=row.split(',').map{it.toInt()};require(v.size==4&&v[0] in 0..5&&v[1] in 1..255&&v[2] in 0..200000&&v[3]>last&&v[3]<=p.count);last=v[3];p.memories.add(HomeMemory(v[0],v[1],v[2],v[3]))};return p
        }
    }
}
