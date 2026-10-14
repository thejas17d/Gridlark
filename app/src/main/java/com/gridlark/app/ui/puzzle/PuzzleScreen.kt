package com.gridlark.app.ui.puzzle

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gridlark.app.AppContainer
import com.gridlark.app.data.Daily
import com.gridlark.app.data.GameType
import com.gridlark.app.data.PlayMode
import com.gridlark.app.data.UserSettings
import com.gridlark.app.data.label
import com.gridlark.app.game.CrownsState
import com.gridlark.app.game.DuoState
import com.gridlark.app.game.SudokuState
import com.gridlark.app.game.TrailState
import com.gridlark.app.ui.components.BareIconButton
import com.gridlark.app.ui.components.GameGlyph
import com.gridlark.app.ui.components.GridIcons
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.components.PaperButton
import com.gridlark.app.ui.components.ToolButton
import com.gridlark.app.ui.components.rememberHaptics
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.ui.theme.TimerStyle
import com.gridlark.app.util.formatDuration
import com.gridlark.app.util.shortLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PuzzleScreen(
    container: AppContainer,
    vm: PuzzleViewModel,
    onBack: () -> Unit,
    onNext: (NextTarget) -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val haptics = rememberHaptics(settings.haptics)
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val c = Grid.colors
    val args = vm.args

    LaunchedEffect(vm, haptics) {
        vm.feedback.collect {
            when (it) {
                Feedback.TICK -> haptics.tick()
                Feedback.DRAG -> haptics.drag()
                Feedback.ON -> haptics.on()
                Feedback.OFF -> haptics.off()
                Feedback.KEY -> haptics.key()
                Feedback.REJECT -> haptics.reject()
                Feedback.SUCCESS -> haptics.success()
            }
        }
    }
    LifecycleResumeEffect(vm) {
        vm.setResumed(true)
        onPauseOrDispose { vm.setResumed(false) }
    }
    // Hint messages fade after a few seconds.
    LaunchedEffect(ui.hint?.key) {
        if (ui.hint != null) { delay(4000); vm.clearHintMessage() }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var hintSheet by remember { mutableStateOf(false) }
    var freeLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.hintsUsed, settings.adsRemoved) { freeLeft = vm.freeHintsLeft() }

    fun leaveResult(target: NextTarget?) {
        val go = { if (target == null) onBack() else onNext(target) }
        val act = activity
        val reviewDue = (ui.completion?.solvedCount ?: 0) >= 5 && !settings.reviewAsked
        when {
            act == null -> go()
            // Never stack a review prompt and an ad: the review moment wins.
            reviewDue -> { askForReview(container, act); go() }
            else -> container.ads.afterResult(act) { go() }
        }
    }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BareIconButton(GridIcons.Back, "Back", onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(args.game.title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.semantics { heading() })
                    val sub = when (args.mode) {
                        PlayMode.DAILY -> {
                            val date = LocalDate.ofEpochDay(args.epochDay)
                            "No. ${Daily.issueNumber(date)} · ${date.shortLabel()} · ${args.difficulty.label()}"
                        }
                        PlayMode.PRACTICE -> "Practice · ${args.difficulty.label()}"
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
                if (settings.showTimer && !ui.loading) {
                    val running = !ui.paused && ui.board?.solved == false && !ui.alreadySolved
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clickable(enabled = running || ui.paused) { vm.setPaused(!ui.paused) }
                            .padding(horizontal = 10.dp)
                            .semantics { contentDescription = "Time ${formatDuration(ui.elapsedMs)}. " + if (ui.paused) "Resume" else "Pause" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatDuration(ui.elapsedMs), style = TimerStyle, color = c.ink)
                        if (running) {
                            Spacer(Modifier.width(6.dp))
                            androidx.compose.material3.Icon(GridIcons.Pause, null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                } else if (!ui.loading && !ui.alreadySolved && ui.board?.solved == false) {
                    BareIconButton(GridIcons.Pause, "Pause", { vm.setPaused(true) })
                }
                Box {
                    BareIconButton(GridIcons.More, "More options", { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = c.surface) {
                        DropdownMenuItem(text = { Text("How to play") }, onClick = { menuOpen = false; vm.showRules() })
                        if (ui.board is SudokuState && !ui.alreadySolved) {
                            DropdownMenuItem(text = { Text("Fill in all notes") }, onClick = { menuOpen = false; vm.autoNotes() })
                        }
                        if (!ui.alreadySolved && ui.board?.solved == false) {
                            DropdownMenuItem(text = { Text("Start over") }, onClick = { menuOpen = false; confirmRestart = true })
                        }
                    }
                }
            }
            Hairline()

            // Board
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight).coerceAtMost(600.dp)
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    val board = ui.board
                    when {
                        ui.loading || board == null -> Text("Setting the board…", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                        ui.paused -> PausedCover { vm.setPaused(false) }
                        else -> {
                            val cbs = GridGestureCallbacks(
                                onDown = vm::onDown,
                                onTap = vm::onTap,
                                onDragStart = vm::onDragStart,
                                onDragEnter = vm::onDragEnter,
                                onDragEnd = vm::onDragEnd,
                            )
                            val hintCell = ui.hint?.cell
                            val hintKey = ui.hint?.key
                            when (board) {
                                is CrownsState -> CrownsBoardView(board, settings.highlightConflicts, settings.patternAssist, hintCell, hintKey, settings.reduceMotion, cbs)
                                is DuoState -> DuoBoardView(board, settings.highlightConflicts, hintCell, hintKey, settings.reduceMotion, cbs)
                                is TrailState -> TrailBoardView(board, hintCell, hintKey, settings.reduceMotion, cbs)
                                is SudokuState -> SudokuBoardView(board, ui.selected, settings.highlightConflicts, hintCell, hintKey, settings.reduceMotion, cbs)
                            }
                        }
                    }
                }
            }

            // Message line: hint text, or the rule in one line.
            val message = ui.hint?.message ?: when {
                ui.alreadySolved -> "Solved in ${formatDuration(ui.elapsedMs)}."
                else -> args.game.tagline + "."
            }
            Text(
                message,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui.hint != null) c.accentText else c.inkMuted,
            )

            val board = ui.board
            if (board is SudokuState && !ui.alreadySolved && !ui.paused) {
                NumberPad(
                    size = board.size,
                    counts = { board.digitCount(it) },
                    notesMode = ui.notesMode,
                    onDigit = vm::onDigit,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Toolbar
            if (!ui.alreadySolved) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val editable = !ui.loading && !ui.paused && ui.board?.solved == false
                    ToolButton(GridIcons.Undo, "Undo", vm::undo, enabled = editable && ui.canUndo)
                    if (board is SudokuState) {
                        ToolButton(GridIcons.Erase, "Erase", vm::onErase, enabled = editable && ui.selected >= 0)
                        ToolButton(GridIcons.Pencil, if (ui.notesMode) "Notes on" else "Notes", vm::toggleNotes, enabled = editable, active = ui.notesMode)
                    }
                    ToolButton(
                        GridIcons.Hint, "Hint",
                        onClick = {
                            scope.launch {
                                if (!vm.tryFreeHint()) hintSheet = true
                            }
                        },
                        enabled = editable,
                        badge = if (freeLeft in 1..9) freeLeft.toString() else null,
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperButton("Share", { shareResult(activity, args.game, args.mode, args.epochDay, args.difficulty.label(), ui.elapsedMs, ui.hintsUsed) },
                        Modifier.weight(1f), primary = false, icon = GridIcons.Share)
                    PaperButton("Done", onBack, Modifier.weight(1f))
                }
            }
        }

        // Rules sheet
        AnimatedVisibility(
            visible = ui.showRules && !ui.loading,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            Scrim { }
        }
        AnimatedVisibility(
            visible = ui.showRules && !ui.loading,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 700f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        ) {
            RulesSheet(args.game) { vm.dismissRules() }
        }

        // Completion card
        val completion = ui.completion
        AnimatedVisibility(visible = completion != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) { Scrim { } }
        AnimatedVisibility(
            visible = completion != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (settings.reduceMotion) fadeIn(tween(150)) else
                slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 2 } + fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            if (completion != null) {
                CompletionCard(
                    game = args.game,
                    completion = completion,
                    nextLabel = when (val n = ui.next) {
                        is NextTarget.Daily -> "Next: ${n.game.title}"
                        is NextTarget.Practice -> "Another one"
                        NextTarget.Home -> null
                    },
                    onShare = { shareResult(activity, args.game, args.mode, args.epochDay, args.difficulty.label(), completion.elapsedMs, completion.hintsUsed) },
                    onNext = { leaveResult(ui.next) },
                    onDone = { leaveResult(null) },
                )
            }
        }

        if (hintSheet) {
            HintDialog(
                rewardedReady = container.ads.rewardedReady(),
                onWatch = {
                    hintSheet = false
                    val act = activity
                    if (act != null) container.ads.showRewarded(act, onReward = { vm.applyHint() }, onSkipped = {})
                    else vm.applyHint()
                },
                onDismiss = { hintSheet = false },
            )
        }
        if (confirmRestart) {
            AlertDialog(
                onDismissRequest = { confirmRestart = false },
                containerColor = c.surface,
                title = { Text("Start over?", style = MaterialTheme.typography.titleMedium) },
                text = { Text("This clears the board. Your time keeps running from where it is.", style = MaterialTheme.typography.bodyMedium) },
                confirmButton = { TextButton({ confirmRestart = false; vm.restart() }) { Text("Start over", color = c.ink) } },
                dismissButton = { TextButton({ confirmRestart = false }) { Text("Keep playing", color = c.inkMuted) } },
            )
        }
    }
}

@Composable
private fun Scrim(onClick: () -> Unit) {
    val c = Grid.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}

@Composable
private fun PausedCover(onResume: () -> Unit) {
    val c = Grid.colors
    Column(
        Modifier.fillMaxSize().background(c.sunken, RoundedCornerShape(6.dp)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Paused", style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Spacer(Modifier.height(16.dp))
        PaperButton("Resume", onResume, icon = GridIcons.Play)
    }
}

private fun rulesFor(game: GameType): Pair<List<String>, String> = when (game) {
    GameType.CROWNS -> listOf(
        "Place one crown in every row, every column and every coloured region.",
        "Crowns can't touch each other, not even at the corners.",
    ) to "Tap once to mark a square ×, twice for a crown. Drag across squares to mark several at once."
    GameType.DUO -> listOf(
        "Fill every square with a sun or a moon.",
        "Each row and column holds as many suns as moons.",
        "No more than two of the same may sit side by side.",
        "= joins two squares that match. × joins two that differ.",
    ) to "Tap a square to cycle sun, moon, blank."
    GameType.TRAIL -> listOf(
        "Draw one line that starts at 1 and passes through every square once.",
        "Visit the numbers in order and finish on the last one.",
        "The line can't cross the thick walls.",
    ) to "Drag from 1. Drag back, or tap a square on your line, to rewind."
    GameType.SUDOKU -> listOf(
        "Fill every row, column and box with each digit exactly once.",
    ) to "Tap a square, then a digit. Turn on Notes to pencil in candidates."
}

@Composable
private fun RulesSheet(game: GameType, onDone: () -> Unit) {
    val c = Grid.colors
    val (rules, controls) = rulesFor(game)
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .background(c.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameGlyph(game, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(game.title, style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(16.dp))
        rules.forEachIndexed { i, rule ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = c.accentText, modifier = Modifier.width(24.dp))
                Text(rule, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Text(controls, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        Spacer(Modifier.height(8.dp))
        Text("Every puzzle has exactly one answer and can be solved by logic alone.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        Spacer(Modifier.height(20.dp))
        PaperButton("Start", onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CompletionCard(
    game: GameType,
    completion: Completion,
    nextLabel: String?,
    onShare: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Grid.colors
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .background(c.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameGlyph(game, size = 44.dp, solved = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Solved", style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })
                Text("in ${formatDuration(completion.elapsedMs)}", style = MaterialTheme.typography.titleMedium, color = c.inkMuted)
            }
        }
        Spacer(Modifier.height(18.dp))
        val lines = buildList {
            if (completion.isNewBest) add("Your fastest ${game.title} yet.")
            completion.medianMs?.let { add("Your usual time: ${formatDuration(it)}.") }
            if (completion.hintsUsed > 0) add(if (completion.hintsUsed == 1) "Solved with 1 hint." else "Solved with ${completion.hintsUsed} hints.")
            if (completion.solvedCount == 1) add("Your first ${game.title}. Nicely done.")
        }
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaperButton("Share", onShare, Modifier.weight(1f), primary = false, icon = GridIcons.Share)
            PaperButton(nextLabel ?: "Done", if (nextLabel != null) onNext else onDone, Modifier.weight(1.4f))
        }
        if (nextLabel != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onDone, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Back to today", style = MaterialTheme.typography.labelLarge, color = c.inkMuted)
            }
        }
    }
}

@Composable
private fun HintDialog(rewardedReady: Boolean, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val c = Grid.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Need a nudge?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                if (rewardedReady) "You've used today's free hint. Watch a short ad for another one."
                else "You've used today's free hint. Here's one on us.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onWatch) { Text(if (rewardedReady) "Watch ad" else "Show hint", color = c.ink, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Keep thinking", color = c.inkMuted) } },
    )
}

private fun shareResult(
    activity: Activity?,
    game: GameType,
    mode: PlayMode,
    epochDay: Long,
    difficulty: String,
    elapsedMs: Long,
    hints: Int,
) {
    activity ?: return
    val head = if (mode == PlayMode.DAILY) {
        val d = LocalDate.ofEpochDay(epochDay)
        "Gridlark No. ${Daily.issueNumber(d)} · ${d.shortLabel()}"
    } else "Gridlark practice"
    val hintText = when (hints) { 0 -> "no hints"; 1 -> "1 hint"; else -> "$hints hints" }
    val text = "$head\n${game.title} ($difficulty) solved in ${formatDuration(elapsedMs)}, $hintText.\n" +
        "https://play.google.com/store/apps/details?id=com.gridlark.app"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { activity.startActivity(Intent.createChooser(send, "Share result")) }
}

private fun askForReview(container: AppContainer, activity: Activity) {
    container.appScope.launch {
        container.settings.setReviewAsked()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            runCatching {
                val manager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
                manager.requestReviewFlow().addOnCompleteListener { task ->
                    if (task.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, task.result)
                }
            }
        }
    }
}
