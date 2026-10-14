package com.gridlark.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gridlark.app.R

private fun fraunces(weight: Int, opsz: Float) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
        FontVariation.Setting("SOFT", 50f),
        FontVariation.Setting("WONK", 0f),
    ),
)

private fun atkinson(weight: Int) = Font(
    R.font.atkinson_next,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Editorial serif for mastheads and titles. */
val DisplayFamily = FontFamily(fraunces(600, 72f))
val TitleFamily = FontFamily(fraunces(500, 24f), fraunces(600, 24f))

/** Highly legible sans for UI and grid digits. */
val UiFamily = FontFamily(atkinson(400), atkinson(500), atkinson(600), atkinson(700))

val MonoFamily = FontFamily(
    Font(R.font.atkinson_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

val GridTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.01).em),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 36.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(600), fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = TitleFamily, fontWeight = FontWeight(600), fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = TitleFamily, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = TitleFamily, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = TitleFamily, fontWeight = FontWeight(500), fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = TitleFamily, fontWeight = FontWeight(500), fontSize = 20.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(700), fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(700), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
)

val TimerStyle = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
