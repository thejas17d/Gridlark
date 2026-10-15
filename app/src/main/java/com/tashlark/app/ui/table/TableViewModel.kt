package com.tashlark.app.ui.table

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.tashlark.app.AppContainer
import com.tashlark.app.data.ActionCodec
import com.tashlark.app.data.GameCatalog
import com.tashlark.app.data.HouseRules
import com.tashlark.app.data.MatchRecord
import com.tashlark.app.data.SavedMatch
import com.tashlark.app.data.SavedRules
import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.CardGame
import com.tashlark.engine.HandState
import com.tashlark.engine.HandSummary
import com.tashlark.engine.MonteCarloAi
import com.tashlark.engine.Rng
import com.tashlark.engine.Skill
import com.tashlark.engine.Standing
import com.tashlark.engine.Suit
import com.tashlark.engine.TableView
import com.tashlark.engine.Teams
import com.tashlark.engine.Trick
import com.tashlark.engine.mixSeed
import com.tashlark.engine.seatName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Everything the table screen draws. Immutable snapshot, rebuilt after every action. */
data class TableUi(
    val loading: Boolean = true,
    val title: String = "",
    val progress: String = "",
    val view: TableView? = null,
    val hand: List<Card> = emptyList(),
    /** Cards seat 0 may play right now (empty when it isn't seat 0's turn to play). */
    val playable: Set<Card> = emptySet(),
    /** Cards seat 0 may set as a face-down trump (29, Mendikot). */
    val trumpCards: Set<Card> = emptySet(),
    /** Suits seat 0 may name as trump (Court Piece). */
    val trumpSuits: List<Suit> = emptyList(),
    val bids: List<Int> = emptyList(),
    val canPass: Boolean = false,
    val canReveal: Boolean = false,
    val canDouble: Boolean = false,
    val canRedouble: Boolean = false,
    val canDecline: Boolean = false,
    val handSizes: List<Int> = List(4) { 0 },
    /** The trick on the table (in progress, or just completed while it's being shown). */
    val trick: Trick? = null,
    val trickWinner: Int? = null,
    val lastTrick: Trick? = null,
    val lastTrickWinner: Int? = null,
    val toAct: Int = -1,
    val teams: Teams = Teams.SOLO,
    val standings: List<Standing> = emptyList(),
    val summary: HandSummary? = null,
    val matchOver: Boolean = false,
    val resultTitle: String = "",
    val hint: Card? = null,
    val hintText: String? = null,
    val illegalMessage: String? = null,
    val illegalKey: Int = 0,
    val rules: List<String> = emptyList(),
    /** Seat 0's own status line, e.g. "Bid 3 · Won 1". */
    val myStatus: String = "",
    /** The face-down trump card, only when seat 0 is the one who placed it. */
    val ownTrumpCard: Card? = null,
)

enum class Cue { CARD_LANDED, TRICK_WON_US, TRICK_WON_THEM, REJECT, HAND_WON, HAND_LOST, MATCH_WON, MATCH_LOST, YOUR_TURN }

/**
 * Runs one match: deals, AI turns with natural pacing, human actions, scoring, saving.
 * Saving is replay-based: the seed plus every action, so a resumed match is identical.
 */
class TableViewModel(
    private val container: AppContainer,
    val gameId: String,
    private val resume: Boolean,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _ui = MutableStateFlow(TableUi())
    val ui: StateFlow<TableUi> = _ui.asStateFlow()

    private val _cues = MutableSharedFlow<Cue>(extraBufferCapacity = 16)
    val cues: SharedFlow<Cue> = _cues.asSharedFlow()

    private lateinit var game: CardGame<HandState, Any>
    private lateinit var ai: MonteCarloAi<HandState>
    private lateinit var match: Any
    private lateinit var hand: HandState
    private var seed = 0L
    private var skill = Skill.MEDIUM
    private var rules = HouseRules()
    private val finishedLogs = mutableListOf<List<String>>()
    private val currentLog = mutableListOf<String>()
    private var assisted = false
    private var aiRng = Rng(1)
    private var loop: Job? = null
    private var showingTrick: Trick? = null
    private var showingWinner: Int? = null
    private var speedFactor = 1.0
    private var reduceMotion = false
    private var handFinished = false
    private var recorded = false
    private var abandoned = false
    /** Saves run one after another, in order, so an older snapshot can never land last. */
    private var lastIo: Job? = null

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val s = container.settings.current()
        speedFactor = s.speed.factor
        reduceMotion = s.reduceMotion
        // After process death the route still says resume=false; the flag says we'd already started.
        val started = savedState.get<Boolean>(STARTED) == true
        val saved = if (resume || started) container.matches.load(gameId) else null
        if (saved != null && runCatching { restore(saved) }.isSuccess) {
            publish()
            runLoop()
            return
        }
        skill = s.skill
        rules = s.rules
        seed = Random.nextLong()
        game = GameCatalog.create(gameId, rules)
        ai = MonteCarloAi(game.rules, game.policy)
        aiRng = Rng(mixSeed(seed, 0x5EED))
        match = game.newMatch(seed)
        finishedLogs.clear()
        currentLog.clear()
        hand = game.deal(match)
        save()
        savedState[STARTED] = true
        publish()
        runLoop()
    }

    /** Rebuilds the match by replaying every recorded action. Throws if anything doesn't fit. */
    private fun restore(saved: SavedMatch) {
        skill = Skill.entries.firstOrNull { it.name == saved.skill } ?: Skill.MEDIUM
        rules = saved.rules.toHouseRules()
        seed = saved.seed
        assisted = saved.assisted
        game = GameCatalog.create(gameId, rules)
        ai = MonteCarloAi(game.rules, game.policy)
        aiRng = Rng(mixSeed(seed, 0x5EED, saved.hands.size.toLong(), saved.current.size.toLong()))
        var m = game.newMatch(seed)
        for (log in saved.hands) {
            var h = game.deal(m)
            for (code in log) h = game.rules.apply(h, ActionCodec.decode(code))
            check(game.rules.isOver(h))
            m = game.finishHand(m, h)
        }
        check(!game.isMatchOver(m))
        var h = game.deal(m)
        for (code in saved.current) h = game.rules.apply(h, ActionCodec.decode(code))
        match = m
        hand = h
        finishedLogs.clear(); finishedLogs.addAll(saved.hands)
        currentLog.clear(); currentLog.addAll(saved.current)
    }

    private fun save() {
        if (abandoned || recorded) return
        val snapshot = SavedMatch(
            gameId = gameId, seed = seed, skill = skill.name, rules = SavedRules.of(rules),
            hands = finishedLogs.toList(), current = currentLog.toList(), assisted = assisted,
            updatedAt = System.currentTimeMillis(),
        )
        val prev = lastIo
        lastIo = container.appScope.launch {
            prev?.join()
            container.matches.save(snapshot)
        }
    }

    /** Throws the match away (no save, no record) and then runs [done]. */
    fun abandon(done: () -> Unit) {
        abandoned = true
        loop?.cancel()
        val prev = lastIo
        viewModelScope.launch {
            withContext(NonCancellable) {
                prev?.join()
                container.matches.clear(gameId)
            }
            done()
        }
    }

    private fun pace(base: Long): Long = if (speedFactor <= 0.0) 0 else (base * speedFactor).toLong()

    // ------------------------------------------------------------------ game loop

    private fun runLoop(showFirst: Boolean = false) {
        loop?.cancel()
        loop = viewModelScope.launch {
            if (showFirst) showTrickIfComplete()
            while (true) {
                if (game.rules.isOver(hand)) {
                    onHandOver()
                    return@launch
                }
                val seat = game.rules.toAct(hand)
                if (seat == 0) {
                    publish()
                    _cues.tryEmit(Cue.YOUR_TURN)
                    return@launch
                }
                publish(thinking = seat)
                val snapshot = hand
                val legal = game.rules.legalActions(snapshot)
                val forced = legal.size == 1
                val isBidLike = legal.none { it is Action.Play }
                val base = if (forced) 320L else if (isBidLike) 900L + aiRng.nextInt(400) else 520L + aiRng.nextInt(260)
                // Fork on the main thread: the shared RNG is never touched from two threads.
                val rng = aiRng.fork()
                val started = SystemClock.uptimeMillis()
                // Think during the natural pause, then wait out only what's left of it.
                val action = withContext(Dispatchers.Default) {
                    if (forced) legal[0]
                    else game.aiSpecial(snapshot, seat, skill, rng) ?: ai.decide(snapshot, seat, skill, rng, maxMillis = 650)
                }
                val left = pace(base) - (SystemClock.uptimeMillis() - started)
                if (left > 0) delay(left)
                ensureActive()
                if (hand !== snapshot) continue
                applyAction(action)
                showTrickIfComplete()
            }
        }
    }

    private fun applyAction(a: Action) {
        val before = hand.tricks.size
        hand = game.rules.apply(hand, a)
        currentLog.add(ActionCodec.encode(a))
        if (a is Action.Play) _cues.tryEmit(Cue.CARD_LANDED)
        if (hand.tricks.size > before) {
            showingTrick = hand.tricks.last()
            showingWinner = winnerOf(hand, showingTrick!!)
        } else if (a is Action.Play) {
            showingTrick = null
            showingWinner = null
        }
        save()
    }

    private suspend fun showTrickIfComplete() {
        val t = showingTrick ?: return
        publish()
        val w = showingWinner
        if (w != null) {
            val ours = if (game.rules.teams == Teams.PAIRS) w % 2 == 0 else w == 0
            _cues.tryEmit(if (ours) Cue.TRICK_WON_US else Cue.TRICK_WON_THEM)
        }
        delay(pace(if (reduceMotion) 700 else 900).coerceAtLeast(450))
        if (showingTrick === t) {
            showingTrick = null
            showingWinner = null
        }
    }

    /**
     * Seat that took completed trick [t]: the game's own answer if it has one, otherwise the
     * leader of the following trick (the winner always leads next).
     */
    private fun winnerOf(state: HandState, t: Trick): Int? {
        game.rules.winnerOf(state, t)?.let { return it }
        val idx = state.tricks.indexOf(t)
        if (idx < 0) return null
        if (idx + 1 < state.tricks.size) return state.tricks[idx + 1].leader
        state.current?.let { return it.leader }
        val seat = game.rules.toAct(state)
        return if (seat >= 0) seat else null
    }

    private fun onHandOver() {
        if (handFinished) return
        handFinished = true
        val summary = game.summary(game.finishHand(match, hand), hand)
        val newMatch = game.finishHand(match, hand)
        match = newMatch
        finishedLogs.add(currentLog.toList())
        currentLog.clear()
        val over = game.isMatchOver(match)
        val winners = if (over) game.winners(match) else emptySet()
        val handWon = game.rules.utility(hand)[0] > 0
        _cues.tryEmit(if (handWon) Cue.HAND_WON else Cue.HAND_LOST)
        if (over) {
            _cues.tryEmit(if (0 in winners) Cue.MATCH_WON else Cue.MATCH_LOST)
            recordMatch(winners)
        } else save()
        _ui.update {
            it.copy(
                summary = summary,
                matchOver = over,
                resultTitle = if (over) resultTitle(winners) else "",
                standings = game.standings(match),
                progress = game.progress(match),
                playable = emptySet(), bids = emptyList(), canPass = false, canReveal = false,
                trumpCards = emptySet(), trumpSuits = emptyList(), canDouble = false, canRedouble = false, canDecline = false,
            )
        }
    }

    private fun resultTitle(winners: Set<Int>): String = when {
        game.rules.teams == Teams.PAIRS -> if (0 in winners) "Your team wins the match" else "The other team wins"
        winners.size > 1 && 0 in winners -> "You share the win"
        0 in winners -> "You win the match"
        winners.size > 1 -> winners.joinToString(" and ") { seatName(it) } + " share the win"
        else -> "${seatName(winners.first())} wins the match"
    }

    private fun recordMatch(winners: Set<Int>) {
        if (recorded) return
        recorded = true
        val yourScore = game.standings(match).firstOrNull()?.score ?: ""
        val record = MatchRecord(
            gameId = gameId, endedAt = System.currentTimeMillis(), won = 0 in winners,
            yourScore = yourScore, hands = finishedLogs.size, skill = skill.name, assisted = assisted,
        )
        val prev = lastIo
        container.appScope.launch {
            prev?.join()
            container.matches.clear(gameId)
            container.db.matches().insert(record)
            container.settings.recordMatchFinished()
        }
    }

    /** Deals the next hand (after the round summary). */
    fun nextHand() {
        if (_ui.value.matchOver || !handFinished) return
        handFinished = false
        hand = game.deal(match)
        showingTrick = null
        _ui.update { it.copy(summary = null, lastTrick = null, lastTrickWinner = null) }
        save()
        publish()
        runLoop()
    }

    // ------------------------------------------------------------------ human input

    private fun humanCan(a: Action): Boolean =
        !game.rules.isOver(hand) && game.rules.toAct(hand) == 0 && a in game.rules.legalActions(hand)

    fun act(a: Action) {
        if (!::game.isInitialized || handFinished) return
        // Not the human's turn (e.g. a fast second tap after playing): ignore quietly.
        if (game.rules.toAct(hand) != 0) return
        if (!humanCan(a)) {
            reject(a)
            return
        }
        _ui.update { it.copy(hint = null, hintText = null, illegalMessage = null) }
        // Stop any loop still showing the previous trick before we move on.
        loop?.cancel()
        applyAction(a)
        runLoop(showFirst = true)
    }

    fun playCard(card: Card) = act(Action.Play(card))

    private fun reject(a: Action) {
        val msg = if (a is Action.Play) illegalReason(a.card) else "Not allowed right now"
        _cues.tryEmit(Cue.REJECT)
        _ui.update { it.copy(illegalMessage = msg, illegalKey = it.illegalKey + 1) }
    }

    private fun illegalReason(card: Card): String {
        if (game.rules.toAct(hand) != 0) return "Wait for your turn"
        val t = hand.current
        val led = t?.ledSuit
        val handCards = hand.hands[0]
        val legal = game.rules.legalActions(hand)
        if (legal.none { it is Action.Play }) return "Finish this step first"
        return when {
            led != null && handCards.has(led) && card.suit != led -> "You must follow ${led.symbol}"
            led != null && card.suit == led -> "You must play a higher ${led.symbol} if you can"
            led != null && !handCards.has(led) && gameId == "callbreak" && card.suit == Suit.SPADES ->
                "Play a higher spade if you can"
            led != null && !handCards.has(led) -> {
                val suits = legal.filterIsInstance<Action.Play>().map { it.card.suit }.toSet()
                if (suits.size == 1) "You must play ${suits.first().symbol}" else "That card isn't allowed here"
            }
            else -> "You can't lead that card yet"
        }
    }

    fun requestHint() {
        if (!::game.isInitialized || handFinished || game.rules.isOver(hand) || game.rules.toAct(hand) != 0) return
        assisted = true
        val snapshot = hand
        val rng = aiRng.fork()
        viewModelScope.launch {
            val a = withContext(Dispatchers.Default) {
                game.aiSpecial(snapshot, 0, Skill.MEDIUM, rng) ?: ai.decide(snapshot, 0, Skill.MEDIUM, rng)
            }
            if (snapshot !== hand) return@launch
            val text = game.explain(snapshot, a) ?: describe(a)
            _ui.update { it.copy(hint = (a as? Action.Play)?.card, hintText = text) }
            save()
        }
    }

    private fun describe(a: Action): String = when (a) {
        is Action.Bid -> "Try a bid of ${a.value}."
        Action.Pass -> "Passing looks wise with this hand."
        is Action.SetTrump -> "Make ${a.suit.symbol} trump" + (a.card?.let { " by setting $it face down" } ?: "") + "."
        Action.CallReveal -> "Ask for the trump to be shown."
        Action.Double -> "Double: your hand can defend this."
        Action.Redouble -> "Redouble: you're confident."
        Action.NoDouble -> "No double this time."
        is Action.Play -> "Play ${a.card}."
    }

    fun clearMessage() = _ui.update { it.copy(illegalMessage = null) }

    // ------------------------------------------------------------------ UI snapshot

    private fun publish(thinking: Int = -1) {
        val legal = if (game.rules.toAct(hand) == 0) game.rules.legalActions(hand) else emptyList()
        val view = game.table(hand)
        val trickOnTable = showingTrick ?: hand.current
        val last = hand.tricks.lastOrNull()
        _ui.update {
            it.copy(
                loading = false,
                title = game.title,
                progress = game.progress(match),
                view = view,
                hand = sortHand(hand.hands[0].toList(), view.trump),
                playable = legal.filterIsInstance<Action.Play>().map { a -> a.card }.toSet(),
                trumpCards = legal.filterIsInstance<Action.SetTrump>().mapNotNull { a -> a.card }.toSet(),
                trumpSuits = legal.filterIsInstance<Action.SetTrump>().filter { a -> a.card == null }.map { a -> a.suit },
                bids = legal.filterIsInstance<Action.Bid>().map { a -> a.value },
                canPass = Action.Pass in legal,
                canReveal = Action.CallReveal in legal,
                canDouble = Action.Double in legal,
                canRedouble = Action.Redouble in legal,
                canDecline = Action.NoDouble in legal,
                handSizes = hand.hands.map { h -> h.size },
                trick = trickOnTable,
                trickWinner = if (showingTrick != null) showingWinner else null,
                lastTrick = last,
                lastTrickWinner = last?.let { t -> winnerOf(hand, t) },
                toAct = if (thinking >= 0) thinking else game.rules.toAct(hand),
                teams = game.rules.teams,
                standings = game.standings(match),
                rules = game.rulesText(),
                myStatus = view.seatStatus.getOrElse(0) { "" },
                ownTrumpCard = view.ownTrumpCard,
            )
        }
    }

    /** Groups by suit (trump first, colours alternating) and sorts by strength within a suit. */
    private fun sortHand(cards: List<Card>, trump: Suit?): List<Card> {
        val order = when (trump) {
            Suit.SPADES, null -> listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)
            Suit.HEARTS -> listOf(Suit.HEARTS, Suit.SPADES, Suit.DIAMONDS, Suit.CLUBS)
            Suit.DIAMONDS -> listOf(Suit.DIAMONDS, Suit.SPADES, Suit.HEARTS, Suit.CLUBS)
            Suit.CLUBS -> listOf(Suit.CLUBS, Suit.HEARTS, Suit.SPADES, Suit.DIAMONDS)
        }
        val strength: (Card) -> Int = if (gameId == "twentynine") ::twentyNineStrength else { c -> c.rank }
        return cards.sortedWith(compareBy<Card>({ order.indexOf(it.suit) }, { -strength(it) }))
    }

    private fun twentyNineStrength(c: Card): Int = when (c.rank) {
        11 -> 8; 9 -> 7; 14 -> 6; 10 -> 5; 13 -> 4; 12 -> 3; 8 -> 2; else -> 1
    }

    override fun onCleared() {
        loop?.cancel()
        if (!abandoned && !_ui.value.matchOver && ::game.isInitialized) save()
    }

    private companion object {
        const val STARTED = "started"
    }
}
