package com.tashlark.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-drawn 24dp line icons with a 1.75 stroke, tinted at use site. */
object GridIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = block)
        }.build()

    val Back = icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val Close = icon("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Chevron = icon("chevron") { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }
    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }

    val Settings = icon("settings") {
        moveTo(4f, 7f); lineTo(7f, 7f)
        moveTo(11f, 7f); lineTo(20f, 7f)
        moveTo(11f, 7f); arcTo(2f, 2f, 0f, true, true, 7f, 7f); arcTo(2f, 2f, 0f, true, true, 11f, 7f)
        moveTo(4f, 17f); lineTo(13f, 17f)
        moveTo(17f, 17f); lineTo(20f, 17f)
        moveTo(17f, 17f); arcTo(2f, 2f, 0f, true, true, 13f, 17f); arcTo(2f, 2f, 0f, true, true, 17f, 17f)
    }

    val Undo = icon("undo") {
        moveTo(9f, 6.5f); lineTo(5f, 10.5f); lineTo(9f, 14.5f)
        moveTo(5f, 10.5f); lineTo(14.5f, 10.5f)
        arcTo(4.75f, 4.75f, 0f, false, true, 14.5f, 20f)
        lineTo(11f, 20f)
    }

    val Hint = icon("hint") {
        moveTo(9.5f, 17.5f); lineTo(14.5f, 17.5f)
        moveTo(10.5f, 20.5f); lineTo(13.5f, 20.5f)
        moveTo(9.5f, 15f)
        curveTo(9.5f, 13f, 6.5f, 12f, 6.5f, 9f)
        arcTo(5.5f, 5.5f, 0f, false, true, 17.5f, 9f)
        curveTo(17.5f, 12f, 14.5f, 13f, 14.5f, 15f)
        close()
    }

    val Pause = icon("pause") { moveTo(9f, 6f); lineTo(9f, 18f); moveTo(15f, 6f); lineTo(15f, 18f) }
    val Play = icon("play") { moveTo(8f, 5.5f); lineTo(18.5f, 12f); lineTo(8f, 18.5f); close() }

    val Share = icon("share") {
        moveTo(12f, 4f); lineTo(12f, 14.5f)
        moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
        moveTo(5f, 12f); lineTo(5f, 19.5f); lineTo(19f, 19.5f); lineTo(19f, 12f)
    }

    val Erase = icon("erase") {
        moveTo(9f, 6f); lineTo(20f, 6f); lineTo(20f, 18f); lineTo(9f, 18f); lineTo(3.5f, 12f); close()
        moveTo(12f, 9.5f); lineTo(17f, 14.5f)
        moveTo(17f, 9.5f); lineTo(12f, 14.5f)
    }

    val Pencil = icon("pencil") {
        moveTo(4.5f, 19.5f); lineTo(5f, 16f); lineTo(15.5f, 5.5f); lineTo(18.5f, 8.5f); lineTo(8f, 19f); close()
        moveTo(13.5f, 7.5f); lineTo(16.5f, 10.5f)
    }

    val Restart = icon("restart") {
        moveTo(19f, 12f)
        arcTo(7f, 7f, 0f, true, true, 16.6f, 6.7f)
        moveTo(17.5f, 3f); lineTo(17.5f, 7.2f); lineTo(13.3f, 7.2f)
    }

    val Rules = icon("rules") {
        moveTo(6f, 4.5f); lineTo(18f, 4.5f); lineTo(18f, 19.5f); lineTo(6f, 19.5f); close()
        moveTo(9f, 9f); lineTo(15f, 9f)
        moveTo(9f, 12f); lineTo(15f, 12f)
        moveTo(9f, 15f); lineTo(13f, 15f)
    }

    val More = filled("more") {
        moveTo(12f, 5f); arcTo(1.6f, 1.6f, 0f, true, true, 12f, 8.2f); arcTo(1.6f, 1.6f, 0f, true, true, 12f, 5f)
        moveTo(12f, 10.4f); arcTo(1.6f, 1.6f, 0f, true, true, 12f, 13.6f); arcTo(1.6f, 1.6f, 0f, true, true, 12f, 10.4f)
        moveTo(12f, 15.8f); arcTo(1.6f, 1.6f, 0f, true, true, 12f, 19f); arcTo(1.6f, 1.6f, 0f, true, true, 12f, 15.8f)
    }

    val Bell = icon("bell") {
        moveTo(6.5f, 16.5f); lineTo(6.5f, 11f)
        arcTo(5.5f, 5.5f, 0f, false, true, 17.5f, 11f)
        lineTo(17.5f, 16.5f); lineTo(19f, 18f); lineTo(5f, 18f); close()
        moveTo(10f, 20.5f); lineTo(14f, 20.5f)
    }
}
