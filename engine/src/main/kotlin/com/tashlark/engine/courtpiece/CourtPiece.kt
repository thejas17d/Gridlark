package com.tashlark.engine.courtpiece

import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.CardSet
import com.tashlark.engine.HandState
import com.tashlark.engine.Policy
import com.tashlark.engine.Rng
import com.tashlark.engine.Rules
import com.tashlark.engine.Suit
import com.tashlark.engine.Teams
import com.tashlark.engine.Trick
import com.tashlark.engine.partnerOf
import com.tashlark.engine.sampleHands
import com.tashlark.engine.teamOf

/**
 * House rules for Court Piece (Rang / Hokm).
 *
 * [doubleSir]: won tricks pile up in the centre; a team collects the pile only when the same
 * player wins two tricks in a row. All 13 tricks are played and a team needs 7 collected.
 * [firstCollectionNeedsThree]: the first collection of the hand needs at least 3 tricks in the
 * pile (so tricks 1 + 2 alone can't be taken). The winner of trick 13 takes whatever is left.
 */
data class CourtPieceConfig(
    val target: Int = 10,
    val redealWeakCaller: Boolean = true,
    val doubleSir: Boolean = false,
    val firstCollectionNeedsThree: Boolean = true,
) {
    init {
        require(target > 0) { "target must be positive" }
    }
}

/**
 * One deal of Court Piece. Partners 0 & 2 (team 0) vs 1 & 3 (team 1).
 *
 * During the trump phase each seat holds only its first five cards in [hands]; the eight cards
 * each seat will receive afterwards wait in [stock] (unknown to everyone). [teamTricks] counts
 * tricks won per team (single sir) or tricks collected per team (double sir). [pile] and
 * [streakSeat] are only used by double sir: tricks waiting in the centre and the seat that won
 * the previous trick since the last collection (-1 if none).
 */
data class CourtPieceState(
    override val hands: List<CardSet>,
    val stock: List<CardSet>,
    val dealer: Int,
    val trump: Suit?,
    override val tricks: List<Trick>,
    override val current: Trick?,
    val teamTricks: List<Int>,
    val pile: Int,
    val streakSeat: Int,
    val played: CardSet,
    override val voids: List<Set<Suit>>,
    val config: CourtPieceConfig,
    /** How many times this hand was redealt because the caller's first five were weak. */
    val redeals: Int = 0,
) : HandState {
    val caller: Int get() = (dealer + 1) % 4
    val callerTeam: Int get() = teamOf(caller)
    val stockCards: CardSet get() = stock.fold(CardSet.EMPTY) { a, b -> a + b }

    companion object {
        const val FIRST_DEAL = 5
        const val SECOND_DEAL = 8

        /**
         * Shuffles once from [rng], deals five each and holds the rest back in [stock]. If
         * [CourtPieceConfig.redealWeakCaller] is on and the caller's five are all below a jack,
         * the same dealer redeals from the next shuffle of the same [rng].
         */
        fun deal(dealer: Int, config: CourtPieceConfig, rng: Rng): CourtPieceState {
            require(dealer in 0..3) { "dealer must be 0..3" }
            val caller = (dealer + 1) % 4
            var redeals = 0
            while (true) {
                val deck = CardSet.FULL.toMutableList()
                rng.shuffle(deck)
                val first = List(4) { seat -> CardSet.of(deck.subList(seat * FIRST_DEAL, seat * FIRST_DEAL + FIRST_DEAL)) }
                if (config.redealWeakCaller && first[caller].all { it.rank < 11 }) {
                    redeals++
                    continue
                }
                val base = 4 * FIRST_DEAL
                val rest = List(4) { seat -> CardSet.of(deck.subList(base + seat * SECOND_DEAL, base + seat * SECOND_DEAL + SECOND_DEAL)) }
                return CourtPieceState(
                    hands = first, stock = rest, dealer = dealer, trump = null, tricks = emptyList(),
                    current = null, teamTricks = listOf(0, 0), pile = 0, streakSeat = -1,
                    played = CardSet.EMPTY, voids = List(4) { emptySet() }, config = config, redeals = redeals,
                )
            }
        }

        /** A state ready for trick 1 with the given full hands and trump (tests, puzzles). */
        fun start(hands: List<CardSet>, dealer: Int, trump: Suit, config: CourtPieceConfig = CourtPieceConfig()): CourtPieceState =
            CourtPieceState(
                hands = hands, stock = List(4) { CardSet.EMPTY }, dealer = dealer, trump = trump,
                tricks = emptyList(), current = null, teamTricks = listOf(0, 0), pile = 0, streakSeat = -1,
                played = CardSet.EMPTY, voids = List(4) { emptySet() }, config = config,
            )
    }
}

/** Trick bookkeeping after each trick (team counts plus the double-sir centre pile). */
data class CourtPieceTally(val teamTricks: List<Int>, val pile: Int, val streakSeat: Int)

/** Outcome of a finished hand. [points] go to [winnerTeam]. */
data class CourtPieceResult(
    val callerTeam: Int,
    val winnerTeam: Int,
    val court: Boolean,
    val points: Int,
    val teamTricks: List<Int>,
) {
    val callerTeamWon: Boolean get() = winnerTeam == callerTeam
}

/** Caller's team: 1, or 3 for a court. Opponents: 2, or 4 for a court ("goon court"). */
fun courtPieceHandPoints(callerTeamWon: Boolean, court: Boolean): Int = when {
    callerTeamWon && court -> 3
    callerTeamWon -> 1
    court -> 4
    else -> 2
}

/**
 * Dealer's team won → the deal passes on; caller's team won without a court → same dealer;
 * caller's team won a court → the dealer's partner deals.
 */
fun courtPieceNextDealer(dealer: Int, result: CourtPieceResult): Int = when {
    result.winnerTeam == teamOf(dealer) -> (dealer + 1) % 4
    !result.court -> dealer
    else -> partnerOf(dealer)
}

object CourtPieceRules : Rules<CourtPieceState> {
    override val teams = Teams.PAIRS

    const val TRICKS_TO_WIN = 7

    /** Whether all tricks of the hand are done (trump must have been chosen). */
    fun handDone(s: CourtPieceState): Boolean {
        if (s.trump == null) return false
        return if (s.config.doubleSir) s.tricks.size >= 13 else s.teamTricks.any { it >= TRICKS_TO_WIN }
    }

    override fun toAct(s: CourtPieceState): Int {
        val trump = s.trump ?: return s.caller
        val cur = s.current
        if (cur != null) return (cur.leader + cur.cards.size) % 4
        if (handDone(s)) return -1
        return if (s.tricks.isEmpty()) s.caller else trickWinner(s.tricks.last(), trump)
    }

    /** Whether [a] beats [b] given the led suit and trump. */
    fun beats(a: Card, b: Card, led: Suit, trump: Suit): Boolean = when {
        a.suit == b.suit -> a.rank > b.rank
        a.suit == trump -> true
        b.suit == trump -> false
        else -> a.suit == led && b.suit != led
    }

    private fun winningIndex(t: Trick, trump: Suit): Int {
        val led = t.ledSuit ?: return -1
        var best = 0
        for (i in 1 until t.cards.size) if (beats(t.cards[i], t.cards[best], led, trump)) best = i
        return best
    }

    fun trickWinner(t: Trick, trump: Suit): Int {
        val i = winningIndex(t, trump)
        require(i >= 0) { "empty trick" }
        return t.seatOf(i)
    }

    fun currentWinningCard(t: Trick, trump: Suit): Card? {
        val i = winningIndex(t, trump)
        return if (i < 0) null else t.cards[i]
    }

    /** Seat currently winning [t], or -1 if it is empty. */
    fun currentWinnerSeat(t: Trick, trump: Suit): Int {
        val i = winningIndex(t, trump)
        return if (i < 0) -1 else t.seatOf(i)
    }

    /** Follow suit if able; otherwise any card (no obligation to trump). */
    fun legalPlays(s: CourtPieceState): CardSet {
        if (s.trump == null) return CardSet.EMPTY
        val seat = toAct(s)
        if (seat < 0) return CardSet.EMPTY
        val hand = s.hands[seat]
        val t = s.current
        if (t == null || t.cards.isEmpty()) return hand
        val led = t.ledSuit!!
        return if (hand.has(led)) hand.ofSuit(led) else hand
    }

    override fun legalActions(s: CourtPieceState): List<Action> {
        if (toAct(s) < 0) return emptyList()
        if (s.trump == null) return Suit.entries.map { Action.SetTrump(null, it) }
        return legalPlays(s).map { Action.Play(it) }
    }

    /** Folds the winner of trick number [trickNumber] (1-based) into the tally. */
    fun afterTrick(tally: CourtPieceTally, winner: Int, trickNumber: Int, config: CourtPieceConfig): CourtPieceTally {
        val team = teamOf(winner)
        if (!config.doubleSir) {
            val tt = tally.teamTricks.toMutableList().also { it[team] = it[team] + 1 }
            return CourtPieceTally(tt, 0, -1)
        }
        val pile = tally.pile + 1
        val collectedSoFar = tally.teamTricks.sum()
        val minPile = if (config.firstCollectionNeedsThree && collectedSoFar == 0) 3 else 2
        val collect = trickNumber >= 13 || (winner == tally.streakSeat && pile >= minPile)
        if (collect) {
            val tt = tally.teamTricks.toMutableList().also { it[team] = it[team] + pile }
            return CourtPieceTally(tt, 0, -1)
        }
        return CourtPieceTally(tally.teamTricks, pile, winner)
    }

    override fun apply(s: CourtPieceState, a: Action): CourtPieceState {
        val seat = toAct(s)
        require(seat >= 0) { "hand is over" }
        val trump = s.trump
        if (trump == null) {
            require(a is Action.SetTrump) { "expected a trump choice, got $a" }
            val shown = a.card
            if (shown != null) {
                require(shown in s.hands[seat] && shown.suit == a.suit) { "trump card $shown not in caller's hand" }
            }
            return s.copy(
                hands = List(4) { s.hands[it] + s.stock[it] },
                stock = List(4) { CardSet.EMPTY },
                trump = a.suit,
            )
        }
        require(a is Action.Play) { "expected a card, got $a" }
        val card = a.card
        require(card in legalPlays(s)) { "illegal play $card by seat $seat" }
        val t = s.current ?: Trick(seat, emptyList())
        val led = t.ledSuit
        val voids = if (led != null && card.suit != led) {
            s.voids.toMutableList().also { it[seat] = it[seat] + led }
        } else s.voids
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        val trick = Trick(t.leader, t.cards + card)
        val played = s.played + card
        if (!trick.isComplete) {
            return s.copy(hands = hands, current = trick, played = played, voids = voids)
        }
        val w = trickWinner(trick, trump)
        val tally = afterTrick(CourtPieceTally(s.teamTricks, s.pile, s.streakSeat), w, s.tricks.size + 1, s.config)
        return s.copy(
            hands = hands, current = null, tricks = s.tricks + trick, played = played, voids = voids,
            teamTricks = tally.teamTricks, pile = tally.pile, streakSeat = tally.streakSeat,
        )
    }

    /** The hand's outcome, or null while it is still being played. */
    fun result(s: CourtPieceState): CourtPieceResult? {
        if (!handDone(s)) return null
        val t = s.teamTricks
        val winner = if (t[0] >= TRICKS_TO_WIN) 0 else 1
        val court = t[1 - winner] == 0
        val callerTeam = s.callerTeam
        return CourtPieceResult(
            callerTeam = callerTeam, winnerTeam = winner, court = court,
            points = courtPieceHandPoints(winner == callerTeam, court), teamTricks = t,
        )
    }

    /** Team-shared and zero-sum: ± hand points, plus (team tricks − opponent tricks) / 100. */
    override fun utility(s: CourtPieceState): DoubleArray {
        val r = result(s)
        val t = s.teamTricks
        return DoubleArray(4) { seat ->
            val team = teamOf(seat)
            val tie = (t[team] - t[1 - team]) / 100.0
            val pts = when {
                r == null -> 0.0
                r.winnerTeam == team -> r.points.toDouble()
                else -> -r.points.toDouble()
            }
            pts + tie
        }
    }

    override fun winnerOf(s: CourtPieceState, t: Trick): Int? = s.trump?.let { trickWinner(t, it) }

    override fun determinize(s: CourtPieceState, viewer: Int, rng: Rng): CourtPieceState {
        if (s.trump == null) {
            // Only five cards each are out; the stock (including the viewer's own next eight) is unknown.
            val pool = (CardSet.FULL - s.played - s.hands[viewer]).toMutableList()
            rng.shuffle(pool)
            var k = 0
            val hands = ArrayList<CardSet>(4)
            for (seat in 0..3) {
                if (seat == viewer) {
                    hands.add(s.hands[seat])
                } else {
                    val n = s.hands[seat].size
                    hands.add(CardSet.of(pool.subList(k, k + n)))
                    k += n
                }
            }
            val stock = ArrayList<CardSet>(4)
            for (seat in 0..3) {
                val n = s.stock[seat].size
                stock.add(CardSet.of(pool.subList(k, k + n)))
                k += n
            }
            return s.copy(hands = hands, stock = stock)
        }
        val unseen = (CardSet.FULL - s.played - s.hands[viewer] - s.stockCards).toList()
        val needs = IntArray(4) { if (it == viewer) 0 else s.hands[it].size }
        val dealt = sampleHands(unseen, needs, s.voids, rng)
        val hands = List(4) { if (it == viewer) s.hands[it] else CardSet.of(dealt[it]) }
        return s.copy(hands = hands)
    }
}

/** Fast heuristic play used by Easy opponents, trump choice and Monte-Carlo playouts. */
object CourtPiecePolicy : Policy<CourtPieceState> {
    override fun choose(s: CourtPieceState, legal: List<Action>, rng: Rng): Action {
        val seat = CourtPieceRules.toAct(s)
        val trump = s.trump
        if (trump == null) {
            val pick = Action.SetTrump(null, chooseTrump(s.hands[seat]))
            return if (pick in legal) pick else legal.first()
        }
        val cards = legal.mapNotNull { (it as? Action.Play)?.card }
        if (cards.isEmpty()) return legal.first()
        return Action.Play(chooseCard(s, seat, trump, cards))
    }

    /** Length counts most; aces, kings, queens (and jacks) add weight. */
    fun trumpScore(hand: CardSet, suit: Suit): Int {
        var score = 0
        for (c in hand.ofSuit(suit)) {
            score += 10 + when (c.rank) {
                14 -> 6
                13 -> 4
                12 -> 2
                11 -> 1
                else -> 0
            }
        }
        return score
    }

    fun chooseTrump(hand: CardSet): Suit = Suit.entries.maxBy { trumpScore(hand, it) }

    private fun strength(c: Card, trump: Suit): Int = if (c.suit == trump) 100 + c.rank else c.rank

    private fun chooseCard(s: CourtPieceState, seat: Int, trump: Suit, cards: List<Card>): Card {
        val hand = s.hands[seat]
        val unseen = CardSet.FULL - s.played - hand
        val myTeam = teamOf(seat)
        val defending = myTeam != s.callerTeam
        val courtDanger = s.teamTricks[myTeam] == 0
        val isMaster = { c: Card -> unseen.ofSuit(c.suit).none { it.rank > c.rank } }

        val t = s.current
        if (t == null || t.cards.isEmpty()) {
            val trumps = cards.filter { it.suit == trump }
            // Attackers with trump length draw trumps with a master trump.
            if (!defending && trumps.size >= 3 && unseen.has(trump)) {
                trumps.filter(isMaster).minByOrNull { it.rank }?.let { return it }
            }
            val opps = listOf((seat + 1) % 4, (seat + 3) % 4)
            val masters = cards.filter { c ->
                c.suit != trump && isMaster(c) &&
                    !(unseen.has(trump) && opps.any { c.suit in s.voids[it] && trump !in s.voids[it] })
            }
            if (masters.isNotEmpty()) return masters.minBy { it.rank }
            // Otherwise lead low from the longest side suit.
            val side = cards.filter { it.suit != trump }.ifEmpty { cards }
            val longest = side.groupBy { it.suit }.maxByOrNull { it.value.size }!!.value
            return longest.minBy { it.rank }
        }

        val led = t.ledSuit!!
        val win = CourtPieceRules.currentWinningCard(t, trump)!!
        val winnerSeat = CourtPieceRules.currentWinnerSeat(t, trump)
        val after = (t.cards.size + 1 until 4).map { t.seatOf(it) }
        val oppsAfter = after.filter { teamOf(it) != myTeam }

        fun canBeat(opp: Int, card: Card): Boolean {
            val oppVoidLed = led in s.voids[opp]
            for (u in unseen) {
                if (u.suit in s.voids[opp]) continue
                if (u.suit != led && !(oppVoidLed && u.suit == trump)) continue
                if (CourtPieceRules.beats(u, card, led, trump)) return true
            }
            return false
        }
        fun safe(card: Card): Boolean = oppsAfter.none { canBeat(it, card) }

        val partnerWinning = winnerSeat == partnerOf(seat)
        if (partnerWinning && safe(win)) return lowest(cards, trump, isMaster)
        val winners = cards.filter { CourtPieceRules.beats(it, win, led, trump) }
        if (winners.isEmpty()) return lowest(cards, trump, isMaster)
        val cheapestSure = winners.filter { safe(it) }.minByOrNull { strength(it, trump) }
        if (partnerWinning) {
            // Only overtake partner with a sure winner of the led suit.
            return if (cheapestSure != null && cheapestSure.suit == led) cheapestSure else lowest(cards, trump, isMaster)
        }
        // An opponent is winning: take it as cheaply as we surely can.
        if (cheapestSure != null) return cheapestSure
        val secondHand = after.size == 2
        if (secondHand && !(defending && courtDanger)) return lowest(cards, trump, isMaster)
        val ledWinners = winners.filter { it.suit == led }
        return if (ledWinners.isNotEmpty()) ledWinners.maxBy { it.rank } else winners.minBy { strength(it, trump) }
    }

    /** Lowest card; when void, the lowest non-master card of the shortest side suit. */
    private fun lowest(cards: List<Card>, trump: Suit, isMaster: (Card) -> Boolean): Card {
        if (cards.map { it.suit }.distinct().size == 1) return cards.minBy { it.rank }
        val side = cards.filter { it.suit != trump }
        if (side.isEmpty()) return cards.minBy { it.rank }
        val bySuit = side.groupBy { it.suit }
        val candidates = bySuit.values.map { cs -> cs.minBy { it.rank } }
        val pool = candidates.filter { !isMaster(it) }.ifEmpty { candidates }
        return pool.minBy { bySuit.getValue(it.suit).size * 100 + it.rank }
    }
}
