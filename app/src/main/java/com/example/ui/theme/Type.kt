package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.R

val BodyFont = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.W400),
    Font(R.font.hind_siliguri_medium, FontWeight.W500),
    Font(R.font.hind_siliguri_semibold, FontWeight.W600),
    Font(R.font.hind_siliguri_bold, FontWeight.W700)
)

val DisplayFont = FontFamily(
    Font(R.font.anek_bangla_semibold, FontWeight.W600),
    Font(R.font.anek_bangla_bold, FontWeight.W700),
    Font(R.font.anek_bangla_extrabold, FontWeight.W800)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.W800,
        fontSize = 34.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.W700,
        fontSize = 26.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.W700,
        fontSize = 20.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.W600,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
)

@Composable
@ReadOnlyComposable
fun amountTextStyle(size: TextUnit): TextStyle {
    return TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.35f,
        fontFeatureSettings = "tnum"
    )
}
