package app.rowan.gym.ui.components

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
import app.rowan.gym.ui.theme.TC
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The 3x3 grid mark. `lit` cells (0..9) glow lime, the rest stay graphite. Used on login, wizard finale, etc. */
@Composable
fun RowanLogo(size: Dp, lit: Float = 9f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val gap = this.size.minDimension * 0.07f
        val cell = (this.size.minDimension - gap * 2) / 3f
        // diagonal build-up order -> reads like a rowan assembling
        val order = listOf(0, 1, 3, 2, 4, 6, 5, 7, 8)
        for (r in 0..2) for (c in 0..2) {
            val idx = order.indexOf(r * 3 + c)
            val a = (lit - idx).coerceIn(0f, 1f)
            val x = c * (cell + gap); val y = r * (cell + gap)
            val base = if ((r + c) % 2 == 0) TC.Lime else Color(0xFFB8F23A)
            val col = lerp(TC.CardHi, base, a)
            val s = 0.55f + 0.45f * a
            val cs = cell * s
            drawRoundRect(col, Offset(x + (cell - cs) / 2, y + (cell - cs) / 2), Size(cs, cs), CornerRadius(cell * 0.28f))
        }
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
                    if (v >= 0.99f) drawCircle(lit.copy(alpha = 0.25f), d * 1.15f, c)
                    drawCircle(lit.copy(alpha = 0.5f + 0.5f * v), d * (0.62f + 0.14f * v), c)
                } else drawCircle(dim, d * 0.42f, c)
            }
        }
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp = 8.dp, color: Color = TC.Lime, track: Color = TC.CardHi, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
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

/** Living grid background for the wizard. Fill grows with [progress]; [pulse] changes send a ripple from [origin]. */
@Composable
fun GridBackground(progress: Float, pulse: Int, origin: Offset, modifier: Modifier = Modifier) {
    val cells = remember { List(600) { Random(it * 7919L).nextFloat() } }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(pulse) { ripple.snapTo(0f); ripple.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val inf = rememberInfiniteTransition(label = "twinkle")
    val t by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val p by androidx.compose.animation.core.animateFloatAsState(progress, tween(900), label = "prog")
    Canvas(modifier.fillMaxSize()) {
        val cell = 34.dp.toPx(); val gap = 6.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val reach = max(size.width, size.height) * ripple.value * 1.1f
        for (r in 0 until rows) for (c in 0 until cols) {
            val th = cells[(r * 23 + c * 7) % cells.size]
            val on = th < p
            val center = Offset(c * cell + cell / 2, r * cell + cell / 2)
            val dist = (center - origin).getDistance()
            val wave = if (ripple.value < 1f) max(0f, 1f - abs(dist - reach) / 140f) else 0f
            val tw = (sin(t + th * 12f) + 1f) / 2f
            val alpha = (if (on) 0.10f + 0.10f * tw else 0.035f) + wave * 0.35f
            val col = if (on || wave > 0f) TC.Lime.copy(alpha = min(alpha, 0.6f)) else Color.White.copy(alpha = alpha)
            drawRoundRect(col, Offset(c * cell + gap / 2, r * cell + gap / 2), Size(cell - gap, cell - gap), CornerRadius(8.dp.toPx()))
        }
        // vignette so content stays readable
        drawRect(Brush.verticalGradient(listOf(TC.Bg.copy(alpha = 0.92f), TC.Bg.copy(alpha = 0.55f), TC.Bg.copy(alpha = 0.95f))))
    }
}

/** One-shot confetti made of small squares. Change [trigger] to fire. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, colors: List<Color> = listOf(TC.Lime, TC.Sky, TC.Violet, TC.Rose, TC.Ember, Color.White)) {
    val anim = remember { Animatable(1f) }
    val parts = remember(trigger) {
        val r = Random(trigger * 31L + 5)
        List(70) { Triple(r.nextFloat() * 2 * PI.toFloat(), 0.25f + r.nextFloat() * 0.75f, r.nextInt(colors.size)) } to
            List(70) { r.nextFloat() * 360f }
    }
    LaunchedEffect(trigger) { if (trigger > 0) { anim.snapTo(0f); anim.animateTo(1f, tween(1800, easing = LinearEasing)) } }
    if (anim.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val o = Offset(size.width / 2, size.height * 0.42f)
        val t = anim.value
        val ease = 1f - (1f - t) * (1f - t)
        parts.first.forEachIndexed { i, (ang, spd, ci) ->
            val dist = spd * size.minDimension * 0.75f * ease
            val x = o.x + cos(ang) * dist
            val y = o.y + sin(ang) * dist + 520f * t * t * spd
            val s = 9.dp.toPx() * (1f - t * 0.5f)
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            drawRoundRect(colors[ci].copy(alpha = alpha), Offset(x - s / 2, y - s / 2), Size(s, s), CornerRadius(s * 0.3f))
        }
    }
}

// ---------------- charts ----------------

data class ChartPoint(val label: String, val value: Float)

@Composable
fun LineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, color: Color = TC.Lime, format: (Float) -> String = { it.toInt().toString() }) {
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
fun BarChart(values: List<Pair<String, Float>>, modifier: Modifier = Modifier, color: Color = TC.Lime, highlightLast: Boolean = true) {
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
