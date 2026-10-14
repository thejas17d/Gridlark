package com.gridlark.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gridlark.app.AppContainer
import com.gridlark.app.data.GameStats
import com.gridlark.app.data.PlayEntity
import com.gridlark.app.data.StreakInfo
import com.gridlark.app.data.computeGameStats
import com.gridlark.app.data.computeStreaks
import com.gridlark.app.ui.components.GameGlyph
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.components.SectionLabel
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.util.formatDuration
import com.gridlark.app.util.weekdayLetter
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val HEATMAP_WEEKS = 12

@Composable
fun StatsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    // Nullable so the empty state doesn't flash before Room's first emission.
    val flow: Flow<List<PlayEntity>?> = remember(container) { container.db.plays().observeSolved() }
    val solved by flow.collectAsStateWithLifecycle(initialValue = null)
    val today = remember { LocalDate.now() }

    val plays = solved
    val streaks = remember(plays, today) { plays?.let { computeStreaks(it, today.toEpochDay()) } }
    val games = remember(plays) { plays?.let { computeGameStats(it) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "title") {
            Text(
                "Stats",
                style = MaterialTheme.typography.headlineLarge,
                color = Grid.colors.ink,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp)
                    .semantics { heading() },
            )
        }
        if (plays == null || streaks == null || games == null) return@LazyColumn
        if (plays.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Solve a puzzle and your numbers will start here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Grid.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            return@LazyColumn
        }
        item(key = "streak") {
            Hairline()
            StreakBlock(streaks)
            Hairline()
        }
        item(key = "heatmap") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                SectionLabel("Last $HEATMAP_WEEKS weeks")
                Spacer(Modifier.height(12.dp))
                ActivityHeatmap(playedDays = streaks.playedDays, today = today)
            }
            Hairline()
        }
        item(key = "games-label") {
            SectionLabel("By game", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp))
        }
        items(games, key = { it.game.key }) { stats ->
            GameStatsRow(stats)
            Hairline(Modifier.padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun StreakBlock(streaks: StreakInfo) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        BigNumber(value = streaks.current, label = "Current streak", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        BigNumber(value = streaks.longest, label = "Longest streak", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BigNumber(value: Int, label: String, modifier: Modifier = Modifier) {
    val c = Grid.colors
    val unit = if (value == 1) "day" else "days"
    Column(
        modifier.clearAndSetSemantics { contentDescription = "$label: $value $unit" },
    ) {
        Row {
            Text(
                value.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum, lnum"),
                color = c.ink,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkMuted,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    }
}

/**
 * 7 rows (Mon..Sun) by [HEATMAP_WEEKS] columns, the last column being the current week.
 * Days after today are left blank.
 */
@Composable
private fun ActivityHeatmap(playedDays: Set<Long>, today: LocalDate) {
    val c = Grid.colors
    val density = LocalDensity.current
    val start = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((HEATMAP_WEEKS - 1).toLong())
    }
    val startDay = start.toEpochDay()
    val todayDay = today.toEpochDay()
    val shownDays = (todayDay - startDay + 1).toInt()
    val playedCount = playedDays.count { it in startDay..todayDay }
    val summary = "Played $playedCount of the last $shownDays days"

    // Weekday letters on Mon, Wed and Fri only, so each label has two rows of room at large font scales.
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = c.inkMuted)
    val labelRows = listOf(0, 2, 4)
    val labels: List<TextLayoutResult> = remember(measurer, labelStyle, start, density) {
        labelRows.map { row -> measurer.measure(AnnotatedString(start.plusDays(row.toLong()).weekdayLetter()), labelStyle) }
    }
    val labelWidthPx = labels.maxOf { it.size.width }

    Column(Modifier.clearAndSetSemantics { contentDescription = summary }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 3.dp
            val labelGap = 8.dp
            val labelWidth = with(density) { labelWidthPx.toDp() }
            val fit = (maxWidth - labelWidth - labelGap - gap * (HEATMAP_WEEKS - 1)) / HEATMAP_WEEKS
            val cell = fit.coerceIn(14.dp, 18.dp)
            val width = labelWidth + labelGap + cell * HEATMAP_WEEKS + gap * (HEATMAP_WEEKS - 1)
            val height = cell * 7 + gap * 6

            Canvas(Modifier.size(width, height)) {
                val cellPx = cell.toPx()
                val gapPx = gap.toPx()
                val gridLeft = labelWidthPx + labelGap.toPx()
                val radius = CornerRadius(cellPx * 0.2f)
                val outline = 2.dp.toPx()

                labels.forEachIndexed { i, layout ->
                    val row = labelRows[i]
                    val centerY = row * (cellPx + gapPx) + cellPx / 2
                    drawText(
                        layout,
                        topLeft = Offset(labelWidthPx - layout.size.width.toFloat(), centerY - layout.size.height / 2f),
                    )
                }

                for (col in 0 until HEATMAP_WEEKS) {
                    for (row in 0 until 7) {
                        val day = startDay + col * 7 + row
                        if (day > todayDay) continue
                        val topLeft = Offset(gridLeft + col * (cellPx + gapPx), row * (cellPx + gapPx))
                        val played = day in playedDays
                        drawRoundRect(
                            color = if (played) c.ink else c.sunken,
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = radius,
                        )
                        if (day == todayDay) {
                            drawRoundRect(
                                color = c.accent,
                                topLeft = Offset(topLeft.x - outline / 2, topLeft.y - outline / 2),
                                size = Size(cellPx + outline, cellPx + outline),
                                cornerRadius = CornerRadius(radius.x + outline / 2),
                                style = Stroke(width = outline),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(summary, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
}

@Composable
private fun GameStatsRow(stats: GameStats) {
    val c = Grid.colors
    val detail = if (stats.solved == 0) {
        "Not played yet"
    } else {
        buildString {
            append("Solved ").append(stats.solved)
            stats.bestMs?.let { append(" · Best ").append(formatDuration(it)) }
            stats.medianMs?.let { append(" · Median ").append(formatDuration(it)) }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(stats.game, size = 28.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stats.game.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = if (stats.solved == 0) c.inkFaint else c.inkMuted,
            )
        }
    }
}
