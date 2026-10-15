package com.tashlark.app.ui.table

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tashlark.app.AppContainer
import com.tashlark.app.ads.AdsManager
import com.tashlark.app.data.CardSize
import com.tashlark.app.data.UserSettings
import com.tashlark.app.ui.cards.PlayingCard
import com.tashlark.app.ui.components.BareIconButton
import com.tashlark.app.ui.components.GridIcons
import com.tashlark.app.ui.components.Hairline
import com.tashlark.app.ui.components.PaperButton
import com.tashlark.app.ui.components.rememberHaptics
import com.tashlark.app.ui.theme.Tash
import com.tashlark.engine.Action
import com.tashlark.engine.Card
import com.tashlark.engine.Phase
import com.tashlark.engine.Teams
import com.tashlark.engine.seatName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TableScreen(
    container: AppContainer,
    vm: TableViewModel,
    onBack: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val haptics = rememberHaptics(settings.haptics)
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val c = Tash.colors
    val cardWidth = if (settings.cardSize == CardSize.LARGE) 84.dp else 72.dp
    val rewardedReady by container.ads.rewardedReadyFlow.collectAsStateWithLifecycle()

    LaunchedEffect(vm, haptics) {
        vm.cues.collect {
            when (it) {
                Cue.CARD_LANDED -> haptics.tick()
                Cue.TRICK_WON_US, Cue.HAND_WON, Cue.MATCH_WON -> haptics.success()
                Cue.REJECT -> haptics.reject()
                else -> Unit
            }
        }
    }
    LaunchedEffect(ui.illegalKey) {
        if (ui.illegalMessage != null) { delay(2200); vm.clearMessage() }
    }

    var selected by remember { mutableStateOf<Card?>(null) }
    var bidChoice by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showLastTrick by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // Clear a stale selection whenever the options change.
    LaunchedEffect(ui.playable, ui.trumpCards) {
        if (selected != null && selected !in ui.playable && selected !in ui.trumpCards) selected = null
    }
    LaunchedEffect(ui.bids) { if (bidChoice != null && bidChoice !in ui.bids) bidChoice = null }

    fun leave(kind: AdsManager.Break, then: () -> Unit) {
        if (leaving) return
        leaving = true
        val act = activity
        if (act == null) then() else container.ads.atBreak(act, kind) { then() }
    }

    BackHandler(enabled = showRules || showLastTrick) { showRules = false; showLastTrick = false }

    Box(Modifier.fillMaxSize().background(c.table)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BareIconButton(GridIcons.Back, "Back to games", onBack)
                Column(Modifier.weight(1f)) {
                    Text(ui.title, style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text(ui.progress, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
                BareIconButton(GridIcons.Hint, "Suggest a move", { vm.requestHint() })
                Box {
                    BareIconButton(GridIcons.More, "More", { menuOpen = true })
                    DropdownMenu(menuOpen, { menuOpen = false }, containerColor = c.panel) {
                        DropdownMenuItem(text = { Text("Rules") }, onClick = { menuOpen = false; showRules = true })
                        if (ui.lastTrick != null) {
                            DropdownMenuItem(text = { Text("Last trick") }, onClick = { menuOpen = false; showLastTrick = true })
                        }
                        DropdownMenuItem(text = { Text("Abandon match") }, onClick = { menuOpen = false; confirmQuit = true })
                    }
                }
            }
            ScoreStrip(ui)
            Hairline()

            // Table area: partner/opponents around a central trick.
            Box(Modifier.weight(1f).heightIn(min = 150.dp).fillMaxWidth()) {
                val view = ui.view
                if (ui.loading || view == null) {
                    Text("Shuffling…", Modifier.align(Alignment.Center), color = c.inkMuted)
                } else {
                    for (seat in 1..3) {
                        val side = sideOf(seat, settings.direction)
                        val align = when (side) {
                            Side.TOP -> Alignment.TopCenter
                            Side.LEFT -> Alignment.CenterStart
                            Side.RIGHT -> Alignment.CenterEnd
                            Side.BOTTOM -> Alignment.BottomCenter
                        }
                        SeatChip(
                            seat = seat,
                            status = view.seatStatus.getOrElse(seat) { "" },
                            cards = ui.handSizes.getOrElse(seat) { 0 },
                            active = ui.toAct == seat,
                            partner = ui.teams == Teams.PAIRS && seat == 2,
                            modifier = Modifier.align(align).padding(8.dp),
                        )
                    }
                    TrickArea(
                        trick = ui.trick, winner = ui.trickWinner, direction = settings.direction,
                        cardWidth = cardWidth * 0.86f, fourColour = settings.fourColourDeck,
                        reduceMotion = settings.reduceMotion,
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.62f).fillMaxSize(0.62f),
                    )
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrumpBadge(view.trump, view.trumpHidden, settings.fourColourDeck)
                            ui.ownTrumpCard?.let { own ->
                                Spacer(Modifier.width(8.dp))
                                PlayingCard(
                                    own, true, 34.dp, settings.fourColourDeck,
                                    modifier = Modifier.semantics { contentDescription = "Your hidden trump card: ${own.spoken}" },
                                )
                            }
                        }
                        if (ui.myStatus.isNotBlank()) {
                            Text("You · ${ui.myStatus}", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        val urgent = ui.illegalMessage != null || view.prompt != null
                        Text(
                            ui.illegalMessage ?: ui.hintText ?: view.prompt ?: view.headline,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (ui.illegalMessage != null) FontWeight.SemiBold else FontWeight.Normal),
                            color = when {
                                ui.illegalMessage != null -> c.madder
                                ui.hintText != null -> c.neem
                                else -> c.ink
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp).then(
                                if (urgent) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                            ),
                        )
                    }
                }
            }

            // Decision panels for the human.
            val myTurn = ui.toAct == 0 && ui.summary == null
            when {
                myTurn && ui.bids.isNotEmpty() -> BidPanel(
                    bids = ui.bids, canPass = ui.canPass, selected = bidChoice, suggestion = null,
                    title = ui.view?.prompt,
                    onSelect = { bidChoice = it; haptics.tick() },
                    onConfirm = { bidChoice?.let { b -> vm.act(Action.Bid(b)); bidChoice = null } },
                    onPass = { vm.act(Action.Pass) },
                )
                myTurn && ui.trumpSuits.isNotEmpty() -> SuitPanel(ui.trumpSuits, settings.fourColourDeck, { s -> vm.act(Action.SetTrump(null, s)) })
                myTurn && (ui.canDouble || ui.canRedouble || ui.canDecline) -> Row(
                    Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (ui.canDecline) PaperButton("No double", { vm.act(Action.NoDouble) }, Modifier.weight(1f), primary = false)
                    if (ui.canDouble) PaperButton("Double", { vm.act(Action.Double) }, Modifier.weight(1f))
                    if (ui.canRedouble) PaperButton("Redouble", { vm.act(Action.Redouble) }, Modifier.weight(1f))
                }
                myTurn && ui.trumpCards.isNotEmpty() -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selected == null) "Tap a card to place face down as trump" else "Hide $selected as trump?",
                        style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f),
                    )
                    PaperButton("Set trump", {
                        selected?.let { card -> vm.act(Action.SetTrump(card, card.suit)); selected = null }
                    }, enabled = selected != null)
                }
                myTurn && ui.canReveal -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (ui.playable.isEmpty()) "Your hidden trump is your last card: reveal it to play."
                        else "You can't follow suit. Play a card, or ask to see the trump.",
                        style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.weight(1f),
                    )
                    PaperButton("Show trump", { vm.act(Action.CallReveal) }, primary = false)
                }
            }

            HandView(
                cards = ui.hand,
                playable = if (myTurn) ui.playable else emptySet(),
                selectable = if (myTurn) ui.trumpCards else emptySet(),
                selected = selected,
                hint = ui.hint,
                myTurn = myTurn,
                cardWidth = cardWidth,
                fourColour = settings.fourColourDeck,
                onTap = { card ->
                    when {
                        myTurn && card in ui.trumpCards -> { selected = card; haptics.tick() }
                        myTurn && card in ui.playable -> {
                            if (settings.oneTapPlay || selected == card) {
                                selected = null
                                vm.playCard(card)
                            } else {
                                selected = card
                                haptics.tick()
                            }
                        }
                        myTurn && ui.playable.isNotEmpty() -> vm.playCard(card) // shows why it isn't allowed
                        else -> Unit
                    }
                },
            )
        }

        // Round summary / match result sheet.
        val summary = ui.summary
        AnimatedVisibility(summary != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(c.table.copy(alpha = 0.75f)).clickable(remember { MutableInteractionSource() }, null) {})
        }
        AnimatedVisibility(
            visible = summary != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (settings.reduceMotion) fadeIn(tween(150)) else slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
        ) {
            if (summary != null) {
                ResultSheet(
                    ui = ui,
                    showRewarded = ui.matchOver && !settings.adsRemoved && rewardedReady &&
                        System.currentTimeMillis() >= settings.adFreeUntil,
                    onNext = { if (!leaving) leave(AdsManager.Break.ROUND) { leaving = false; vm.nextHand() } },
                    onPlayAgain = { leave(AdsManager.Break.MATCH) { onPlayAgain() } },
                    onHome = { leave(AdsManager.Break.MATCH) { onBack() } },
                    onRewarded = {
                        val act = activity ?: return@ResultSheet
                        container.ads.showRewarded(act, onReward = {
                            scope.launch { container.settings.setAdFreeUntil(System.currentTimeMillis() + AdsManager.AD_FREE_HOUR_MS) }
                        }, onSkipped = {})
                    },
                )
            }
        }

        if (showRules) {
            RulesDialog(ui.title, ui.rules) { showRules = false }
        }
        if (showLastTrick) {
            val t = ui.lastTrick
            AlertDialog(
                onDismissRequest = { showLastTrick = false },
                containerColor = c.panel,
                title = { Text("Last trick", style = MaterialTheme.typography.titleMedium) },
                text = {
                    if (t == null) Text("No trick yet.") else Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            t.cards.forEachIndexed { i, card ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    PlayingCard(card, true, 52.dp, settings.fourColourDeck)
                                    Text(seatName(t.seatOf(i)), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${seatName(t.leader)} led." + (ui.lastTrickWinner?.let { w -> " ${seatName(w)} took it." } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = { TextButton({ showLastTrick = false }) { Text("Close", color = c.ink) } },
            )
        }
        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                containerColor = c.panel,
                title = { Text("Abandon this match?", style = MaterialTheme.typography.titleMedium) },
                text = { Text("The match won't be saved or counted in your record.") },
                confirmButton = {
                    TextButton({
                        confirmQuit = false
                        vm.abandon(onBack)
                    }) { Text("Abandon", color = c.madder) }
                },
                dismissButton = { TextButton({ confirmQuit = false }) { Text("Keep playing", color = c.ink) } },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScoreStrip(ui: TableUi) {
    val c = Tash.colors
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ui.standings.forEach { s ->
            Text(
                "${s.label} ${s.score}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (s.leading) FontWeight.Bold else FontWeight.Medium),
                color = if (s.leading) c.ink else c.inkMuted,
            )
        }
    }
}

@Composable
private fun ResultSheet(
    ui: TableUi,
    showRewarded: Boolean,
    onNext: () -> Unit,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    onRewarded: () -> Unit,
) {
    val c = Tash.colors
    val summary = ui.summary ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .background(c.panel, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        if (ui.matchOver) {
            Text(ui.resultTitle, style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
        }
        Text(summary.title, style = MaterialTheme.typography.titleMedium, color = if (ui.matchOver) c.inkMuted else c.ink)
        Spacer(Modifier.height(10.dp))
        summary.rows.forEach { (who, detail, delta) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(who, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.width(110.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.weight(1f))
                Text(
                    delta,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (delta.startsWith("+")) c.neem else if (delta.startsWith("−") || delta.startsWith("-")) c.madder else c.ink,
                )
            }
            Hairline()
        }
        summary.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink)
        }
        Spacer(Modifier.height(14.dp))
        Text("Standings", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
        ui.standings.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(s.label, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                Text(s.score, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (s.leading) FontWeight.Bold else FontWeight.Normal), color = c.ink)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (ui.matchOver) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperButton("Home", onHome, Modifier.weight(1f), primary = false)
                PaperButton("Play again", onPlayAgain, Modifier.weight(1f))
            }
            if (showRewarded) {
                TextButton(onRewarded, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Watch a short ad for an hour without ads", color = c.inkMuted)
                }
            }
        } else {
            PaperButton("Next round", onNext, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun RulesDialog(title: String, rules: List<String>, onClose: () -> Unit) {
    val c = Tash.colors
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.panel,
        title = { Text("How $title works", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rules.forEachIndexed { i, r ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = c.madder, modifier = Modifier.width(22.dp))
                        Text(r, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("House rules can be changed in Settings.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
        },
        confirmButton = { TextButton(onClose) { Text("Got it", color = c.ink) } },
    )
}
