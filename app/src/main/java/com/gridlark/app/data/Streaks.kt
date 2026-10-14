package com.gridlark.app.data

/**
 * Streak numbers for the Stats screen.
 *
 * @property current consecutive counted days ending today, or ending yesterday when today
 *   has not been counted yet (so the streak doesn't read 0 before the player has played today).
 * @property longest longest run of consecutive counted days ever.
 * @property playedDays every epoch day that counts towards a streak.
 */
data class StreakInfo(val current: Int, val longest: Int, val playedDays: Set<Long>)

/**
 * A day counts when at least one DAILY puzzle for that day was solved on that same local day.
 * Practice plays and archive solves (solved on a later day) never count.
 * Pure function: [today] is the local epoch day.
 */
fun computeStreaks(solved: List<PlayEntity>, today: Long): StreakInfo {
    val days = HashSet<Long>()
    for (p in solved) {
        if (p.mode == PlayMode.DAILY.name && p.solved && p.solvedOnEpochDay == p.epochDay) days.add(p.epochDay)
    }
    if (days.isEmpty()) return StreakInfo(current = 0, longest = 0, playedDays = emptySet())

    val anchor = if (today in days) today else today - 1
    var current = 0
    var d = anchor
    while (d in days) {
        current++
        d--
    }

    var longest = 0
    var run = 0
    var previous = Long.MIN_VALUE
    for (day in days.sorted()) {
        run = if (previous != Long.MIN_VALUE && day == previous + 1) run + 1 else 1
        if (run > longest) longest = run
        previous = day
    }
    return StreakInfo(current = current, longest = maxOf(longest, current), playedDays = days)
}

/**
 * Per-game totals. [bestMs] and [medianMs] use clean solves (no hints) when there are any,
 * otherwise every solve of that game; null when the game has never been solved.
 */
data class GameStats(
    val game: GameType,
    val solved: Int,
    val bestMs: Long?,
    val medianMs: Long?,
    val dailySolved: Int,
)

/** One entry per [GameType], in enum order. Rows that are not solved are ignored. */
fun computeGameStats(solved: List<PlayEntity>): List<GameStats> {
    val byGame = solved.filter { it.solved }.groupBy { it.game }
    return GameType.entries.map { game ->
        val plays = byGame[game.key].orEmpty()
        val clean = plays.filter { it.hintsUsed == 0 }
        val timed = clean.ifEmpty { plays }.map { it.elapsedMs }.sorted()
        GameStats(
            game = game,
            solved = plays.size,
            bestMs = timed.firstOrNull(),
            medianMs = medianOfSorted(timed),
            dailySolved = plays.count { it.mode == PlayMode.DAILY.name },
        )
    }
}

/** Median of an ascending list; the mean of the two middle values for even sizes. */
internal fun medianOfSorted(sorted: List<Long>): Long? {
    if (sorted.isEmpty()) return null
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
