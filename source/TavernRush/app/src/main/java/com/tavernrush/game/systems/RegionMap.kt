package com.tavernrush.game.systems

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/** Authored geometry. Logical landmark IDs remain stable in old saves. */
class RegionMap(val x:FloatArray,val y:FloatArray,val walls:Array<Wall>) {
    data class Wall(val x:Float,val y:Float,val hx:Float,val hy:Float,val opensWith:Int=0)
    fun enabled(i:Int,flags:Int)=if(i==0)flags and 2==0 else walls[i].opensWith==0||flags and walls[i].opensWith==0
    fun blocked(x:Float,y:Float,r:Float,flags:Int):Boolean {
        for(i in walls.indices){val b=walls[i];if(enabled(i,flags)&&abs(x-b.x)<b.hx+r&&abs(y-b.y)<b.hy+r)return true};return false
    }
    fun clear(ax:Float,ay:Float,bx:Float,by:Float,r:Float,flags:Int):Boolean {
        if(!ax.isFinite()||!ay.isFinite()||!bx.isFinite()||!by.isFinite())return false
        for(i in walls.indices){if(!enabled(i,flags))continue;val b=walls[i];var lo=0f;var hi=1f
            val dx=bx-ax;val dy=by-ay
            if(abs(dx)<.0001f){if(abs(ax-b.x)>=b.hx+r)continue}else{val a=(b.x-b.hx-r-ax)/dx;val c=(b.x+b.hx+r-ax)/dx;lo=max(lo,min(a,c));hi=min(hi,max(a,c));if(lo>hi)continue}
            if(abs(dy)<.0001f){if(abs(ay-b.y)>=b.hy+r)continue}else{val a=(b.y-b.hy-r-ay)/dy;val c=(b.y+b.hy+r-ay)/dy;lo=max(lo,min(a,c));hi=min(hi,max(a,c));if(lo>hi)continue}
            return false
        };return true
    }
    /** Conservative finite ray clip for a beam's half-width, shared with its telegraph. */
    fun rayLength(x:Float,y:Float,dx:Float,dy:Float,length:Float,r:Float,flags:Int):Float {
        if(!x.isFinite()||!y.isFinite()||!dx.isFinite()||!dy.isFinite()||!length.isFinite()||length<=0f||!clear(x,y,x,y,r,flags))return 0f
        if(clear(x,y,x+dx*length,y+dy*length,r,flags))return length
        var low=0f;var high=length
        repeat(12){val mid=(low+high)*.5f;if(clear(x,y,x+dx*mid,y+dy*mid,r,flags))low=mid else high=mid};return (low-1f).coerceAtLeast(0f)
    }
    /** Minimum-axis depenetration. Caller retains velocity on the free axis. */
    fun resolve(x:Float,y:Float,r:Float,flags:Int,out:FloatArray):Int {
        var px=x.coerceIn(-650f,650f);var py=y.coerceIn(-950f,650f);var mask=0
        repeat(2){for(i in walls.indices){if(!enabled(i,flags))continue;val b=walls[i];val dx=px-b.x;val dy=py-b.y;val ox=b.hx+r-abs(dx);val oy=b.hy+r-abs(dy)
            if(ox>0f&&oy>0f){if(ox<oy){px=b.x+(if(dx<0f)-1 else 1)*(b.hx+r);mask=mask or 1}else{py=b.y+(if(dy<0f)-1 else 1)*(b.hy+r);mask=mask or 2}}}}
        out[0]=px;out[1]=py;return mask
    }
    fun sweep(ax:Float,ay:Float,bx:Float,by:Float,r:Float,flags:Int,out:FloatArray):Int {
        val dx=bx-ax;val dy=by-ay;val steps=(kotlin.math.ceil(max(abs(dx),abs(dy))/12f).toInt()).coerceIn(1,256)
        var mask=resolve(ax,ay,r,flags,out);var x=out[0];var y=out[1]
        repeat(steps){mask=mask or resolve(x+dx/steps,y+dy/steps,r,flags,out);x=out[0];y=out[1]};return mask
    }
    companion object {
        private fun make(points:IntArray,gate:Wall,vararg other:Wall):RegionMap {
            val x=FloatArray(15);val y=FloatArray(15);val ids=intArrayOf(0,1,2,3,4,5,6,10,11)
            for(i in ids.indices){x[ids[i]]=points[i*2].toFloat();y[ids[i]]=points[i*2+1].toFloat()}
            x[7]=x[6]-90;x[8]=x[6]+90;x[9]=x[6];y[7]=y[6];y[8]=y[6];y[9]=y[6]-90
            x[12]=-480f;y[12]=250f;x[13]=-555f;y[13]=35f;x[14]=-480f;y[14]=-350f
            return RegionMap(x,y,arrayOf(gate,*other))
        }
        val all=arrayOf(
            make(intArrayOf(0,120,-250,-200,260,150,0,-520,-320,-650,0,500,390,-420,430,-160,-500,-730),Wall(0f,120f,170f,25f),
                Wall(-605f,-110f,15f,345f),Wall(-335f,-110f,15f,345f),Wall(-480f,-455f,140f,15f),
                Wall(-567f,235f,53f,15f),Wall(-383f,235f,63f,15f),
                Wall(-567f,-120f,53f,15f),Wall(-383f,-120f,63f,15f),Wall(-480f,-120f,34f,15f,256)),
            make(intArrayOf(-90,180,-430,-200,350,80,210,-620,-310,-620,-360,500,410,-320,480,-160,-540,-820),Wall(-90f,180f,150f,25f),Wall(-100f,-300f,32f,150f),Wall(260f,-150f,150f,22f)),
            make(intArrayOf(70,120,-420,-340,340,20,-120,-650,430,-580,300,520,350,-350,430,-160,-420,-820),Wall(70f,120f,170f,25f),Wall(-240f,-20f,30f,160f),Wall(120f,-470f,150f,22f)),
            make(intArrayOf(-170,120,120,-280,360,140,450,-600,-400,-500,-300,520,0,-630,430,-160,-520,-820),Wall(-170f,120f,25f,170f),Wall(-10f,-130f,30f,160f),Wall(220f,-510f,150f,22f)),
            make(intArrayOf(120,120,-300,-210,330,60,0,-640,-450,-520,300,520,330,-530,410,-350,-510,-820),Wall(120f,120f,170f,25f),Wall(40f,-180f,30f,150f),Wall(-210f,-610f,90f,22f)),
            make(intArrayOf(-70,80,-420,-260,340,170,320,-600,-200,-620,-330,500,400,-360,430,-160,-520,-800),Wall(-70f,80f,25f,170f),Wall(130f,-190f,25f,160f),Wall(-90f,-490f,170f,22f))
        )
    }
}

/** Small visibility graph, reused buffers, no per-actor path allocations. */
class RegionNavigator {
    private var capacity=0
    private var xs=FloatArray(0);private var ys=FloatArray(0);private var dist=FloatArray(0);private var parent=IntArray(0);private var used=BooleanArray(0)
    private fun ensure(n:Int){if(n<=capacity)return;capacity=n;xs=FloatArray(n);ys=FloatArray(n);dist=FloatArray(n);parent=IntArray(n);used=BooleanArray(n)}
    fun next(m:RegionMap,flags:Int,ax:Float,ay:Float,bx:Float,by:Float,r:Float,out:FloatArray):Boolean {
        ensure(2+m.walls.size*4)
        out[0]=bx;out[1]=by
        if(m.blocked(ax,ay,r,flags)){m.resolve(ax,ay,r+1f,flags,out);return true}
        if(m.clear(ax,ay,bx,by,r,flags))return true
        xs[0]=ax;ys[0]=ay;xs[1]=bx;ys[1]=by;var n=2
        for(i in m.walls.indices){if(!m.enabled(i,flags))continue;val b=m.walls[i];for(k in 0..3){xs[n]=b.x+(if(k and 1==0)-1 else 1)*(b.hx+r+8f);ys[n]=b.y+(if(k and 2==0)-1 else 1)*(b.hy+r+8f);n++}}
        dist.fill(Float.POSITIVE_INFINITY);parent.fill(-1);used.fill(false);dist[0]=0f
        repeat(n){var u=-1;var best=Float.POSITIVE_INFINITY;for(i in 0 until n)if(!used[i]&&dist[i]<best){best=dist[i];u=i};if(u<0)return@repeat
            used[u]=true
            for(v in 0 until n){if(used[v]||!m.clear(xs[u],ys[u],xs[v],ys[v],r,flags))continue;val dx=xs[u]-xs[v];val dy=ys[u]-ys[v];val d=best+kotlin.math.sqrt(dx*dx+dy*dy);if(d<dist[v]){dist[v]=d;parent[v]=u}}
        }
        if(parent[1]<0){out[0]=ax;out[1]=ay;return false}
        var k=1;while(parent[k]>0)k=parent[k];out[0]=xs[k];out[1]=ys[k];return true
    }
}
