package com.gridlark.app.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridlark.app.game.TrailState
import com.gridlark.app.ui.theme.Grid
import com.gridlark.app.ui.theme.UiFamily

@Composable
fun TrailBoardView(
    state: TrailState,
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
    val measurer = rememberTextMeasurer()
    val onPath = remember(state) { state.path.toHashSet() }
    Box(modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize().gridGestures(n, cb)) {
            val cell = size.width / n
            val thin = 1.dp.toPx()
            drawRect(c.surface)
            for (k in 1 until n) {
                drawLine(c.rule, Offset(k * cell, 0f), Offset(k * cell, size.height), thin)
                drawLine(c.rule, Offset(0f, k * cell), Offset(size.width, k * cell), thin)
            }
            fun center(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)

            // The player's line.
            val lineColor = if (state.solved) c.success else c.accent
            if (state.path.size >= 2) {
                val p = Path()
                state.path.forEachIndexed { k, i ->
                    val o = center(i)
                    if (k == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                }
                drawPath(p, lineColor, style = Stroke(width = cell * 0.42f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            state.head?.let { drawCircle(lineColor, cell * 0.27f, center(it)) }

            // Walls.
            val wall = 4.dp.toPx()
            for (i in 0 until n * n) {
                val r = i / n; val col = i % n
                if (col < n - 1 && state.puzzle.wallRight[i]) {
                    val x = (col + 1) * cell
                    drawLine(c.ink, Offset(x, r * cell), Offset(x, (r + 1) * cell), wall, StrokeCap.Round)
                }
                if (r < n - 1 && state.puzzle.wallDown[i]) {
                    val y = (r + 1) * cell
                    drawLine(c.ink, Offset(col * cell, y), Offset((col + 1) * cell, y), wall, StrokeCap.Round)
                }
            }

            // Numbered stops.
            val style = TextStyle(
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (cell * 0.34f / density / fontScale).sp,
                color = c.paper,
            )
            state.puzzle.checkpoints.forEachIndexed { k, i ->
                val o = center(i)
                drawCircle(c.ink, cell * 0.31f, o)
                if (i in onPath) drawCircle(lineColor, cell * 0.31f, o, style = Stroke(2.5.dp.toPx()))
                val layout = measurer.measure((k + 1).toString(), style)
                drawText(layout, topLeft = Offset(o.x - layout.size.width / 2f, o.y - layout.size.height / 2f))
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
                val num = state.puzzle.numberAt[i]
                val label = if (num != 0) "number $num" else "square"
                val step = state.path.indexOf(i)
                val where = when {
                    step < 0 -> "not on the line"
                    step == state.path.size - 1 -> "end of your line"
                    else -> "step ${step + 1} of your line"
                }
                "Row ${i / n + 1}, column ${i % n + 1}, $label, $where"
            },
            onActivate = { callbacks.onTap(it) },
        )
    }
}
