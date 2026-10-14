package com.tavernrush.game.systems

import kotlin.math.floor

/**
 * Пространственная сетка врагов вокруг героя (мир бесконечный, сетка «едет» за героем).
 * Перестраивается раз в тик сортировкой подсчётом: O(n + ячейки), без аллокаций.
 * Враги вне сетки (далеко за экраном) в запросы не попадают — для них это и не нужно.
 */
class SpatialHash(private val w: World, val cell: Float = 80f, val cols: Int = 36, val rows: Int = 52) {
    private val cellCount = cols * rows
    private val start = IntArray(cellCount + 1)
    private val cursor = IntArray(cellCount)
    private val items = IntArray(World.MAX_ENEMIES)
    private val itemCell = IntArray(World.MAX_ENEMIES)
    private var ox = 0f
    private var oy = 0f
    private val inv = 1f / cell

    fun rebuild(cx: Float, cy: Float) {
        ox = cx - cols * cell * 0.5f
        oy = cy - rows * cell * 0.5f
        java.util.Arrays.fill(cursor, 0)
        val es = w.enemies
        val n = w.enemyCount
        for (i in 0 until n) {
            val e = es[i]
            var c = -1
            if (e.alive) {
                val fx = (e.x - ox) * inv
                val fy = (e.y - oy) * inv
                if (fx >= 0f && fy >= 0f) {
                    val gx = fx.toInt()
                    val gy = fy.toInt()
                    if (gx < cols && gy < rows) {
                        c = gy * cols + gx
                        cursor[c]++
                    }
                }
            }
            itemCell[i] = c
        }
        var sum = 0
        for (c in 0 until cellCount) {
            start[c] = sum
            sum += cursor[c]
            cursor[c] = start[c]
        }
        start[cellCount] = sum
        for (i in 0 until n) {
            val c = itemCell[i]
            if (c >= 0) {
                items[cursor[c]] = i
                cursor[c]++
            }
        }
    }

    private var qx0 = 0
    private var qx1 = 0
    private var qy0 = 0
    private var qy1 = 0

    private fun range(x: Float, y: Float, r: Float): Boolean {
        val m = r + MAX_ENEMY_RADIUS
        qx0 = floor((x - m - ox) * inv).toInt()
        qx1 = floor((x + m - ox) * inv).toInt()
        qy0 = floor((y - m - oy) * inv).toInt()
        qy1 = floor((y + m - oy) * inv).toInt()
        if (qx1 < 0 || qy1 < 0 || qx0 >= cols || qy0 >= rows) return false
        if (qx0 < 0) qx0 = 0
        if (qy0 < 0) qy0 = 0
        if (qx1 >= cols) qx1 = cols - 1
        if (qy1 >= rows) qy1 = rows - 1
        return true
    }

    /** Индексы живых врагов, чей круг пересекает круг (x, y, r). Возвращает количество. */
    fun query(x: Float, y: Float, r: Float, out: IntArray): Int {
        if (!range(x, y, r)) return 0
        var n = 0
        val es = w.enemies
        for (gy in qy0..qy1) {
            for (gx in qx0..qx1) {
                val c = gy * cols + gx
                for (k in start[c] until start[c + 1]) {
                    val i = items[k]
                    val e = es[i]
                    if (!e.alive) continue
                    val dx = e.x - x
                    val dy = e.y - y
                    val rr = r + e.radius
                    if (dx * dx + dy * dy <= rr * rr) {
                        out[n++] = i
                        if (n == out.size) return n
                    }
                }
            }
        }
        return n
    }

    /** Ближайший живой (и уже проявившийся) враг в радиусе r или -1. */
    fun nearest(x: Float, y: Float, r: Float): Int = nearestExcluding(x, y, r, null, 0)

    /** Ближайший враг, uid которого нет в списке exclude[0 until exCount]. */
    fun nearestExcluding(x: Float, y: Float, r: Float, exclude: IntArray?, exCount: Int): Int {
        if (!range(x, y, r)) return -1
        var best = -1
        var bestD = r * r
        val es = w.enemies
        for (gy in qy0..qy1) {
            for (gx in qx0..qx1) {
                val c = gy * cols + gx
                for (k in start[c] until start[c + 1]) {
                    val i = items[k]
                    val e = es[i]
                    if (!e.alive || e.spawnFade > 0f) continue
                    val dx = e.x - x
                    val dy = e.y - y
                    val d = dx * dx + dy * dy
                    if (d < bestD) {
                        if (exclude != null) {
                            var skip = false
                            for (q in 0 until exCount) if (exclude[q] == e.uid) { skip = true; break }
                            if (skip) continue
                        }
                        bestD = d
                        best = i
                    }
                }
            }
        }
        return best
    }

    companion object {
        /** Не меньше радиуса самого крупного врага (босс-людоед 54, именная немезида до 1,6× от 30 → 48..., запас). */
        const val MAX_ENEMY_RADIUS = 62f
    }
}
