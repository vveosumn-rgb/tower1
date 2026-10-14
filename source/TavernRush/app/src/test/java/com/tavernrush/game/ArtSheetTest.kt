package com.tavernrush.game

import com.tavernrush.game.render.pixel.Baked
import com.tavernrush.game.render.pixel.BakeOpts
import com.tavernrush.game.render.pixel.Model
import com.tavernrush.game.render.pixel.ModelBaker
import com.tavernrush.game.render.pixel.models.BossModels
import com.tavernrush.game.render.pixel.models.EnemyModels
import com.tavernrush.game.render.pixel.models.PropModels
import com.tavernrush.game.render.pixel.models.HeroModels
import org.junit.Test
import java.io.File

/** Листы спрайтов для глаз: все модели, кадры ходьбы, оба направления, на «траве» ×4. Обычный JUnit, без Robolectric. */
class ArtSheetTest {
    private val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private class Img(val width: Int, val height: Int) {
        val px = IntArray(width * height)
        fun getRGB(x: Int, y: Int) = px[y * width + x]
        fun setRGB(x: Int, y: Int, c: Int) { px[y * width + x] = c }
    }

    private fun draw(img: Img, b: Baked, x: Int, y: Int, zoom: Int) {
        for (py in 0 until b.h) for (px in 0 until b.w) {
            val c = b.pixels[py * b.w + px]
            val a = c ushr 24
            if (a == 0) continue
            val fg = if (a == 255) c else blend(img, x + px * zoom, y + py * zoom, c)
            for (zy in 0 until zoom) for (zx in 0 until zoom) {
                val xx = x + px * zoom + zx; val yy = y + py * zoom + zy
                if (xx in 0 until img.width && yy in 0 until img.height) img.setRGB(xx, yy, fg)
            }
        }
    }

    private fun blend(img: Img, x: Int, y: Int, c: Int): Int {
        if (x !in 0 until img.width || y !in 0 until img.height) return c or (0xFF shl 24)
        val d = img.getRGB(x, y)
        val a = (c ushr 24) / 255f
        fun ch(s: Int) = (((c shr s) and 255) * a + ((d shr s) and 255) * (1 - a)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun clipOf(m: Model, clip: String): String? = if (m.clip(clip) != null) clip else null

    private fun sheet(name: String, models: List<Model>, zoom: Int = 4, clip: String = "walk", bg: Int = 0xFF3F6B3A.toInt()) {
        val cellW = models.maxOf { m ->
            val s = ModelBaker.bake(m, clipOf(m, clip), false, opts = BakeOpts())
            s.frames[0].w
        } + 14
        val frames = 6
        val cellH = models.maxOf { m -> ModelBaker.bake(m, clipOf(m, clip), false).frames[0].h } + 10
        val img = Img(cellW * zoom * frames + 8, (cellH * zoom + 6) * models.size + 6)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, bg)
        for ((row, m) in models.withIndex()) {
            val r = ModelBaker.bake(m, clipOf(m, clip), false)
            val l = ModelBaker.bake(m, clipOf(m, clip), true)
            for (f in 0 until minOf(frames, r.frames.size)) {
                val set = if (f < 3) r else l
                val fr = (if (f < 3) f * 2 else (f - 3) * 2).coerceAtMost(set.frames.size - 1)
                val x0 = 4 + f * cellW * zoom
                val y0 = 4 + row * (cellH * zoom + 6)
                val sh = set.shadows[fr]
                draw(img, sh, x0 + (cellW / 2 - sh.ox) * zoom, y0 + (cellH - 6 - sh.oy) * zoom, zoom)
                val b = set.frames[fr]
                draw(img, b, x0 + (cellW / 2 - b.ox) * zoom, y0 + (cellH - 6 - b.oy) * zoom, zoom)
            }
        }
        PngWriter.write(File(dir, "art_$name.png"), img.width, img.height, img.px)
    }

    @Test
    fun heroes() = sheet("heroes", listOf(HeroModels.innkeeper(), HeroModels.guardian(), HeroModels.sage(), HeroModels.ranger()), clip = "walk")

    @Test
    fun enemies() = sheet("enemies", listOf(EnemyModels.rat(), EnemyModels.goblin(), EnemyModels.bat(), EnemyModels.slime(), EnemyModels.orc()), clip = "walk")

    @Test
    fun enemies2() = sheet("enemies2", listOf(EnemyModels.archer(), EnemyModels.boar(), EnemyModels.bomber(), EnemyModels.shaman(), EnemyModels.ghost(), EnemyModels.spider(), EnemyModels.slime(0.92f, 4f, "slimelet")), clip = "walk")

    @Test
    fun bosses() = sheet("bosses", listOf(BossModels.ratKing(), BossModels.ogreChef(), BossModels.banshee()), zoom = 3, clip = "walk")

    @Test
    fun bossesAttack() = sheet("bosses_attack", listOf(BossModels.ratKing(), BossModels.ogreChef(), BossModels.banshee()), zoom = 3, clip = "attack")

    @Test
    fun creatures() = sheet("creatures", listOf(com.tavernrush.game.render.pixel.models.CreatureModels.stream(), com.tavernrush.game.render.pixel.models.CreatureModels.lynx(), com.tavernrush.game.render.pixel.models.CreatureModels.horn()), zoom = 5, clip = "walk")

    @Test
    fun creaturesAct() = sheet("creatures_act", listOf(com.tavernrush.game.render.pixel.models.CreatureModels.stream(), com.tavernrush.game.render.pixel.models.CreatureModels.lynx(), com.tavernrush.game.render.pixel.models.CreatureModels.horn()), zoom = 5, clip = "act")

    @Test
    fun props() = sheet("props", listOf(PropModels.chest(false), PropModels.treeCanopy(), PropModels.lanternPost(), PropModels.barrelProp()), zoom = 3, clip = "none")
}
