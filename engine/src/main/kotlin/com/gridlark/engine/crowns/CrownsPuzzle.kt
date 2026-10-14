package com.gridlark.engine.crowns

import com.gridlark.engine.Difficulty
import kotlin.math.abs

/**
 * Crowns: an N×N grid split into N coloured regions.
 * Place exactly one crown in every row, every column and every region.
 * Crowns may not touch each other, not even diagonally.
 *
 * @property regions region id (0 until size) for each cell, row-major.
 * @property solution solution[row] = column holding that row's crown.
 */
class CrownsPuzzle(
    val size: Int,
    val regions: IntArray,
    val solution: IntArray,
    val difficulty: Difficulty,
) {
    init {
        require(size >= 4) { "size must be >= 4" }
        require(regions.size == size * size) { "regions must have size*size entries" }
        require(solution.size == size) { "solution must have one entry per row" }
    }

    fun regionAt(row: Int, col: Int): Int = regions[row * size + col]

    fun isCrownInSolution(index: Int): Boolean = solution[index / size] == index % size
}

/** Rule checks used by the UI. Pure functions over a player's crown placement. */
object CrownsRules {

    private fun clash(size: Int, regions: IntArray, a: Int, b: Int): Boolean {
        val ar = a / size; val ac = a % size
        val br = b / size; val bc = b % size
        return ar == br || ac == bc || regions[a] == regions[b] ||
            (abs(ar - br) <= 1 && abs(ac - bc) <= 1)
    }

    /** Returns a flag per cell: true when that crown breaks a rule with another crown. */
    fun conflicts(size: Int, regions: IntArray, crowns: BooleanArray): BooleanArray {
        val bad = BooleanArray(size * size)
        val placed = crowns.indices.filter { crowns[it] }
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                if (clash(size, regions, placed[i], placed[j])) {
                    bad[placed[i]] = true
                    bad[placed[j]] = true
                }
            }
        }
        return bad
    }

    /** True when the placement is a complete, valid solution. */
    fun isSolved(size: Int, regions: IntArray, crowns: BooleanArray): Boolean {
        if (crowns.count { it } != size) return false
        return conflicts(size, regions, crowns).none { it }
    }

    /**
     * Cells that can no longer hold a crown because of the crowns already placed
     * (same row, column, region, or touching). Used for the optional auto-mark assist.
     */
    fun blockedBy(size: Int, regions: IntArray, crowns: BooleanArray): BooleanArray {
        val blocked = BooleanArray(size * size)
        for (i in crowns.indices) if (crowns[i]) {
            for (j in 0 until size * size) {
                if (j != i && clash(size, regions, i, j)) blocked[j] = true
            }
        }
        return blocked
    }
}
