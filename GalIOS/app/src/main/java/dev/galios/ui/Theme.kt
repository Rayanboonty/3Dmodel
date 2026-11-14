package dev.galios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GalIOS's design: an OLED-black canvas, grouped cards in the dark greys iOS uses, One UI's generous rounding and
 * roomy headers. One accent (system blue) for what can be tapped; green, orange and red only for state. No gradients,
 * no glow, no borders, no looping motion.
 */
object Gal {
    // Canvas and surfaces (iOS dark grouped: system background, secondary grouped, tertiary grouped).
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF1C1C1E)
    val Raised = Color(0xFF2C2C2E)
    val Separator = Color(0xFF38383A)

    // The one accent and the three states (iOS dark system colors).
    val Blue = Color(0xFF0A84FF)
    val Mint = Color(0xFF30D158)
    val Amber = Color(0xFFFF9F0A)
    val Rose = Color(0xFFFF453A)
    val Indigo = Color(0xFF5E5CE6)
    val Teal = Color(0xFF40C8E0)
    val Purple = Color(0xFFBF5AF2)
    val Gray = Color(0xFF8E8E93)
    val Graphite = Color(0xFF636366)

    // Text: primary, secondary (60 %), tertiary (30 %) labels.
    val Text = Color(0xFFFFFFFF)
    val Dim = Color(0x99EBEBF5)
    val Faint = Color(0x4DEBEBF5)

    // Names the screens already use, mapped onto the palette above.
    val Violet = Blue
    val Cyan = Blue
    val Scrim = Color(0xCC000000)
    val Accent: Brush = SolidColor(Blue)
    fun glassFill(): Brush = SolidColor(Ink)
    fun glassEdge(): Brush = SolidColor(Color.Transparent)
    fun tint(c: Color): Brush = SolidColor(c.copy(alpha = 0.16f))
}

@Composable
fun GalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gal.Blue, secondary = Gal.Blue, background = Gal.Black, surface = Gal.Ink, surfaceVariant = Gal.Raised,
            onPrimary = Color.White, onBackground = Gal.Text, onSurface = Gal.Text, outline = Gal.Separator, error = Gal.Rose,
        ),
        content = content,
    )
}

/** The canvas: true black, nothing behind the content. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") drift: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Gal.Black), content = content)
}

/** A grouped card: the dark grey surface, One UI's rounding, no border. [tint] washes it faintly with a state color. */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 22.dp, padding: Dp = 18.dp, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Gal.Ink)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.10f)) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The type scale: iOS's sizes (large title 34, title 22, headline and body 17, footnote 13), tight tracking on big text. */
object Type {
    private val F = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = F, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.4).sp)
    val Title = TextStyle(fontFamily = F, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.3).sp)
    val Headline = TextStyle(fontFamily = F, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text, letterSpacing = (-0.2).sp)
    val Body = TextStyle(fontFamily = F, fontSize = 16.sp, lineHeight = 22.sp, color = Gal.Text, letterSpacing = (-0.1).sp)
    val Caption = TextStyle(fontFamily = F, fontSize = 13.sp, lineHeight = 18.sp, color = Gal.Dim)
    val Label = TextStyle(fontFamily = F, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = Gal.Dim, letterSpacing = 0.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = Gal.Text, lineHeight = 16.sp)
}

/** A section's name above its card, as iOS's grouped lists write it. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.Label, modifier = modifier.padding(start = 18.dp, bottom = 7.dp))
}

/** The primary action: a solid blue capsule. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 52.dp) {
    val shape = RoundedCornerShape(height / 2)
    // Disabled: the same blue, faded (as iOS dims a filled button), so it shows on the canvas, on cards and in dialogs.
    val fg = if (enabled) Color.White else Color.White.copy(alpha = 0.45f)
    Box(
        modifier.fillMaxWidth().height(height).clip(shape)
            .background(if (enabled) Gal.Blue else Gal.Blue.copy(alpha = 0.28f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = Type.Headline.copy(color = fg))
        }
    }
}

/** A secondary action: blue text on a faint blue capsule (iOS's tinted button). */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(20.dp)
    val fg = if (enabled) Gal.Blue else Gal.Faint
    Row(
        modifier
            .height(40.dp).clip(shape).background(if (enabled) Gal.Blue.copy(alpha = 0.16f) else Gal.Raised)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = Type.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg))
    }
}

enum class Mark { OK, WAIT, BAD, OFF }

private fun Mark.color() = when (this) { Mark.OK -> Gal.Mint; Mark.WAIT -> Gal.Amber; Mark.BAD -> Gal.Rose; Mark.OFF -> Gal.Gray }

/** One row of a grouped list: a small colored tile with its symbol, the title and its value, the state at the end. */
@Composable
fun StatusRow(icon: ImageVector, title: String, value: String, mark: Mark, modifier: Modifier = Modifier, tile: Color = Gal.Blue) {
    val state = mark.color()
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body)
            Text(value, style = Type.Caption, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            when (mark) { Mark.OK -> Icons.Rounded.CheckCircle; Mark.WAIT -> Icons.Rounded.Schedule; Mark.BAD -> Icons.Rounded.Error; Mark.OFF -> Icons.Rounded.RemoveCircleOutline },
            null, tint = state, modifier = Modifier.size(20.dp),
        )
    }
}

/** The hairline between rows, inset to start under the text (past the icon tile). */
@Composable
fun RowDivider(inset: Dp = 44.dp) = Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(Gal.Separator))

@Composable
fun GalProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Gal.Raised)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Gal.Blue))
    }
}

/** An activity ring: a thin grey arc turning, as the system's indicator does. */
@Composable
fun Spinner(size: Dp = 20.dp, stroke: Dp = 2.5.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(Modifier.size(size)) { drawArc(Gal.Dim, startAngle = angle, sweepAngle = 280f, useCenter = false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round)) }
}

/** A state label: the state's color on a faint wash of it. */
@Composable
fun Pill(text: String, color: Color = Gal.Blue) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.Caption.copy(color = color, fontWeight = FontWeight.SemiBold))
    }
}

/** A short fade on first composition; nothing moves or loops. */
@Composable
fun Appear(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, modifier, enter = fadeIn(tween(220))) { content() }
}

fun bytes(n: Long?): String = when {
    n == null || n < 0 -> "?"
    n >= 1L shl 30 -> "%.1f GB".format(n / 1073741824.0)
    n >= 1L shl 20 -> "%.0f MB".format(n / 1048576.0)
    n >= 1L shl 10 -> "%.0f KB".format(n / 1024.0)
    else -> "$n B"
}

@Composable fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
