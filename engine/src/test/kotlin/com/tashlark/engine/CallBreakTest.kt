package com.tashlark.engine

import com.tashlark.engine.callbreak.CallBreakConfig
import com.tashlark.engine.callbreak.CallBreakPolicy
import com.tashlark.engine.callbreak.CallBreakRules
import com.tashlark.engine.callbreak.CallBreakState
import com.tashlark.engine.callbreak.callBreakScoreTenths
import com.tashlark.engine.callbreak.callBreakSimulatedBid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallBreakTest {

    private fun c(s: String) = Card.parse(s)
    private fun set(vararg s: String) = CardSet.of(s.map { Card.parse(it) })

    /** A hand-built mid-trick state for rule checks. Seat 0 leads; we test seat [toAct]'s options. */
    private fun midTrick(hand: CardSet, trick: List<String>, config: CallBreakConfig = CallBreakConfig()): CallBreakState {
        val seat = trick.size // leader is seat 0
        val hands = List(4) { if (it == seat) hand else set("2C") }
        return CallBreakState(
            hands = hands, dealer = 3, bids = listOf(1, 1, 1, 1), tricks = emptyList(),
            current = Trick(0, trick.map { c(it) }), won = List(4) { 0 }, played = CardSet.EMPTY,
            voids = List(4) { emptySet() }, config = config,
        )
    }

    @Test
    fun scoring_matches_common_rules() {
        assertEquals(30, callBreakScoreTenths(3, 3))
        assertEquals(32, callBreakScoreTenths(3, 5))
        assertEquals(-40, callBreakScoreTenths(4, 3))
        assertEquals(130, callBreakScoreTenths(13, 13))
    }

    @Test
    fun must_follow_and_must_beat_when_following() {
        val s = midTrick(set("3H", "QH", "KS", "AD"), listOf("JH"))
        assertEquals(set("QH"), CallBreakRules.legalPlays(s))
        // Can't beat: any heart.
        val s2 = midTrick(set("3H", "5H", "KS"), listOf("JH"))
        assertEquals(set("3H", "5H"), CallBreakRules.legalPlays(s2))
        // Variant off: any heart.
        val s3 = midTrick(set("3H", "QH", "KS"), listOf("JH"), CallBreakConfig(mustBeatWhenFollowing = false))
        assertEquals(set("3H", "QH"), CallBreakRules.legalPlays(s3))
    }

    @Test
    fun following_after_a_trump_need_not_beat() {
        // Hearts led, someone trumped: following hearts can't win, so any heart is fine.
        val s = midTrick(set("3H", "AH", "2S"), listOf("JH", "4S"))
        assertEquals(set("3H", "AH"), CallBreakRules.legalPlays(s))
    }

    @Test
    fun void_must_trump_and_overtrump() {
        val s = midTrick(set("3S", "9S", "AD"), listOf("JH"))
        assertEquals(set("3S", "9S"), CallBreakRules.legalPlays(s))
        val s2 = midTrick(set("3S", "9S", "AD"), listOf("JH", "5S"))
        assertEquals(set("9S"), CallBreakRules.legalPlays(s2))
        // Can't overtrump: anything.
        val s3 = midTrick(set("3S", "AD"), listOf("JH", "5S"))
        assertEquals(set("3S", "AD"), CallBreakRules.legalPlays(s3))
        // No spades: anything.
        val s4 = midTrick(set("3D", "AD"), listOf("JH"))
        assertEquals(set("3D", "AD"), CallBreakRules.legalPlays(s4))
    }

    @Test
    fun spades_led_must_beat_highest_spade() {
        val s = midTrick(set("3S", "QS", "AH"), listOf("10S"))
        assertEquals(set("QS"), CallBreakRules.legalPlays(s))
    }

    @Test
    fun trick_winner_trump_beats_led_suit() {
        val t = Trick(1, listOf(c("AH"), c("2S"), c("KH"), c("3D")))
        assertEquals(2, CallBreakRules.trickWinner(t))
        val t2 = Trick(1, listOf(c("10H"), c("JH"), c("KD"), c("9H")))
        assertEquals(2, CallBreakRules.trickWinner(t2))
    }

    @Test
    fun full_deals_conserve_cards_and_stay_legal() {
        val rng = Rng(42)
        repeat(2000) { n ->
            var s = CallBreakState.deal(n % 4, CallBreakConfig(), rng)
            assertTrue(s.hands.all { it.has(Suit.SPADES) })
            assertEquals(CardSet.FULL, s.hands.fold(CardSet.EMPTY) { a, h -> a + h })
            var guard = 0
            while (!CallBreakRules.isOver(s)) {
                val legal = CallBreakRules.legalActions(s)
                assertFalse(legal.isEmpty())
                val a = CallBreakPolicy.choose(s, legal, rng)
                assertTrue("policy chose illegal $a", a in legal)
                s = CallBreakRules.apply(s, a)
                assertTrue(++guard < 100)
            }
            assertEquals(13, s.tricks.size)
            assertEquals(13, s.won.sum())
            assertEquals(CardSet.FULL, s.played)
            assertTrue(s.hands.all { it.isEmpty() })
        }
    }

    @Test
    fun determinize_keeps_viewer_hand_and_respects_voids() {
        val rng = Rng(7)
        var s = CallBreakState.deal(0, CallBreakConfig(), rng)
        s = s.copy(bids = listOf(2, 3, 2, 3))
        // Play a few tricks to create voids.
        repeat(20) { s = CallBreakRules.apply(s, CallBreakPolicy.choose(s, CallBreakRules.legalActions(s), rng)) }
        repeat(200) {
            val w = CallBreakRules.determinize(s, 1, rng)
            assertEquals(s.hands[1], w.hands[1])
            for (seat in 0..3) {
                assertEquals(s.hands[seat].size, w.hands[seat].size)
                for (v in s.voids[seat]) assertFalse(w.hands[seat].has(v))
            }
            val all = w.hands.fold(w.played) { a, h -> a + h }
            assertEquals(CardSet.FULL, all)
        }
    }

    @Test
    fun ai_plays_legal_and_beats_random_play() {
        val rng = Rng(11)
        val ai = MonteCarloAi(CallBreakRules, CallBreakPolicy)
        var aiScore = 0.0
        var otherScore = 0.0
        repeat(30) { n ->
            var s = CallBreakState.deal(n % 4, CallBreakConfig(), rng)
            while (!CallBreakRules.isOver(s)) {
                val seat = CallBreakRules.toAct(s)
                val legal = CallBreakRules.legalActions(s)
                val a = when {
                    !s.biddingDone && seat == 0 -> Action.Bid(callBreakSimulatedBid(s, 0, 30, rng))
                    seat == 0 -> ai.decide(s, 0, Skill.MEDIUM, rng)
                    !s.biddingDone -> legal[rng.nextInt(minOf(4, legal.size))]
                    else -> legal[rng.nextInt(legal.size)]
                }
                assertTrue(a in legal)
                s = CallBreakRules.apply(s, a)
            }
            val u = CallBreakRules.utility(s)
            aiScore += u[0]
            otherScore += (u[1] + u[2] + u[3]) / 3
        }
        assertTrue("AI $aiScore should beat random $otherScore", aiScore > otherScore)
    }
}
