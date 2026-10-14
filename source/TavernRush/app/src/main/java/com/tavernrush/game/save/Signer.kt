package com.tavernrush.game.save

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 подпись сохранений. Ключ собирается из двух половин при первом обращении
 * (не лежит строкой в DEX). Это защита от ручной правки файла, а не криптостойкая тайна:
 * оффлайн-игра не может спрятать ключ полностью (R03 добавит привязку к установке).
 */
internal object Signer {
    private val keyA = intArrayOf(0x54, 0x52, 0x2d, 0x73, 0x61, 0x76, 0x65, 0x2d, 0x6b, 0x33, 0x79, 0x2d)
    private val keyB = intArrayOf(0x1f, 0x7a, 0x33, 0x52, 0x0c, 0x44, 0x6e, 0x21, 0x58, 0x09, 0x63, 0x3d, 0x2e, 0x71, 0x15, 0x48)

    private val key: ByteArray by lazy {
        ByteArray(keyA.size + keyB.size) { i ->
            if (i < keyA.size) keyA[i].toByte() else (keyB[i - keyA.size] xor 0x2a xor (i * 7)).toByte()
        }
    }

    fun sign(text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val out = mac.doFinal(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(out.size * 2)
        for (b in out) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 15])
        }
        return sb.toString()
    }

    /** Сравнение за постоянное время. */
    fun verify(text: String, sig: String): Boolean {
        val good = sign(text)
        if (good.length != sig.length) return false
        var diff = 0
        for (i in good.indices) diff = diff or (good[i].code xor sig[i].code)
        return diff == 0
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
