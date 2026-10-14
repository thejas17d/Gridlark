package com.gridlark.app.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridlark.app.game.SudokuState
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.ui.theme.MonoFamily
import com.gridlark.app.ui.theme.UiFamily

@Composable
fun SudokuBoardView(
    state: SudokuState,
    selected: Int,
    highlightConflicts: Boolean,
    hintCell: Int?,
    hintKey: Any?,
    reduceMotion: Boolean,
    callbacks: GridGestureCallbacks,
    modifier: Modifier = Modifier,
) {
    val c = Grid.colors
    val n = state.size
    val p = state.puzzle
    val cb = rememberUpdatedState(callbacks)
    val pulse by rememberPulse(hintKey, reduceMotion)
    val measurer = rememberTextMeasurer()
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize().gridGestures(n, cb)) {
            val cell = size.width / n
            val selValue = if (selected >= 0) state.values[selected] else 0
            val conflicts = state.conflicts
            drawRect(c.surface)
            for (i in 0 until n * n) {
                val tl = Offset((i % n) * cell, (i / n) * cell)
                val related = selected >= 0 && i != selected &&
                    (i / n == selected / n || i % n == selected % n || p.boxOf(i) == p.boxOf(selected))
                val bg = when {
                    i == selected -> c.accent.copy(alpha = 0.45f)
                    selValue != 0 && state.values[i] == selValue -> c.accent.copy(alpha = 0.2f)
                    related -> c.sunken
                    else -> null
                }
                if (bg != null) drawRect(bg, tl, Size(cell, cell))
            }
            val thin = 1.dp.toPx()
            val thick = 2.dp.toPx()
            for (k in 1 until n) {
                val vThick = k % p.boxCols == 0
                val hThick = k % p.boxRows == 0
                drawLine(if (vThick) c.ink else c.rule, Offset(k * cell, 0f), Offset(k * cell, size.height), if (vThick) thick else thin)
                drawLine(if (hThick) c.ink else c.rule, Offset(0f, k * cell), Offset(size.width, k * cell), if (hThick) thick else thin)
            }
            val unit = cell / density / fontScale
            val digitGiven = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = (unit * 0.56f).sp, color = c.ink)
            val digitUser = digitGiven.copy(fontWeight = FontWeight.Medium, color = c.accentText)
            val digitBad = digitGiven.copy(fontWeight = FontWeight.Medium, color = c.error)
            val noteStyle = TextStyle(fontFamily = MonoFamily, fontSize = (unit * 0.22f).sp, color = c.inkMuted)
            val noteCols = p.boxCols
            val noteRows = (n + noteCols - 1) / noteCols
            for (i in 0 until n * n) {
                val left = (i % n) * cell; val top = (i / n) * cell
                val v = state.values[i]
                if (v != 0) {
                    val style = when {
                        state.isGiven(i) -> digitGiven
                        highlightConflicts && conflicts[i] -> digitBad
                        else -> digitUser
                    }
                    val layout = measurer.measure(v.toString(), style)
                    drawText(layout, topLeft = Offset(left + (cell - layout.size.width) / 2f, top + (cell - layout.size.height) / 2f))
                } else if (state.notes[i] != 0) {
                    for (d in 1..n) {
                        if (state.notes[i] and (1 shl d) == 0) continue
                        val k = d - 1
                        val sx = cell / noteCols; val sy = cell / noteRows
                        val layout = measurer.measure(d.toString(), noteStyle)
                        drawText(layout, topLeft = Offset(
                            left + (k % noteCols) * sx + (sx - layout.size.width) / 2f,
                            top + (k / noteCols) * sy + (sy - layout.size.height) / 2f,
                        ))
                    }
                }
            }
            if (hintCell != null && pulse > 0f) {
                val inset = 3.dp.toPx()
                drawRoundRect(
                    c.accentText.copy(alpha = pulse),
                    topLeft = Offset((hintCell % n) * cell + inset, (hintCell / n) * cell + inset),
                    size = Size(cell - 2 * inset, cell - 2 * inset),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(3.dp.toPx()),
                )
            }
            drawRoundRect(c.ink, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.5.dp.toPx()))
        }
        AccessibleGrid(
            n = n,
            describe = { i ->
                val v = state.values[i]
                val content = when {
                    v != 0 && state.isGiven(i) -> "$v, given"
                    v != 0 -> if (state.conflicts[i]) "$v, clashes" else "$v"
                    state.notes[i] != 0 -> "notes " + (1..n).filter { state.notes[i] and (1 shl it) != 0 }.joinToString(" ")
                    else -> "empty"
                }
                val sel = if (i == selected) ", selected" else ""
                "Row ${i / n + 1}, column ${i % n + 1}, $content$sel"
            },
            onActivate = { callbacks.onTap(it) },
        )
    }
}

/** Digit keys. A digit that is fully placed dims, but stays usable (players may want to clear). */
@Composable
fun NumberPad(size: Int, counts: (Int) -> Int, notesMode: Boolean, onDigit: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Grid.colors
    val tap by rememberUpdatedState(onDigit)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 1..size) {
            val done = counts(d) >= size
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (notesMode) c.sunken else c.surface)
                    .clickable(role = Role.Button) { tap(d) }
                    .semantics { contentDescription = if (notesMode) "Note $d" else "Digit $d" }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold),
                    color = when {
                        done -> c.inkFaint
                        notesMode -> c.inkMuted
                        else -> c.ink
                    },
                )
            }
        }
    }
}
