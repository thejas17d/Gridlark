package com.tashlark.engine

/** Broad phase of a hand, for the table UI. */
enum class Phase { BIDDING, TRUMP, DOUBLING, PLAY, OVER }

/**
 * What the table shows for one hand, from seat 0's (the human's) point of view.
 * Never contains information seat 0 isn't allowed to see.
 */
data class TableView(
    val phase: Phase,
    /** Trump suit if seat 0 may know it (always-trump games, revealed, or seat 0 set it). */
    val trump: Suit?,
    /** True while a face-down trump exists that seat 0 cannot see. */
    val trumpHidden: Boolean,
    /** Seat holding the face-down trump card (29 declarer, Mendikot setter), or -1. */
    val trumpHolder: Int,
    /** Short status per seat, e.g. "Bid 3 · Won 1" or "Partner". */
    val seatStatus: List<String>,
    /** One line explaining the moment, e.g. "Bidding: highest bid so far 18". */
    val headline: String,
    /** Optional line for the human's decision, e.g. "Choose a card to set as trump". */
    val prompt: String?,
    /** The face-down trump card, set only when seat 0 placed it (the holder may look at it). */
    val ownTrumpCard: Card? = null,
)

/** One line of the scoreboard. */
data class Standing(val label: String, val score: String, val leading: Boolean)

/** Result of a finished hand, shown on the round summary sheet. */
data class HandSummary(
    val title: String,
    /** Rows of (who, detail, delta) e.g. ("You", "Bid 3 · Won 4", "+3.1"). */
    val rows: List<Triple<String, String, String>>,
    val note: String? = null,
)

/**
 * A complete game: rules for a hand plus the match around it. The app drives every game
 * through this interface, so the table UI is shared.
 *
 * Seats: 0 = human (bottom), then 1, 2, 3 in play order. Pairs games: 0 & 2 vs 1 & 3.
 */
interface CardGame<S : HandState, M : Any> {
    val id: String
    val title: String
    val rules: Rules<S>
    val policy: Policy<S>

    fun newMatch(seed: Long): M
    /** Deals the next hand of the match (deterministic from the match seed and hand number). */
    fun deal(match: M): S
    /** Folds a finished hand into the match. */
    fun finishHand(match: M, hand: S): M
    fun isMatchOver(match: M): Boolean
    /** Seats on the winning side once the match is over. */
    fun winners(match: M): Set<Int>

    fun table(hand: S): TableView
    fun summary(match: M, hand: S): HandSummary
    fun standings(match: M): List<Standing>
    /** e.g. "Round 2 of 5" or "First to 6". */
    fun progress(match: M): String

    /**
     * AI decision for a non-play choice (bids, trump, doubling), or null to let the
     * Monte-Carlo search decide. Games use this for bidding where simulation needs care.
     */
    fun aiSpecial(hand: S, seat: Int, skill: Skill, rng: Rng): Action? = null

    /** Plain-language rules for the in-app rules sheet, one short paragraph per item. */
    fun rulesText(): List<String>

    /** A short reason for a suggested human move (hints), or null. */
    fun explain(hand: S, action: Action): String? = null
}

/** Display names for seats from the human's perspective. */
val SeatNames = listOf("You", "Asha", "Ravi", "Meera")

fun seatName(seat: Int) = SeatNames[seat]
