package com.tavernrush.game.meta
import com.tavernrush.game.content.Content
import com.tavernrush.game.systems.PipeBoard

/** Личность, а не вид. Родители всегда сохраняются. */
data class Animal(val uid:Int,val species:Int,var age:Int,var trait:Int,var talent:Int,val parentA:Int=0,val parentB:Int=0,val generation:Int=0,var practice:Int=0,val source:Int=0) {
    val name:String get()=Content.creatures[species].names[(uid-1)%Content.creatures[species].names.size]+" · "+uid
    fun record()=listOf(uid,species,age,trait,talent,parentA,parentB,generation,practice,source).joinToString(",")
}
/** Все изменения записываются одной подписанной транзакцией профиля. */
class AdventureProgress {
    val regions=IntArray(6)
    var materials=0;var rescued=0;var decoration=0;var recipes=0;var recipe=0;var blueprint=0;var scheme=0
    var nextId=1;var selected=0;var operation=0;var contractCounter=0;var contractPaid=0;var story=0;var expeditions=0
    val animals=ArrayList<Animal>()
    fun find(id:Int)=animals.firstOrNull{it.uid==id}
    fun copyFrom(o:AdventureProgress){o.regions.copyInto(regions);materials=o.materials;rescued=o.rescued;decoration=o.decoration;recipes=o.recipes;recipe=o.recipe;blueprint=o.blueprint;scheme=o.scheme;nextId=o.nextId;selected=o.selected;operation=o.operation;contractCounter=o.contractCounter;contractPaid=o.contractPaid;story=o.story;expeditions=o.expeditions;animals.clear();animals.addAll(o.animals.map{it.copy()})}
    fun adopt(species:Int,source:Int):Animal? {
        if(species !in Content.creatures.indices||!Content.creatures[species].wild||source !in 1..18||animals.size>=32||animals.any{it.source==source}||nextId>200000)return null
        val a=Animal(nextId++,species,2,source%3,(source-1)%3,source=source);animals.add(a);if(selected==0)selected=a.uid;return a
    }
    fun collectWaiting(){for(i in regions.indices){if(regions[i] and 4!=0)adopt(i%3,i*2+1);if(regions[i] and 8!=0)adopt(i%3,i*2+2);if(regions[i] and 64!=0)adopt(3,13+i)}}
    fun breed(a:Int,b:Int,choice:Int,request:Int):Animal? {
        if(request!=operation+1||operation>=1000000||choice !in 0..1||a==b||animals.size>=32||nextId>200000||materials<2)return null
        val x=find(a)?:return null;val y=find(b)?:return null
        if(x.age!=2||y.age!=2||x.species!=y.species)return null
        val gen=maxOf(x.generation,y.generation)+1;if(gen>8)return null
        val chosen=if(choice==0)x else y
        val child=Animal(nextId,x.species,0,chosen.trait,chosen.talent,a,b,gen)
        materials-=2;nextId++;operation=request;animals.add(child);return child
    }
    /** Hand-authored fantasy line, NOT universal biological breeding or donor consumption. */
    fun hybrid(a:Int,b:Int,choice:Int,request:Int,unlocked:Boolean):Animal? {
        if(!unlocked||request!=operation+1||operation>=1000000||choice !in 0..1||a==b||animals.size>=32||nextId>200000||materials<4)return null
        val x=find(a)?:return null;val y=find(b)?:return null
        if(x.age!=2||y.age!=2||setOf(x.species,y.species)!=setOf(0,2))return null
        val gen=maxOf(x.generation,y.generation)+1;if(gen>8)return null
        val chosen=if(choice==0)x else y
        val child=Animal(nextId,4,0,chosen.trait,chosen.talent,a,b,gen)
        materials-=4;nextId++;operation=request;animals.add(child);return child
    }
    fun practiceDiscovery(){find(selected)?.let{it.practice=(it.practice+1).coerceAtMost(1000000);it.age=maxOf(it.age,if(it.practice>=3)2 else if(it.practice>=1)1 else 0)}}
    fun choose(id:Int):Boolean{if(find(id)==null)return false;selected=id;return true}
    fun train(id:Int,talent:Int):Boolean{val a=find(id)?:return false;if(talent !in 0..2||a.practice<2)return false;a.talent=talent;return true}
    fun encode():String {
        val head=regions.toList()+listOf(materials,rescued,decoration,recipes,recipe,blueprint,scheme,nextId,selected,operation,contractCounter,contractPaid,story,expeditions)
        return head.joinToString(",")+animals.joinToString(";",prefix=";"){it.record()}
    }
    companion object {
        fun decode(s:String):AdventureProgress {
            require(s.length<=8000)
            val rows=s.split(';');val nums=rows[0].split(',').map{it.toInt()};require(nums.size==20)
            val limits=listOf(0..1023)+List(5){0..255}+listOf(0..100000,0..63,0..3,0..7,0..2,0..262143,0..1,1..200001,0..200000,0..1000000,0..1000000,0..1000000,0..6,0..1000000)
            for(i in nums.indices)require(nums[i] in limits[i])
            require(nums[0] and 512==0||nums[0] and 256!=0)
            val p=AdventureProgress();for(i in 0..5)p.regions[i]=nums[i]
            p.materials=nums[6];p.rescued=nums[7];p.decoration=nums[8];p.recipes=nums[9];p.recipe=nums[10];p.blueprint=nums[11];p.scheme=nums[12];p.nextId=nums[13];p.selected=nums[14];p.operation=nums[15];p.contractCounter=nums[16];p.contractPaid=nums[17];p.story=nums[18];p.expeditions=nums[19]
            require(p.contractPaid<=p.contractCounter)
            if(p.scheme!=0){val b=PipeBoard();b.load(p.blueprint);require(b.solve())}
            val body=rows.drop(1).filter{it.isNotEmpty()};require(body.size<=32)
            for(row in body){val n=row.split(',').map{it.toInt()};require(n.size==10)
                val lim=listOf(1..200000,0..(Content.creatures.size-1),0..2,0..2,0..2,0..200000,0..200000,0..8,0..1000000,0..18)
                for(i in n.indices)require(n[i] in lim[i])
                require(p.find(n[0])==null&&n[0]<p.nextId);require((n[5]==0)==(n[6]==0))
                if(n[5]!=0){val a=p.find(n[5])?:error("parent A");val b=p.find(n[6])?:error("parent B");require(a.uid!=b.uid&&a.uid<n[0]&&b.uid<n[0]&&(a.species==n[1]&&b.species==n[1]||n[1]==4&&setOf(a.species,b.species)==setOf(0,2))&&n[7]==maxOf(a.generation,b.generation)+1)}else require(n[7]==0&&n[1]!=4)
                require(n[1]!=4||n[9]==0);require(n[9]==0||p.animals.none{it.source==n[9]})
                p.animals.add(Animal(n[0],n[1],n[2],n[3],n[4],n[5],n[6],n[7],n[8],n[9]))
            }
            require(p.selected==0||p.find(p.selected)!=null);return p
        }
    }
}
