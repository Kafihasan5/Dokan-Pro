package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==============================================================================
// 1. BRAND COLOR RAMP
// ==============================================================================
val Brand900 = Color(0xFF04231A)
val Brand700 = Color(0xFF076B4C)
val Brand500 = Color(0xFF0E9F6E)
val Brand300 = Color(0xFF5FD3AC)
val Brand100 = Color(0xFFD7F5E9)

// ==============================================================================
// 2. GOLD RAMP (ONLY for money highlights and primary CTAs)
// ==============================================================================
val Gold500 = Color(0xFFE0A106)
val Gold100 = Color(0xFFFDF0D2)

// ==============================================================================
// 3. SEMANTIC COLORS & CONTAINERS
// ==============================================================================
val Success = Color(0xFF15803D)
val SuccessContainerLight = Color(0xFFDCFCE7)
val SuccessContainerDark = Color(0xFF052E16)

val Warning = Color(0xFFB45309)
val WarningContainerLight = Color(0xFFFEF3C7)
val WarningContainerDark = Color(0xFF451A03)

val Danger = Color(0xFFDC2626)
val DangerContainerLight = Color(0xFFFEE2E2)
val DangerContainerDark = Color(0xFF450A0A)

val Info = Color(0xFF1D4ED8)
val InfoContainerLight = Color(0xFFDBEAFE)
val InfoContainerDark = Color(0xFF172554)

// ==============================================================================
// 4. LIGHT NEUTRALS
// ==============================================================================
val BgLight = Color(0xFFF5F7F8)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceAltLight = Color(0xFFEDF1F2)
val BorderLight = Color(0xFFE1E7E9)
val InkLight = Color(0xFF0C1512)
val Ink2Light = Color(0xFF5B6A64)
val Ink3Light = Color(0xFF8A9993)

// ==============================================================================
// 5. DARK NEUTRALS
// ==============================================================================
val BgDark = Color(0xFF0A0F0D)
val SurfaceDark = Color(0xFF121917)
val SurfaceAltDark = Color(0xFF1A2320)
val BorderDark = Color(0xFF26312D)
val InkDark = Color(0xFFE8EEEB)
val Ink2Dark = Color(0xFF9BAAA4)
val Ink3Dark = Color(0xFF6E7D77)

// ==============================================================================
// 6. BACKWARD COMPATIBILITY / DEPRECATED ALIASES
// ==============================================================================
@Deprecated("Use Brand500", ReplaceWith("Brand500"))
val EmeraldPrimaryLight = Brand500

@Deprecated("Use Brand300", ReplaceWith("Brand300"))
val EmeraldPrimaryDark = Brand300

@Deprecated("Use Brand100", ReplaceWith("Brand100"))
val EmeraldPrimaryContainerLight = Brand100

@Deprecated("Use Brand900", ReplaceWith("Brand900"))
val EmeraldPrimaryContainerDark = Brand900

@Deprecated("Use Color.White", ReplaceWith("Color.White"))
val EmeraldOnPrimary = Color.White

@Deprecated("Use Brand900", ReplaceWith("Brand900"))
val EmeraldOnPrimaryContainerLight = Brand900

@Deprecated("Use Brand100", ReplaceWith("Brand100"))
val EmeraldOnPrimaryContainerDark = Brand100

@Deprecated("Use BgLight", ReplaceWith("BgLight"))
val BackgroundLight = BgLight

@Deprecated("Use BgDark", ReplaceWith("BgDark"))
val BackgroundDark = BgDark

@Deprecated("Use SurfaceAltLight", ReplaceWith("SurfaceAltLight"))
val SurfaceVariantLight = SurfaceAltLight

@Deprecated("Use SurfaceAltDark", ReplaceWith("SurfaceAltDark"))
val SurfaceVariantDark = SurfaceAltDark

@Deprecated("Use InkLight", ReplaceWith("InkLight"))
val TextPrimaryLight = InkLight

@Deprecated("Use InkDark", ReplaceWith("InkDark"))
val TextPrimaryDark = InkDark

@Deprecated("Use Ink2Light", ReplaceWith("Ink2Light"))
val TextSecondaryLight = Ink2Light

@Deprecated("Use Ink2Dark", ReplaceWith("Ink2Dark"))
val TextSecondaryDark = Ink2Dark

@Deprecated("Use Success", ReplaceWith("Success"))
val StatusSuccess = Success

@Deprecated("Use Warning", ReplaceWith("Warning"))
val StatusWarning = Warning

@Deprecated("Use Danger", ReplaceWith("Danger"))
val StatusDanger = Danger

@Deprecated("Use Info", ReplaceWith("Info"))
val StatusInfo = Info
