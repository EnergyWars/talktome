package com.wafflehq.uikit.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class TypeStyleSpec(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
)

@Immutable
data class AppTypeScale(
    val displayLarge: TypeStyleSpec,
    val displayMedium: TypeStyleSpec,
    val displaySmall: TypeStyleSpec,
    val headlineLarge: TypeStyleSpec,
    val headlineMedium: TypeStyleSpec,
    val headlineSmall: TypeStyleSpec,
    val titleLarge: TypeStyleSpec,
    val titleMedium: TypeStyleSpec,
    val titleSmall: TypeStyleSpec,
    val bodyLarge: TypeStyleSpec,
    val bodyMedium: TypeStyleSpec,
    val bodySmall: TypeStyleSpec,
    val labelLarge: TypeStyleSpec,
    val labelMedium: TypeStyleSpec,
    val labelSmall: TypeStyleSpec,
) {
    companion object {
        val Default = AppTypeScale(
            displayLarge = TypeStyleSpec(57.sp, 64.sp, FontWeight.Bold, (-0.025).em),
            displayMedium = TypeStyleSpec(45.sp, 52.sp, FontWeight.Bold, (-0.025).em),
            displaySmall = TypeStyleSpec(36.sp, 44.sp, FontWeight.Bold, (-0.025).em),
            headlineLarge = TypeStyleSpec(32.sp, 40.sp, FontWeight.Bold, (-0.022).em),
            headlineMedium = TypeStyleSpec(28.sp, 36.sp, FontWeight.Bold, (-0.022).em),
            headlineSmall = TypeStyleSpec(24.sp, 32.sp, FontWeight.Bold, (-0.022).em),
            titleLarge = TypeStyleSpec(22.sp, 28.sp, FontWeight.SemiBold, (-0.008).em),
            titleMedium = TypeStyleSpec(16.sp, 24.sp, FontWeight.SemiBold, (-0.004).em),
            titleSmall = TypeStyleSpec(14.sp, 20.sp, FontWeight.SemiBold),
            bodyLarge = TypeStyleSpec(16.sp, 24.sp, FontWeight.Normal),
            bodyMedium = TypeStyleSpec(14.sp, 20.sp, FontWeight.Normal),
            bodySmall = TypeStyleSpec(12.sp, 16.sp, FontWeight.Normal),
            labelLarge = TypeStyleSpec(14.sp, 20.sp, FontWeight.SemiBold, 0.01.em),
            labelMedium = TypeStyleSpec(12.sp, 16.sp, FontWeight.SemiBold, 0.01.em),
            labelSmall = TypeStyleSpec(11.sp, 16.sp, FontWeight.SemiBold, 0.08.em),
        )
    }
}

val LocalAppTypeScale = staticCompositionLocalOf { AppTypeScale.Default }

private fun TypeStyleSpec.toTextStyle(fontFamily: androidx.compose.ui.text.font.FontFamily) = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    fontFeatureSettings = "tnum",
)

internal fun buildTypography(scale: AppTypeScale, fontFamily: androidx.compose.ui.text.font.FontFamily): Typography =
    Typography(
        displayLarge = scale.displayLarge.toTextStyle(fontFamily),
        displayMedium = scale.displayMedium.toTextStyle(fontFamily),
        displaySmall = scale.displaySmall.toTextStyle(fontFamily),
        headlineLarge = scale.headlineLarge.toTextStyle(fontFamily),
        headlineMedium = scale.headlineMedium.toTextStyle(fontFamily),
        headlineSmall = scale.headlineSmall.toTextStyle(fontFamily),
        titleLarge = scale.titleLarge.toTextStyle(fontFamily),
        titleMedium = scale.titleMedium.toTextStyle(fontFamily),
        titleSmall = scale.titleSmall.toTextStyle(fontFamily),
        bodyLarge = scale.bodyLarge.toTextStyle(fontFamily),
        bodyMedium = scale.bodyMedium.toTextStyle(fontFamily),
        bodySmall = scale.bodySmall.toTextStyle(fontFamily),
        labelLarge = scale.labelLarge.toTextStyle(fontFamily),
        labelMedium = scale.labelMedium.toTextStyle(fontFamily),
        labelSmall = scale.labelSmall.toTextStyle(fontFamily),
    )
