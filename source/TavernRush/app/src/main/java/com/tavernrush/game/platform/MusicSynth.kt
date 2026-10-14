package com.tavernrush.game.platform
import kotlin.math.*
object MusicSynth {
    const val TRACKS=3;const val VERSION=1
    fun render(theme:Int):ShortArray{
        require(theme in 0..2);val beat=floatArrayOf(0.48f,0.4f,0.52f)[theme];val rate=SoundSynth.RATE;val out=FloatArray((beat*32f*rate).toInt())
        val root=when(theme){0->intArrayOf(60,57,53,55);1->intArrayOf(57,53,52,57);else->intArrayOf(62,58,60,65)}
        fun note(start:Float,dur:Float,midi:Int,v:Float){val from=(start*rate).toInt();val count=(dur*rate).toInt();val hz=(440.0*2.0.pow((midi-69)/12.0)).toFloat();for(i in 0 until count){val ix=from+i;if(ix>=out.size)break;val t=i.toFloat()/rate;val env=minOf(1f,t/0.025f)*minOf(1f,(dur-t)/0.06f).coerceAtLeast(0f);out[ix]+=sin(t*hz*2f*PI.toFloat())*v*env*exp(-t/(dur*0.65f))}}
        for(b in 0..31){val r=root[(b/8)%4];note(b*beat,beat*0.92f,r-12,0.12f);val iv=if(theme==0)intArrayOf(0,4,7,12,7,4,2,7)else intArrayOf(0,3,7,12,7,3,5,7);note(b*beat,beat*0.8f,r+iv[b%8],0.08f);if(b%4==0){note(b*beat,beat*3.8f,r+7,0.035f);note(b*beat,beat*3.8f,r+12,0.025f)}}
        return ShortArray(out.size){(out[it].coerceIn(-0.8f,0.8f)*32767f).toInt().toShort()}
    }
}
