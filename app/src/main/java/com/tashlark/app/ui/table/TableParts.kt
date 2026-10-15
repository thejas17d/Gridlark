package com.tashlark.app.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tashlark.app.data.Direction
import com.tashlark.app.ui.cards.CARD_ASPECT
import com.tashlark.app.ui.cards.PlayingCard
import com.tashlark.app.ui.cards.SuitGlyph
import com.tashlark.app.ui.theme.Tash
import com.tashlark.app.ui.theme.suitColor
import com.tashlark.engine.Card
import com.tashlark.engine.Suit
import com.tashlark.engine.Trick
import com.tashlark.engine.seatName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where a seat sits on screen. */
enum class Side { BOTTOM, RIGHT, TOP, LEFT }

fun sideOf(seat: Int, direction: Direction): Side = when (seat) {
    0 -> Side.BOTTOM
    2 -> Side.TOP
    1 -> if (direction == Direction.ANTICLOCKWISE) Side.RIGHT else Side.LEFT
    else -> if (direction == Direction.ANTICLOCKWISE) Side.LEFT else Side.RIGHT
}

/** Unit vector from the table centre towards a side. */
private fun Side.vector(): Offset = when (this) {
    Side.BOTTOM -> Offset(0f, 1f)
    Side.TOP -> Offset(0f, -1f)
    Side.LEFT -> Offset(-1f, 0f)
    Side.RIGHT -> Offset(1f, 0f)
}

/** Name, status and card count for one computer player. Ring marks whose turn it is. */
@Composable
fun SeatChip(seat: Int, status: String, cards: Int, active: Boolean, partner: Boolean, modifier: Modifier = Modifier) {
    val c = Tash.colors
    val ring by animateDpAsState(if (active) 3.dp else 0.dp, tween(150), label = "ring")
    Column(
        modifier
            .widthInSafe()
            .semantics(mergeDescendants = true) {
                contentDescription = "${seatName(seat)}${if (partner) ", your partner" else ""}. $status. $cards cards" +
                    if (active) ". Thinking" else ""
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .border(ring, c.haldi, CircleShape)
                .padding(3.dp)
                .background(if (partner) c.neem else c.panel, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                seatName(seat).take(1),
                style = MaterialTheme.typography.titleMedium,
                color = if (partner) c.panel else c.ink,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(seatName(seat), style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 1)
        Text(
            status, style = MaterialTheme.typography.labelSmall, color = c.inkMuted,
            maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
        if (cards > 0) Text("$cards cards", style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
    }
}

private fun Modifier.widthInSafe(): Modifier = this.sizeIn(minWidth = 72.dp, maxWidth = 112.dp)

/**
 * The cards on the table. Each card slides in from its player's side; when the trick is
 * complete the cards gather towards the winner.
 */
@Composable
fun TrickArea(
    trick: Trick?,
    winner: Int?,
    direction: Direction,
    cardWidth: Dp,
    fourColour: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Tash.colors
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val slot = cardWidth * 0.62f
        val far = maxWidth.coerceAtLeast(maxHeight) * 0.55f
        if (trick != null) {
            trick.cards.forEachIndexed { i, card ->
                val seat = trick.seatOf(i)
                val side = sideOf(seat, direction)
                key(card, trick.leader) {
                    TrickCard(
                        card = card, side = side, slot = slot, far = far, winnerSide = winner?.let { sideOf(it, direction) },
                        cardWidth = cardWidth, fourColour = fourColour, reduceMotion = reduceMotion,
                        isWinner = winner == seat, haldi = c.haldi, index = i, who = seatName(seat),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrickCard(
    card: Card,
    side: Side,
    slot: Dp,
    far: Dp,
    winnerSide: Side?,
    cardWidth: Dp,
    fourColour: Boolean,
    reduceMotion: Boolean,
    isWinner: Boolean,
    haldi: androidx.compose.ui.graphics.Color,
    index: Int,
    who: String,
) {
    val v = side.vector()
    val start = Offset(v.x * far.value, v.y * far.value)
    val rest = Offset(v.x * slot.value, v.y * slot.value)
    val pos = remember { Animatable(if (reduceMotion) rest else start, Offset.VectorConverter) }
    val fade = remember { Animatable(if (reduceMotion) 1f else 0.6f) }
    val tilt = remember(card) { ((card.code * 37) % 9 - 4).toFloat() }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            coroutineScope {
                launch { fade.animateTo(1f, tween(140)) }
                pos.animateTo(rest, tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
            }
        }
    }
    LaunchedEffect(winnerSide) {
        if (winnerSide != null) {
            delay(450)
            val w = winnerSide.vector()
            val target = Offset(w.x * far.value, w.y * far.value)
            if (reduceMotion) {
                fade.animateTo(0f, tween(120))
            } else {
                coroutineScope {
                    launch { pos.animateTo(target, tween(320, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))) }
                    launch { delay(240); fade.animateTo(0f, tween(80)) }
                }
            }
        }
    }
    PlayingCard(
        card = card, faceUp = true, width = cardWidth, fourColour = fourColour,
        highlight = if (isWinner && winnerSide != null) haldi else null,
        modifier = Modifier
            .offset { IntOffset((pos.value.x * density).roundToInt(), (pos.value.y * density).roundToInt()) }
            .graphicsLayer {
                alpha = fade.value
                rotationZ = tilt
                scaleX = 0.92f; scaleY = 0.92f
            }
            .semantics { contentDescription = "$who played ${card.spoken}" },
    )
}

/**
 * The human's hand: one row when the cards fit with a 44dp visible sliver each, otherwise two
 * rows split at suit boundaries (back row raised so every index stays visible and tappable).
 */
@Composable
fun HandView(
    cards: List<Card>,
    playable: Set<Card>,
    selectable: Set<Card>,
    selected: Card?,
    hint: Card?,
    myTurn: Boolean,
    cardWidth: Dp,
    fourColour: Boolean,
    onTap: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tash.colors
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.BottomCenter) {
        val avail = maxWidth
        val minSliver = 44.dp
        val oneRow = cards.size <= 1 || cardWidth + minSliver * (cards.size - 1) <= avail
        val rows: List<List<Card>> = if (oneRow) listOf(cards) else splitRows(cards)
        val rowLift = cardWidth * CARD_ASPECT * 0.52f
        val handHeight = cardWidth * CARD_ASPECT + (if (rows.size > 1) rowLift else 0.dp) + 24.dp
        Box(Modifier.fillMaxWidth().height(handHeight), contentAlignment = Alignment.BottomCenter) {
            rows.forEachIndexed { r, row ->
                val back = rows.size > 1 && r == 0
                val maxStep = cardWidth + 4.dp
                val step = if (row.size <= 1) 0.dp else minOf(maxStep, (avail - cardWidth) / (row.size - 1))
                val total = cardWidth + step * (row.size - 1).coerceAtLeast(0)
                val startX = (avail - total) / 2
                row.forEachIndexed { i, card -> key(card.code) {
                    val canPlay = card in playable
                    val canSelect = card in selectable
                    val isSel = card == selected
                    val lift by animateDpAsState(
                        targetValue = when {
                            isSel -> 20.dp
                            myTurn && (canPlay || canSelect) -> 8.dp
                            else -> 0.dp
                        },
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
                        label = "lift",
                    )
                    val dim = myTurn && (playable.isNotEmpty() || selectable.isNotEmpty()) && !canPlay && !canSelect
                    val baseY = if (back) -rowLift else 0.dp
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset {
                                IntOffset((startX + step * i).roundToPx(), (baseY - lift).roundToPx())
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = card.spoken
                                stateDescription = when {
                                    isSel -> "selected"
                                    canPlay || canSelect -> "playable"
                                    myTurn -> "not playable"
                                    else -> ""
                                }
                                onClick(label = if (canSelect) "Choose as trump" else if (canPlay) "Play" else "Why not?") { onTap(card); true }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { onTap(card) },
                    ) {
                        PlayingCard(
                            card = card, faceUp = true, width = cardWidth, fourColour = fourColour, dimmed = dim,
                            highlight = when {
                                isSel -> c.haldi
                                card == hint -> c.neem
                                else -> null
                            },
                        )
                    }
                } }
            }
        }
    }
}

/** Splits a sorted hand into two rows at a suit boundary, as evenly as possible. */
private fun splitRows(cards: List<Card>): List<List<Card>> {
    val half = (cards.size + 1) / 2
    // Candidate cut points: suit boundaries; pick the one closest to half.
    val cuts = (1 until cards.size).filter { cards[it].suit != cards[it - 1].suit }
    val cut = cuts.minByOrNull { kotlin.math.abs(it - half) }?.takeIf { kotlin.math.abs(it - half) <= 2 } ?: half
    return listOf(cards.subList(0, cut), cards.subList(cut, cards.size))
}

/** Bidding chips with an explicit confirm, so a slip of the thumb never becomes a bid. */
@Composable
fun BidPanel(
    bids: List<Int>,
    canPass: Boolean,
    selected: Int?,
    suggestion: Int?,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onPass: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val c = Tash.colors
    Column(modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(vertical = 14.dp)) {
        Text(
            title ?: if (canPass) "Your bid (or pass)" else "Your bid",
            style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        // One scrollable row keeps the table visible on small phones.
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(bids.size) { idx ->
                val b = bids[idx]
                val sel = b == selected
                Box(
                    Modifier
                        .size(48.dp)
                        .background(if (sel) c.haldi else c.panelRaised, RoundedCornerShape(10.dp))
                        .border(1.dp, if (b == suggestion && !sel) c.neem else c.rule, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onSelect(b) }
                        .semantics { contentDescription = "Bid $b" + if (b == suggestion) ", suggested" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(b.toString(), style = MaterialTheme.typography.titleMedium, color = if (sel) c.onHaldi else c.ink)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canPass) com.tashlark.app.ui.components.PaperButton("Pass", onPass, primary = false, modifier = Modifier.weight(1f))
            com.tashlark.app.ui.components.PaperButton(
                if (selected != null) "Bid $selected" else "Choose a bid",
                onConfirm, enabled = selected != null, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Suit picker for Court Piece's trump call. */
@Composable
fun SuitPanel(suits: List<Suit>, fourColour: Boolean, onPick: (Suit) -> Unit, modifier: Modifier = Modifier) {
    val c = Tash.colors
    Column(modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(16.dp)) {
        Text("Name the trump suit", style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            suits.forEach { s ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .background(c.cardFace, RoundedCornerShape(10.dp))
                        .border(1.dp, c.rule, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onPick(s) }
                        .semantics { contentDescription = "Make ${s.label} trump" },
                    contentAlignment = Alignment.Center,
                ) { SuitGlyph(s, 28.dp, suitColor(s, fourColour)) }
            }
        }
    }
}

/** Small "Trump ♥" or "Trump hidden" badge in the centre of the table. */
@Composable
fun TrumpBadge(trump: Suit?, hidden: Boolean, fourColour: Boolean, modifier: Modifier = Modifier) {
    val c = Tash.colors
    if (trump == null && !hidden) return
    Row(
        modifier
            .background(c.panel, RoundedCornerShape(50))
            .border(1.dp, c.haldi, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (trump != null) "Trump is ${trump.label}" else "Trump is hidden"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Trump", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
        Spacer(Modifier.width(6.dp))
        if (trump != null) {
            Box(Modifier.size(20.dp).background(c.cardFace, CircleShape), contentAlignment = Alignment.Center) {
                SuitGlyph(trump, 14.dp, suitColor(trump, fourColour))
            }
        } else {
            Text("hidden", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
        }
    }
}

