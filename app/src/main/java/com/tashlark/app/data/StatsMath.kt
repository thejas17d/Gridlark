package com.tashlark.app.data

import kotlin.math.max
import kotlin.math.roundToInt

/** Consecutive wins: the run that is still going, and the longest run ever. */
data class Streaks(val current: Int, val best: Int)

/** One game's record, ready to show. */
data class GameRecord(
    val gameId: String,
    val title: String,
    val played: Int,
    val won: Int,
    /** Null when nothing has been played yet. */
    val winPercent: Int?,
    val currentStreak: Int,
    val bestStreak: Int,
    /** The most recently finished match, if any. */
    val last: MatchRecord?,
)

data class Totals(val played: Int, val won: Int, val winPercent: Int?)

/**
 * Whole-number win rate. Never shows 100% unless every match was won, and never 0%
 * unless none were, so rounding can't misreport a perfect or empty record.
 */
fun winPercent(won: Int, played: Int): Int? {
    if (played <= 0) return null
    return when {
        won >= played -> 100
        won <= 0 -> 0
        else -> (won * 100.0 / played).roundToInt().coerceIn(1, 99)
    }
}

/** Oldest first; ties (same millisecond) fall back to insertion order. */
fun chronological(list: List<MatchRecord>): List<MatchRecord> =
    list.sortedWith(compareBy<MatchRecord>({ it.endedAt }, { it.id }))

/** Win streaks over [list], in any order; it is sorted by end time first. */
fun streaks(list: List<MatchRecord>): Streaks {
    var run = 0
    var best = 0
    for (r in chronological(list)) {
        if (r.won) {
            run++
            best = max(best, run)
        } else {
            run = 0
        }
    }
    return Streaks(current = run, best = best)
}

fun totals(list: List<MatchRecord>): Totals {
    val won = list.count { it.won }
    return Totals(played = list.size, won = won, winPercent = winPercent(won, list.size))
}

/** One [GameRecord] per game in [games] order, including games not played yet. Unknown ids are ignored. */
fun computeRecords(list: List<MatchRecord>, games: List<GameInfo> = GameCatalog.all): List<GameRecord> {
    val byGame = list.groupBy { it.gameId }
    return games.map { game ->
        val mine = byGame[game.id].orEmpty()
        val won = mine.count { it.won }
        val s = streaks(mine)
        GameRecord(
            gameId = game.id,
            title = game.title,
            played = mine.size,
            won = won,
            winPercent = winPercent(won, mine.size),
            currentStreak = s.current,
            bestStreak = s.best,
            last = chronological(mine).lastOrNull(),
        )
    }
}

/** The newest [limit] matches, newest first. */
fun recent(list: List<MatchRecord>, limit: Int = 20): List<MatchRecord> =
    chronological(list).asReversed().take(limit)
