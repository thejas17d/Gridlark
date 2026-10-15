package com.tashlark.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsMathTest {

    private var nextId = 1L

    private fun rec(game: String, endedAt: Long, won: Boolean) = MatchRecord(
        id = nextId++,
        gameId = game,
        endedAt = endedAt,
        won = won,
        yourScore = "1",
        hands = 5,
        skill = "MEDIUM",
        assisted = false,
    )

    @Test
    fun emptyHistoryHasNoStreaks() {
        assertEquals(Streaks(0, 0), streaks(emptyList()))
    }

    @Test
    fun currentStreakCountsTrailingWins() {
        val list = listOf(
            rec("callbreak", 1, true),
            rec("callbreak", 2, true),
            rec("callbreak", 3, true),
            rec("callbreak", 4, false),
            rec("callbreak", 5, true),
            rec("callbreak", 6, true),
        )
        assertEquals(Streaks(current = 2, best = 3), streaks(list))
    }

    @Test
    fun lossAtTheEndResetsCurrent() {
        val list = listOf(rec("29", 1, true), rec("29", 2, true), rec("29", 3, false))
        assertEquals(Streaks(current = 0, best = 2), streaks(list))
    }

    @Test
    fun streaksIgnoreInputOrder() {
        // Room hands records newest first; the result must not depend on that.
        val list = listOf(
            rec("mendikot", 30, true),
            rec("mendikot", 10, false),
            rec("mendikot", 20, true),
        )
        assertEquals(Streaks(current = 2, best = 2), streaks(list))
        assertEquals(Streaks(current = 2, best = 2), streaks(list.reversed()))
    }

    @Test
    fun sameTimestampFallsBackToInsertionOrder() {
        val a = rec("courtpiece", 5, false)
        val b = rec("courtpiece", 5, true)
        assertEquals(Streaks(current = 1, best = 1), streaks(listOf(b, a)))
    }

    @Test
    fun winPercentRoundsWithoutLying() {
        assertNull(winPercent(0, 0))
        assertEquals(0, winPercent(0, 3))
        assertEquals(100, winPercent(4, 4))
        assertEquals(67, winPercent(2, 3))
        assertEquals(99, winPercent(199, 200))
        assertEquals(1, winPercent(1, 300))
    }

    @Test
    fun recordsFollowCatalogOrderAndIncludeUnplayedGames() {
        val list = listOf(
            rec("mendikot", 1, true),
            rec("callbreak", 2, false),
            rec("callbreak", 3, true),
            rec("retired-game", 4, true),
        )
        val records = computeRecords(list)
        assertEquals(GameCatalog.all.map { it.id }, records.map { it.gameId })

        val cb = records.first { it.gameId == "callbreak" }
        assertEquals(2, cb.played)
        assertEquals(1, cb.won)
        assertEquals(50, cb.winPercent)
        assertEquals(1, cb.currentStreak)
        assertEquals(1, cb.bestStreak)
        assertEquals(3L, cb.last?.endedAt)

        val tn = records.first { it.gameId == "twentynine" }
        assertEquals(0, tn.played)
        assertNull(tn.winPercent)
        assertNull(tn.last)
    }

    @Test
    fun totalsCountEveryMatch() {
        val list = listOf(rec("callbreak", 1, true), rec("twentynine", 2, false), rec("mendikot", 3, true))
        assertEquals(Totals(played = 3, won = 2, winPercent = 67), totals(list))
    }

    @Test
    fun recentIsNewestFirstAndLimited() {
        val list = (1L..30L).map { rec("callbreak", it, it % 2 == 0L) }
        val r = recent(list.shuffled(), limit = 20)
        assertEquals(20, r.size)
        assertEquals(30L, r.first().endedAt)
        assertEquals(11L, r.last().endedAt)
    }
}
