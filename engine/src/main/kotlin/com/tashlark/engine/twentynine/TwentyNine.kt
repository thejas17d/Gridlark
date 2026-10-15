package com.tashlark.engine.twentynine

import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.CardSet
import com.tashlark.engine.HandState
import com.tashlark.engine.Phase
import com.tashlark.engine.Policy
import com.tashlark.engine.Rng
import com.tashlark.engine.Rules
import com.tashlark.engine.Suit
import com.tashlark.engine.Teams
import com.tashlark.engine.Trick
import com.tashlark.engine.partnerOf
import com.tashlark.engine.sampleHands
import com.tashlark.engine.teamOf
import kotlin.math.floor
import kotlin.math.max

/** House rules for 29. Defaults follow the common North Indian / Bangladeshi game. */
data class TwentyNineConfig(
    /** The side winning the 8th trick scores 1 extra point (29 in total); max bid becomes 29. */
    val lastTrickPoint: Boolean = false,
    /** Defenders may double after trump is set; the declarer may then redouble. */
    val doubling: Boolean = true,
    /** Before trump is revealed, the declarer may not lead trump unless holding only trumps. */
    val declarerNoTrumpLeadBeforeReveal: Boolean = true,
    /** A player who asks to see trump must play a trump if they hold one. */
    val mustTrumpAfterAsking: Boolean = true,
    /** King + queen of trump ("pair"/marriage) moves the target by 4 once trump is revealed. */
    val pair: Boolean = true,
    /** Game points needed to win the match (and the negative score that loses it). */
    val matchTarget: Int = 6,
) {
    val maxBid: Int get() = if (lastTrickPoint) 29 else 28
    val minBid: Int get() = 16
}

/**
 * One deal of 29. Seats act 0,1,2,3 starting next(dealer). 32-card deck, 4 cards each
 * before bidding; the remaining 16 ([stock]) are dealt after trump (and doubling).
 *
 * The declarer's face-down [indicator] is NOT in their hand until [revealed]; then it joins
 * the hand and becomes playable.
 */
data class TwentyNineState(
    override val hands: List<CardSet>,
    val dealer: Int,
    val config: TwentyNineConfig = TwentyNineConfig(),
    /** Undealt cards, in deal order (4 to each seat from next(dealer)). Empty once play starts. */
    val stock: List<Card> = emptyList(),
    // --- auction ---
    val bidTurn: Int = (dealer + 1) % 4,
    /** Highest bid so far, 0 if none. */
    val highBid: Int = 0,
    val highBidder: Int = -1,
    /** Latest bid per seat (for display). */
    val seatBids: List<Int?> = List(4) { null },
    val passed: List<Boolean> = List(4) { false },
    /** Declarer seat, -1 while the auction is running. */
    val declarer: Int = -1,
    /** The winning bid (0 while the auction is running). */
    val bid: Int = 0,
    /** Points the declarer side needs; starts at [bid], moved by a pair. */
    val target: Int = 0,
    // --- trump ---
    val indicator: Card? = null,
    val trump: Suit? = null,
    val revealed: Boolean = false,
    val revealedBy: Int = -1,
    /** Index of the trick during which trump was revealed, -1 if not. */
    val revealTrick: Int = -1,
    /** True if the reveal only happened because the declarer had nothing left but the indicator. */
    val forcedReveal: Boolean = false,
    /** Seat that has just asked for trump and has not yet played, else -1. */
    val revealCaller: Int = -1,
    // --- doubling ---
    /** 0: first defender to act, 1: second defender, 2: declarer may redouble. */
    val doubleStage: Int = 0,
    val multiplier: Int = 1,
    val doubledBy: Int = -1,
    // --- play ---
    override val tricks: List<Trick> = emptyList(),
    override val current: Trick? = null,
    val trickWinners: List<Int> = emptyList(),
    /** Card points won per team (0 = seats 0&2, 1 = seats 1&3), excluding the last-trick point. */
    val points: List<Int> = listOf(0, 0),
    val played: CardSet = CardSet.EMPTY,
    override val voids: List<Set<Suit>> = List(4) { emptySet() },
    // --- pair ---
    val pairSeat: Int = -1,
) : HandState {
    val firstSeat: Int get() = (dealer + 1) % 4

    val phase: Phase
        get() = when {
            declarer < 0 -> Phase.BIDDING
            indicator == null -> Phase.TRUMP
            stock.isNotEmpty() -> Phase.DOUBLING
            tricks.size >= 8 -> Phase.OVER
            else -> Phase.PLAY
        }

    val declarerTeam: Int get() = if (declarer < 0) -1 else teamOf(declarer)

    /** Finished hand in which trump was never revealed by choice: no score, same dealer redeals. */
    val annulled: Boolean get() = tricks.size >= 8 && (!revealed || forcedReveal)

    companion object {
        /** Shuffles the 32-card deck once and deals 4 cards to each seat; 16 stay in [stock]. */
        fun deal(dealer: Int, config: TwentyNineConfig, rng: Rng): TwentyNineState {
            val deck = CardSet.PIQUET.toMutableList()
            rng.shuffle(deck)
            val hands = MutableList(4) { CardSet.EMPTY }
            for (i in 0..3) {
                val seat = (dealer + 1 + i) % 4
                hands[seat] = CardSet.of(deck.subList(i * 4, i * 4 + 4))
            }
            return TwentyNineState(
                hands = hands, dealer = dealer, config = config,
                stock = deck.subList(16, 32).toList(),
            )
        }
    }
}

/** Outcome of a finished hand. */
data class TwentyNineResult(
    val annulled: Boolean,
    val declarer: Int,
    val bid: Int,
    val target: Int,
    /** Card points (plus the last-trick point if enabled) of the declarer's side. */
    val declarerPoints: Int,
    val defenderPoints: Int,
    val multiplier: Int,
    val made: Boolean,
    val pairSeat: Int,
) {
    val declarerTeam: Int get() = teamOf(declarer)

    /** Game-point change for the declarer's team (defenders never change). */
    val delta: Int get() = if (annulled) 0 else if (made) multiplier else -multiplier

    fun teamDelta(team: Int): Int = if (team == declarerTeam) delta else 0
}

object TwentyNineRules : Rules<TwentyNineState> {
    override val teams = Teams.PAIRS

    /** Rank within a suit: J > 9 > A > 10 > K > Q > 8 > 7 (8 high .. 1 low; 0 for non-29 cards). */
    fun strength(c: Card): Int = when (c.rank) {
        11 -> 8
        9 -> 7
        14 -> 6
        10 -> 5
        13 -> 4
        12 -> 3
        8 -> 2
        7 -> 1
        else -> 0
    }

    /** Card points: J 3, 9 2, A 1, 10 1, others 0 (28 in the deck). */
    fun points(c: Card): Int = when (c.rank) {
        11 -> 3
        9 -> 2
        14, 10 -> 1
        else -> 0
    }

    /** Trump suit that counts in tricks right now (null until revealed). */
    fun activeTrump(s: TwentyNineState): Suit? = if (s.revealed) s.trump else null

    /** Whether [a] beats [b] given the led suit and the active trump (or null). */
    fun beats(a: Card, b: Card, led: Suit, trump: Suit?): Boolean = when {
        a.suit == b.suit -> strength(a) > strength(b)
        trump != null && a.suit == trump -> true
        trump != null && b.suit == trump -> false
        else -> a.suit == led && b.suit != led
    }

    /** Index in [t] of the card currently winning. */
    fun winningIndex(t: Trick, trump: Suit?): Int {
        var best = 0
        val led = t.ledSuit ?: return 0
        for (i in 1 until t.cards.size) if (beats(t.cards[i], t.cards[best], led, trump)) best = i
        return best
    }

    fun trickWinner(t: Trick, trump: Suit?): Int = t.seatOf(winningIndex(t, trump))

    /** Card points of [team] including the last-trick point when enabled. */
    fun teamPoints(s: TwentyNineState, team: Int): Int {
        var p = s.points[team]
        if (s.config.lastTrickPoint && s.trickWinners.size >= 8 && teamOf(s.trickWinners[7]) == team) p += 1
        return p
    }

    fun result(s: TwentyNineState): TwentyNineResult {
        val d = s.declarer
        val dt = if (d < 0) 0 else teamOf(d)
        val dp = teamPoints(s, dt)
        val op = teamPoints(s, 1 - dt)
        return TwentyNineResult(
            annulled = s.annulled, declarer = d, bid = s.bid, target = s.target,
            declarerPoints = dp, defenderPoints = op, multiplier = s.multiplier,
            made = dp >= s.target, pairSeat = s.pairSeat,
        )
    }

    override fun toAct(s: TwentyNineState): Int = when (s.phase) {
        Phase.BIDDING -> s.bidTurn
        Phase.TRUMP -> s.declarer
        Phase.DOUBLING -> when (s.doubleStage) {
            0 -> (s.declarer + 1) % 4
            1 -> (s.declarer + 3) % 4
            else -> s.declarer
        }
        Phase.PLAY -> {
            val cur = s.current
            when {
                cur != null -> (cur.leader + cur.cards.size) % 4
                s.trickWinners.isEmpty() -> s.firstSeat
                else -> s.trickWinners.last()
            }
        }
        Phase.OVER -> -1
    }

    /** The declarer must act with nothing in hand but the face-down indicator: reveal is forced. */
    fun mustForceReveal(s: TwentyNineState, seat: Int): Boolean =
        s.phase == Phase.PLAY && !s.revealed && seat == s.declarer && s.hands[seat].isEmpty()

    /** [seat] may ask to see trump: not yet revealed and void in the led suit. */
    fun canCallReveal(s: TwentyNineState, seat: Int): Boolean {
        if (s.phase != Phase.PLAY || s.revealed) return false
        val t = s.current ?: return false
        val led = t.ledSuit ?: return false
        return !s.hands[seat].has(led)
    }

    fun legalPlays(s: TwentyNineState): CardSet {
        val seat = toAct(s)
        if (seat < 0) return CardSet.EMPTY
        val hand = s.hands[seat]
        val trump = s.trump
        val t = s.current
        val led = t?.ledSuit
        if (led == null) {
            if (!s.revealed && seat == s.declarer && s.config.declarerNoTrumpLeadBeforeReveal && trump != null) {
                val nonTrump = hand - hand.ofSuit(trump)
                if (!nonTrump.isEmpty()) return nonTrump
            }
            return hand
        }
        if (hand.has(led)) return hand.ofSuit(led)
        if (s.revealCaller == seat && s.config.mustTrumpAfterAsking && trump != null && hand.has(trump)) {
            return hand.ofSuit(trump)
        }
        return hand
    }

    private fun legalBids(s: TwentyNineState): List<Action> {
        val passedCount = s.passed.count { it }
        if (s.highBidder < 0 && passedCount >= 3) return listOf(Action.Bid(s.config.minBid))
        val out = ArrayList<Action>()
        out.add(Action.Pass)
        val lo = max(s.config.minBid, s.highBid + 1)
        for (v in lo..s.config.maxBid) out.add(Action.Bid(v))
        return out
    }

    override fun legalActions(s: TwentyNineState): List<Action> = when (s.phase) {
        Phase.BIDDING -> legalBids(s)
        Phase.TRUMP -> s.hands[s.declarer].map { Action.SetTrump(it, it.suit) }
        Phase.DOUBLING ->
            if (s.doubleStage <= 1) listOf(Action.Double, Action.NoDouble)
            else listOf(Action.Redouble, Action.NoDouble)
        Phase.PLAY -> {
            val seat = toAct(s)
            if (mustForceReveal(s, seat)) {
                listOf(Action.CallReveal)
            } else {
                val out = ArrayList<Action>()
                for (c in legalPlays(s)) out.add(Action.Play(c))
                if (canCallReveal(s, seat)) out.add(Action.CallReveal)
                out
            }
        }
        Phase.OVER -> emptyList()
    }

    override fun apply(s: TwentyNineState, a: Action): TwentyNineState {
        val seat = toAct(s)
        require(seat >= 0) { "hand is over" }
        return when (s.phase) {
            Phase.BIDDING -> applyBid(s, seat, a)
            Phase.TRUMP -> applyTrump(s, a)
            Phase.DOUBLING -> applyDouble(s, seat, a)
            Phase.PLAY -> applyPlay(s, seat, a)
            Phase.OVER -> throw IllegalStateException("hand is over")
        }
    }

    private fun applyBid(s: TwentyNineState, seat: Int, a: Action): TwentyNineState {
        require(a in legalBids(s)) { "illegal bid $a by seat $seat" }
        var highBid = s.highBid
        var highBidder = s.highBidder
        val seatBids = s.seatBids.toMutableList()
        val passed = s.passed.toMutableList()
        if (a is Action.Bid) {
            highBid = a.value
            highBidder = seat
            seatBids[seat] = a.value
        } else {
            passed[seat] = true
        }
        val passedCount = passed.count { it }
        if (passedCount >= 3 && highBidder >= 0) {
            return s.copy(
                highBid = highBid, highBidder = highBidder, seatBids = seatBids, passed = passed,
                declarer = highBidder, bid = highBid, target = highBid, bidTurn = -1,
            )
        }
        var next = seat
        for (i in 1..4) {
            val n = (seat + i) % 4
            if (!passed[n]) {
                next = n
                break
            }
        }
        return s.copy(highBid = highBid, highBidder = highBidder, seatBids = seatBids, passed = passed, bidTurn = next)
    }

    private fun applyTrump(s: TwentyNineState, a: Action): TwentyNineState {
        val st = a as? Action.SetTrump ?: throw IllegalArgumentException("expected trump choice, got $a")
        val card = st.card ?: throw IllegalArgumentException("29 trump needs a card")
        val d = s.declarer
        require(card in s.hands[d] && st.suit == card.suit) { "illegal trump card $card" }
        val hands = s.hands.toMutableList()
        hands[d] = hands[d] - card
        val next = s.copy(hands = hands, indicator = card, trump = card.suit, doubleStage = 0, multiplier = 1)
        return if (s.config.doubling) next else dealStock(next)
    }

    private fun applyDouble(s: TwentyNineState, seat: Int, a: Action): TwentyNineState {
        if (s.doubleStage <= 1) {
            return when (a) {
                Action.Double -> s.copy(multiplier = 2, doubledBy = seat, doubleStage = 2)
                Action.NoDouble ->
                    if (s.doubleStage == 0) s.copy(doubleStage = 1) else dealStock(s.copy(doubleStage = 3))
                else -> throw IllegalArgumentException("expected Double or NoDouble, got $a")
            }
        }
        return when (a) {
            Action.Redouble -> dealStock(s.copy(multiplier = 4, doubleStage = 3))
            Action.NoDouble -> dealStock(s.copy(doubleStage = 3))
            else -> throw IllegalArgumentException("expected Redouble or NoDouble, got $a")
        }
    }

    /** Deals the second 16 cards, 4 to each seat from next(dealer). */
    private fun dealStock(s: TwentyNineState): TwentyNineState {
        require(s.stock.size == 16) { "stock must hold 16 cards" }
        val hands = s.hands.toMutableList()
        for (i in 0..3) {
            val seat = (s.dealer + 1 + i) % 4
            hands[seat] = hands[seat] + CardSet.of(s.stock.subList(i * 4, i * 4 + 4))
        }
        return s.copy(hands = hands, stock = emptyList())
    }

    private fun applyPlay(s: TwentyNineState, seat: Int, a: Action): TwentyNineState {
        if (a == Action.CallReveal) {
            val forced = mustForceReveal(s, seat)
            require(forced || canCallReveal(s, seat)) { "seat $seat may not ask for trump now" }
            val ind = s.indicator ?: throw IllegalStateException("no trump indicator")
            val hands = s.hands.toMutableList()
            hands[s.declarer] = hands[s.declarer] + ind
            return s.copy(
                hands = hands, revealed = true, revealedBy = seat, revealTrick = s.tricks.size,
                forcedReveal = forced, revealCaller = seat,
            )
        }
        val p = a as? Action.Play ?: throw IllegalArgumentException("expected a card, got $a")
        val card = p.card
        require(!mustForceReveal(s, seat) && card in legalPlays(s)) { "illegal play $card by seat $seat" }
        val t = s.current ?: Trick(seat, emptyList())
        val led = t.ledSuit
        val trump = s.trump
        var voids = s.voids
        if (led != null && card.suit != led) {
            var v = voids[seat] + led
            if (s.revealCaller == seat && s.config.mustTrumpAfterAsking && trump != null && card.suit != trump) v = v + trump
            voids = voids.toMutableList().also { it[seat] = v }
        }
        val hands = s.hands.toMutableList()
        hands[seat] = hands[seat] - card
        val trick = Trick(t.leader, t.cards + card)
        val played = s.played + card
        if (!trick.isComplete) {
            return s.copy(hands = hands, current = trick, played = played, voids = voids, revealCaller = -1)
        }
        val w = trickWinner(trick, activeTrump(s))
        val team = teamOf(w)
        val pts = s.points.toMutableList()
        pts[team] = pts[team] + trick.cards.sumOf { points(it) }
        var next = s.copy(
            hands = hands, current = null, tricks = s.tricks + trick, trickWinners = s.trickWinners + w,
            points = pts, played = played, voids = voids, revealCaller = -1,
        )
        if (s.config.pair && next.revealed && next.pairSeat < 0 && trump != null) {
            val king = Card.of(trump, 13)
            val queen = Card.of(trump, 12)
            val holder = listOf(w, partnerOf(w)).firstOrNull { king in hands[it] && queen in hands[it] }
            if (holder != null) {
                val adjusted = if (teamOf(holder) == teamOf(s.declarer)) s.bid - 4 else s.bid + 4
                next = next.copy(pairSeat = holder, target = adjusted.coerceIn(s.config.minBid, s.config.maxBid))
            }
        }
        return next
    }

    override fun utility(s: TwentyNineState): DoubleArray {
        if (!isOver(s) || s.annulled || s.declarer < 0) return DoubleArray(4)
        val r = result(s)
        val dt = r.declarerTeam
        val declValue = r.delta + (r.declarerPoints - r.defenderPoints) / 100.0
        return DoubleArray(4) { if (teamOf(it) == dt) declValue else -declValue }
    }

    override fun winnerOf(s: TwentyNineState, t: Trick): Int? {
        val i = s.tricks.indexOf(t)
        return if (i >= 0 && i < s.trickWinners.size) s.trickWinners[i] else null
    }

    override fun determinize(s: TwentyNineState, viewer: Int, rng: Rng): TwentyNineState {
        val d = s.declarer
        val ind = s.indicator
        val indicatorKnown = ind != null && (viewer == d || s.revealed)
        var known = s.played + s.hands[viewer]
        if (ind != null && indicatorKnown) known = known + ind
        val unseen = (CardSet.PIQUET - known).toList()
        // After a reveal everyone knows the declarer holds the indicator until it is played.
        val fixedInd: Card? = if (ind != null && s.revealed && d != viewer && ind in s.hands[d]) ind else null
        val needs = IntArray(4) { seat -> if (seat == viewer) 0 else s.hands[seat].size }
        if (fixedInd != null) needs[d] = needs[d] - 1
        val sampleIndicator = ind != null && !indicatorKnown
        val noVoids = s.voids.all { it.isEmpty() }
        val attempts = 24
        for (attempt in 0 until attempts) {
            val pool = unseen.toMutableList()
            var newInd: Card? = ind
            // The hidden indicator may be any unseen card (it is exempt from the declarer's voids).
            if (sampleIndicator && pool.isNotEmpty()) newInd = pool.removeAt(rng.nextInt(pool.size))
            val hands = s.hands.toMutableList()
            var stock = s.stock
            var ok = true
            if (s.stock.isNotEmpty() || noVoids) {
                rng.shuffle(pool)
                var k = 0
                for (seat in 0..3) {
                    if (seat == viewer) continue
                    hands[seat] = CardSet.of(pool.subList(k, k + needs[seat]))
                    k += needs[seat]
                }
                stock = if (s.stock.isEmpty()) emptyList() else pool.subList(k, pool.size).toList()
            } else {
                val dealt = sampleHands(pool, needs, s.voids, rng)
                for (seat in 0..3) {
                    if (seat == viewer) continue
                    if (dealt[seat].any { it.suit in s.voids[seat] }) ok = false
                    hands[seat] = CardSet.of(dealt[seat])
                }
            }
            if (fixedInd != null) hands[d] = hands[d] + fixedInd
            if (ok || attempt == attempts - 1) {
                val nt = if (newInd != null) newInd.suit else s.trump
                return s.copy(hands = hands, stock = stock, indicator = newInd, trump = nt)
            }
        }
        throw IllegalStateException("unreachable")
    }
}

/**
 * Fast heuristic used by Easy opponents, for bidding/trump choice, and inside simulations.
 * Reads only what the acting seat may know (own hand, played cards, trump if visible to them).
 */
object TwentyNinePolicy : Policy<TwentyNineState> {
    private val R = TwentyNineRules

    override fun choose(s: TwentyNineState, legal: List<Action>, rng: Rng): Action {
        if (legal.size == 1) return legal[0]
        val seat = R.toAct(s)
        return when (s.phase) {
            Phase.BIDDING -> chooseBid(s, seat)
            Phase.TRUMP -> chooseTrump(s.hands[seat])
            Phase.DOUBLING -> chooseDouble(s, seat, legal)
            Phase.PLAY -> choosePlay(s, seat, legal)
            Phase.OVER -> legal[0]
        }
    }

    /** Rough value of a 4-card hand in team points (with partner's average help). */
    fun estimateBid(hand: CardSet, config: TwentyNineConfig): Int {
        var e = 10.0
        for (c in hand) {
            e += when (c.rank) {
                11 -> 2.5
                9 -> if (Card.of(c.suit, 11) in hand) 2.0 else 1.0
                14 -> 1.0
                10 -> 0.5
                else -> 0.0
            }
        }
        val longest = Suit.entries.maxOf { hand.ofSuit(it).size }
        if (longest > 1) e += (longest - 1) * 1.5
        if (config.lastTrickPoint) e += 0.5
        return floor(e).toInt()
    }

    private fun chooseBid(s: TwentyNineState, seat: Int): Action {
        val est = estimateBid(s.hands[seat], s.config)
        val minBid = max(s.config.minBid, s.highBid + 1)
        val partnerHigh = s.highBidder >= 0 && s.highBidder == partnerOf(seat)
        val ceiling = if (partnerHigh) est - 2 else est
        return if (minBid <= ceiling && minBid <= s.config.maxBid) Action.Bid(minBid) else Action.Pass
    }

    fun suitScore(hand: CardSet, suit: Suit): Int {
        val cs = hand.ofSuit(suit)
        var v = cs.size * 2
        for (c in cs) v += when (c.rank) {
            11 -> 3
            9 -> 2
            14, 10 -> 1
            else -> 0
        }
        return v
    }

    fun chooseTrump(hand: CardSet): Action {
        val suit = Suit.entries.filter { hand.has(it) }.maxBy { suitScore(hand, it) }
        val card = hand.ofSuit(suit).minBy { R.strength(it) }
        return Action.SetTrump(card, suit)
    }

    private fun chooseDouble(s: TwentyNineState, seat: Int, legal: List<Action>): Action {
        var cards = s.hands[seat]
        val ind = s.indicator
        if (seat == s.declarer && ind != null) cards = cards + ind
        val strong = cards.count { it.rank == 11 } >= 2
        return when {
            strong && Action.Double in legal -> Action.Double
            strong && Action.Redouble in legal -> Action.Redouble
            Action.NoDouble in legal -> Action.NoDouble
            else -> legal[0]
        }
    }

    private fun choosePlay(s: TwentyNineState, seat: Int, legal: List<Action>): Action {
        val plays = legal.mapNotNull { (it as? Action.Play)?.card }
        if (plays.isEmpty()) return legal[0]
        val hand = s.hands[seat]
        val ind = s.indicator
        val knownTrump: Suit? = if (s.revealed || seat == s.declarer) s.trump else null
        val active = R.activeTrump(s)
        val mine = if (seat == s.declarer && ind != null) hand + ind else hand
        val unplayed = CardSet.PIQUET - s.played

        fun isTrump(c: Card) = knownTrump != null && c.suit == knownTrump
        fun cost(c: Card) = (if (isTrump(c)) 100 else 0) + R.points(c) * 10 + R.strength(c)
        fun isMaster(c: Card) = unplayed.ofSuit(c.suit).none { it !in mine && R.strength(it) > R.strength(c) }

        val t = s.current
        val led = t?.ledSuit
        if (t == null || led == null) {
            val masters = plays.filter { isMaster(it) && !isTrump(it) }
            if (masters.isNotEmpty()) return Action.Play(masters.maxBy { R.points(it) * 10 + R.strength(it) })
            val side = plays.filter { !isTrump(it) }.ifEmpty { plays }
            val longest = side.groupBy { it.suit }.maxBy { it.value.size }.value
            return Action.Play(longest.minBy { cost(it) })
        }

        val winIdx = R.winningIndex(t, active)
        val winSeat = t.seatOf(winIdx)
        val winCard = t.cards[winIdx]
        val partnerWinning = teamOf(winSeat) == teamOf(seat)
        val trickPts = t.cards.sumOf { R.points(it) }
        val lastToPlay = t.cards.size == 3

        if (Action.CallReveal in legal && !partnerWinning && trickPts >= 1) return Action.CallReveal

        if (partnerWinning) {
            val safe = lastToPlay || (isMaster(winCard) && (active == null || winCard.suit == active || led == active))
            val nonTrump = plays.filter { !isTrump(it) }.ifEmpty { plays }
            return if (safe) {
                Action.Play(nonTrump.maxBy { R.points(it) * 10 - R.strength(it) })
            } else {
                Action.Play(nonTrump.minBy { cost(it) })
            }
        }
        val winners = plays.filter { R.beats(it, winCard, led, active) }
        if (winners.isNotEmpty()) {
            val cheapest = winners.minBy { cost(it) }
            if (!isTrump(cheapest) || trickPts > 0 || lastToPlay) return Action.Play(cheapest)
        }
        return Action.Play(plays.minBy { cost(it) })
    }
}
