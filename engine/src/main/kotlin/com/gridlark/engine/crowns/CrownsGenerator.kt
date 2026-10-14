package com.gridlark.engine.crowns

import com.gridlark.engine.Difficulty
import com.gridlark.engine.Rng
import kotlin.math.abs

object CrownsGenerator {

    /** Grid size used for each difficulty. */
    fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 6
        Difficulty.MEDIUM -> 7
        Difficulty.HARD -> 8
        Difficulty.EXPERT -> 9
    }

    private fun targetTier(difficulty: Difficulty): IntRange = when (difficulty) {
        Difficulty.EASY -> 1..2
        Difficulty.MEDIUM -> 3..4
        Difficulty.HARD -> 4..4
        Difficulty.EXPERT -> 5..5
    }

    fun generate(difficulty: Difficulty, seed: Long, size: Int = sizeFor(difficulty)): CrownsPuzzle {
        require(size in 5..10) { "size must be 5..10" }
        val rng = Rng(seed)
        val target = targetTier(difficulty)
        var attempt = 0
        while (true) {
            val solution = randomSolution(size, rng)
            val regions = growRegions(size, solution, rng)
            if (!makeUnique(size, regions, solution, rng)) continue
            attempt++
            val outcome = CrownsSolver(size, regions).solve()
            if (outcome.solved && accept(outcome.maxTier, target, attempt)) {
                return CrownsPuzzle(size, regions, solution, difficulty)
            }
        }
    }

    /** Strict at first, then progressively relaxed so generation always finishes quickly. */
    private fun accept(tier: Int, target: IntRange, attempt: Int): Boolean = when {
        attempt < 30 -> tier in target
        attempt < 90 -> tier in (target.first - 1)..(target.last + 1)
        else -> true
    }

    /**
     * Repairs [regions] in place until [solution] is the only solution. Each round finds another
     * solution and moves one of its crown cells (never a real crown) into a neighbouring region,
     * which breaks that alternative while keeping every region connected.
     */
    internal fun makeUnique(n: Int, regions: IntArray, solution: IntArray, rng: Rng): Boolean {
        repeat(n * n * 2) {
            val alt = findOtherSolution(n, regions, solution) ?: return true
            val candidates = (0 until n).filter { alt[it] != solution[it] }.map { it * n + alt[it] }.toMutableList()
            rng.shuffle(candidates)
            var moved = false
            for (x in candidates) {
                val from = regions[x]
                val targets = orthogonal(n, x).map { regions[it] }.filter { it != from }.distinct().toMutableList()
                if (targets.isEmpty() || !staysConnected(n, regions, from, x)) continue
                rng.shuffle(targets)
                regions[x] = targets[0]
                moved = true
                break
            }
            if (!moved) return false
        }
        return findOtherSolution(n, regions, solution) == null
    }

    private fun orthogonal(n: Int, cell: Int): List<Int> {
        val r = cell / n; val c = cell % n
        val out = ArrayList<Int>(4)
        if (r > 0) out.add(cell - n)
        if (r < n - 1) out.add(cell + n)
        if (c > 0) out.add(cell - 1)
        if (c < n - 1) out.add(cell + 1)
        return out
    }

    /** Whether region [g] stays in one piece after removing [removed]. */
    private fun staysConnected(n: Int, regions: IntArray, g: Int, removed: Int): Boolean {
        val cells = (0 until n * n).filter { regions[it] == g && it != removed }
        if (cells.isEmpty()) return false
        val seen = BooleanArray(n * n)
        val stack = ArrayDeque<Int>()
        stack.add(cells[0]); seen[cells[0]] = true
        var count = 0
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            count++
            for (o in orthogonal(n, c)) {
                if (!seen[o] && o != removed && regions[o] == g) { seen[o] = true; stack.add(o) }
            }
        }
        return count == cells.size
    }

    /** Any valid crown placement other than [solution], or null if there is none. */
    internal fun findOtherSolution(n: Int, regions: IntArray, solution: IntArray): IntArray? {
        val cols = IntArray(n)
        val usedCol = BooleanArray(n)
        val usedReg = BooleanArray(n)
        fun rec(row: Int): Boolean {
            if (row == n) return !cols.contentEquals(solution)
            for (c in 0 until n) {
                if (usedCol[c]) continue
                if (row > 0 && abs(cols[row - 1] - c) <= 1) continue
                val g = regions[row * n + c]
                if (usedReg[g]) continue
                usedCol[c] = true; usedReg[g] = true; cols[row] = c
                if (rec(row + 1)) return true
                usedCol[c] = false; usedReg[g] = false
            }
            return false
        }
        return if (rec(0)) cols.copyOf() else null
    }

    /** A random placement of one crown per row and column with no two crowns touching. */
    internal fun randomSolution(n: Int, rng: Rng): IntArray {
        val cols = IntArray(n) { -1 }
        val used = BooleanArray(n)
        fun rec(row: Int): Boolean {
            if (row == n) return true
            val order = rng.permutation(n)
            for (c in order) {
                if (used[c]) continue
                if (row > 0 && abs(cols[row - 1] - c) <= 1) continue
                used[c] = true; cols[row] = c
                if (rec(row + 1)) return true
                used[c] = false
            }
            cols[row] = -1
            return false
        }
        check(rec(0)) { "no crown placement exists for n=$n" }
        return cols
    }

    /**
     * Grows one region from each crown with random, uneven speeds, then relabels the
     * regions so colours are not tied to row order.
     */
    internal fun growRegions(n: Int, solution: IntArray, rng: Rng): IntArray {
        val cells = n * n
        val region = IntArray(cells) { -1 }
        for (r in 0 until n) region[r * n + solution[r]] = r
        val weight = DoubleArray(n) { 0.35 + rng.nextDouble() * 1.9 }
        var unassigned = cells - n
        val frontier = IntArray(cells)
        val canGrow = BooleanArray(n)
        while (unassigned > 0) {
            var total = 0.0
            for (g in 0 until n) {
                canGrow[g] = hasFrontier(n, region, g)
                if (canGrow[g]) total += weight[g]
            }
            var pick = rng.nextDouble() * total
            var chosen = -1
            for (g in 0 until n) if (canGrow[g]) {
                chosen = g
                pick -= weight[g]
                if (pick <= 0) break
            }
            var fc = 0
            for (i in 0 until cells) {
                if (region[i] == -1 && adjacentToRegion(n, region, i, chosen)) frontier[fc++] = i
            }
            region[frontier[rng.nextInt(fc)]] = chosen
            unassigned--
        }
        val relabel = rng.permutation(n)
        return IntArray(cells) { relabel[region[it]] }
    }

    private fun hasFrontier(n: Int, region: IntArray, g: Int): Boolean {
        for (i in region.indices) if (region[i] == -1 && adjacentToRegion(n, region, i, g)) return true
        return false
    }

    private fun adjacentToRegion(n: Int, region: IntArray, cell: Int, g: Int): Boolean {
        val r = cell / n; val c = cell % n
        return (r > 0 && region[cell - n] == g) || (r < n - 1 && region[cell + n] == g) ||
            (c > 0 && region[cell - 1] == g) || (c < n - 1 && region[cell + 1] == g)
    }
}
