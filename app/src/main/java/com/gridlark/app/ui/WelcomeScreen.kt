package com.gridlark.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gridlark.app.AppContainer
import com.gridlark.app.data.GameType
import com.gridlark.app.ui.components.GameGlyph
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.components.PaperButton
import com.gridlark.app.ui.theme.Grid
import kotlinx.coroutines.launch

/** First launch only. One screen, no carousel, no account. */
@Composable
fun WelcomeScreen(container: AppContainer, onDone: () -> Unit) {
    val c = Grid.colors
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("GRIDLARK", style = MaterialTheme.typography.labelMedium, color = c.ink)
        Spacer(Modifier.height(40.dp))
        Text(
            "Four small puzzles, every day.",
            style = MaterialTheme.typography.displayMedium,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Each one has a single answer you can reach by logic alone. No guessing, no account, and it all works offline.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(28.dp))
        Hairline(color = c.ink)
        GameType.entries.forEach { game ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GameGlyph(game, size = 32.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(game.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text(game.tagline, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
            }
            Hairline()
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "New puzzles arrive at midnight. They start gentle on Monday and get trickier towards the weekend.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(28.dp))
        PaperButton(
            "Begin",
            onClick = { scope.launch { container.settings.setWelcomeDone(); onDone() } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
