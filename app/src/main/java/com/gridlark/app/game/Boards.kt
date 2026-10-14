package com.gridlark.app.game

import com.gridlark.app.data.AnyPuzzle
import com.gridlark.engine.crowns.CrownsPuzzle
import com.gridlark.engine.crowns.CrownsRules
import com.gridlark.engine.duo.DuoPuzzle
import com.gridlark.engine.duo.DuoRules
import com.gridlark.engine.sudoku.SudokuPuzzle
import com.gridlark.engine.sudoku.SudokuRules
import com.gridlark.engine.trail.TrailPuzzle
import com.gridlark.engine.trail.TrailRules
import kotlinx.serialization.Serializable

/** What gets written to the database. Two plain int lists cover every game. */
@Serializable
data class SavedBoard(val a: List<Int>, val b: List<Int> = emptyList())

/** Result of a hint: the new board, a sentence for the player, and the cell to spotlight. */
data class Hint(val board: BoardState, val message: String, val cell: Int)

/** Immutable player state for one puzzle. Every move returns a new instance. */
sealed interface BoardState {
    val solved: Boolean
    /** 0..1, for the progress hairline on the Today list. */
    val progress: Float
    fun save(): SavedBoard
    /** Null when the board is already solved. */
    fun hint(): Hint?

    companion object {
        fun fresh(puzzle: AnyPuzzle): BoardState = when (puzzle) {
            is AnyPuzzle.Crowns -> CrownsState(puzzle.p, IntArray(puzzle.p.size * puzzle.p.size))
            is AnyPuzzle.Duo -> DuoState(puzzle.p, puzzle.p.givens.copyOf())
            is AnyPuzzle.Trail -> TrailState(puzzle.p, emptyList())
            is AnyPuzzle.Sudoku -> SudokuState(puzzle.p, puzzle.p.givens.copyOf(), IntArray(puzzle.p.givens.size))
        }

        /** Restores a saved board, falling back to a fresh one if the data doesn't fit the puzzle. */
        fun restore(puzzle: AnyPuzzle, saved: SavedBoard?): BoardState {
            if (saved == null) return fresh(puzzle)
            return runCatching {
                when (puzzle) {
                    is AnyPuzzle.Crowns -> {
                        val n = puzzle.p.size * puzzle.p.size
                        require(saved.a.size == n && saved.a.all { it in 0..2 })
                        CrownsState(puzzle.p, saved.a.toIntArray())
                    }
                    is AnyPuzzle.Duo -> {
                        val g = puzzle.p.givens
                        require(saved.a.size == g.size && saved.a.all { it in 0..2 })
                        val values = saved.a.toIntArray()
                        for (i in g.indices) if (g[i] != DuoPuzzle.EMPTY) values[i] = g[i]
                        DuoState(puzzle.p, values)
                    }
                    is AnyPuzzle.Trail -> {
                        var path = emptyList<Int>()
                        for (cell in saved.a) {
                            require(TrailRules.canExtend(puzzle.p, path, cell))
                            path = path + cell
                        }
                        TrailState(puzzle.p, path)
                    }
                    is AnyPuzzle.Sudoku -> {
                        val g = puzzle.p.givens
                        val size = puzzle.p.size
                        require(saved.a.size == g.size && saved.b.size == g.size)
                        require(saved.a.all { it in 0..size })
                        val values = saved.a.toIntArray()
                        for (i in g.indices) if (g[i] != 0) values[i] = g[i]
                        SudokuState(puzzle.p, values, saved.b.toIntArray())
                    }
                }
            }.getOrElse { fresh(puzzle) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Crowns

class CrownsState(val puzzle: CrownsPuzzle, val marks: IntArray) : BoardState {
    val size get() = puzzle.size
    private val crowns: BooleanArray by lazy { BooleanArray(marks.size) { marks[it] == CROWN } }
    val conflicts: BooleanArray by lazy { CrownsRules.conflicts(size, puzzle.regions, crowns) }
    override val solved: Boolean by lazy { CrownsRules.isSolved(size, puzzle.regions, crowns) }
    override val progress: Float get() = crowns.count { it }.toFloat() / size
    val crownCount: Int get() = crowns.count { it }

    /** Tap: empty → × → crown → empty. */
    fun cycle(cell: Int): CrownsState = with(cell, (marks[cell] + 1) % 3)

    fun with(cell: Int, mark: Int): CrownsState {
        if (marks[cell] == mark) return this
        val m = marks.copyOf(); m[cell] = mark
        return CrownsState(puzzle, m)
    }

    /** Drag-marking: sets every cell in [cells] that currently holds [from] to [to]. */
    fun withAll(cells: Collection<Int>, from: Int, to: Int): CrownsState {
        val m = marks.copyOf()
        var changed = false
        for (c in cells) if (m[c] == from) { m[c] = to; changed = true }
        return if (changed) CrownsState(puzzle, m) else this
    }

    override fun save() = SavedBoard(marks.toList())

    override fun hint(): Hint? {
        if (solved) return null
        val n = size
        for (i in marks.indices) {
            if (marks[i] == CROWN && !puzzle.isCrownInSolution(i)) {
                return Hint(with(i, EMPTY), "This crown can't stay here.", i)
            }
        }
        // Rows still missing their crown; reveal the one in the smallest region (the most visible logic).
        val regionSize = IntArray(n).also { s -> puzzle.regions.forEach { s[it]++ } }
        val target = (0 until n)
            .map { r -> r * n + puzzle.solution[r] }
            .filter { marks[it] != CROWN }
            .minByOrNull { regionSize[puzzle.regions[it]] }
            ?: return null
        return Hint(with(target, CROWN), "A crown belongs here.", target)
    }

    companion object {
        const val EMPTY = 0
        const val CROSS = 1
        const val CROWN = 2
    }
}

// ---------------------------------------------------------------------------------------------
// Sun & Moon

class DuoState(val puzzle: DuoPuzzle, val values: IntArray) : BoardState {
    val size get() = puzzle.size
    val conflicts: BooleanArray by lazy { DuoRules.conflicts(size, values, puzzle.edges) }
    override val solved: Boolean by lazy { DuoRules.isSolved(size, values, puzzle.edges) }
    override val progress: Float
        get() {
            val open = puzzle.givens.count { it == DuoPuzzle.EMPTY }
            if (open == 0) return 1f
            val filled = values.indices.count { puzzle.givens[it] == DuoPuzzle.EMPTY && values[it] != DuoPuzzle.EMPTY }
            return filled.toFloat() / open
        }

    fun isGiven(cell: Int) = puzzle.givens[cell] != DuoPuzzle.EMPTY

    /** Tap: empty → sun → moon → empty. Givens never change. */
    fun cycle(cell: Int): DuoState {
        if (isGiven(cell)) return this
        val v = IntArray(values.size) { values[it] }
        v[cell] = (v[cell] + 1) % 3
        return DuoState(puzzle, v)
    }

    override fun save() = SavedBoard(values.toList())

    override fun hint(): Hint? {
        if (solved) return null
        for (i in values.indices) {
            if (!isGiven(i) && values[i] != DuoPuzzle.EMPTY && values[i] != puzzle.solution[i]) {
                return Hint(set(i, puzzle.solution[i]), "This one should be a ${name(puzzle.solution[i])}.", i)
            }
        }
        val n = size
        val target = values.indices.filter { values[it] == DuoPuzzle.EMPTY }.maxByOrNull { cell ->
            val r = cell / n; val c = cell % n
            var score = 0
            if (r > 0 && values[cell - n] != 0) score++
            if (r < n - 1 && values[cell + n] != 0) score++
            if (c > 0 && values[cell - 1] != 0) score++
            if (c < n - 1 && values[cell + 1] != 0) score++
            score * 2 + puzzle.edges.count { it.a == cell || it.b == cell }
        } ?: return null
        return Hint(set(target, puzzle.solution[target]), "This square holds a ${name(puzzle.solution[target])}.", target)
    }

    private fun set(cell: Int, v: Int): DuoState {
        val arr = values.copyOf(); arr[cell] = v
        return DuoState(puzzle, arr)
    }

    private fun name(v: Int) = if (v == DuoPuzzle.SUN) "sun" else "moon"
}

// ---------------------------------------------------------------------------------------------
// Trail

class TrailState(val puzzle: TrailPuzzle, val path: List<Int>) : BoardState {
    val size get() = puzzle.size
    override val solved: Boolean by lazy { TrailRules.isSolved(puzzle, path) }
    override val progress: Float get() = path.size.toFloat() / (size * size)
    val head: Int? get() = path.lastOrNull()

    fun canExtend(cell: Int) = TrailRules.canExtend(puzzle, path, cell)
    fun extend(cell: Int): TrailState = if (canExtend(cell)) TrailState(puzzle, path + cell) else this

    /** Keeps the path up to and including [cell]. Returns this if [cell] is not on the path. */
    fun truncateTo(cell: Int): TrailState {
        val i = path.indexOf(cell)
        if (i < 0 || i == path.size - 1) return this
        return TrailState(puzzle, path.subList(0, i + 1).toList())
    }

    fun backOne(): TrailState = if (path.isEmpty()) this else TrailState(puzzle, path.dropLast(1))

    override fun save() = SavedBoard(path)

    override fun hint(): Hint? {
        if (solved) return null
        val sol = puzzle.solution
        var k = 0
        while (k < path.size && path[k] == sol[k]) k++
        val strayed = k < path.size
        val target = minOf(sol.size, maxOf(k, 1) + 3)
        val next = sol.take(target)
        val msg = if (strayed) "Your line went astray here. Here's the right way." else "Here's the next stretch."
        return Hint(TrailState(puzzle, next), msg, sol[maxOf(k, 0).coerceAtMost(sol.size - 1)])
    }
}

// ---------------------------------------------------------------------------------------------
// Sudoku

class SudokuState(
    val puzzle: SudokuPuzzle,
    val values: IntArray,
    /** Pencil marks: bit v set = note v. */
    val notes: IntArray,
) : BoardState {
    val size get() = puzzle.size
    val conflicts: BooleanArray by lazy { SudokuRules.conflicts(puzzle, values) }
    override val solved: Boolean by lazy { SudokuRules.isSolved(puzzle, values) }
    override val progress: Float
        get() {
            val open = puzzle.givens.count { it == 0 }
            if (open == 0) return 1f
            return values.indices.count { puzzle.givens[it] == 0 && values[it] != 0 }.toFloat() / open
        }

    fun isGiven(cell: Int) = puzzle.givens[cell] != 0

    fun peers(cell: Int): List<Int> {
        val n = size
        return (0 until n * n).filter {
            it != cell && (it / n == cell / n || it % n == cell % n || puzzle.boxOf(it) == puzzle.boxOf(cell))
        }
    }

    /** Places [digit] (or clears it if already there). Clears that note from peers. */
    fun place(cell: Int, digit: Int): SudokuState {
        if (isGiven(cell)) return this
        val v = values.copyOf()
        val nt = notes.copyOf()
        if (v[cell] == digit) {
            v[cell] = 0
        } else {
            v[cell] = digit
            nt[cell] = 0
            val bit = (1 shl digit).inv()
            for (p in peers(cell)) nt[p] = nt[p] and bit
        }
        return SudokuState(puzzle, v, nt)
    }

    fun toggleNote(cell: Int, digit: Int): SudokuState {
        if (isGiven(cell) || values[cell] != 0) return this
        val nt = notes.copyOf()
        nt[cell] = nt[cell] xor (1 shl digit)
        return SudokuState(puzzle, values, nt)
    }

    fun erase(cell: Int): SudokuState {
        if (isGiven(cell) || (values[cell] == 0 && notes[cell] == 0)) return this
        val v = values.copyOf(); val nt = notes.copyOf()
        v[cell] = 0; nt[cell] = 0
        return SudokuState(puzzle, v, nt)
    }

    /** Fills every empty cell's notes with its remaining candidates. */
    fun autoNotes(): SudokuState {
        val cand = SudokuRules.candidates(puzzle, values)
        val nt = IntArray(notes.size) { if (values[it] == 0) cand[it] else 0 }
        return SudokuState(puzzle, values, nt)
    }

    fun digitCount(d: Int): Int = values.count { it == d }

    override fun save() = SavedBoard(values.toList(), notes.toList())

    override fun hint(): Hint? {
        if (solved) return null
        for (i in values.indices) {
            if (!isGiven(i) && values[i] != 0 && values[i] != puzzle.solution[i]) {
                return Hint(place(i, puzzle.solution[i]), "This cell should be ${puzzle.solution[i]}.", i)
            }
        }
        val cand = SudokuRules.candidates(puzzle, values)
        val target = values.indices.filter { values[it] == 0 }.minByOrNull { Integer.bitCount(cand[it]) } ?: return null
        val only = Integer.bitCount(cand[target]) == 1
        val d = puzzle.solution[target]
        val msg = if (only) "Only $d fits here." else "This cell is $d."
        return Hint(place(target, d), msg, target)
    }
}
