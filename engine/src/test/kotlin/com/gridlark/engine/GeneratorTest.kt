package com.gridlark.engine

import com.gridlark.engine.crowns.CrownsGenerator
import com.gridlark.engine.crowns.CrownsRules
import com.gridlark.engine.crowns.CrownsSolver
import com.gridlark.engine.duo.DuoGenerator
import com.gridlark.engine.duo.DuoPuzzle
import com.gridlark.engine.duo.DuoRules
import com.gridlark.engine.duo.DuoSolver
import com.gridlark.engine.sudoku.SudokuGenerator
import com.gridlark.engine.sudoku.SudokuRules
import com.gridlark.engine.sudoku.SudokuSolver
import com.gridlark.engine.trail.TrailGenerator
import com.gridlark.engine.trail.TrailRules
import com.gridlark.engine.trail.TrailSolver
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {

    private val samples = (System.getProperty("gridlark.samples") ?: "25").toInt()

    private inline fun timed(label: String, count: Int, block: (Long) -> Unit) {
        var max = 0L
        val start = System.nanoTime()
        for (i in 0 until count) {
            val t = System.nanoTime()
            block(1000L + i * 7919L)
            max = maxOf(max, System.nanoTime() - t)
        }
        val avg = (System.nanoTime() - start) / count / 1_000_000
        println("$label: avg ${avg}ms, max ${max / 1_000_000}ms over $count")
    }

    @Test
    fun crowns_valid_unique_deterministic() {
        for (d in Difficulty.entries) {
            timed("crowns $d", samples) { seed ->
                val p = CrownsGenerator.generate(d, seed)
                val n = p.size
                // Every region is non-empty and contiguous ids.
                assertEquals((0 until n).toSet(), p.regions.toSet())
                val crowns = BooleanArray(n * n) { p.isCrownInSolution(it) }
                assertTrue("solution invalid", CrownsRules.isSolved(n, p.regions, crowns))
                assertEquals("not unique", 1, CrownsSolver.countSolutions(n, p.regions, 2))
                val again = CrownsGenerator.generate(d, seed)
                assertArrayEquals(p.regions, again.regions)
                assertArrayEquals(p.solution, again.solution)
            }
        }
    }

    @Test
    fun crowns_regions_are_connected() {
        for (seed in 1L..40L) {
            val p = CrownsGenerator.generate(Difficulty.MEDIUM, seed)
            val n = p.size
            for (g in 0 until n) {
                val cells = (0 until n * n).filter { p.regions[it] == g }
                val seen = HashSet<Int>()
                val stack = ArrayDeque(listOf(cells.first()))
                while (stack.isNotEmpty()) {
                    val c = stack.removeLast()
                    if (!seen.add(c)) continue
                    val r = c / n; val col = c % n
                    listOf(
                        if (r > 0) c - n else -1, if (r < n - 1) c + n else -1,
                        if (col > 0) c - 1 else -1, if (col < n - 1) c + 1 else -1,
                    ).filter { it >= 0 && p.regions[it] == g }.forEach { stack.add(it) }
                }
                assertEquals("region $g not connected", cells.size, seen.size)
            }
        }
    }

    @Test
    fun duo_valid_unique_deterministic() {
        for (d in Difficulty.entries) {
            timed("duo $d", samples) { seed ->
                val p = DuoGenerator.generate(d, seed)
                val n = p.size
                assertTrue(DuoRules.isSolved(n, p.solution, p.edges))
                for (i in p.givens.indices) {
                    if (p.givens[i] != DuoPuzzle.EMPTY) assertEquals(p.solution[i], p.givens[i])
                }
                for (e in p.edges) assertEquals(e.same, p.solution[e.a] == p.solution[e.b])
                val solved = DuoSolver(n, p.edges).solve(p.givens)
                assertTrue(solved.solved)
                assertArrayEquals(p.solution, solved.grid)
                if (n == 6) assertEquals("not unique", 1, DuoSolver.countSolutions(n, p.givens, p.edges))
                val again = DuoGenerator.generate(d, seed)
                assertArrayEquals(p.givens, again.givens)
                assertEquals(p.edges, again.edges)
            }
        }
    }

    @Test
    fun trail_valid_unique_deterministic() {
        for (d in Difficulty.entries) {
            timed("trail $d", samples) { seed ->
                val p = TrailGenerator.generate(d, seed)
                assertTrue("solution invalid", TrailRules.isSolved(p, p.solution.toList()))
                val res = TrailSolver(
                    p.size, p.numberAt, p.checkpoints.size, p.wallRight, p.wallDown, nodeLimit = 50_000_000,
                ).count(2)
                assertFalse("solver aborted", res.aborted)
                assertEquals("not unique", 1, res.count)
                val again = TrailGenerator.generate(d, seed)
                assertArrayEquals(p.checkpoints, again.checkpoints)
                assertArrayEquals(p.solution, again.solution)
            }
        }
    }

    @Test
    fun trail_rules_enforce_order() {
        val p = TrailGenerator.generate(Difficulty.MEDIUM, 42)
        val path = mutableListOf<Int>()
        for (cell in p.solution) {
            assertTrue(TrailRules.canExtend(p, path, cell))
            path.add(cell)
        }
        assertTrue(TrailRules.isSolved(p, path))
        // Can't start anywhere but 1.
        assertFalse(TrailRules.canExtend(p, emptyList(), p.solution[1]))
    }

    @Test
    fun sudoku_valid_unique_deterministic() {
        for (d in Difficulty.entries) {
            timed("sudoku $d", samples) { seed ->
                val p = SudokuGenerator.generate(d, seed)
                assertTrue(SudokuRules.isSolved(p, p.solution))
                for (i in p.givens.indices) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
                val out = SudokuSolver(p.size, p.boxRows, p.boxCols).solve(p.givens)
                assertTrue(out.solved)
                assertArrayEquals(p.solution, out.grid)
                assertEquals("not unique", 1, SudokuSolver.countSolutions(p, 2))
                val again = SudokuGenerator.generate(d, seed)
                assertArrayEquals(p.givens, again.givens)
            }
        }
        timed("sudoku mini", samples) { seed ->
            val p = SudokuGenerator.generate(Difficulty.MEDIUM, seed, mini = true)
            assertEquals(6, p.size)
            assertEquals(1, SudokuSolver.countSolutions(p, 2))
        }
    }

    @Test
    fun rng_is_stable() {
        // Pinned values: if these change, every daily puzzle changes. Never update casually.
        val r = Rng(12345)
        val first = LongArray(3) { r.nextLong() }
        assertArrayEquals(longArrayOf(2454886589211414944L, 3778200017661327597L, 2205171434679333405L), first)
        // Pin a daily puzzle too: generators must not drift between releases.
        val p = CrownsGenerator.generate(Difficulty.MEDIUM, 20240L)
        val again = CrownsGenerator.generate(Difficulty.MEDIUM, 20240L)
        assertArrayEquals(p.regions, again.regions)
        assertEquals("1111111610111166001256002225664422536642253664225", p.regions.joinToString(""))
    }
}
