package com.gridlark.engine.duo

import com.gridlark.engine.Difficulty
import com.gridlark.engine.Rng

/**
 * Duo: fill every cell with a sun or a moon.
 *  - Each row and column holds the same number of suns and moons.
 *  - No more than two of the same symbol may sit next to each other in a row or column.
 *  - "=" between two cells means they match; "×" means they differ.
 *
 * Cell values: [EMPTY] = 0, [SUN] = 1, [MOON] = 2.
 */
class DuoPuzzle(
    val size: Int,
    val givens: IntArray,
    val edges: List<DuoEdge>,
    val solution: IntArray,
    val difficulty: Difficulty,
) {
    init {
        require(size % 2 == 0 && size >= 4) { "size must be even and >= 4" }
        require(givens.size == size * size && solution.size == size * size)
    }

    companion object {
        const val EMPTY = 0
        const val SUN = 1
        const val MOON = 2
        fun opposite(v: Int): Int = if (v == SUN) MOON else SUN
    }
}

/** Relation between two orthogonally adjacent cells, with [a] < [b]. */
data class DuoEdge(val a: Int, val b: Int, val same: Boolean)

object DuoRules {

    /**
     * Flags every cell that takes part in a broken rule: three in a line, too many of a
     * symbol in a line, or a violated "=" / "×" sign.
     */
    fun conflicts(size: Int, values: IntArray, edges: List<DuoEdge>): BooleanArray {
        val bad = BooleanArray(size * size)
        val half = size / 2
        for (line in 0 until size) {
            for (horizontal in booleanArrayOf(true, false)) {
                fun idx(k: Int) = if (horizontal) line * size + k else k * size + line
                for (k in 0 until size - 2) {
                    val v = values[idx(k)]
                    if (v != DuoPuzzle.EMPTY && v == values[idx(k + 1)] && v == values[idx(k + 2)]) {
                        bad[idx(k)] = true; bad[idx(k + 1)] = true; bad[idx(k + 2)] = true
                    }
                }
                for (sym in intArrayOf(DuoPuzzle.SUN, DuoPuzzle.MOON)) {
                    var count = 0
                    for (k in 0 until size) if (values[idx(k)] == sym) count++
                    if (count > half) for (k in 0 until size) if (values[idx(k)] == sym) bad[idx(k)] = true
                }
            }
        }
        for (e in edges) {
            val va = values[e.a]; val vb = values[e.b]
            if (va != DuoPuzzle.EMPTY && vb != DuoPuzzle.EMPTY && (va == vb) != e.same) {
                bad[e.a] = true; bad[e.b] = true
            }
        }
        return bad
    }

    fun isSolved(size: Int, values: IntArray, edges: List<DuoEdge>): Boolean =
        values.none { it == DuoPuzzle.EMPTY } && conflicts(size, values, edges).none { it }
}

/**
 * Logical solver. Tiers: 1 = signs and "no three" patterns, 2 = line counts,
 * 3 = one-step look-ahead (a value here breaks a rule after propagation).
 */
class DuoSolver(private val n: Int, private val edges: List<DuoEdge>) {

    class Outcome(val solved: Boolean, val maxTier: Int, val grid: IntArray)

    private val half = n / 2
    private val lines: Array<IntArray> = Array(2 * n) { i ->
        if (i < n) IntArray(n) { k -> i * n + k } else IntArray(n) { k -> k * n + (i - n) }
    }

    fun solve(givens: IntArray, maxTierAllowed: Int = 3): Outcome {
        val g = givens.copyOf()
        var maxTier = 0
        while (true) {
            if (g.none { it == DuoPuzzle.EMPTY }) {
                return Outcome(valid(g), maxTier, g)
            }
            var st = basic(g)
            if (st < 0) return Outcome(false, maxTier, g)
            if (st > 0) { maxTier = maxOf(maxTier, 1); continue }
            if (maxTierAllowed < 2) return Outcome(false, maxTier, g)
            st = counts(g)
            if (st < 0) return Outcome(false, maxTier, g)
            if (st > 0) { maxTier = maxOf(maxTier, 2); continue }
            if (maxTierAllowed < 3) return Outcome(false, maxTier, g)
            st = probe(g)
            if (st > 0) { maxTier = maxOf(maxTier, 3); continue }
            return Outcome(false, maxTier, g)
        }
    }

    private fun valid(g: IntArray): Boolean = DuoRules.conflicts(n, g, edges).none { it }

    private fun set(g: IntArray, cell: Int, v: Int): Int {
        val cur = g[cell]
        if (cur == v) return 0
        if (cur != DuoPuzzle.EMPTY) return -1
        g[cell] = v
        return 1
    }

    /** Signs and "no three in a row". */
    private fun basic(g: IntArray): Int {
        var progress = 0
        for (e in edges) {
            val va = g[e.a]; val vb = g[e.b]
            if (va != DuoPuzzle.EMPTY && vb != DuoPuzzle.EMPTY) {
                if ((va == vb) != e.same) return -1
            } else if (va != DuoPuzzle.EMPTY) {
                if (set(g, e.b, if (e.same) va else DuoPuzzle.opposite(va)) < 0) return -1
                progress = 1
            } else if (vb != DuoPuzzle.EMPTY) {
                if (set(g, e.a, if (e.same) vb else DuoPuzzle.opposite(vb)) < 0) return -1
                progress = 1
            }
        }
        for (line in lines) {
            for (k in 0 until n - 2) {
                val a = line[k]; val b = line[k + 1]; val c = line[k + 2]
                val va = g[a]; val vb = g[b]; val vc = g[c]
                val filled = (if (va != 0) 1 else 0) + (if (vb != 0) 1 else 0) + (if (vc != 0) 1 else 0)
                if (filled == 3) {
                    if (va == vb && vb == vc) return -1
                } else if (filled == 2) {
                    when {
                        va == 0 && vb == vc -> { if (set(g, a, DuoPuzzle.opposite(vb)) < 0) return -1; progress = 1 }
                        vb == 0 && va == vc -> { if (set(g, b, DuoPuzzle.opposite(va)) < 0) return -1; progress = 1 }
                        vc == 0 && va == vb -> { if (set(g, c, DuoPuzzle.opposite(va)) < 0) return -1; progress = 1 }
                    }
                }
            }
        }
        return progress
    }

    /** A line that already has half of one symbol gets the other symbol everywhere else. */
    private fun counts(g: IntArray): Int {
        var progress = 0
        for (line in lines) {
            var suns = 0; var moons = 0
            for (c in line) when (g[c]) {
                DuoPuzzle.SUN -> suns++
                DuoPuzzle.MOON -> moons++
            }
            if (suns > half || moons > half) return -1
            if (suns + moons == n) continue
            val fill = when {
                suns == half -> DuoPuzzle.MOON
                moons == half -> DuoPuzzle.SUN
                else -> continue
            }
            for (c in line) if (g[c] == DuoPuzzle.EMPTY) { g[c] = fill; progress = 1 }
        }
        return progress
    }

    private fun propagateOk(g: IntArray): Boolean {
        while (true) {
            var st = basic(g)
            if (st < 0) return false
            if (st > 0) continue
            st = counts(g)
            if (st < 0) return false
            if (st > 0) continue
            if (g.none { it == DuoPuzzle.EMPTY }) return valid(g)
            return true
        }
    }

    private fun probe(g: IntArray): Int {
        for (cell in g.indices) {
            if (g[cell] != DuoPuzzle.EMPTY) continue
            for (v in intArrayOf(DuoPuzzle.SUN, DuoPuzzle.MOON)) {
                val copy = g.copyOf()
                copy[cell] = v
                if (!propagateOk(copy)) {
                    g[cell] = DuoPuzzle.opposite(v)
                    return 1
                }
            }
        }
        return 0
    }

    companion object {
        /** Exhaustive solution count (stops at [limit]). Used by tests. */
        fun countSolutions(n: Int, givens: IntArray, edges: List<DuoEdge>, limit: Int = 2): Int {
            val g = givens.copyOf()
            var count = 0
            fun rec(i: Int) {
                if (count >= limit) return
                if (i == n * n) {
                    if (DuoRules.isSolved(n, g, edges)) count++
                    return
                }
                if (g[i] != DuoPuzzle.EMPTY) {
                    if (!DuoRules.conflicts(n, g, edges)[i]) rec(i + 1)
                    return
                }
                for (v in intArrayOf(DuoPuzzle.SUN, DuoPuzzle.MOON)) {
                    g[i] = v
                    if (!DuoRules.conflicts(n, g, edges)[i]) rec(i + 1)
                    g[i] = DuoPuzzle.EMPTY
                }
            }
            rec(0)
            return count
        }
    }
}

object DuoGenerator {

    fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY, Difficulty.MEDIUM -> 6
        Difficulty.HARD -> 8
        Difficulty.EXPERT -> 10
    }

    private fun tierCap(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 2
        else -> 3
    }

    fun generate(difficulty: Difficulty, seed: Long, size: Int = sizeFor(difficulty)): DuoPuzzle {
        val rng = Rng(seed)
        val n = size
        val solution = randomSolution(n, rng)
        val cap = tierCap(difficulty)

        val allEdges = ArrayList<DuoEdge>()
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            if (c + 1 < n) allEdges.add(DuoEdge(i, i + 1, solution[i] == solution[i + 1]))
            if (r + 1 < n) allEdges.add(DuoEdge(i, i + n, solution[i] == solution[i + n]))
        }
        rng.shuffle(allEdges)
        val edgeShare = when (difficulty) {
            Difficulty.EASY -> 0.18
            Difficulty.MEDIUM -> 0.22
            Difficulty.HARD -> 0.16
            Difficulty.EXPERT -> 0.12
        }
        val edges = ArrayList(allEdges.take((allEdges.size * edgeShare).toInt().coerceAtLeast(2)))
        val givens = solution.copyOf()

        fun solvable(gv: IntArray, es: List<DuoEdge>): Boolean =
            DuoSolver(n, es).solve(gv, cap).solved

        check(solvable(givens, edges))

        // Remove givens first, so the signs carry part of the logic.
        val order = rng.permutation(n * n)
        for (cell in order) {
            val keep = givens[cell]
            givens[cell] = DuoPuzzle.EMPTY
            if (!solvable(givens, edges)) givens[cell] = keep
        }
        // Then drop signs that are no longer needed.
        val edgeOrder = ArrayList(edges)
        rng.shuffle(edgeOrder)
        for (e in edgeOrder) {
            edges.remove(e)
            if (!solvable(givens, edges)) edges.add(e)
        }
        // Easy puzzles get a few cells back so the opening moves are obvious.
        if (difficulty == Difficulty.EASY) {
            val empties = (0 until n * n).filter { givens[it] == DuoPuzzle.EMPTY }.toMutableList()
            rng.shuffle(empties)
            for (cell in empties.take(n / 2)) givens[cell] = solution[cell]
        }
        val sortedEdges = edges.sortedWith(compareBy({ it.a }, { it.b }))
        return DuoPuzzle(n, givens, sortedEdges, solution, difficulty)
    }

    internal fun randomSolution(n: Int, rng: Rng): IntArray {
        val g = IntArray(n * n)
        val half = n / 2
        fun ok(i: Int): Boolean {
            val r = i / n; val c = i % n; val v = g[i]
            if (c >= 2 && g[i - 1] == v && g[i - 2] == v) return false
            if (r >= 2 && g[i - n] == v && g[i - 2 * n] == v) return false
            var rowCount = 0
            for (k in 0..c) if (g[r * n + k] == v) rowCount++
            if (rowCount > half) return false
            var colCount = 0
            for (k in 0..r) if (g[k * n + c] == v) colCount++
            if (colCount > half) return false
            return true
        }
        fun rec(i: Int): Boolean {
            if (i == n * n) return true
            val first = if (rng.nextInt(2) == 0) DuoPuzzle.SUN else DuoPuzzle.MOON
            for (v in intArrayOf(first, DuoPuzzle.opposite(first))) {
                g[i] = v
                if (ok(i) && rec(i + 1)) return true
            }
            g[i] = DuoPuzzle.EMPTY
            return false
        }
        check(rec(0)) { "no Duo grid for n=$n" }
        return g
    }
}
