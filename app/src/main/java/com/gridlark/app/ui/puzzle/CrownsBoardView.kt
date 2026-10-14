package com.gridlark.app.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.gridlark.app.game.CrownsState
import com.gridlark.app.ui.components.crownPath
import com.gridlark.app.ui.theme.Grid

@Composable
fun CrownsBoardView(
    state: CrownsState,
    highlightConflicts: Boolean,
    patternAssist: Boolean,
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
    // Boards with more regions than the colour-blind-safe set always get patterns.
    val patterns = patternAssist || n > 9
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize().gridGestures(n, cb)) {
            val cell = size.width / n
            val thin = 1.dp.toPx()
            val thick = 2.5.dp.toPx()
            val regions = state.puzzle.regions
            val conflicts = state.conflicts

            for (i in 0 until n * n) {
                val r = i / n; val col = i % n
                val tl = Offset(col * cell, r * cell)
                drawRect(c.regions[regions[i] % c.regions.size], tl, Size(cell, cell))
                if (patterns) drawPattern(regions[i], tl, cell, c.ink.copy(alpha = 0.22f))
            }
            // Thin cell lines.
            val lineColor = c.ink.copy(alpha = 0.16f)
            for (k in 1 until n) {
                drawLine(lineColor, Offset(k * cell, 0f), Offset(k * cell, size.height), thin)
                drawLine(lineColor, Offset(0f, k * cell), Offset(size.width, k * cell), thin)
            }
            // Thick region borders.
            for (i in 0 until n * n) {
                val r = i / n; val col = i % n
                if (col < n - 1 && regions[i] != regions[i + 1]) {
                    val x = (col + 1) * cell
                    drawLine(c.ink, Offset(x, r * cell - thick / 2), Offset(x, (r + 1) * cell + thick / 2), thick, StrokeCap.Square)
                }
                if (r < n - 1 && regions[i] != regions[i + n]) {
                    val y = (r + 1) * cell
                    drawLine(c.ink, Offset(col * cell - thick / 2, y), Offset((col + 1) * cell + thick / 2, y), thick, StrokeCap.Square)
                }
            }
            // Marks.
            for (i in 0 until n * n) {
                val r = i / n; val col = i % n
                val left = col * cell; val top = r * cell
                when (state.marks[i]) {
                    CrownsState.CROSS -> {
                        val m = cell * 0.39f
                        val w = 1.75.dp.toPx()
                        val ink = c.ink.copy(alpha = 0.62f)
                        drawLine(ink, Offset(left + m, top + m), Offset(left + cell - m, top + cell - m), w, StrokeCap.Round)
                        drawLine(ink, Offset(left + cell - m, top + m), Offset(left + m, top + cell - m), w, StrokeCap.Round)
                    }
                    CrownsState.CROWN -> {
                        val bad = highlightConflicts && conflicts[i]
                        val s = cell * 0.62f
                        drawPath(crownPath(left + (cell - s) / 2, top + (cell - s) / 2, s), if (bad) c.error else c.ink)
                        if (bad) {
                            val y = top + cell * 0.86f
                            drawLine(c.error, Offset(left + cell * 0.25f, y), Offset(left + cell * 0.75f, y), 2.dp.toPx(), StrokeCap.Round)
                        }
                    }
                }
            }
            if (hintCell != null && pulse > 0f) {
                val r = hintCell / n; val col = hintCell % n
                val inset = 3.dp.toPx()
                drawRoundRect(
                    c.accent.copy(alpha = pulse),
                    topLeft = Offset(col * cell + inset, r * cell + inset),
                    size = Size(cell - 2 * inset, cell - 2 * inset),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(3.dp.toPx()),
                )
            }
            drawRoundRect(c.ink, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(thick))
        }
        AccessibleGrid(
            n = n,
            describe = { i ->
                val mark = when (state.marks[i]) {
                    CrownsState.CROWN -> if (state.conflicts[i]) "crown, breaks a rule" else "crown"
                    CrownsState.CROSS -> "marked empty"
                    else -> "blank"
                }
                "Row ${i / n + 1}, column ${i % n + 1}, ${RegionNames[state.puzzle.regions[i] % RegionNames.size]} region, $mark"
            },
            onActivate = { callbacks.onTap(it) },
        )
    }
}

/** Subtle per-region texture so regions can be told apart without colour. */
private fun DrawScope.drawPattern(region: Int, tl: Offset, s: Float, color: Color) {
    val w = 1.25.dp.toPx()
    clipRect(tl.x, tl.y, tl.x + s, tl.y + s) {
        when (region % 12) {
            0 -> for (a in 1..2) for (b in 1..2) drawCircle(color, s * 0.05f, Offset(tl.x + s * a / 3, tl.y + s * b / 3))
            1 -> for (k in -1..1) drawLine(color, Offset(tl.x + s * (k * 0.5f), tl.y + s), Offset(tl.x + s * (k * 0.5f + 1), tl.y), w)
            2 -> for (k in 1..3) drawLine(color, Offset(tl.x, tl.y + s * k / 4), Offset(tl.x + s, tl.y + s * k / 4), w)
            3 -> {
                drawLine(color, Offset(tl.x, tl.y), Offset(tl.x + s, tl.y + s), w)
                drawLine(color, Offset(tl.x + s, tl.y), Offset(tl.x, tl.y + s), w)
            }
            4 -> for (k in 1..3) drawLine(color, Offset(tl.x + s * k / 4, tl.y), Offset(tl.x + s * k / 4, tl.y + s), w)
            5 -> drawCircle(color, s * 0.3f, Offset(tl.x + s / 2, tl.y + s / 2), style = Stroke(w))
            6 -> drawPath(Path().apply {
                moveTo(tl.x + s * 0.5f, tl.y + s * 0.2f); lineTo(tl.x + s * 0.8f, tl.y + s * 0.78f); lineTo(tl.x + s * 0.2f, tl.y + s * 0.78f); close()
            }, color, style = Stroke(w))
            7 -> {
                drawRect(color, Offset(tl.x, tl.y), Size(s / 2, s / 2))
                drawRect(color, Offset(tl.x + s / 2, tl.y + s / 2), Size(s / 2, s / 2))
            }
            8 -> for (k in -1..1) drawLine(color, Offset(tl.x + s * (k * 0.5f), tl.y), Offset(tl.x + s * (k * 0.5f + 1), tl.y + s), w)
            9 -> {
                drawLine(color, Offset(tl.x + s / 2, tl.y + s * 0.2f), Offset(tl.x + s / 2, tl.y + s * 0.8f), w)
                drawLine(color, Offset(tl.x + s * 0.2f, tl.y + s / 2), Offset(tl.x + s * 0.8f, tl.y + s / 2), w)
            }
            10 -> drawPath(Path().apply {
                moveTo(tl.x, tl.y + s * 0.5f)
                cubicTo(tl.x + s * 0.25f, tl.y + s * 0.3f, tl.x + s * 0.25f, tl.y + s * 0.7f, tl.x + s * 0.5f, tl.y + s * 0.5f)
                cubicTo(tl.x + s * 0.75f, tl.y + s * 0.3f, tl.x + s * 0.75f, tl.y + s * 0.7f, tl.x + s, tl.y + s * 0.5f)
            }, color, style = Stroke(w))
            else -> drawRect(color, Offset(tl.x + s * 0.3f, tl.y + s * 0.3f), Size(s * 0.4f, s * 0.4f), style = Stroke(w))
        }
    }
}
