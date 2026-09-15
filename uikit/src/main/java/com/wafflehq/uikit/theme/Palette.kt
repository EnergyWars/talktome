package com.wafflehq.uikit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ColorRamp(
    val name: String,
    val tones: List<Pair<String, Color>>,
) {
    init {
        require(tones.size == 9) { "ColorRamp '$name' needs exactly 9 tones (10..90), has ${tones.size}" }
    }

    operator fun get(tone: String): Color =
        tones.firstOrNull { it.first == tone }?.second
            ?: error("ColorRamp '$name' has no tone '$tone'")
}

private fun ramp(name: String, tones: List<Color>): ColorRamp {
    require(tones.size == 9) { "ramp('$name') needs exactly 9 tones (10..90), has ${tones.size}" }
    val labels = listOf("10", "20", "30", "40", "50", "60", "70", "80", "90")
    return ColorRamp(name, labels.zip(tones))
}

@Immutable
data class WafflePalette(
    val primary: ColorRamp,
    val secondary: ColorRamp,
    val tertiary: ColorRamp,
    val success: ColorRamp,
    val warning: ColorRamp,
    val error: ColorRamp,
    val neutral: ColorRamp,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val darkSurface3: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val lightSurface3: Color,
    val onSurfaceDark: Color,
    val onSurfaceLight: Color,
    val onSurfaceVariantDark: Color,
    val onSurfaceVariantLight: Color,
    val outlineDark: Color,
    val outlineLight: Color,
) {
    val ramps: List<ColorRamp>
        get() = listOf(primary, secondary, tertiary, success, warning, error, neutral)

    fun forRole(role: AppRole): ColorRamp = when (role) {
        AppRole.Primary -> primary
        AppRole.Secondary -> secondary
        AppRole.Tertiary -> tertiary
        AppRole.Success -> success
        AppRole.Warning -> warning
        AppRole.Error -> error
        AppRole.Neutral -> neutral
    }

    fun withRole(role: AppRole, newRamp: ColorRamp): WafflePalette = when (role) {
        AppRole.Primary -> copy(primary = newRamp)
        AppRole.Secondary -> copy(secondary = newRamp)
        AppRole.Tertiary -> copy(tertiary = newRamp)
        AppRole.Success -> copy(success = newRamp)
        AppRole.Warning -> copy(warning = newRamp)
        AppRole.Error -> copy(error = newRamp)
        AppRole.Neutral -> copy(neutral = newRamp)
    }

    companion object {
        val Default = WafflePalette(
            primary = ramp(
                "Sapphire",
                listOf(
                    Color(0xFF000F23), Color(0xFF001D3D), Color(0xFF0A3055), Color(0xFF0E3D6E), Color(0xFF325E8D),
                    Color(0xFF5580AD), Color(0xFF79A3CD), Color(0xFF9EC8EE), Color(0xFFCFE5F8),
                ),
            ),
            secondary = ramp(
                "Aquamarine",
                listOf(
                    Color(0xFF001A1F), Color(0xFF002F37), Color(0xFF1A4750), Color(0xFF1F5A66), Color(0xFF3D7480),
                    Color(0xFF5A8E9A), Color(0xFF74AAB7), Color(0xFF8FCBD8), Color(0xFFC7E5EB),
                ),
            ),
            tertiary = ramp(
                "Amethyst",
                listOf(
                    Color(0xFF1F0014), Color(0xFF380024), Color(0xFF5A1A45), Color(0xFF5E3656), Color(0xFF78516E),
                    Color(0xFF926D88), Color(0xFFAD8AA2), Color(0xFFC9A8BD), Color(0xFFE8D6E0),
                ),
            ),
            success = ramp(
                "Emerald",
                listOf(
                    Color(0xFF001A0B), Color(0xFF003018), Color(0xFF1A4228), Color(0xFF2D5A3D), Color(0xFF427353),
                    Color(0xFF598D6A), Color(0xFF6FA882), Color(0xFF87C49B), Color(0xFFBDE5CA),
                ),
            ),
            warning = ramp(
                "Citrine",
                listOf(
                    Color(0xFF1F1000), Color(0xFF3A1F00), Color(0xFF5A3300), Color(0xFF8B4A00), Color(0xFFA76411),
                    Color(0xFFC48021), Color(0xFFE19C31), Color(0xFFFFB940), Color(0xFFFFDFA8),
                ),
            ),
            error = ramp(
                "Garnet",
                listOf(
                    Color(0xFF26060F), Color(0xFF451220), Color(0xFF6B1F32), Color(0xFF8E2A3D), Color(0xFFA74B58),
                    Color(0xFFC06974), Color(0xFFD88892), Color(0xFFF0A8B0), Color(0xFFFAD5DA),
                ),
            ),
            neutral = ramp(
                "Graphite",
                listOf(
                    Color(0xFF0B0F18), Color(0xFF161B27), Color(0xFF2C3340), Color(0xFF44516B), Color(0xFF5C6A82),
                    Color(0xFF74829A), Color(0xFF94A2B8), Color(0xFFB6BFD0), Color(0xFFDCE0EA),
                ),
            ),
            darkBackground = Color(0xFF080E18),
            darkSurface = Color(0xFF0E1825),
            darkSurfaceVariant = Color(0xFF1A2535),
            darkSurface3 = Color(0xFF243047),
            lightBackground = Color(0xFFF4F6FA),
            lightSurface = Color(0xFFFFFFFF),
            lightSurfaceVariant = Color(0xFFDDE3EC),
            lightSurface3 = Color(0xFFCBD2DE),
            onSurfaceDark = Color(0xFFB0BCC8),
            onSurfaceLight = Color(0xFF191C22),
            onSurfaceVariantDark = Color(0xFF7588A0),
            onSurfaceVariantLight = Color(0xFF424850),
            outlineDark = Color(0xFF586E88),
            outlineLight = Color(0xFF72788A),
        )
    }
}

val LocalWafflePalette = staticCompositionLocalOf { WafflePalette.Default }
