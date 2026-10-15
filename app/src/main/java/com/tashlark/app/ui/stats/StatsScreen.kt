package com.tashlark.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tashlark.app.AppContainer
import com.tashlark.app.data.GameCatalog
import com.tashlark.app.data.GameRecord
import com.tashlark.app.data.MatchRecord
import com.tashlark.app.data.Totals
import com.tashlark.app.data.computeRecords
import com.tashlark.app.data.recent
import com.tashlark.app.data.totals
import com.tashlark.app.ui.components.Hairline
import com.tashlark.app.ui.components.SectionLabel
import com.tashlark.app.ui.theme.Tash
import com.tashlark.engine.Skill
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val RECENT_LIMIT = 20

@Composable
fun StatsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val c = Tash.colors
    // Nullable so the empty state doesn't flash before Room's first emission.
    val flow: Flow<List<MatchRecord>?> = remember(container) { container.db.matches().observeAll() }
    val records by flow.collectAsStateWithLifecycle(initialValue = null)

    val list = records
    val overall = remember(list) { list?.let { totals(it) } }
    val games = remember(list) { list?.let { computeRecords(it) } }
    val latest = remember(list) { list?.let { recent(it, RECENT_LIMIT) } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(c.table),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "title") {
            Text(
                "Your record",
                style = MaterialTheme.typography.headlineLarge,
                color = c.ink,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp)
                    .semantics { heading() },
            )
        }
        if (list == null || overall == null || games == null || latest == null) return@LazyColumn
        if (list.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Finish a match and your record starts here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.inkMuted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "totals") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.panel),
            ) {
                Hairline()
                TotalsBlock(overall)
                Hairline()
            }
        }

        item(key = "games-label") { BandLabel("By game") }
        itemsIndexed(games, key = { _, g -> "game-${g.gameId}" }) { index, game ->
            BandRow(first = index == 0, last = index == games.lastIndex) { GameRow(game) }
        }

        item(key = "recent-label") { BandLabel("Recent matches") }
        itemsIndexed(latest, key = { _, r -> "match-${r.id}" }) { index, record ->
            BandRow(first = index == 0, last = index == latest.lastIndex) { MatchRow(record) }
        }
    }
}

@Composable
private fun BandLabel(text: String) {
    SectionLabel(
        text,
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** One row of a flat panel band: full rules above the first and below the last, inset rules between. */
@Composable
private fun BandRow(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Tash.colors.panel),
    ) {
        if (first) Hairline()
        content()
        if (last) Hairline() else Hairline(Modifier.padding(start = 20.dp))
    }
}

@Composable
private fun TotalsBlock(t: Totals) {
    // Three big numbers side by side; stacked when the font is scaled up so none of them wraps.
    val stacked = LocalDensity.current.fontScale > 1.3f
    val played = t.played.toString()
    val won = t.won.toString()
    val rate = t.winPercent?.let { "$it%" } ?: "–"
    if (stacked) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            BigNumber(played, "played", "${t.played} played")
            Spacer(Modifier.height(12.dp))
            BigNumber(won, "won", "${t.won} won")
            Spacer(Modifier.height(12.dp))
            BigNumber(rate, "win rate", "Win rate $rate")
        }
    } else {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            BigNumber(played, "played", "${t.played} played", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            BigNumber(won, "won", "${t.won} won", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            BigNumber(rate, "win rate", "Win rate $rate", Modifier.weight(1f))
        }
    }
}

@Composable
private fun BigNumber(value: String, label: String, spoken: String, modifier: Modifier = Modifier) {
    val c = Tash.colors
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Text(
            value,
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "lnum, tnum"),
            color = c.ink,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    }
}

@Composable
private fun GameRow(g: GameRecord) {
    val c = Tash.colors
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(g.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
        val numbers = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
        val last = g.last
        if (g.played == 0 || last == null) {
            Text("Not played yet", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        } else {
            Text(
                "Played ${g.played} · Won ${g.won} · ${g.winPercent ?: 0}%",
                style = numbers,
                color = c.ink,
            )
            Text(
                "Win streak ${g.currentStreak} · Best ${g.bestStreak}",
                style = numbers,
                color = c.inkMuted,
            )
            Text(
                "Last: ${resultWord(last)} ${last.yourScore} on ${formatDay(last.endedAt)}",
                style = numbers,
                color = c.inkMuted,
            )
        }
    }
}

@Composable
private fun MatchRow(r: MatchRecord) {
    val c = Tash.colors
    val title = GameCatalog.all.firstOrNull { it.id == r.gameId }?.title ?: r.gameId
    val meta = buildList {
        add(formatDay(r.endedAt))
        add(skillName(r.skill))
        if (r.assisted) add("assisted")
    }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                resultWord(r),
                style = MaterialTheme.typography.titleSmall,
                color = if (r.won) c.neem else c.inkMuted,
                textAlign = TextAlign.End,
            )
            Text(
                r.yourScore,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = c.inkMuted,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun resultWord(r: MatchRecord): String = if (r.won) "Won" else "Lost"

private fun skillName(stored: String): String = when (Skill.entries.firstOrNull { it.name == stored }) {
    Skill.EASY -> "Easy"
    Skill.MEDIUM -> "Medium"
    Skill.HARD -> "Hard"
    null -> stored.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** "12 Oct", or "12 Oct 2025" for an earlier year. Formatters are made per call so the locale stays current. */
private fun formatDay(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val pattern = if (day.year == LocalDate.now(zone).year) "d MMM" else "d MMM yyyy"
    return day.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
