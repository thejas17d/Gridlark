package com.tashlark.engine

import com.tashlark.engine.mendikot.MendikotConfig
import com.tashlark.engine.mendikot.MendikotGame
import com.tashlark.engine.mendikot.MendikotPolicy
import com.tashlark.engine.mendikot.MendikotRules
import com.tashlark.engine.mendikot.MendikotScoring
import com.tashlark.engine.mendikot.MendikotState
import com.tashlark.engine.mendikot.MendikotTrumpMode
import com.tashlark.engine.mendikot.mendikotNextDealer
import com.tashlark.engine.mendikot.mendikotResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MendikotTest {

    private val band = MendikotConfig()
    private val cut = MendikotConfig(trumpMode = MendikotTrumpMode.CUT_HUKUM)

    private fun c(s: String) = Card.parse(s)
    private fun set(s: String): CardSet =
        if (s.isBlank()) CardSet.EMPTY else CardSet.of(s.trim().split(" ").map { Card.parse(it) })

    /**
     * Hand-built state. Dealer 3, so seat 0 is the setter and leads. [indicator] is the face-down
     * card (band hukum) and is not in any hand unless [revealed].
     */
    private fun state(
        hands: List<String>,
        config: MendikotConfig = band,
        indicator: String? = "2S",
        revealed: Boolean = false,
        current: Trick? = null,
    ): MendikotState {
        val ind = indicator?.let { c(it) }
        return MendikotState(
            hands = hands.map { set(it) }, dealer = 3, trumpCard = ind, trump = ind?.suit, revealed = revealed,
            trumpFrom = 0, caller = -1, tricks = emptyList(), current = current, trickWinners = emptyList(),
            played = CardSet.of(current?.cards ?: emptyList()), voids = List(4) { emptySet() }, config = config,
        )
    }

    private fun play(s: MendikotState, card: String): MendikotState = MendikotRules.apply(s, Action.Play(c(card)))
    private fun plays(s: MendikotState): Set<Card> =
        MendikotRules.legalActions(s).mapNotNull { (it as? Action.Play)?.card }.toSet()

    private fun conserved(s: MendikotState): Boolean {
        var all = s.played
        var n = s.played.size
        for (h in s.hands) {
            all += h
            n += h.size
        }
        val tc = s.trumpCard
        if (s.trumpHidden && tc != null) {
            all += tc
            n += 1
        }
        return all == CardSet.FULL && n == 52
    }

    private fun randomHand(config: MendikotConfig, dealer: Int, rng: Rng, usePolicy: Boolean): MendikotState {
        var s = MendikotState.deal(dealer, config, rng)
        var guard = 0
        while (!MendikotRules.isOver(s)) {
            val legal = MendikotRules.legalActions(s)
            assertFalse(legal.isEmpty())
            val a = if (usePolicy) MendikotPolicy.choose(s, legal, rng) else legal[rng.nextInt(legal.size)]
            assertTrue("chose illegal $a", a in legal)
            s = MendikotRules.apply(s, a)
            assertTrue("cards not conserved", conserved(s))
            assertTrue(++guard < 100)
        }
        return s
    }

    // ---- Deal and trump setting ----

    @Test
    fun deal_gives_13_each_and_setter_hides_one_card() {
        val s = MendikotState.deal(2, band, Rng(1))
        assertTrue(s.hands.all { it.size == 13 })
        assertEquals(CardSet.FULL, s.hands.fold(CardSet.EMPTY) { a, h -> a + h })
        assertTrue(s.inTrumpPhase)
        assertEquals(3, MendikotRules.toAct(s))
        val legal = MendikotRules.legalActions(s)
        assertEquals(13, legal.size)
        assertTrue(legal.all { it is Action.SetTrump })
        val choice = MendikotPolicy.trumpChoice(s.hands[3])
        assertTrue(choice in legal)
        val s2 = MendikotRules.apply(s, choice)
        assertEquals(12, s2.hands[3].size)
        assertFalse(choice.card!! in s2.hands[3])
        assertEquals(choice.card, s2.trumpCard)
        assertTrue(s2.trumpHidden)
        assertFalse(s2.inTrumpPhase)
        // next(dealer) leads trick 1, and the hand is still 52 cards.
        assertEquals(3, MendikotRules.toAct(s2))
        assertTrue(conserved(s2))
        assertTrue(MendikotRules.legalActions(s2).all { it is Action.Play })
        // Policy hides a low non-ten from its longest suit.
        val card = choice.card!!
        assertTrue(card.rank != 10)
        val len = s.hands[3].ofSuit(card.suit).size
        assertTrue(Suit.entries.all { s.hands[3].ofSuit(it).size <= len })
    }

    @Test
    fun cut_hukum_has_no_trump_phase() {
        val s = MendikotState.deal(1, cut, Rng(2))
        assertFalse(s.inTrumpPhase)
        assertEquals(2, MendikotRules.toAct(s))
        assertEquals(13, MendikotRules.legalActions(s).size)
        assertTrue(MendikotRules.legalActions(s).all { it is Action.Play })
    }

    // ---- Band hukum reveal ----

    @Test
    fun reveal_only_when_void_and_unrevealed() {
        // Seat 0 led A♥; seat 1 is void in hearts.
        val led = Trick(0, listOf(c("AH")))
        val s = state(listOf("5D", "3S 9S 4D", "7D", "8D"), current = led)
        val legal = MendikotRules.legalActions(s)
        assertTrue(Action.CallReveal in legal)
        assertEquals(setOf(c("3S"), c("9S"), c("4D")), plays(s))
        // Holding a heart: must follow, no reveal.
        val s2 = state(listOf("5D", "5H 3S", "7D", "8D"), current = led)
        assertEquals(listOf<Action>(Action.Play(c("5H"))), MendikotRules.legalActions(s2))
        // Already revealed: no reveal.
        val s3 = state(listOf("5D 2S", "3S 9S 4D", "7D", "8D"), revealed = true, current = led)
        assertFalse(Action.CallReveal in MendikotRules.legalActions(s3))
        // Leading: no reveal.
        val s4 = state(listOf("5D AH", "3S 9S 4D", "7D", "8D"))
        assertFalse(Action.CallReveal in MendikotRules.legalActions(s4))
    }

    @Test
    fun must_play_trump_after_asking() {
        val led = Trick(0, listOf(c("AH")))
        val s = MendikotRules.apply(state(listOf("5D", "3S 9S 4D", "7D", "8D"), current = led), Action.CallReveal)
        assertTrue(s.revealed)
        assertEquals(Suit.SPADES, s.trump)
        assertTrue(c("2S") in s.hands[0]) // indicator back with the setter
        assertEquals(1, MendikotRules.toAct(s)) // still the caller's turn
        assertEquals(listOf<Action>(Action.Play(c("3S")), Action.Play(c("9S"))), MendikotRules.legalActions(s))
        // No trumps: any card after asking, and we learn the caller is void in trumps.
        val s2 = MendikotRules.apply(state(listOf("5D", "4D 5C", "7D", "8D"), current = led), Action.CallReveal)
        assertEquals(setOf(c("4D"), c("5C")), plays(s2))
        val s3 = play(s2, "4D")
        assertTrue(Suit.SPADES in s3.voids[1])
        assertTrue(Suit.HEARTS in s3.voids[1])
        // A void player who doesn't ask may play anything.
        val s4 = play(state(listOf("5D", "3S 9S 4D", "7D", "8D"), current = led), "4D")
        assertFalse(s4.revealed)
    }

    @Test
    fun earlier_trump_suit_cards_in_the_same_trick_dont_count() {
        val hands = listOf("AH 5D", "KS 6D", "3S 7D", "4H 8D")
        var s = state(hands)
        s = play(s, "AH")
        s = play(s, "KS") // seat 1 void, discards a spade without asking
        s = MendikotRules.apply(s, Action.CallReveal) // seat 2 asks: spades are trump
        assertEquals(listOf<Action>(Action.Play(c("3S"))), MendikotRules.legalActions(s))
        s = play(s, "3S")
        s = play(s, "4H")
        assertEquals(1, s.tricks.size)
        assertEquals(2, s.trickWinners.last()) // 3♠ counts as trump, K♠ does not
        // Next trick: trump counts from the first card.
        assertEquals(0, s.trumpFrom)

        // Control: with trump already revealed the K♠ wins.
        var r = state(listOf("AH 5D 2S", "KS 6D", "3S 7D", "4H 8D"), revealed = true)
        r = play(r, "AH"); r = play(r, "KS"); r = play(r, "3S"); r = play(r, "4H")
        assertEquals(1, r.trickWinners.last())

        // Nobody reveals: highest of the led suit wins even over spades.
        var u = state(hands)
        u = play(u, "AH"); u = play(u, "KS"); u = play(u, "3S"); u = play(u, "4H")
        assertEquals(0, u.trickWinners.last())
        assertFalse(u.revealed)
    }

    @Test
    fun setter_with_only_the_indicator_is_forced_to_reveal() {
        // Leading with an empty hand.
        val s = state(listOf("", "6D", "7D", "8D"))
        assertEquals(0, MendikotRules.toAct(s))
        assertEquals(listOf<Action>(Action.CallReveal), MendikotRules.legalActions(s))
        val s2 = MendikotRules.apply(s, Action.CallReveal)
        assertEquals(listOf<Action>(Action.Play(c("2S"))), MendikotRules.legalActions(s2))
        // Following with an empty hand.
        val f = state(listOf("", "6D", "7D", "5H"), current = Trick(3, listOf(c("5H"))))
        assertEquals(listOf<Action>(Action.CallReveal), MendikotRules.legalActions(f))
        // The setter asking voluntarily must play a trump: here only the indicator.
        val v = state(listOf("4D", "6D", "7D", "5H"), current = Trick(3, listOf(c("5H"))))
        assertTrue(Action.CallReveal in MendikotRules.legalActions(v))
        val v2 = MendikotRules.apply(v, Action.CallReveal)
        assertEquals(listOf<Action>(Action.Play(c("2S"))), MendikotRules.legalActions(v2))
    }

    // ---- Cut hukum ----

    @Test
    fun cut_hukum_first_void_card_sets_trump() {
        var s = state(listOf("AH 2C", "3D 4C", "KH 5C", "5H 6C"), config = cut, indicator = null)
        s = play(s, "AH")
        assertFalse(Action.CallReveal in MendikotRules.legalActions(s))
        assertEquals(setOf(c("3D"), c("4C")), plays(s))
        s = play(s, "3D")
        assertEquals(Suit.DIAMONDS, s.trump)
        assertTrue(s.revealed)
        s = play(s, "KH")
        s = play(s, "5H")
        assertEquals(1, s.trickWinners.last())
        assertEquals(Suit.DIAMONDS, MendikotGame(cut).table(s).trump)

        // A higher card of the new trump suit later in the trick wins; no obligation to trump.
        var o = state(listOf("AH 2C", "3D 4C", "9D 5C", "5H 6C"), config = cut, indicator = null)
        o = play(o, "AH")
        o = play(o, "3D")
        assertEquals(setOf(c("9D"), c("5C")), plays(o))
        o = play(o, "9D")
        o = play(o, "5H")
        assertEquals(2, o.trickWinners.last())
    }

    // ---- Results and scoring ----

    @Test
    fun hand_result_rules_and_scoring() {
        val w = MendikotScoring.WEIGHTED
        val simple = MendikotScoring.SIMPLE
        val three = mendikotResult(listOf(3, 1), listOf(5, 8), w)
        assertEquals(0, three.winner); assertEquals(1, three.points)
        assertFalse(three.mendikot); assertFalse(three.whitewash)
        assertEquals(1, mendikotResult(listOf(1, 3), listOf(9, 4), w).winner)
        assertEquals(0, mendikotResult(listOf(2, 2), listOf(7, 6), w).winner)
        assertEquals(1, mendikotResult(listOf(2, 2), listOf(6, 7), w).winner)
        val mendi = mendikotResult(listOf(4, 0), listOf(9, 4), w)
        assertTrue(mendi.mendikot); assertEquals(2, mendi.points)
        assertEquals(1, mendikotResult(listOf(4, 0), listOf(9, 4), simple).points)
        val wash = mendikotResult(listOf(0, 4), listOf(0, 13), w)
        assertEquals(1, wash.winner); assertTrue(wash.whitewash); assertEquals(3, wash.points)
        assertEquals(1, mendikotResult(listOf(0, 4), listOf(0, 13), simple).points)
    }

    @Test
    fun dealer_rotation() {
        val team0Win = mendikotResult(listOf(3, 1), listOf(8, 5), MendikotScoring.WEIGHTED)
        val team0Wash = mendikotResult(listOf(4, 0), listOf(13, 0), MendikotScoring.WEIGHTED)
        val team1Wash = mendikotResult(listOf(0, 4), listOf(0, 13), MendikotScoring.WEIGHTED)
        // Dealer's team won: the next player deals.
        assertEquals(1, mendikotNextDealer(0, team0Win))
        assertEquals(3, mendikotNextDealer(2, team0Win))
        assertEquals(1, mendikotNextDealer(0, team0Wash))
        // Dealer's team lost: same dealer.
        assertEquals(1, mendikotNextDealer(1, team0Win))
        assertEquals(3, mendikotNextDealer(3, team0Win))
        // Opponents whitewashed: the dealer's partner deals.
        assertEquals(3, mendikotNextDealer(1, team0Wash))
        assertEquals(2, mendikotNextDealer(0, team1Wash))
    }

    // ---- Full hands ----

    @Test
    fun random_hands_conserve_cards_and_stay_legal() {
        for (config in listOf(band, cut)) {
            val rng = Rng(42)
            repeat(2000) { n ->
                val s = randomHand(config, n % 4, rng, usePolicy = false)
                assertEquals(13, s.tricks.size)
                assertEquals(13, s.trickWinners.size)
                assertEquals(CardSet.FULL, s.played)
                assertTrue(s.hands.all { it.isEmpty() })
                assertEquals(4, s.tensOf(0) + s.tensOf(1))
                if (config.trumpMode == MendikotTrumpMode.BAND_HUKUM) assertTrue(s.revealed)
            }
            repeat(300) { n ->
                val s = randomHand(config, n % 4, rng, usePolicy = true)
                assertEquals(13, s.tricks.size)
                assertEquals(CardSet.FULL, s.played)
            }
        }
    }

    // ---- Determinization ----

    private fun checkWorld(s: MendikotState, viewer: Int, w: MendikotState) {
        assertEquals(s.hands[viewer], w.hands[viewer])
        assertEquals(s.played, w.played)
        assertTrue(conserved(w))
        val wtc = w.trumpCard
        for (seat in 0..3) {
            assertEquals(s.hands[seat].size, w.hands[seat].size)
            var h = w.hands[seat]
            if (seat == s.setter && wtc != null) h -= wtc
            for (v in s.voids[seat]) assertFalse("seat $seat void in $v", h.has(v))
        }
        val tc = s.trumpCard
        if (tc == null) {
            assertNull(wtc)
        } else if (s.revealed || viewer == s.setter) {
            assertEquals(tc, wtc)
            if (tc in s.hands[s.setter]) assertTrue(tc in w.hands[s.setter])
        } else {
            assertNotNull(wtc)
            assertFalse(wtc!! in s.hands[viewer])
            assertFalse(wtc in s.played)
            assertEquals(wtc.suit, w.trump)
        }
    }

    @Test
    fun determinize_keeps_viewer_hand_respects_voids_and_conserves_cards() {
        for (config in listOf(band, cut)) {
            val rng = Rng(7)
            repeat(40) { n ->
                var s = MendikotState.deal(n % 4, config, rng)
                var step = 0
                while (!MendikotRules.isOver(s)) {
                    if (step % 4 == 0) for (viewer in 0..3) repeat(3) { checkWorld(s, viewer, MendikotRules.determinize(s, viewer, rng)) }
                    s = MendikotRules.apply(s, MendikotPolicy.choose(s, MendikotRules.legalActions(s), rng))
                    step++
                }
            }
        }
    }

    @Test
    fun determinize_does_not_leak_the_hidden_indicator() {
        val rng = Rng(9)
        var s = MendikotState.deal(0, band, rng)
        s = MendikotRules.apply(s, MendikotPolicy.trumpChoice(s.hands[s.setter]))
        repeat(8) {
            val legal = MendikotRules.legalActions(s)
            s = MendikotRules.apply(s, legal.firstOrNull { it is Action.Play } ?: legal[0])
        }
        assertTrue(s.trumpHidden)
        val viewer = (s.setter + 1) % 4
        val seen = mutableSetOf<Card>()
        var same = 0
        repeat(300) {
            val w = MendikotRules.determinize(s, viewer, rng)
            checkWorld(s, viewer, w)
            seen += w.trumpCard!!
            if (w.trumpCard == s.trumpCard) same++
        }
        assertTrue("indicator samples too narrow: ${seen.size}", seen.size > 10)
        assertTrue("indicator leaked: $same/300", same < 60)
        repeat(20) { assertEquals(s.trumpCard, MendikotRules.determinize(s, s.setter, rng).trumpCard) }
        // The table never shows seat 0 a trump it can't see.
        val view = MendikotGame().table(s)
        if (s.setter != 0) {
            assertNull(view.trump)
            assertTrue(view.trumpHidden)
        } else {
            assertEquals(s.trump, view.trump)
            assertFalse(view.trumpHidden)
        }
        assertEquals(s.setter, view.trumpHolder)
    }

    // ---- Match ----

    @Test
    fun full_match_reaches_target() {
        for (config in listOf(band, cut, cut.copy(scoring = MendikotScoring.SIMPLE))) {
            val game = MendikotGame(config)
            val rng = Rng(123)
            var m = game.newMatch(123)
            assertEquals(game.deal(m), game.deal(m))
            assertEquals("First to 5", game.progress(m))
            var hands = 0
            while (!game.isMatchOver(m)) {
                var s = game.deal(m)
                assertEquals(m.dealer, s.dealer)
                while (!MendikotRules.isOver(s)) {
                    val view = game.table(s)
                    val seat = MendikotRules.toAct(s)
                    if (seat == 0) assertNotNull(view.prompt)
                    val legal = MendikotRules.legalActions(s)
                    val a = game.aiSpecial(s, seat, Skill.EASY, rng) ?: MendikotPolicy.choose(s, legal, rng)
                    assertTrue(a in legal)
                    game.explain(s, a)
                    s = MendikotRules.apply(s, a)
                }
                assertEquals(Phase.OVER, game.table(s).phase)
                val before = m
                m = game.finishHand(m, s)
                assertEquals(mendikotNextDealer(before.dealer, s.result()), m.dealer)
                assertEquals(before.handsPlayed + 1, m.handsPlayed)
                val sum = game.summary(m, s)
                assertEquals(2, sum.rows.size)
                assertTrue(++hands < 300)
            }
            assertTrue(m.points.max() >= 5)
            assertEquals(2, game.winners(m).size)
            assertEquals(2, game.standings(m).size)
            assertEquals("Match over", game.progress(m))
            assertTrue(game.rulesText().size in 6..9)
        }
    }

    @Test
    fun ai_team_beats_random_team() {
        val ai = MonteCarloAi(MendikotRules, MendikotPolicy)
        val rng = Rng(21)
        var us = 0
        var them = 0
        repeat(30) { n ->
            var s = MendikotState.deal(n % 4, band, rng)
            while (!MendikotRules.isOver(s)) {
                val seat = MendikotRules.toAct(s)
                val legal = MendikotRules.legalActions(s)
                val a = when {
                    teamOf(seat) != 0 -> legal[rng.nextInt(legal.size)]
                    s.inTrumpPhase -> MendikotPolicy.trumpChoice(s.hands[seat])
                    else -> ai.decide(s, seat, Skill.MEDIUM, rng)
                }
                assertTrue(a in legal)
                s = MendikotRules.apply(s, a)
            }
            val r = s.result()
            if (r.winner == 0) us += r.points else them += r.points
        }
        assertTrue("AI team $us should beat random team $them", us > them)
    }
}
