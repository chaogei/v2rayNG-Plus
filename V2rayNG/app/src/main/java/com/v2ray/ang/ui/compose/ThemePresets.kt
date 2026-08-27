package com.v2ray.ang.ui.compose

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.v2ray.ang.R

/**
 * Predefined color themes ("theme presets").
 *
 * Each preset only swaps the accent roles (primary / secondary / tertiary
 * groups) and the backdrop glow colors; all neutral surface roles come from
 * the shared base schemes in Theme.kt and are glassified by the single
 * [toGlassScheme] pass. That keeps the frosted material identical across
 * themes while the brand hue, glow and accent widgets change instantly.
 */
enum class ThemePreset(val id: String, @StringRes val labelRes: Int) {
    AMBER("amber", R.string.theme_preset_amber),
    MIDNIGHT("midnight", R.string.theme_preset_midnight),
    AURORA("aurora", R.string.theme_preset_aurora),
    SAKURA("sakura", R.string.theme_preset_sakura),
    GRAPHITE("graphite", R.string.theme_preset_graphite),
    SILVER("silver", R.string.theme_preset_silver);

    companion object {
        const val DEFAULT_ID = "amber"

        fun fromId(id: String?): ThemePreset =
            entries.firstOrNull { it.id == id } ?: AMBER
    }
}

/** The three brand colors a preset feeds into the [AppGlassBackground] glows. */
data class GlassGlow(
    val first: Color,
    val second: Color,
    val third: Color,
)

/** Accent-role overrides applied on top of the shared neutral base scheme. */
private class ThemeAccents(
    val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color,
    val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color,
    val tertiaryContainer: Color, val onTertiaryContainer: Color,
)

private fun ColorScheme.withAccents(a: ThemeAccents): ColorScheme = copy(
    primary = a.primary, onPrimary = a.onPrimary,
    primaryContainer = a.primaryContainer, onPrimaryContainer = a.onPrimaryContainer,
    secondary = a.secondary, onSecondary = a.onSecondary,
    secondaryContainer = a.secondaryContainer, onSecondaryContainer = a.onSecondaryContainer,
    tertiary = a.tertiary, onTertiary = a.onTertiary,
    tertiaryContainer = a.tertiaryContainer, onTertiaryContainer = a.onTertiaryContainer,
)

// Midnight Blue
private val MidnightLight = ThemeAccents(
    primary = Color(0xFF1F3A93), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF), onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF3D6DEB), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE6FF), onSecondaryContainer = Color(0xFF0A1F5C),
    tertiary = Color(0xFF0093B8), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2ECF7), onTertiaryContainer = Color(0xFF002830),
)
private val MidnightDark = ThemeAccents(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF0A2378),
    primaryContainer = Color(0xFF2C4494), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9DB6FF), onSecondary = Color(0xFF10286B),
    secondaryContainer = Color(0xFF274293), onSecondaryContainer = Color(0xFFDDE6FF),
    tertiary = Color(0xFF6FD4EE), onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF004E5D), onTertiaryContainer = Color(0xFFC2ECF7),
)

// Aurora Green
private val AuroraLight = ThemeAccents(
    primary = Color(0xFF006C4F), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF86F8C9), onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF00A36C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC9F3DE), onSecondaryContainer = Color(0xFF00251A),
    tertiary = Color(0xFF00829B), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF6), onTertiaryContainer = Color(0xFF001F26),
)
private val AuroraDark = ThemeAccents(
    primary = Color(0xFF69DBAE), onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138), onPrimaryContainer = Color(0xFF86F8C9),
    secondary = Color(0xFF5CE0A9), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF005236), onSecondaryContainer = Color(0xFFC9F3DE),
    tertiary = Color(0xFF5BD5F0), onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF004E5D), onTertiaryContainer = Color(0xFFBEEAF6),
)

// Sakura Pink
private val SakuraLight = ThemeAccents(
    primary = Color(0xFF8E2F56), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E4), onPrimaryContainer = Color(0xFF3B0021),
    secondary = Color(0xFFE0507E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2), onSecondaryContainer = Color(0xFF3F0018),
    tertiary = Color(0xFF8C5BB8), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1DBFF), onTertiaryContainer = Color(0xFF2E004E),
)
private val SakuraDark = ThemeAccents(
    primary = Color(0xFFFFB0C8), onPrimary = Color(0xFF541D33),
    primaryContainer = Color(0xFF703349), onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFFFFA8BE), onSecondary = Color(0xFF5C1130),
    secondaryContainer = Color(0xFF7A2949), onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFDDB9FF), onTertiary = Color(0xFF45146B),
    tertiaryContainer = Color(0xFF5D2C84), onTertiaryContainer = Color(0xFFF1DBFF),
)

// Graphite Purple
private val GraphiteLight = ThemeAccents(
    primary = Color(0xFF4A3D8F), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF150065),
    secondary = Color(0xFF7C5CD6), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DDFF), onSecondaryContainer = Color(0xFF23005C),
    tertiary = Color(0xFF605A71), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DFF9), onTertiaryContainer = Color(0xFF1C172B),
)
private val GraphiteDark = ThemeAccents(
    primary = Color(0xFFC8BFFF), onPrimary = Color(0xFF2F2176),
    primaryContainer = Color(0xFF46388E), onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFCFBCFF), onSecondary = Color(0xFF3C1D71),
    secondaryContainer = Color(0xFF533A8F), onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFFCAC1DC), onTertiary = Color(0xFF322E41),
    tertiaryContainer = Color(0xFF494458), onTertiaryContainer = Color(0xFFE6DFF9),
)

// Silver Gray (neutral, low saturation)
private val SilverLight = ThemeAccents(
    primary = Color(0xFF3E4C59), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE4EC), onPrimaryContainer = Color(0xFF101C26),
    secondary = Color(0xFF5C7488), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE9F0), onSecondaryContainer = Color(0xFF16232D),
    tertiary = Color(0xFF4E6E6A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2E8E3), onTertiaryContainer = Color(0xFF0E2320),
)
private val SilverDark = ThemeAccents(
    primary = Color(0xFFBCC8D4), onPrimary = Color(0xFF26323D),
    primaryContainer = Color(0xFF3C4954), onPrimaryContainer = Color(0xFFDBE4EC),
    secondary = Color(0xFFB3C7D6), onSecondary = Color(0xFF263845),
    secondaryContainer = Color(0xFF435562), onSecondaryContainer = Color(0xFFDFE9F0),
    tertiary = Color(0xFFB2CCC6), onTertiary = Color(0xFF1F3531),
    tertiaryContainer = Color(0xFF364B47), onTertiaryContainer = Color(0xFFD2E8E3),
)

/**
 * Resolve the full opaque color scheme for a preset. The result is passed
 * through [toGlassScheme] by the theme root, so every preset shares one glass
 * implementation. AMBER is the factory default and returns the original base
 * schemes untouched.
 */
internal fun ThemePreset.colorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) DarkColor else LightColor
    return when (this) {
        ThemePreset.AMBER -> base
        ThemePreset.MIDNIGHT -> base.withAccents(if (darkTheme) MidnightDark else MidnightLight)
        ThemePreset.AURORA -> base.withAccents(if (darkTheme) AuroraDark else AuroraLight)
        ThemePreset.SAKURA -> base.withAccents(if (darkTheme) SakuraDark else SakuraLight)
        ThemePreset.GRAPHITE -> base.withAccents(if (darkTheme) GraphiteDark else GraphiteLight)
        ThemePreset.SILVER -> base.withAccents(if (darkTheme) SilverDark else SilverLight)
    }
}

/**
 * Brand colors feeding the three backdrop glows. Alphas are applied inside
 * [AppGlassBackground] so every theme keeps the same low-opacity glass look.
 */
fun ThemePreset.glassGlow(): GlassGlow = when (this) {
    ThemePreset.AMBER -> GlassGlow(Color(0xFFF97910), Color(0xFF009966), Color(0xFF5B7CFA))
    ThemePreset.MIDNIGHT -> GlassGlow(Color(0xFF3D6DEB), Color(0xFF7C4DFF), Color(0xFF00B8D4))
    ThemePreset.AURORA -> GlassGlow(Color(0xFF00A36C), Color(0xFF00B8D4), Color(0xFF3DDC84))
    ThemePreset.SAKURA -> GlassGlow(Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFFFF8A80))
    ThemePreset.GRAPHITE -> GlassGlow(Color(0xFF7C5CD6), Color(0xFF536DFE), Color(0xFFB388FF))
    ThemePreset.SILVER -> GlassGlow(Color(0xFF8FA6B8), Color(0xFF7F8C99), Color(0xFF6FA5A0))
}
