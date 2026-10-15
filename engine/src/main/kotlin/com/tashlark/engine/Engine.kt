package com.tashlark.engine

/**
 * Every decision a seat can make in any of the games. Kept tiny and serializable-friendly.
 */
sealed interface Action {
    data class Play(val card: Card) : Action
    /** A numeric bid (Call Break 1..13, 29 16..28). */
    data class Bid(val value: Int) : Action
    data object Pass : Action
    /** Choose trump by suit (Court Piece) or by placing a face-down card (29, Mendikot band hukum). */
    data class SetTrump(val card: Card?, val suit: Suit) : Action
    /** Ask for the hidden trump to be shown (29, Mendikot band hukum). */
    data object CallReveal : Action
    data object Double : Action
    data object Redouble : Action
    data object NoDouble : Action
}

/** Who is on which team: Call Break is every seat for itself; the others are fixed pairs. */
enum class Teams { SOLO, PAIRS }

fun partnerOf(seat: Int) = (seat + 2) % 4
fun teamOf(seat: Int) = seat % 2

/** A completed or in-progress trick. */
data class Trick(val leader: Int, val cards: List<Card>) {
    val isComplete: Boolean get() = cards.size == 4
    fun seatOf(index: Int): Int = (leader + index) % 4
    val ledSuit: Suit? get() = cards.firstOrNull()?.suit
}

/**
 * Pure rules for one game. States are immutable; [apply] returns a new state.
 * The same interface drives the UI, the AI's simulations and the tests.
 */
interface Rules<S : HandState> {
    val teams: Teams

    /** Seat that must act, or -1 when the hand is over. */
    fun toAct(s: S): Int
    fun legalActions(s: S): List<Action>
    fun apply(s: S, a: Action): S
    fun isOver(s: S): Boolean = toAct(s) < 0

    /**
     * Outcome per seat for AI evaluation, roughly in "match points" (higher is better).
     * For pair games, both partners receive their team's value.
     */
    fun utility(s: S): DoubleArray

    /**
     * A copy of [s] where everything [viewer] cannot know (other hands, a face-down trump)
     * is resampled consistently with what has been observed. Used by the AI only.
     */
    fun determinize(s: S, viewer: Int, rng: Rng): S

    /** Seat that won completed trick [t] of hand [s], if the game can say (used for the UI highlight). */
    fun winnerOf(s: S, t: Trick): Int? = null
}

/** Common read-only view the UI and AI rely on. */
interface HandState {
    val hands: List<CardSet>
    val tricks: List<Trick>
    val current: Trick?
    /** Known void suits per seat, inferred from failures to follow. */
    val voids: List<Set<Suit>>
}

/**
 * Deals the cards in [unseen] to the seats in [needs] (seat → count) at random, honouring
 * known voids. Retries with a fresh shuffle on dead ends; falls back to ignoring voids only
 * if the constraints are contradictory (should not happen with correct bookkeeping).
 */
fun sampleHands(
    unseen: List<Card>,
    needs: IntArray,
    voids: List<Set<Suit>>,
    rng: Rng,
): Array<MutableList<Card>> {
    repeat(60) {
        val pool = unseen.toMutableList()
        rng.shuffle(pool)
        // Most constrained cards first: suits that fewer seats can hold.
        pool.sortBy { c -> (0..3).count { seat -> needs[seat] > 0 && c.suit !in voids[seat] } }
        val out = Array(4) { mutableListOf<Card>() }
        val left = needs.copyOf()
        var ok = true
        for (c in pool) {
            val options = (0..3).filter { left[it] > 0 && c.suit !in voids[it] }
            if (options.isEmpty()) { ok = false; break }
            // Prefer seats with the most room, randomised among equals, to avoid painting into corners.
            val maxLeft = options.maxOf { left[it] }
            val best = options.filter { left[it] >= maxLeft - 1 }
            val seat = best[rng.nextInt(best.size)]
            out[seat].add(c)
            left[seat]--
        }
        if (ok) return out
    }
    // Contradictory constraints: deal ignoring voids so the AI still has a consistent world.
    val pool = unseen.toMutableList()
    rng.shuffle(pool)
    val out = Array(4) { mutableListOf<Card>() }
    var k = 0
    for (seat in 0..3) repeat(needs[seat]) { out[seat].add(pool[k++]) }
    return out
}
