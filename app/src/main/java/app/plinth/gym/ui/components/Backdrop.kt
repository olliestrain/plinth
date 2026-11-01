package app.plinth.gym.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.plinth.gym.ui.theme.TC
import kotlin.random.Random

/** A painted colour field: three soft blobs + film grain. One per kind of day. */
enum class Field(val base: Color, val blobs: List<Triple<Color, Offset, Float>>) {
    EMBER(Color(0xFF0C0708), listOf(
        Triple(Color(0xFFC2253A), Offset(0.10f, 0.20f), 0.62f),
        Triple(Color(0xFF5B3A9E), Offset(0.95f, 0.42f), 0.55f),
        Triple(Color(0xFFE0703A), Offset(0.55f, 0.02f), 0.42f),
    )),
    DEEP_WATER(Color(0xFF060C10), listOf(
        Triple(Color(0xFF1B6E86), Offset(0.12f, 0.34f), 0.62f),
        Triple(Color(0xFF2A3E9E), Offset(0.95f, 0.06f), 0.52f),
        Triple(Color(0xFF3FA8A0), Offset(0.60f, 0.52f), 0.36f),
    )),
    AMBER(Color(0xFF0D0905), listOf(
        Triple(Color(0xFFC4741F), Offset(0.10f, 0.46f), 0.62f),
        Triple(Color(0xFF8E2F1E), Offset(0.95f, 0.08f), 0.55f),
        Triple(Color(0xFFD9A441), Offset(0.50f, 0.0f), 0.38f),
    )),
    VIOLET(Color(0xFF0A0A12), listOf(
        Triple(Color(0xFF5B3A9E), Offset(0.10f, 0.22f), 0.62f),
        Triple(Color(0xFFB0386F), Offset(0.95f, 0.54f), 0.50f),
        Triple(Color(0xFF2E4BC0), Offset(0.50f, 0.62f), 0.36f),
    )),
    MOSS(Color(0xFF080C09), listOf(
        Triple(Color(0xFF3F6B3A), Offset(0.08f, 0.38f), 0.62f),
        Triple(Color(0xFF7A7A22), Offset(0.95f, 0.04f), 0.46f),
        Triple(Color(0xFF1F5A52), Offset(0.62f, 0.62f), 0.38f),
    )),
    SLATE(Color(0xFF0A0B0D), listOf(
        Triple(Color(0xFF3A4350), Offset(0.10f, 0.30f), 0.62f),
        Triple(Color(0xFF2B2F3A), Offset(0.95f, 0.56f), 0.52f),
        Triple(Color(0xFF4A3F58), Offset(0.55f, 0.04f), 0.34f),
    ));

    companion object {
        /** Template colour index -> its field, so a workout keeps its look everywhere. */
        fun forColor(i: Int) = entries[((i % 6) + 6) % 6]
    }
}

private val GRAIN = Random(7).let { r -> List(1400) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }

fun DrawScope.paintField(field: Field) {
    drawRect(field.base)
    val w = size.width; val h = size.height; val m = maxOf(w, h)
    field.blobs.forEach { (c, at, rad) ->
        val center = Offset(w * at.x, h * at.y)
        val r = m * rad * 0.62f
        drawCircle(
            Brush.radialGradient(
                0f to c.copy(alpha = 0.80f), 0.45f to c.copy(alpha = 0.40f), 1f to Color.Transparent,
                center = center, radius = r,
            ),
            radius = r, center = center,
        )
    }
    // settle into the page toward the bottom so content stays legible
    drawRect(Brush.verticalGradient(0.30f to Color.Transparent, 0.85f to field.base.copy(alpha = 0.94f)))
    GRAIN.forEach { (x, y, a) -> drawCircle(Color.White.copy(alpha = 0.02f + 0.035f * a), 0.9f, Offset(x * w, y * h)) }
}

fun Modifier.backdrop(field: Field): Modifier = this.drawBehind { paintField(field) }

@Composable
fun Backdrop(field: Field, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.backdrop(field))
}
