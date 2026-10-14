package com.gridlark.app.ui.puzzle

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridlark.app.AppContainer
import com.gridlark.app.ads.AdsManager
import com.gridlark.app.data.AnyPuzzle
import com.gridlark.app.data.GameType
import com.gridlark.app.data.PlayEntity
import com.gridlark.app.data.PlayMode
import com.gridlark.app.data.PuzzleFactory
import com.gridlark.app.game.BoardState
import com.gridlark.app.game.CrownsState
import com.gridlark.app.game.DuoState
import com.gridlark.app.game.SavedBoard
import com.gridlark.app.game.SudokuState
import com.gridlark.app.game.TrailState
import com.gridlark.engine.Difficulty
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** What a puzzle screen is showing. */
data class PuzzleArgs(
    val game: GameType,
    val mode: PlayMode,
    val epochDay: Long,
    val seed: Long,
    val difficulty: Difficulty,
) {
    val id: String
        get() = if (mode == PlayMode.DAILY) PlayEntity.dailyId(game, epochDay) else PlayEntity.practiceId(game, seed)
}

/** Short-lived spotlight on a hinted cell. [key] changes for every hint so the pulse restarts. */
data class HintFlash(val cell: Int, val message: String, val key: Int)

data class Completion(
    val elapsedMs: Long,
    val hintsUsed: Int,
    val medianMs: Long?,
    val previousBestMs: Long?,
    val solvedCount: Int,
) {
    val isNewBest: Boolean get() = hintsUsed == 0 && previousBestMs != null && elapsedMs < previousBestMs
}

/** Where "Next" goes after a solve. */
sealed interface NextTarget {
    data class Daily(val game: GameType, val epochDay: Long) : NextTarget
    data class Practice(val game: GameType, val difficulty: Difficulty) : NextTarget
    data object Home : NextTarget
}

enum class Feedback { TICK, DRAG, ON, OFF, KEY, REJECT, SUCCESS }

data class PuzzleUi(
    val loading: Boolean = true,
    val board: BoardState? = null,
    val canUndo: Boolean = false,
    val elapsedMs: Long = 0,
    val hintsUsed: Int = 0,
    val paused: Boolean = false,
    val showRules: Boolean = false,
    val hint: HintFlash? = null,
    val selected: Int = -1,
    val notesMode: Boolean = false,
    val completion: Completion? = null,
    val alreadySolved: Boolean = false,
    val next: NextTarget = NextTarget.Home,
)

class PuzzleViewModel(private val container: AppContainer, val args: PuzzleArgs) : ViewModel() {

    private val dao = container.db.plays()
    private val _ui = MutableStateFlow(PuzzleUi())
    val ui: StateFlow<PuzzleUi> = _ui.asStateFlow()

    private val _feedback = MutableSharedFlow<Feedback>(extraBufferCapacity = 16)
    val feedback: SharedFlow<Feedback> = _feedback.asSharedFlow()

    private var puzzle: AnyPuzzle? = null
    private val undo = ArrayDeque<BoardState>()
    private var gestureSnapshot: BoardState? = null
    private var dragMode: Pair<Int, Int>? = null
    private var hintCounter = 0

    // Timer: accumulated time plus the current running stretch.
    private var baseElapsed = 0L
    private var runningSince: Long? = null
    private var resumed = false
    private var ticker: Job? = null

    private var record: PlayEntity? = null
    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            val p = withContext(Dispatchers.Default) {
                PuzzleFactory.create(args.game, args.difficulty, args.seed, daily = args.mode == PlayMode.DAILY)
            }
            puzzle = p
            val existing = dao.get(args.id)
            record = existing
            val saved = existing?.stateJson?.let { runCatching { json.decodeFromString(SavedBoard.serializer(), it) }.getOrNull() }
            val board = withContext(Dispatchers.Default) { BoardState.restore(p, saved) }
            baseElapsed = existing?.elapsedMs ?: 0L
            val rulesSeen = container.settings.current().rulesSeen
            val solvedBefore = existing?.solved == true
            _ui.value = PuzzleUi(
                loading = false,
                board = board,
                elapsedMs = baseElapsed,
                hintsUsed = existing?.hintsUsed ?: 0,
                showRules = args.game.key !in rulesSeen && !solvedBefore,
                alreadySolved = solvedBefore,
            )
            updateClock()
        }
    }

    // ------------------------------------------------------------------ lifecycle & timer

    fun setResumed(isResumed: Boolean) {
        resumed = isResumed
        updateClock()
        if (!isResumed) saveNow()
    }

    fun setPaused(paused: Boolean) {
        _ui.update { it.copy(paused = paused) }
        updateClock()
    }

    fun dismissRules() {
        _ui.update { it.copy(showRules = false) }
        viewModelScope.launch { container.settings.markRulesSeen(args.game) }
        updateClock()
    }

    fun showRules() {
        _ui.update { it.copy(showRules = true) }
        updateClock()
    }

    private fun shouldRun(): Boolean {
        val u = _ui.value
        return resumed && !u.loading && !u.paused && !u.showRules && u.board?.solved == false && !u.alreadySolved
    }

    private fun currentElapsed(): Long = baseElapsed + (runningSince?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

    private fun updateClock() {
        val run = shouldRun()
        if (run && runningSince == null) {
            runningSince = SystemClock.elapsedRealtime()
            ticker?.cancel()
            ticker = viewModelScope.launch {
                while (isActive) {
                    _ui.update { it.copy(elapsedMs = currentElapsed()) }
                    delay(250)
                }
            }
        } else if (!run && runningSince != null) {
            baseElapsed = currentElapsed()
            runningSince = null
            ticker?.cancel()
            ticker = null
            _ui.update { it.copy(elapsedMs = baseElapsed) }
        }
    }

    // ------------------------------------------------------------------ editing core

    private fun canEdit(): Boolean {
        val u = _ui.value
        return !u.loading && !u.paused && u.completion == null && u.board?.solved == false && !u.alreadySolved
    }

    /** Starts a gesture: remembers the board so the whole gesture undoes in one step. */
    private fun begin() {
        if (gestureSnapshot == null) gestureSnapshot = _ui.value.board
    }

    private fun set(board: BoardState) {
        _ui.update { it.copy(board = board) }
    }

    /** Ends a gesture: records one undo step if anything changed, then checks for a solve. */
    private fun commit() {
        val before = gestureSnapshot
        gestureSnapshot = null
        dragMode = null
        val now = _ui.value.board ?: return
        if (before == null || before === now) return
        undo.addLast(before)
        while (undo.size > MAX_UNDO) undo.removeFirst()
        _ui.update { it.copy(canUndo = true, hint = null) }
        afterChange(now)
    }

    private fun afterChange(board: BoardState) {
        if (board.solved) onSolved() else scheduleSave()
    }

    fun undo() {
        if (!canEdit()) return
        val prev = undo.removeLastOrNull() ?: return
        gestureSnapshot = null
        _ui.update { it.copy(board = prev, canUndo = undo.isNotEmpty(), hint = null) }
        _feedback.tryEmit(Feedback.TICK)
        scheduleSave()
    }

    fun restart() {
        val p = puzzle ?: return
        if (!canEdit()) return
        val current = _ui.value.board ?: return
        undo.addLast(current)
        _ui.update { it.copy(board = BoardState.fresh(p), canUndo = true, hint = null, selected = -1) }
        scheduleSave()
    }

    // ------------------------------------------------------------------ gestures per game

    fun onDown(cell: Int) {
        if (!canEdit()) return
        begin()
        val b = _ui.value.board
        if (b is TrailState) {
            val next = when {
                b.path.isEmpty() -> b.extend(cell)
                cell in b.path -> b.truncateTo(cell)
                else -> b.extend(cell)
            }
            if (next !== b) {
                set(next)
                _feedback.tryEmit(if (b.puzzle.numberAt[cell] != 0) Feedback.ON else Feedback.TICK)
            } else if (b.path.isEmpty()) {
                _feedback.tryEmit(Feedback.REJECT)
            }
        }
    }

    fun onTap(cell: Int) {
        if (!canEdit()) { gestureSnapshot = null; return }
        begin()
        when (val b = _ui.value.board) {
            is CrownsState -> {
                val next = b.cycle(cell)
                set(next)
                _feedback.tryEmit(
                    when (next.marks[cell]) {
                        CrownsState.CROWN -> Feedback.ON
                        CrownsState.EMPTY -> Feedback.OFF
                        else -> Feedback.TICK
                    },
                )
            }
            is DuoState -> {
                if (b.isGiven(cell)) {
                    _feedback.tryEmit(Feedback.REJECT)
                } else {
                    val next = b.cycle(cell)
                    set(next)
                    _feedback.tryEmit(if (next.values[cell] == 0) Feedback.OFF else Feedback.ON)
                }
            }
            is SudokuState -> {
                _ui.update { it.copy(selected = if (it.selected == cell) -1 else cell) }
                _feedback.tryEmit(Feedback.TICK)
            }
            is TrailState, null -> Unit
        }
        commit()
    }

    fun onDragStart(start: Int) {
        if (!canEdit()) return
        begin()
        val b = _ui.value.board
        if (b is CrownsState) {
            dragMode = when (b.marks[start]) {
                CrownsState.EMPTY -> CrownsState.EMPTY to CrownsState.CROSS
                CrownsState.CROSS -> CrownsState.CROSS to CrownsState.EMPTY
                else -> null
            }
            dragMode?.let { (from, to) -> set(b.withAll(listOf(start), from, to)) }
        }
    }

    fun onDragEnter(cell: Int) {
        if (!canEdit()) return
        when (val b = _ui.value.board) {
            is CrownsState -> {
                val (from, to) = dragMode ?: return
                val next = b.withAll(listOf(cell), from, to)
                if (next !== b) { set(next); _feedback.tryEmit(Feedback.DRAG) }
            }
            is TrailState -> {
                val next = when {
                    b.path.size >= 2 && cell == b.path[b.path.size - 2] -> b.backOne()
                    cell in b.path -> b.truncateTo(cell)
                    else -> b.extend(cell)
                }
                if (next !== b) {
                    set(next)
                    val reachedNumber = next.path.size > b.path.size && b.puzzle.numberAt[cell] != 0
                    _feedback.tryEmit(if (reachedNumber) Feedback.ON else Feedback.DRAG)
                }
            }
            else -> Unit
        }
    }

    fun onDragEnd() = commit()

    // ------------------------------------------------------------------ sudoku tools

    fun onDigit(d: Int) {
        if (!canEdit()) return
        val b = _ui.value.board as? SudokuState ?: return
        val cell = _ui.value.selected
        if (cell < 0) { _feedback.tryEmit(Feedback.REJECT); return }
        if (b.isGiven(cell)) { _feedback.tryEmit(Feedback.REJECT); return }
        begin()
        set(if (_ui.value.notesMode) b.toggleNote(cell, d) else b.place(cell, d))
        _feedback.tryEmit(Feedback.KEY)
        commit()
    }

    fun onErase() {
        if (!canEdit()) return
        val b = _ui.value.board as? SudokuState ?: return
        val cell = _ui.value.selected
        if (cell < 0) return
        begin()
        set(b.erase(cell))
        commit()
    }

    fun toggleNotes() {
        _ui.update { it.copy(notesMode = !it.notesMode) }
        _feedback.tryEmit(Feedback.TICK)
    }

    fun autoNotes() {
        if (!canEdit()) return
        val b = _ui.value.board as? SudokuState ?: return
        begin()
        set(b.autoNotes())
        commit()
    }

    // ------------------------------------------------------------------ hints

    /** Free hints left today (ads-free players have unlimited). */
    suspend fun freeHintsLeft(): Int {
        if (container.settings.current().adsRemoved) return Int.MAX_VALUE
        return container.settings.freeHintsLeft(LocalDate.now().toEpochDay(), AdsManager.FREE_HINTS_PER_DAY)
    }

    /** Uses a free hint if one is left. Returns false when the player must choose (ad / skip). */
    suspend fun tryFreeHint(): Boolean {
        if (!canEdit()) return true
        val s = container.settings.current()
        val granted = s.adsRemoved ||
            container.settings.tryUseFreeHint(LocalDate.now().toEpochDay(), AdsManager.FREE_HINTS_PER_DAY)
        if (granted) applyHint()
        return granted
    }

    fun applyHint() {
        if (!canEdit()) return
        val b = _ui.value.board ?: return
        val hint = b.hint() ?: return
        begin()
        set(hint.board)
        hintCounter++
        _ui.update { it.copy(hintsUsed = it.hintsUsed + 1) }
        val flash = HintFlash(hint.cell, hint.message, hintCounter)
        commit()
        _ui.update { it.copy(hint = flash) }
    }

    fun clearHintMessage() {
        _ui.update { it.copy(hint = null) }
    }

    // ------------------------------------------------------------------ solve & persistence

    private fun onSolved() {
        updateClock()
        val elapsed = currentElapsed().also { baseElapsed = it }
        _feedback.tryEmit(Feedback.SUCCESS)
        viewModelScope.launch {
            val previous = dao.solvedForGame(args.game.key).filter { it.id != args.id }
            val clean = previous.filter { it.hintsUsed == 0 }.map { it.elapsedMs }.sorted()
            val pool = clean.ifEmpty { previous.map { it.elapsedMs }.sorted() }
            val median = if (pool.isEmpty()) null else {
                val m = pool.size / 2
                if (pool.size % 2 == 1) pool[m] else (pool[m - 1] + pool[m]) / 2
            }
            persist(solved = true)
            container.settings.recordSolve()
            val next = computeNext()
            _ui.update {
                it.copy(
                    elapsedMs = elapsed,
                    completion = Completion(elapsed, it.hintsUsed, median, clean.firstOrNull(), previous.size + 1),
                    next = next,
                    hint = null,
                )
            }
        }
    }

    private suspend fun computeNext(): NextTarget = when (args.mode) {
        PlayMode.PRACTICE -> NextTarget.Practice(args.game, args.difficulty)
        PlayMode.DAILY -> {
            val solved = dao.dailies(args.epochDay, args.epochDay).filter { it.solved }.map { it.game }.toSet()
            val order = GameType.entries
            val start = order.indexOf(args.game)
            val nextGame = (1 until order.size).map { order[(start + it) % order.size] }.firstOrNull { it.key !in solved }
            if (nextGame != null) NextTarget.Daily(nextGame, args.epochDay) else NextTarget.Home
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            persist(solved = false)
        }
    }

    /** Saves on the app scope so the write finishes even if the screen is closing. */
    private fun saveNow() {
        if (_ui.value.loading) return
        saveJob?.cancel()
        container.appScope.launch { persist(solved = _ui.value.board?.solved == true) }
    }

    private suspend fun persist(solved: Boolean) {
        val board = _ui.value.board ?: return
        if (_ui.value.alreadySolved) return
        saveMutex.withLock {
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            val prev = record ?: dao.get(args.id)
            if (prev?.solved == true) { record = prev; return }
            val entity = PlayEntity(
                id = args.id,
                game = args.game.key,
                mode = args.mode.name,
                epochDay = if (args.mode == PlayMode.DAILY) args.epochDay else (prev?.epochDay ?: today),
                seed = args.seed,
                difficulty = args.difficulty.name,
                stateJson = json.encodeToString(SavedBoard.serializer(), board.save()),
                elapsedMs = if (solved) baseElapsed else currentElapsed(),
                hintsUsed = _ui.value.hintsUsed,
                mistakesFixed = 0,
                solved = solved,
                solvedAtMillis = if (solved) now else null,
                solvedOnEpochDay = if (solved) today else null,
                updatedAt = now,
            )
            dao.upsert(entity)
            record = entity
        }
    }

    override fun onCleared() {
        saveNow()
    }

    companion object {
        private const val MAX_UNDO = 300
        private val json = Json { ignoreUnknownKeys = true }
    }
}
