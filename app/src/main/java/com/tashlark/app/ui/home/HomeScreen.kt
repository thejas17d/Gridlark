package com.tashlark.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tashlark.app.AppContainer
import com.tashlark.app.data.GameCatalog
import com.tashlark.app.data.GameInfo
import com.tashlark.app.data.SavedMatch
import com.tashlark.app.ui.cards.SuitGlyph
import com.tashlark.app.ui.components.BareIconButton
import com.tashlark.app.ui.components.GridIcons
import com.tashlark.app.ui.components.Hairline
import com.tashlark.app.ui.components.SectionLabel
import com.tashlark.app.ui.theme.Tash
import com.tashlark.app.ui.theme.suitColor
import com.tashlark.engine.Suit
import kotlinx.coroutines.launch

private val GameSuit = mapOf(
    "callbreak" to Suit.SPADES,
    "twentynine" to Suit.HEARTS,
    "courtpiece" to Suit.CLUBS,
    "mendikot" to Suit.DIAMONDS,
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (gameId: String, resume: Boolean) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tash.colors
    var saved by remember { mutableStateOf<List<SavedMatch>>(emptyList()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Refresh saved matches every time Home comes back into view.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch { saved = container.matches.all().sortedByDescending { it.updatedAt } }
        onPauseOrDispose { job.cancel() }
    }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val fourColour = settings?.fourColourDeck ?: false
    var confirmNew by remember { mutableStateOf<GameInfo?>(null) }

    LazyColumn(modifier.fillMaxSize().background(c.table)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TASHLARK", style = MaterialTheme.typography.labelLarge, color = c.ink, modifier = Modifier.weight(1f))
                BareIconButton(GridIcons.Rules, "Your record", onStats)
                BareIconButton(GridIcons.Settings, "Settings", onSettings)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Deal a hand", style = MaterialTheme.typography.displaySmall, color = c.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(
                    "Four classic games against thoughtful computer players. Works offline.",
                    style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                )
            }
        }
        if (saved.isNotEmpty()) {
            item { SectionLabel("Continue", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)) }
            items(saved, key = { "c-" + it.gameId }) { m ->
                val info = runCatching { GameCatalog.info(m.gameId) }.getOrNull() ?: return@items
                ContinueRow(info, m, fourColour) { onPlay(m.gameId, true) }
            }
        }
        item { SectionLabel("Games", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)) }
        items(GameCatalog.all, key = { it.id }) { info ->
            GameRow(info, fourColour) {
                if (saved.any { it.gameId == info.id }) confirmNew = info else onPlay(info.id, false)
            }
            Hairline(Modifier.padding(start = 92.dp, end = 20.dp))
        }
        item {
            Text(
                "No chips, no betting, no account. The computer players never see your cards.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
    }

    confirmNew?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmNew = null },
            containerColor = c.panel,
            title = { Text("Start a new ${info.title} match?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Your ${info.title} match in progress will be replaced.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                androidx.compose.material3.TextButton({ confirmNew = null; onPlay(info.id, false) }) {
                    Text("Start new", color = c.madder)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton({ confirmNew = null; onPlay(info.id, true) }) {
                    Text("Continue match", color = c.ink)
                }
            },
        )
    }
}

/** A round block-print style emblem: concentric rings around a suit. */
@Composable
private fun Roundel(suit: Suit, fourColour: Boolean, modifier: Modifier = Modifier) {
    val c = Tash.colors
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val r = size.minDimension / 2
            drawCircle(c.cardFace, r)
            drawCircle(c.madder, r - 3.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val p = Offset(center.x + (r - 8.dp.toPx()) * kotlin.math.cos(a).toFloat(), center.y + (r - 8.dp.toPx()) * kotlin.math.sin(a).toFloat())
                drawCircle(c.cardBack, 1.6.dp.toPx(), p)
            }
        }
        SuitGlyph(suit, 22.dp, suitColor(suit, fourColour))
    }
}

@Composable
private fun GameRow(info: GameInfo, fourColour: Boolean, onClick: () -> Unit) {
    val c = Tash.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable(role = Role.Button, onClickLabel = "Start a new match", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Roundel(GameSuit[info.id] ?: Suit.SPADES, fourColour)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(info.title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Text(info.tagline, style = MaterialTheme.typography.bodyMedium, color = c.ink)
            Text("${info.players} · ${info.length}", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        }
        Icon(GridIcons.Chevron, null, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ContinueRow(info: GameInfo, m: SavedMatch, fourColour: Boolean, onClick: () -> Unit) {
    val c = Tash.colors
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(c.panel, RoundedCornerShape(12.dp))
            .border(1.dp, c.haldi, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Continue ${info.title}, hand ${m.hands.size + 1}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(c.haldi, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Continue ${info.title}", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(
                "Hand ${m.hands.size + 1} · " + m.skill.lowercase().replaceFirstChar { it.uppercase() } + " opponents",
                style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
            )
        }
        SuitGlyph(GameSuit[info.id] ?: Suit.SPADES, 18.dp, suitColor(GameSuit[info.id] ?: Suit.SPADES, fourColour))
        Spacer(Modifier.width(6.dp))
        Icon(GridIcons.Chevron, null, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}
