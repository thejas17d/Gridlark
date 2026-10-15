package com.gridlark.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gridlark.app.AppContainer
import com.gridlark.app.data.Daily
import com.gridlark.app.data.GameType
import com.gridlark.app.data.PlayEntity
import com.gridlark.app.data.UserSettings
import com.gridlark.app.data.computeStreaks
import com.gridlark.app.data.estimateMinutes
import com.gridlark.app.data.label
import com.gridlark.app.reminders.Reminders
import com.gridlark.app.ui.components.BareIconButton
import com.gridlark.app.ui.components.GameGlyph
import com.gridlark.app.ui.components.GridIcons
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.components.PaperButton
import com.gridlark.app.ui.components.SectionLabel
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.util.formatDuration
import com.gridlark.app.util.longLabel
import com.gridlark.app.util.weekdayLetter
import com.gridlark.engine.Difficulty
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun HomeScreen(
    container: AppContainer,
    today: LocalDate,
    onOpenDaily: (GameType, Long) -> Unit,
    onOpenPractice: (GameType, Difficulty, Long?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Grid.colors
    val day = today.toEpochDay()
    val dao = container.db.plays()
    val todayFlow = remember(day) { dao.observeDailies(day, day) }
    val todays by todayFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val solvedFlow = remember { dao.observeSolved() }
    val solved by solvedFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val openFlow = remember { dao.observeOpenPractice() }
    val openPractice by openFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val streak = remember(solved, day) { computeStreaks(solved, day) }
    val difficulty = Daily.difficultyFor(today)
    var pickFor by remember { mutableStateOf<GameType?>(null) }

    LazyColumn(modifier.fillMaxSize().background(c.paper)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("GRIDLARK", style = MaterialTheme.typography.labelMedium, color = c.ink, modifier = Modifier.weight(1f))
                BareIconButton(GridIcons.Settings, "Settings", onOpenSettings)
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(today.longLabel(), style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(
                    "No. ${Daily.issueNumber(today)} · ${difficulty.label()} day · ${GameType.entries.size} puzzles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkMuted,
                )
                Spacer(Modifier.height(14.dp))
                StreakLine(streak.current, streak.playedDays, today)
                Spacer(Modifier.height(18.dp))
            }
            Hairline(Modifier.padding(horizontal = 20.dp), color = c.ink)
        }

        if (!settings.reminderPromptDone && solved.isNotEmpty()) {
            item { ReminderPrompt(container) }
        }

        item {
            SectionLabel("Today", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp))
        }
        items(GameType.entries, key = { "d-" + it.key }) { game ->
            val play = todays.firstOrNull { it.game == game.key }
            DailyRow(game, difficulty, play) { onOpenDaily(game, day) }
            Hairline(Modifier.padding(start = 72.dp, end = 20.dp))
        }

        item {
            SectionLabel("Practice", Modifier.padding(start = 20.dp, top = 28.dp, bottom = 4.dp))
            Text(
                "Endless puzzles at the level you choose.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        items(GameType.entries, key = { "p-" + it.key }) { game ->
            val open = openPractice.firstOrNull { it.game == game.key }
            PracticeRow(game, open) { pickFor = game }
            Hairline(Modifier.padding(start = 72.dp, end = 20.dp))
        }
        item {
            Text(
                "Every puzzle has one answer, reachable by logic alone. Everything works offline.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkFaint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            )
        }
    }

    pickFor?.let { game ->
        val open = openPractice.firstOrNull { it.game == game.key }
        DifficultyDialog(
            game = game,
            openDifficulty = open?.let { runCatching { Difficulty.valueOf(it.difficulty) }.getOrNull() },
            onContinue = {
                pickFor = null
                if (open != null) {
                    onOpenPractice(game, runCatching { Difficulty.valueOf(open.difficulty) }.getOrDefault(Difficulty.MEDIUM), open.seed)
                }
            },
            onPick = { d -> pickFor = null; onOpenPractice(game, d, null) },
            onDismiss = { pickFor = null },
        )
    }
}

@Composable
private fun StreakLine(current: Int, played: Set<Long>, today: LocalDate) {
    val c = Grid.colors
    val monday = today.with(DayOfWeek.MONDAY)
    val label = when (current) {
        0 -> "Start a streak today"
        1 -> "1 day streak"
        else -> "$current day streak"
    }
    val playedThisWeek = (0..6).count { monday.plusDays(it.toLong()).toEpochDay() in played }
    // Label above the week strip so it never collides at large font sizes.
    Column(Modifier.clearAndSetSemantics { contentDescription = "$label. Played $playedThisWeek of 7 days this week." }) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val done = d.toEpochDay() in played
            val isToday = d == today
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 22.dp).padding(end = 6.dp)) {
                Text(d.weekdayLetter(), style = MaterialTheme.typography.labelSmall, color = if (isToday) c.ink else c.inkFaint)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (done) c.ink else if (isToday) c.accent else c.rule, CircleShape),
                )
            }
        }
        }
    }
}

@Composable
private fun DailyRow(game: GameType, difficulty: Difficulty, play: PlayEntity?, onClick: () -> Unit) {
    val c = Grid.colors
    val solved = play?.solved == true
    val started = play != null && !solved && play.stateJson != null
    val status = when {
        solved -> "Solved in ${formatDuration(play!!.elapsedMs)}" + if ((play.hintsUsed) > 0) " · ${play.hintsUsed} hint${if (play.hintsUsed > 1) "s" else ""}" else ""
        started -> "In progress · ${formatDuration(play!!.elapsedMs)}"
        else -> "${difficulty.label()} · about ${estimateMinutes(game, difficulty)} min"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = if (solved) "Review" else "Play", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(game, size = 36.dp, solved = solved)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (solved) c.success else c.inkMuted)
        }
        if (!solved) {
            Text(if (started) "Resume" else "Play", style = MaterialTheme.typography.labelLarge, color = c.ink)
            Icon(GridIcons.Chevron, null, tint = c.ink, modifier = Modifier.size(20.dp))
        } else {
            Icon(GridIcons.Check, null, tint = c.success, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PracticeRow(game: GameType, open: PlayEntity?, onClick: () -> Unit) {
    val c = Grid.colors
    val sub = if (open != null) {
        val d = runCatching { Difficulty.valueOf(open.difficulty) }.getOrDefault(Difficulty.MEDIUM)
        "Continue your ${d.label().lowercase()} puzzle"
    } else game.tagline
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(game, size = 36.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(game.title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Icon(GridIcons.Chevron, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DifficultyDialog(
    game: GameType,
    openDifficulty: Difficulty?,
    onContinue: () -> Unit,
    onPick: (Difficulty) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Grid.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(game.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (openDifficulty != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button, onClick = onContinue)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Continue", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = c.ink)
                            Text("Your ${openDifficulty.label().lowercase()} puzzle in progress", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                        Icon(GridIcons.Chevron, null, tint = c.ink, modifier = Modifier.size(20.dp))
                    }
                    Hairline()
                    Text("Or start a new one", style = MaterialTheme.typography.labelSmall, color = c.inkMuted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                Difficulty.entries.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) { onPick(d) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.label(), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                            Text("About ${estimateMinutes(game, d)} min", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                        Icon(GridIcons.Chevron, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = c.inkMuted) } },
    )
}

@Composable
private fun ReminderPrompt(container: AppContainer) {
    val c = Grid.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val minutes = 8 * 60
    // App scope: saving hides this prompt, which would cancel a screen-level scope mid-way.
    val app = context.applicationContext
    fun enable() = container.appScope.launch {
        container.settings.setReminder(true, minutes)
        Reminders.schedule(app, true, minutes)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable() else scope.launch { container.settings.setReminderPromptDone() }
    }
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .background(c.sunken, RoundedCornerShape(8.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GridIcons.Bell, null, tint = c.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("A quiet nudge each morning?", style = MaterialTheme.typography.titleSmall, color = c.ink)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "One notification at 8:00 while today's puzzles are unsolved. Change the time or turn it off in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperButton("Remind me", {
                if (Build.VERSION.SDK_INT >= 33 && !Reminders.hasPermission(context)) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else enable()
            })
            PaperButton("No thanks", { scope.launch { container.settings.setReminderPromptDone() } }, primary = false)
        }
    }
}
