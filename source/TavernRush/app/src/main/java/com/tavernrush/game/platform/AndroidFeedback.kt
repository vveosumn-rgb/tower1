package com.tavernrush.game.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tavernrush.game.core.GameEvents
import com.tavernrush.game.core.Haptic
import com.tavernrush.game.core.Sfx
import java.io.File
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong

/**
 * Звук (SoundPool из синтезированных WAV в cacheDir) и вибрация. Вызывается из игрового потока:
 * всё быстрое и неблокирующее, частота повторов ограничена, чтобы толпа врагов не «трещала».
 */
class AndroidFeedback(context: Context, private val onExit: () -> Unit) : GameEvents {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val musicSamples=AtomicIntegerArray(MusicSynth.TRACKS)
    private var musicStream=0;private var musicTheme=-1
    private val sampleOf = AtomicIntegerArray(Sfx.COUNT)
    private val ready = AtomicLong(0L)
    private val lastPlay = LongArray(Sfx.COUNT)
    private var seed = 0x2545F491
    private val vibrator: Vibrator?
    private val effects: Array<VibrationEffect>
    private var lastVibe = 0L
    @Volatile private var released = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId in 1..63) {
                while (true) {
                    val m = ready.get()
                    if (ready.compareAndSet(m, m or (1L shl sampleId))) break
                }
            }
        }
        require(tableSizes().all{it==Sfx.COUNT})
        val dir = context.cacheDir
        Thread({
            for (id in 0 until Sfx.COUNT) {
                if (released) return@Thread
                try {
                    val f = File(dir, "sfx_v${SoundSynth.VERSION}_$id.wav")
                    if (!f.exists() || f.length() < 64) {
                        val tmp = File(dir, f.name + ".tmp")
                        tmp.writeBytes(SoundSynth.wav(SoundSynth.render(id)))
                        tmp.renameTo(f)
                    }
                    sampleOf.set(id, pool.load(f.path, 1))
                } catch (e: Exception) {
                    // без звука игра остаётся играбельной
                }
            }
            for(id in 0 until MusicSynth.TRACKS){if(released)return@Thread;try{val f=File(dir,"music_v${MusicSynth.VERSION}_$id.wav");if(!f.exists()||f.length()<64)f.writeBytes(SoundSynth.wav(MusicSynth.render(id)));musicSamples.set(id,pool.load(f.path,1))}catch(_:Exception){}}
        }, "sfx-synth").start()

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val amp = vibrator?.hasAmplitudeControl() == true
        fun shot(ms: Long, a: Int) = VibrationEffect.createOneShot(ms, if (amp) a else VibrationEffect.DEFAULT_AMPLITUDE)
        effects = arrayOf(shot(12, 70), shot(35, 170), shot(85, 255), shot(18, 120), shot(48, 210))
    }

    override fun sfx(id: Int) {
        if (id < 0 || id >= Sfx.COUNT || released) return
        val sid = sampleOf.get(id)
        if (sid <= 0 || sid > 63 || (ready.get() and (1L shl sid)) == 0L) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlay[id] < MIN_GAP_MS[id]) return
        lastPlay[id] = now
        seed = seed * 1103515245 + 12345
        val jitter = ((seed ushr 16) and 0x7FFF) / 32767f
        val rate = if (PITCHY[id]) 0.9f + 0.25f * jitter else 1f
        pool.play(sid, VOLUME[id], VOLUME[id], PRIORITY[id], 0, rate)
    }

    override fun haptic(kind: Int) {
        val v = vibrator ?: return
        if (kind < 0 || kind >= effects.size) return
        val now = SystemClock.uptimeMillis()
        val gap = if (kind == Haptic.TICK) 60L else if (kind == Haptic.DASH) 90L else 140L
        if (now - lastVibe < gap) return
        lastVibe = now
        try { v.vibrate(effects[kind]) } catch (e: Exception) { }
    }

    @Synchronized override fun music(theme:Int){
        if(released)return
        if(theme !in 0 until MusicSynth.TRACKS){if(musicStream!=0)pool.stop(musicStream);musicStream=0;musicTheme=-1;return}
        if(musicTheme==theme&&musicStream!=0)return
        val sid=musicSamples.get(theme);if(sid<=0||sid>63||ready.get()and(1L shl sid)==0L)return
        if(musicStream!=0)pool.stop(musicStream);musicTheme=theme;musicStream=pool.play(sid,0.22f,0.22f,0,-1,1f)
    }
    override fun exitRequested() = onExit()

    fun release() {
        released = true
        pool.release()
    }

    internal companion object {
        fun tableSizes()=intArrayOf(MIN_GAP_MS.size,VOLUME.size,PRIORITY.size,PITCHY.size)
        //                              HIT KILL XP  COIN LVL HURT CLICK DEATH BOOM ZAP CHEST WAVE BUY THROW VICT DASH TELE SHOT ROAR PERF SHLD PHASE
        val MIN_GAP_MS = longArrayOf(   45,  40, 35,  50,  200, 120,  40,  500,  90, 70, 200,  400, 60, 70,  800, 120, 160, 90, 600, 300, 120, 600, 70, 100, 100, 100, 70, 700, 400)
        val VOLUME = floatArrayOf(    0.45f, 0.5f, 0.35f, 0.45f, 0.8f, 0.7f, 0.6f, 0.9f, 0.75f, 0.5f, 0.8f, 0.7f, 0.7f, 0.3f, 0.9f, 0.55f, 0.5f, 0.4f, 0.95f, 0.85f, 0.6f, 0.9f, 0.6f, 0.6f, 0.55f, 0.65f, 0.5f, 0.45f, 0.75f)
        val PRIORITY = intArrayOf(       1,   1,  0,   1,   3,   2,   3,    3,    2,   1,   3,    2,   3,  0,    3,    2,    2,   1,    3,    3,    2,    3, 2, 2, 2, 2, 1, 2, 3)
        val PITCHY = booleanArrayOf(  true, true, true, true, false, true, false, false, true, true, false, false, false, true, false, true, false, true, false, false, true, false, false, false, false, false, true, false, false)
    }
}
