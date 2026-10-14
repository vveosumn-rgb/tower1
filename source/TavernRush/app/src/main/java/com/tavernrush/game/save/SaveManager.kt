package com.tavernrush.game.save

import com.tavernrush.game.meta.Profile
import java.io.File
import java.io.FileOutputStream

/** Хранилище профиля (в тестах — в памяти или во временной папке). */
interface ProfileStore {
    fun load(): Profile
    fun save(p: Profile): Boolean
    val status: SaveStatus
}

enum class SaveStatus {
    /** Сохранений не было — новый профиль. */
    NEW,
    OK,
    /** Основной файл испорчен/отсутствует — восстановлено из резервной копии. */
    RESTORED,
    /** Все копии испорчены — начат новый профиль (испорченные файлы отложены в *.corrupt). */
    RESET,
}

/**
 * Надёжная запись: profile.sav.tmp (+fsync) → старый profile.sav становится .bak → tmp переименовывается в main.
 * При загрузке выбирается валидная копия с наибольшим seq среди main/tmp/bak; испорченный main
 * откладывается в .corrupt (не удаляется — можно разобрать баг).
 */
class SaveManager(private val dir: File) : ProfileStore {
    private val main = File(dir, "profile.sav")
    private val tmp = File(dir, "profile.sav.tmp")
    private val bak = File(dir, "profile.sav.bak")

    override var status = SaveStatus.NEW
        private set
    var lastError: String? = null
        private set

    @Synchronized
    override fun load(): Profile {
        dir.mkdirs()
        val anyFile = main.exists() || tmp.exists() || bak.exists()
        val pMain = read(main)
        val pTmp = read(tmp)
        val pBak = read(bak)
        var best: Profile? = null
        for (c in arrayOf(pMain, pTmp, pBak)) if (c != null && (best == null || c.seq > best.seq)) best = c
        if (main.exists() && pMain == null) quarantine(main)
        status = when {
            !anyFile -> SaveStatus.NEW
            best == null -> SaveStatus.RESET
            best === pMain -> SaveStatus.OK
            else -> SaveStatus.RESTORED
        }
        if (best == null) {
            if (tmp.exists()) quarantine(tmp)
            if (bak.exists()) quarantine(bak)
            return Profile()
        }
        if (status == SaveStatus.RESTORED) {
            // При сбое восстановления tmp может быть единственной свежей валидной копией.
            // Удаляем её только после успешного переноса в main.
            if (save(best)) tmp.delete()
        } else {
            tmp.delete()
        }
        return best
    }

    private fun read(f: File): Profile? {
        if (!f.exists()) return null
        if (f.length() > 262144L) { lastError = f.name + ": слишком большой файл"; return null }
        return try {
            SaveCodec.decode(f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            lastError = f.name + ": " + e.message
            null
        }
    }

    private fun quarantine(f: File) {
        var n = 0
        var q: File
        do { q = File(dir, f.name + ".corrupt" + (if (n == 0) "" else "." + n)); n++ } while (q.exists() && n < 20)
        if (!f.renameTo(q)) f.delete()
    }

    @Synchronized
    override fun save(p: Profile): Boolean {
        val previousSeq = p.seq
        return try {
            if (p.seq == Long.MAX_VALUE) throw SaveCodec.Invalid("переполнение версии записи")
            dir.mkdirs()
            p.seq++
            val encoded = SaveCodec.encode(p)
            SaveCodec.decode(encoded)
            val data = encoded.toByteArray(Charsets.UTF_8)
            FileOutputStream(tmp).use { out ->
                out.write(data)
                out.flush()
                out.fd.sync()
            }
            if (main.exists()) {
                bak.delete()
                if (!main.renameTo(bak)) main.copyTo(bak, overwrite = true)
            }
            if (!tmp.renameTo(main)) {
                tmp.copyTo(main, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: Exception) {
            p.seq = previousSeq
            lastError = e.message
            false
        }
    }
}

/** Хранилище в памяти (тесты, режим без диска). */
class MemoryStore(private var text: String? = null) : ProfileStore {
    override var status = SaveStatus.NEW
        private set
    var saves = 0
        private set

    override fun load(): Profile {
        val t = text ?: return Profile().also { status = SaveStatus.NEW }
        return try {
            SaveCodec.decode(t).also { status = SaveStatus.OK }
        } catch (e: Exception) {
            status = SaveStatus.RESET
            Profile()
        }
    }

    override fun save(p: Profile): Boolean {
        p.seq++
        text = SaveCodec.encode(p)
        saves++
        return true
    }

    fun raw(): String? = text
}
