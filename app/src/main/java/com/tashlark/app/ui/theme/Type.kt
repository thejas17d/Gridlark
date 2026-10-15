package com.tashlark.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tashlark.app.R

private fun anek(weight: Int, width: Float = 100f) = Font(
    R.font.anek,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Anek: matches across Latin, Devanagari and Bangla. Used for UI and card indices. */
val UiFamily = FontFamily(anek(400), anek(500), anek(600), anek(700))

/** Condensed Anek for card indices, so "10" fits the corner. */
val IndexFamily = FontFamily(anek(700, 75f))

/** Tiro: bookish print warmth for titles and result headlines. */
val DisplayFamily = FontFamily(Font(R.font.tiro, FontWeight.Normal))

val TashTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 40.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 32.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontSize = 28.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
