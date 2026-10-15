package com.tashlark.engine

import com.tashlark.engine.courtpiece.CourtPieceConfig
import com.tashlark.engine.courtpiece.CourtPieceGame
import com.tashlark.engine.courtpiece.CourtPiecePolicy
import com.tashlark.engine.courtpiece.CourtPieceResult
import com.tashlark.engine.courtpiece.CourtPieceRules
import com.tashlark.engine.courtpiece.CourtPieceState
import com.tashlark.engine.courtpiece.CourtPieceTally
import com.tashlark.engine.courtpiece.courtPieceHandPoints
import com.tashlark.engine.courtpiece.courtPieceNextDealer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourtPieceTest {

    private fun c(s: String) = Card.parse(s)
    private fun set(vararg s: String) = CardSet.of(s.map { Card.parse(it) })
    private fun suitSet(suit: Suit) = CardSet(CardSet.suitMask(suit))

    /** Mid-trick state; seat 0 led [trick]; seat trick.size holds [hand]. */
    private fun midTrick(hand: CardSet, trick: List<String>, trump: Suit = Suit.SPADES): CourtPieceState {
        val seat = trick.size
        val hands = List(4) { if (it == seat) hand else set("2C") }
        return CourtPieceState.start(hands, dealer = 3, trump = trump).copy(current = Trick(0, trick.map { c(it) }))
    }

    private fun union(s: CourtPieceState): CardSet = s.hands.fold(s.played + s.stockCards) { a, h -> a + h }

    private fun playOut(start: CourtPieceState, rng: Rng, random: Boolean = false): CourtPieceState {
        var s = start
        var guard = 0
        while (!CourtPieceRules.isOver(s)) {
            val legal = CourtPieceRules.legalActions(s)
            assertFalse(legal.isEmpty())
            val a = if (random) legal[rng.nextInt(legal.size)] else CourtPiecePolicy.choose(s, legal, rng)
            assertTrue("chose illegal $a", a in legal)
            s = CourtPieceRules.apply(s, a)
            assertEquals(CardSet.FULL, union(s))
            assertTrue(++guard < 100)
        }
        return s
    }

    @Test
    fun deal_gives_five_then_thirteen_and_caller_leads() {
        val s = CourtPieceState.deal(2, CourtPieceConfig(), Rng(5))
        assertEquals(3, s.caller)
        assertEquals(3, CourtPieceRules.toAct(s))
        assertTrue(s.hands.all { it.size == 5 })
        assertTrue(s.stock.all { it.size == 8 })
        assertEquals(CardSet.FULL, union(s))
        assertEquals(Suit.entries.map { Action.SetTrump(null, it) }, CourtPieceRules.legalActions(s))
        assertEquals(Phase.TRUMP, CourtPieceGame().table(s).phase)

        val p = CourtPieceRules.apply(s, Action.SetTrump(null, Suit.HEARTS))
        assertEquals(Suit.HEARTS, p.trump)
        assertTrue(p.hands.all { it.size == 13 })
        for (seat in 0..3) assertEquals(s.hands[seat] + s.stock[seat], p.hands[seat])
        assertTrue(p.stockCards.isEmpty())
        assertEquals(CardSet.FULL, union(p))
        assertEquals(3, CourtPieceRules.toAct(p))
        val end = playOut(p, Rng(1))
        assertEquals(3, end.tricks.first().leader)
    }

    @Test
    fun table_prompts_human_caller() {
        val s = CourtPieceState.deal(3, CourtPieceConfig(), Rng(9))
        val view = CourtPieceGame().table(s)
        assertEquals("Choose trump from your first five cards", view.prompt)
        assertNull(view.trump)
        assertFalse(view.trumpHidden)
        assertEquals(-1, view.trumpHolder)
        val p = CourtPieceRules.apply(s, Action.SetTrump(null, Suit.HEARTS))
        val v2 = CourtPieceGame().table(p)
        assertEquals(Phase.PLAY, v2.phase)
        assertEquals(Suit.HEARTS, v2.trump)
        assertTrue(v2.headline.startsWith("Trump ♥ · Your team 0 – 0 Their team"))
    }

    @Test
    fun weak_caller_is_redealt_from_same_rng() {
        var redealt = 0
        for (seed in 0L until 500L) {
            val dealer = (seed % 4).toInt()
            val on = CourtPieceState.deal(dealer, CourtPieceConfig(), Rng(seed))
            val off = CourtPieceState.deal(dealer, CourtPieceConfig(redealWeakCaller = false), Rng(seed))
            assertTrue(on.hands[on.caller].any { it.rank >= 11 })
            assertEquals(0, off.redeals)
            if (on.redeals == 0) {
                assertEquals(off.hands, on.hands)
                assertEquals(off.stock, on.stock)
            } else {
                redealt++
                assertTrue(off.hands[off.caller].all { it.rank < 11 })
                assertEquals(CardSet.FULL, union(on))
            }
        }
        assertTrue(redealt > 0)
    }

    @Test
    fun must_follow_suit_but_need_not_trump() {
        val s = midTrick(set("3H", "QH", "KS", "AD"), listOf("JH"))
        assertEquals(set("3H", "QH"), CourtPieceRules.legalPlays(s))
        // Void in hearts: anything, trumping is optional.
        val s2 = midTrick(set("3S", "AD", "5C"), listOf("JH"))
        assertEquals(set("3S", "AD", "5C"), CourtPieceRules.legalPlays(s2))
        // Leader may play anything.
        val lead = CourtPieceState.start(List(4) { set("3S", "AD", "5C") }, dealer = 3, trump = Suit.SPADES)
        assertEquals(set("3S", "AD", "5C"), CourtPieceRules.legalPlays(lead))
    }

    @Test
    fun trick_winner_with_and_without_trump() {
        val t = Trick(1, listOf(c("AH"), c("2S"), c("KH"), c("3D")))
        assertEquals(2, CourtPieceRules.trickWinner(t, Suit.SPADES))
        assertEquals(1, CourtPieceRules.trickWinner(t, Suit.CLUBS))
        val t2 = Trick(1, listOf(c("10H"), c("JH"), c("KD"), c("9H")))
        assertEquals(2, CourtPieceRules.trickWinner(t2, Suit.CLUBS))
        assertEquals(3, CourtPieceRules.trickWinner(t2, Suit.DIAMONDS))
        val t3 = Trick(0, listOf(c("10H"), c("3S"), c("QS"), c("AH")))
        assertEquals(2, CourtPieceRules.trickWinner(t3, Suit.SPADES))
    }

    @Test
    fun hand_ends_when_a_team_reaches_seven() {
        val hands = listOf(set("KC"), set("KD"), set("QC"), set("4H", "5D"))
        val base = CourtPieceState.start(hands, dealer = 3, trump = Suit.SPADES)
            .copy(current = Trick(0, listOf(c("AH"), c("2H"), c("3H"))), played = set("AH", "2H", "3H"))
        val ending = CourtPieceRules.apply(base.copy(teamTricks = listOf(6, 3)), Action.Play(c("4H")))
        assertTrue(CourtPieceRules.isOver(ending))
        assertEquals(listOf(7, 3), ending.teamTricks)
        assertTrue(CourtPieceRules.legalActions(ending).isEmpty())
        val goingOn = CourtPieceRules.apply(base.copy(teamTricks = listOf(5, 3)), Action.Play(c("4H")))
        assertFalse(CourtPieceRules.isOver(goingOn))
        assertEquals(0, CourtPieceRules.toAct(goingOn))
        assertNull(CourtPieceRules.result(goingOn))
    }

    @Test
    fun scoring_table() {
        assertEquals(1, courtPieceHandPoints(callerTeamWon = true, court = false))
        assertEquals(3, courtPieceHandPoints(callerTeamWon = true, court = true))
        assertEquals(2, courtPieceHandPoints(callerTeamWon = false, court = false))
        assertEquals(4, courtPieceHandPoints(callerTeamWon = false, court = true))
        // Dealer 3 → caller 0, callers are team 0.
        val base = CourtPieceState.start(List(4) { CardSet.EMPTY }, dealer = 3, trump = Suit.SPADES)
        fun res(a: Int, b: Int) = CourtPieceRules.result(base.copy(teamTricks = listOf(a, b)))!!
        assertEquals(CourtPieceResult(0, 0, false, 1, listOf(7, 3)), res(7, 3))
        assertEquals(CourtPieceResult(0, 0, true, 3, listOf(7, 0)), res(7, 0))
        assertEquals(CourtPieceResult(0, 1, false, 2, listOf(4, 7)), res(4, 7))
        assertEquals(CourtPieceResult(0, 1, true, 4, listOf(0, 7)), res(0, 7))
        // Dealer 0 → caller 1: team 0 are the opponents.
        val other = CourtPieceRules.result(base.copy(dealer = 0, teamTricks = listOf(7, 2)))!!
        assertEquals(1, other.callerTeam)
        assertEquals(2, other.points)
        // Utility is zero-sum per team with a trick tie-break.
        val u = CourtPieceRules.utility(base.copy(teamTricks = listOf(7, 3)))
        assertEquals(1.04, u[0], 1e-9)
        assertEquals(1.04, u[2], 1e-9)
        assertEquals(-1.04, u[1], 1e-9)
        assertEquals(-1.04, u[3], 1e-9)
    }

    @Test
    fun caller_court_by_play() {
        // Seat 0 (caller) holds every spade, spades trump: seven straight tricks.
        val hands = listOf(suitSet(Suit.SPADES), suitSet(Suit.HEARTS), suitSet(Suit.DIAMONDS), suitSet(Suit.CLUBS))
        val end = playOut(CourtPieceState.start(hands, dealer = 3, trump = Suit.SPADES), Rng(3))
        assertEquals(listOf(7, 0), end.teamTricks)
        assertEquals(7, end.tricks.size)
        val r = CourtPieceRules.result(end)!!
        assertTrue(r.court && r.callerTeamWon)
        assertEquals(3, r.points)
    }

    @Test
    fun goon_court_by_play() {
        // Seat 1 (opponent) holds every spade and ruffs the caller's lead.
        val hands = listOf(suitSet(Suit.HEARTS), suitSet(Suit.SPADES), suitSet(Suit.DIAMONDS), suitSet(Suit.CLUBS))
        val end = playOut(CourtPieceState.start(hands, dealer = 3, trump = Suit.SPADES), Rng(3))
        assertEquals(listOf(0, 7), end.teamTricks)
        assertEquals(0, end.tricks.first().leader)
        val r = CourtPieceRules.result(end)!!
        assertTrue(r.court)
        assertFalse(r.callerTeamWon)
        assertEquals(4, r.points)
        assertTrue(Suit.HEARTS in end.voids[1])
    }

    @Test
    fun dealer_rotation() {
        // Dealer 3 (team 1), caller 0 (team 0).
        assertEquals(0, courtPieceNextDealer(3, CourtPieceResult(0, 1, false, 2, listOf(3, 7))))
        assertEquals(0, courtPieceNextDealer(3, CourtPieceResult(0, 1, true, 4, listOf(0, 7))))
        assertEquals(3, courtPieceNextDealer(3, CourtPieceResult(0, 0, false, 1, listOf(7, 3))))
        assertEquals(1, courtPieceNextDealer(3, CourtPieceResult(0, 0, true, 3, listOf(7, 0))))

        val game = CourtPieceGame()
        val match = game.newMatch(1L).copy(dealer = 3)
        val base = CourtPieceState.start(List(4) { CardSet.EMPTY }, dealer = 3, trump = Suit.SPADES)
        val m1 = game.finishHand(match, base.copy(teamTricks = listOf(3, 7)))
        assertEquals(0, m1.dealer)
        assertEquals(listOf(0, 2), m1.points)
        val m2 = game.finishHand(match, base.copy(teamTricks = listOf(7, 3)))
        assertEquals(3, m2.dealer)
        assertEquals(listOf(1, 0), m2.points)
        val m3 = game.finishHand(match, base.copy(teamTricks = listOf(7, 0)))
        assertEquals(1, m3.dealer)
        assertEquals(listOf(3, 0), m3.points)
        assertEquals(1, m3.handsPlayed)
        assertEquals("Court! Your team took the first seven tricks.", game.summary(m3, base.copy(teamTricks = listOf(7, 0))).note)
    }

    @Test
    fun many_full_hands_stay_legal_and_conserve_cards() {
        val rng = Rng(42)
        repeat(2000) { n ->
            val s0 = CourtPieceState.deal(n % 4, CourtPieceConfig(), rng.fork())
            assertEquals(CardSet.FULL, union(s0))
            val s = playOut(s0, rng, random = n % 2 == 1)
            assertNotNull(s.trump)
            assertEquals(7, s.teamTricks.max())
            assertTrue(s.teamTricks.min() < 7)
            assertEquals(s.tricks.size, s.teamTricks.sum())
            assertTrue(s.tricks.size in 7..13)
            assertEquals(s0.caller, s.tricks.first().leader)
            for (i in 1 until s.tricks.size) {
                assertEquals(CourtPieceRules.trickWinner(s.tricks[i - 1], s.trump!!), s.tricks[i].leader)
            }
            assertEquals(CardSet.FULL, union(s))
            assertNotNull(CourtPieceRules.result(s))
        }
    }

    @Test
    fun determinize_keeps_viewer_hand_respects_voids_and_conserves_cards() {
        val rng = Rng(7)
        // Trump phase: other hands and the whole stock are resampled.
        val s = CourtPieceState.deal(0, CourtPieceConfig(), Rng(77))
        repeat(50) {
            val w = CourtPieceRules.determinize(s, 1, rng)
            assertEquals(s.hands[1], w.hands[1])
            for (seat in 0..3) {
                assertEquals(s.hands[seat].size, w.hands[seat].size)
                assertEquals(s.stock[seat].size, w.stock[seat].size)
            }
            assertEquals(CardSet.FULL, union(w))
        }
        assertTrue((0 until 20).any { CourtPieceRules.determinize(s, 1, rng).stock[1] != s.stock[1] })

        var tested = 0
        for (seed in 0L until 40L) {
            var p = CourtPieceState.deal((seed % 4).toInt(), CourtPieceConfig(), Rng(seed))
            p = CourtPieceRules.apply(p, CourtPiecePolicy.choose(p, CourtPieceRules.legalActions(p), rng))
            repeat(22) { p = CourtPieceRules.apply(p, CourtPiecePolicy.choose(p, CourtPieceRules.legalActions(p), rng)) }
            assertFalse(CourtPieceRules.isOver(p))
            if (p.voids.all { it.isEmpty() }) continue
            tested++
            val viewer = (seed % 4).toInt()
            repeat(30) {
                val w = CourtPieceRules.determinize(p, viewer, rng)
                assertEquals(p.hands[viewer], w.hands[viewer])
                assertEquals(p.played, w.played)
                for (seat in 0..3) {
                    assertEquals(p.hands[seat].size, w.hands[seat].size)
                    for (v in p.voids[seat]) assertFalse(w.hands[seat].has(v))
                }
                assertEquals(CardSet.FULL, union(w))
            }
        }
        assertTrue(tested > 0)
    }

    @Test
    fun full_match_reaches_target() {
        for (target in listOf(5, 10)) {
            val game = CourtPieceGame(CourtPieceConfig(target = target))
            var m = game.newMatch(2024L + target)
            val rng = Rng(5)
            var guard = 0
            while (!game.isMatchOver(m)) {
                assertEquals("First to $target", game.progress(m))
                val hand = game.deal(m)
                assertEquals((m.dealer + 1) % 4, hand.caller)
                val end = playOut(hand, rng)
                m = game.finishHand(m, end)
                assertTrue(++guard < 200)
            }
            assertEquals("Match over", game.progress(m))
            assertTrue(m.points.max() >= target)
            assertTrue(m.points.min() < target)
            val team = if (m.points[0] >= target) 0 else 1
            assertEquals(setOf(team, team + 2), game.winners(m))
            assertEquals(m.handsPlayed, m.history.size)
            assertEquals(2, game.standings(m).size)
        }
    }

    @Test
    fun deals_are_deterministic_from_match_seed() {
        val game = CourtPieceGame()
        val m = game.newMatch(99L)
        assertEquals(game.deal(m), game.deal(game.newMatch(99L)))
    }

    @Test
    fun ai_team_beats_random_team() {
        val game = CourtPieceGame()
        val ai = MonteCarloAi(CourtPieceRules, CourtPiecePolicy)
        val rng = Rng(11)
        var aiPoints = 0
        var randomPoints = 0
        repeat(30) { n ->
            var s = CourtPieceState.deal(n % 4, CourtPieceConfig(), Rng(mixSeed(99L, n.toLong())))
            while (!CourtPieceRules.isOver(s)) {
                val seat = CourtPieceRules.toAct(s)
                val legal = CourtPieceRules.legalActions(s)
                val a = if (teamOf(seat) == 0) {
                    game.aiSpecial(s, seat, Skill.MEDIUM, rng) ?: ai.decide(s, seat, Skill.MEDIUM, rng, maxMillis = 300)
                } else {
                    legal[rng.nextInt(legal.size)]
                }
                assertTrue(a in legal)
                s = CourtPieceRules.apply(s, a)
            }
            val r = CourtPieceRules.result(s)!!
            if (r.winnerTeam == 0) aiPoints += r.points else randomPoints += r.points
        }
        assertTrue("AI $aiPoints should beat random $randomPoints", aiPoints > randomPoints)
    }

    // ---- Double sir ----

    @Test
    fun double_sir_collection_rules() {
        val cfg = CourtPieceConfig(doubleSir = true)
        var tally = CourtPieceTally(listOf(0, 0), 0, -1)
        val winners = listOf(0, 0, 1, 1, 2, 2, 0, 2, 2, 3, 1, 3, 0)
        val teams = listOf(
            listOf(0, 0), listOf(0, 0), listOf(0, 0), listOf(0, 4), listOf(0, 4), listOf(2, 4), listOf(2, 4),
            listOf(2, 4), listOf(5, 4), listOf(5, 4), listOf(5, 4), listOf(5, 4), listOf(9, 4),
        )
        val piles = listOf(1, 2, 3, 0, 1, 0, 1, 2, 0, 1, 2, 3, 0)
        winners.forEachIndexed { i, w ->
            tally = CourtPieceRules.afterTrick(tally, w, i + 1, cfg)
            assertEquals("after trick ${i + 1}", teams[i], tally.teamTricks)
            assertEquals("pile after trick ${i + 1}", piles[i], tally.pile)
        }
        // Without the three-trick rule, tricks 1 + 2 by one player are collected.
        val loose = CourtPieceConfig(doubleSir = true, firstCollectionNeedsThree = false)
        var t2 = CourtPieceTally(listOf(0, 0), 0, -1)
        t2 = CourtPieceRules.afterTrick(t2, 1, 1, loose)
        t2 = CourtPieceRules.afterTrick(t2, 1, 2, loose)
        assertEquals(listOf(0, 2), t2.teamTricks)
        assertEquals(0, t2.pile)
        // Partners winning alternately is not "the same player".
        var t3 = CourtPieceTally(listOf(0, 0), 0, -1)
        for (i in 1..4) t3 = CourtPieceRules.afterTrick(t3, if (i % 2 == 0) 0 else 2, i, cfg)
        assertEquals(listOf(0, 0), t3.teamTricks)
        assertEquals(4, t3.pile)
    }

    @Test
    fun double_sir_plays_all_thirteen_and_scores() {
        val cfg = CourtPieceConfig(doubleSir = true)
        val rng = Rng(21)
        repeat(300) { n ->
            val s = playOut(CourtPieceState.deal(n % 4, cfg, rng.fork()), rng, random = n % 2 == 0)
            assertEquals(13, s.tricks.size)
            assertEquals(13, s.teamTricks.sum())
            assertEquals(0, s.pile)
            assertTrue(s.hands.all { it.isEmpty() })
            val r = CourtPieceRules.result(s)!!
            assertTrue(s.teamTricks[r.winnerTeam] >= 7)
            assertEquals(s.teamTricks[1 - r.winnerTeam] == 0, r.court)
        }
        // One player wins every trick: court over all 13.
        val hands = listOf(suitSet(Suit.SPADES), suitSet(Suit.HEARTS), suitSet(Suit.DIAMONDS), suitSet(Suit.CLUBS))
        val end = playOut(CourtPieceState.start(hands, dealer = 3, trump = Suit.SPADES, config = cfg), rng)
        assertEquals(listOf(13, 0), end.teamTricks)
        val r = CourtPieceRules.result(end)!!
        assertTrue(r.court && r.callerTeamWon)
        assertEquals(3, r.points)
        // Mid-hand, 7 collected doesn't end a double-sir hand.
        val mid = CourtPieceState.start(hands, dealer = 3, trump = Suit.SPADES, config = cfg).copy(teamTricks = listOf(7, 0))
        assertFalse(CourtPieceRules.isOver(mid))
    }
}
