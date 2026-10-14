package com.gridlark.engine.sudoku

import com.gridlark.engine.Difficulty
import com.gridlark.engine.Rng

/**
 * Sudoku with rectangular boxes: 6×6 (boxes 2×3) or 9×9 (boxes 3×3).
 * Values are 1..size, 0 = empty.
 */
class SudokuPuzzle(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val givens: IntArray,
    val solution: IntArray,
    val difficulty: Difficulty,
) {
    init {
        require(boxRows * boxCols == size)
        require(givens.size == size * size && solution.size == size * size)
    }

    fun boxOf(cell: Int): Int {
        val r = cell / size; val c = cell % size
        return (r / boxRows) * (size / boxCols) + (c / boxCols)
    }
}

object SudokuRules {

    fun conflicts(p: SudokuPuzzle, values: IntArray): BooleanArray {
        val n = p.size
        val bad = BooleanArray(n * n)
        for (a in 0 until n * n) {
            val v = values[a]
            if (v == 0) continue
            for (b in a + 1 until n * n) {
                if (values[b] != v) continue
                if (a / n == b / n || a % n == b % n || p.boxOf(a) == p.boxOf(b)) {
                    bad[a] = true; bad[b] = true
                }
            }
        }
        return bad
    }

    fun isSolved(p: SudokuPuzzle, values: IntArray): Boolean =
        values.none { it == 0 } && conflicts(p, values).none { it }

    /** Candidates still possible for each empty cell, as bitmasks (bit v = value v). */
    fun candidates(p: SudokuPuzzle, values: IntArray): IntArray {
        val n = p.size
        val full = ((1 shl (n + 1)) - 1) and 1.inv()
        return IntArray(n * n) { cell ->
            if (values[cell] != 0) 0 else {
                var m = full
                for (o in 0 until n * n) {
                    if (o == cell || values[o] == 0) continue
                    if (o / n == cell / n || o % n == cell % n || p.boxOf(o) == p.boxOf(cell)) {
                        m = m and (1 shl values[o]).inv()
                    }
                }
                m
            }
        }
    }
}

/**
 * Candidate-based logical solver.
 * Tiers: 1 = naked singles, 2 = hidden singles, 3 = locked candidates and naked pairs,
 * 4 = one-step look-ahead.
 */
class SudokuSolver(private val n: Int, private val boxRows: Int, private val boxCols: Int) {

    class Outcome(val solved: Boolean, val maxTier: Int, val grid: IntArray)

    private val cells = n * n
    private val units: Array<IntArray>
    private val unitsOf: Array<IntArray>
    private val peers: Array<IntArray>

    init {
        val list = ArrayList<IntArray>()
        for (r in 0 until n) list.add(IntArray(n) { r * n + it })
        for (c in 0 until n) list.add(IntArray(n) { it * n + c })
        val boxesAcross = n / boxCols
        for (b in 0 until n) {
            val br = (b / boxesAcross) * boxRows
            val bc = (b % boxesAcross) * boxCols
            list.add(IntArray(n) { k -> (br + k / boxCols) * n + bc + k % boxCols })
        }
        units = list.toTypedArray()
        unitsOf = Array(cells) { cell -> units.indices.filter { cell in units[it] }.toIntArray() }
        peers = Array(cells) { cell ->
            unitsOf[cell].flatMap { units[it].toList() }.filter { it != cell }.distinct().toIntArray()
        }
    }

    private val fullMask = ((1 shl (n + 1)) - 1) and 1.inv()

    fun solve(givens: IntArray, maxTierAllowed: Int = 4): Outcome {
        val values = givens.copyOf()
        val cand = IntArray(cells) { if (values[it] == 0) fullMask else 0 }
        for (c in 0 until cells) if (values[c] != 0) {
            if (!eliminateFromPeers(values, cand, c)) return Outcome(false, 0, values)
        }
        var maxTier = 0
        while (true) {
            if (values.none { it == 0 }) return Outcome(true, maxTier, values)
            var st = nakedSingles(values, cand)
            if (st < 0) return Outcome(false, maxTier, values)
            if (st > 0) { maxTier = maxOf(maxTier, 1); continue }
            if (maxTierAllowed < 2) return Outcome(false, maxTier, values)
            st = hiddenSingles(values, cand)
            if (st < 0) return Outcome(false, maxTier, values)
            if (st > 0) { maxTier = maxOf(maxTier, 2); continue }
            if (maxTierAllowed < 3) return Outcome(false, maxTier, values)
            st = lockedAndPairs(values, cand)
            if (st < 0) return Outcome(false, maxTier, values)
            if (st > 0) { maxTier = maxOf(maxTier, 3); continue }
            if (maxTierAllowed < 4) return Outcome(false, maxTier, values)
            st = probe(values, cand)
            if (st > 0) { maxTier = maxOf(maxTier, 4); continue }
            return Outcome(false, maxTier, values)
        }
    }

    private fun assign(values: IntArray, cand: IntArray, cell: Int, v: Int): Boolean {
        if (cand[cell] and (1 shl v) == 0) return false
        values[cell] = v
        cand[cell] = 0
        return eliminateFromPeers(values, cand, cell)
    }

    private fun eliminateFromPeers(values: IntArray, cand: IntArray, cell: Int): Boolean {
        val bit = 1 shl values[cell]
        for (p in peers[cell]) {
            if (values[p] == values[cell]) return false
            if (values[p] == 0) {
                cand[p] = cand[p] and bit.inv()
                if (cand[p] == 0) return false
            }
        }
        return true
    }

    private fun nakedSingles(values: IntArray, cand: IntArray): Int {
        var progress = 0
        for (c in 0 until cells) {
            if (values[c] != 0) continue
            val m = cand[c]
            if (m == 0) return -1
            if (Integer.bitCount(m) == 1) {
                if (!assign(values, cand, c, Integer.numberOfTrailingZeros(m))) return -1
                progress = 1
            }
        }
        return progress
    }

    private fun hiddenSingles(values: IntArray, cand: IntArray): Int {
        for (unit in units) {
            for (v in 1..n) {
                var where = -1; var count = 0; var placed = false
                for (c in unit) {
                    if (values[c] == v) { placed = true; break }
                    if (values[c] == 0 && cand[c] and (1 shl v) != 0) { count++; where = c }
                }
                if (placed) continue
                if (count == 0) return -1
                if (count == 1) {
                    return if (assign(values, cand, where, v)) 1 else -1
                }
            }
        }
        return 0
    }

    /** Pointing/claiming (locked candidates) and naked pairs. */
    private fun lockedAndPairs(values: IntArray, cand: IntArray): Int {
        // Locked candidates: if v's spots in unit A all lie in unit B, remove v from the rest of B.
        for (a in units) for (v in 1..n) {
            val spots = a.filter { values[it] == 0 && cand[it] and (1 shl v) != 0 }
            if (spots.size < 2) continue
            val shared = unitsOf[spots[0]].filter { u -> units[u] !== a && spots.all { it in units[u] } }
            for (u in shared) {
                var changed = false
                for (c in units[u]) {
                    if (c in spots || values[c] != 0) continue
                    if (cand[c] and (1 shl v) != 0) {
                        cand[c] = cand[c] and (1 shl v).inv()
                        if (cand[c] == 0) return -1
                        changed = true
                    }
                }
                if (changed) return 1
            }
        }
        // Naked pairs.
        for (unit in units) {
            val open = unit.filter { values[it] == 0 }
            for (i in open.indices) {
                val m = cand[open[i]]
                if (Integer.bitCount(m) != 2) continue
                for (j in i + 1 until open.size) {
                    if (cand[open[j]] != m) continue
                    var changed = false
                    for (c in open) {
                        if (c == open[i] || c == open[j]) continue
                        if (cand[c] and m != 0) {
                            cand[c] = cand[c] and m.inv()
                            if (cand[c] == 0) return -1
                            changed = true
                        }
                    }
                    if (changed) return 1
                }
            }
        }
        return 0
    }

    private fun propagateOk(values: IntArray, cand: IntArray): Boolean {
        while (true) {
            if (values.none { it == 0 }) return true
            var st = nakedSingles(values, cand)
            if (st < 0) return false
            if (st > 0) continue
            st = hiddenSingles(values, cand)
            if (st < 0) return false
            if (st > 0) continue
            return true
        }
    }

    private fun probe(values: IntArray, cand: IntArray): Int {
        for (c in 0 until cells) {
            if (values[c] != 0) continue
            var m = cand[c]
            while (m != 0) {
                val v = Integer.numberOfTrailingZeros(m)
                m = m and (m - 1)
                val vv = values.copyOf(); val cc = cand.copyOf()
                if (!assign(vv, cc, c, v) || !propagateOk(vv, cc)) {
                    cand[c] = cand[c] and (1 shl v).inv()
                    if (cand[c] == 0) return -1
                    return 1
                }
            }
        }
        return 0
    }

    companion object {
        fun countSolutions(p: SudokuPuzzle, limit: Int = 2): Int {
            val n = p.size
            val g = p.givens.copyOf()
            var count = 0
            fun ok(cell: Int, v: Int): Boolean {
                for (o in 0 until n * n) {
                    if (o == cell || g[o] != v) continue
                    if (o / n == cell / n || o % n == cell % n || p.boxOf(o) == p.boxOf(cell)) return false
                }
                return true
            }
            fun rec(i: Int) {
                if (count >= limit) return
                if (i == n * n) { count++; return }
                if (g[i] != 0) { rec(i + 1); return }
                for (v in 1..n) {
                    if (ok(i, v)) { g[i] = v; rec(i + 1); g[i] = 0 }
                }
            }
            rec(0)
            return count
        }
    }
}

object SudokuGenerator {

    /** EASY plays on 6×6; everything else is classic 9×9. */
    fun shapeFor(difficulty: Difficulty): Triple<Int, Int, Int> = when (difficulty) {
        Difficulty.EASY -> Triple(6, 2, 3)
        else -> Triple(9, 3, 3)
    }

    private fun tierCap(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 2
        Difficulty.MEDIUM -> 2
        Difficulty.HARD -> 3
        Difficulty.EXPERT -> 4
    }

    fun generate(difficulty: Difficulty, seed: Long, mini: Boolean = false): SudokuPuzzle {
        val (n, br, bc) = if (mini) Triple(6, 2, 3) else shapeFor(difficulty)
        val rng = Rng(seed)
        val cap = tierCap(difficulty)
        val solver = SudokuSolver(n, br, bc)
        var best: SudokuPuzzle? = null
        repeat(6) {
            val solution = randomGrid(n, br, bc, rng)
            val givens = solution.copyOf()
            val order = rng.permutation(n * n)
            for (cell in order) {
                val keep = givens[cell]
                givens[cell] = 0
                if (!solver.solve(givens, cap).solved) givens[cell] = keep
            }
            // Easier boards keep a few more clues.
            val extra = when (difficulty) {
                Difficulty.EASY -> n / 2
                Difficulty.MEDIUM -> 4
                else -> 0
            }
            val empties = (0 until n * n).filter { givens[it] == 0 }.toMutableList()
            rng.shuffle(empties)
            for (cell in empties.take(extra)) givens[cell] = solution[cell]

            val tier = solver.solve(givens, cap).maxTier
            val puzzle = SudokuPuzzle(n, br, bc, givens, solution, difficulty)
            // Prefer a board that actually needs the hardest allowed technique.
            if (tier >= cap || difficulty == Difficulty.EASY || difficulty == Difficulty.MEDIUM) return puzzle
            if (best == null) best = puzzle
        }
        return best!!
    }

    internal fun randomGrid(n: Int, br: Int, bc: Int, rng: Rng): IntArray {
        val g = IntArray(n * n)
        val boxesAcross = n / bc
        fun box(cell: Int) = ((cell / n) / br) * boxesAcross + ((cell % n) / bc)
        fun ok(cell: Int, v: Int): Boolean {
            val r = cell / n; val c = cell % n
            for (k in 0 until n) {
                if (g[r * n + k] == v || g[k * n + c] == v) return false
            }
            for (o in 0 until n * n) if (g[o] == v && box(o) == box(cell)) return false
            return true
        }
        fun rec(i: Int): Boolean {
            if (i == n * n) return true
            for (v in rng.permutation(n)) {
                val value = v + 1
                if (ok(i, value)) {
                    g[i] = value
                    if (rec(i + 1)) return true
                    g[i] = 0
                }
            }
            return false
        }
        check(rec(0))
        return g
    }
}
