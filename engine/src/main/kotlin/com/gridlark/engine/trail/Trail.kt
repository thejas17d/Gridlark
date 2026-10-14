package com.gridlark.engine.trail

import com.gridlark.engine.Difficulty
import com.gridlark.engine.Rng

/**
 * Trail: draw one continuous line that visits every cell exactly once,
 * starting at 1 and passing through the numbered cells in order. Lines can't cross walls.
 *
 * @property checkpoints checkpoints[k] = cell holding the number k + 1.
 * @property wallRight wallRight[cell] = wall between cell and the cell to its right.
 * @property wallDown wallDown[cell] = wall between cell and the cell below it.
 * @property solution the unique path, as cell indices in order.
 */
class TrailPuzzle(
    val size: Int,
    val checkpoints: IntArray,
    val wallRight: BooleanArray,
    val wallDown: BooleanArray,
    val solution: IntArray,
    val difficulty: Difficulty,
) {
    init {
        require(size in 3..8) { "size must be 3..8" }
        require(checkpoints.size >= 2) { "need a start and an end" }
        require(solution.size == size * size)
    }

    /** number at a cell (1-based) or 0. */
    val numberAt: IntArray = IntArray(size * size).also { arr ->
        checkpoints.forEachIndexed { k, cell -> arr[cell] = k + 1 }
    }

    fun blocked(a: Int, b: Int): Boolean = TrailRules.blocked(size, wallRight, wallDown, a, b)
}

object TrailRules {

    fun adjacent(size: Int, a: Int, b: Int): Boolean {
        val ar = a / size; val ac = a % size; val br = b / size; val bc = b % size
        return (ar == br && kotlin.math.abs(ac - bc) == 1) || (ac == bc && kotlin.math.abs(ar - br) == 1)
    }

    fun blocked(size: Int, wallRight: BooleanArray, wallDown: BooleanArray, a: Int, b: Int): Boolean {
        val lo = minOf(a, b); val hi = maxOf(a, b)
        return if (hi - lo == 1) wallRight[lo] else wallDown[lo]
    }

    /**
     * Whether the player may extend [path] onto [cell]. Enforces adjacency, walls,
     * no revisits, and visiting numbers in order.
     */
    fun canExtend(p: TrailPuzzle, path: List<Int>, cell: Int): Boolean {
        if (path.isEmpty()) return cell == p.checkpoints[0]
        val last = path.last()
        if (!adjacent(p.size, last, cell) || p.blocked(last, cell)) return false
        if (cell in path) return false
        val num = p.numberAt[cell]
        if (num != 0) {
            val reached = path.maxOf { p.numberAt[it] }
            if (num != reached + 1) return false
            if (num == p.checkpoints.size && path.size + 1 != p.size * p.size) return false
        }
        return true
    }

    fun isSolved(p: TrailPuzzle, path: List<Int>): Boolean {
        if (path.size != p.size * p.size) return false
        if (path.first() != p.checkpoints[0] || path.last() != p.checkpoints.last()) return false
        if (path.toSet().size != path.size) return false
        var next = 1
        for (i in path.indices) {
            if (i > 0 && (!adjacent(p.size, path[i - 1], path[i]) || p.blocked(path[i - 1], path[i]))) return false
            val num = p.numberAt[path[i]]
            if (num != 0) {
                if (num != next) return false
                next++
            }
        }
        return next == p.checkpoints.size + 1
    }
}

/**
 * Exhaustive search with strong pruning, counting solutions up to a limit.
 * The grid (at most 8×8 = 64 cells) is held in a Long bitboard so the dead-end and
 * connectivity checks at every step cost only a few machine instructions.
 */
internal class TrailSolver(
    private val n: Int,
    private val numberAt: IntArray,
    private val lastNumber: Int,
    wallRight: BooleanArray,
    wallDown: BooleanArray,
    private val nodeLimit: Long = 150_000,
) {
    class Result(val count: Int, val firstOther: IntArray?, val aborted: Boolean)

    private val total = n * n
    private val all: Long = if (total == 64) -1L else (1L shl total) - 1
    private val canRight: Long
    private val canLeft: Long
    private val canDown: Long
    private val canUp: Long
    private val neighbors: Array<IntArray>

    init {
        require(total <= 64) { "Trail solver supports up to 8x8" }
        var r = 0L; var l = 0L; var d = 0L; var u = 0L
        for (cell in 0 until total) {
            val row = cell / n; val col = cell % n
            if (col + 1 < n && !wallRight[cell]) r = r or (1L shl cell)
            if (col > 0 && !wallRight[cell - 1]) l = l or (1L shl cell)
            if (row + 1 < n && !wallDown[cell]) d = d or (1L shl cell)
            if (row > 0 && !wallDown[cell - n]) u = u or (1L shl cell)
        }
        canRight = r; canLeft = l; canDown = d; canUp = u
        neighbors = Array(total) { cell ->
            val list = ArrayList<Int>(4)
            val bit = 1L shl cell
            if (canRight and bit != 0L) list.add(cell + 1)
            if (canLeft and bit != 0L) list.add(cell - 1)
            if (canDown and bit != 0L) list.add(cell + n)
            if (canUp and bit != 0L) list.add(cell - n)
            list.toIntArray()
        }
    }

    private fun expand(s: Long): Long =
        ((s and canRight) shl 1) or ((s and canLeft) ushr 1) or ((s and canDown) shl n) or ((s and canUp) ushr n)

    private val endCell = numberAt.indexOfFirst { it == lastNumber }
    private var visited = 0L
    private val path = IntArray(total)
    private var nodes = 0L
    private var count = 0
    private var aborted = false
    private var other: IntArray? = null
    private var avoid: IntArray? = null
    private var limit = 2

    /** Counts solutions; [known] (if given) is excluded when reporting [Result.firstOther]. */
    fun count(limit: Int = 2, known: IntArray? = null): Result {
        this.limit = limit
        this.avoid = known
        val start = numberAt.indexOfFirst { it == 1 }
        visited = 1L shl start
        path[0] = start
        if (total == 1) return Result(1, null, false)
        dfs(start, 1, 2)
        return Result(count, other, aborted)
    }

    private fun dfs(head: Int, depth: Int, nextNumber: Int) {
        if (++nodes > nodeLimit) { aborted = true; return }
        if (depth == total) {
            if (head == endCell) {
                count++
                if (other == null && (avoid == null || !path.contentEquals(avoid!!))) other = path.copyOf()
            }
            return
        }
        for (nb in neighbors[head]) {
            val bit = 1L shl nb
            if (visited and bit != 0L) continue
            val num = numberAt[nb]
            if (num != 0 && num != nextNumber) continue
            if (nb == endCell && depth + 1 != total) continue
            visited = visited or bit
            path[depth] = nb
            if (feasible(head, nb, depth + 1)) dfs(nb, depth + 1, if (num != 0) nextNumber + 1 else nextNumber)
            visited = visited and bit.inv()
            if (count >= limit || aborted) return
        }
    }

    private fun feasible(oldHead: Int, newHead: Int, depth: Int): Boolean {
        if (depth == total) return true
        val open = all and visited.inv()
        val headBit = 1L shl newHead
        // Only cells next to the old head lost a usable neighbour; check them for dead ends.
        for (c in neighbors[oldHead]) {
            val cb = 1L shl c
            if (open and cb == 0L) continue
            val free = java.lang.Long.bitCount(expand(cb) and (open or headBit))
            if (c == endCell) { if (free < 1) return false } else if (free < 2) return false
        }
        // Every open cell must still be reachable from the head.
        var reach = expand(headBit) and open
        if (reach == 0L) return false
        while (true) {
            val next = (reach or expand(reach)) and open
            if (next == reach) break
            reach = next
        }
        return reach == open
    }
}

object TrailGenerator {

    fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 6
        Difficulty.HARD -> 7
        Difficulty.EXPERT -> 8
    }

    fun generate(difficulty: Difficulty, seed: Long, size: Int = sizeFor(difficulty)): TrailPuzzle {
        val rng = Rng(seed)
        while (true) {
            val result = attempt(difficulty, size, rng)
            if (result != null) return result
        }
    }

    private fun attempt(difficulty: Difficulty, n: Int, rng: Rng): TrailPuzzle? {
        val total = n * n
        val path = randomHamiltonianPath(n, rng)
        val indexOf = IntArray(total).also { arr -> path.forEachIndexed { i, c -> arr[c] = i } }

        // Walls on edges the solution never uses.
        val wallRight = BooleanArray(total)
        val wallDown = BooleanArray(total)
        val wallShare = when (difficulty) {
            Difficulty.EASY -> 0.0
            Difficulty.MEDIUM -> 0.06
            Difficulty.HARD -> 0.09
            Difficulty.EXPERT -> 0.10
        }
        for (cell in 0 until total) {
            val r = cell / n; val c = cell % n
            if (c + 1 < n && kotlin.math.abs(indexOf[cell] - indexOf[cell + 1]) != 1 && rng.chance(wallShare)) wallRight[cell] = true
            if (r + 1 < n && kotlin.math.abs(indexOf[cell] - indexOf[cell + n]) != 1 && rng.chance(wallShare)) wallDown[cell] = true
        }

        // Checkpoints are positions along the path; 0 and total-1 are always present.
        // Start dense (every third step), so the first uniqueness check is cheap.
        val cps = sortedSetOf(0, total - 1)
        var idx = 1 + rng.nextInt(3)
        while (idx < total - 1) {
            cps.add(idx)
            idx += 2 + rng.nextInt(3)
        }

        fun numbersFor(set: Set<Int>): IntArray {
            val arr = IntArray(total)
            set.sorted().forEachIndexed { k, i -> arr[path[i]] = k + 1 }
            return arr
        }
        fun check(set: Set<Int>): TrailSolver.Result =
            TrailSolver(n, numbersFor(set), set.size, wallRight, wallDown).count(2, path)

        // Make sure the path is the only solution, adding checkpoints where an alternative diverges.
        var guard = 0
        while (true) {
            if (++guard > total) return null
            val res = check(cps)
            if (res.aborted) return null
            if (res.count == 1) break
            val alt = res.firstOther ?: return null
            val diverge = (1 until total).first { alt[it] != path[it] }
            if (!cps.add(diverge.coerceAtMost(total - 2))) return null
        }

        // Then remove checkpoints that aren't needed, down to a floor that sets the difficulty.
        val floor = when (difficulty) {
            Difficulty.EASY -> n + 3
            Difficulty.MEDIUM -> n + 1
            Difficulty.HARD -> 0
            Difficulty.EXPERT -> 0
        }
        val interior = cps.filter { it != 0 && it != total - 1 }.toMutableList()
        rng.shuffle(interior)
        for (i in interior) {
            if (cps.size <= floor) break
            cps.remove(i)
            val res = check(cps)
            if (res.aborted || res.count != 1) cps.add(i)
        }

        val sorted = cps.sorted()
        val checkpoints = IntArray(sorted.size) { path[sorted[it]] }
        return TrailPuzzle(n, checkpoints, wallRight, wallDown, path, difficulty)
    }

    /**
     * Random Hamiltonian path by "backbite" moves, starting from a serpentine path.
     * Each move keeps the path Hamiltonian, so the result is always valid.
     */
    internal fun randomHamiltonianPath(n: Int, rng: Rng): IntArray {
        val total = n * n
        val path = IntArray(total)
        var k = 0
        for (r in 0 until n) {
            if (r % 2 == 0) for (c in 0 until n) path[k++] = r * n + c
            else for (c in n - 1 downTo 0) path[k++] = r * n + c
        }
        val pos = IntArray(total).also { arr -> path.forEachIndexed { i, c -> arr[c] = i } }
        fun reverse(from: Int, to: Int) {
            var i = from; var j = to
            while (i < j) {
                val t = path[i]; path[i] = path[j]; path[j] = t
                pos[path[i]] = i; pos[path[j]] = j
                i++; j--
            }
        }
        val nb = IntArray(4)
        repeat(total * 60) {
            val atHead = rng.nextInt(2) == 0
            val end = if (atHead) path[0] else path[total - 1]
            val r = end / n; val c = end % n
            var cnt = 0
            if (r > 0) nb[cnt++] = end - n
            if (r < n - 1) nb[cnt++] = end + n
            if (c > 0) nb[cnt++] = end - 1
            if (c < n - 1) nb[cnt++] = end + 1
            val x = nb[rng.nextInt(cnt)]
            val j = pos[x]
            if (atHead) {
                if (j == 1) return@repeat
                reverse(0, j - 1)
            } else {
                if (j == total - 2) return@repeat
                reverse(j + 1, total - 1)
            }
        }
        return path
    }
}
