package com.tavernrush.game.platform

import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.Sfx
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Процедурные звуки (без файлов-ассетов): короткие PCM 16 бит, моно, 22 050 Гц.
 * Это временная озвучка до этапа R07: «сочная», но простая. Меняя звуки — поднимите [VERSION],
 * иначе на устройстве останутся старые WAV из кэша.
 */
object SoundSynth {
    const val RATE = 22050
    const val VERSION = 3

    private const val SINE = 0
    private const val SQUARE = 1
    private const val TRI = 2
    private const val NOISE = 3

    private class Buf(seconds: Float) {
        val a = FloatArray((seconds * RATE).toInt().coerceAtLeast(1))
    }

    private val rng = Rng(77)

    /**
     * Добавить тон: частота f0→f1 (экспоненциально), огибающая — атака + степенной спад.
     * lp — коэффициент однополюсного ФНЧ для шума (1 — без фильтра).
     */
    private fun tone(b: Buf, start: Float, dur: Float, f0: Float, f1: Float, wave: Int, vol: Float,
                     attack: Float = 0.004f, pow: Float = 2f, lp: Float = 1f, vibHz: Float = 0f, vibDepth: Float = 0f) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var phase = 0.0
        var flt = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= b.a.size) break
            val t = i.toFloat() / n
            val f = f0 * Math.pow((f1 / f0).toDouble(), t.toDouble()).toFloat() *
                (1f + vibDepth * sin(2.0 * PI * vibHz * i / RATE).toFloat())
            phase += f / RATE
            val ph = (phase - Math.floor(phase)).toFloat()
            var v = when (wave) {
                SINE -> sin(2.0 * PI * ph).toFloat()
                SQUARE -> if (ph < 0.5f) 0.8f else -0.8f
                TRI -> 4f * abs(ph - 0.5f) - 1f
                else -> rng.nextFloat() * 2f - 1f
            }
            if (lp < 1f) { flt += (v - flt) * lp; v = flt * 1.6f }
            val at = i.toFloat() / RATE
            val env = (if (at < attack) at / attack else 1f) * Math.pow((1f - t).toDouble(), pow.toDouble()).toFloat()
            b.a[idx] += v * vol * env
        }
    }

    fun render(id: Int): ShortArray {
        val b: Buf
        when (id) {
            Sfx.HIT -> { b = Buf(0.08f); tone(b, 0f, 0.05f, 1f, 1f, NOISE, 0.5f, lp = 0.35f); tone(b, 0f, 0.07f, 240f, 110f, SINE, 0.55f) }
            Sfx.KILL -> { b = Buf(0.12f); tone(b, 0f, 0.1f, 560f, 170f, SINE, 0.6f); tone(b, 0f, 0.035f, 1f, 1f, NOISE, 0.25f, lp = 0.5f) }
            Sfx.XP -> { b = Buf(0.08f); tone(b, 0f, 0.07f, 1250f, 1750f, TRI, 0.38f, pow = 1.5f) }
            Sfx.COIN -> { b = Buf(0.2f); tone(b, 0f, 0.055f, 1320f, 1320f, SQUARE, 0.2f, pow = 0.6f); tone(b, 0.05f, 0.14f, 1760f, 1760f, SQUARE, 0.2f) }
            Sfx.LEVEL_UP -> {
                b = Buf(0.5f)
                val notes = floatArrayOf(523f, 659f, 784f, 1046f)
                for (i in notes.indices) tone(b, i * 0.07f, 0.2f, notes[i], notes[i], TRI, 0.42f, pow = 1.4f)
            }
            Sfx.HURT -> { b = Buf(0.2f); tone(b, 0f, 0.17f, 210f, 90f, SQUARE, 0.32f); tone(b, 0f, 0.09f, 1f, 1f, NOISE, 0.32f, lp = 0.25f) }
            Sfx.CLICK -> { b = Buf(0.05f); tone(b, 0f, 0.035f, 900f, 760f, SINE, 0.45f); tone(b, 0f, 0.006f, 1f, 1f, NOISE, 0.3f) }
            Sfx.DEATH -> { b = Buf(0.9f); tone(b, 0f, 0.85f, 330f, 55f, TRI, 0.55f, pow = 1.2f, vibHz = 9f, vibDepth = 0.06f); tone(b, 0f, 0.3f, 1f, 1f, NOISE, 0.35f, lp = 0.12f) }
            Sfx.BOOM -> { b = Buf(0.5f); tone(b, 0f, 0.45f, 1f, 1f, NOISE, 0.95f, lp = 0.07f, pow = 2.2f); tone(b, 0f, 0.35f, 95f, 38f, SINE, 0.7f) }
            Sfx.ZAP -> { b = Buf(0.12f); tone(b, 0f, 0.09f, 1900f, 600f, SQUARE, 0.22f); tone(b, 0f, 0.06f, 1f, 1f, NOISE, 0.3f) }
            Sfx.CHEST -> {
                b = Buf(0.6f)
                val notes = floatArrayOf(784f, 988f, 1175f, 1568f)
                for (i in notes.indices) tone(b, i * 0.06f, 0.3f, notes[i], notes[i], SINE, 0.32f, pow = 1.3f)
                tone(b, 0.2f, 0.35f, 3136f, 3136f, SINE, 0.08f, vibHz = 14f, vibDepth = 0.02f)
            }
            Sfx.WAVE -> { b = Buf(0.7f); tone(b, 0f, 0.62f, 220f, 220f, SQUARE, 0.2f, attack = 0.05f, pow = 1f, vibHz = 5f, vibDepth = 0.015f); tone(b, 0.02f, 0.6f, 330f, 330f, TRI, 0.25f, attack = 0.05f, pow = 1f) }
            Sfx.BUY -> { b = Buf(0.3f); tone(b, 0f, 0.07f, 988f, 988f, SQUARE, 0.22f, pow = 0.7f); tone(b, 0.06f, 0.2f, 1319f, 1319f, SQUARE, 0.22f); tone(b, 0.06f, 0.2f, 1976f, 1976f, SINE, 0.12f) }
            Sfx.THROW -> { b = Buf(0.09f); tone(b, 0f, 0.08f, 1f, 1f, NOISE, 0.2f, attack = 0.02f, lp = 0.18f, pow = 1.2f) }
            Sfx.VICTORY -> {
                b = Buf(1.4f)
                val notes = floatArrayOf(523f, 659f, 784f, 1046f, 784f, 1046f)
                val lens = floatArrayOf(0.13f, 0.13f, 0.13f, 0.2f, 0.13f, 0.6f)
                var t = 0f
                for (i in notes.indices) { tone(b, t, lens[i] + 0.1f, notes[i], notes[i], TRI, 0.42f, pow = 1.1f); t += lens[i] }
            }
            Sfx.DASH -> { b = Buf(0.16f); tone(b, 0f, 0.15f, 1f, 1f, NOISE, 0.42f, attack = 0.015f, lp = 0.28f, pow = 1.1f); tone(b, 0f, 0.14f, 420f, 1100f, SINE, 0.16f, pow = 1.6f) }
            Sfx.TELEGRAPH -> { b = Buf(0.3f); tone(b, 0f, 0.09f, 740f, 740f, SQUARE, 0.2f, pow = 0.8f); tone(b, 0.12f, 0.12f, 880f, 880f, SQUARE, 0.22f, pow = 0.8f) }
            Sfx.SHOT -> { b = Buf(0.12f); tone(b, 0f, 0.1f, 1400f, 360f, TRI, 0.3f, pow = 1.4f); tone(b, 0f, 0.05f, 1f, 1f, NOISE, 0.18f, lp = 0.4f) }
            Sfx.ROAR -> { b = Buf(0.9f); tone(b, 0f, 0.85f, 120f, 62f, SQUARE, 0.4f, attack = 0.05f, pow = 1.1f, vibHz = 26f, vibDepth = 0.2f); tone(b, 0f, 0.7f, 1f, 1f, NOISE, 0.4f, lp = 0.08f, pow = 1.4f); tone(b, 0.05f, 0.7f, 70f, 40f, SINE, 0.5f) }
            Sfx.PERFECT -> { b = Buf(0.45f); tone(b, 0f, 0.18f, 880f, 1760f, SINE, 0.35f, pow = 1.2f); tone(b, 0.07f, 0.3f, 1320f, 2640f, TRI, 0.3f, pow = 1.4f); tone(b, 0.1f, 0.3f, 3520f, 3520f, SINE, 0.07f, vibHz = 16f, vibDepth = 0.03f) }
            Sfx.SHIELD -> { b = Buf(0.22f); tone(b, 0f, 0.2f, 1800f, 700f, SQUARE, 0.25f, pow = 1.8f); tone(b, 0f, 0.05f, 1f, 1f, NOISE, 0.3f, lp = 0.6f) }
            Sfx.PHASE -> { b = Buf(0.7f); tone(b, 0f, 0.6f, 160f, 340f, SQUARE, 0.28f, attack = 0.03f, pow = 0.9f); tone(b, 0.12f, 0.5f, 240f, 480f, TRI, 0.28f, pow = 1f); tone(b, 0f, 0.5f, 1f, 1f, NOISE, 0.25f, lp = 0.1f) }
            Sfx.CONDUCT -> { b = Buf(0.3f); tone(b, 0f, 0.16f, 2600f, 500f, SQUARE, 0.2f, pow = 1.4f); tone(b, 0.05f, 0.2f, 1700f, 380f, SQUARE, 0.18f, pow = 1.6f); tone(b, 0f, 0.14f, 1f, 1f, NOISE, 0.22f, lp = 0.7f) }
            Sfx.FREEZE -> { b = Buf(0.4f); tone(b, 0f, 0.3f, 3400f, 2100f, SINE, 0.25f, pow = 1.1f, vibHz = 38f, vibDepth = 0.04f); tone(b, 0.04f, 0.25f, 5100f, 3300f, SINE, 0.18f, pow = 1.4f); tone(b, 0f, 0.06f, 1f, 1f, NOISE, 0.2f, lp = 0.9f) }
            Sfx.STEAM -> { b = Buf(0.5f); tone(b, 0f, 0.45f, 1f, 1f, NOISE, 0.32f, attack = 0.05f, pow = 1.2f, lp = 0.25f); tone(b, 0f, 0.3f, 420f, 260f, TRI, 0.12f, pow = 1.5f) }
            Sfx.SHATTER -> { b = Buf(0.35f); tone(b, 0f, 0.05f, 1f, 1f, NOISE, 0.4f, lp = 0.95f); tone(b, 0f, 0.25f, 2600f, 900f, SQUARE, 0.2f, pow = 2f); tone(b, 0.03f, 0.22f, 4200f, 1500f, TRI, 0.18f, pow = 2.2f); tone(b, 0f, 0.2f, 140f, 60f, SINE, 0.3f, pow = 1.4f) }
            Sfx.SPLASH -> { b = Buf(0.3f); tone(b, 0f, 0.22f, 1f, 1f, NOISE, 0.28f, attack = 0.02f, pow = 1.6f, lp = 0.4f); tone(b, 0f, 0.18f, 500f, 900f, SINE, 0.16f, pow = 1.8f, vibHz = 30f, vibDepth = 0.1f) }
            Sfx.CRY -> { b = Buf(0.55f); tone(b, 0f, 0.22f, 700f, 1250f, SINE, 0.3f, pow = 1.2f, vibHz = 11f, vibDepth = 0.05f); tone(b, 0.2f, 0.3f, 1250f, 820f, SINE, 0.26f, pow = 1.5f, vibHz = 9f, vibDepth = 0.06f) }
            Sfx.JOIN -> { b = Buf(0.75f); tone(b, 0f, 0.16f, 784f, 784f, TRI, 0.3f, pow = 1.1f); tone(b, 0.12f, 0.16f, 988f, 988f, TRI, 0.3f, pow = 1.1f); tone(b, 0.24f, 0.4f, 1319f, 1319f, SINE, 0.34f, pow = 1.3f); tone(b, 0.3f, 0.4f, 1976f, 1976f, SINE, 0.16f, pow = 1.6f) }
            else -> b = Buf(0.01f)
        }
        // мягкое ограничение и нормализация
        var peak = 0.0001f
        for (i in b.a.indices) {
            val x = b.a[i]
            val y = x / (1f + abs(x) * 0.6f)
            b.a[i] = y
            if (abs(y) > peak) peak = abs(y)
        }
        val k = (0.9f / peak).coerceAtMost(1.6f)
        return ShortArray(b.a.size) { (b.a[it] * k * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }

    fun wav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII)); bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII)); bb.putInt(16)
        bb.putShort(1); bb.putShort(1); bb.putInt(RATE); bb.putInt(RATE * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray(Charsets.US_ASCII)); bb.putInt(dataLen)
        for (s in pcm) bb.putShort(s)
        return bb.array()
    }
}
