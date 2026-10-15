package com.tashlark.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tashlark.app.AppContainer
import com.tashlark.app.data.GameCatalog
import com.tashlark.app.data.Speed
import com.tashlark.app.data.UserSettings
import com.tashlark.app.ui.cards.SuitGlyph
import com.tashlark.app.ui.components.Hairline
import com.tashlark.app.ui.components.PaperButton
import com.tashlark.app.ui.settings.SegmentedChoice
import com.tashlark.app.ui.settings.skillExplanation
import com.tashlark.app.ui.settings.skillLabel
import com.tashlark.app.ui.theme.Tash
import com.tashlark.app.ui.theme.suitColor
import com.tashlark.engine.Skill
import com.tashlark.engine.Suit
import kotlinx.coroutines.launch

/** First launch only. One scrollable screen, no carousel, no account. */
@Composable
fun WelcomeScreen(container: AppContainer, onDone: () -> Unit) {
    val c = Tash.colors
    val scope = rememberCoroutineScope()
    val repo = container.settings
    val s by repo.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    var leaving by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.table)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            "TASHLARK",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
            color = c.ink,
        )
        Spacer(Modifier.height(36.dp))
        Text(
            "Your favourite card games, against thoughtful computer players.",
            style = MaterialTheme.typography.displaySmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Call Break, 29, Court Piece and Mendikot. Works offline. Your rules, your pace — and the computer never sees your cards.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.inkMuted,
        )

        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.panel, RoundedCornerShape(8.dp)),
        ) {
            val suits = listOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS)
            GameCatalog.all.forEachIndexed { index, game ->
                if (index > 0) Hairline(Modifier.padding(start = 16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SuitTile(suits[index % suits.size], fourColour = s.fourColourDeck)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(game.tagline, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "How do you play?",
            style = MaterialTheme.typography.headlineSmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "You can change both any time in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkMuted,
        )

        Spacer(Modifier.height(20.dp))
        Text("Pace", style = MaterialTheme.typography.bodyLarge, color = c.ink)
        Text("How quickly the computer players take their turns", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        Spacer(Modifier.height(10.dp))
        SegmentedChoice(
            options = Speed.entries,
            selected = s.speed,
            label = { it.label },
            onSelect = { v -> scope.launch { repo.setSpeed(v) } },
        )

        Spacer(Modifier.height(20.dp))
        Text("Computer players", style = MaterialTheme.typography.bodyLarge, color = c.ink)
        Text(skillExplanation(s.skill), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        Spacer(Modifier.height(10.dp))
        SegmentedChoice(
            options = Skill.entries,
            selected = s.skill,
            label = { skillLabel(it) },
            onSelect = { v -> scope.launch { repo.setSkill(v) } },
        )

        Spacer(Modifier.height(36.dp))
        PaperButton(
            "Deal me in",
            onClick = {
                if (!leaving) {
                    leaving = true
                    scope.launch {
                        repo.setWelcomeDone()
                        onDone()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A small paper card with one suit drawn on it; suit inks are tuned for the card face, not the cloth. */
@Composable
private fun SuitTile(suit: Suit, fourColour: Boolean, size: Dp = 22.dp) {
    val c = Tash.colors
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .size(width = 34.dp, height = 44.dp)
            .background(c.cardFace, shape)
            .border(1.dp, c.cardEdge, shape),
        contentAlignment = Alignment.Center,
    ) {
        SuitGlyph(suit, size = size, color = suitColor(suit, fourColour))
    }
}
