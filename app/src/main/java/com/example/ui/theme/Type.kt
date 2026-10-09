package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.R

/** Hind Siliguri includes both Bengali and Latin glyphs, so one family keeps both scripts consistent. */
val BodyFont = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.W400),
    Font(R.font.hind_siliguri_medium, FontWeight.W500),
    Font(R.font.hind_siliguri_semibold, FontWeight.W600),
    Font(R.font.hind_siliguri_bold, FontWeight.W700)
)
val DisplayFont: FontFamily = BodyFont
private val HindTextPlatformStyle = PlatformTextStyle(includeFontPadding = true)

/** Tuned type scale with clear separation between labels, body copy and dashboard figures. */
val Typography = Typography(
    displayLarge = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 32.sp, lineHeight = 46.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 28.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 24.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 23.sp, lineHeight = 35.sp),
    headlineMedium = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 21.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W600, fontSize = 19.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 18.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = DisplayFont, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = BodyFont, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = BodyFont, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = BodyFont, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = BodyFont, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 23.sp),
    labelMedium = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = BodyFont, fontWeight = FontWeight.W600, fontSize = 12.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(platformStyle = HindTextPlatformStyle, fontFamily = BodyFont, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 18.sp)
)

@Composable
@ReadOnlyComposable
fun amountTextStyle(size: TextUnit): TextStyle = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.W700,
    fontSize = size,
    lineHeight = size * 1.52f,
    platformStyle = HindTextPlatformStyle,
    fontFeatureSettings = "tnum"
)
