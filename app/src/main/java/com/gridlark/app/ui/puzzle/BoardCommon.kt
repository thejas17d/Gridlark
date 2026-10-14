package com.gridlark.app.ui.puzzle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import kotlin.math.sign

/** Callbacks a board receives from the shared grid gesture detector. */
class GridGestureCallbacks(
    val onDown: (Int) -> Unit = {},
    val onTap: (Int) -> Unit = {},
    val onDragStart: (Int) -> Unit = {},
    val onDragEnter: (Int) -> Unit = {},
    val onDragEnd: () -> Unit = {},
)

/**
 * Tap vs drag detection over an n×n grid. Fast drags that skip cells are filled in
 * one orthogonal step at a time, so drawn paths never jump.
 */
fun Modifier.gridGestures(n: Int, callbacks: State<GridGestureCallbacks>): Modifier = pointerInput(n) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val cellSize = size.width.toFloat() / n
        fun cellAt(p: Offset): Int? {
            if (p.x < 0 || p.y < 0) return null
            val c = (p.x / cellSize).toInt()
            val r = (p.y / cellSize).toInt()
            return if (c in 0 until n && r in 0 until n) r * n + c else null
        }
        val start = cellAt(down.position) ?: return@awaitEachGesture
        callbacks.value.onDown(start)
        var dragging = false
        var last = start
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val cell = cellAt(change.position)
            if (cell != null && cell != last) {
                if (!dragging) {
                    dragging = true
                    callbacks.value.onDragStart(start)
                }
                for (step in stepsBetween(n, last, cell)) callbacks.value.onDragEnter(step)
                last = cell
            }
            change.consume()
        }
        if (dragging) callbacks.value.onDragEnd() else callbacks.value.onTap(start)
    }
}

/** Cells from [from] (exclusive) to [to] (inclusive), moving one orthogonal step at a time. */
internal fun stepsBetween(n: Int, from: Int, to: Int): List<Int> {
    var r = from / n; var c = from % n
    val tr = to / n; val tc = to % n
    val out = ArrayList<Int>()
    while (r != tr || c != tc) {
        // Move along the axis with the larger remaining distance first, keeping the line close to the finger.
        if (abs(tc - c) >= abs(tr - r)) c += (tc - c).sign else r += (tr - r).sign
        out.add(r * n + c)
    }
    return out
}

/**
 * Invisible per-cell semantics so TalkBack can read and activate every cell.
 * Doesn't intercept touches.
 */
@Composable
fun AccessibleGrid(n: Int, describe: (Int) -> String, onActivate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val activate by rememberUpdatedState(onActivate)
    val desc by rememberUpdatedState(describe)
    Column(modifier.fillMaxSize()) {
        for (r in 0 until n) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until n) {
                    val cell = r * n + c
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .semantics {
                                role = Role.Button
                                contentDescription = desc(cell)
                                onClick { activate(cell); true }
                            },
                    )
                }
            }
        }
    }
}

/** 0→1→0 pulse used to spotlight a hinted cell. Restarts whenever [key] changes. */
@Composable
fun rememberPulse(key: Any?, reduceMotion: Boolean): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == null) { anim.snapTo(0f); return@LaunchedEffect }
        if (reduceMotion) {
            anim.snapTo(1f); kotlinx.coroutines.delay(1400); anim.snapTo(0f)
        } else {
            repeat(2) {
                anim.animateTo(1f, tween(320))
                anim.animateTo(0.25f, tween(320))
            }
            anim.animateTo(0f, tween(400))
        }
    }
    return anim.asState()
}

/** Colour names for TalkBack, aligned with the region palette order. */
val RegionNames = listOf(
    "blue", "coral", "sand", "pink", "sky", "mint", "pear", "olive", "grey", "lilac", "tan", "sage",
)
