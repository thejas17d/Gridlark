package com.gridlark.engine.crowns

import kotlin.math.abs

/**
 * Human-style logical solver. It only makes deductions a careful person could make,
 * so a puzzle it solves completely has exactly one solution and never needs guessing.
 *
 * Tiers (used for difficulty):
 *  1 = singles (a row, column or region with one free cell)
 *  2 = confinement (a region's free cells all sit in one row/column, or vice versa)
 *  3 = pigeonhole sets (k regions squeezed into k rows/columns)
 *  4 = shallow look-ahead (a crown here would empty some row, column or region)
 *  5 = deep look-ahead (a crown here leads to a contradiction after propagation)
 */
class CrownsSolver(private val n: Int, private val regions: IntArray) {

    class Outcome(val solved: Boolean, val maxTier: Int, val grid: IntArray)

    private val cellCount = n * n

    init {
        require(regions.size == cellCount) { "regions size mismatch" }
        require(regions.all { it in 0 until n }) { "region ids must be in 0 until n" }
    }

    /** unit type 0 = rows, 1 = columns, 2 = regions; unitCells[type][index] = cells. */
    private val unitCells: Array<Array<IntArray>> = arrayOf(
        Array(n) { r -> IntArray(n) { c -> r * n + c } },
        Array(n) { c -> IntArray(n) { r -> r * n + c } },
        Array(n) { g -> (0 until cellCount).filter { regions[it] == g }.toIntArray() },
    )

    init {
        require(unitCells[2].all { it.isNotEmpty() }) { "every region needs at least one cell" }
    }

    private fun unitOf(type: Int, cell: Int): Int = when (type) {
        0 -> cell / n
        1 -> cell % n
        else -> regions[cell]
    }

    private val peers: Array<IntArray> = Array(cellCount) { cell ->
        val r = cell / n; val c = cell % n
        (0 until cellCount).filter { o ->
            if (o == cell) return@filter false
            val orow = o / n; val ocol = o % n
            orow == r || ocol == c || regions[o] == regions[cell] ||
                (abs(orow - r) <= 1 && abs(ocol - c) <= 1)
        }.toIntArray()
    }

    fun solve(maxTierAllowed: Int = 5): Outcome = solveFrom(IntArray(cellCount), maxTierAllowed)

    /**
     * Continues solving from a partial grid of UNKNOWN / EMPTY / CROWN values.
     * Used for hints: the first deduction made from the player's position.
     */
    fun solveFrom(start: IntArray, maxTierAllowed: Int = 5): Outcome {
        val g = start.copyOf()
        var maxTier = 0
        while (true) {
            if (crownCount(g) == n) return Outcome(true, maxTier, g)
            var st = singles(g)
            if (st < 0) return Outcome(false, maxTier, g)
            if (st > 0) { maxTier = maxOf(maxTier, 1); continue }
            if (maxTierAllowed < 2) return Outcome(false, maxTier, g)
            st = subsets(g, 1)
            if (st < 0) return Outcome(false, maxTier, g)
            if (st > 0) { maxTier = maxOf(maxTier, 2); continue }
            if (maxTierAllowed < 3) return Outcome(false, maxTier, g)
            st = subsets(g, n)
            if (st < 0) return Outcome(false, maxTier, g)
            if (st > 0) { maxTier = maxOf(maxTier, 3); continue }
            if (maxTierAllowed < 4) return Outcome(false, maxTier, g)
            st = probe(g, deep = false)
            if (st > 0) { maxTier = maxOf(maxTier, 4); continue }
            if (maxTierAllowed < 5) return Outcome(false, maxTier, g)
            st = probe(g, deep = true)
            if (st > 0) { maxTier = maxOf(maxTier, 5); continue }
            return Outcome(false, maxTier, g)
        }
    }

    private fun crownCount(g: IntArray): Int = g.count { it == CROWN }

    /** Returns false if placing a crown at [cell] contradicts the grid. */
    private fun place(g: IntArray, cell: Int): Boolean {
        if (g[cell] == EMPTY) return false
        g[cell] = CROWN
        for (p in peers[cell]) {
            if (g[p] == CROWN) return false
            g[p] = EMPTY
        }
        return true
    }

    /** Non-probing propagation to a fixed point. -1 contradiction, 0 otherwise. */
    private fun propagate(g: IntArray): Int {
        while (true) {
            if (crownCount(g) == n) return 0
            var st = singles(g)
            if (st < 0) return -1
            if (st > 0) continue
            st = subsets(g, 1)
            if (st < 0) return -1
            if (st > 0) continue
            st = subsets(g, n)
            if (st < 0) return -1
            if (st > 0) continue
            return 0
        }
    }

    private fun singles(g: IntArray): Int {
        var progress = 0
        for (type in 0..2) for (cells in unitCells[type]) {
            var crowns = 0; var unknowns = 0; var last = -1
            for (c in cells) when (g[c]) {
                CROWN -> crowns++
                UNKNOWN -> { unknowns++; last = c }
            }
            if (crowns > 1) return -1
            if (crowns == 1) continue
            if (unknowns == 0) return -1
            if (unknowns == 1) {
                if (!place(g, last)) return -1
                progress = 1
            }
        }
        return progress
    }

    private val unionScratch = IntArray(1 shl n)

    /**
     * Pigeonhole rule between two unit types A and B. If k unfilled A-units have all their
     * free cells inside exactly k B-units, those B-units' crowns all belong to the A-units,
     * so every other free cell in those B-units is empty.
     *
     * With maxK == 1 only single units are tried (tier 2). Otherwise sets of size 2..m/2 are
     * tried; a larger set is never needed because its complement in the other direction
     * gives the same eliminations.
     */
    private fun subsets(g: IntArray, maxK: Int): Int {
        val open = IntArray(n)
        val masks = IntArray(n)
        for (a in 0..2) for (b in 0..2) {
            if (a == b) continue
            var m = 0
            for (u in 0 until n) {
                val cells = unitCells[a][u]
                var hasCrown = false
                var mask = 0
                for (c in cells) when (g[c]) {
                    CROWN -> hasCrown = true
                    UNKNOWN -> mask = mask or (1 shl unitOf(b, c))
                }
                if (hasCrown) continue
                if (mask == 0) return -1
                open[m] = u; masks[m] = mask; m++
            }
            if (m <= 1) continue
            if (maxK == 1) {
                for (i in 0 until m) {
                    if (Integer.bitCount(masks[i]) == 1) {
                        val st = eliminate(g, a, b, masks[i], 1 shl open[i])
                        if (st != 0) return st
                    }
                }
                continue
            }
            val half = m / 2
            if (half < 2) continue
            unionScratch[0] = 0
            for (sub in 1 until (1 shl m)) {
                val low = Integer.numberOfTrailingZeros(sub)
                val union = unionScratch[sub and (sub - 1)] or masks[low]
                unionScratch[sub] = union
                val k = Integer.bitCount(sub)
                if (k < 2 || k > half) continue
                val pc = Integer.bitCount(union)
                if (pc < k) return -1
                if (pc != k) continue
                var members = 0
                for (i in 0 until m) if (sub and (1 shl i) != 0) members = members or (1 shl open[i])
                val st = eliminate(g, a, b, union, members)
                if (st != 0) return st
            }
        }
        return 0
    }

    /** Empties free cells in the B-units of [unionB] whose A-unit is not in [membersA]. */
    private fun eliminate(g: IntArray, a: Int, b: Int, unionB: Int, membersA: Int): Int {
        var changed = false
        var rest = unionB
        while (rest != 0) {
            val bu = Integer.numberOfTrailingZeros(rest)
            rest = rest and (rest - 1)
            for (c in unitCells[b][bu]) {
                if (g[c] == UNKNOWN && (membersA and (1 shl unitOf(a, c))) == 0) {
                    g[c] = EMPTY
                    changed = true
                }
            }
        }
        return if (changed) 1 else 0
    }

    private fun probe(g: IntArray, deep: Boolean): Int {
        for (cell in 0 until cellCount) {
            if (g[cell] != UNKNOWN) continue
            val copy = g.copyOf()
            val ok = place(copy, cell) && if (deep) propagate(copy) >= 0 else everyOpenUnitHasRoom(copy)
            if (!ok) {
                g[cell] = EMPTY
                return 1
            }
        }
        return 0
    }

    private fun everyOpenUnitHasRoom(g: IntArray): Boolean {
        for (type in 0..2) for (cells in unitCells[type]) {
            var hasCrown = false; var hasUnknown = false
            for (c in cells) when (g[c]) {
                CROWN -> hasCrown = true
                UNKNOWN -> hasUnknown = true
            }
            if (!hasCrown && !hasUnknown) return false
        }
        return true
    }

    companion object {
        const val UNKNOWN = 0
        const val EMPTY = 1
        const val CROWN = 2

        /** Exhaustive solution count (stops at [limit]). Used by tests to double-check uniqueness. */
        fun countSolutions(n: Int, regions: IntArray, limit: Int = 2): Int {
            val usedCol = BooleanArray(n)
            val usedReg = BooleanArray(n)
            var count = 0
            fun rec(row: Int, prevCol: Int) {
                if (count >= limit) return
                if (row == n) { count++; return }
                for (c in 0 until n) {
                    if (usedCol[c]) continue
                    if (prevCol >= 0 && abs(prevCol - c) <= 1) continue
                    val g = regions[row * n + c]
                    if (usedReg[g]) continue
                    usedCol[c] = true; usedReg[g] = true
                    rec(row + 1, c)
                    usedCol[c] = false; usedReg[g] = false
                }
            }
            rec(0, -1)
            return count
        }
    }
}
