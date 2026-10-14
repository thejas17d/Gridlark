package com.gridlark.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gridlark.app.BuildConfig
import com.gridlark.app.ui.components.BareIconButton
import com.gridlark.app.ui.components.GridIcons
import com.gridlark.app.ui.components.Hairline
import com.gridlark.app.ui.theme.Grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Placeholder in the bundled privacy policy, swapped for BuildConfig.SUPPORT_EMAIL when that is set. */
private const val SUPPORT_EMAIL_PLACEHOLDER = "[support email — set SUPPORT_EMAIL]"

/**
 * Shows a plain-text document bundled in assets (privacy policy, licences).
 * Format: lines starting with "# " are headings, blank lines separate paragraphs, and
 * hard-wrapped lines inside a paragraph are joined so the text reflows to the screen.
 */
@Composable
fun DocumentScreen(title: String, assetName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Grid.colors
    val context = LocalContext.current
    val text by produceState<String?>(null, assetName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse { "This document couldn't be opened." }
        }
    }
    val blocks = remember(text) { text?.let { parseDocument(fillPlaceholders(it)) } }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(GridIcons.Back, "Back", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Hairline()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        ) {
            if (blocks != null) {
                SelectionContainer {
                    Column {
                        blocks.forEachIndexed { index, block ->
                            when (block) {
                                is DocBlock.Heading -> Text(
                                    block.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = c.ink,
                                    modifier = Modifier
                                        .padding(top = if (index == 0) 8.dp else 14.dp, bottom = 6.dp)
                                        .semantics { heading() },
                                )
                                is DocBlock.Paragraph -> Text(
                                    block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.ink,
                                )
                                DocBlock.Gap -> Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface DocBlock {
    data class Heading(val text: String) : DocBlock
    data class Paragraph(val text: String) : DocBlock
    data object Gap : DocBlock
}

private val ListItemStart = Regex("""^(- |\* |\d+[.)] )""")

private fun parseDocument(raw: String): List<DocBlock> {
    val out = mutableListOf<DocBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotEmpty()) {
            out += DocBlock.Paragraph(paragraph.toString())
            paragraph.setLength(0)
        }
    }
    for (rawLine in raw.lines()) {
        val line = rawLine.trim()
        when {
            line.isEmpty() -> {
                flush()
                if (out.isNotEmpty() && out.last() !is DocBlock.Gap) out += DocBlock.Gap
            }
            line.startsWith("# ") -> {
                flush()
                out += DocBlock.Heading(line.removePrefix("# ").trim())
            }
            ListItemStart.containsMatchIn(line) -> {
                // Each list item starts its own line.
                flush()
                paragraph.append(line)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flush()
    while (out.isNotEmpty() && out.last() is DocBlock.Gap) out.removeAt(out.lastIndex)
    return out
}

private fun fillPlaceholders(text: String): String {
    val email = BuildConfig.SUPPORT_EMAIL
    return if (email.isNotBlank()) text.replace(SUPPORT_EMAIL_PLACEHOLDER, email) else text
}
