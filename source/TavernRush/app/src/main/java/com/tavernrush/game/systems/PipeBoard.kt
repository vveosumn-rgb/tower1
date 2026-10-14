package com.tavernrush.game.systems
/** Конечная сеть: вход слева в 0, выход справа из 8. Несколько допустимых маршрутов. */
class PipeBoard {
    val rotations=IntArray(9)
    private val shapes=intArrayOf(10,12,10,10,3,12,3,10,3)
    var reachable=0;private set
    fun mask(i:Int):Int {var m=shapes[i];repeat(rotations[i]){m=((m shl 1)and 15)or(m ushr 3)};return m}
    fun rotate(i:Int){if(i !in 0..8)return;rotations[i]=(rotations[i]+1)and 3;solve()}
    fun reset(){rotations.fill(1);solve()}
    fun load(packed:Int){for(i in rotations.indices)rotations[i]=(packed ushr(i*2))and 3;solve()}
    fun packed():Int {var a=0;for(i in rotations.indices)a=a or(rotations[i]shl(i*2));return a}
    fun solve():Boolean {
        reachable=0;if(mask(0)and 8==0)return false
        val queue=IntArray(9);var head=0;var tail=1;queue[0]=0;reachable=1
        while(head<tail){val i=queue[head++];val m=mask(i)
            for(d in 0..3){if(m and(1 shl d)==0)continue
                val j=when(d){0->if(i>=3)i-3 else -1;1->if(i%3<2)i+1 else -1;2->if(i<6)i+3 else -1;else->if(i%3>0)i-1 else -1}
                if(j<0||reachable and(1 shl j)!=0||mask(j)and(1 shl((d+2)%4))==0)continue
                reachable=reachable or(1 shl j);queue[tail++]=j
            }
        }
        return reachable and 256!=0&&mask(8)and 2!=0
    }
}
