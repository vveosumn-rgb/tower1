package com.tavernrush.game

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Минимальный PNG-писатель для тестов (в Android-classpath нет javax.imageio). */
object PngWriter {
    fun write(file: File, w: Int, h: Int, argb: IntArray) {
        val raw = ByteArrayOutputStream(w * h * 4 + h)
        for (y in 0 until h) {
            raw.write(0)
            for (x in 0 until w) {
                val c = argb[y * w + x]
                raw.write((c shr 16) and 255); raw.write((c shr 8) and 255); raw.write(c and 255); raw.write((c ushr 24) and 255)
            }
        }
        val def = Deflater(6)
        def.setInput(raw.toByteArray()); def.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        while (!def.finished()) { val n = def.deflate(buf); z.write(buf, 0, n) }
        val out = DataOutputStream(file.outputStream().buffered())
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0) }
        chunk(out, "IHDR", ihdr.toByteArray())
        chunk(out, "IDAT", z.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        out.close()
    }

    private fun chunk(out: DataOutputStream, type: String, data: ByteArray) {
        out.writeInt(data.size)
        val t = type.toByteArray(Charsets.US_ASCII)
        out.write(t); out.write(data)
        val crc = CRC32(); crc.update(t); crc.update(data)
        out.writeInt(crc.value.toInt())
    }
}
