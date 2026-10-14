package com.tavernrush.game.render.pixel

import android.graphics.Bitmap
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.CreatureLook
import com.tavernrush.game.content.EnemyLook
import com.tavernrush.game.render.pixel.models.PropModels
import com.tavernrush.game.systems.World
import kotlin.math.PI

/** Спрайт: битмап и точка «земля под ногами» (ox, oy) внутри него. */
class Spr(val bmp: Bitmap, val ox: Int, val oy: Int) {
    val w: Int get() = bmp.width
    val h: Int get() = bmp.height
}

/**
 * Запечённая анимация: frames[dir][кадр], dir 0 — лицом вправо, 1 — влево (геометрия зеркалится,
 * свет остаётся сверху-слева). drop — на сколько пикселей ноги ниже центра хитбокса.
 */
class Anim(val frames: Array<Array<Spr>>, val shadows: Array<Array<Spr>>, val drop: Float) {
    val count: Int get() = frames[0].size
    fun frame(dir: Int, i: Int): Spr = frames[dir][((i % count) + count) % count]
    fun shadow(dir: Int, i: Int): Spr = shadows[dir][((i % count) + count) % count]
}

/**
 * Все запечённые спрайты игры. Не зависят от размера экрана (пиксель = пиксель), поэтому
 * пекутся один раз на процесс; тяжёлое — лениво и потокобезопасно.
 */
object SpriteBank {
    const val HEADINGS = 16

    private fun bmp(b: Baked): Bitmap = Bitmap.createBitmap(b.pixels, b.w, b.h, Bitmap.Config.ARGB_8888)
    private fun spr(b: Baked) = Spr(bmp(b), b.ox, b.oy)

    fun bake(model: Model, clip: String?, scale: Float = model.scale, opts: BakeOpts = BakeOpts()): Anim {
        val r = ModelBaker.bake(model, clip, false, scale, opts)
        val l = ModelBaker.bake(model, clip, true, scale, opts)
        return Anim(
            arrayOf(Array(r.frames.size) { spr(r.frames[it]) }, Array(l.frames.size) { spr(l.frames[it]) }),
            arrayOf(Array(r.shadows.size) { spr(r.shadows[it]) }, Array(l.shadows.size) { spr(l.shadows[it]) }),
            model.footDrop * scale / model.scale,
        )
    }

    /** Модель в N направлениях (снаряды): frames[0][направление]. */
    fun bakeHeadings(model: Model, n: Int = HEADINGS, scale: Float = model.scale): Anim {
        val fr = ArrayList<Spr>(); val sh = ArrayList<Spr>()
        for (i in 0 until n) {
            val a = (2.0 * PI * i / n).toFloat()
            val s = ModelBaker.bake(model, null, false, scale, BakeOpts(spin = a))
            fr.add(spr(s.frames[0])); sh.add(spr(s.shadows[0]))
        }
        val f = fr.toTypedArray(); val s = sh.toTypedArray()
        return Anim(arrayOf(f, f), arrayOf(s, s), model.footDrop * scale / model.scale)
    }

    // ------------------------------------------------------------ персонажи

    val heroWalk: Array<Anim> by lazy { Array(HEROES) { bake(ModelLibrary.hero(it), "walk") } }
    val heroIdle: Array<Anim> by lazy { Array(HEROES) { bake(ModelLibrary.hero(it), "idle") } }

    private val enemyCache = HashMap<String, Anim>()

    private fun cached(key: String, make: () -> Anim): Anim = enemyCache.getOrPut(key) { make() }

    /** Анимация вида врага по клипу (walk по умолчанию; attack — у боссов). null, если клипа нет. */
    @Synchronized fun enemy(look: EnemyLook, clip: String = "walk"): Anim {
        val m = ModelLibrary.enemy(look)
        val c = if (m.clip(clip) != null) clip else "walk"
        return cached("n:${look.ordinal}:$c") { bake(m, c) }
    }

    /** Друг-существо: walk, idle или act (приём, прыжок). */
    @Synchronized fun creature(look: CreatureLook, clip: String = "walk", age:Int=2, form:Int=0): Anim {
        require(age in 0..2 && form in 0..2)
        val c=if(clip=="walk"||clip=="idle"||clip=="act")clip else "walk"
        return cached("c:${look.ordinal}:$c:$age:$form") { bake(CreatureForms.model(look,age,form),c) }
    }

    @Synchronized fun hasClip(look: EnemyLook, clip: String): Boolean = ModelLibrary.enemy(look).clip(clip) != null

    /** Элита: крупнее ×1.45 и золотой контур (отличима от обычного врага по силуэту, а не только по свечению). */
    @Synchronized fun elite(look: EnemyLook, sizeMul: Float): Anim = cached("e:${look.ordinal}") {
        val m = ModelLibrary.enemy(look)
        bake(m, "walk", m.scale * sizeMul, BakeOpts(outline = 0xFFE8B23A.toInt()))
    }

    /** Немезида: ×1.6 и багровый контур. */
    @Synchronized fun named(look: EnemyLook, sizeMul: Float): Anim = cached("m:${look.ordinal}") {
        val m = ModelLibrary.enemy(look)
        bake(m, "walk", m.scale * sizeMul, BakeOpts(outline = 0xFFD63A4A.toInt()))
    }

    // ------------------------------------------------------------ лут

    val gems: Array<Anim> by lazy {
        arrayOf(
            bake(PropModels.gem(0xFF57C7FF.toInt(), 5f), "walk"),
            bake(PropModels.gem(0xFF63E07A.toInt(), 6f), "walk"),
            bake(PropModels.gem(0xFFC77DFF.toInt(), 7f), "walk"),
            bake(PropModels.gem(0xFFFF5D73.toInt(), 8.5f), "walk"),
        )
    }
    val coin: Anim by lazy { bake(PropModels.coin(), "walk") }
    val coinBig: Anim by lazy { bake(PropModels.coin(), "walk", 1.45f) }
    val chicken: Anim by lazy { bake(PropModels.chicken(), "walk") }
    val magnet: Anim by lazy { bake(PropModels.magnet(), "walk") }
    val chest: Anim by lazy { bake(PropModels.chest(false), "walk") }

    // ------------------------------------------------------------ снаряды

    val knife: Anim by lazy { bakeHeadings(PropModels.knife()) }
    val frost: Anim by lazy { bakeHeadings(PropModels.frostBolt()) }
    val pan: Anim by lazy { bake(PropModels.pan(), "spin") }
    val barrel: Anim by lazy { bake(PropModels.barrel(), "spin") }
    val mug: Anim by lazy { bake(PropModels.mug(), "spin") }
    val raven: Anim by lazy { bake(PropModels.raven(), "walk") }
    val trapOpen: Anim by lazy { bake(PropModels.trap(false), null) }
    val trapClosed: Anim by lazy { bake(PropModels.trap(true), null) }

    // ------------------------------------------------------------ реквизит

    class Prop(val body: Spr, val shadow: Spr, val canopy: Spr?, val canopyShadow: Spr?)

    private fun prop(m: Model): Prop {
        val s = ModelBaker.bake(m, null, false, m.scale, BakeOpts())
        return Prop(spr(s.frames[0]), spr(s.shadows[0]), null, null)
    }

    val props: Array<Prop> by lazy {
        arrayOf(
            prop(PropModels.barrelProp()), prop(PropModels.crate()), prop(PropModels.haystack()),
            prop(PropModels.lanternPost()), prop(PropModels.rock()), prop(PropModels.mushroom()),
            prop(PropModels.bush()), prop(PropModels.fence()),
        )
    }
    const val P_BARREL = 0
    const val P_CRATE = 1
    const val P_HAY = 2
    const val P_LANTERN = 3
    const val P_ROCK = 4
    const val P_MUSHROOM = 5
    const val P_BUSH = 6
    const val P_FENCE = 7

    val treeTrunk: Prop by lazy { prop(PropModels.treeTrunk()) }
    val treeCanopy: Prop by lazy { prop(PropModels.treeCanopy()) }

    const val HEROES = 4

    private var warming = false

    /** Запускает прогрев на фоновом потоке, один раз за процесс. */
    @Synchronized fun warmUpAsync() {
        if (warming) return
        warming = true
        Thread({ warmUp() }, "sprite-warmup").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** Прогрев: первый кадр забега и первое появление элиты или немезиды не должны ждать запекания. */
    fun warmUp() {
        heroWalk; heroIdle
        for (l in EnemyLook.entries) { enemy(l); if (hasClip(l, "attack")) enemy(l, "attack") }
        // у боссов нет элитных и именных вариантов
        val regular = Content.enemies.filter { !it.boss }.map { it.look }.toSet()
        for (l in regular) { elite(l, World.ELITE_SIZE); named(l, World.NAMED_SIZE) }
        for (l in CreatureLook.entries) { creature(l); creature(l, "idle"); creature(l, "act") }
        gems; coin; coinBig; chicken; magnet; chest
        knife; frost; pan; barrel; mug; raven; trapOpen; trapClosed
        props; treeTrunk; treeCanopy
    }
}
