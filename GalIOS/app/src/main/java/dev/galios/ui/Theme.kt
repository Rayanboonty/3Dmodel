package dev.galios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Error
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
 * GalaxyIOS's design: a true black canvas (the owner's direction), frosted glass that is white at a few percent over it
 * with a white hairline where light would catch its edge, white text at three strengths, and one bold place per screen:
 * on Boot, the version cards, each with its own colour world. The primary action is a white pill with black text. State
 * colours (green, amber, red) only say state. Motion answers touch (a spring on press, the cards' swipe) and there is one
 * entrance moment, the cards'; nothing loops except the activity ring while work runs.
 */
object Gal {
    val Black = Color(0xFF000000)                 // the canvas
    val Glass = Color(0x14FFFFFF)                 // a frosted surface over the black
    val GlassStrong = Color(0x24FFFFFF)           // a control on glass
    val Edge = Color(0x24FFFFFF)                  // glass's hairline
    val Line = Color(0x1AFFFFFF)                  // dividers

    val Text = Color(0xFFFFFFFF)
    val Dim = Color(0x9EFFFFFF)                   // 62 %
    val Faint = Color(0x61FFFFFF)                 // 38 %

    val Mint = Color(0xFF32D74B)
    val Amber = Color(0xFFFFB340)
    val Rose = Color(0xFFFF6961)

    // Icon washes of list rows (state never comes from these).
    val Indigo = Color(0xFF8E8CFF)
    val Teal = Color(0xFF5AD8E6)
    val Purple = Color(0xFFD08CFF)
    val Gray = Color(0xFFA1A1A6)
    val Graphite = Color(0xFFC7C7CC)

    // Names other screens use for roles.
    val Bone = Black
    val Paper = Color(0xFF121214)                 // an opaque surface: dialogs, the log viewer
    val Fill = GlassStrong
    val Night = Color(0xFF0C0C0E)
    val NightRaised = GlassStrong
    val OnNight = Text
    val OnNightDim = Dim
    val Signal = Color(0xFFFFFFFF)
    val Ink = Glass
    val Raised = GlassStrong
    val Separator = Line
    val Blue = Text
    val Violet = Text
    val Cyan = Text
    val Scrim = Color(0xB3000000)
    val Accent: Brush = SolidColor(Text)
    fun glassFill(): Brush = SolidColor(Glass)
    fun glassEdge(): Brush = SolidColor(Edge)
    fun tint(c: Color): Brush = SolidColor(c.copy(alpha = 0.14f))
}

@Composable
fun GalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Color.Black, secondary = Color.White, background = Gal.Black, surface = Gal.Paper,
            surfaceVariant = Gal.GlassStrong, onBackground = Gal.Text, onSurface = Gal.Text, outline = Gal.Line, error = Gal.Rose,
        ),
        content = content,
    )
}

/** A press that answers: the element sinks a little on a spring while it is held. */
fun Modifier.pressable(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "press")
    this.scale(s)
}

/** Kept for callers: on black there is no shadow to cast. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.softShadow(shape: Shape, elevation: Dp = 14.dp): Modifier = this

/** The canvas: true black. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") drift: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Gal.Black), content = content)
}

/**
 * A glass surface in flat colour: a clear white tint and one even hairline. No gradients. [strength] scales it (1 for
 * panels, more for small controls).
 */
fun Modifier.liquidGlass(shape: Shape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = (0.09f * strength).coerceAtMost(0.22f)))
    .border(1.dp, Color.White.copy(alpha = (0.16f * strength).coerceAtMost(0.32f)), shape)
/** A liquid glass panel over the black, deeply rounded. */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 26.dp, padding: Dp = 18.dp, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(shape)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.10f)) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** An opaque dark card, for the rare surface that must not show what is behind it. */
@Composable
fun NightCard(modifier: Modifier = Modifier, radius: Dp = 30.dp, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Gal.Paper).border(1.dp, Gal.Edge, shape).padding(padding),
        content = content,
    )
}

/** The type scale. The version numerals are drawn by the cards themselves; this is the rest. */
object Type {
    private val F = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = F, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.8).sp)
    val Title = TextStyle(fontFamily = F, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.4).sp)
    val Headline = TextStyle(fontFamily = F, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text, letterSpacing = (-0.2).sp)
    val Body = TextStyle(fontFamily = F, fontSize = 16.sp, lineHeight = 22.sp, color = Gal.Text, letterSpacing = (-0.1).sp)
    val Caption = TextStyle(fontFamily = F, fontSize = 13.sp, lineHeight = 18.sp, color = Gal.Dim)
    val Label = TextStyle(fontFamily = F, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text, letterSpacing = (-0.1).sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = Gal.Dim, lineHeight = 16.sp)
}

/** A section's name above its panel, in sentence case. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.Label, modifier = modifier.padding(start = 6.dp, bottom = 10.dp))
}

/** The primary action: a white pill with black text. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 54.dp) {
    PillButton(text, onClick, modifier.fillMaxWidth(), enabled, icon, height, if (enabled) Color.White else Gal.GlassStrong, if (enabled) Color.Black else Gal.Faint)
}

/** Kept as a name: the same white pill. */
@Composable
fun SignalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 54.dp) =
    GradientButton(text, onClick, modifier, enabled, icon, height)

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, icon: ImageVector?, height: Dp, bg: Color, fg: Color) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.height(height).pressable(interaction, enabled).clip(RoundedCornerShape(height / 2)).background(bg)
            .clickable(interaction, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)) }
            Text(text, style = Type.Headline.copy(color = fg))
        }
    }
}

/** A secondary action: white text on frosted glass. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val fg = if (enabled) Gal.Text else Gal.Faint
    Row(
        modifier
            .height(42.dp).pressable(interaction, enabled).liquidGlass(RoundedCornerShape(21.dp), 1.4f)
            .clickable(interaction, null, enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = Type.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg))
    }
}

/** A round icon action on glass, its name under it. */
@Composable
fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 48.dp, showLabel: Boolean = true, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier.size(size).pressable(interaction, enabled).liquidGlass(CircleShape, 1.4f)
                .clickable(interaction, null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (enabled) Gal.Text else Gal.Faint, modifier = Modifier.size(size * 0.42f)) }
        if (showLabel) {
            VSpace(6.dp)
            Text(label, style = Type.Caption.copy(fontWeight = FontWeight.Medium))
        }
    }
}

enum class Mark { OK, WAIT, BAD, OFF }

/** One row of a list: a round wash with its symbol, the title and its value, the state at the end. */
@Composable
fun StatusRow(icon: ImageVector, title: String, value: String, mark: Mark, modifier: Modifier = Modifier, tile: Color = Gal.Graphite) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(tile.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tile, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body.copy(fontWeight = FontWeight.Medium))
            Text(value, style = Type.Caption, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        StateMark(mark)
    }
}

@Composable
fun StateMark(mark: Mark) {
    when (mark) {
        Mark.OK -> Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "In place", tint = Color.Black, modifier = Modifier.size(15.dp))
        }
        Mark.WAIT -> Icon(Icons.Rounded.Schedule, "Waiting", tint = Gal.Amber, modifier = Modifier.size(23.dp))
        Mark.BAD -> Icon(Icons.Rounded.Error, "Needs attention", tint = Gal.Rose, modifier = Modifier.size(23.dp))
        Mark.OFF -> Box(Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, Gal.Faint, CircleShape))
    }
}

/** The hairline between rows, inset to start under the text. */
@Composable
fun RowDivider(inset: Dp = 52.dp) = Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(Gal.Line))

@Composable
fun GalProgress(fraction: Float, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") onNight: Boolean = false) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "progress")
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Gal.GlassStrong)) {
        Box(Modifier.fillMaxWidth(f).height(5.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun Spinner(size: Dp = 20.dp, stroke: Dp = 2.5.dp, color: Color = Gal.Text) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(Modifier.size(size)) { drawArc(color, startAngle = angle, sweepAngle = 280f, useCenter = false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round)) }
}

/** A state chip: a dot and the word on a wash of the state's colour. */
@Composable
fun Pill(text: String, color: Color = Gal.Text, @Suppress("UNUSED_PARAMETER") onNight: Boolean = false) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.Caption.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
    }
}

/** A plain fade on first composition, for messages that arrive; the one choreographed entrance is the version cards'. */
@Composable
fun Appear(modifier: Modifier = Modifier, delayMillis: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, modifier, enter = fadeIn(tween(220, delayMillis))) { content() }
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
