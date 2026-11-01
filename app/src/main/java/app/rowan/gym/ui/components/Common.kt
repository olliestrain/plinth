package app.rowan.gym.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rowan.gym.ui.theme.Mono
import app.rowan.gym.ui.theme.TC

/** Springy press feedback for anything tappable. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    return this.scale(s).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun TCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = TC.Card,
    radius: Dp = 28.dp,
    border: Color = TC.Outline.copy(alpha = 0.6f),
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val m = modifier.clip(shape).background(color).border(BorderStroke(1.dp, border), shape)
    Box((if (onClick != null) m.pressable(onClick) else m).padding(padding)) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val bg = if (enabled) Brush.horizontalGradient(listOf(TC.Lime, Color(0xFFB8F23A))) else SolidColor(TC.CardHi)
    Row(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(bg)
            .pressable(onClick, enabled).padding(horizontal = 26.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = Color.Black); Box(Modifier.size(8.dp)) }
        Text(text, color = if (enabled) Color.Black else TC.Faint, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) =
    PrimaryButton(text, onClick, modifier, enabled, icon)

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TC.Text, icon: ImageVector? = null) {
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, TC.Outline, RoundedCornerShape(24.dp))
            .pressable(onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = color); Box(Modifier.size(8.dp)) }
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun IconPill(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = TC.Text, bg: Color = TC.CardHi, size: Dp = 44.dp) {
    Box(modifier.size(size).clip(CircleShape).background(bg).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun TChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TC.Lime) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape).background(if (selected) color else TC.Card)
            .border(1.dp, if (selected) color else TC.Outline, shape).pressable(onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Color.Black else TC.Dim, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = TC.Dim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun BigNumber(value: String, unit: String? = null, modifier: Modifier = Modifier, size: Int = 40, color: Color = TC.Text) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, fontFamily = Mono, lineHeight = (size + 2).sp)
        if (unit != null) Text(unit, color = TC.Dim, fontSize = (size * 0.38f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 5.dp, bottom = (size * 0.1f).dp))
    }
}

/** Compact numeric/text input used inside set rows. */
@Composable
fun MiniField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "",
    decimal: Boolean = false, numeric: Boolean = true, width: Dp = 64.dp, accent: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = if (accent) TC.Lime else TC.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(TC.Lime),
        keyboardOptions = KeyboardOptions(keyboardType = if (!numeric) KeyboardType.Text else if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
        decorationBox = { inner ->
            Box(Modifier.width(width).heightIn(min = 44.dp).clip(shape).background(TC.CardHi).border(1.dp, TC.Outline, shape), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) Text(hint, color = TC.Faint, fontSize = 17.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                inner()
            }
        },
    )
}

/** Animated 0..1 value that springs when it changes. */
@Composable
fun springFloat(target: Float, label: String = "f"): Float =
    animateFloatAsState(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = label).value

@Composable
fun SpacerH(h: Int) = Box(Modifier.size(h.dp))

@Composable
fun Column16(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

suspend fun Animatable<Float, *>.pop() {
    snapTo(0f)
    animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Box(Modifier.size(110.dp)) // room for the floating tab bar
    }
}

/** Multi-line / single-line free text input in the Rowan style. */
@Composable
fun TextBox(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    singleLine: Boolean = false, minHeight: Dp = 52.dp, accent: Color = TC.Text,
) {
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine, cursorBrush = SolidColor(TC.Lime),
        textStyle = TextStyle(color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(TC.CardHi).border(1.dp, TC.Outline, shape).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (value.isEmpty()) Text(hint, color = TC.Faint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                inner()
            }
        },
    )
}
