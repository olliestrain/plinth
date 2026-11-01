package app.plinth.gym.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.times
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import app.plinth.gym.ui.theme.TC
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Brand mark: a block resting on its plinth. */
@Composable
fun PlinthMark(size: Dp, modifier: Modifier = Modifier, color: Color = TC.Accent) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 48f
        drawRoundRect(color, Offset(14 * u, 8 * u), Size(20 * u, 18 * u), CornerRadius(2 * u))
        drawRoundRect(color, Offset(6 * u, 30 * u), Size(36 * u, 10 * u), CornerRadius(2 * u))
    }
}

fun lerp(a: Color, b: Color, t: Float): Color = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)

/** Dot matrix, the signature visual: columns of weeks/days, each dot lit by intensity 0..1 (or -1 = absent). */
@Composable
fun DotMatrix(
    columns: List<List<Float>>, modifier: Modifier = Modifier, dot: Dp = 5.dp, gap: Dp = 8.dp,
    lit: Color = Color.White, dim: Color = Color.White.copy(alpha = 0.18f),
) {
    val cols = columns.size
    val rows = columns.maxOfOrNull { it.size } ?: 0
    Canvas(modifier.size(width = (cols * (dot + gap) - gap).coerceAtLeast(0.dp), height = (rows * (dot + gap) - gap).coerceAtLeast(0.dp))) {
        val d = dot.toPx(); val g = gap.toPx()
        columns.forEachIndexed { ci, col ->
            col.forEachIndexed { ri, v ->
                if (v < 0f) return@forEachIndexed
                val c = Offset(ci * (d + g) + d / 2, ri * (d + g) + d / 2)
                if (v > 0f) {
                    drawCircle(lit.copy(alpha = 0.5f + 0.5f * v), d * (0.62f + 0.14f * v), c)
                } else drawCircle(dim, d * 0.42f, c)
            }
        }
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp = 5.dp, color: Color = TC.Accent, track: Color = TC.CardHi, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val inset = s / 2
            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - s, this.size.height - s), style = Stroke(s))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

// ---------------- charts ----------------

data class ChartPoint(val label: String, val value: Float)

@Composable
fun LineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, color: Color = TC.Gold, format: (Float) -> String = { it.toInt().toString() }) {
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(900)) }
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val padL = 8.dp.toPx(); val padR = 8.dp.toPx(); val padT = 12.dp.toPx(); val padB = 8.dp.toPx()
        val w = size.width - padL - padR; val h = size.height - padT - padB
        val lo = points.minOf { it.value }; val hi = points.maxOf { it.value }
        val span = if (hi - lo < 1e-3f) 1f else hi - lo
        val min = lo - span * 0.15f; val rng = span * 1.3f
        for (i in 0..3) { // grid
            val y = padT + h * i / 3f
            drawLine(Color.White.copy(alpha = 0.06f), Offset(padL, y), Offset(padL + w, y), 1.dp.toPx())
        }
        fun pt(i: Int) = Offset(
            padL + if (points.size == 1) w / 2 else w * i / (points.size - 1f),
            padT + h * (1f - (points[i].value - min) / rng),
        )
        val path = Path(); val fill = Path()
        points.indices.forEach { i ->
            val p = pt(i)
            if (i == 0) { path.moveTo(p.x, p.y); fill.moveTo(p.x, padT + h); fill.lineTo(p.x, p.y) }
            else {
                val q = pt(i - 1); val cx = (q.x + p.x) / 2
                path.cubicTo(cx, q.y, cx, p.y, p.x, p.y); fill.cubicTo(cx, q.y, cx, p.y, p.x, p.y)
            }
        }
        fill.lineTo(pt(points.lastIndex).x, padT + h); fill.close()
        val visible = reveal.value
        clipRect(right = padL + w * visible + 2f) {
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), startY = padT, endY = padT + h))
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            points.indices.forEach { i ->
                val p = pt(i)
                drawCircle(TC.Bg, 5.dp.toPx(), p); drawCircle(color, 3.5.dp.toPx(), p)
            }
        }
    }
}

@Composable
fun BarChart(values: List<Pair<String, Float>>, modifier: Modifier = Modifier, color: Color = TC.Gold, highlightLast: Boolean = true) {
    val reveal = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { reveal.animateTo(1f, tween(700)) }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val hi = max(values.maxOf { it.second }, 1f)
        val n = values.size
        val gap = 8.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, (_, v) ->
            val h = (size.height * (v / hi)) * reveal.value
            val c = if (highlightLast && i == n - 1) color else color.copy(alpha = 0.35f)
            val x = i * (bw + gap)
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(bw, size.height), CornerRadius(8.dp.toPx()))
            if (v > 0) drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), CornerRadius(8.dp.toPx()))
        }
    }
}

fun DrawScope.dashedLine(color: Color, from: Offset, to: Offset) =
    drawLine(color, from, to, 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
