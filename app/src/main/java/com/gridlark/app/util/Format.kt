package com.gridlark.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%d:%02d".format(Locale.US, m, s)
}

// Built per call so a language change while the app is open is picked up.
fun LocalDate.longLabel(): String = format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
fun LocalDate.shortLabel(): String = format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
fun LocalDate.weekdayLetter(): String = dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
