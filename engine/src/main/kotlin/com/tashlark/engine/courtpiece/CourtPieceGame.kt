package com.tashlark.engine.courtpiece

import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.CardGame
import com.tashlark.engine.CardSet
import com.tashlark.engine.HandSummary
import com.tashlark.engine.Phase
import com.tashlark.engine.Rng
import com.tashlark.engine.Skill
import com.tashlark.engine.Standing
import com.tashlark.engine.TableView
import com.tashlark.engine.mixSeed
import com.tashlark.engine.partnerOf
import com.tashlark.engine.seatName
import com.tashlark.engine.teamOf

/** Match state: points per team (team 0 = seats 0 & 2, team 1 = seats 1 & 3). */
data class CourtPieceMatch(
    val seed: Long,
    val config: CourtPieceConfig,
    val dealer: Int,
    val handsPlayed: Int,
    val points: List<Int>,
    val history: List<CourtPieceResult>,
)

private fun teamLabel(team: Int) = if (team == 0) "Your team" else "Their team"

class CourtPieceGame(private val config: CourtPieceConfig = CourtPieceConfig()) : CardGame<CourtPieceState, CourtPieceMatch> {
    override val id = "courtpiece"
    override val title = "Court Piece"
    override val rules = CourtPieceRules
    override val policy = CourtPiecePolicy

    override fun newMatch(seed: Long) = CourtPieceMatch(
        seed = seed, config = config, dealer = Rng(seed).nextInt(4),
        handsPlayed = 0, points = listOf(0, 0), history = emptyList(),
    )

    override fun deal(match: CourtPieceMatch): CourtPieceState =
        CourtPieceState.deal(match.dealer, match.config, Rng(mixSeed(match.seed, match.handsPlayed.toLong())))

    override fun finishHand(match: CourtPieceMatch, hand: CourtPieceState): CourtPieceMatch {
        val r = CourtPieceRules.result(hand)
        require(r != null) { "hand not finished" }
        return match.copy(
            dealer = courtPieceNextDealer(hand.dealer, r),
            handsPlayed = match.handsPlayed + 1,
            points = List(2) { if (it == r.winnerTeam) match.points[it] + r.points else match.points[it] },
            history = match.history + r,
        )
    }

    override fun isMatchOver(match: CourtPieceMatch) = match.points.any { it >= match.config.target }

    override fun winners(match: CourtPieceMatch): Set<Int> {
        if (!isMatchOver(match)) return emptySet()
        val team = if (match.points[0] >= match.points[1]) 0 else 1
        return setOf(team, team + 2)
    }

    private fun scoreLine(hand: CourtPieceState) =
        "Your team ${hand.teamTricks[0]} – ${hand.teamTricks[1]} Their team"

    override fun table(hand: CourtPieceState): TableView {
        val over = rules.isOver(hand)
        val trump = hand.trump
        val phase = when {
            over -> Phase.OVER
            trump == null -> Phase.TRUMP
            else -> Phase.PLAY
        }
        val word = if (hand.config.doubleSir) "Collected" else "Tricks"
        val status = List(4) { seat ->
            val parts = mutableListOf<String>()
            if (seat == hand.caller) {
                parts.add(if (phase == Phase.TRUMP) "Caller · choosing trump" else "Caller")
            } else if (seat == 2) {
                parts.add("Partner")
            }
            if (seat == hand.dealer) parts.add("Dealer")
            if (phase != Phase.TRUMP) parts.add("$word ${hand.teamTricks[teamOf(seat)]}")
            parts.joinToString(" · ")
        }
        val trumpText = trump?.symbol ?: ""
        val pileText = if (hand.config.doubleSir) " · Pile ${hand.pile}" else ""
        val headline = when (phase) {
            Phase.TRUMP -> if (hand.caller == 0) "You are the caller · choose trump" else "${seatName(hand.caller)} is choosing trump"
            Phase.OVER -> "Hand over · ${scoreLine(hand)}"
            else -> "Trump $trumpText · ${scoreLine(hand)}$pileText"
        }
        val prompt = when {
            phase == Phase.TRUMP && hand.caller == 0 -> "Choose trump from your first five cards"
            phase == Phase.PLAY && rules.toAct(hand) == 0 -> "Your turn"
            else -> null
        }
        return TableView(phase, trump, false, -1, status, headline, prompt)
    }

    override fun summary(match: CourtPieceMatch, hand: CourtPieceState): HandSummary {
        val r = CourtPieceRules.result(hand)
        val word = if (hand.config.doubleSir) "Collected" else "Tricks"
        val rows = (0..1).map { team ->
            val role = if (team == hand.callerTeam) " · Callers" else ""
            val delta = if (r != null && r.winnerTeam == team) "+${r.points}" else "0"
            Triple(teamLabel(team), "$word ${hand.teamTricks[team]}$role", delta)
        }
        val sweep = if (hand.config.doubleSir) "all 13 tricks" else "the first seven tricks"
        val note = when {
            r == null -> null
            r.court && r.callerTeamWon -> "Court! ${teamLabel(r.winnerTeam)} took $sweep."
            r.court -> "Goon court! ${teamLabel(r.winnerTeam)} took $sweep against the callers."
            r.callerTeamWon -> "${teamLabel(r.winnerTeam)} made the call."
            else -> "${teamLabel(r.winnerTeam)} beat the callers."
        }
        return HandSummary(title = "Hand ${match.handsPlayed}", rows = rows, note = note)
    }

    override fun standings(match: CourtPieceMatch): List<Standing> {
        val best = match.points.max()
        return (0..1).map { Standing(teamLabel(it), match.points[it].toString(), match.points[it] == best && match.handsPlayed > 0) }
    }

    override fun progress(match: CourtPieceMatch): String =
        if (isMatchOver(match)) "Match over" else "First to ${match.config.target}"

    override fun aiSpecial(hand: CourtPieceState, seat: Int, skill: Skill, rng: Rng): Action? {
        if (hand.trump != null || rules.toAct(hand) != seat) return null
        return Action.SetTrump(null, CourtPiecePolicy.chooseTrump(hand.hands[seat]))
    }

    override fun rulesText(): List<String> {
        val out = mutableListOf(
            "Four players in two teams: you and ${seatName(2)} (sitting opposite) against ${seatName(1)} and ${seatName(3)}. All 52 cards are used, aces high.",
            "Everyone gets five cards first. The player after the dealer is the caller and picks trump from those five; then the rest are dealt, 13 each." +
                if (config.redealWeakCaller) " If the caller's five cards have no jack or higher, the hand is redealt." else "",
            "The caller leads the first trick. Follow suit if you can; if you can't, play any card. You never have to trump.",
            "The highest trump wins the trick; with no trump, the highest card of the led suit wins. The winner leads the next trick.",
        )
        if (config.doubleSir) {
            out.add(
                "Double sir: won tricks pile up in the middle. A team collects the pile only when the same player wins two tricks in a row" +
                    (if (config.firstCollectionNeedsThree) " (the first collection needs at least three tricks)" else "") +
                    ", and the winner of the last trick takes whatever is left. All 13 tricks are played; collect 7 to win the hand.",
            )
            out.add("Callers' team wins: 1 point, or 3 for a court (all 13 tricks). Opponents win: 2 points, or 4 for a court.")
        } else {
            out.add("The hand ends as soon as a team has 7 tricks.")
            out.add("Callers' team wins: 1 point, or 3 for a court (the first 7 tricks in a row). Opponents win: 2 points, or 4 for a court.")
        }
        out.add("If the dealer's team wins, the next player deals. If the callers win, the same dealer deals again, or the dealer's partner after a court.")
        out.add("The first team to ${config.target} points wins the match.")
        return out
    }

    override fun explain(hand: CourtPieceState, action: Action): String? = when (action) {
        is Action.SetTrump -> "${action.suit.symbol} is your strongest suit among your first five cards."
        is Action.Play -> explainPlay(hand, action.card)
        else -> null
    }

    private fun explainPlay(hand: CourtPieceState, card: Card): String? {
        val trump = hand.trump ?: return null
        val seat = rules.toAct(hand)
        if (seat < 0) return null
        val unseen = CardSet.FULL - hand.played - hand.hands[seat]
        val master = unseen.ofSuit(card.suit).none { it.rank > card.rank }
        val t = hand.current
        if (t == null || t.cards.isEmpty()) {
            return when {
                card.suit == trump -> "Lead trump to pull the opponents' trumps."
                master -> "$card is the highest left in its suit, so lead it."
                else -> "Lead low from a long suit and keep your high cards."
            }
        }
        val led = t.ledSuit!!
        val win = CourtPieceRules.currentWinningCard(t, trump)!!
        return when {
            CourtPieceRules.currentWinnerSeat(t, trump) == partnerOf(seat) &&
                !CourtPieceRules.beats(card, win, led, trump) -> "Your partner is winning, so play low."
            CourtPieceRules.beats(card, win, led, trump) ->
                if (card.suit == trump && led != trump) "Trump in to take the trick." else "$card should take this trick."
            card.suit != led -> "You can't follow or win, so throw your least useful card."
            else -> "You can't win this one, so play your lowest."
        }
    }
}
