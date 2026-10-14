package com.gridlark.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {

    private val today = 20_000L

    private fun daily(
        day: Long,
        game: GameType = GameType.CROWNS,
        solvedOn: Long? = day,
        elapsedMs: Long = 60_000L,
        hints: Int = 0,
        solved: Boolean = true,
    ) = PlayEntity(
        id = PlayEntity.dailyId(game, day),
        game = game.key,
        mode = PlayMode.DAILY.name,
        epochDay = day,
        seed = day,
        difficulty = "EASY",
        stateJson = null,
        elapsedMs = elapsedMs,
        hintsUsed = hints,
        mistakesFixed = 0,
        solved = solved,
        solvedAtMillis = if (solved) 0L else null,
        solvedOnEpochDay = if (solved) solvedOn else null,
        updatedAt = 0L,
    )

    private fun practice(day: Long, game: GameType = GameType.CROWNS, elapsedMs: Long = 60_000L, hints: Int = 0) =
        PlayEntity(
            id = PlayEntity.practiceId(game, day * 31 + elapsedMs),
            game = game.key,
            mode = PlayMode.PRACTICE.name,
            epochDay = day,
            seed = day * 31 + elapsedMs,
            difficulty = "EASY",
            stateJson = null,
            elapsedMs = elapsedMs,
            hintsUsed = hints,
            mistakesFixed = 0,
            solved = true,
            solvedAtMillis = 0L,
            solvedOnEpochDay = day,
            updatedAt = 0L,
        )

    // --- streaks ---

    @Test
    fun emptyHistoryHasNoStreak() {
        assertEquals(StreakInfo(0, 0, emptySet()), computeStreaks(emptyList(), today))
    }

    @Test
    fun todayOnly() {
        val s = computeStreaks(listOf(daily(today)), today)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
        assertEquals(setOf(today), s.playedDays)
    }

    @Test
    fun streakEndingYesterdayContinuesBeforeTodayIsPlayed() {
        val s = computeStreaks(listOf(daily(today - 1), daily(today - 2), daily(today - 3)), today)
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
    }

    @Test
    fun yesterdayOnly() {
        val s = computeStreaks(listOf(daily(today - 1)), today)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
    }

    @Test
    fun gapBreaksTheStreak() {
        val plays = listOf(
            daily(today), daily(today - 1),
            // today - 2 missed
            daily(today - 3), daily(today - 4), daily(today - 5), daily(today - 6),
        )
        val s = computeStreaks(plays, today)
        assertEquals(2, s.current)
        assertEquals(4, s.longest)
    }

    @Test
    fun streakEndingBeforeYesterdayIsZero() {
        val s = computeStreaks(listOf(daily(today - 2), daily(today - 3)), today)
        assertEquals(0, s.current)
        assertEquals(2, s.longest)
    }

    @Test
    fun severalGamesOnOneDayCountOnce() {
        val s = computeStreaks(GameType.entries.map { daily(today, game = it) }, today)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
        assertEquals(setOf(today), s.playedDays)
    }

    @Test
    fun practicePlaysAreIgnored() {
        val s = computeStreaks(listOf(practice(today), practice(today - 1)), today)
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertTrue(s.playedDays.isEmpty())
    }

    @Test
    fun archiveSolvesAreIgnoredForStreaks() {
        // Two past dailies, both caught up on today from the archive.
        val plays = listOf(daily(today - 1, solvedOn = today), daily(today - 2, solvedOn = today))
        val s = computeStreaks(plays, today)
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertTrue(s.playedDays.isEmpty())
    }

    @Test
    fun archiveSolveDoesNotBridgeAGap() {
        val plays = listOf(daily(today), daily(today - 1, solvedOn = today), daily(today - 2))
        val s = computeStreaks(plays, today)
        assertEquals(1, s.current)
        assertEquals(setOf(today, today - 2), s.playedDays)
    }

    @Test
    fun unsolvedRowsAreIgnored() {
        val s = computeStreaks(listOf(daily(today, solved = false)), today)
        assertEquals(0, s.current)
        assertTrue(s.playedDays.isEmpty())
    }

    // --- per-game stats ---

    @Test
    fun gameStatsCoverEveryGameInEnumOrder() {
        val stats = computeGameStats(emptyList())
        assertEquals(GameType.entries.toList(), stats.map { it.game })
        stats.forEach {
            assertEquals(0, it.solved)
            assertNull(it.bestMs)
            assertNull(it.medianMs)
            assertEquals(0, it.dailySolved)
        }
    }

    @Test
    fun medianOfOddCount() {
        val plays = listOf(
            daily(today, elapsedMs = 30_000),
            daily(today - 1, elapsedMs = 90_000),
            daily(today - 2, elapsedMs = 60_000),
        )
        val crowns = computeGameStats(plays).first { it.game == GameType.CROWNS }
        assertEquals(3, crowns.solved)
        assertEquals(3, crowns.dailySolved)
        assertEquals(30_000L, crowns.bestMs)
        assertEquals(60_000L, crowns.medianMs)
    }

    @Test
    fun medianOfEvenCountIsMeanOfMiddlePair() {
        val plays = listOf(
            daily(today, game = GameType.SUDOKU, elapsedMs = 100_000),
            daily(today - 1, game = GameType.SUDOKU, elapsedMs = 40_000),
            practice(today, game = GameType.SUDOKU, elapsedMs = 60_000),
            practice(today - 1, game = GameType.SUDOKU, elapsedMs = 200_000),
        )
        val sudoku = computeGameStats(plays).first { it.game == GameType.SUDOKU }
        assertEquals(4, sudoku.solved)
        assertEquals(2, sudoku.dailySolved)
        assertEquals(40_000L, sudoku.bestMs)
        assertEquals(80_000L, sudoku.medianMs)
    }

    @Test
    fun timesPreferHintFreeSolves() {
        val plays = listOf(
            daily(today, game = GameType.TRAIL, elapsedMs = 10_000, hints = 2),
            daily(today - 1, game = GameType.TRAIL, elapsedMs = 50_000),
        )
        val trail = computeGameStats(plays).first { it.game == GameType.TRAIL }
        assertEquals(2, trail.solved)
        assertEquals(50_000L, trail.bestMs)
        assertEquals(50_000L, trail.medianMs)
    }

    @Test
    fun timesFallBackToHintedSolvesWhenNoCleanOnes() {
        val plays = listOf(
            daily(today, game = GameType.DUO, elapsedMs = 20_000, hints = 1),
            daily(today - 1, game = GameType.DUO, elapsedMs = 40_000, hints = 3),
        )
        val duo = computeGameStats(plays).first { it.game == GameType.DUO }
        assertEquals(20_000L, duo.bestMs)
        assertEquals(30_000L, duo.medianMs)
    }

    @Test
    fun unsolvedRowsDoNotCountInGameStats() {
        val stats = computeGameStats(listOf(daily(today, solved = false)))
        assertEquals(0, stats.first { it.game == GameType.CROWNS }.solved)
    }
}
