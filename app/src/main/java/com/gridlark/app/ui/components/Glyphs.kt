package com.gridlark.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gridlark.app.data.GameType
import com.gridlark.app.ui.theme.Grid

/** Crown silhouette in a unit box; scaled by callers. */
fun crownPath(left: Float, top: Float, size: Float): Path = Path().apply {
    fun p(x: Float, y: Float) = Offset(left + x * size, top + y * size)
    val a = p(0.14f, 0.78f); moveTo(a.x, a.y)
    p(0.10f, 0.30f).let { lineTo(it.x, it.y) }
    p(0.33f, 0.50f).let { lineTo(it.x, it.y) }
    p(0.50f, 0.20f).let { lineTo(it.x, it.y) }
    p(0.67f, 0.50f).let { lineTo(it.x, it.y) }
    p(0.90f, 0.30f).let { lineTo(it.x, it.y) }
    p(0.86f, 0.78f).let { lineTo(it.x, it.y) }
    close()
}

/** Small per-game emblem drawn from the game's own grid. */
@Composable
fun GameGlyph(game: GameType, modifier: Modifier = Modifier, size: Dp = 28.dp, solved: Boolean = false) {
    val c = Grid.colors
    val ink = c.ink
    val accent = c.accent
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = 1.6.dp.toPx()
        val frame = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (solved) {
            drawRoundRect(accent, cornerRadius = CornerRadius(s * 0.18f))
        } else {
            drawRoundRect(ink, topLeft = Offset(stroke / 2, stroke / 2), size = Size(s - stroke, s - stroke),
                cornerRadius = CornerRadius(s * 0.18f), style = frame)
        }
        val fg = if (solved) c.onAccent else ink
        when (game) {
            GameType.CROWNS -> drawPath(crownPath(s * 0.2f, s * 0.18f, s * 0.6f), fg)
            GameType.DUO -> {
                drawCircle(if (solved) fg else accent, radius = s * 0.14f, center = Offset(s * 0.33f, s * 0.5f))
                moon(fg, Offset(s * 0.67f, s * 0.5f), s * 0.15f)
            }
            GameType.TRAIL -> {
                val path = Path().apply {
                    moveTo(s * 0.3f, s * 0.3f); lineTo(s * 0.7f, s * 0.3f); lineTo(s * 0.7f, s * 0.5f)
                    lineTo(s * 0.3f, s * 0.5f); lineTo(s * 0.3f, s * 0.7f); lineTo(s * 0.7f, s * 0.7f)
                }
                drawPath(path, fg, style = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(fg, radius = s * 0.08f, center = Offset(s * 0.3f, s * 0.3f))
            }
            GameType.SUDOKU -> {
                val w = stroke * 0.8f
                for (k in 1..2) {
                    val t = s * (0.2f + 0.2f * k)
                    drawLine(fg, Offset(t, s * 0.2f), Offset(t, s * 0.8f), w)
                    drawLine(fg, Offset(s * 0.2f, t), Offset(s * 0.8f, t), w)
                }
                drawRect(if (solved) fg else accent, topLeft = Offset(s * 0.4f + w, s * 0.4f + w), size = Size(s * 0.2f - 2 * w, s * 0.2f - 2 * w))
            }
        }
    }
}

/** Crescent: a disc with an offset disc cut out, so it works on any background. */
fun moonPath(center: Offset, radius: Float): Path {
    val outer = Path().apply { addOval(Rect(center, radius)) }
    val cut = Path().apply { addOval(Rect(Offset(center.x + radius * 0.48f, center.y - radius * 0.28f), radius * 0.86f)) }
    return Path().apply { op(outer, cut, PathOperation.Difference) }
}

fun DrawScope.moon(color: Color, center: Offset, radius: Float) {
    drawPath(moonPath(center, radius), color)
}
