package com.tashlark.engine.mendikot

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

/** How trump is chosen. */
enum class MendikotTrumpMode {
    /** Closed trump: the player after the dealer hides one card face down; its suit is trump once revealed. */
    BAND_HUKUM,

    /** No trump at first: the first card played by someone who can't follow suit names trump. */
    CUT_HUKUM,
}

/** SIMPLE: every won hand is 1 point. WEIGHTED: win 1, mendikot 2, whitewash 3. */
enum class MendikotScoring { SIMPLE, WEIGHTED }

data class MendikotConfig(
    val trumpMode: MendikotTrumpMode = MendikotTrumpMode.BAND_HUKUM,
    val scoring: MendikotScoring = MendikotScoring.WEIGHTED,
    /** Match ends when a team reaches this many points. */
    val target: Int = 5,
)

/**
 * One deal of Mendikot. Seats 0..3 play in order; partners 0 & 2 vs 1 & 3.
 *
 * Band hukum: while [trumpCard] is hidden it is NOT in the setter's hand (they play from 12 cards);
 * on reveal it returns to [hands] of the [setter]. [trump] is the trump suit as chosen (it may be
 * set while still hidden); it only takes effect once [revealed].
 *
 * Cut hukum: [trumpCard] stays null; [trump] and [revealed] are set by the first failure to follow.
 *
 * Voids: a recorded void for the setter made while trump was hidden means "no such card in hand";
 * the hidden indicator itself may still be of that suit.
 */
data class MendikotState(
    override val hands: List<CardSet>,
    val dealer: Int,
    /** Band hukum: the face-down indicator chosen by the setter (kept after reveal for reference). */
    val trumpCard: Card?,
    val trump: Suit?,
    /** True once trump is active (band: revealed; cut: a cut has happened). */
    val revealed: Boolean,
    /** In the trick in progress, trump-suit cards at an index below this don't count as trump. */
    val trumpFrom: Int,
    /** Seat that has just called for the reveal and must now play (a trump if they hold one), or -1. */
    val caller: Int,
    override val tricks: List<Trick>,
    override val current: Trick?,
    /** Winner seat of each completed trick, parallel to [tricks]. */
    val trickWinners: List<Int>,
    val played: CardSet,
    override val voids: List<Set<Suit>>,
    val config: MendikotConfig,
) : HandState {
    /** The trump setter in band hukum; also the leader of trick 1 in both modes. */
    val setter: Int get() = (dealer + 1) % 4
    val isBand: Boolean get() = config.trumpMode == MendikotTrumpMode.BAND_HUKUM
    val inTrumpPhase: Boolean get() = isBand && trumpCard == null
    /** A face-down indicator exists and trump has not been revealed. */
    val trumpHidden: Boolean get() = isBand && trumpCard != null && !revealed

    fun seatTricks(seat: Int): Int = trickWinners.count { it == seat }

    fun seatTens(seat: Int): Int {
        var n = 0
        for (i in tricks.indices) if (trickWinners[i] == seat) n += tricks[i].cards.count { it.rank == 10 }
        return n
    }

    fun tricksOf(team: Int): Int = trickWinners.count { teamOf(it) == team }

    fun tensOf(team: Int): Int {
        var n = 0
        for (i in tricks.indices) if (teamOf(trickWinners[i]) == team) n += tricks[i].cards.count { it.rank == 10 }
        return n
    }

    /** The hand result from the cards captured so far (meaningful once all 13 tricks are played). */
    fun result(): MendikotResult =
        mendikotResult(listOf(tensOf(0), tensOf(1)), listOf(tricksOf(0), tricksOf(1)), config.scoring)

    companion object {
        /** Shuffles once from [rng] and deals 13 cards to each seat. */
        fun deal(dealer: Int, config: MendikotConfig, rng: Rng): MendikotState {
            val deck = CardSet.FULL.toMutableList()
            rng.shuffle(deck)
            val hands = List(4) { seat -> CardSet.of(deck.subList(seat * 13, seat * 13 + 13)) }
            return MendikotState(
                hands = hands, dealer = dealer, trumpCard = null, trump = null, revealed = false,
                trumpFrom = 0, caller = -1, tricks = emptyList(), current = null, trickWinners = emptyList(),
                played = CardSet.EMPTY, voids = List(4) { emptySet() }, config = config,
            )
        }
    }
}

/** Outcome of a finished hand. Lists are indexed by team (0 = seats 0 & 2, 1 = seats 1 & 3). */
data class MendikotResult(
    val tens: List<Int>,
    val tricks: List<Int>,
    val winner: Int,
    /** The winners captured all four tens. */
    val mendikot: Boolean,
    /** The winners took all 13 tricks. */
    val whitewash: Boolean,
    val points: Int,
)

/** 3 or 4 tens wins; at 2–2 the team with 7+ tricks wins. */
fun mendikotResult(tens: List<Int>, tricks: List<Int>, scoring: MendikotScoring): MendikotResult {
    val winner = when {
        tens[0] >= 3 -> 0
        tens[1] >= 3 -> 1
        tricks[0] >= 7 -> 0
        else -> 1
    }
    val whitewash = tricks[winner] == 13
    val mendikot = tens[winner] == 4
    val points = when (scoring) {
        MendikotScoring.SIMPLE -> 1
        MendikotScoring.WEIGHTED -> when {
            whitewash -> 3
            mendikot -> 2
            else -> 1
        }
    }
    return MendikotResult(tens, tricks, winner, mendikot, whitewash, points)
}

/** Pagat: dealer's team won → next player deals; opponents whitewashed → dealer's partner; else same dealer. */
fun mendikotNextDealer(dealer: Int, result: MendikotResult): Int = when {
    teamOf(dealer) == result.winner -> (dealer + 1) % 4
    result.whitewash -> partnerOf(dealer)
    else -> dealer
}

private fun <T> List<T>.replaced(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

object MendikotRules : Rules<MendikotState> {
    override val teams = Teams.PAIRS

    override fun toAct(s: MendikotState): Int {
        if (s.inTrumpPhase) return s.setter
        val cur = s.current
        if (cur != null) return (cur.leader + cur.cards.size) % 4
        if (s.tricks.size == 13) return -1
        return if (s.tricks.isEmpty()) s.setter else s.trickWinners.last()
    }

    /**
     * Index of the winning card. A card counts as trump if it is of [trump] and was played at
     * index ≥ [trumpFrom] (or trump is the led suit, where rank alone decides).
     */
    fun winningIndex(cards: List<Card>, trump: Suit?, trumpFrom: Int): Int {
        val led = cards[0].suit
        var best = 0
        var bestScore = -1
        for (i in cards.indices) {
            val c = cards[i]
            val score = when {
                trump != null && c.suit == trump && (i >= trumpFrom || c.suit == led) -> 200 + c.rank
                c.suit == led -> 100 + c.rank
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    fun trickWinner(t: Trick, trump: Suit?, trumpFrom: Int = 0): Int = t.seatOf(winningIndex(t.cards, trump, trumpFrom))

    /** Trump suit currently in force, or null (hidden / not yet cut). */
    fun activeTrump(s: MendikotState): Suit? = if (s.revealed) s.trump else null

    /** Index in the current trick of the card winning so far. Requires a non-empty trick. */
    fun currentWinnerIndex(s: MendikotState): Int {
        val t = s.current
        require(t != null && t.cards.isNotEmpty()) { "no trick in progress" }
        return winningIndex(t.cards, activeTrump(s), s.trumpFrom)
    }

    /** Whether playing [card] now would make it the winning card of the current trick (so far). */
    fun wouldWin(s: MendikotState, card: Card): Boolean {
        val t = s.current
        if (t == null || t.cards.isEmpty()) return true
        val led = t.ledSuit!!
        var trump = activeTrump(s)
        var from = s.trumpFrom
        if (!s.isBand && s.trump == null && card.suit != led) {
            trump = card.suit
            from = t.cards.size
        }
        return winningIndex(t.cards + card, trump, from) == t.cards.size
    }

    /** CallReveal is allowed: trump hidden and the seat to act is void in the led suit (or is the empty-handed setter). */
    fun canReveal(s: MendikotState): Boolean {
        if (!s.trumpHidden) return false
        val seat = toAct(s)
        if (seat < 0) return false
        val hand = s.hands[seat]
        if (seat == s.setter && hand.isEmpty()) return true
        val t = s.current ?: return false
        return t.cards.isNotEmpty() && !hand.has(t.ledSuit!!)
    }

    /** The setter must play but holds only the hidden indicator: revealing is the only option. */
    fun mustReveal(s: MendikotState): Boolean {
        if (!s.trumpHidden) return false
        val seat = toAct(s)
        return seat >= 0 && seat == s.setter && s.hands[seat].isEmpty()
    }

    fun legalPlays(s: MendikotState): CardSet {
        val seat = toAct(s)
        if (seat < 0 || s.inTrumpPhase) return CardSet.EMPTY
        val hand = s.hands[seat]
        val trump = s.trump
        if (s.caller == seat && trump != null) {
            val trumps = hand.ofSuit(trump)
            return if (trumps.isEmpty()) hand else trumps
        }
        val t = s.current
        if (t == null || t.cards.isEmpty()) return hand
        val led = t.ledSuit!!
        return if (hand.has(led)) hand.ofSuit(led) else hand
    }

    override fun legalActions(s: MendikotState): List<Action> {
        val seat = toAct(s)
        if (seat < 0) return emptyList()
        if (s.inTrumpPhase) return s.hands[seat].map { Action.SetTrump(it, it.suit) }
        if (mustReveal(s)) return listOf(Action.CallReveal)
        val plays: List<Action> = legalPlays(s).map { Action.Play(it) }
        return if (canReveal(s)) plays + Action.CallReveal else plays
    }

    override fun apply(s: MendikotState, a: Action): MendikotState {
        val seat = toAct(s)
        require(seat >= 0) { "hand is over" }
        if (s.inTrumpPhase) {
            require(a is Action.SetTrump) { "expected a trump card, got $a" }
            val card = a.card ?: throw IllegalArgumentException("band hukum needs a face-down card")
            require(card in s.hands[seat] && a.suit == card.suit) { "illegal trump choice $a" }
            return s.copy(hands = s.hands.replaced(seat, s.hands[seat] - card), trumpCard = card, trump = card.suit)
        }
        if (a is Action.CallReveal) {
            require(canReveal(s)) { "seat $seat may not call for trump now" }
            val tc = s.trumpCard ?: throw IllegalStateException("no hidden trump")
            val h = s.setter
            return s.copy(
                hands = s.hands.replaced(h, s.hands[h] + tc),
                revealed = true,
                caller = seat,
                trumpFrom = s.current?.cards?.size ?: 0,
            )
        }
        require(a is Action.Play) { "expected a card, got $a" }
        val card = a.card
        require(card in legalPlays(s)) { "illegal play $card by seat $seat" }
        val t = s.current ?: Trick(seat, emptyList())
        var trump = s.trump
        var revealed = s.revealed
        var trumpFrom = s.trumpFrom
        var voids = s.voids
        if (t.cards.isNotEmpty()) {
            val led = t.ledSuit!!
            if (card.suit != led) {
                voids = voids.replaced(seat, voids[seat] + led)
                if (!s.isBand && trump == null) {
                    trump = card.suit
                    revealed = true
                    trumpFrom = t.cards.size
                }
            }
        }
        // A caller who plays a non-trump after asking holds no trumps.
        val callerTrump = s.trump
        if (s.caller == seat && callerTrump != null && card.suit != callerTrump) {
            voids = voids.replaced(seat, voids[seat] + callerTrump)
        }
        val hands = s.hands.replaced(seat, s.hands[seat] - card)
        val trick = Trick(t.leader, t.cards + card)
        if (!trick.isComplete) {
            return s.copy(
                hands = hands, current = trick, played = s.played + card, voids = voids,
                trump = trump, revealed = revealed, trumpFrom = trumpFrom, caller = -1,
            )
        }
        val w = trickWinner(trick, if (revealed) trump else null, trumpFrom)
        return s.copy(
            hands = hands, current = null, tricks = s.tricks + trick, trickWinners = s.trickWinners + w,
            played = s.played + card, voids = voids, trump = trump, revealed = revealed, trumpFrom = 0, caller = -1,
        )
    }

    /** Team-shared, zero-sum: ± hand points plus small tie-breaks for tens and tricks. */
    override fun utility(s: MendikotState): DoubleArray {
        val tens = intArrayOf(s.tensOf(0), s.tensOf(1))
        val tricks = intArrayOf(s.tricksOf(0), s.tricksOf(1))
        val res = if (isOver(s)) s.result() else null
        val team = DoubleArray(2) { t ->
            val pts = if (res == null) 0 else if (res.winner == t) res.points else -res.points
            pts + (tens[t] - tens[1 - t]) / 10.0 + (tricks[t] - tricks[1 - t]) / 200.0
        }
        return DoubleArray(4) { team[teamOf(it)] }
    }

    /**
     * Resamples hidden cards for [viewer]. A non-setter viewer who hasn't seen the indicator gets
     * a fresh indicator drawn from the unseen cards (dealt through the viewer's own empty slot so
     * it is consistent with everyone's voids), and trump becomes its suit. After a reveal the
     * indicator is public: if unplayed it stays pinned in the setter's hand.
     */
    override fun winnerOf(s: MendikotState, t: Trick): Int? {
        val i = s.tricks.indexOf(t)
        return if (i >= 0 && i < s.trickWinners.size) s.trickWinners[i] else null
    }

    override fun determinize(s: MendikotState, viewer: Int, rng: Rng): MendikotState {
        val h = s.setter
        val tc = s.trumpCard
        val hideFromViewer = s.trumpHidden && viewer != h
        val pinned: Card? = if (s.isBand && tc != null && s.revealed && viewer != h && tc in s.hands[h]) tc else null
        var unseen = CardSet.FULL - s.played - s.hands[viewer]
        if (tc != null && !hideFromViewer) unseen -= tc
        val needs = IntArray(4) { if (it == viewer) 0 else s.hands[it].size }
        if (pinned != null) needs[h] -= 1
        var voids = s.voids
        if (hideFromViewer) {
            needs[viewer] = 1
            voids = voids.replaced(viewer, emptySet())
        }
        val dealt = sampleHands(unseen.toList(), needs, voids, rng)
        val hands = List(4) { seat ->
            when {
                seat == viewer -> s.hands[seat]
                seat == h && pinned != null -> CardSet.of(dealt[seat]) + pinned
                else -> CardSet.of(dealt[seat])
            }
        }
        if (hideFromViewer) {
            val indicator = dealt[viewer][0]
            return s.copy(hands = hands, trumpCard = indicator, trump = indicator.suit)
        }
        return s.copy(hands = hands)
    }
}

/**
 * Fast heuristic used by Easy opponents and Monte-Carlo playouts. Only reads what the seat to
 * act may know (the hidden trump suit is used only by the setter).
 */
object MendikotPolicy : Policy<MendikotState> {
    override fun choose(s: MendikotState, legal: List<Action>, rng: Rng): Action {
        if (legal.size == 1) return legal[0]
        val seat = MendikotRules.toAct(s)
        if (s.inTrumpPhase) return trumpChoice(s.hands[seat])
        val plays = legal.mapNotNull { (it as? Action.Play)?.card }
        if (plays.isEmpty()) return legal[0]
        val canReveal = legal.any { it is Action.CallReveal }
        val t = s.current
        return if (t == null || t.cards.isEmpty()) Action.Play(lead(s, seat, plays)) else follow(s, seat, t, plays, canReveal)
    }

    /** Hide a low non-ten card from the longest suit (ties: the stronger suit). */
    fun trumpChoice(hand: CardSet): Action.SetTrump {
        require(!hand.isEmpty()) { "empty hand" }
        val suit = Suit.entries.maxBy { su ->
            val cs = hand.ofSuit(su)
            cs.size * 1000 + cs.sumOf { it.rank }
        }
        val cs = hand.ofSuit(suit).toList()
        val card = cs.filter { it.rank != 10 }.minByOrNull { it.rank } ?: cs.minBy { it.rank }
        return Action.SetTrump(card, card.suit)
    }

    /** The trump suit as far as [seat] knows it. */
    private fun knownTrump(s: MendikotState, seat: Int): Suit? =
        if (s.revealed || (s.isBand && seat == s.setter)) s.trump else null

    /** Unplayed cards that [seat] doesn't know the location of (others' hands, unseen indicator). */
    private fun outside(s: MendikotState, seat: Int): CardSet {
        var o = CardSet.FULL - s.played - s.hands[seat]
        val tc = s.trumpCard
        if (tc != null && seat == s.setter) o -= tc
        return o
    }

    private fun isMaster(c: Card, outside: CardSet): Boolean = outside.ofSuit(c.suit).none { it.rank > c.rank }

    private fun strength(c: Card, trump: Suit?): Int = if (c.suit == trump) 100 + c.rank else c.rank

    private fun lead(s: MendikotState, seat: Int, plays: List<Card>): Card {
        val trump = knownTrump(s, seat)
        val out = outside(s, seat)
        val hand = s.hands[seat]
        // Masters (aces first) in side suits whose ten is still out: flush the tens.
        plays.filter { it.suit != trump && it.rank != 10 && isMaster(it, out) && Card.of(it.suit, 10) in out }
            .maxByOrNull { it.rank }?.let { return it }
        plays.firstOrNull { it.rank == 14 && it.suit != trump && out.has(it.suit) }?.let { return it }
        // Otherwise low from the longest side suit; never an unprotected ten.
        val noTen = plays.filter { it.rank != 10 || isMaster(it, out) }.ifEmpty { plays }
        val side = noTen.filter { it.suit != trump }.ifEmpty { noTen }
        val suit = side.map { it.suit }.distinct().maxBy { hand.ofSuit(it).size }
        return side.filter { it.suit == suit }.minBy { it.rank }
    }

    /** [card] can't be beaten by anything still out, nor trumped by a known-void opponent still to play. */
    private fun holds(s: MendikotState, t: Trick, card: Card, team: Int, remaining: List<Int>, out: CardSet): Boolean {
        if (!isMaster(card, out)) return false
        val led = t.ledSuit!!
        if (card.suit != led) return true // an off-suit winner is a trump; master trump holds
        if (MendikotRules.activeTrump(s) == led) return true
        return remaining.none { teamOf(it) != team && led in s.voids[it] }
    }

    private fun follow(s: MendikotState, seat: Int, t: Trick, plays: List<Card>, canReveal: Boolean): Action {
        val led = t.ledSuit!!
        val trump = knownTrump(s, seat)
        val out = outside(s, seat)
        val hand = s.hands[seat]
        val team = teamOf(seat)
        val wi = MendikotRules.currentWinnerIndex(s)
        val partnerWinning = teamOf(t.seatOf(wi)) == team
        val tensInTrick = t.cards.count { it.rank == 10 }
        val last = t.cards.size == 3
        val remaining = ((t.cards.size + 1)..3).map { t.seatOf(it) }

        if (canReveal && tensInTrick > 0 && !partnerWinning) return Action.CallReveal

        // Cut hukum, void, no trump yet: whatever we play becomes trump and wins for now.
        if (!s.isBand && s.trump == null && !hand.has(led)) {
            return Action.Play(cutCard(hand, plays, high = tensInTrick > 0 || !partnerWinning))
        }

        val surely = partnerWinning && (last || holds(s, t, t.cards[wi], team, remaining, out))
        if (surely) {
            plays.firstOrNull { it.rank == 10 }?.let { return Action.Play(it) }
            return Action.Play(discard(hand, plays, trump))
        }
        if (!partnerWinning) {
            val winners = plays.filter { MendikotRules.wouldWin(s, it) }
            if (winners.isNotEmpty()) {
                if (last) {
                    winners.firstOrNull { it.rank == 10 }?.let { return Action.Play(it) }
                    return Action.Play(winners.minBy { strength(it, trump) })
                }
                val safe = winners.filter { holds(s, t, it, team, remaining, out) }
                safe.firstOrNull { it.rank == 10 }?.let { return Action.Play(it) }
                if (tensInTrick > 0) {
                    val pool = safe.ifEmpty { winners.filter { it.rank != 10 } }.ifEmpty { winners }
                    return Action.Play(pool.minBy { strength(it, trump) })
                }
                if (safe.isNotEmpty()) return Action.Play(safe.minBy { strength(it, trump) })
            }
        }
        return Action.Play(discard(hand, plays, trump))
    }

    /** Pick the suit to make trump: the longest, favouring one holding a ten. */
    private fun cutCard(hand: CardSet, plays: List<Card>, high: Boolean): Card {
        val suit = plays.map { it.suit }.distinct().maxBy { su ->
            val cs = hand.ofSuit(su)
            cs.size * 2 + (if (Card.of(su, 10) in cs) 3 else 0)
        }
        val cs = plays.filter { it.suit == suit }
        val noTen = cs.filter { it.rank != 10 }
        return if (high) noTen.maxByOrNull { it.rank } ?: cs.maxBy { it.rank }
        else noTen.minByOrNull { it.rank } ?: cs.minBy { it.rank }
    }

    /** Lowest card from the shortest suit, keeping tens, trumps and high honours where possible. */
    private fun discard(hand: CardSet, plays: List<Card>, trump: Suit?): Card {
        val noTen = plays.filter { it.rank != 10 }.ifEmpty { plays }
        val pool = noTen.filter { it.suit != trump }.ifEmpty { noTen }
        return pool.minWith(
            compareBy<Card>({ if (it.rank >= 12) 1 else 0 }, { hand.ofSuit(it.suit).size }, { it.rank }),
        )
    }
}
