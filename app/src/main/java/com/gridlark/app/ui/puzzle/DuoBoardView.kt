package com.gridlark.app.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gridlark.app.game.DuoState
import com.gridlark.app.ui.components.moon
import com.gridlark.app.ui.theme.Grid
import com.gridlark.engine.duo.DuoPuzzle

@Composable
fun DuoBoardView(
    state: DuoState,
    highlightConflicts: Boolean,
    hintCell: Int?,
    hintKey: Any?,
    reduceMotion: Boolean,
    callbacks: GridGestureCallbacks,
    modifier: Modifier = Modifier,
) {
    val c = Grid.colors
    val n = state.size
    val cb = rememberUpdatedState(callbacks)
    val pulse by rememberPulse(hintKey, reduceMotion)
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize().gridGestures(n, cb)) {
            val cell = size.width / n
            val thin = 1.dp.toPx()
            val conflicts = state.conflicts
            drawRect(c.surface)
            for (i in 0 until n * n) {
                val r = i / n; val col = i % n
                val tl = Offset(col * cell, r * cell)
                if (state.isGiven(i)) drawRect(c.sunken, tl, Size(cell, cell))
                if (highlightConflicts && conflicts[i]) drawRect(c.error.copy(alpha = 0.14f), tl, Size(cell, cell))
            }
            for (k in 1 until n) {
                drawLine(c.rule, Offset(k * cell, 0f), Offset(k * cell, size.height), thin)
                drawLine(c.rule, Offset(0f, k * cell), Offset(size.width, k * cell), thin)
            }
            // Symbols.
            for (i in 0 until n * n) {
                val center = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
                val radius = cell * 0.27f
                when (state.values[i]) {
                    DuoPuzzle.SUN -> {
                        drawCircle(c.accent, radius, center)
                        drawCircle(c.accentText.copy(alpha = 0.5f), radius, center, style = Stroke(1.dp.toPx()))
                    }
                    DuoPuzzle.MOON -> moon(c.ink, center, radius)
                }
                if (highlightConflicts && conflicts[i]) {
                    val y = center.y + cell * 0.38f
                    drawLine(c.error, Offset(center.x - cell * 0.22f, y), Offset(center.x + cell * 0.22f, y), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            // Relation signs sit on the shared border.
            for (e in state.puzzle.edges) {
                val ar = e.a / n; val ac = e.a % n
                val horizontal = e.b == e.a + 1
                val mid = if (horizontal) Offset((ac + 1) * cell, (ar + 0.5f) * cell) else Offset((ac + 0.5f) * cell, (ar + 1) * cell)
                val rr = cell * 0.15f
                drawCircle(c.paper, rr, mid)
                drawCircle(c.ink, rr, mid, style = Stroke(1.25.dp.toPx()))
                val w = 1.6.dp.toPx()
                val g = rr * 0.5f
                if (e.same) {
                    drawLine(c.ink, Offset(mid.x - g, mid.y - g * 0.45f), Offset(mid.x + g, mid.y - g * 0.45f), w, StrokeCap.Round)
                    drawLine(c.ink, Offset(mid.x - g, mid.y + g * 0.45f), Offset(mid.x + g, mid.y + g * 0.45f), w, StrokeCap.Round)
                } else {
                    drawLine(c.ink, Offset(mid.x - g * 0.75f, mid.y - g * 0.75f), Offset(mid.x + g * 0.75f, mid.y + g * 0.75f), w, StrokeCap.Round)
                    drawLine(c.ink, Offset(mid.x + g * 0.75f, mid.y - g * 0.75f), Offset(mid.x - g * 0.75f, mid.y + g * 0.75f), w, StrokeCap.Round)
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
                val v = when (state.values[i]) { DuoPuzzle.SUN -> "sun"; DuoPuzzle.MOON -> "moon"; else -> "blank" }
                val fixed = if (state.isGiven(i)) ", fixed" else ""
                val bad = if (state.conflicts[i]) ", breaks a rule" else ""
                val signs = state.puzzle.edges.filter { it.a == i || it.b == i }.joinToString("") { e ->
                    val other = if (e.a == i) e.b else e.a
                    val dir = when (other - i) { 1 -> "right"; -1 -> "left"; n -> "below"; else -> "above" }
                    ", ${if (e.same) "same as" else "opposite to"} the cell $dir"
                }
                "Row ${i / n + 1}, column ${i % n + 1}, $v$fixed$bad$signs"
            },
            onActivate = { callbacks.onTap(it) },
        )
    }
}
