package com.tashlark.app.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tashlark.app.ui.theme.DisplayFamily
import com.tashlark.app.ui.theme.IndexFamily
import com.tashlark.app.ui.theme.Tash
import com.tashlark.app.ui.theme.TashColors
import com.tashlark.app.ui.theme.suitColor
import com.tashlark.engine.Card
import com.tashlark.engine.Suit

/** Card proportions: 1 : 1.4. */
const val CARD_ASPECT = 1.4f

/** A suit drawn as a filled path inside [box] (crisp at any size, no font dependency). */
fun suitPath(suit: Suit, box: Rect): Path {
    val w = box.width; val h = box.height; val x = box.left; val y = box.top
    return Path().apply {
        when (suit) {
            Suit.HEARTS -> {
                moveTo(x + w / 2, y + h * 0.95f)
                cubicTo(x + w * 0.05f, y + h * 0.62f, x - w * 0.02f, y + h * 0.18f, x + w * 0.27f, y + h * 0.08f)
                cubicTo(x + w * 0.40f, y + h * 0.04f, x + w * 0.48f, y + h * 0.14f, x + w / 2, y + h * 0.24f)
                cubicTo(x + w * 0.52f, y + h * 0.14f, x + w * 0.60f, y + h * 0.04f, x + w * 0.73f, y + h * 0.08f)
                cubicTo(x + w * 1.02f, y + h * 0.18f, x + w * 0.95f, y + h * 0.62f, x + w / 2, y + h * 0.95f)
                close()
            }
            Suit.DIAMONDS -> {
                moveTo(x + w / 2, y)
                quadraticTo(x + w * 0.70f, y + h * 0.28f, x + w * 0.95f, y + h / 2)
                quadraticTo(x + w * 0.70f, y + h * 0.72f, x + w / 2, y + h)
                quadraticTo(x + w * 0.30f, y + h * 0.72f, x + w * 0.05f, y + h / 2)
                quadraticTo(x + w * 0.30f, y + h * 0.28f, x + w / 2, y)
                close()
            }
            Suit.SPADES -> {
                moveTo(x + w / 2, y + h * 0.02f)
                cubicTo(x + w * 0.62f, y + h * 0.20f, x + w * 1.0f, y + h * 0.38f, x + w * 0.95f, y + h * 0.60f)
                cubicTo(x + w * 0.90f, y + h * 0.80f, x + w * 0.62f, y + h * 0.80f, x + w * 0.54f, y + h * 0.68f)
                lineTo(x + w * 0.64f, y + h * 0.98f)
                lineTo(x + w * 0.36f, y + h * 0.98f)
                lineTo(x + w * 0.46f, y + h * 0.68f)
                cubicTo(x + w * 0.38f, y + h * 0.80f, x + w * 0.10f, y + h * 0.80f, x + w * 0.05f, y + h * 0.60f)
                cubicTo(x + w * 0.0f, y + h * 0.38f, x + w * 0.38f, y + h * 0.20f, x + w / 2, y + h * 0.02f)
                close()
            }
            Suit.CLUBS -> {
                val r = w * 0.23f
                addOval(Rect(Offset(x + w / 2, y + h * 0.25f), r))
                addOval(Rect(Offset(x + w * 0.26f, y + h * 0.56f), r))
                addOval(Rect(Offset(x + w * 0.74f, y + h * 0.56f), r))
                moveTo(x + w * 0.46f, y + h * 0.45f)
                lineTo(x + w * 0.54f, y + h * 0.45f)
                lineTo(x + w * 0.64f, y + h * 0.98f)
                lineTo(x + w * 0.36f, y + h * 0.98f)
                close()
            }
        }
    }
}

fun DrawScope.drawSuit(suit: Suit, box: Rect, color: Color) = drawPath(suitPath(suit, box), color)

/** Pre-measured corner labels, rebuilt only when the card size or theme changes. */
private class FaceText(val ranks: Map<String, TextLayoutResult>, val courts: Map<String, TextLayoutResult>)

private val RANK_LABELS = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")

/**
 * One playing card, face up or down. [width] sets the size; height follows the 1:1.4 aspect.
 * Drawn with cached text so a full hand stays cheap to redraw on low-end phones.
 */
@Composable
fun PlayingCard(
    card: Card?,
    faceUp: Boolean,
    width: Dp,
    fourColour: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    highlight: Color? = null,
) {
    val c = Tash.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val text = remember(widthPx, density) {
        FaceTextCache.get(widthPx, density.density, density.fontScale) { buildFaceText(measurer, widthPx, density.density, density.fontScale) }
    }
    Canvas(modifier.size(width, width * CARD_ASPECT)) {
        val radius = CornerRadius(6.dp.toPx())
        // Soft drop shadow: a single offset rounded rect, no blur (cheap).
        drawRoundRect(Color.Black.copy(alpha = 0.12f), topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = radius)
        if (!faceUp || card == null) {
            drawBack(c, radius)
        } else {
            drawFace(card, c, fourColour, radius, text)
        }
        if (dimmed) drawRoundRect(c.table.copy(alpha = 0.55f), cornerRadius = radius)
        if (highlight != null) {
            drawRoundRect(highlight, cornerRadius = radius, style = Stroke(2.5.dp.toPx()))
        }
    }
}

/** Measured layouts are immutable, so cards of one size can share them (a hand redraws cheaply). */
private object FaceTextCache {
    private val map = HashMap<Triple<Float, Float, Float>, FaceText>()
    @Synchronized
    fun get(widthPx: Float, density: Float, fontScale: Float, build: () -> FaceText): FaceText {
        val k = Triple(widthPx, density, fontScale)
        map[k]?.let { return it }
        if (map.size > 24) map.clear()
        return build().also { map[k] = it }
    }
}

private fun buildFaceText(measurer: androidx.compose.ui.text.TextMeasurer, widthPx: Float, density: Float, fontScale: Float): FaceText {
    run {
        val unit = widthPx / density / fontScale
        val rankStyle = TextStyle(fontFamily = IndexFamily, fontWeight = FontWeight.Bold, fontSize = (unit * 0.30f).sp, color = Color.Black)
        val courtStyle = TextStyle(fontFamily = DisplayFamily, fontSize = (unit * 0.42f).sp, color = Color.Black)
        return FaceText(
            ranks = RANK_LABELS.associateWith { measurer.measure(it, rankStyle) },
            courts = listOf("J", "Q", "K").associateWith { measurer.measure(it, courtStyle) },
        )
    }
}

private fun DrawScope.drawFace(card: Card, c: TashColors, fourColour: Boolean, radius: CornerRadius, text: FaceText) {
    val w = size.width; val h = size.height
    drawRoundRect(c.cardFace, cornerRadius = radius)
    drawRoundRect(c.cardEdge, cornerRadius = radius, style = Stroke(1.dp.toPx()))
    val ink = suitColor(card.suit, fourColour)
    // Corner index: rank over a small pip (top-left), mirrored bottom-right.
    val rank = text.ranks.getValue(card.rankLabel)
    val pad = w * 0.07f
    val pip = w * 0.20f
    fun corner() {
        drawText(rank, color = ink, topLeft = Offset(pad + (pip - rank.size.width) / 2f, pad * 0.6f))
        drawSuit(card.suit, Rect(Offset(pad, pad * 0.6f + rank.size.height * 0.92f), Size(pip, pip)), ink)
    }
    corner()
    rotate(180f) { corner() }
    val cx = w / 2; val cy = h / 2
    if (card.rank in 11..13) {
        // Court card: a round frame with the letter and a small pip, ganjifa-like restraint.
        val r = w * 0.30f
        drawCircle(ink.copy(alpha = 0.10f), r, Offset(cx, cy))
        drawCircle(ink, r, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
        val letter = text.courts.getValue(card.rankLabel)
        drawText(letter, color = ink, topLeft = Offset(cx - letter.size.width / 2f, cy - letter.size.height / 2f - r * 0.18f))
        val small = w * 0.13f
        drawSuit(card.suit, Rect(Offset(cx - small / 2, cy + r * 0.38f), Size(small, small)), ink)
    } else {
        // Number cards and aces: one large pip, calm and legible.
        val big = if (card.rank == 14) w * 0.50f else w * 0.40f
        drawSuit(card.suit, Rect(Offset(cx - big / 2, cy - big / 2), Size(big, big)), ink)
    }
}

private fun DrawScope.drawBack(c: TashColors, radius: CornerRadius) {
    val w = size.width; val h = size.height
    drawRoundRect(c.cardBack, cornerRadius = radius)
    val inset = w * 0.07f
    drawRoundRect(
        c.cardBackMotif.copy(alpha = 0.85f), topLeft = Offset(inset, inset), size = Size(w - 2 * inset, h - 2 * inset),
        cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()),
    )
    // Round ganjifa medallion: rings and eight petals.
    val center = Offset(w / 2, h / 2)
    val r = w * 0.32f
    drawCircle(c.cardBackMotif, r, center, style = Stroke(1.5.dp.toPx()))
    drawCircle(c.cardBackMotif.copy(alpha = 0.9f), r * 0.55f, center, style = Stroke(1.dp.toPx()))
    for (i in 0 until 8) {
        rotate(i * 45f, center) {
            val petal = Path().apply {
                moveTo(center.x, center.y - r * 0.55f)
                quadraticTo(center.x + r * 0.16f, center.y - r * 0.78f, center.x, center.y - r * 0.98f)
                quadraticTo(center.x - r * 0.16f, center.y - r * 0.78f, center.x, center.y - r * 0.55f)
                close()
            }
            drawPath(petal, c.cardBackMotif)
        }
    }
    drawCircle(c.cardBackMotif, r * 0.14f, center)
}

/** Small suit glyph as a composable (for badges and buttons). */
@Composable
fun SuitGlyph(suit: Suit, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawSuit(suit, Rect(Offset.Zero, this.size), color) }
}
