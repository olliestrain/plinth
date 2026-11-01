package app.rowan.gym.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TC {
    val Bg = Color(0xFF0B0B0D)
    val Surface = Color(0xFF131316)
    val Card = Color(0xFF1B1B20)
    val CardHi = Color(0xFF25252C)
    val Outline = Color(0xFF2E2E37)
    val Text = Color(0xFFF4F4F6)
    val Dim = Color(0xFF9A9AA8)
    val Faint = Color(0xFF5A5A66)
    val Lime = Color(0xFFD7FF3A)
    val LimeDeep = Color(0xFF9DBB1F)
    val Ember = Color(0xFFFF7A45)
    val Danger = Color(0xFFFF5C6C)
    val Good = Color(0xFF4ADE80)
    val Sky = Color(0xFF7DD3FC)
    val Violet = Color(0xFFB69CFF)
    val Rose = Color(0xFFFF8FB8)

    /** Workout colour palette (index stored in the DB). */
    val Palette = listOf(Lime, Sky, Violet, Rose, Ember, Good)
    fun palette(i: Int) = Palette[((i % Palette.size) + Palette.size) % Palette.size]
}

val Mono = FontFamily.Monospace

private val scheme = darkColorScheme(
    primary = TC.Lime, onPrimary = Color(0xFF0B0B0D),
    secondary = TC.Sky, onSecondary = Color(0xFF0B0B0D),
    tertiary = TC.Violet,
    background = TC.Bg, onBackground = TC.Text,
    surface = TC.Surface, onSurface = TC.Text,
    surfaceVariant = TC.Card, onSurfaceVariant = TC.Dim,
    surfaceContainer = TC.Card, surfaceContainerHigh = TC.CardHi, surfaceContainerHighest = TC.CardHi,
    surfaceContainerLow = TC.Surface, surfaceContainerLowest = TC.Bg,
    outline = TC.Outline, outlineVariant = TC.Outline,
    error = TC.Danger,
)

private val type = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.8.sp),
)

@Composable
fun RowanTheme(content: @Composable () -> Unit) {
    // Rowan is dark by design; the dot-matrix look only works on graphite.
    @Suppress("UNUSED_VARIABLE") val sys = isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = type) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides TC.Text, content = content,
        )
    }
}
