package com.tashlark.engine.callbreak

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
import com.tashlark.engine.seatName

/** Match state: totals in tenths per seat, one entry per finished hand. */
data class CallBreakMatch(
    val seed: Long,
    val config: CallBreakConfig,
    val firstDealer: Int,
    val handsPlayed: Int,
    val totals: List<Int>,
    val history: List<List<Int>>,
) {
    val dealer: Int get() = (firstDealer + handsPlayed) % 4
}

fun formatTenths(t: Int): String {
    val sign = if (t < 0) "−" else ""
    val a = kotlin.math.abs(t)
    return "$sign${a / 10}.${a % 10}"
}

class CallBreakGame(private val config: CallBreakConfig = CallBreakConfig()) : CardGame<CallBreakState, CallBreakMatch> {
    override val id = "callbreak"
    override val title = "Call Break"
    override val rules = CallBreakRules
    override val policy = CallBreakPolicy

    override fun newMatch(seed: Long) = CallBreakMatch(
        seed = seed, config = config, firstDealer = Rng(seed).nextInt(4),
        handsPlayed = 0, totals = List(4) { 0 }, history = emptyList(),
    )

    override fun deal(match: CallBreakMatch): CallBreakState =
        CallBreakState.deal(match.dealer, match.config, Rng(mixSeed(match.seed, match.handsPlayed.toLong())))

    override fun finishHand(match: CallBreakMatch, hand: CallBreakState): CallBreakMatch {
        require(rules.isOver(hand)) { "hand not finished" }
        val deltas = List(4) { callBreakScoreTenths(hand.bids[it] ?: 0, hand.won[it]) }
        return match.copy(
            handsPlayed = match.handsPlayed + 1,
            totals = List(4) { match.totals[it] + deltas[it] },
            history = match.history + listOf(deltas),
        )
    }

    override fun isMatchOver(match: CallBreakMatch) = match.handsPlayed >= match.config.rounds

    override fun winners(match: CallBreakMatch): Set<Int> {
        val best = match.totals.max()
        return match.totals.indices.filter { match.totals[it] == best }.toSet()
    }

    override fun table(hand: CallBreakState): TableView {
        val status = List(4) { seat ->
            val bid = hand.bids[seat]
            if (bid == null) "Bidding…" else "Bid $bid · Won ${hand.won[seat]}"
        }
        val phase = when {
            rules.isOver(hand) -> Phase.OVER
            !hand.biddingDone -> Phase.BIDDING
            else -> Phase.PLAY
        }
        val headline = when (phase) {
            Phase.BIDDING -> "Bidding · spades are trump"
            Phase.OVER -> "Hand over"
            else -> "Trick ${hand.tricks.size + 1} of 13 · spades are trump"
        }
        val prompt = when {
            phase == Phase.BIDDING && rules.toAct(hand) == 0 -> "How many tricks will you take?"
            phase == Phase.PLAY && rules.toAct(hand) == 0 -> "Your turn"
            else -> null
        }
        return TableView(phase, Suit.SPADES, false, -1, status, headline, prompt)
    }

    override fun summary(match: CallBreakMatch, hand: CallBreakState): HandSummary = HandSummary(
        title = "Round ${match.handsPlayed} of ${match.config.rounds}",
        rows = (0..3).map { seat ->
            val bid = hand.bids[seat] ?: 0
            val delta = callBreakScoreTenths(bid, hand.won[seat])
            Triple(seatName(seat), "Bid $bid · Won ${hand.won[seat]}", (if (delta > 0) "+" else "") + formatTenths(delta))
        },
        note = if (hand.won[0] >= (hand.bids[0] ?: 0)) "You made your bid." else "You fell short this round.",
    )

    override fun standings(match: CallBreakMatch): List<Standing> {
        val best = match.totals.max()
        return (0..3).map { Standing(seatName(it), formatTenths(match.totals[it]), match.totals[it] == best && match.handsPlayed > 0) }
    }

    override fun progress(match: CallBreakMatch): String =
        if (isMatchOver(match)) "Match over" else "Round ${match.handsPlayed + 1} of ${match.config.rounds}"

    override fun aiSpecial(hand: CallBreakState, seat: Int, skill: Skill, rng: Rng): Action? {
        if (hand.biddingDone) return null
        val samples = when (skill) { Skill.EASY -> 0; Skill.MEDIUM -> 40; Skill.HARD -> 120 }
        return Action.Bid(callBreakSimulatedBid(hand, seat, samples, rng))
    }

    override fun rulesText(): List<String> = listOf(
        "Four players, each on their own. All 52 cards are dealt, 13 each. Spades are always trump.",
        "Before play, everyone bids how many tricks they will take (1 to ${config.maxBid}).",
        "Follow the suit that was led. If you can, you must play a card that beats the best card so far." +
            if (config.mustBeatWhenFollowing) "" else " (Off in your rules: any card of the suit.)",
        "If you have none of the led suit, you must play a spade" +
            (if (config.mustOvertrump) ", higher than any spade already played if you can." else ".") +
            " With no spades, play anything.",
        "The highest spade wins the trick; with no spade, the highest card of the led suit wins.",
        "Make your bid and you score the bid, plus 0.1 for each extra trick. Fall short and you lose your bid.",
        "After ${config.rounds} rounds, the highest total wins.",
    )

    override fun explain(hand: CallBreakState, action: Action): String? {
        if (action is Action.Bid) return "Your hand counts to about ${action.value} likely tricks."
        val card = (action as? Action.Play)?.card ?: return null
        val t = hand.current
        val need = (hand.bids[0] ?: 0) - hand.won[0]
        return when {
            t == null || t.cards.isEmpty() -> if (need > 0) "Lead ${card} to push for the tricks you still need." else "Lead low: your bid is already made."
            need <= 0 -> "Your bid is made, so stay low and keep out of the way."
            CallBreakRules.beats(card, CallBreakRules.currentWinningCard(t)!!, t.ledSuit!!) -> "${card} should take this trick."
            else -> "You can't win this one cheaply, so play your lowest."
        }
    }
}
