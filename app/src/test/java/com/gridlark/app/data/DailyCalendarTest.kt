package com.gridlark.app.data

import com.gridlark.app.game.BoardState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Generates every real daily puzzle for the next year exactly as the app does, so a
 * pathological seed can never leave players staring at "Setting the board…".
 */
class DailyCalendarTest {

    @Test
    fun every_daily_for_a_year_generates_fast_and_is_solvable() {
        val start = LocalDate.of(2026, 10, 1)
        var slowest = 0L
        for (offset in 0 until 365) {
            val date = start.plusDays(offset.toLong())
            val day = date.toEpochDay()
            for (game in GameType.entries) {
                val t = System.nanoTime()
                val p = PuzzleFactory.create(game, Daily.difficultyFor(date), Daily.seedFor(game, day), daily = true)
                val ms = (System.nanoTime() - t) / 1_000_000
                slowest = maxOf(slowest, ms)
                assertTrue("$game on $date took ${ms}ms", ms < 5_000)
                // The stored solution must satisfy the game's own rules.
                var b = BoardState.fresh(p)
                var guard = 0
                while (!b.solved && guard++ < 400) b = b.hint()!!.board
                assertTrue("$game on $date not solvable via hints", b.solved)
            }
        }
        println("slowest daily generation: ${slowest}ms")
    }
}
