package com.tavernrush.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.tavernrush.game.content.EnemyBehavior
import com.tavernrush.game.content.Content
import com.tavernrush.game.content.IconId
import com.tavernrush.game.content.Reactions
import com.tavernrush.game.content.WeaponKind
import com.tavernrush.game.core.M
import com.tavernrush.game.core.Rng
import com.tavernrush.game.core.TWO_PI
import com.tavernrush.game.entities.Enemy
import com.tavernrush.game.entities.EnemyShot
import com.tavernrush.game.entities.Hazard
import com.tavernrush.game.entities.FloatText
import com.tavernrush.game.entities.Particle
import com.tavernrush.game.entities.Pickup
import com.tavernrush.game.entities.Projectile
import com.tavernrush.game.render.pixel.Anim
import com.tavernrush.game.render.pixel.Biomes
import com.tavernrush.game.render.pixel.Gfx
import com.tavernrush.game.render.pixel.GfxConfig
import com.tavernrush.game.render.pixel.GfxGovernor
import com.tavernrush.game.render.pixel.GfxPreset
import com.tavernrush.game.render.pixel.GroundLayer
import com.tavernrush.game.render.pixel.PixelAssets
import com.tavernrush.game.render.pixel.PixelFont
import com.tavernrush.game.render.pixel.Postfx
import com.tavernrush.game.render.pixel.Spr
import com.tavernrush.game.render.pixel.SpriteBank
import com.tavernrush.game.systems.BossSystem
import com.tavernrush.game.systems.EncounterSystem
import com.tavernrush.game.systems.EnemyAI
import com.tavernrush.game.systems.World
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Пиксельный 2.5D-рендерер мира. Кадр рисуется в маленький буфер (≈360 пикселей по ширине), где
 * 1 пиксель = 2 единицы мира, и выводится на экран целым масштабом без сглаживания: каждый
 * «пиксель» арта — квадрат k×k пикселей экрана. Глубина — из настоящего объёма запечённых моделей,
 * проекционных теней, сортировки по Y, высоты частиц и снарядов, карты света и свечения.
 * Только чтение World; без аллокаций в кадре (кэши Paint/Rect/Path, спрайты запечены заранее).
 */
class WorldRenderer {
    var w = 0
        private set
    var h = 0
        private set
    /** Пикселей экрана на пиксель арта. */
    var k = 3
        private set
    var vw = 360
        private set
    var vh = 780
        private set
    /** Пикселей экрана на единицу мира (используется HUD). */
    val scale: Float get() = k * PXU

    var cfg: GfxConfig = Gfx.HIGH
        private set
    private var userPreset = GfxPreset.AUTO
    val governor = GfxGovernor()
    /** Время последнего кадра, мс: рисование в буфер (работа CPU) и подача на экран (на устройстве — работа GPU). */
    var lastBufferMs = 0f
        private set
    var lastPresentMs = 0f
        private set

    private var buf: Bitmap? = null
    private var bc: Canvas? = null
    private var vignette: Bitmap? = null
    private val post = Postfx()
    private val ground = GroundLayer(Biomes.MEADOW)
    private val regionGrounds=arrayOfNulls<GroundLayer>(6)
    private val font = PixelFont()
    private val rng = Rng(4242)

    private val fp = Paint()
    private val sp = Paint().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER }
    private val bp = Paint()
    private val alphaPaint = Paint()
    private val flashPaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val dashPaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFF7FD3FF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val slowPaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFF8CE9FF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val wetPaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFF3FA9FF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val burnPaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFFFF7A2E.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val icePaint = Paint().apply { colorFilter = PorterDuffColorFilter(0xFFBDEBFF.toInt(), PorterDuff.Mode.SRC_ATOP) }
    private val labelTx = Txt()
    private val wallArt=WallArt()
    private val conduitArt=ConduitArt()
    private val wallBounds=FloatArray(4)
    private val addPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val glowPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val filters = HashMap<Int, PorterDuffColorFilter>()
    private val pixelGlows = HashMap<Int, Bitmap>()
    private val dst = RectF()
    private val idst = Rect()
    private val path = Path()
    private val overlayPaint = Paint().apply { isFilterBitmap = true }

    // кадр
    private var camPxX = 0
    private var camPxY = 0
    private var offX = 0
    private var offY = 0
    private var shX = 0
    private var shY = 0
    private var animT = 0f

    // сортировка по глубине (подсчётом)
    private val sKind = IntArray(SORT_CAP)
    private val sIdx = IntArray(SORT_CAP)
    private val sKey = IntArray(SORT_CAP)
    private var sCount = 0
    private val bucket = IntArray(BUCKETS + 1)
    private val oKind = IntArray(SORT_CAP)
    private val oIdx = IntArray(SORT_CAP)

    // реквизит арены (пересчитывается каждый кадр из хэша клеток)
    private val propKind = IntArray(PROP_CAP)
    private val propX = IntArray(PROP_CAP)
    private val propY = IntArray(PROP_CAP)
    private var propCount = 0

    // атмосферные пылинки/светлячки
    private val moteU = FloatArray(MOTE_CAP) { M.hash01(it, 1, 9) }
    private val moteV = FloatArray(MOTE_CAP) { M.hash01(it, 2, 9) }
    private val moteP = FloatArray(MOTE_CAP) { M.hash01(it, 3, 9) * 6.28f }

    val spriteBank: SpriteBank get() = SpriteBank

    fun resize(width: Int, height: Int) {
        if (width == w && height == h && buf != null) return
        w = width; h = height
        var kk = ceil(w / 380f).toInt().coerceAtLeast(1)
        var vhh = ceil(h / kk.toFloat()).toInt()
        while (vhh > 880) { kk++; vhh = ceil(h / kk.toFloat()).toInt() }
        k = kk
        vw = ceil(w / k.toFloat()).toInt()
        vh = ceil(h / k.toFloat()).toInt()
        buf?.recycle()
        val b = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        buf = b; bc = Canvas(b)
        post.resize(vw, vh)
        vignette?.recycle()
        vignette = makeVignette()
        fp.isAntiAlias = false
        applyPreset()
    }

    fun setPreset(p: GfxPreset) {
        userPreset = p
        if (p == GfxPreset.AUTO) governor.reset(GfxPreset.HIGH)
        applyPreset()
    }

    private fun applyPreset() {
        cfg = Gfx.config(if (userPreset == GfxPreset.AUTO) governor.level else userPreset)
    }

    private fun makeVignette(): Bitmap {
        val vw0 = (w / 5).coerceAtLeast(16)
        val vh0 = (h / 5).coerceAtLeast(16)
        val b = Bitmap.createBitmap(vw0, vh0, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = maxOf(vw0, vh0) * 0.78f
        p.shader = RadialGradient(vw0 / 2f, vh0 / 2f, r, intArrayOf(0x00000000, 0x00000000, 0x70120A24), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        Canvas(b).drawRect(0f, 0f, vw0.toFloat(), vh0.toFloat(), p)
        return b
    }

    private fun px(x: Float): Int = Math.round(x * PXU) + offX
    private fun py(y: Float): Int = Math.round(y * PXU) + offY
    private fun inView(x: Int, y: Int, m: Int): Boolean = x > -m && y > -m && x < vw + m && y < vh + m

    private fun tint(color: Int): PorterDuffColorFilter = filters.getOrPut(color or (0xFF shl 24)) { PorterDuffColorFilter(color or (0xFF shl 24), PorterDuff.Mode.SRC_IN) }

    private fun pixelGlow(r: Int): Bitmap = pixelGlows.getOrPut(r) { PixelAssets.pixelGlow(r) }

    /** Аддитивное пиксельное свечение (дизеринг), радиус r кадра. */
    private fun glowAt(c: Canvas, x: Int, y: Int, r: Int, color: Int, alpha: Int) {
        val g = pixelGlow(r)
        glowPaint.colorFilter = tint(color)
        glowPaint.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(g, (x - r).toFloat(), (y - r).toFloat(), glowPaint)
    }

    // ------------------------------------------------------------ кадр

    fun draw(c: Canvas, world: World, dt: Float = 1f / 60f) {
        val t0 = System.nanoTime()
        val b = buf ?: return
        val g = bc ?: return
        animT += dt.coerceIn(0f, 0.1f)
        world.fx.particleCap = cfg.maxParticles
        prepare(world)
        val terrain=if(world.adventure.active){val i=world.adventure.region;regionGrounds[i]?:GroundLayer(Biomes.region(i)).also{regionGrounds[i]=it}}else ground
        terrain.draw(g, camPxX - shX, camPxY - shY, vw, vh, cfg.groundDetail)
        if (cfg.cloudShadows) terrain.clouds(g, camPxX - shX, camPxY - shY, vw, vh, animT)
        collectProps(world)
        drawGroundFx(g, world)
        drawAdventure(g, world)
        drawEncounterRing(g, world)
        drawShadows(g, world)
        drawPickups(g, world)
        drawTrail(g, world)
        sCount = 0
        collectSorted(world)
        drawSorted(g, world)
        drawAir(g, world)
        drawShots(g, world)
        drawParticles(g, world, false)
        drawCanopies(g, world)
        if (cfg.lighting > 0) lighting(g, world)
        additive(g, world)
        if (cfg.bloom) post.bloom(b, g, 118)
        drawNumbers(g, world)
        val t1 = System.nanoTime()
        post.present(c, post.finish(b, cfg.grading, if (cfg.aberration) world.fx.aberration else 0f), k)
        overlay(c, world)
        lastBufferMs = (t1 - t0) / 1_000_000f
        lastPresentMs = (System.nanoTime() - t1) / 1_000_000f
        if (userPreset == GfxPreset.AUTO) {
            // Решение принимаем по CPU-части: подача на экран на устройстве идёт на GPU.
            if (governor.feed(lastBufferMs, dt)) applyPreset()
        }
    }

    private fun prepare(world: World) {
        camPxX = Math.round(world.camX * PXU)
        camPxY = Math.round(world.camY * PXU)
        val sh = world.fx.shake
        if (sh > 0.02f) {
            val a = sh * sh * 7f
            shX = Math.round(rng.signed() * a); shY = Math.round(rng.signed() * a)
        } else { shX = 0; shY = 0 }
        offX = vw / 2 - camPxX + shX
        offY = vh / 2 - camPxY + shY
    }

    // ------------------------------------------------------------ реквизит

    private fun collectProps(world: World) {
        propCount = 0
        if (!cfg.props) return
        val hw = vw / PXU * 0.5f + 140f
        val hh = vh / PXU * 0.5f + 180f
        val cx0 = Math.floorDiv((world.camX - hw).toInt(), PCELL); val cx1 = Math.floorDiv((world.camX + hw).toInt(), PCELL)
        val cy0 = Math.floorDiv((world.camY - hh).toInt(), PCELL); val cy1 = Math.floorDiv((world.camY + hh).toInt(), PCELL)
        for (cy in cy0..cy1) for (cx in cx0..cx1) {
            val hsh = M.hash(cx, cy, 77)
            val r = (hsh and 0xFFFF) / 65536f
            if (r > 0.3f * cfg.groundDetail.coerceAtMost(1f)) continue
            val x = cx * PCELL + 24 + ((hsh ushr 8) % (PCELL - 48))
            val y = cy * PCELL + 24 + ((hsh ushr 18) % (PCELL - 48))
            if (abs(x) < 120 && abs(y) < 120) continue
            if(world.adventure.active&&(world.adventure.map.blocked(x.toFloat(),y.toFloat(),28f,world.adventure.flags)||world.adventure.insideConduit(x.toFloat(),y.toFloat())))continue
            val pick = (hsh ushr 4) % 100
            val kind = when {
                pick < 20 -> K_TREE
                pick < 34 -> SpriteBank.P_BUSH
                pick < 46 -> SpriteBank.P_ROCK
                pick < 56 -> SpriteBank.P_BARREL
                pick < 64 -> SpriteBank.P_CRATE
                pick < 72 -> SpriteBank.P_HAY
                pick < 80 -> SpriteBank.P_LANTERN
                pick < 90 -> SpriteBank.P_FENCE
                else -> SpriteBank.P_MUSHROOM
            }
            if (propCount >= PROP_CAP) return
            propKind[propCount] = kind
            propX[propCount] = px(x.toFloat())
            propY[propCount] = py(y.toFloat())
            propCount++
        }
    }

    // ------------------------------------------------------------ плоские слои земли

    /** Общие зоны состояния; цвета и формы говорят о действии до попадания. */
    private fun drawAdventure(g:Canvas,w:World){
        val a=w.adventure;val h=w.heroAction
        fun zone(x:Float,y:Float,rx:Float,ry:Float,color:Int){fp.color=color;g.drawRect(px(x-rx).toFloat(),py(y-ry).toFloat(),px(x+rx).toFloat(),py(y+ry).toFloat(),fp)}
        if(a.active){
            if(a.region==0)conduitArt.floor(g,px(-590f).toFloat(),py(-440f).toFloat(),px(-350f).toFloat(),py(220f).toFloat())
            if(a.region==2)zone(0f,-350f,350f,300f,0x557acddf)
            if(a.region==1&&a.flags and 1==0)zone(100f,-180f,100f,120f,0x88963a19.toInt())
            if(a.region==4&&a.flags and 1==0&&!a.device)zone(0f,-250f,200f,250f,0x55496664)
            if(a.flags and 1!=0){val color=when{a.chargedT>0f->0x99896dee.toInt();a.frozenT>0f->0x99bfeaf5.toInt();a.steamT>0f->0x8893a2a7.toInt();else->0x7754abc1}
                zone(a.nodesX[1],a.nodesY[1],140f,170f,color);zone(a.nodesX[3],a.nodesY[3],a.sourceHalf,a.sourceHalf,color)}
            for(i in a.map.walls.indices){if(!a.map.enabled(i,a.flags))continue;val b=a.map.walls[i]
                wallArt.shadow(g,px(b.x-b.hx).toFloat(),py(b.y-b.hy).toFloat(),px(b.x+b.hx).toFloat(),py(b.y+b.hy).toFloat())
            }
            for(i in a.nodesX.indices){if(i>=12||i in 7..9&&a.defence !in 1..2||!a.visibleNode(i))continue
                val x=px(a.nodesX[i]).toFloat();val y=py(a.nodesY[i]).toFloat();if(x< -80||y< -80||x>vw+80||y>vh+80)continue
                val color=if(a.nodeDone(i))0xff77b884.toInt() else a.colors[a.region]
                fp.color=0x55000000;g.drawOval(x-19,y+6,x+19,y+14,fp)
                sp.color=color;sp.strokeWidth=3f
                when(i){
                    0->{};1->{fp.color=0xff547576.toInt();g.drawRect(x-15,y-26,x+15,y+7,fp);g.drawLine(x-12,y-17,x+12,y-17,sp);g.drawLine(x,y-25,x,y+3,sp)}
                    2->{val sprite=com.tavernrush.game.render.pixel.SpriteBank.heroIdle[0];com.tavernrush.game.render.pixel.PixelBlit.sprite(g,sprite.frame(0,0),x,y,1)}
                    3->{g.drawCircle(x,y,26f,sp);fp.color=0xff324640.toInt();g.drawRect(x-10,y-30,x+10,y+5,fp)}
                    4->{fp.color=0xff493e36.toInt();g.drawRect(x-23,y-29,x+23,y+5,fp);fp.color=0xff14232a.toInt();g.drawRect(x-13,y-19,x+13,y+5,fp)}
                    5->{fp.color=0xffbda267.toInt();g.drawRect(x-21,y-16,x+21,y+7,fp);g.drawLine(x-21,y-17,x,y-34,sp);g.drawLine(x,y-34,x+21,y-17,sp)}
                    6->g.drawCircle(x,y,34f,sp)
                    10->{fp.color=if(a.flags and 1!=0)0xff8aa95e.toInt() else 0xff85774d.toInt();g.drawOval(x-35,y-10,x+35,y+15,fp)
                        if(a.nursery||a.nodeDone(10)){val model=SpriteBank.creature(com.tavernrush.game.content.CreatureLook.MOSS,"idle");com.tavernrush.game.render.pixel.PixelBlit.sprite(g,model.frame(0,(animT*4).toInt()),x,y,1)}
                    }
                    11->{fp.color=0xff84613e.toInt();g.drawRect(x-17,y-19,x+17,y+4,fp);fp.color=0xffe7c875.toInt();g.drawRect(x-17,y-15,x+17,y-11,fp);g.drawRect(x-3,y-13,x+3,y-3,fp)}
                    else->{fp.color=if(a.fortHp[i-7]>0)0xffc49c55.toInt() else 0x6673614b;g.drawRect(x-17,y-23,x+17,y+4,fp);g.drawLine(x-13,y-19,x+13,y-19,sp)}
                }
            }
            if(a.baitT>0f){fp.color=0xffffc86a.toInt();g.drawCircle(px(a.baitX).toFloat(),py(a.baitY).toFloat(),8f,fp)}
        }
        if(w.team.wardT>0f){sp.color=0xffb6e09a.toInt();sp.strokeWidth=3f;val wx=px(w.team.wardX).toFloat();val wy=py(w.team.wardY).toFloat();val radius=w.team.wardRadius*PXU
            fp.color=0x336ba25c;g.drawCircle(wx,wy,radius,fp);g.drawCircle(wx,wy,radius,sp);g.drawCircle(wx,wy,radius*.93f,sp)}
        if(h.hookT>0f){sp.color=0xff9cffd4.toInt();sp.strokeWidth=2f;val x=px(h.hookEndX).toFloat();val y=py(h.hookEndY).toFloat();g.drawLine(px(w.player.x).toFloat(),py(w.player.y).toFloat(),x,y,sp);g.drawCircle(x,y,7f,sp)}
        if(h.fieldT>0f){sp.color=0x9989bfe8.toInt();sp.strokeWidth=3f;g.drawCircle(px(h.fieldX).toFloat(),py(h.fieldY).toFloat(),70f,sp)}
        if(h.shield>0f){sp.color=0xffe4d583.toInt();sp.strokeWidth=4f;val x=px(w.player.x).toFloat();val y=py(w.player.y).toFloat();g.drawLine(x+h.sx*25-h.sy*20,y+h.sy*25+h.sx*20,x+h.sx*25+h.sy*20,y+h.sy*25-h.sx*20,sp)}
    }

    private fun drawGroundFx(g: Canvas, world: World) {
        val pl = world.player
        // Зоны действия рисуем кругами: в логике они круглые, и игрок должен видеть честную границу.
        for (i in 0 until world.weaponCount) {
            val wi = world.weapons[i]
            if (wi.def.kind != WeaponKind.AURA) continue
            val healing = wi.stats.healOnHit > 0f
            val rr = (wi.stats.radius * PXU * (1f + 0.03f * sin(wi.pulse * 6f))).coerceAtLeast(6f)
            val cx = px(pl.x).toFloat(); val cy = py(pl.y).toFloat()
            val col = if (healing) 0xFF8EDDA9.toInt() else 0xFFFF7A2E.toInt()
            val fa = if (wi.active > 0f) 70 else 44
            fp.style = Paint.Style.FILL
            fp.color = (col and 0x00FFFFFF) or (fa shl 24)
            g.drawCircle(cx, cy, rr, fp)
            sp.color = (col and 0x00FFFFFF) or (210 shl 24)
            sp.strokeWidth = 1f
            g.drawCircle(cx, cy, rr, sp)
            sp.color = (col and 0x00FFFFFF) or (110 shl 24)
            g.drawCircle(cx, cy, rr * (0.8f + 0.05f * sin(animT * 7f)), sp)
        }
        // метки падения бочек
        for (i in 0 until world.projectileCount) {
            val p = world.projectiles[i]
            if (p.kind != Projectile.BARREL) continue
            val x = px(p.tx).toFloat(); val y = py(p.ty).toFloat()
            val r = (p.radius * PXU).coerceAtLeast(4f)
            val blink = if (p.state == 1) 0.5f + 0.5f * sin(p.timer * 40f) else 0.25f
            fp.style = Paint.Style.FILL
            fp.color = ((40 + 50 * blink).toInt() shl 24) or 0x00FF5A2E
            g.drawCircle(x, y, r, fp)
            sp.color = ((120 + 100 * blink).toInt() shl 24) or 0x00FFB347
            sp.strokeWidth = 1f
            g.drawCircle(x, y, r, sp)
        }
        // телеграфы и зоны врагов
        for (i in 0 until world.hazardCount) drawHazard(g, world.hazards[i])
        for (i in 0 until world.enemyCount) {
            val e = world.enemies[i]
            if (!e.alive) continue
            drawAim(g, e)
            if (e.named && e.spawnFade <= 0f) {
                val x = px(e.x).toFloat(); val y = py(e.y).toFloat()
                val rr = e.radius * PXU * (1.25f + 0.08f * sin(animT * 4f))
                sp.color = (150 shl 24) or 0x00D63A4A
                sp.strokeWidth = 1f
                g.drawCircle(x, y + 4f, rr, sp)
            }
        }
        // кольца-волны (взрывы, подбор, эволюция)
        val fx = world.fx
        for (i in 0 until fx.ringCount) {
            val r = fx.rings[i]
            val t = 1f - (r.life / r.maxLife).coerceIn(0f, 1f)
            val rad = (r.r0 + (r.r1 - r.r0) * t) * PXU
            val a = (230 * (1f - t)).toInt().coerceIn(0, 255)
            sp.color = (a shl 24) or (r.color and 0x00FFFFFF)
            sp.strokeWidth = (r.width * PXU * (1f - t * 0.6f)).coerceAtLeast(1f)
            g.drawCircle(px(r.x).toFloat(), py(r.y).toFloat(), rad, sp)
        }
    }

    private fun waveArc(g:Canvas,h:Hazard,x:Float,y:Float,r:Float){
        if(h.gapHalf<=0f){g.drawCircle(x,y,r,sp);return}
        val degrees=57.29578f
        g.drawArc(x-r,y-r,x+r,y+r,(h.gapAngle+h.gapHalf)*degrees,360f-h.gapHalf*2f*degrees,false,sp)
    }
    private fun drawHazard(g: Canvas, h: Hazard) {
        val x = px(h.x).toFloat(); val y = py(h.y).toFloat()
        val col = h.color and 0x00FFFFFF
        when (h.kind) {
            Hazard.RING -> {
                if (h.state == 0) {
                    val t = 1f - (h.delay / h.maxDelay.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    sp.color = ((120 + 120 * t).toInt() shl 24) or col
                    sp.strokeWidth = 2f
                    waveArc(g,h,x,y,(h.r0*PXU)*(1f+.3f*sin(animT*20f)))
                    if(h.gapHalf>0f){sp.color=0xffb8e898.toInt();sp.strokeWidth=2f
                        val length=h.r1*PXU
                        for(k in 0..1){val sign=if(k==0)-1 else 1;val angle=h.gapAngle+sign*h.gapHalf;g.drawLine(x+cos(angle)*h.r0*PXU,y+sin(angle)*h.r0*PXU,x+cos(angle)*length,y+sin(angle)*length,sp)}
                    }
                } else {
                    val t = 1f - (h.life / h.maxLife).coerceIn(0f, 1f)
                    val rad = (h.r0 + (h.r1 - h.r0) * t) * PXU
                    val th = (h.r * PXU).coerceAtLeast(2f)
                    sp.color = (90 shl 24) or col
                    sp.strokeWidth = th * 2f
                    waveArc(g,h,x,y,rad)
                    sp.color = (230 shl 24) or col
                    sp.strokeWidth = (th * 0.7f).coerceAtLeast(1.5f)
                    waveArc(g,h,x,y,rad)
                    sp.color = (255 shl 24) or 0x00FFFFFF
                    sp.strokeWidth = 1f
                    waveArc(g,h,x,y,rad)
                }
            }
            Hazard.LINE -> {
                val dx = cos(h.ang); val dy = sin(h.ang)
                strip(g, x, y, dx, dy, h.len * PXU, h.r * PXU, col, if (h.state == 0) 1f - (h.delay / h.maxDelay.coerceAtLeast(0.01f)) else 1f)
            }
            else -> {
                val rr = h.r * PXU
                if (h.visual) {
                    // руна: вращающийся ромб и кольцо
                    val t = 1f - (h.delay / h.maxDelay.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    sp.color = ((100 + 140 * t).toInt() shl 24) or col
                    sp.strokeWidth = 1f
                    g.drawCircle(x, y, rr * (0.6f + 0.4f * t), sp)
                    val a = animT * 3f
                    fp.style = Paint.Style.FILL
                    fp.color = ((140 + 100 * t).toInt() shl 24) or col
                    for (k in 0 until 4) {
                        val aa = a + k * 1.5708f
                        g.drawRect(x + cos(aa) * rr * 0.8f - 1f, y + sin(aa) * rr * 0.8f - 1f, x + cos(aa) * rr * 0.8f + 1f, y + sin(aa) * rr * 0.8f + 1f, fp)
                    }
                    return
                }
                if (h.state == 0) {
                    val t = 1f - (h.delay / h.maxDelay.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    val pulse = 0.5f + 0.5f * sin(animT * (10f + 14f * t))
                    fp.style = Paint.Style.FILL
                    fp.color = ((38 + 40 * t).toInt() shl 24) or col
                    g.drawCircle(x, y, rr, fp)
                    fp.color = ((70 + 90 * t).toInt() shl 24) or col
                    g.drawCircle(x, y, rr * t, fp)
                    sp.color = ((170 + 85 * pulse).toInt().coerceAtMost(255) shl 24) or col
                    sp.strokeWidth = 1.5f
                    g.drawCircle(x, y, rr, sp)
                    // «зубцы» по границе — читается как предупреждение даже без цвета
                    val n = (rr / 3f).toInt().coerceIn(8, 36)
                    fp.color = (230 shl 24) or col
                    for (k in 0 until n) {
                        val aa = k * 6.2831855f / n + animT * 0.5f
                        if (k and 1 == 0) g.drawRect(x + cos(aa) * (rr + 2f) - 1f, y + sin(aa) * (rr + 2f) - 1f, x + cos(aa) * (rr + 2f) + 1f, y + sin(aa) * (rr + 2f) + 1f, fp)
                    }
                } else {
                    // лужа: пузырящаяся зона
                    val a = (h.life / h.maxLife).coerceIn(0f, 1f)
                    fp.style = Paint.Style.FILL
                    fp.color = ((70 + 30 * sin(animT * 3f + h.x)).toInt().coerceIn(0, 255) shl 24) or col
                    g.drawCircle(x, y, rr, fp)
                    sp.color = (((140 * minOf(1f, a * 4f)).toInt()) shl 24) or col
                    sp.strokeWidth = 1f
                    g.drawCircle(x, y, rr, sp)
                    fp.color = (200 shl 24) or 0x00DFFFC0
                    for (k in 0 until 5) {
                        val ph = (animT * 0.9f + k * 0.2f + h.x * 0.01f) % 1f
                        val bx = x + cos(k * 2.4f + h.y) * rr * 0.6f
                        val by = y + sin(k * 2.4f + h.y) * rr * 0.5f - ph * 6f
                        g.drawRect(bx - 1f, by - 1f, bx + 1f, by + 1f, fp)
                    }
                }
            }
        }
    }

    /** Полоса-предупреждение с бегущими шевронами; t — готовность 0..1. */
    private fun strip(g: Canvas, x: Float, y: Float, dx: Float, dy: Float, len: Float, half: Float, col: Int, t: Float) {
        val nx = -dy; val ny = dx
        path.reset()
        path.moveTo(x + nx * half, y + ny * half)
        path.lineTo(x + dx * len + nx * half, y + dy * len + ny * half)
        path.lineTo(x + dx * len - nx * half, y + dy * len - ny * half)
        path.lineTo(x - nx * half, y - ny * half)
        path.close()
        fp.style = Paint.Style.FILL
        fp.color = ((34 + 70 * t).toInt() shl 24) or col
        g.drawPath(path, fp)
        sp.color = ((150 + 100 * t).toInt().coerceAtMost(255) shl 24) or col
        sp.strokeWidth = 1f
        g.drawPath(path, sp)
        // шевроны бегут к цели
        val step = 14f
        val off = (animT * (30f + 60f * t)) % step
        fp.color = ((120 + 120 * t).toInt() shl 24) or col
        var d = off
        while (d < len) {
            val cx = x + dx * d; val cy = y + dy * d
            g.drawRect(cx - 1f, cy - 1f, cx + 1f, cy + 1f, fp)
            g.drawRect(cx - nx * half * 0.5f - 1f, cy - ny * half * 0.5f - 1f, cx - nx * half * 0.5f + 1f, cy - ny * half * 0.5f + 1f, fp)
            g.drawRect(cx + nx * half * 0.5f - 1f, cy + ny * half * 0.5f - 1f, cx + nx * half * 0.5f + 1f, cy + ny * half * 0.5f + 1f, fp)
            d += step
        }
    }

    /** Линии прицела стрелков, замаха кабана и атак боссов — игрок видит удар заранее. */
    private fun drawAim(g: Canvas, e: Enemy) {
        if (e.spawnFade > 0f) return
        val x = px(e.x).toFloat(); val y = py(e.y).toFloat()
        val red = 0x00FF4D4D
        when (e.def.behavior) {
            EnemyBehavior.SHOOTER -> if (e.state == 1) {
                val len = e.def.range * 1.7f * PXU
                sp.color = ((90 + 140 * e.windup).toInt() shl 24) or (if (e.def.special >= 1f) 0x00E8E4F2 else red)
                sp.strokeWidth = 1f
                g.drawLine(x, y, x + e.tx * len, y + e.ty * len, sp)
            }
            EnemyBehavior.CHARGER -> if (e.state == 1) {
                strip(g, x, y, e.tx, e.ty, e.def.special * EnemyAI.CHARGE_TIME * PXU, e.radius * PXU * 0.9f, red, e.windup)
            }
            EnemyBehavior.BOSS -> if (e.state == BossSystem.TELE) {
                when (e.act) {
                    BossSystem.A_DASH -> strip(g, x, y, e.tx, e.ty, BossSystem.DASH_LINE_LEN * PXU, BossSystem.DASH_LINE_HALF * PXU, red, e.windup)
                    BossSystem.A_CLEAVE -> {
                        val n = if (e.stage >= 2) 5 else 3
                        val base = atan2(e.ty, e.tx)
                        sp.color = ((90 + 140 * e.windup).toInt() shl 24) or red
                        sp.strokeWidth = 1f
                        for (k in 0 until n) {
                            val a = base + (k - (n - 1) * 0.5f) * 0.26f
                            g.drawLine(x, y, x + cos(a) * 330f * 0.9f * PXU, y + sin(a) * 330f * 0.9f * PXU, sp)
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun drawShadows(g: Canvas, world: World) {
        if (cfg.shadowMode == 0) return
        val projected = cfg.shadowMode >= 2
        // враги
        for (i in 0 until world.enemyCount) {
            val e = world.enemies[i]
            if (!e.alive) continue
            val x = px(e.x); val y0 = py(e.y)
            if (!inView(x, y0, 70)) continue
            val anim = enemyAnim(e)
            val dir = if (e.faceLeft) 1 else 0
            val gy = y0 + anim.drop.toInt()
            val rr = (e.radius * PXU * 0.95f)
            blob(g, x, gy, rr, if (e.spawnFade > 0f) 30 else 70)
            if (projected && e.spawnFade <= 0f) {
                val s = anim.shadow(dir, enemyFrame(e))
                g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (gy - s.oy).toFloat(), null)
            }
        }
        // герой
        val pl = world.player
        val hx = px(pl.x); val hy0 = py(pl.y)
        val ha = heroAnim(world)
        val hdir = if (pl.faceX < -0.1f) 1 else 0
        val hgy = hy0 + ha.drop.toInt()
        blob(g, hx, hgy, 11f, 80)
        if (projected) {
            val s = ha.shadow(hdir, heroFrame(world))
            g.drawBitmap(s.bmp, (hx - s.ox).toFloat(), (hgy - s.oy).toFloat(), null)
        }
        // друзья-существа
        for (i in 0 until world.team.count) {
            val m = world.team.mates[i]!!
            if (!m.active) continue
            val anim = mateAnim(m)
            val mx = px(m.x); val my = py(m.y) + anim.drop.toInt()
            if(m.state==com.tavernrush.game.entities.Mate.ACT&&m.species==com.tavernrush.game.systems.CompanionSystem.S_LYNX){
                val lx=px(m.tx);val ly=py(m.ty)
                if(inView(lx,ly,50)){
                    sp.style=Paint.Style.STROKE;sp.strokeWidth=maxOf(1f,2f*PXU);sp.color=0xCCB59CFF.toInt()
                    val rr=35f*PXU;g.drawCircle(lx.toFloat(),ly.toFloat(),rr,sp)
                    g.drawLine(lx-rr-4f,ly.toFloat(),lx-rr+3f,ly.toFloat(),sp);g.drawLine(lx+rr-3f,ly.toFloat(),lx+rr+4f,ly.toFloat(),sp)
                }
            }
            if (!inView(mx, my, 60)) continue
            blob(g, mx, my, 10f, 70)
            if (projected && m.state != com.tavernrush.game.entities.Mate.DOWNED) {
                val s = anim.shadow(if (m.faceLeft) 1 else 0, mateFrame(m))
                g.drawBitmap(s.bmp, (mx - s.ox).toFloat(), (my - s.oy).toFloat(), null)
            }
        }
        val wild = wildAnim(world)
        if (wild != null) {
            val enc = world.encounter
            val wx = px(enc.x); val wy = py(enc.y) + wild.drop.toInt()
            if (inView(wx, wy, 60)) {
                blob(g, wx, wy, 10f, 70)
                if (projected) {
                    val s = wild.shadow(if (enc.faceLeft) 1 else 0, (enc.anim * 4f).toInt())
                    g.drawBitmap(s.bmp, (wx - s.ox).toFloat(), (wy - s.oy).toFloat(), null)
                }
            }
        }
        // реквизит
        for (i in 0 until propCount) {
            val kind = propKind[i]
            val pr = if (kind == K_TREE) SpriteBank.treeTrunk else SpriteBank.props[kind]
            val x = propX[i]; val y = propY[i]
            if (!inView(x, y, 80)) continue
            if (projected) g.drawBitmap(pr.shadow.bmp, (x - pr.shadow.ox).toFloat(), (y - pr.shadow.oy).toFloat(), null)
            if (kind == K_TREE) {
                val cn = SpriteBank.treeCanopy
                g.drawBitmap(cn.shadow.bmp, (x - cn.shadow.ox).toFloat(), (y - cn.shadow.oy).toFloat(), null)
            }
        }
    }

    private fun blob(g: Canvas, x: Int, y: Int, rx: Float, alpha: Int) {
        fp.style = Paint.Style.FILL
        fp.color = (alpha shl 24) or 0x00140A28
        g.drawOval(x - rx, y - rx * 0.42f, x + rx, y + rx * 0.42f, fp)
        fp.color = ((alpha * 3 / 5) shl 24) or 0x00140A28
        g.drawOval(x - rx * 0.62f, y - rx * 0.26f, x + rx * 0.62f, y + rx * 0.26f, fp)
    }

    // ------------------------------------------------------------ лут

    private val gemGlow = intArrayOf(0xFF57C7FF.toInt(), 0xFF63E07A.toInt(), 0xFFC77DFF.toInt(), 0xFFFF5D73.toInt())

    private fun drawPickups(g: Canvas, world: World) {
        for (i in 0 until world.pickupCount) {
            val p = world.pickups[i]
            val x = px(p.x); val y = py(p.y)
            if (!inView(x, y, 30)) continue
            val bob = (sin(p.age * 4f + p.x * 0.05f) * 1.6f).toInt()
            val anim: Anim
            var glowCol = 0
            var glowR = 0
            when (p.kind) {
                Pickup.XP -> {
                    val tier = if (p.value >= 20) 3 else if (p.value >= 5) 2 else if (p.value >= 2) 1 else 0
                    anim = SpriteBank.gems[tier]; glowCol = gemGlow[tier]; glowR = 6 + tier
                }
                Pickup.GOLD -> { anim = if (p.value >= 5) SpriteBank.coinBig else SpriteBank.coin; glowCol = 0xFFFFC94D.toInt(); glowR = 6 }
                Pickup.HEAL -> { anim = SpriteBank.chicken; glowCol = 0xFF7CFF8A.toInt(); glowR = 9 }
                Pickup.MAGNET -> { anim = SpriteBank.magnet; glowCol = 0xFF7FD3FF.toInt(); glowR = 9 }
                else -> { anim = SpriteBank.chest; glowCol = 0xFFFFC94D.toInt(); glowR = 16 }
            }
            blob(g, x, y + 5, if (p.kind == Pickup.CHEST) 11f else 5f, 60)
            val frame = (p.age * 9f).toInt()
            val s = anim.frame(0, if (p.kind == Pickup.XP || p.kind == Pickup.GOLD) frame else 0)
            val jump = if (p.kind == Pickup.CHEST) (abs(sin(p.age * 3f)) * 3f).toInt() else 0
            val gy = y + anim.drop.toInt() - bob - jump
            g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (gy - s.oy).toFloat(), null)
            if (glowR > 0 && cfg.preset.ordinal >= GfxPreset.MEDIUM.ordinal) {
                val pulse = (110 + 40 * sin(p.age * 5f + p.x)).toInt()
                glowAt(g, x, y - 2 - bob, glowR, glowCol, if (p.kind == Pickup.CHEST) pulse + 40 else pulse / 2)
            }
        }
    }

    // ------------------------------------------------------------ тела с сортировкой по глубине

    private fun bossAttackClip(e: Enemy): Boolean {
        if (!e.boss || !SpriteBank.hasClip(e.def.look, "attack")) return false
        val st = e.state
        return if (e.def.id == "rat_king") st == BossSystem.TELE || st == BossSystem.RECOVER
        else st == BossSystem.TELE || st == BossSystem.ACT || st == BossSystem.RECOVER || st == BossSystem.PHASE || st == BossSystem.INTRO
    }

    private fun enemyAnim(e: Enemy): Anim {
        val look = e.def.look
        if (e.boss) return if (bossAttackClip(e)) SpriteBank.enemy(look, "attack") else SpriteBank.enemy(look)
        if (e.named) return SpriteBank.named(look, World.NAMED_SIZE)
        if (e.elite) return SpriteBank.elite(look, World.ELITE_SIZE)
        return SpriteBank.enemy(look)
    }

    private fun enemyFrame(e: Enemy): Int {
        if (bossAttackClip(e)) {
            return when (e.state) {
                BossSystem.TELE -> (e.windup * 2.99f).toInt().coerceIn(0, 2)
                BossSystem.ACT -> if (e.stateT > 0.2f) 3 else 4
                BossSystem.RECOVER -> if (e.stateT > 0.35f) 4 else 5
                else -> 2
            }
        }
        return (e.anim * WALK_FPS[e.def.look.ordinal % WALK_FPS.size]).toInt()
    }

    private fun heroAnim(world: World): Anim {
        val i = world.heroIndex.coerceIn(0, SpriteBank.HEROES - 1)
        return if (world.player.moving) SpriteBank.heroWalk[i] else SpriteBank.heroIdle[i]
    }

    private fun heroFrame(world: World): Int =
        if (world.player.moving) (world.player.walk * 6f / TWO_PI).toInt() else (world.time * 3.2f).toInt()

    private fun add(kind: Int, idx: Int, keyPx: Int) {
        if (sCount >= SORT_CAP) return
        sKind[sCount] = kind; sIdx[sCount] = idx
        sKey[sCount] = (keyPx + 140).coerceIn(0, BUCKETS - 1)
        sCount++
    }

    private fun collectSorted(world: World) {
        val a=world.adventure
        if(a.active&&a.region==0)for(i in 12..14){val x=px(a.nodesX[i]);val y=py(a.nodesY[i]);if(inView(x,y,80))add(K_CONDUIT,i,y)}
        if(a.active)for(i in a.map.walls.indices){if(!a.map.enabled(i,a.flags))continue;val b=a.map.walls[i]
            for(tile in 0 until WallArt.count(b)){WallArt.bounds(b,tile,wallBounds)
                val x=px((wallBounds[0]+wallBounds[2])*.5f);val y=py(wallBounds[3])
                if(inView(x,y,90))add(K_WALL,i*WallArt.STRIDE+tile,y)
            }
        }
        for (i in 0 until world.enemyCount) {
            val e = world.enemies[i]
            if (!e.alive) continue
            val y = py(e.y)
            if (!inView(px(e.x), y, 80)) continue
            add(K_ENEMY, i, y)
        }
        val pl = world.player
        add(K_HERO, 0, py(pl.y) + 6)
        for (i in 0 until world.team.count) {
            val m = world.team.mates[i]!!
            if (m.active && inView(px(m.x), py(m.y), 70)) add(K_MATE, i, py(m.y) + 4)
        }
        if (wildAnim(world) != null && inView(px(world.encounter.x), py(world.encounter.y), 70)) add(K_WILD, 0, py(world.encounter.y) + 4)
        for (i in 0 until propCount) if (inView(propX[i], propY[i], 90)) add(K_PROP, i, propY[i])
        for (s in 0 until world.weaponCount) {
            val wi = world.weapons[s]
            if (wi.def.kind != WeaponKind.ORBIT || wi.fade <= 0.01f) continue
            val n = wi.stats.amount.coerceAtMost(15)
            for (j in 0 until n) {
                val a = wi.angle + j * TWO_PI / wi.stats.amount
                add(K_ORBIT, s * 16 + j, py(pl.y + sin(a) * wi.stats.range) + 4)
            }
        }
    }

    private fun drawSorted(g: Canvas, world: World) {
        java.util.Arrays.fill(bucket, 0)
        for (i in 0 until sCount) bucket[sKey[i] + 1]++
        for (i in 1..BUCKETS) bucket[i] += bucket[i - 1]
        for (i in 0 until sCount) {
            val pos = bucket[sKey[i]]++
            oKind[pos] = sKind[i]; oIdx[pos] = sIdx[i]
        }
        for (i in 0 until sCount) {
            when (oKind[i]) {
                K_ENEMY -> drawEnemy(g, world.enemies[oIdx[i]], world)
                K_HERO -> drawHero(g, world)
                K_PROP -> drawProp(g, oIdx[i])
                K_ORBIT -> drawOrbit(g, world, oIdx[i] shr 4, oIdx[i] and 15)
                K_MATE -> drawMate(g, world.team.mates[oIdx[i]]!!, world)
                K_WILD -> drawWild(g, world)
                K_WALL -> drawWall(g,world,oIdx[i])
                K_CONDUIT -> {val a=world.adventure;val n=oIdx[i];conduitArt.landmark(g,px(a.nodesX[n]).toFloat(),py(a.nodesY[n]).toFloat(),n,a.nodeDone(n),n==12&&kotlin.math.abs(world.player.x-a.nodesX[n])<70f&&world.player.y<=a.nodesY[n]&&world.player.y>a.nodesY[n]-120f)}
            }
        }
    }

    private fun drawWall(g:Canvas,w:World,encoded:Int){
        val index=encoded/WallArt.STRIDE;val tile=encoded%WallArt.STRIDE;val a=w.adventure
        val b=a.map.walls[index];WallArt.bounds(b,tile,wallBounds)
        val l=px(wallBounds[0]).toFloat();val t=py(wallBounds[1]).toFloat();val r=px(wallBounds[2]).toFloat();val bottom=py(wallBounds[3]).toFloat()
        val alpha=if(WallArt.hidesHero(l,t,r,bottom,px(w.player.x).toFloat(),py(w.player.y).toFloat()))100 else 255
        wallArt.tile(g,l,t,r,bottom,a.region,index*31+tile,alpha)
        if(index==0&&a.region==5&&tile==WallArt.count(b)/2){fp.color=(alpha shl 24)or 0xecd69a;g.drawRect(l+3,t-WallArt.HEIGHT+3,r-3,t-WallArt.HEIGHT+5,fp)}
    }

    private fun drawEnemy(g: Canvas, e: Enemy, world: World) {
        val anim = enemyAnim(e)
        val dir = if (e.faceLeft) 1 else 0
        val s = anim.frame(dir, enemyFrame(e))
        var x = px(e.x)
        val gy = py(e.y) + anim.drop.toInt()
        // замах дрожит: враг «накапливает» удар
        if (e.windup > 0.05f && (e.def.behavior == EnemyBehavior.CHARGER || e.def.behavior == EnemyBehavior.BOMBER || e.def.behavior == EnemyBehavior.SUMMONER)) {
            x += if (((animT * 40f).toInt() and 1) == 0) 1 else -1
        }
        val left = (x - s.ox).toFloat(); val top = (gy - s.oy).toFloat()
        if (e.spawnFade > 0f) {
            val t = 1f - e.spawnFade / com.tavernrush.game.systems.WaveDirector.RING_TELEGRAPH
            fp.style = Paint.Style.FILL
            fp.color = ((50 + 80 * t).toInt() shl 24) or 0x00FF3B30
            val rr = e.radius * PXU * (0.6f + 0.5f * t)
            g.drawCircle(x.toFloat(), gy.toFloat(), rr, fp)
            alphaPaint.alpha = (60 + 140 * t).toInt()
            g.drawBitmap(s.bmp, left, top, alphaPaint)
            return
        }
        val mid = cfg.preset.ordinal >= GfxPreset.MEDIUM.ordinal
        if (mid && (e.elite || e.named || e.boss || e.shield > 0f)) {
            val col = when {
                e.named -> 0xFFD63A4A.toInt()
                e.boss -> 0xFFFFB347.toInt()
                e.affix > 0 -> AFFIX_COL[e.affix]
                else -> 0xFFFFC94D.toInt()
            }
            glowAt(g, x, gy - (s.oy / 2), (e.radius * PXU * (if (e.boss) 1.2f else 1.5f)).toInt().coerceIn(10, 60), col, (80 + 30 * sin(animT * 6f)).toInt())
        }
        // призрак и подрывник: прозрачность/мигание по состоянию роли
        var alpha = 255
        if (e.def.behavior == EnemyBehavior.BLINKER) {
            alpha = if (e.state == 0) 205 else (255 * (1f - 0.88f * e.windup)).toInt().coerceIn(30, 255)
        }
        if (e.invulnT > 0f && e.boss) alpha = if (((animT * 18f).toInt() and 1) == 0) 255 else 170
        // отклик на удар: короткое «сплющивание» вокруг точки под ногами (боссы и заморозка — жёсткие)
        val squash = e.squash > 0f && !e.boss && e.frozenT <= 0f
        if (squash) {
            val t = e.squash / com.tavernrush.game.systems.CombatSystem.SQUASH_TIME
            g.save()
            g.scale(1f + 0.14f * t, 1f - 0.12f * t, x.toFloat(), gy.toFloat())
        }
        if (alpha < 255) { alphaPaint.alpha = alpha; g.drawBitmap(s.bmp, left, top, alphaPaint) } else g.drawBitmap(s.bmp, left, top, null)
        if (e.slowTimer > 0f) {
            slowPaint.alpha = 64
            g.drawBitmap(s.bmp, left, top, slowPaint)
        }
        if (e.frozenT > 0f) {
            icePaint.alpha = 170
            g.drawBitmap(s.bmp, left, top, icePaint)
        } else {
            if (e.wetT > 0f) {
                wetPaint.alpha = (78 + 22 * sin(animT * 5f + e.phase)).toInt()
                g.drawBitmap(s.bmp, left, top, wetPaint)
            }
            if (e.burnT > 0f) {
                burnPaint.alpha = (80 + 50 * sin(animT * 15f + e.phase)).toInt().coerceIn(0, 255)
                g.drawBitmap(s.bmp, left, top, burnPaint)
            }
        }
        var flash = if (e.flash > 0f) (255 * (e.flash / 0.11f).coerceIn(0f, 1f)).toInt() else 0
        if (e.def.behavior == EnemyBehavior.BOMBER && e.state == 1) flash = if (((animT * (8f + 14f * e.windup)).toInt() and 1) == 0) 220 else 0
        if (e.boss && e.windup > 0.6f && e.state == BossSystem.TELE) flash = maxOf(flash, (70 * sin(animT * 30f).coerceAtLeast(0f)).toInt())
        if (flash > 0) {
            flashPaint.alpha = flash
            g.drawBitmap(s.bmp, left, top, flashPaint)
        }
        if (squash) g.restore()
        if (e.shield > 0f) {
            sp.color = (((110 + 60 * sin(animT * 8f)).toInt()) shl 24) or 0x007FD3FF
            sp.strokeWidth = 1f
            g.drawCircle(x.toFloat(), (gy - s.oy / 2).toFloat(), e.radius * PXU * 1.15f, sp)
        }
        if (e.stun > 0f) {
            fp.style = Paint.Style.FILL
            fp.color = 0xFFFFE066.toInt()
            for (k in 0 until 3) {
                val a = animT * 6f + k * 2.0944f
                val sx = x + cos(a) * e.radius * PXU * 0.7f; val sy = gy - s.oy + 2f + sin(a) * 2.5f
                g.drawRect(sx - 1f, sy - 1f, sx + 1f, sy + 1f, fp)
            }
        }
        if (e.named) pixelBar(g, x, gy - s.oy - 5, (e.radius * PXU * 1.8f).toInt().coerceAtLeast(20), e.hp / e.maxHp, 0xFFD63A4A.toInt())
        else if (e.elite) pixelBar(g, x, gy - s.oy - 4, (e.radius * PXU * 1.4f).toInt().coerceAtLeast(14), e.hp / e.maxHp, if (e.affix > 0) AFFIX_COL[e.affix] else 0xFFFFC94D.toInt())
    }

    // ------------------------------------------------------------ существа-друзья и встреча

    private fun mateAnim(m: com.tavernrush.game.entities.Mate): Anim {
        val look = Content.creatures[m.species].look
        val clip = when (m.state) {
            com.tavernrush.game.entities.Mate.ACT -> "act"
            com.tavernrush.game.entities.Mate.DOWNED, com.tavernrush.game.entities.Mate.RECOVER -> "idle"
            else -> if (m.vx * m.vx + m.vy * m.vy > 400f) "walk" else "idle"
        }
        return SpriteBank.creature(look, clip, m.visualAge, m.visualForm)
    }

    private fun mateFrame(m: com.tavernrush.game.entities.Mate): Int {
        val fast = m.state == com.tavernrush.game.entities.Mate.ACT || m.vx * m.vx + m.vy * m.vy > 400f
        return (m.anim * (if (fast) 9f else 3.2f)).toInt()
    }

    /** Дикое существо на встрече: видно, пока идёт знакомство или оно убегает/уходит домой. */
    private fun wildAnim(world: World): Anim? {
        val enc = world.encounter
        if (enc.species < 0) return null
        val st = enc.state
        if (st == EncounterSystem.ACTIVE || st == EncounterSystem.FLED || (st == EncounterSystem.JOINED && !enc.joinedAsMate)) {
            return SpriteBank.creature(Content.creatures[enc.species].look, if (st == EncounterSystem.ACTIVE) "idle" else "walk")
        }
        return null
    }

    private fun drawMate(g: Canvas, m: com.tavernrush.game.entities.Mate, world: World) {
        val anim = mateAnim(m)
        val dir = if (m.faceLeft) 1 else 0
        val s = anim.frame(dir, mateFrame(m))
        val x = px(m.x)
        val gy = py(m.y) + anim.drop.toInt()
        val ageScale=1f // proportions and size are baked, not a uniform child shrink
        g.save();g.scale(ageScale,ageScale,x.toFloat(),gy.toFloat())
        val left = (x - s.ox).toFloat()
        val top = (gy - s.oy).toFloat() - m.hop * PXU
        val def = Content.creatures[m.species]
        val downed = m.state == com.tavernrush.game.entities.Mate.DOWNED
        if (m.state == com.tavernrush.game.entities.Mate.ENTER || m.emote > 0f && !downed) {
            glowAt(g, x, gy - s.oy / 2, 14, def.color, (60 + 40 * sin(animT * 9f)).toInt())
        }
        if(m.resonanceFlash>0f&&m.resonance in Content.creatures.indices&&!downed){glowAt(g,x,gy-8,20,Content.creatures[m.resonance].color,(120*m.resonanceFlash).toInt().coerceIn(0,160))}
        if (downed) {
            g.save()
            g.scale(1.12f, 0.55f, x.toFloat(), gy.toFloat())
            alphaPaint.alpha = 150
            g.drawBitmap(s.bmp, left, top, alphaPaint)
            g.restore()
        } else {
            if (m.invuln > 0f && m.state != com.tavernrush.game.entities.Mate.ACT && ((animT * 16f).toInt() and 1) == 0) {
                alphaPaint.alpha = 170
                g.drawBitmap(s.bmp, left, top, alphaPaint)
            } else g.drawBitmap(s.bmp, left, top, null)
            if (m.state == com.tavernrush.game.entities.Mate.RECOVER) {
                // отдышка: мелкие искры над спиной — приём стоил сил, друг сейчас уязвим
                fp.style = Paint.Style.FILL
                fp.color = 0xFFFFE066.toInt()
                for (k in 0 until 2) {
                    val a = animT * 11f + k * 3.1f
                    val sx = x + cos(a) * 5f; val sy = top + 2f + sin(a * 1.7f) * 2f
                    g.drawRect(sx - 1f, sy - 1f, sx + 1f, sy + 1f, fp)
                }
            }
        }
        // метки над существом: здоровье, щиты Камнерога, заряд Грозорыса
        val barY = gy - s.oy - 4 - (m.hop * PXU).toInt()
        if (downed || m.hp < m.maxHp * 0.995f) pixelBar(g, x, barY, 20, if (downed) m.reviveT / com.tavernrush.game.systems.CompanionSystem.REVIVE_TIME else m.hp / m.maxHp, if (downed) 0xFFFFE066.toInt() else 0xFF7CFF8A.toInt())
        if (!downed && m.species == com.tavernrush.game.systems.CompanionSystem.S_HORN) {
            val n = if (m.spec == 1) 4 else 3
            fp.style = Paint.Style.FILL
            for (k in 0 until n) {
                val px0 = x - (n * 4) / 2 + k * 4
                fp.color = if (k < m.guard) 0xFF9FD8FF.toInt() else 0x55223A55
                g.drawRect(px0.toFloat(), (barY - 4).toFloat(), (px0 + 3).toFloat(), (barY - 1).toFloat(), fp)
            }
        }
        if (!downed && (m.species == com.tavernrush.game.systems.CompanionSystem.S_LYNX || m.species == com.tavernrush.game.systems.CompanionSystem.S_CONFLUENCE) && m.charge > 0.02f) {
            fp.style = Paint.Style.FILL
            fp.color = 0x66231A44
            g.drawRect((x - 10).toFloat(), (barY - 3).toFloat(), (x + 10).toFloat(), (barY - 1).toFloat(), fp)
            fp.color = if (m.charge >= (if(m.species==4).6f else 1f)) 0xFFFFF29A.toInt() else Content.creatures[m.species].color
            g.drawRect((x - 10).toFloat(), (barY - 3).toFloat(), (x - 10 + 20 * m.charge.coerceIn(0f, 1f)), (barY - 1).toFloat(), fp)
        }
        if (m.emote > 0f && !downed) font.char(g, '+', x, barY - 14 - ((1f - m.emote.coerceAtMost(1f)) * 4f).toInt(), 0xFFFF9EC0.toInt(), 1, (255 * m.emote.coerceAtMost(1f)).toInt())
            g.restore()
    }

    private fun drawWild(g: Canvas, world: World) {
        val enc = world.encounter
        val anim = wildAnim(world) ?: return
        val dir = if (enc.faceLeft) 1 else 0
        val s = anim.frame(dir, (enc.anim * (if (enc.state == EncounterSystem.ACTIVE) 3.2f else 9f)).toInt())
        var x = px(enc.x)
        val gy = py(enc.y) + anim.drop.toInt()
        // напуган: пока доверия нет, дрожит
        if (enc.state == EncounterSystem.ACTIVE && enc.stage == 0 && enc.trust < 0.2f) x += if (((animT * 30f).toInt() and 1) == 0) 1 else -1
        val left = (x - s.ox).toFloat(); val top = (gy - s.oy).toFloat()
        val def = Content.creatures[enc.species]
        if (enc.state == EncounterSystem.ACTIVE) glowAt(g, x, gy - s.oy / 2, 13, def.color, (40 + 30 * sin(animT * 5f)).toInt())
        if (enc.state == EncounterSystem.JOINED || enc.state == EncounterSystem.FLED) { alphaPaint.alpha = 210; g.drawBitmap(s.bmp, left, top, alphaPaint) }
        else g.drawBitmap(s.bmp, left, top, null)
        if (enc.state == EncounterSystem.ACTIVE && enc.hp < enc.maxHp * 0.999f) pixelBar(g, x, gy - s.oy - 4, 22, enc.hp / enc.maxHp, 0xFFFF6B6B.toInt())
        if (enc.emote > 0f) {
            val a = (255 * enc.emote.coerceAtMost(1f)).toInt()
            val rise = ((1f - enc.emote.coerceAtMost(1f)) * 4f).toInt()
            if (enc.state == EncounterSystem.ACTIVE && enc.stage == 0) font.char(g, '!', x, gy - s.oy - 14 - rise, 0xFFFFE066.toInt(), 1, a)
            else font.char(g, '+', x, gy - s.oy - 14 - rise, 0xFFFF9EC0.toInt(), 1, a)
        }
    }

    /** Кольцо встречи на земле: пунктир, заливка и дуга доверия, заполняющаяся, пока герой рядом и враги отогнаны. */
    private fun drawEncounterRing(g: Canvas, world: World) {
        val enc = world.encounter
        if (!enc.active) return
        val cx = px(enc.siteX).toFloat(); val cy = py(enc.siteY).toFloat()
        val r = EncounterSystem.RING_R * PXU
        if (!inView(cx.toInt(), cy.toInt(), (r + 24).toInt())) return
        val col = Content.creatures[enc.species].color and 0x00FFFFFF
        val hx = world.player.x - enc.siteX; val hy = world.player.y - enc.siteY
        val inRing = hx * hx + hy * hy <= EncounterSystem.RING_R * EncounterSystem.RING_R
        fp.style = Paint.Style.FILL
        fp.color = col or ((if (inRing) 34 else 18) shl 24)
        g.drawCircle(cx, cy, r, fp)
        dst.set(cx - r, cy - r, cx + r, cy + r)
        sp.strokeWidth = 1f
        sp.color = col or ((if (inRing) 230 else 150) shl 24)
        for (k in 0 until 24) g.drawArc(dst, k * 15f + animT * 8f, 8f, false, sp)
        if (enc.trust > 0.005f) {
            sp.strokeWidth = 2f
            sp.color = 0xFFFFC2D8.toInt()
            dst.set(cx - r + 3f, cy - r + 3f, cx + r - 3f, cy + r - 3f)
            g.drawArc(dst, -90f, 360f * enc.trust, false, sp)
            sp.strokeWidth = 1f
        }
    }

    /** Остаточные образы рывка: голубые силуэты героя, гаснущие за 0,3 с. */
    private fun drawTrail(g: Canvas, world: World) {
        val anim = heroAnim(world)
        val fr = heroFrame(world)
        for (i in 0 until World.TRAIL) {
            val age = world.trailAge[i]
            if (age >= 0.3f) continue
            val dir = world.trailDir[i]
            val s = anim.frame(dir, fr)
            val x = px(world.trailX[i]); val gy = py(world.trailY[i]) + anim.drop.toInt()
            dashPaint.alpha = (140 * (1f - age / 0.3f)).toInt().coerceIn(0, 255)
            g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (gy - s.oy).toFloat(), dashPaint)
        }
    }

    private fun drawHero(g: Canvas, world: World) {
        val pl = world.player
        val anim = heroAnim(world)
        val dir = if (pl.faceX < -0.1f) 1 else 0
        val s = anim.frame(dir, heroFrame(world))
        val x = px(pl.x)
        val gy = py(pl.y) + anim.drop.toInt()
        val left = (x - s.ox).toFloat(); val top = (gy - s.oy).toFloat()
        if (pl.dead) {
            alphaPaint.alpha = 150
            g.drawBitmap(s.bmp, left, top + 4f, alphaPaint)
            return
        }
        val blink = pl.invuln > 0f && ((pl.invuln * 14f).toInt() and 1) == 0
        if (blink) {
            alphaPaint.alpha = 120
            g.drawBitmap(s.bmp, left, top, alphaPaint)
        } else g.drawBitmap(s.bmp, left, top, null)
        if (pl.hurtFlash > 0f) {
            flashPaint.alpha = (255 * (pl.hurtFlash / 0.25f).coerceIn(0f, 1f)).toInt()
            g.drawBitmap(s.bmp, left, top, flashPaint)
        }
        if (world.levelFlash > 0f) {
            val t = world.levelFlash
            glowAt(g, x, gy - s.oy / 2, (22 + (1f - t) * 26f).toInt(), 0xFFFFD54A.toInt(), (230 * t).toInt())
        }
        pixelBar(g, x, gy + 5, 24, pl.hpFrac, 0xFFE8483F.toInt())
    }

    private fun drawProp(g: Canvas, i: Int) {
        val kind = propKind[i]
        val pr = if (kind == K_TREE) SpriteBank.treeTrunk else SpriteBank.props[kind]
        val x = propX[i]; val y = propY[i]
        g.drawBitmap(pr.body.bmp, (x - pr.body.ox).toFloat(), (y - pr.body.oy).toFloat(), null)
    }

    private fun drawOrbit(g: Canvas, world: World, slot: Int, j: Int) {
        val wi = world.weapons[slot]
        val pl = world.player
        val n = wi.stats.amount
        val a = wi.angle + j * TWO_PI / n
        val x = px(pl.x + cos(a) * wi.stats.range)
        val y = py(pl.y + sin(a) * wi.stats.range)
        val anim = SpriteBank.mug
        val fi = ((a % TWO_PI + TWO_PI) % TWO_PI / TWO_PI * anim.count).toInt()
        val s = anim.frame(0, fi)
        val gy = y + anim.drop.toInt()
        val sh = anim.shadow(0, fi)
        if (cfg.shadowMode >= 2) g.drawBitmap(sh.bmp, (x - sh.ox).toFloat(), (gy - sh.oy + 8).toFloat(), null)
        alphaPaint.alpha = (255 * wi.fade).toInt().coerceIn(0, 255)
        g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (gy - s.oy).toFloat(), alphaPaint)
    }

    private fun pixelBar(g: Canvas, cx: Int, y: Int, wPx: Int, frac: Float, color: Int) {
        val l = cx - wPx / 2
        fp.style = Paint.Style.FILL
        fp.color = 0xFF1B1224.toInt()
        g.drawRect((l - 1).toFloat(), (y - 1).toFloat(), (l + wPx + 1).toFloat(), (y + 4).toFloat(), fp)
        fp.color = 0xFF4A1512.toInt()
        g.drawRect(l.toFloat(), y.toFloat(), (l + wPx).toFloat(), (y + 3).toFloat(), fp)
        val f = (wPx * frac.coerceIn(0f, 1f)).toInt()
        if (f > 0) {
            fp.color = color
            g.drawRect(l.toFloat(), y.toFloat(), (l + f).toFloat(), (y + 3).toFloat(), fp)
            fp.color = 0x66FFFFFF
            g.drawRect(l.toFloat(), y.toFloat(), (l + f).toFloat(), (y + 1).toFloat(), fp)
        }
    }

    // ------------------------------------------------------------ снаряды в воздухе

    private fun heading(p: Projectile): Int {
        val a = atan2(-p.vy, p.vx)
        return Math.floorMod(Math.round(a / TWO_PI * SpriteBank.HEADINGS), SpriteBank.HEADINGS)
    }

    private fun drawAir(g: Canvas, world: World) {
        val fx = world.fx
        for (i in 0 until world.projectileCount) {
            val p = world.projectiles[i]
            val x = px(p.x); val y = py(p.y)
            if (!inView(x, y, 50)) continue
            when (p.kind) {
                Projectile.KNIFE -> {
                    val frost = world.weapons[p.slot].def.icon == IconId.FROST
                    val anim = if (frost) SpriteBank.frost else SpriteBank.knife
                    sprite(g, anim, 0, heading(p), x, y, shadow = true)
                    if (cfg.trails && ((world.ticks + i) and 1) == 0) {
                        fx.particle(p.x - p.vx * 0.012f, p.y - p.vy * 0.012f, 0f, 0f, 0.18f, 3f, if (frost) 0xFFBDF3FF.toInt() else 0xFFE6EEF7.toInt(), Particle.DOT, 8f, z = 12f)
                    }
                }
                Projectile.PAN -> sprite(g, SpriteBank.pan, 0, (p.rot / TWO_PI * 12f).toInt(), x, y, shadow = true)
                Projectile.BARREL -> {
                    val zpx = (p.z * PXU).toInt()
                    sprite(g, SpriteBank.barrel, 0, (p.rot / TWO_PI * 12f).toInt(), x, y - zpx, shadow = true, shadowY = y)
                    if (p.state == 1) glowAt(g, x + 1, y - zpx - 10, 5, 0xFFFFC94D.toInt(), (180 + 60 * sin(p.timer * 50f)).toInt())
                }
                Projectile.TRAP -> {
                    alphaPaint.alpha = if (p.timer > 0f) 150 else 255
                    val anim = if (p.state == 1) SpriteBank.trapClosed else SpriteBank.trapOpen
                    val s = anim.frame(0, 0)
                    g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (y + anim.drop.toInt() - s.oy).toFloat(), alphaPaint)
                }
                Projectile.RAVEN -> {
                    val dir = if (cos(p.rot) < 0f) 1 else 0
                    val fi = ((world.time * 11f).toInt() + i) and 3
                    val anim = SpriteBank.raven
                    val s = anim.frame(dir, fi)
                    val sh = anim.shadow(dir, fi)
                    val gy = y + 14
                    if (cfg.shadowMode >= 2) g.drawBitmap(sh.bmp, (x - sh.ox).toFloat(), (gy - sh.oy).toFloat(), null)
                    g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (y + anim.drop.toInt() - s.oy).toFloat(), null)
                }
            }
        }
    }

    private fun drawShots(g: Canvas, world: World) {
        for (i in 0 until world.shotCount) {
            val sh = world.shots[i]
            val x = px(sh.x); val y = py(sh.y)
            if (!inView(x, y, 30)) continue
            blob(g, x, y + 8, 3.5f, 40)
            fp.style = Paint.Style.FILL
            when (sh.kind) {
                EnemyShot.ARROW -> {
                    val l = 5f; val ca = cos(sh.rot); val sa = sin(sh.rot)
                    sp.color = 0xFFC9A66B.toInt(); sp.strokeWidth = 1f
                    g.drawLine(x - ca * l, y - sa * l, x + ca * l, y + sa * l, sp)
                    fp.color = 0xFFE9EEF7.toInt()
                    g.drawRect(x + ca * l - 1f, y + sa * l - 1f, x + ca * l + 1f, y + sa * l + 1f, fp)
                    fp.color = 0xFFD9443A.toInt()
                    g.drawRect(x - ca * l - 1f, y - sa * l - 0.5f, x - ca * l, y - sa * l + 0.5f, fp)
                }
                EnemyShot.WEB -> {
                    fp.color = 0xFFF2F0FA.toInt(); g.drawCircle(x.toFloat(), y.toFloat(), 3f, fp)
                    sp.color = 0xFF9A94B8.toInt(); sp.strokeWidth = 1f; g.drawCircle(x.toFloat(), y.toFloat(), 3f, sp)
                    g.drawLine(x - 3f, y.toFloat(), x + 3f, y.toFloat(), sp); g.drawLine(x.toFloat(), y - 3f, x.toFloat(), y + 3f, sp)
                }
                EnemyShot.CLEAVER -> {
                    g.save()
                    g.rotate(sh.rot * 57.29578f, x.toFloat(), y.toFloat())
                    fp.color = 0xFF86552F.toInt(); g.drawRect(x - 7f, y - 1f, x - 2f, y + 1f, fp)
                    fp.color = 0xFFDDE3EE.toInt(); g.drawRect(x - 2f, y - 3f, x + 6f, y + 3f, fp)
                    fp.color = 0xFF8C98AC.toInt(); g.drawRect(x - 2f, y + 1f, x + 6f, y + 3f, fp)
                    g.restore()
                }
                else -> {
                    fp.color = 0xFFEAD7FF.toInt(); g.drawCircle(x.toFloat(), y.toFloat(), 3f, fp)
                    fp.color = 0xFFB27CFF.toInt(); g.drawCircle(x.toFloat(), y.toFloat() + 1f, 2f, fp)
                    sp.color = 0xFF3A2866.toInt(); sp.strokeWidth = 1f; g.drawCircle(x.toFloat(), y.toFloat(), 3.5f, sp)
                }
            }
        }
    }

    private fun sprite(g: Canvas, anim: Anim, dir: Int, frame: Int, x: Int, y: Int, shadow: Boolean, shadowY: Int = y) {
        val s = anim.frame(dir, frame)
        val gy = y + anim.drop.toInt()
        if (shadow && cfg.shadowMode >= 2) {
            val sh = anim.shadow(dir, frame)
            g.drawBitmap(sh.bmp, (x - sh.ox).toFloat(), (shadowY + anim.drop.toInt() - sh.oy).toFloat(), null)
        }
        g.drawBitmap(s.bmp, (x - s.ox).toFloat(), (gy - s.oy).toFloat(), null)
    }

    // ------------------------------------------------------------ частицы

    private fun drawParticles(g: Canvas, world: World, additive: Boolean) {
        val fx = world.fx
        fp.style = Paint.Style.FILL
        for (i in 0 until fx.particleCount) {
            val p = fx.particles[i]
            val isAdd = p.kind == Particle.EMBER || p.kind == Particle.MOTE || p.kind == Particle.SPARK || p.kind == Particle.STAR || p.kind == Particle.SPARKLE
            if (isAdd != additive) continue
            val x = px(p.x)
            val y = py(p.y) - (p.z * PXU).toInt()
            if (!inView(x, y, 10)) continue
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val sz = (p.size * PXU)
            when (p.kind) {
                Particle.SPARK -> {
                    sp.color = (((255 * a).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    sp.strokeWidth = 1f
                    g.drawLine(x.toFloat(), y.toFloat(), x - p.vx * 0.022f * PXU, y - p.vy * 0.022f * PXU, sp)
                }
                Particle.STAR -> {
                    val r = (sz * (0.5f + 0.9f * a)).coerceAtLeast(1.5f)
                    fp.color = (((255 * a).toInt()) shl 24) or 0x00FFFFFF
                    g.drawRect(x - r, y - 0.5f, x + r, y + 0.5f, fp)
                    g.drawRect(x - 0.5f, y - r, x + 0.5f, y + r, fp)
                    fp.color = (((255 * a).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - 1f, y - 1f, x + 1f, y + 1f, fp)
                }
                Particle.SMOKE -> {
                    val r = (sz * (1.4f - a * 0.5f)).coerceAtLeast(1f)
                    fp.color = (((110 * a).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - r, y - r, x + r, y + r, fp)
                }
                Particle.EMBER -> {
                    val flick = 0.6f + 0.4f * sin(animT * 30f + p.x)
                    fp.color = (((255 * a * flick).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - 1f, y - 1f, x + 1f, y + 1f, fp)
                }
                Particle.MOTE -> {
                    val pulse = 0.55f + 0.45f * sin(animT * 2.2f + p.x * 0.7f)
                    fp.color = (((200 * pulse * a).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - 0.5f, y - 0.5f, x + 1f, y + 1f, fp)
                }
                Particle.SPARKLE -> {
                    val on = ((animT * 12f + p.x).toInt() and 1) == 0
                    if (on) {
                        fp.color = (((255 * a).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                        g.drawRect(x - 2f, y - 0.5f, x + 2f, y + 0.5f, fp)
                        g.drawRect(x - 0.5f, y - 2f, x + 0.5f, y + 2f, fp)
                    }
                }
                Particle.CHUNK -> {
                    val r = (sz * 0.5f).coerceAtLeast(1f)
                    if (p.z > 1f) { fp.color = 0x55140A28; g.drawRect(x - r, py(p.y) - 0.5f, x + r, py(p.y) + 0.5f, fp) }
                    fp.color = (((255 * minOf(1f, a * 3f)).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - r, y - r, x + r, y + r, fp)
                    fp.color = (((255 * minOf(1f, a * 3f)).toInt()) shl 24) or (PxShade.dark(p.color) and 0x00FFFFFF)
                    g.drawRect(x - r, y + r - 1f, x + r, y + r, fp)
                }
                Particle.COIN -> {
                    val spin = if (((animT * 14f + p.x).toInt() and 1) == 0) 1f else 0.5f
                    if (p.z > 1f) { fp.color = 0x55140A28; g.drawRect(x - 1f, py(p.y) - 0.5f, x + 1f, py(p.y) + 0.5f, fp) }
                    fp.color = (((255 * minOf(1f, a * 3f)).toInt()) shl 24) or 0x00F2B33D
                    g.drawRect(x - spin * 1.5f, y - 2f, x + spin * 1.5f, y + 2f, fp)
                    fp.color = (((255 * minOf(1f, a * 3f)).toInt()) shl 24) or 0x00FFE890
                    g.drawRect(x - spin * 0.5f, y - 1f, x + spin * 0.5f, y.toFloat(), fp)
                }
                Particle.LEAF -> {
                    fp.color = (((200 * a).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - 1.5f, y - 0.5f, x + 1.5f, y + 0.5f, fp)
                }
                else -> {
                    val r = (sz * (0.45f + 0.55f * a)).coerceAtLeast(0.8f)
                    fp.color = (((255 * minOf(1f, a * 2f)).toInt()) shl 24) or (p.color and 0x00FFFFFF)
                    g.drawRect(x - r, y - r, x + r, y + r, fp)
                }
            }
        }
    }

    // ------------------------------------------------------------ кроны деревьев (поверх, с прозрачностью)

    private fun drawCanopies(g: Canvas, world: World) {
        if (propCount == 0) return
        val pl = world.player
        val hx = px(pl.x); val hy = py(pl.y)
        val cn = SpriteBank.treeCanopy.body
        for (i in 0 until propCount) {
            if (propKind[i] != K_TREE) continue
            val x = propX[i]; val y = propY[i]
            if (!inView(x, y, 90)) continue
            val l = x - cn.ox; val t = y - cn.oy
            val hidden = hx > l && hx < l + cn.w && hy > t && hy < y + 4
            alphaPaint.alpha = if (hidden) 105 else 255
            g.drawBitmap(cn.bmp, l.toFloat(), t.toFloat(), alphaPaint)
        }
    }

    // ------------------------------------------------------------ свет и свечение

    private fun lighting(g: Canvas, world: World) {
        val fx = world.fx
        val pl = world.player
        post.beginLight(Biomes.MEADOW.ambient)
        var used = 0
        val max = cfg.lightCount
        val hx = px(pl.x).toFloat(); val hy = (py(pl.y) - 6).toFloat()
        post.light(hx, hy, 150f * PXU * 1.6f, 0xFFFFE2B0.toInt(), 0.95f)
        for (i in 0 until fx.lightCount) {
            if (used >= max) break
            val l = fx.lights[i]
            val t = (l.life / l.maxLife).coerceIn(0f, 1f)
            post.light(px(l.x).toFloat(), py(l.y).toFloat(), l.radius * PXU * 1.5f, l.color, l.intensity * t); used++
        }
        for (i in 0 until world.enemyCount) {
            if (used >= max) break
            val e = world.enemies[i]
            if (!e.alive || !e.elite) continue
            val y = py(e.y)
            if (!inView(px(e.x), y, 60)) continue
            post.light(px(e.x).toFloat(), y.toFloat(), 46f, 0xFFFFC94D.toInt(), 0.7f); used++
        }
        for (i in 0 until world.projectileCount) {
            if (used >= max) break
            val p = world.projectiles[i]
            if (p.kind == Projectile.BARREL && p.state == 1) { post.light(px(p.x).toFloat(), (py(p.y) - (p.z * PXU)), 34f, 0xFFFFA24D.toInt(), 0.85f); used++ }
            else if (p.kind == Projectile.RAVEN) { post.light(px(p.x).toFloat(), py(p.y).toFloat(), 22f, 0xFFB6A6FF.toInt(), 0.5f); used++ }
        }
        for (i in 0 until world.pickupCount) {
            if (used >= max) break
            val p = world.pickups[i]
            if (p.kind != Pickup.CHEST) continue
            post.light(px(p.x).toFloat(), py(p.y).toFloat(), 40f, 0xFFFFC94D.toInt(), 0.8f); used++
        }
        for (i in 0 until propCount) {
            if (used >= max) break
            if (propKind[i] != SpriteBank.P_LANTERN) continue
            val flick = 0.85f + 0.15f * sin(animT * 9f + i)
            post.light(propX[i].toFloat(), (propY[i] - 24).toFloat(), 95f, 0xFFFFC26A.toInt(), 0.95f * flick); used++
        }
        for (i in 0 until world.weaponCount) {
            val wi = world.weapons[i]
            if (wi.def.kind == WeaponKind.AURA) post.light(hx, hy, wi.stats.radius * PXU * 1.7f, if (wi.stats.healOnHit > 0f) 0xFF8EDDA9.toInt() else 0xFFFF8A3C.toInt(), 0.7f)
        }
        post.applyLight(g, cfg.lighting >= 2)
    }

    /** Всё, что светится само: аддитивные частицы, молнии, лучи, ореолы, пылинки. */
    private fun additive(g: Canvas, world: World) {
        val pl = world.player
        if (cfg.lighting >= 2) {
            post.glowOn(g, px(pl.x).toFloat(), (py(pl.y) - 6).toFloat(), 40f, 0xFFFFD9A0.toInt(), 24)
        }
        drawParticles(g, world, true)
        for (i in 0 until world.shotCount) { val sh = world.shots[i]; glowAt(g, px(sh.x), py(sh.y), if (sh.kind == EnemyShot.ORB) 7 else 5, sh.color, 150) }
        drawBolts(g, world)
        if (cfg.ambientMotes > 0) drawMotes(g, world)
        // светящиеся глаза в темноте не нужны — пикселей и так достаточно
    }

    private fun drawBolts(g: Canvas, world: World) {
        val fx = world.fx
        for (i in 0 until fx.boltCount) {
            val b = fx.bolts[i]
            if (b.count < 2) continue
            val a = (b.life / b.maxLife).coerceIn(0f, 1f)
            path.reset()
            path.moveTo(px(b.pts[0]).toFloat(), py(b.pts[1]).toFloat())
            for (kk in 1 until b.count) {
                val x0 = b.pts[(kk - 1) * 2]; val y0 = b.pts[(kk - 1) * 2 + 1]
                val x1 = b.pts[kk * 2]; val y1 = b.pts[kk * 2 + 1]
                if (!b.straight) {
                    for (s in 1..3) {
                        val t = s / 4f
                        path.lineTo(px(x0 + (x1 - x0) * t + rng.signed() * 14f).toFloat(), py(y0 + (y1 - y0) * t + rng.signed() * 14f).toFloat())
                    }
                }
                path.lineTo(px(x1).toFloat(), py(y1).toFloat())
            }
            val col = b.color and 0x00FFFFFF
            sp.xfermode = ADD
            sp.strokeCap = Paint.Cap.BUTT
            sp.color = ((120 * a).toInt() shl 24) or col
            sp.strokeWidth = (b.width * PXU * (if (b.straight) 1.2f else 0.7f)).coerceAtLeast(3f)
            g.drawPath(path, sp)
            sp.color = ((255 * a).toInt() shl 24) or col
            sp.strokeWidth = (b.width * PXU * (if (b.straight) 0.62f else 0.3f)).coerceAtLeast(1.5f)
            g.drawPath(path, sp)
            sp.color = ((255 * a).toInt() shl 24) or 0x00FFFFFF
            sp.strokeWidth = (b.width * PXU * (if (b.straight) 0.24f else 0.12f)).coerceAtLeast(1f)
            g.drawPath(path, sp)
            sp.xfermode = null
            // искры по ходу луча
            if (b.straight && cfg.preset.ordinal >= GfxPreset.MEDIUM.ordinal) {
                val steps = 8
                for (s in 0..steps) {
                    val t = s / steps.toFloat()
                    val x = b.pts[0] + (b.pts[2] - b.pts[0]) * t
                    val y = b.pts[1] + (b.pts[3] - b.pts[1]) * t
                    if (rng.chance(0.35f)) glowAt(g, px(x), py(y), 4, b.color, (150 * a).toInt())
                }
            }
        }
    }

    private fun drawMotes(g: Canvas, world: World) {
        val n = cfg.ambientMotes.coerceAtMost(MOTE_CAP)
        fp.style = Paint.Style.FILL
        for (i in 0 until n) {
            val depth = if (i and 1 == 0) 1.18f else 0.82f
            val fieldW = vw + 40f; val fieldH = vh + 40f
            var x = (moteU[i] * fieldW - camPxX * depth + animT * (4f + moteP[i])) % fieldW
            var y = (moteV[i] * fieldH - camPxY * depth - animT * (3f + moteP[i] * 0.5f) + sin(animT * 0.8f + moteP[i]) * 6f) % fieldH
            if (x < 0) x += fieldW
            if (y < 0) y += fieldH
            val pulse = 0.5f + 0.5f * sin(animT * 2.4f + moteP[i])
            val col = if (i % 3 == 0) 0xFFFFE680.toInt() else 0xFFBFFFB0.toInt()
            glowAt(g, (x - 20).toInt(), (y - 20).toInt(), 3, col, (60 + 120 * pulse).toInt())
            fp.color = ((140 + 110 * pulse).toInt() shl 24) or (col and 0x00FFFFFF)
            g.drawRect(x - 20f, y - 20f, x - 19f, y - 19f, fp)
        }
    }

    // ------------------------------------------------------------ числа и итоговый оверлей

    private fun drawNumbers(g: Canvas, world: World) {
        val fx = world.fx
        val n = minOf(fx.textCount, cfg.damageNumbers)
        for (i in 0 until n) {
            val t = fx.texts[i]
            if (t.kind == FloatText.REACTION) continue
            val a = (t.life / t.maxLife).coerceIn(0f, 1f)
            val x = px(t.x); val top = py(t.y) - (if (t.big) 5 else 3)
            if (!inView(x, top, 20)) continue
            val color: Int
            var sign = ' '
            when (t.kind) {
                FloatText.HEAL -> { color = 0xFF7CFF8A.toInt(); sign = '+' }
                FloatText.GOLD -> { color = 0xFFFFD54A.toInt(); sign = '+' }
                FloatText.PLAYER_HURT -> { color = 0xFFFF5252.toInt(); sign = '-' }
                else -> color = if (t.color == -1) 0xFFFFFFFF.toInt() else 0xFFFFE066.toInt()
            }
            font.draw(g, t.value, sign, x, top, color, if (t.big) 2 else 1, (255 * minOf(1f, a * 2.2f)).toInt())
        }
    }

    private fun overlay(c: Canvas, world: World) {
        val v = vignette
        if (v != null) {
            dst.set(0f, 0f, w.toFloat(), h.toFloat())
            c.drawBitmap(v, null, dst, overlayPaint)
        }
        val f = world.fx.flash
        if (f > 0.01f) {
            fp.style = Paint.Style.FILL
            fp.color = (((110 * f.coerceIn(0f, 1f)).toInt()) shl 24) or (world.fx.flashColor and 0x00FFFFFF)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fp)
        }
        drawReactionLabels(c, world)
    }

    /** Названия реакций словом над целью: в экранных координатах, чтобы читались чётко, а не пикселями 3×5. */
    private fun drawReactionLabels(c: Canvas, world: World) {
        val fx = world.fx
        for (i in 0 until fx.textCount) {
            val t = fx.texts[i]
            if (t.kind != FloatText.REACTION || t.value <= 0 || t.value >= Reactions.COUNT) continue
            val a = (t.life / t.maxLife).coerceIn(0f, 1f)
            val rise = (1f - a) * 12f * PXU * k
            val sx = screenX(world, t.x); val sy = screenY(world, t.y) - rise
            if (sx < -200f || sx > w + 200f || sy < -80f || sy > h + 80f) continue
            val col = Reactions.colors[t.value]
            val al = (255 * minOf(1f, a * 2.5f)).toInt()
            labelTx.setup(10.5f * k, (al shl 24) or (col and 0x00FFFFFF), outline = (al shl 24) or (Palette.OUTLINE and 0x00FFFFFF))
            labelTx.draw(c, Reactions.names[t.value], sx, sy)
        }
    }

    /** Экранные координаты точки мира (для HUD: плашки, стрелки у края). */
    fun screenX(world: World, x: Float): Float = ((x - world.camX) * PXU + vw * 0.5f) * k
    fun screenY(world: World, y: Float): Float = ((y - world.camY) * PXU + vh * 0.5f) * k

    /** Экранные координаты героя (для HUD). */
    fun heroScreenX(world: World) = ((world.player.x - world.camX) * PXU + vw * 0.5f) * k
    fun heroScreenY(world: World) = ((world.player.y - world.camY) * PXU + vh * 0.5f) * k

    private object PxShade {
        fun dark(c: Int): Int = com.tavernrush.game.render.pixel.PxColor.mul(c, 0.6f)
    }

    companion object {
        /** Пикселей кадра на единицу мира. */
        const val PXU = 0.5f
        private const val SORT_CAP = 900
        private const val BUCKETS = 1400
        private const val PROP_CAP = 120
        private const val MOTE_CAP = 48
        private const val PCELL = 240
        private const val K_ENEMY = 0
        private const val K_HERO = 1
        private const val K_PROP = 2
        private const val K_ORBIT = 3
        private const val K_MATE = 4
        private const val K_WILD = 5
        private const val K_WALL = 6
        private const val K_CONDUIT = 7
        private const val K_TREE = 100
        private val ADD = PorterDuffXfermode(PorterDuff.Mode.ADD)
        /** Кадров ходьбы в секунду по виду врага (индекс — EnemyLook.ordinal). */
        private val WALK_FPS = floatArrayOf(14f, 9f, 11f, 6.5f, 7f, 9f, 10f, 12f, 7f, 7f, 14f, 7f, 11f, 5f, 6f)
        private val AFFIX_COL = intArrayOf(0, 0xFFFFE066.toInt(), 0xFFB8C4D8.toInt(), 0xFFE0447A.toInt(), 0xFFFF7A2E.toInt(), 0xFF7FD3FF.toInt())
    }
}
