package com.gridlark.app.game

import com.gridlark.app.data.AnyPuzzle
import com.gridlark.app.data.Daily
import com.gridlark.app.data.GameType
import com.gridlark.app.data.PuzzleFactory
import com.gridlark.engine.Difficulty
import com.gridlark.engine.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardsTest {

    private fun puzzles(): List<AnyPuzzle> = GameType.entries.flatMap { g ->
        listOf(Difficulty.EASY, Difficulty.HARD).flatMap { d ->
            (1L..3L).map { seed -> PuzzleFactory.create(g, d, seed * 31 + d.ordinal, daily = false) }
        }
    }

    /** Applies hints until solved; fails if a hint ever returns null early or it takes too long. */
    private fun solveWithHints(start: BoardState): BoardState {
        var b = start
        var guard = 0
        while (!b.solved) {
            val h = b.hint()
            assertNotNull("hint returned null on an unsolved board", h)
            b = h!!.board
            assertTrue("hints did not converge", ++guard < 500)
        }
        assertNull(b.hint())
        return b
    }

    @Test
    fun hints_solve_every_fresh_board() {
        for (p in puzzles()) {
            val solved = solveWithHints(BoardState.fresh(p))
            assertTrue(solved.solved)
            assertEquals(1f, solved.progress, 0.0001f)
        }
    }

    @Test
    fun hints_recover_from_wrong_moves() {
        val rng = Rng(7)
        for (p in puzzles()) {
            var b = BoardState.fresh(p)
            // Scramble with legal-but-wrong moves.
            repeat(25) {
                b = when (b) {
                    is CrownsState -> (b as CrownsState).cycle(rng.nextInt((b as CrownsState).size * (b as CrownsState).size))
                    is DuoState -> (b as DuoState).cycle(rng.nextInt((b as DuoState).size * (b as DuoState).size))
                    is SudokuState -> {
                        val s = b as SudokuState
                        s.place(rng.nextInt(s.size * s.size), 1 + rng.nextInt(s.size))
                    }
                    is TrailState -> {
                        val t = b as TrailState
                        val options = (0 until t.size * t.size).filter { t.canExtend(it) }
                        if (options.isEmpty()) t else t.extend(options[rng.nextInt(options.size)])
                    }
                }
            }
            assertTrue(solveWithHints(b).solved)
        }
    }

    @Test
    fun save_and_restore_round_trip() {
        for (p in puzzles()) {
            var b = BoardState.fresh(p)
            repeat(3) { b = b.hint()?.board ?: b }
            val restored = BoardState.restore(p, b.save())
            assertEquals(b.save(), restored.save())
        }
    }

    @Test
    fun restore_rejects_garbage_and_falls_back_to_fresh() {
        for (p in puzzles()) {
            val restored = BoardState.restore(p, SavedBoard(listOf(99, -4, 7)))
            assertEquals(BoardState.fresh(p).save(), restored.save())
        }
    }

    @Test
    fun crowns_tap_cycles_and_drag_marks() {
        val p = PuzzleFactory.create(GameType.CROWNS, Difficulty.MEDIUM, 5, daily = false) as AnyPuzzle.Crowns
        var b = BoardState.fresh(p) as CrownsState
        b = b.cycle(0); assertEquals(CrownsState.CROSS, b.marks[0])
        b = b.cycle(0); assertEquals(CrownsState.CROWN, b.marks[0])
        b = b.cycle(0); assertEquals(CrownsState.EMPTY, b.marks[0])
        b = b.withAll(listOf(0, 1, 2), CrownsState.EMPTY, CrownsState.CROSS)
        assertTrue((0..2).all { b.marks[it] == CrownsState.CROSS })
    }

    @Test
    fun duo_givens_are_locked() {
        val p = PuzzleFactory.create(GameType.DUO, Difficulty.EASY, 9, daily = false) as AnyPuzzle.Duo
        val b = BoardState.fresh(p) as DuoState
        val given = p.p.givens.indexOfFirst { it != 0 }
        assertTrue(given >= 0)
        assertTrue(b.cycle(given) === b)
    }

    @Test
    fun trail_truncate_and_back() {
        val p = PuzzleFactory.create(GameType.TRAIL, Difficulty.MEDIUM, 3, daily = false) as AnyPuzzle.Trail
        var b = BoardState.fresh(p) as TrailState
        for (cell in p.p.solution.take(6)) b = b.extend(cell)
        assertEquals(6, b.path.size)
        b = b.truncateTo(p.p.solution[2])
        assertEquals(3, b.path.size)
        b = b.backOne()
        assertEquals(2, b.path.size)
        // Can't jump to a non-adjacent cell.
        assertFalse(b.canExtend(p.p.solution[10]))
    }

    @Test
    fun sudoku_place_clears_peer_notes_and_toggles() {
        val p = PuzzleFactory.create(GameType.SUDOKU, Difficulty.MEDIUM, 4, daily = false) as AnyPuzzle.Sudoku
        var b = BoardState.fresh(p) as SudokuState
        val empties = (0 until b.size * b.size).filter { !b.isGiven(it) }
        val a = empties[0]
        val peer = empties.drop(1).first { it in b.peers(a) }
        val d = p.p.solution[a]
        b = b.toggleNote(peer, d)
        assertTrue(b.notes[peer] and (1 shl d) != 0)
        b = b.place(a, d)
        assertEquals(d, b.values[a])
        assertEquals(0, b.notes[peer] and (1 shl d))
        b = b.place(a, d) // same digit again clears it
        assertEquals(0, b.values[a])
    }

    @Test
    fun daily_seeds_are_stable_and_distinct() {
        val day = Daily.EPOCH.toEpochDay() + 13
        val seeds = GameType.entries.map { Daily.seedFor(it, day) }
        assertEquals(seeds.size, seeds.toSet().size)
        assertEquals(seeds, GameType.entries.map { Daily.seedFor(it, day) })
        assertTrue(Daily.seedFor(GameType.CROWNS, day) != Daily.seedFor(GameType.CROWNS, day + 1))
    }
}
