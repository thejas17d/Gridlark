package com.gridlark.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gridlark.app.AppContainer
import com.gridlark.app.data.Daily
import com.gridlark.app.data.GameType
import com.gridlark.app.data.PlayEntity
import com.gridlark.app.data.label
import com.gridlark.app.ui.components.GameGlyph
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.util.shortLabel
import java.time.LocalDate

/** How far back the archive list goes. */
private const val MAX_DAYS_BACK = 120L

private enum class CellState { NOT_STARTED, IN_PROGRESS, SOLVED }

@Composable
fun ArchiveScreen(
    container: AppContainer,
    onOpen: (game: GameType, epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Grid.colors
    val today = remember { LocalDate.now() }
    val to = today.toEpochDay() - 1
    val from = maxOf(Daily.EPOCH.toEpochDay(), today.toEpochDay() - MAX_DAYS_BACK)
    val dates: List<LocalDate> = remember(from, to) {
        if (to < from) emptyList() else (to downTo from).map { LocalDate.ofEpochDay(it) }
    }

    val flow = remember(container, from, to) { container.db.plays().observeDailies(from, to) }
    val plays by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val states: Map<String, CellState> = remember(plays) {
        plays.associate { p ->
            p.id to when {
                p.solved -> CellState.SOLVED
                p.stateJson != null || p.elapsedMs > 0 -> CellState.IN_PROGRESS
                else -> CellState.NOT_STARTED
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp)) {
                Text(
                    "Archive",
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (dates.isEmpty()) "The archive fills up from tomorrow." else "Every past daily, free to play.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.inkMuted,
                )
            }
            if (dates.isNotEmpty()) Hairline()
        }
        items(dates, key = { it.toEpochDay() }) { date ->
            ArchiveRow(
                date = date,
                stateOf = { game -> states[PlayEntity.dailyId(game, date.toEpochDay())] ?: CellState.NOT_STARTED },
                onOpen = onOpen,
            )
            Hairline(Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun ArchiveRow(
    date: LocalDate,
    stateOf: (GameType) -> CellState,
    onOpen: (game: GameType, epochDay: Long) -> Unit,
) {
    val c = Grid.colors
    val dateLabel = date.shortLabel()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(dateLabel, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(
                "No. ${Daily.issueNumber(date)} · ${Daily.difficultyFor(date).label()}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = c.inkMuted,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            for (game in GameType.entries) {
                val state = stateOf(game)
                val stateText = when (state) {
                    CellState.SOLVED -> "solved"
                    CellState.IN_PROGRESS -> "in progress"
                    CellState.NOT_STARTED -> "not started"
                }
                GlyphButton(
                    game = game,
                    state = state,
                    description = "${game.title}, $dateLabel, $stateText",
                    onClick = { onOpen(game, date.toEpochDay()) },
                )
            }
        }
    }
}

@Composable
private fun GlyphButton(game: GameType, state: CellState, description: String, onClick: () -> Unit) {
    val c = Grid.colors
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open", onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        GameGlyph(game, size = 28.dp, solved = state == CellState.SOLVED)
        if (state == CellState.IN_PROGRESS) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(5.dp)
                    .background(c.accent, CircleShape),
            )
        }
    }
}
