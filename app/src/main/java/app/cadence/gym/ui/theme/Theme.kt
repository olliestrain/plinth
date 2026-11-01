package app.cadence.gym.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TC {
    val Bg = Color(0xFF0D0D0F)
    val Surface = Color(0xFF121214)
    val Card = Color(0xFF17171A)
    val CardHi = Color(0xFF1E1E22)
    val Outline = Color(0xFF2A2A2F)
    val Text = Color(0xFFEAE8E3)
    val Dim = Color(0xFF8C8B88)
    val Faint = Color(0xFF56565A)

    /** Single restrained accent: brushed brass. */
    val Accent = Color(0xFFC8A96A)
    val AccentDeep = Color(0xFF9C8250)
    val Ember = Color(0xFFC98A52)
    val Danger = Color(0xFFC4575A)
    val Good = Color(0xFF6FA283)
    val Sky = Color(0xFF7C93A8)
    val Violet = Color(0xFF8F89A8)
    val Rose = Color(0xFFA68490)

    /** Workout colour palette (index stored in the DB). */
    val Palette = listOf(Accent, Sky, Violet, Rose, Ember, Good)
    fun palette(i: Int) = Palette[((i % Palette.size) + Palette.size) % Palette.size]
}

val Mono = FontFamily.Monospace

private val scheme = darkColorScheme(
    primary = TC.Accent, onPrimary = Color(0xFF0D0D0F),
    secondary = TC.Sky, onSecondary = Color(0xFF0D0D0F),
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
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

@Composable
fun CadenceTheme(content: @Composable () -> Unit) {
    // Dark by design.
    MaterialTheme(colorScheme = scheme, typography = type) {
        CompositionLocalProvider(LocalContentColor provides TC.Text, content = content)
    }
}
