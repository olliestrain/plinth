package app.plinth.gym.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.plinth.gym.R

object TC {
    val Bg = Color(0xFF0C0708)
    /** Opaque surface for sheets and dialogs. */
    val Surface = Color(0xFF151012)
    /** Translucent dark scrims: the painted backdrop shows through, the hairline defines the edge. */
    val Card = Color(0xA80E090B)
    val CardHi = Color(0x1FFFFFFF)
    val Outline = Color(0x1AFFFFFF)
    val Text = Color(0xFFF4EFEA)
    val Dim = Color(0xB3F4EFEA)
    val Faint = Color(0x73F4EFEA)

    /** Primary is the light ink of the interface itself; colour comes from the backdrops. */
    val Accent = Color(0xFFF4EFEA)
    val AccentDeep = Color(0xFFB9B2AB)
    val Ember = Color(0xFFE0703A)
    val Danger = Color(0xFFE0636C)
    val Good = Color(0xFF6EC8A0)
    val Sky = Color(0xFF6EC8BE)
    val Violet = Color(0xFFA78BE0)
    val Rose = Color(0xFFE08AA8)
    val Gold = Color(0xFFE9B26A)

    /** Workout colour palette (index stored in the DB); also picks the backdrop for that workout. */
    val Palette = listOf(Ember, Sky, Gold, Violet, Good, Rose)
    fun palette(i: Int) = Palette[((i % Palette.size) + Palette.size) % Palette.size]
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

val Sans = variable(R.font.instrument_sans, 400, 500, 600)
val Mono = variable(R.font.jetbrains_mono, 400, 500)

private val scheme = darkColorScheme(
    primary = TC.Accent, onPrimary = Color(0xFF0C0708),
    secondary = TC.Sky, onSecondary = Color(0xFF0C0708),
    tertiary = TC.Violet,
    background = TC.Bg, onBackground = TC.Text,
    surface = TC.Surface, onSurface = TC.Text,
    surfaceVariant = TC.Surface, onSurfaceVariant = TC.Dim,
    surfaceContainer = TC.Surface, surfaceContainerHigh = TC.Surface, surfaceContainerHighest = TC.Surface,
    surfaceContainerLow = TC.Surface, surfaceContainerLowest = TC.Bg,
    outline = TC.Outline, outlineVariant = TC.Outline,
    error = TC.Danger,
)

private fun style(w: FontWeight, size: Int, line: Int, spacing: Double) =
    TextStyle(fontFamily = Sans, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val type = Typography(
    displayLarge = style(FontWeight.Medium, 44, 46, -1.3),
    displayMedium = style(FontWeight.Medium, 38, 40, -1.1),
    headlineLarge = style(FontWeight.Medium, 30, 34, -0.9),
    headlineMedium = style(FontWeight.Medium, 24, 28, -0.6),
    headlineSmall = style(FontWeight.Medium, 20, 24, -0.4),
    titleLarge = style(FontWeight.SemiBold, 18, 22, -0.2),
    titleMedium = style(FontWeight.SemiBold, 16, 20, -0.1),
    titleSmall = style(FontWeight.Medium, 14, 18, 0.0),
    bodyLarge = style(FontWeight.Normal, 16, 23, 0.0),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.0),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.0),
    labelLarge = style(FontWeight.SemiBold, 14, 18, 0.0),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 1.5.sp),
)

@Composable
fun PlinthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type) {
        CompositionLocalProvider(LocalContentColor provides TC.Text, content = content)
    }
}
