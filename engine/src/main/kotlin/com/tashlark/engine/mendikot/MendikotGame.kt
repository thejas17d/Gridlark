package com.tashlark.engine.mendikot

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
import com.tashlark.engine.teamOf

/** Match state. [points] is per team (0 = you & partner, 1 = opponents). */
data class MendikotMatch(
    val seed: Long,
    val config: MendikotConfig,
    /** Dealer of the next hand. */
    val dealer: Int,
    val handsPlayed: Int,
    val points: List<Int>,
    val history: List<MendikotResult>,
)

class MendikotGame(private val config: MendikotConfig = MendikotConfig()) : CardGame<MendikotState, MendikotMatch> {
    override val id = "mendikot"
    override val title = "Mendikot"
    override val rules = MendikotRules
    override val policy = MendikotPolicy

    override fun newMatch(seed: Long) = MendikotMatch(
        seed = seed, config = config, dealer = Rng(seed).nextInt(4),
        handsPlayed = 0, points = listOf(0, 0), history = emptyList(),
    )

    override fun deal(match: MendikotMatch): MendikotState =
        MendikotState.deal(match.dealer, match.config, Rng(mixSeed(match.seed, match.handsPlayed.toLong())))

    override fun finishHand(match: MendikotMatch, hand: MendikotState): MendikotMatch {
        require(rules.isOver(hand)) { "hand not finished" }
        val r = hand.result()
        return match.copy(
            dealer = mendikotNextDealer(match.dealer, r),
            handsPlayed = match.handsPlayed + 1,
            points = List(2) { if (it == r.winner) match.points[it] + r.points else match.points[it] },
            history = match.history + r,
        )
    }

    override fun isMatchOver(match: MendikotMatch) = match.points.any { it >= match.config.target }

    override fun winners(match: MendikotMatch): Set<Int> {
        if (!isMatchOver(match)) return emptySet()
        val team = if (match.points[0] >= match.points[1]) 0 else 1
        return setOf(team, team + 2)
    }

    private fun suitName(s: Suit?): String = s?.symbol ?: "?"

    override fun table(hand: MendikotState): TableView {
        val over = rules.isOver(hand)
        val phase = when {
            over -> Phase.OVER
            hand.inTrumpPhase -> Phase.TRUMP
            else -> Phase.PLAY
        }
        val seat0Knows = hand.revealed || (hand.isBand && hand.setter == 0 && hand.trumpCard != null)
        val trump = if (seat0Knows) hand.trump else null
        val trumpHidden = hand.trumpHidden && hand.setter != 0
        val holder = if (hand.isBand) hand.setter else -1
        val status = List(4) { seat ->
            val base = "Tens ${hand.seatTens(seat)} · Tricks ${hand.seatTricks(seat)}"
            when {
                hand.isBand && seat == hand.setter && hand.inTrumpPhase -> "Setter"
                hand.isBand && seat == hand.setter -> "Setter · $base"
                else -> base
            }
        }
        val tensLine = "Tens: you ${hand.tensOf(0)}, them ${hand.tensOf(1)}"
        val trumpLine = when {
            hand.revealed -> "Trump ${suitName(hand.trump)}"
            !hand.isBand -> "No trump yet"
            hand.setter == 0 -> "Trump ${suitName(hand.trump)} (hidden)"
            else -> "Trump hidden"
        }
        val headline = when (phase) {
            Phase.TRUMP ->
                if (hand.setter == 0) "You hide the trump card this hand"
                else "${seatName(hand.setter)} is hiding a trump card"
            Phase.OVER -> {
                val who = if (hand.result().winner == 0) "Your team" else "Their team"
                "$who wins the hand · $tensLine"
            }
            else -> "$trumpLine · $tensLine"
        }
        val prompt = if (over || rules.toAct(hand) != 0) null else promptFor(hand)
        val own = if (hand.isBand && hand.setter == 0 && !hand.revealed) hand.trumpCard else null
        return TableView(phase, trump, trumpHidden, holder, status, headline, prompt, own)
    }

    private fun promptFor(hand: MendikotState): String {
        if (hand.inTrumpPhase) return "Pick a card to hide as trump"
        if (rules.mustReveal(hand)) return "Only your hidden trump is left: reveal it"
        val trump = hand.trump
        if (hand.caller == 0 && trump != null) {
            return if (hand.hands[0].has(trump)) "Trump is ${trump.symbol}: you must play a trump"
            else "Trump is ${trump.symbol}: you have none, play any card"
        }
        if (rules.canReveal(hand)) return "You can't follow suit: play any card, or ask to reveal trump"
        val t = hand.current
        if (!hand.isBand && trump == null && t != null && t.cards.isNotEmpty() && !hand.hands[0].has(t.ledSuit!!)) {
            return "You can't follow suit: the card you play makes its suit trump"
        }
        return "Your turn"
    }

    override fun summary(match: MendikotMatch, hand: MendikotState): HandSummary {
        val r = hand.result()
        fun row(team: Int, label: String) = Triple(
            label,
            "Tens ${r.tens[team]} · Tricks ${r.tricks[team]}",
            if (r.winner == team) "+${r.points}" else "0",
        )
        val note = when {
            r.whitewash -> "Whitewash!"
            r.mendikot -> "Mendikot!"
            r.tens[0] == 2 && r.tens[1] == 2 -> "Tens split 2–2: decided on tricks."
            else -> null
        }
        return HandSummary(
            title = if (r.winner == 0) "Your team wins the hand" else "Their team wins the hand",
            rows = listOf(row(0, "Your team"), row(1, "Their team")),
            note = note,
        )
    }

    override fun standings(match: MendikotMatch): List<Standing> = listOf(
        Standing("Your team", match.points[0].toString(), match.points[0] > match.points[1]),
        Standing("Their team", match.points[1].toString(), match.points[1] > match.points[0]),
    )

    override fun progress(match: MendikotMatch): String =
        if (isMatchOver(match)) "Match over" else "First to ${match.config.target}"

    override fun aiSpecial(hand: MendikotState, seat: Int, skill: Skill, rng: Rng): Action? {
        if (!hand.inTrumpPhase || rules.toAct(hand) != seat) return null
        return MendikotPolicy.trumpChoice(hand.hands[seat])
    }

    override fun rulesText(): List<String> {
        val out = mutableListOf(
            "Four players in two partnerships: you and ${seatName(2)} against the other two. " +
                "All 52 cards are dealt, 13 each, and aces are high.",
            "The aim is to capture tens. The team that takes three or four tens wins the hand; " +
                "if the tens split two each, the team with seven or more tricks wins.",
        )
        when (config.trumpMode) {
            MendikotTrumpMode.BAND_HUKUM -> {
                out += "Before play, the player after the dealer picks one card and lays it face down. " +
                    "Its suit is the hidden trump, and they play from their other twelve cards."
                out += "You must follow the suit that was led. If you can't, you may play any card, " +
                    "or first ask for the trump to be revealed."
                out += "When trump is revealed, the hidden card goes back to its owner and its suit is trump " +
                    "from then on. Whoever asked must play a trump if they have one, and trumps played " +
                    "earlier in that same trick don't count."
                out += "Until trump is revealed, the highest card of the led suit wins. After that, " +
                    "the highest trump wins, or the highest card of the led suit if no trump was played."
            }
            MendikotTrumpMode.CUT_HUKUM -> {
                out += "There is no trump at first. The first player who can't follow suit plays any card, " +
                    "and its suit becomes trump at once, for that trick and the rest of the hand."
                out += "Always follow the suit that was led if you can; otherwise play any card."
                out += "The highest trump wins a trick; if no trump is played, the highest card of the led suit wins."
            }
        }
        out += when (config.scoring) {
            MendikotScoring.WEIGHTED ->
                "A won hand scores 1 point. Taking all four tens is a mendikot, worth 2; " +
                    "taking all 13 tricks is a whitewash, worth 3."
            MendikotScoring.SIMPLE ->
                "Each hand won scores 1 point, even a mendikot (all four tens) or a whitewash (all 13 tricks)."
        }
        out += "The first team to ${config.target} points wins the match. " +
            "If the dealer's team loses a hand, the same player deals again."
        return out
    }

    override fun explain(hand: MendikotState, action: Action): String? {
        return when (action) {
            is Action.SetTrump -> "A low card from your longest suit gives you the most trumps and keeps your tens."
            is Action.CallReveal ->
                if (rules.mustReveal(hand)) "Only your hidden trump card is left, so it must be revealed."
                else "You can't follow suit: revealing trump lets you cut in and fight for the tens."
            is Action.Play -> {
                val card = action.card
                val t = hand.current
                if (t == null || t.cards.isEmpty()) {
                    return if (card.rank == 14) "Lead the ace to draw out the tens."
                    else "Lead low from a long suit and keep your tens safe."
                }
                val partnerWinning = teamOf(t.seatOf(rules.currentWinnerIndex(hand))) == 0
                when {
                    card.rank == 10 && partnerWinning ->
                        "Your partner is winning this trick, so it's a good moment to drop a ten."
                    rules.wouldWin(hand, card) && t.cards.any { it.rank == 10 } -> "$card takes the trick and the ten in it."
                    rules.wouldWin(hand, card) -> "$card should take this trick."
                    else -> "You can't win this one cheaply, so play low and keep your tens."
                }
            }
            else -> null
        }
    }
}
