package com.tashlark.engine

import com.tashlark.engine.twentynine.TwentyNineConfig
import com.tashlark.engine.twentynine.TwentyNineGame
import com.tashlark.engine.twentynine.TwentyNinePolicy
import com.tashlark.engine.twentynine.TwentyNineRules
import com.tashlark.engine.twentynine.TwentyNineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TwentyNineTest {

    private val R = TwentyNineRules

    private fun c(s: String) = Card.parse(s)
    private fun set(vararg s: String) = CardSet.of(s.map { Card.parse(it) })

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            return
        } catch (e: IllegalStateException) {
            return
        }
        fail("expected the action to be rejected")
    }

    private val dummyTrick = Trick(0, listOf(Card.parse("7C"), Card.parse("8C"), Card.parse("QC"), Card.parse("KC")))

    /**
     * A hand-built play-phase state. [leader] leads the current trick ([trick] cards already played);
     * the seat to act is leader + trick.size. If [revealed], [hands] must already contain the indicator.
     */
    private fun playState(
        hands: List<CardSet>,
        declarer: Int,
        indicator: Card,
        revealed: Boolean = false,
        leader: Int = 0,
        trick: List<String> = emptyList(),
        bid: Int = 18,
        config: TwentyNineConfig = TwentyNineConfig(),
        tricksDone: Int = 0,
    ): TwentyNineState {
        val t = trick.map { c(it) }
        return TwentyNineState(
            hands = hands, dealer = (leader + 3) % 4, config = config,
            bidTurn = -1, highBid = bid, highBidder = declarer, declarer = declarer, bid = bid, target = bid,
            indicator = indicator, trump = indicator.suit, revealed = revealed,
            revealedBy = if (revealed) declarer else -1,
            tricks = List(tricksDone) { dummyTrick }, trickWinners = List(tricksDone) { leader },
            current = if (t.isEmpty()) null else Trick(leader, t),
            played = CardSet.of(t),
        )
    }

    /** A finished hand with the given card points for the declarer's side. */
    private fun overState(
        declarer: Int,
        bid: Int,
        declPts: Int,
        mult: Int,
        lastWinner: Int = 0,
        config: TwentyNineConfig = TwentyNineConfig(),
        revealed: Boolean = true,
    ): TwentyNineState {
        val dt = teamOf(declarer)
        val pts = MutableList(2) { 0 }
        pts[dt] = declPts
        pts[1 - dt] = 28 - declPts
        return TwentyNineState(
            hands = List(4) { CardSet.EMPTY }, dealer = 0, config = config, bidTurn = -1,
            highBid = bid, highBidder = declarer, declarer = declarer, bid = bid, target = bid,
            indicator = c("7S"), trump = Suit.SPADES, revealed = revealed, revealedBy = if (revealed) 1 else -1,
            multiplier = mult, tricks = List(8) { dummyTrick }, trickWinners = List(7) { 0 } + lastWinner,
            points = pts, played = CardSet.PIQUET,
        )
    }

    /** Every one of the 32 cards is in exactly one place: a hand, the stock, played, or face down. */
    private fun checkConservation(s: TwentyNineState) {
        var all = s.played
        var n = s.played.size
        for (h in s.hands) {
            all = all + h
            n += h.size
        }
        for (x in s.stock) {
            all = all + x
            n++
        }
        val ind = s.indicator
        if (ind != null && !s.revealed) {
            assertFalse("face-down indicator must not be in a hand or played", ind in all)
            all = all + ind
            n++
        }
        if (ind != null && s.revealed) assertTrue(ind in all)
        assertEquals(32, n)
        assertEquals(CardSet.PIQUET, all)
    }

    @Test
    fun strength_order_and_points_total_28() {
        for (s in Suit.entries) {
            val order = listOf(11, 9, 14, 10, 13, 12, 8, 7).map { Card.of(s, it) }
            for (i in 0 until order.size - 1) {
                assertTrue("${order[i]} should beat ${order[i + 1]}", R.strength(order[i]) > R.strength(order[i + 1]))
            }
        }
        assertEquals(32, CardSet.PIQUET.size)
        assertEquals(28, CardSet.PIQUET.sumOf { R.points(it) })
        assertEquals(3, R.points(c("JH")))
        assertEquals(2, R.points(c("9H")))
        assertEquals(1, R.points(c("AH")))
        assertEquals(1, R.points(c("10H")))
        assertEquals(0, R.points(c("KH")))
        assertTrue(R.beats(c("9H"), c("AH"), Suit.HEARTS, null))
        assertFalse(R.beats(c("JS"), c("7H"), Suit.HEARTS, null))
        assertTrue(R.beats(c("7S"), c("JH"), Suit.HEARTS, Suit.SPADES))
    }

    @Test
    fun deal_gives_four_each_and_sixteen_in_stock() {
        val s = TwentyNineState.deal(2, TwentyNineConfig(), Rng(99))
        assertTrue(s.hands.all { it.size == 4 })
        assertEquals(16, s.stock.size)
        checkConservation(s)
        assertEquals(Phase.BIDDING, s.phase)
        assertEquals(3, R.toAct(s))
        // Deterministic from the rng seed.
        assertEquals(s, TwentyNineState.deal(2, TwentyNineConfig(), Rng(99)))
    }

    @Test
    fun bidding_legality_and_auction_end() {
        val s0 = TwentyNineState.deal(3, TwentyNineConfig(), Rng(1))
        assertEquals(0, R.toAct(s0))
        val legal0 = R.legalActions(s0)
        assertTrue(Action.Pass in legal0)
        assertTrue(Action.Bid(16) in legal0)
        assertTrue(Action.Bid(28) in legal0)
        assertFalse(Action.Bid(15) in legal0)
        assertFalse(Action.Bid(29) in legal0)
        expectFailure { R.apply(s0, Action.Bid(15)) }

        var s = R.apply(s0, Action.Bid(16))
        assertEquals(1, R.toAct(s))
        assertFalse("must exceed the current bid", Action.Bid(16) in R.legalActions(s))
        assertTrue(Action.Bid(17) in R.legalActions(s))
        val afterFirst = s
        expectFailure { R.apply(afterFirst, Action.Bid(16)) }
        s = R.apply(s, Action.Bid(17))
        s = R.apply(s, Action.Pass)
        s = R.apply(s, Action.Pass)
        assertEquals("passed players are skipped", 0, R.toAct(s))
        s = R.apply(s, Action.Bid(18))
        assertEquals(1, R.toAct(s))
        assertEquals(Phase.BIDDING, s.phase)
        s = R.apply(s, Action.Pass)
        assertEquals(Phase.TRUMP, s.phase)
        assertEquals(0, s.declarer)
        assertEquals(18, s.bid)
        assertEquals(18, s.target)
        assertEquals(0, R.toAct(s))
        assertEquals(4, R.legalActions(s).size)

        // First three pass: dealer must bid 16.
        var f = TwentyNineState.deal(3, TwentyNineConfig(), Rng(2))
        repeat(3) { f = R.apply(f, Action.Pass) }
        assertEquals(3, R.toAct(f))
        assertEquals(listOf<Action>(Action.Bid(16)), R.legalActions(f))
        val forced = f
        expectFailure { R.apply(forced, Action.Pass) }
        f = R.apply(f, Action.Bid(16))
        assertEquals(Phase.TRUMP, f.phase)
        assertEquals(3, f.declarer)
        assertEquals(16, f.bid)

        // Last-trick point raises the maximum bid to 29.
        val lt = TwentyNineState.deal(0, TwentyNineConfig(lastTrickPoint = true), Rng(3))
        assertTrue(Action.Bid(29) in R.legalActions(lt))

        // A bid of the maximum leaves only Pass for everyone else.
        var m = TwentyNineState.deal(0, TwentyNineConfig(), Rng(4))
        m = R.apply(m, Action.Bid(28))
        assertEquals(listOf<Action>(Action.Pass), R.legalActions(m))
    }

    @Test
    fun trump_choice_and_doubling_flow() {
        fun auctionTo1(cfg: TwentyNineConfig): TwentyNineState {
            var s = TwentyNineState.deal(0, cfg, Rng(5))
            s = R.apply(s, Action.Bid(16)) // seat 1
            s = R.apply(s, Action.Pass) // 2
            s = R.apply(s, Action.Pass) // 3
            s = R.apply(s, Action.Pass) // 0
            assertEquals(1, s.declarer)
            return s
        }
        val base = auctionTo1(TwentyNineConfig())
        val card = base.hands[1].first()
        var s = R.apply(base, Action.SetTrump(card, card.suit))
        assertEquals(Phase.DOUBLING, s.phase)
        assertEquals(card, s.indicator)
        assertEquals(card.suit, s.trump)
        assertFalse(card in s.hands[1])
        assertEquals(2, R.toAct(s))
        checkConservation(s)
        val doubling = s
        s = R.apply(s, Action.Double)
        assertEquals("first double ends the defenders' option", 1, R.toAct(s))
        assertEquals(listOf(Action.Redouble, Action.NoDouble), R.legalActions(s))
        s = R.apply(s, Action.Redouble)
        assertEquals(Phase.PLAY, s.phase)
        assertEquals(4, s.multiplier)
        assertEquals(7, s.hands[1].size)
        for (seat in listOf(0, 2, 3)) assertEquals(8, s.hands[seat].size)
        assertTrue(s.stock.isEmpty())
        checkConservation(s)
        assertEquals("next(dealer) leads trick 1", 1, R.toAct(s))

        var n = R.apply(doubling, Action.NoDouble)
        assertEquals(0, R.toAct(n))
        n = R.apply(n, Action.NoDouble)
        assertEquals(Phase.PLAY, n.phase)
        assertEquals(1, n.multiplier)

        var d2 = R.apply(doubling, Action.NoDouble)
        d2 = R.apply(d2, Action.Double)
        assertEquals(1, R.toAct(d2))
        d2 = R.apply(d2, Action.NoDouble)
        assertEquals(2, d2.multiplier)
        assertEquals(0, d2.doubledBy)
        assertEquals(Phase.PLAY, d2.phase)
        assertEquals("first seat after the dealer leads", 1, R.toAct(d2))

        val noDbl = auctionTo1(TwentyNineConfig(doubling = false))
        val c2 = noDbl.hands[1].first()
        val p = R.apply(noDbl, Action.SetTrump(c2, c2.suit))
        assertEquals(Phase.PLAY, p.phase)
        checkConservation(p)
    }

    @Test
    fun declarer_cannot_lead_trump_before_reveal() {
        val others = listOf(set("7D"), set("8D"), set("9D"))
        val hands = listOf(set("JH", "9S", "7C")) + others
        val s = playState(hands, declarer = 0, indicator = c("7H"))
        assertEquals(0, R.toAct(s))
        assertEquals(set("9S", "7C"), R.legalPlays(s))
        assertFalse(Action.Play(c("JH")) in R.legalActions(s))
        assertFalse("no reveal on the lead", Action.CallReveal in R.legalActions(s))
        expectFailure { R.apply(s, Action.Play(c("JH"))) }

        // Only trumps in hand: may lead them.
        val s2 = playState(listOf(set("JH", "9H")) + others, declarer = 0, indicator = c("7H"))
        assertEquals(set("JH", "9H"), R.legalPlays(s2))

        // Toggle off.
        val s3 = playState(hands, declarer = 0, indicator = c("7H"), config = TwentyNineConfig(declarerNoTrumpLeadBeforeReveal = false))
        assertEquals(hands[0], R.legalPlays(s3))

        // After the reveal the indicator is in hand and anything may be led.
        val s4 = playState(listOf(set("JH", "9S", "7C", "7H")) + others, declarer = 0, indicator = c("7H"), revealed = true)
        assertEquals(set("JH", "9S", "7C", "7H"), R.legalPlays(s4))

        // A defender may lead the hidden trump suit freely.
        val s5 = playState(listOf(set("7D"), set("JH", "9S"), set("8D"), set("9D")), declarer = 0, indicator = c("7H"), leader = 1)
        assertEquals(set("JH", "9S"), R.legalPlays(s5))
    }

    @Test
    fun reveal_only_when_void_and_must_trump_after_asking() {
        // Declarer 2, trump spades (8S face down). Seat 0 leads KH, seat 1 to act.
        fun st(h1: CardSet, cfg: TwentyNineConfig = TwentyNineConfig()) = playState(
            listOf(set("7C"), h1, set("QH", "AD"), set("9C")), declarer = 2, indicator = c("8S"),
            leader = 0, trick = listOf("KH"), config = cfg,
        )
        val holding = st(set("7H", "QS"))
        assertEquals(1, R.toAct(holding))
        assertEquals(listOf<Action>(Action.Play(c("7H"))), R.legalActions(holding))
        expectFailure { R.apply(holding, Action.CallReveal) }

        val void = st(set("QS", "7D"))
        val legal = R.legalActions(void)
        assertTrue(Action.CallReveal in legal)
        assertTrue("a void player who doesn't ask may play anything", Action.Play(c("7D")) in legal && Action.Play(c("QS")) in legal)
        val asked = R.apply(void, Action.CallReveal)
        assertTrue(asked.revealed)
        assertEquals(1, asked.revealedBy)
        assertTrue("indicator joins the declarer's hand", c("8S") in asked.hands[2])
        assertEquals(1, R.toAct(asked))
        assertEquals(set("QS"), R.legalPlays(asked))
        assertFalse(Action.CallReveal in R.legalActions(asked))
        checkTrumpVoidInference(asked)

        val lenient = R.apply(st(set("QS", "7D"), TwentyNineConfig(mustTrumpAfterAsking = false)), Action.CallReveal)
        assertEquals(set("QS", "7D"), R.legalPlays(lenient))

        val noTrump = R.apply(st(set("7D", "8D")), Action.CallReveal)
        assertEquals(set("7D", "8D"), R.legalPlays(noTrump))
        val afterPlay = R.apply(noTrump, Action.Play(c("7D")))
        assertTrue(Suit.HEARTS in afterPlay.voids[1])
        assertTrue("asked, held no trump: void in trump", Suit.SPADES in afterPlay.voids[1])
    }

    private fun checkTrumpVoidInference(asked: TwentyNineState) {
        val played = R.apply(asked, Action.Play(c("QS")))
        assertTrue(Suit.HEARTS in played.voids[1])
        assertFalse(Suit.SPADES in played.voids[1])
    }

    @Test
    fun declarer_may_reveal_and_must_follow_with_indicator() {
        // Trump hearts (indicator 7H), hearts led, declarer 1 has no other heart: void in hand, may ask.
        val s = playState(listOf(CardSet.EMPTY, set("9C", "KD"), set("8D"), set("9D")), declarer = 1, indicator = c("7H"), leader = 0, trick = listOf("AH"))
        assertTrue(Action.CallReveal in R.legalActions(s))
        val r = R.apply(s, Action.CallReveal)
        assertEquals("the indicator now follows suit", set("7H"), R.legalPlays(r))
    }

    @Test
    fun reveal_makes_trump_count_for_cards_already_in_the_trick() {
        // Declarer 2, trump spades (8S hidden). Seat 3 led AH, seat 0 discarded 7S; seat 1 is void.
        fun st() = playState(
            listOf(CardSet.EMPTY, set("7D", "8D"), set("QH"), CardSet.EMPTY), declarer = 2, indicator = c("8S"),
            leader = 3, trick = listOf("AH", "7S"),
        )
        // Without a reveal: highest heart wins.
        var a = R.apply(st(), Action.Play(c("7D")))
        assertEquals(listOf<Action>(Action.Play(c("QH"))), R.legalActions(a))
        a = R.apply(a, Action.Play(c("QH")))
        assertEquals(3, a.trickWinners.last())
        assertEquals(1, a.points[1])

        // With a reveal: the 7S already played is now a trump and wins.
        var b = R.apply(st(), Action.CallReveal)
        b = R.apply(b, Action.Play(c("7D")))
        assertEquals("declarer must follow suit (indicator is not a heart)", listOf<Action>(Action.Play(c("QH"))), R.legalActions(b))
        b = R.apply(b, Action.Play(c("QH")))
        assertEquals(0, b.trickWinners.last())
        assertEquals(1, b.points[0])
        assertEquals(0, R.toAct(b))
    }

    @Test
    fun pair_moves_target_both_ways_and_clamps() {
        // Declarer side: declarer 0 holds KS QS (trump spades, revealed) and wins the trick.
        fun declSide(bid: Int, revealed: Boolean = true, cfg: TwentyNineConfig = TwentyNineConfig()) = playState(
            listOf(set("JH", "KS", "QS"), set("7D"), set("8D"), set("10D")), declarer = 0, indicator = c("7S"),
            revealed = revealed, leader = 1, trick = listOf("7H", "8H", "9H"), bid = bid, config = cfg,
        )
        val d = R.apply(declSide(22), Action.Play(c("JH")))
        assertEquals(0, d.trickWinners.last())
        assertEquals(0, d.pairSeat)
        assertEquals(18, d.target)
        assertEquals("clamped at 16", 16, R.apply(declSide(18), Action.Play(c("JH"))).target)
        val hidden = R.apply(declSide(22, revealed = false), Action.Play(c("JH")))
        assertEquals("no pair before the reveal", -1, hidden.pairSeat)
        assertEquals(22, hidden.target)
        val off = R.apply(declSide(22, cfg = TwentyNineConfig(pair = false)), Action.Play(c("JH")))
        assertEquals(22, off.target)
        val once = R.apply(declSide(22).copy(pairSeat = 1, target = 26), Action.Play(c("JH")))
        assertEquals("only one pair per hand", 26, once.target)

        // Defender side: seat 1 holds KS QS and partner seat 3 wins with JH.
        fun defSide(bid: Int) = playState(
            listOf(set("8H", "7C"), set("9D", "KS", "QS"), set("8D"), set("10D")), declarer = 0, indicator = c("7S"),
            revealed = true, leader = 2, trick = listOf("7H", "JH", "8H"), bid = bid,
        )
        val f = R.apply(defSide(20), Action.Play(c("9D")))
        assertEquals(3, f.trickWinners.last())
        assertEquals(1, f.pairSeat)
        assertEquals(24, f.target)
        assertEquals("clamped at 28", 28, R.apply(defSide(26), Action.Play(c("9D"))).target)
    }

    @Test
    fun never_revealed_hand_is_annulled() {
        // Last trick: the declarer has only the face-down indicator left and must reveal it.
        val s = playState(
            List(4) { CardSet.EMPTY }, declarer = 0, indicator = c("7S"), leader = 1,
            trick = listOf("AH", "9H", "8H"), tricksDone = 7,
        )
        assertEquals(0, R.toAct(s))
        assertEquals(listOf<Action>(Action.CallReveal), R.legalActions(s))
        val r = R.apply(s, Action.CallReveal)
        assertTrue(r.forcedReveal)
        assertEquals(listOf<Action>(Action.Play(c("7S"))), R.legalActions(r))
        val end = R.apply(r, Action.Play(c("7S")))
        assertTrue(R.isOver(end))
        assertTrue(end.annulled)
        assertEquals(0, R.result(end).delta)
        assertTrue(R.utility(end).all { it == 0.0 })

        val never = overState(declarer = 1, bid = 18, declPts = 25, mult = 2, revealed = false)
        assertTrue(never.annulled)
        assertEquals(0, R.result(never).delta)

        // The match: no score, same dealer, but the hand counter (and so the next deal) moves on.
        val game = TwentyNineGame()
        val m = game.newMatch(9)
        assertEquals(game.deal(m), game.deal(m))
        val m2 = game.finishHand(m, end)
        assertEquals(m.dealer, m2.dealer)
        assertEquals(1, m2.handsDealt)
        assertEquals(listOf(0, 0), m2.gamePoints)
        assertTrue(game.deal(m2) != game.deal(m))
        assertEquals("Hand annulled", game.summary(m2, end).title)
    }

    @Test
    fun scoring_with_double_and_redouble() {
        val made = overState(declarer = 1, bid = 18, declPts = 18, mult = 2)
        val r = R.result(made)
        assertTrue(r.made)
        assertEquals(2, r.delta)
        assertEquals(2, r.teamDelta(1))
        assertEquals(0, r.teamDelta(0))
        val u = R.utility(made)
        assertEquals(2.08, u[1], 1e-9)
        assertEquals(2.08, u[3], 1e-9)
        assertEquals(-2.08, u[0], 1e-9)

        val failed = overState(declarer = 0, bid = 20, declPts = 19, mult = 4)
        assertFalse(R.result(failed).made)
        assertEquals(-4, R.result(failed).delta)
        assertEquals(-4.0 + (19 - 9) / 100.0, R.utility(failed)[2], 1e-9)

        val plain = overState(declarer = 2, bid = 16, declPts = 10, mult = 1)
        assertEquals(-1, R.result(plain).delta)

        // Last-trick point tips 19 to 20.
        val ltCfg = TwentyNineConfig(lastTrickPoint = true)
        val lt = overState(declarer = 0, bid = 20, declPts = 19, mult = 1, lastWinner = 2, config = ltCfg)
        assertEquals(20, R.teamPoints(lt, 0))
        assertTrue(R.result(lt).made)
        val ltLost = overState(declarer = 0, bid = 20, declPts = 19, mult = 1, lastWinner = 1, config = ltCfg)
        assertFalse(R.result(ltLost).made)
        assertEquals(10, R.teamPoints(ltLost, 1))

        // Match bookkeeping: only the declarer's team moves, dealer rotates.
        val game = TwentyNineGame()
        val m = game.newMatch(3)
        val m2 = game.finishHand(m, made)
        assertEquals(listOf(0, 2), m2.gamePoints)
        assertEquals((m.dealer + 1) % 4, m2.dealer)
        val m3 = game.finishHand(m2, failed)
        assertEquals(listOf(-4, 2), m3.gamePoints)
        assertFalse(game.isMatchOver(m3))
        val st = game.standings(m3)
        assertEquals("−4", st[0].score)
        assertTrue(st[1].leading)
        assertEquals(2, game.summary(m3, failed).rows.size)
    }

    @Test
    fun full_hands_conserve_cards_and_stay_legal() {
        val rng = Rng(42)
        var scored = 0
        var annulled = 0
        repeat(2000) { n ->
            val cfg = when (n % 5) {
                3 -> TwentyNineConfig(lastTrickPoint = true)
                4 -> TwentyNineConfig(doubling = false, mustTrumpAfterAsking = false, declarerNoTrumpLeadBeforeReveal = false, pair = false)
                else -> TwentyNineConfig()
            }
            var s = TwentyNineState.deal(n % 4, cfg, rng)
            checkConservation(s)
            var guard = 0
            while (!R.isOver(s)) {
                val legal = R.legalActions(s)
                assertFalse(legal.isEmpty())
                val pa = TwentyNinePolicy.choose(s, legal, rng)
                assertTrue("policy chose illegal $pa in ${s.phase}", pa in legal)
                val a = if (rng.nextDouble() < 0.25) legal[rng.nextInt(legal.size)] else pa
                s = R.apply(s, a)
                checkConservation(s)
                assertTrue(++guard < 200)
            }
            assertEquals(8, s.tricks.size)
            assertEquals(8, s.trickWinners.size)
            assertEquals(CardSet.PIQUET, s.played)
            assertTrue(s.hands.all { it.isEmpty() })
            assertEquals(28, s.points.sum())
            assertTrue("the indicator is always played", s.revealed)
            assertTrue(s.target in 16..cfg.maxBid)
            val r = R.result(s)
            if (r.annulled) {
                annulled++
                assertEquals(0, r.delta)
            } else {
                scored++
                assertTrue(r.delta != 0)
                assertEquals(r.multiplier, kotlin.math.abs(r.delta))
            }
        }
        assertTrue(scored > 0)
        assertEquals(2000, scored + annulled)
    }

    private fun checkDeterminized(s: TwentyNineState, w: TwentyNineState, viewer: Int) {
        assertEquals(s.hands[viewer], w.hands[viewer])
        assertEquals(s.played, w.played)
        assertEquals(s.stock.size, w.stock.size)
        for (seat in 0..3) {
            assertEquals(s.hands[seat].size, w.hands[seat].size)
            if (seat == viewer) continue
            val ind = w.indicator
            val h = if (seat == w.declarer && w.revealed && ind != null) w.hands[seat] - ind else w.hands[seat]
            for (v in s.voids[seat]) assertFalse("seat $seat is void in $v", h.has(v))
        }
        checkConservation(w)
        val ind = w.indicator
        if (ind != null) assertEquals(ind.suit, w.trump)
    }

    @Test
    fun determinize_hides_the_indicator_and_respects_voids() {
        val rng = Rng(7)

        // Bidding: stock and other hands are resampled.
        val b = TwentyNineState.deal(1, TwentyNineConfig(), Rng(70))
        var stockChanged = false
        repeat(50) {
            val w = R.determinize(b, 0, rng)
            checkDeterminized(b, w, 0)
            if (w.stock != b.stock) stockChanged = true
        }
        assertTrue(stockChanged)

        var unrevealedTested = 0
        var voidTested = 0
        var revealedTested = 0
        var seed = 0L
        while ((unrevealedTested < 4 || voidTested < 3 || revealedTested < 3) && seed < 600) {
            var s = TwentyNineState.deal((seed % 4).toInt(), TwentyNineConfig(), Rng(seed))
            seed++
            var doneUnrevealed = false
            var doneRevealed = false
            while (!R.isOver(s)) {
                val d = s.declarer
                if (s.phase == Phase.DOUBLING) {
                    val viewer = (d + 1) % 4
                    val inds = mutableSetOf<Card>()
                    repeat(30) {
                        val w = R.determinize(s, viewer, rng)
                        checkDeterminized(s, w, viewer)
                        inds.add(w.indicator!!)
                    }
                    assertTrue(inds.size > 1)
                }
                if (s.phase == Phase.PLAY && !s.revealed && s.tricks.size >= 2 && !doneUnrevealed) {
                    doneUnrevealed = true
                    val viewer = (d + 1) % 4
                    val inds = mutableSetOf<Card>()
                    repeat(100) {
                        val w = R.determinize(s, viewer, rng)
                        checkDeterminized(s, w, viewer)
                        inds.add(w.indicator!!)
                    }
                    assertTrue("non-declarer must not learn the indicator", inds.size > 1)
                    // The declarer knows their own trump.
                    repeat(20) {
                        val w = R.determinize(s, d, rng)
                        checkDeterminized(s, w, d)
                        assertEquals(s.indicator, w.indicator)
                        assertEquals(s.trump, w.trump)
                    }
                    unrevealedTested++
                    if (s.voids.any { it.isNotEmpty() }) voidTested++
                }
                if (s.phase == Phase.PLAY && s.revealed && s.tricks.size >= 3 && !doneRevealed) {
                    doneRevealed = true
                    for (viewer in 0..3) repeat(20) {
                        val w = R.determinize(s, viewer, rng)
                        checkDeterminized(s, w, viewer)
                        assertEquals("a revealed indicator is public", s.indicator, w.indicator)
                        val ind = s.indicator!!
                        if (ind in s.hands[d]) assertTrue(ind in w.hands[d])
                    }
                    revealedTested++
                    if (s.voids.any { it.isNotEmpty() }) voidTested++
                }
                s = R.apply(s, TwentyNinePolicy.choose(s, R.legalActions(s), rng))
            }
        }
        assertTrue(unrevealedTested >= 4)
        assertTrue(voidTested >= 3)
        assertTrue(revealedTested >= 3)
    }

    private fun playMatch(seed: Long): Pair<TwentyNineGame, TwentyNineMatchResult> {
        val game = TwentyNineGame()
        var m = game.newMatch(seed)
        val rng = Rng(seed + 1)
        var hands = 0
        while (!game.isMatchOver(m)) {
            var s = game.deal(m)
            while (!game.rules.isOver(s)) {
                val view = game.table(s)
                if (s.indicator != null && !s.revealed && s.declarer != 0) {
                    assertNull("seat 0 must not see a hidden trump", view.trump)
                    assertTrue(view.trumpHidden)
                }
                val legal = game.rules.legalActions(s)
                val seat = game.rules.toAct(s)
                val a = game.aiSpecial(s, seat, Skill.EASY, rng) ?: game.policy.choose(s, legal, rng)
                assertTrue(a in legal)
                if (seat == 0) game.explain(s, a)
                s = game.rules.apply(s, a)
            }
            assertEquals(Phase.OVER, game.table(s).phase)
            m = game.finishHand(m, s)
            game.summary(m, s)
            assertTrue(++hands < 3000)
        }
        return game to TwentyNineMatchResult(m.gamePoints, m.handsDealt, game.winners(m), m.history.size)
    }

    private data class TwentyNineMatchResult(val points: List<Int>, val handsDealt: Int, val winners: Set<Int>, val historySize: Int)

    @Test
    fun full_match_reaches_six() {
        val (game, r) = playMatch(2024)
        assertTrue(r.points.any { it >= 6 || it <= -6 })
        assertEquals(2, r.winners.size)
        assertEquals(r.handsDealt, r.historySize)
        assertEquals("Match over", game.progress(game.newMatch(1).copy(gamePoints = listOf(6, 0))))
        assertEquals("First to 6", game.progress(game.newMatch(1)))
        assertEquals(setOf(1, 3), game.winners(game.newMatch(1).copy(gamePoints = listOf(-6, 2))))
        assertTrue(game.rulesText().size in 7..10)
        // Deterministic replays.
        assertEquals(r, playMatch(2024).second)
    }

    @Test
    fun ai_team_beats_random_play() {
        val rng = Rng(11)
        val game = TwentyNineGame()
        val ai = MonteCarloAi(TwentyNineRules, TwentyNinePolicy)
        var team0 = 0.0
        var team1 = 0.0
        repeat(30) { n ->
            var s = TwentyNineState.deal(n % 4, TwentyNineConfig(), rng)
            while (!R.isOver(s)) {
                val seat = R.toAct(s)
                val legal = R.legalActions(s)
                val a = when {
                    teamOf(seat) == 0 -> game.aiSpecial(s, seat, Skill.MEDIUM, rng) ?: ai.decide(s, seat, Skill.MEDIUM, rng)
                    s.phase == Phase.BIDDING -> legal[rng.nextInt(minOf(3, legal.size))]
                    else -> legal[rng.nextInt(legal.size)]
                }
                assertTrue(a in legal)
                s = R.apply(s, a)
            }
            val u = R.utility(s)
            team0 += u[0]
            team1 += u[1]
        }
        assertTrue("AI team $team0 should beat random $team1", team0 > team1)
    }
}
