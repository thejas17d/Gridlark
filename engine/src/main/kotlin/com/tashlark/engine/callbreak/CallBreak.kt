package com.tashlark.engine.callbreak

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
import com.tashlark.engine.sampleHands
import kotlin.math.max
import kotlin.math.roundToInt

/** House rules. Defaults follow the most common app rules (Bhoos, catsatcards). */
data class CallBreakConfig(
    val mustBeatWhenFollowing: Boolean = true,
    val mustTrumpWhenVoid: Boolean = true,
    val mustOvertrump: Boolean = true,
    val maxBid: Int = 13,
    val rounds: Int = 5,
    val redealWithoutSpade: Boolean = true,
)

/**
 * One deal of Call Break. Spades are always trump. Seats act in order 0,1,2,3 from
 * next(dealer). Scores are in tenths to keep arithmetic exact (3.2 → 32).
 */
data class CallBreakState(
    override val hands: List<CardSet>,
    val dealer: Int,
    val bids: List<Int?>,
    override val tricks: List<Trick>,
    override val current: Trick?,
    val won: List<Int>,
    val played: CardSet,
    override val voids: List<Set<Suit>>,
    val config: CallBreakConfig,
) : HandState {
    val biddingDone: Boolean get() = bids.all { it != null }
    val firstSeat: Int get() = (dealer + 1) % 4

    companion object {
        /** Deals a fresh hand. Redeals (deterministically) while any hand lacks a spade, if enabled. */
        fun deal(dealer: Int, config: CallBreakConfig, rng: Rng): CallBreakState {
            while (true) {
                val deck = CardSet.FULL.toMutableList()
                rng.shuffle(deck)
                val hands = List(4) { seat -> CardSet.of(deck.subList(seat * 13, seat * 13 + 13)) }
                if (config.redealWithoutSpade && hands.any { !it.has(Suit.SPADES) }) continue
                return CallBreakState(
                    hands = hands, dealer = dealer, bids = List(4) { null }, tricks = emptyList(),
                    current = null, won = List(4) { 0 }, played = CardSet.EMPTY,
                    voids = List(4) { emptySet() }, config = config,
                )
            }
        }
    }
}

/** Points for one deal, in tenths: made → bid*10 + overtricks; failed → −bid*10. */
fun callBreakScoreTenths(bid: Int, won: Int): Int = if (won >= bid) bid * 10 + (won - bid) else -bid * 10

object CallBreakRules : Rules<CallBreakState> {
    override val teams = Teams.SOLO

    override fun toAct(s: CallBreakState): Int {
        if (!s.biddingDone) {
            val done = s.bids.count { it != null }
            return (s.firstSeat + done) % 4
        }
        val cur = s.current
        if (cur != null) return (cur.leader + cur.cards.size) % 4
        if (s.tricks.size == 13) return -1
        return if (s.tricks.isEmpty()) s.firstSeat else trickWinner(s.tricks.last())
    }

    fun trickWinner(t: Trick): Int {
        var best = 0
        for (i in 1 until t.cards.size) if (beats(t.cards[i], t.cards[best], t.ledSuit!!)) best = i
        return t.seatOf(best)
    }

    /** Whether [a] beats [b] given the led suit, with spades as trump. */
    fun beats(a: Card, b: Card, led: Suit): Boolean = when {
        a.suit == b.suit -> a.rank > b.rank
        a.suit == Suit.SPADES -> true
        b.suit == Suit.SPADES -> false
        else -> a.suit == led && b.suit != led
    }

    fun currentWinningCard(t: Trick): Card? {
        if (t.cards.isEmpty()) return null
        var best = t.cards[0]
        for (c in t.cards.drop(1)) if (beats(c, best, t.ledSuit!!)) best = c
        return best
    }

    fun legalPlays(s: CallBreakState): CardSet {
        val seat = toAct(s)
        val hand = s.hands[seat]
        val t = s.current
        if (t == null || t.cards.isEmpty()) return hand
        val led = t.ledSuit!!
        val win = currentWinningCard(t)!!
        val cfg = s.config
        if (hand.has(led)) {
            val follow = hand.ofSuit(led)
            val trumped = led != Suit.SPADES && win.suit == Suit.SPADES
            if (!cfg.mustBeatWhenFollowing || trumped) return follow
            val higher = CardSet.of(follow.filter { it.rank > win.rank })
            return if (higher.isEmpty()) follow else higher
        }
        if (hand.has(Suit.SPADES) && cfg.mustTrumpWhenVoid) {
            val spades = hand.ofSuit(Suit.SPADES)
            val trumped = win.suit == Suit.SPADES
            val over = if (trumped && cfg.mustOvertrump) CardSet.of(spades.filter { it.rank > win.rank }) else spades
            return if (over.isEmpty()) hand else over
        }
        return hand
    }

    override fun legalActions(s: CallBreakState): List<Action> {
        if (toAct(s) < 0) return emptyList()
        if (!s.biddingDone) return (1..s.config.maxBid).map { Action.Bid(it) }
        return legalPlays(s).map { Action.Play(it) }
    }

    override fun apply(s: CallBreakState, a: Action): CallBreakState {
        val seat = toAct(s)
        require(seat >= 0) { "hand is over" }
        if (!s.biddingDone) {
            require(a is Action.Bid && a.value in 1..s.config.maxBid) { "expected a bid, got $a" }
            return s.copy(bids = s.bids.toMutableList().also { it[seat] = a.value })
        }
        require(a is Action.Play) { "expected a card, got $a" }
        val card = a.card
        require(card in legalPlays(s)) { "illegal play $card by seat $seat" }
        val t = s.current ?: Trick(seat, emptyList())
        val voids = if (t.cards.isNotEmpty() && card.suit != t.ledSuit) {
            var v = s.voids[seat] + t.ledSuit!!
            val winning = currentWinningCard(t)!!
            if (s.config.mustTrumpWhenVoid && card.suit != Suit.SPADES && winning.suit != Suit.SPADES) v = v + Suit.SPADES
            s.voids.toMutableList().also { it[seat] = v }
        } else s.voids
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        val trick = Trick(t.leader, t.cards + card)
        if (!trick.isComplete) {
            return s.copy(hands = hands, current = trick, played = s.played + card, voids = voids)
        }
        val w = trickWinner(trick)
        return s.copy(
            hands = hands, current = null, tricks = s.tricks + trick, played = s.played + card, voids = voids,
            won = s.won.toMutableList().also { it[w] = it[w] + 1 },
        )
    }

    override fun utility(s: CallBreakState): DoubleArray = DoubleArray(4) { seat ->
        val bid = s.bids[seat] ?: 0
        callBreakScoreTenths(bid, s.won[seat]) / 10.0
    }

    override fun winnerOf(s: CallBreakState, t: Trick): Int = trickWinner(t)

    override fun determinize(s: CallBreakState, viewer: Int, rng: Rng): CallBreakState {
        val unseen = (CardSet.FULL - s.played - s.hands[viewer]).toList()
        val needs = IntArray(4) { if (it == viewer) 0 else s.hands[it].size }
        val dealt = sampleHands(unseen, needs, s.voids, rng)
        val hands = List(4) { if (it == viewer) s.hands[it] else CardSet.of(dealt[it]) }
        return s.copy(hands = hands)
    }
}

/** Fast, sensible play used by Easy opponents and inside simulations. */
object CallBreakPolicy : Policy<CallBreakState> {
    override fun choose(s: CallBreakState, legal: List<Action>, rng: Rng): Action {
        if (!s.biddingDone) return Action.Bid(heuristicBid(s.hands[CallBreakRules.toAct(s)], s.config.maxBid))
        val seat = CallBreakRules.toAct(s)
        val cards = legal.map { (it as Action.Play).card }
        val t = s.current
        val need = (s.bids[seat] ?: 0) - s.won[seat]
        val unplayed = CardSet.FULL - s.played
        fun isMaster(c: Card) = unplayed.ofSuit(c.suit).none { it.rank > c.rank && it !in s.hands[seat] }

        if (t == null || t.cards.isEmpty()) {
            val masters = cards.filter { isMaster(it) }
            if (need > 0) {
                masters.firstOrNull { it.suit != Suit.SPADES }?.let { return Action.Play(it) }
                masters.firstOrNull()?.let { return Action.Play(it) }
            }
            // Lead low from the longest side suit, keeping spades back.
            val side = cards.filter { it.suit != Suit.SPADES }.ifEmpty { cards }
            val bySuit = side.groupBy { it.suit }
            val longest = bySuit.maxByOrNull { (_, v) -> v.size }!!.value
            return Action.Play(longest.minBy { it.rank })
        }
        val led = t.ledSuit!!
        val win = CallBreakRules.currentWinningCard(t)!!
        val winners = cards.filter { CallBreakRules.beats(it, win, led) }
        val lastToPlay = t.cards.size == 3
        if (need > 0 && winners.isNotEmpty()) {
            // Cheapest card that wins; if others still play after us, prefer a master.
            val safe = if (lastToPlay) winners else winners.filter { isMaster(it) || it.suit == Suit.SPADES && led != Suit.SPADES }
            return Action.Play((safe.ifEmpty { winners }).minBy { strength(it, led) })
        }
        // Bid made (or can't win): play the lowest card, shedding high cards in short side suits.
        val losers = cards.filter { it !in winners }.ifEmpty { cards }
        return Action.Play(losers.minBy { strength(it, led) })
    }

    private fun strength(c: Card, led: Suit): Int = when (c.suit) {
        Suit.SPADES -> if (led == Suit.SPADES) c.rank else 100 + c.rank
        led -> c.rank
        else -> c.rank - 20
    }

    /** Classic count: aces, guarded kings, spade length and high spades. */
    fun heuristicBid(hand: CardSet, maxBid: Int): Int {
        var tricks = 0.0
        val spades = hand.ofSuit(Suit.SPADES)
        for (suit in Suit.entries) {
            val cs = hand.ofSuit(suit)
            if (suit == Suit.SPADES) continue
            if (Card.of(suit, 14) in cs) tricks += 1.0
            if (Card.of(suit, 13) in cs && cs.size >= 2) tricks += if (cs.size <= 4) 0.75 else 0.5
            if (Card.of(suit, 12) in cs && cs.size in 3..4) tricks += 0.25
            if (cs.size <= 1 && spades.size >= 3) tricks += if (cs.isEmpty()) 1.0 else 0.5
        }
        for (r in listOf(14, 13, 12)) if (Card.of(Suit.SPADES, r) in spades) tricks += if (r == 12 && spades.size < 3) 0.5 else 1.0
        tricks += max(0, spades.size - 3).toDouble() * 0.85
        return tricks.roundToInt().coerceIn(1, maxBid)
    }
}

/**
 * Bid by simulation: sample the other hands, play the deal out with the fast policy, and
 * pick the bid with the best expected score. Blended with the hand count for stability.
 */
fun callBreakSimulatedBid(s: CallBreakState, seat: Int, samples: Int, rng: Rng): Int {
    val hand = s.hands[seat]
    val base = CallBreakPolicy.heuristicBid(hand, s.config.maxBid)
    if (samples <= 0) return base
    val counts = IntArray(14)
    repeat(samples) {
        var w = CallBreakRules.determinize(s, seat, rng)
        // Others bid by the hand count; we bid our base so the playout behaves sensibly.
        val bids = List(4) { i -> if (i == seat) base else CallBreakPolicy.heuristicBid(w.hands[i], w.config.maxBid) }
        w = w.copy(bids = bids)
        var guard = 0
        while (!CallBreakRules.isOver(w)) {
            val legal = CallBreakRules.legalActions(w)
            w = CallBreakRules.apply(w, CallBreakPolicy.choose(w, legal, rng))
            check(++guard < 200)
        }
        counts[w.won[seat]]++
    }
    var bestBid = base
    var bestEv = Double.NEGATIVE_INFINITY
    for (b in 1..s.config.maxBid) {
        var ev = 0.0
        for (t in 0..13) ev += counts[t] * callBreakScoreTenths(b, t)
        if (ev > bestEv) { bestEv = ev; bestBid = b }
    }
    // Lean slightly conservative: a failed bid costs the whole bid.
    return ((bestBid + base) / 2.0).toInt().coerceIn(1, s.config.maxBid)
}
