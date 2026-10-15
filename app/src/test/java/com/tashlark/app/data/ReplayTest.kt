package com.tashlark.app.data

import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.HandState
import com.tashlark.engine.Rng
import com.tashlark.engine.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved matches store only the seed and the actions taken. These tests prove that replaying
 * them rebuilds exactly the same state for every game, so a resumed match never changes.
 */
class ReplayTest {

    @Test
    fun action_codec_round_trips_every_kind() {
        val actions = listOf(
            Action.Play(Card(0)), Action.Play(Card(51)), Action.Bid(1), Action.Bid(28), Action.Pass,
            Action.SetTrump(Card(17), Suit.HEARTS), Action.SetTrump(null, Suit.CLUBS),
            Action.CallReveal, Action.Double, Action.Redouble, Action.NoDouble,
        )
        for (a in actions) assertEquals(a, ActionCodec.decode(ActionCodec.encode(a)))
    }

    @Test
    fun replaying_a_logged_match_rebuilds_identical_state() {
        for (info in GameCatalog.all) {
            val game = GameCatalog.create(info.id, HouseRules())
            val rng = Rng(99)
            val seed = 123456789L
            var match = game.newMatch(seed)
            val logs = mutableListOf<List<String>>()
            // Play two full hands and part of a third with the fast policy.
            var hand: HandState = game.deal(match)
            var log = mutableListOf<String>()
            var handsDone = 0
            var steps = 0
            while (handsDone < 2 || steps < 6) {
                if (game.isMatchOver(match)) break
                if (game.rules.isOver(hand)) {
                    match = game.finishHand(match, hand)
                    logs.add(log)
                    log = mutableListOf()
                    handsDone++
                    if (game.isMatchOver(match)) break
                    hand = game.deal(match)
                    steps = 0
                    continue
                }
                val legal = game.rules.legalActions(hand)
                val a = game.policy.choose(hand, legal, rng)
                assertTrue("${info.id}: policy chose illegal $a", a in legal)
                hand = game.rules.apply(hand, a)
                log.add(ActionCodec.encode(a))
                if (handsDone >= 2) steps++
            }
            // Rebuild from the log alone.
            var m2 = game.newMatch(seed)
            for (l in logs) {
                var h = game.deal(m2)
                for (code in l) h = game.rules.apply(h, ActionCodec.decode(code))
                m2 = game.finishHand(m2, h)
            }
            assertEquals("${info.id}: standings differ after replay", game.standings(match), game.standings(m2))
            if (!game.isMatchOver(match)) {
                var h2 = game.deal(m2)
                for (code in log) h2 = game.rules.apply(h2, ActionCodec.decode(code))
                assertEquals("${info.id}: hands differ after replay", hand.hands, h2.hands)
                assertEquals("${info.id}: tricks differ after replay", hand.tricks, h2.tricks)
            }
        }
    }

    @Test
    fun saved_rules_round_trip() {
        val r = HouseRules(cbMustBeat = false, cbMaxBid = 8, cbRounds = 10, tnPair = false, tnDoubling = false,
            tnLastTrickPoint = true, cpTarget = 15, mkCutHukum = true, mkWeighted = false, mkTarget = 7)
        assertEquals(r, SavedRules.of(r).toHouseRules())
    }
}
