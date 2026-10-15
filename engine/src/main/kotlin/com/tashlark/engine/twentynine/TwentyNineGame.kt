package com.tashlark.engine.twentynine

import com.tashlark.engine.Action
import com.tashlark.engine.CardGame
import com.tashlark.engine.HandSummary
import com.tashlark.engine.Phase
import com.tashlark.engine.Rng
import com.tashlark.engine.Skill
import com.tashlark.engine.Standing
import com.tashlark.engine.Suit
import com.tashlark.engine.TableView
import com.tashlark.engine.mixSeed
import com.tashlark.engine.partnerOf
import com.tashlark.engine.seatName
import com.tashlark.engine.teamOf

/**
 * Match state. [handsDealt] counts every hand dealt (annulled ones too) and seeds the next
 * deal, so replays are deterministic. [gamePoints] per team: 0 = seats 0&2, 1 = seats 1&3.
 */
data class TwentyNineMatch(
    val seed: Long,
    val config: TwentyNineConfig,
    val dealer: Int,
    val handsDealt: Int,
    val scoredHands: Int,
    val gamePoints: List<Int>,
    val history: List<TwentyNineResult>,
)

private fun teamLabel(team: Int) = if (team == 0) "Your team" else "Their team"

private fun signed(v: Int): String = when {
    v > 0 -> "+$v"
    v < 0 -> "−${-v}"
    else -> "0"
}

class TwentyNineGame(private val config: TwentyNineConfig = TwentyNineConfig()) :
    CardGame<TwentyNineState, TwentyNineMatch> {
    override val id = "twentynine"
    override val title = "29"
    override val rules = TwentyNineRules
    override val policy = TwentyNinePolicy

    override fun newMatch(seed: Long) = TwentyNineMatch(
        seed = seed, config = config, dealer = Rng(seed).nextInt(4), handsDealt = 0, scoredHands = 0,
        gamePoints = listOf(0, 0), history = emptyList(),
    )

    override fun deal(match: TwentyNineMatch): TwentyNineState =
        TwentyNineState.deal(match.dealer, match.config, Rng(mixSeed(match.seed, match.handsDealt.toLong())))

    override fun finishHand(match: TwentyNineMatch, hand: TwentyNineState): TwentyNineMatch {
        require(rules.isOver(hand)) { "hand not finished" }
        val r = TwentyNineRules.result(hand)
        if (r.annulled) {
            return match.copy(handsDealt = match.handsDealt + 1, history = match.history + r)
        }
        return match.copy(
            dealer = (match.dealer + 1) % 4,
            handsDealt = match.handsDealt + 1,
            scoredHands = match.scoredHands + 1,
            gamePoints = List(2) { match.gamePoints[it] + r.teamDelta(it) },
            history = match.history + r,
        )
    }

    override fun isMatchOver(match: TwentyNineMatch): Boolean {
        val t = match.config.matchTarget
        return match.gamePoints.any { it >= t || it <= -t }
    }

    override fun winners(match: TwentyNineMatch): Set<Int> {
        val t = match.config.matchTarget
        val team = when {
            match.gamePoints[0] >= t || match.gamePoints[1] <= -t -> 0
            match.gamePoints[1] >= t || match.gamePoints[0] <= -t -> 1
            else -> return emptySet()
        }
        return setOf(team, team + 2)
    }

    override fun table(hand: TwentyNineState): TableView {
        val phase = hand.phase
        val d = hand.declarer
        val seat0SeesTrump = hand.revealed || d == 0
        val trump: Suit? = if (seat0SeesTrump) hand.trump else null
        val trumpHidden = hand.indicator != null && !hand.revealed && d != 0
        val trumpHolder = if (hand.indicator != null) d else -1

        val status = List(4) { seat ->
            if (phase == Phase.BIDDING) {
                val b = hand.seatBids[seat]
                when {
                    hand.passed[seat] -> "Passed"
                    b != null -> "Bid $b"
                    else -> "Bidding…"
                }
            } else {
                val parts = mutableListOf(
                    when (seat) {
                        d -> "Declarer · ${hand.bid}"
                        partnerOf(d) -> "Partner"
                        else -> "Defender"
                    },
                )
                if (hand.doubledBy == seat) parts.add("Doubled")
                if (seat == d && hand.multiplier >= 4) parts.add("Redoubled")
                if (hand.pairSeat == seat) parts.add("Pair")
                parts.joinToString(" · ")
            }
        }

        val headline = when (phase) {
            Phase.BIDDING -> if (hand.highBid > 0) "Bidding · highest ${hand.highBid}" else "Bidding · no bids yet"
            Phase.TRUMP -> "${seatName(d)} won the bid at ${hand.bid} · choosing trump"
            Phase.DOUBLING -> "Trump hidden · bid ${hand.bid}" + if (hand.multiplier > 1) " · doubled" else ""
            Phase.PLAY -> {
                val t = hand.trump
                when {
                    hand.pairSeat >= 0 -> "Pair! Target now ${hand.target}"
                    hand.revealed && t != null -> "Trump ${t.symbol} · target ${hand.target}"
                    d == 0 && t != null -> "Your trump ${t.symbol} is hidden · target ${hand.target}"
                    else -> "Trump hidden · target ${hand.target}"
                }
            }
            Phase.OVER -> {
                val r = TwentyNineRules.result(hand)
                when {
                    r.annulled -> "Trump never revealed · hand annulled"
                    r.made -> "${teamLabel(r.declarerTeam)} made ${r.declarerPoints} of ${r.target}"
                    else -> "${teamLabel(r.declarerTeam)} fell short: ${r.declarerPoints} of ${r.target}"
                }
            }
        }

        val toAct = rules.toAct(hand)
        val tr = hand.trump
        val ledNow = hand.current?.ledSuit
        val mustTrump = toAct == 0 && hand.revealCaller == 0 && hand.config.mustTrumpAfterAsking &&
            tr != null && hand.hands[0].has(tr) && (ledNow == null || !hand.hands[0].has(ledNow))
        val prompt: String? = if (toAct != 0) null else when (phase) {
            Phase.BIDDING -> when {
                hand.highBidder < 0 && hand.passed.count { it } >= 3 -> "Everyone passed: you must bid 16"
                hand.highBid > 0 -> "Bid more than ${hand.highBid}, or pass"
                else -> "Bid ${hand.config.minBid} or more, or pass"
            }
            Phase.TRUMP -> "Choose a card to set face-down as trump"
            Phase.DOUBLING -> if (hand.doubleStage <= 1) "Double the bid of ${hand.bid}?" else "You were doubled. Redouble?"
            Phase.PLAY -> when {
                TwentyNineRules.mustForceReveal(hand, 0) -> "Reveal your trump card to play it"
                mustTrump -> "Play a trump"
                TwentyNineRules.canCallReveal(hand, 0) -> "Play a card, or ask to see trump"
                else -> "Your turn"
            }
            Phase.OVER -> null
        }
        val own = if (hand.declarer == 0 && !hand.revealed) hand.indicator else null
        return TableView(phase, trump, trumpHidden, trumpHolder, status, headline, prompt, own)
    }

    override fun summary(match: TwentyNineMatch, hand: TwentyNineState): HandSummary {
        val r = TwentyNineRules.result(hand)
        val title = when {
            r.annulled -> "Hand annulled"
            r.made -> "${seatName(r.declarer)} bid ${r.bid} · made"
            else -> "${seatName(r.declarer)} bid ${r.bid} · fell short"
        }
        val rows = (0..1).map { team ->
            val pts = TwentyNineRules.teamPoints(hand, team)
            val detail = if (team == r.declarerTeam) "$pts card points · target ${r.target}" else "$pts card points"
            Triple(teamLabel(team), detail, signed(r.teamDelta(team)))
        }
        val notes = mutableListOf<String>()
        if (r.annulled) notes.add("Trump was never revealed, so nobody scores and the same dealer deals again.")
        if (r.pairSeat >= 0) notes.add("Pair held by ${seatName(r.pairSeat)}: target became ${r.target}.")
        if (r.multiplier == 2) notes.add("Doubled: worth 2 game points.")
        if (r.multiplier >= 4) notes.add("Redoubled: worth 4 game points.")
        return HandSummary(title, rows, notes.joinToString(" ").ifEmpty { null })
    }

    override fun standings(match: TwentyNineMatch): List<Standing> {
        val a = match.gamePoints[0]
        val b = match.gamePoints[1]
        return listOf(
            Standing(teamLabel(0), signed(a), a > b),
            Standing(teamLabel(1), signed(b), b > a),
        )
    }

    override fun progress(match: TwentyNineMatch): String =
        if (isMatchOver(match)) "Match over" else "First to ${match.config.matchTarget}"

    override fun aiSpecial(hand: TwentyNineState, seat: Int, skill: Skill, rng: Rng): Action? {
        val phase = hand.phase
        if (phase != Phase.BIDDING && phase != Phase.TRUMP) return null
        if (rules.toAct(hand) != seat) return null
        val legal = rules.legalActions(hand)
        if (legal.isEmpty()) return null
        return policy.choose(hand, legal, rng)
    }

    override fun rulesText(): List<String> = listOf(
        "Four players in two partnerships: you and the player opposite against the other two. Only the 7 to the ace of each suit are used, 32 cards.",
        "In every suit the order is J, 9, A, 10, K, Q, 8, 7. Jacks are worth 3 points, nines 2, aces and tens 1 each: 28 points in the deck" +
            if (config.lastTrickPoint) ", plus 1 for winning the last trick." else ".",
        "Everyone gets 4 cards and bids for how many points their team will take, from 16 up to ${config.maxBid}. Each bid must be higher; once you pass you are out. If the first three pass, the dealer must bid 16.",
        "The highest bidder picks a trump suit by placing one of their cards face down. Nobody else knows the trump yet. Then the rest of the cards are dealt, 8 each.",
        if (config.doubling) "Before the rest of the cards are dealt, the other team may double if they think the bid will fail, and the bidder may redouble." else "There is no doubling in your rules.",
        "Follow the suit that was led if you can. Until trump is revealed the highest card of the led suit wins" +
            if (config.declarerNoTrumpLeadBeforeReveal) ", and the bidder may not lead a trump unless they hold nothing else." else ".",
        "If you can't follow suit you may ask to see the trump. From then on trumps win tricks, including the trick in progress" +
            if (config.mustTrumpAfterAsking) ", and if you asked and hold a trump you must play one." else ".",
        if (config.pair) "Once trump is shown, a player holding the king and queen of trump (a pair) whose team then wins a trick moves the target by 4: down for the bidder's team, up for the defenders (16 to ${config.maxBid})." else "Pairs (king and queen of trump) have no effect in your rules.",
        "If the bidder's team reaches the target they score 1 game point, otherwise they lose 1 (times any doubling). If trump is never asked for, the hand is replayed.",
        "The first team to reach ${config.matchTarget} game points wins; a team that falls to −${config.matchTarget} loses.",
    )

    override fun explain(hand: TwentyNineState, action: Action): String? {
        val r = TwentyNineRules
        return when (action) {
            is Action.Bid -> "Your four cards are worth about ${TwentyNinePolicy.estimateBid(hand.hands[0], hand.config)} points with your partner's help."
            Action.Pass -> "Your hand isn't strong enough to go higher."
            is Action.SetTrump -> "${action.suit.label.replaceFirstChar { it.uppercase() }} is your strongest suit; hide a low one as trump."
            Action.Double -> "With two jacks you can expect to stop the bid."
            Action.Redouble -> "Your hand is strong enough to make the bid even doubled."
            Action.NoDouble -> "Not strong enough to risk a double."
            Action.CallReveal -> "You can't follow suit and the other team is winning points: ask to see trump."
            is Action.Play -> {
                val card = action.card
                val t = hand.current
                val led = t?.ledSuit
                if (t == null || led == null) {
                    if (r.points(card) > 0) "Lead $card to collect points while it is the top card left."
                    else "Lead low from your longest suit and keep your points safe."
                } else {
                    val active = r.activeTrump(hand)
                    val winIdx = r.winningIndex(t, active)
                    val partnerWinning = teamOf(t.seatOf(winIdx)) == teamOf(0)
                    when {
                        partnerWinning && r.points(card) > 0 -> "Your partner is winning: add $card's ${r.points(card)} points."
                        partnerWinning -> "Your partner is winning; keep your points for later."
                        r.beats(card, t.cards[winIdx], led, active) -> "$card takes the trick cheaply."
                        else -> "You can't win this one, so throw your least valuable card."
                    }
                }
            }
            else -> null
        }
    }
}
