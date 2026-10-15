package com.tashlark.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tashlark.app.AppContainer
import com.tashlark.app.BuildConfig
import com.tashlark.app.billing.BillingManager
import com.tashlark.app.data.CardSize
import com.tashlark.app.data.Direction
import com.tashlark.app.data.GameCatalog
import com.tashlark.app.data.HouseRules
import com.tashlark.app.data.SettingsRepository
import com.tashlark.app.data.Speed
import com.tashlark.app.data.UserSettings
import com.tashlark.app.ui.components.BareIconButton
import com.tashlark.app.ui.components.GridIcons
import com.tashlark.app.ui.components.Hairline
import com.tashlark.app.ui.components.SectionLabel
import com.tashlark.app.ui.components.SwitchRow
import com.tashlark.app.ui.components.ValueRow
import com.tashlark.app.ui.theme.Tash
import com.tashlark.app.ui.theme.ThemeMode
import com.tashlark.engine.Skill
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tash.colors
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val repo = container.settings

    val s by repo.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val billingStatus by container.billing.status.collectAsStateWithLifecycle()
    val privacyOptionsRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()

    var expandedGame by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var historyCleared by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.billing.connect() }

    fun write(block: suspend SettingsRepository.() -> Unit) {
        scope.launch { repo.block() }
    }

    fun writeRules(change: (HouseRules) -> HouseRules) {
        val next = change(s.rules)
        scope.launch { repo.setRules(next) }
    }

    if (confirmClear) {
        ClearHistoryDialog(
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                historyCleared = true
                // App scope: the delete must finish even if the player leaves the screen.
                container.appScope.launch { container.db.matches().clear() }
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.table)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(GridIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }

        // ------------------------------------------------------------------ table
        Section("Table") {
            LabeledSegment(
                title = "Theme",
                subtitle = null,
                options = ThemeMode.entries,
                selected = s.theme,
                label = ::themeLabel,
                onSelect = { v -> write { setTheme(v) } },
            )
            RowRule()
            SwitchRow(
                title = "Four-colour deck",
                subtitle = "Diamonds blue, clubs green — easier to tell apart",
                checked = s.fourColourDeck,
                onChange = { on -> write { setFourColour(on) } },
            )
            RowRule()
            LabeledSegment(
                title = "Card size",
                subtitle = "Large cards are easier to read; your hand overlaps a little more",
                options = CardSize.entries,
                selected = s.cardSize,
                label = { if (it == CardSize.LARGE) "Large" else "Normal" },
                onSelect = { v -> write { setCardSize(v) } },
            )
            RowRule()
            LabeledSegment(
                title = "Play speed",
                subtitle = "How quickly the computer players take their turns",
                options = Speed.entries,
                selected = s.speed,
                label = { it.label },
                onSelect = { v -> write { setSpeed(v) } },
            )
            RowRule()
            SettingTitle("Play direction", "Which way the turn passes around the table")
            Column(Modifier.selectableGroup()) {
                OptionRow(
                    title = "Anticlockwise",
                    subtitle = "The traditional direction",
                    selected = s.direction == Direction.ANTICLOCKWISE,
                    onClick = { write { setDirection(Direction.ANTICLOCKWISE) } },
                )
                OptionRow(
                    title = "Clockwise",
                    subtitle = null,
                    selected = s.direction == Direction.CLOCKWISE,
                    onClick = { write { setDirection(Direction.CLOCKWISE) } },
                )
            }
            RowRule()
            SwitchRow(
                title = "One-tap play",
                subtitle = "Play a card with a single tap instead of two",
                checked = s.oneTapPlay,
                onChange = { on -> write { setOneTapPlay(on) } },
            )
            RowRule()
            SwitchRow(
                title = "Reduce motion",
                subtitle = "Cards move into place with fewer animations",
                checked = s.reduceMotion,
                onChange = { on -> write { setReduceMotion(on) } },
            )
            RowRule()
            SwitchRow(
                title = "Haptics",
                subtitle = "A light tap when a card lands and when it's your turn",
                checked = s.haptics,
                onChange = { on -> write { setHaptics(on) } },
            )
        }

        // ------------------------------------------------------------------ computer players
        Section(
            "Computer players",
            footnote = "At every level the computer plays only from what it could see at a real table. It never looks at your cards.",
        ) {
            LabeledSegment(
                title = "Skill",
                subtitle = skillExplanation(s.skill),
                options = Skill.entries,
                selected = s.skill,
                label = ::skillLabel,
                onSelect = { v -> write { setSkill(v) } },
            )
        }

        // ------------------------------------------------------------------ house rules
        Section(
            "House rules",
            footnote = "Changes apply to new matches. A match in progress keeps the rules it started with.",
        ) {
            GameCatalog.all.forEachIndexed { index, game ->
                if (index > 0) RowRule()
                val expanded = expandedGame == game.id
                RulesHeader(
                    title = game.title,
                    summary = rulesSummary(game.id, s.rules),
                    expanded = expanded,
                    onClick = { expandedGame = if (expanded) null else game.id },
                )
                if (expanded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.panelRaised),
                    ) {
                        Hairline()
                        GameRules(gameId = game.id, rules = s.rules, onChange = { change -> writeRules(change) })
                    }
                }
            }
        }

        // ------------------------------------------------------------------ support
        Section("Support Tashlark") {
            val status = billingStatus
            val owned = status is BillingManager.Status.Owned || s.adsRemoved
            when {
                owned -> InfoRow(title = "Ads removed. Thank you.", subtitle = null)
                status is BillingManager.Status.Pending -> InfoRow(
                    title = "Purchase pending",
                    subtitle = "Ads go away as soon as Google Play confirms it",
                )
                status is BillingManager.Status.Ready -> ValueRow(
                    title = "Remove ads",
                    value = status.price,
                    subtitle = "One purchase. Every game stays free either way.",
                    onClick = { activity?.let { container.billing.buy(it) } },
                )
                else -> InfoRow(
                    title = "Remove ads",
                    subtitle = "Available when Google Play is reachable",
                    muted = true,
                )
            }
            if (!owned) {
                RowRule()
                ValueRow(title = "Restore purchase", value = null, onClick = { container.billing.restore() })
            }
        }

        // ------------------------------------------------------------------ data
        Section("Data") {
            ValueRow(
                title = "Clear match history",
                value = null,
                subtitle = if (historyCleared) {
                    "History cleared"
                } else {
                    "Removes your record and recent matches from this phone"
                },
                onClick = { confirmClear = true },
            )
        }

        // ------------------------------------------------------------------ privacy
        Section("Privacy") {
            ValueRow(title = "Privacy policy", value = null, onClick = onOpenPrivacy)
            if (privacyOptionsRequired) {
                RowRule()
                ValueRow(
                    title = "Ad privacy choices",
                    value = null,
                    subtitle = "Review how ads may use your data",
                    onClick = { activity?.let { container.ads.showPrivacyOptions(it) {} } },
                )
            }
            RowRule()
            ValueRow(title = "Open-source licences", value = null, onClick = onOpenLicenses)
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            Text("Tashlark ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            Text("No chips, no betting — just cards.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
    }
}

// ---------------------------------------------------------------------------- house rules

@Composable
private fun GameRules(gameId: String, rules: HouseRules, onChange: ((HouseRules) -> HouseRules) -> Unit) {
    when (gameId) {
        "callbreak" -> {
            SwitchRow(
                title = "Must beat the best card when following",
                subtitle = "If you can follow with a higher card than the best one on the trick, you must",
                checked = rules.cbMustBeat,
                onChange = { on -> onChange { it.copy(cbMustBeat = on) } },
            )
            RowRule()
            LabeledSegment(
                title = "Highest bid",
                subtitle = null,
                options = listOf(8, 13),
                selected = rules.cbMaxBid,
                label = { it.toString() },
                onSelect = { v -> onChange { it.copy(cbMaxBid = v) } },
            )
            RowRule()
            LabeledSegment(
                title = "Rounds",
                subtitle = null,
                options = listOf(5, 10),
                selected = rules.cbRounds,
                label = { it.toString() },
                onSelect = { v -> onChange { it.copy(cbRounds = v) } },
            )
        }
        "twentynine" -> {
            SwitchRow(
                title = "Pair (K+Q of trump) changes the target",
                subtitle = "Showing the king and queen of trump moves the bid target by 4",
                checked = rules.tnPair,
                onChange = { on -> onChange { it.copy(tnPair = on) } },
            )
            RowRule()
            SwitchRow(
                title = "Double and redouble",
                subtitle = "The defenders may double the bid, and the bidders may redouble",
                checked = rules.tnDoubling,
                onChange = { on -> onChange { it.copy(tnDoubling = on) } },
            )
            RowRule()
            SwitchRow(
                title = "Last trick earns a point (game of 29)",
                subtitle = "Without it the cards carry 28 points in all",
                checked = rules.tnLastTrickPoint,
                onChange = { on -> onChange { it.copy(tnLastTrickPoint = on) } },
            )
        }
        "courtpiece" -> {
            LabeledSegment(
                title = "Points to win",
                subtitle = null,
                options = listOf(5, 10, 15, 20),
                selected = rules.cpTarget,
                label = { it.toString() },
                onSelect = { v -> onChange { it.copy(cpTarget = v) } },
            )
        }
        "mendikot" -> {
            SettingTitle("Trump method", null)
            Column(Modifier.selectableGroup()) {
                OptionRow(
                    title = "Hidden trump (band hukum)",
                    subtitle = "A card is set aside face down and turned over when someone can't follow suit",
                    selected = !rules.mkCutHukum,
                    onClick = { onChange { it.copy(mkCutHukum = false) } },
                )
                OptionRow(
                    title = "Cut trump (cut hukum)",
                    subtitle = "The first suit played by someone who can't follow becomes trump",
                    selected = rules.mkCutHukum,
                    onClick = { onChange { it.copy(mkCutHukum = true) } },
                )
            }
            RowRule()
            SwitchRow(
                title = "Bonus points for mendikot and whitewash",
                subtitle = "Taking all four tens, or all 13 tricks, counts for more than one point",
                checked = rules.mkWeighted,
                onChange = { on -> onChange { it.copy(mkWeighted = on) } },
            )
            RowRule()
            LabeledSegment(
                title = "Points to win",
                subtitle = null,
                options = listOf(3, 5, 7),
                selected = rules.mkTarget,
                label = { it.toString() },
                onSelect = { v -> onChange { it.copy(mkTarget = v) } },
            )
        }
    }
}

private fun rulesSummary(gameId: String, r: HouseRules): String = when (gameId) {
    "callbreak" -> buildString {
        append("Highest bid ").append(r.cbMaxBid)
        append(" · ").append(r.cbRounds).append(" rounds")
        if (!r.cbMustBeat) append(" · free follow")
    }
    "twentynine" -> listOfNotNull(
        if (r.tnPair) "Pair" else null,
        if (r.tnDoubling) "Doubling" else null,
        if (r.tnLastTrickPoint) "Last-trick point" else null,
    ).joinToString(" · ").ifEmpty { "Plain bidding, no extras" }
    "courtpiece" -> "First to ${r.cpTarget} points"
    "mendikot" -> buildString {
        append(if (r.mkCutHukum) "Cut trump" else "Hidden trump")
        append(" · first to ").append(r.mkTarget)
        if (r.mkWeighted) append(" · bonuses")
    }
    else -> ""
}

@Composable
private fun RulesHeader(title: String, summary: String, expanded: Boolean, onClick: () -> Unit) {
    val c = Tash.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                onClickLabel = if (expanded) "Hide $title rules" else "Show $title rules",
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            GridIcons.Chevron,
            contentDescription = null,
            tint = c.inkMuted,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 90f else 0f),
        )
    }
}

// ---------------------------------------------------------------------------- building blocks

/** Spaced-caps label on the cloth, then a flat panel band bordered by hairlines. */
@Composable
private fun Section(title: String, footnote: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Tash.colors
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(
            title,
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.panel),
        ) {
            Hairline()
            content()
            Hairline()
        }
        if (footnote != null) {
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = c.inkMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
    }
}

/** Inset rule between rows inside a panel band. */
@Composable
private fun RowRule() {
    Hairline(Modifier.padding(start = 20.dp))
}

@Composable
private fun SettingTitle(title: String, subtitle: String?) {
    val c = Tash.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
}

/** Title (and optional explanation) stacked above a segmented choice, so long labels never squeeze it. */
@Composable
private fun <T> LabeledSegment(
    title: String,
    subtitle: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val c = Tash.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        Spacer(Modifier.height(10.dp))
        SegmentedChoice(options = options, selected = selected, label = label, onSelect = onSelect)
    }
}

/**
 * One row of equal segments; the chosen one is filled with haldi. Each segment is at least
 * 48dp tall and its label wraps rather than clipping at large font sizes.
 */
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tash.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, c.inkMuted, shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(c.inkMuted),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .background(if (isSelected) c.haldi else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) c.onHaldi else c.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A radio-style row: title, optional explanation, and a round mark that fills with haldi when chosen. */
@Composable
private fun OptionRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Tash.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(24.dp)
                .then(
                    if (selected) {
                        Modifier.background(c.haldi, CircleShape)
                    } else {
                        Modifier.border(1.5.dp, c.inkMuted, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(GridIcons.Check, contentDescription = null, tint = c.onHaldi, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** A non-interactive row in the same rhythm as [ValueRow]. */
@Composable
private fun InfoRow(title: String, subtitle: String?, muted: Boolean = false) {
    val c = Tash.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (muted) c.inkMuted else c.ink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
}

@Composable
private fun ClearHistoryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = Tash.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = c.madder),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Clear history", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = c.ink),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Keep it", style = MaterialTheme.typography.labelLarge) }
        },
        title = { Text("Clear match history?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Your wins, streaks and recent matches will be removed from this phone. This can't be undone. A match in progress is kept.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = c.panel,
        titleContentColor = c.ink,
        textContentColor = c.ink,
        tonalElevation = 0.dp,
    )
}

// ---------------------------------------------------------------------------- labels

internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

internal fun skillLabel(skill: Skill): String = when (skill) {
    Skill.EASY -> "Easy"
    Skill.MEDIUM -> "Medium"
    Skill.HARD -> "Hard"
}

internal fun skillExplanation(skill: Skill): String = when (skill) {
    Skill.EASY -> "Plays by simple habits and lets the odd card slip. Good for learning a game."
    Skill.MEDIUM -> "Thinks through a couple of dozen possible deals before each card."
    Skill.HARD -> "Weighs many more possible deals and plays the percentages closely."
}
