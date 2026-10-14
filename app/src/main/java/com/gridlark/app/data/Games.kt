package com.gridlark.app.data

import com.gridlark.engine.Difficulty
import com.gridlark.engine.Rng
import com.gridlark.engine.crowns.CrownsGenerator
import com.gridlark.engine.crowns.CrownsPuzzle
import com.gridlark.engine.duo.DuoGenerator
import com.gridlark.engine.duo.DuoPuzzle
import com.gridlark.engine.sudoku.SudokuGenerator
import com.gridlark.engine.sudoku.SudokuPuzzle
import com.gridlark.engine.trail.TrailGenerator
import com.gridlark.engine.trail.TrailPuzzle
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The four games. [key] and [seedSalt] are persisted / used for daily seeds, so they must
 * never change once the app is published.
 */
enum class GameType(val key: String, val seedSalt: Long, val title: String, val tagline: String) {
    CROWNS("crowns", 0x43524F574EL, "Crowns", "One crown in every row, column and colour"),
    DUO("duo", 0x44554F31L, "Sun & Moon", "Balance suns and moons in every line"),
    TRAIL("trail", 0x545241494CL, "Trail", "One line through every cell, in number order"),
    SUDOKU("sudoku", 0x5355444FL, "Sudoku", "Every digit once per row, column and box");

    companion object {
        fun fromKey(key: String): GameType = entries.first { it.key == key }
    }
}

enum class PlayMode { DAILY, PRACTICE }

fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> "Gentle"
    Difficulty.MEDIUM -> "Steady"
    Difficulty.HARD -> "Tricky"
    Difficulty.EXPERT -> "Fiendish"
}

/** Rough solving time shown on the Today list, in minutes. */
fun estimateMinutes(game: GameType, difficulty: Difficulty): Int = when (game) {
    GameType.CROWNS, GameType.DUO, GameType.TRAIL -> when (difficulty) {
        Difficulty.EASY -> 1; Difficulty.MEDIUM -> 2; Difficulty.HARD -> 4; Difficulty.EXPERT -> 7
    }
    GameType.SUDOKU -> when (difficulty) {
        Difficulty.EASY -> 2; Difficulty.MEDIUM -> 6; Difficulty.HARD -> 10; Difficulty.EXPERT -> 18
    }
}

/** A generated puzzle of any game. */
sealed interface AnyPuzzle {
    val game: GameType
    val difficulty: Difficulty

    data class Crowns(val p: CrownsPuzzle) : AnyPuzzle {
        override val game get() = GameType.CROWNS
        override val difficulty get() = p.difficulty
    }
    data class Duo(val p: DuoPuzzle) : AnyPuzzle {
        override val game get() = GameType.DUO
        override val difficulty get() = p.difficulty
    }
    data class Trail(val p: TrailPuzzle) : AnyPuzzle {
        override val game get() = GameType.TRAIL
        override val difficulty get() = p.difficulty
    }
    data class Sudoku(val p: SudokuPuzzle) : AnyPuzzle {
        override val game get() = GameType.SUDOKU
        override val difficulty get() = p.difficulty
    }
}

object Daily {
    /** Day 1 of the Gridlark daily series. Issue numbers count from here. */
    val EPOCH: LocalDate = LocalDate.of(2026, 10, 1)

    /** Bumping this would change every daily puzzle. Never change after release. */
    private const val SEED_VERSION = 1L

    fun issueNumber(date: LocalDate): Long = date.toEpochDay() - EPOCH.toEpochDay() + 1

    /** Gentle early in the week, trickier towards the weekend. */
    fun difficultyFor(date: LocalDate): Difficulty = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> Difficulty.EASY
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> Difficulty.MEDIUM
        else -> Difficulty.HARD
    }

    fun seedFor(game: GameType, epochDay: Long): Long = Rng.mix(SEED_VERSION, game.seedSalt, epochDay)
}

object PuzzleFactory {
    /** Pure and deterministic: same arguments, same puzzle. Can be slow; call off the main thread. */
    fun create(game: GameType, difficulty: Difficulty, seed: Long, daily: Boolean): AnyPuzzle = when (game) {
        GameType.CROWNS -> AnyPuzzle.Crowns(CrownsGenerator.generate(difficulty, seed))
        GameType.DUO -> AnyPuzzle.Duo(DuoGenerator.generate(difficulty, seed))
        GameType.TRAIL -> AnyPuzzle.Trail(TrailGenerator.generate(difficulty, seed))
        // The daily Sudoku stays bite-sized: the 6×6 board on gentle days.
        GameType.SUDOKU -> AnyPuzzle.Sudoku(
            SudokuGenerator.generate(difficulty, seed, mini = daily && difficulty == Difficulty.EASY),
        )
    }
}
