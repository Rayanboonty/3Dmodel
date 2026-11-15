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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
 * GalIOS's design, "bone and ink": a warm bone canvas, paper cards that sit on it with a soft shadow, ink-black for the
 * hero card and the primary pill buttons, and one signal yellow kept for the single most important action. State colors
 * (green, amber, red) only say state. Motion is physical and short: a spring on every press, a rise on entrance, a
 * spring on the tab bar. Nothing loops except an activity ring while work is running.
 */
object Gal {
    // Surfaces.
    val Bone = Color(0xFFEEEBE5)          // the canvas
    val Paper = Color(0xFFFBF9F5)         // cards
    val Fill = Color(0xFFEDE9E2)          // quiet fills inside cards: secondary buttons, tracks, code
    val Line = Color(0xFFE2DDD5)          // hairlines
    val Night = Color(0xFF141414)         // the hero card, primary buttons, the tab bar
    val NightRaised = Color(0xFF262626)   // round buttons on the night card

    // The one accent, and the states.
    val Signal = Color(0xFFF4D541)
    val Mint = Color(0xFF2F9E5E)
    val Amber = Color(0xFFD27D25)
    val Rose = Color(0xFFD5473C)

    // Tile tints for the icons of list rows (washes, never actions).
    val Indigo = Color(0xFF5A57D6)
    val Teal = Color(0xFF2A93A8)
    val Purple = Color(0xFF9B4DD3)
    val Gray = Color(0xFF8E8880)
    val Graphite = Color(0xFF57534D)

    // Text on bone and paper, and on night.
    val Text = Color(0xFF141414)
    val Dim = Color(0xFF6E6961)
    val Faint = Color(0xFFA9A39A)
    val OnNight = Color(0xFFFFFFFF)
    val OnNightDim = Color(0x99FFFFFF)

    // Names the screens use for roles, mapped onto the palette.
    val Black = Bone                     // the canvas
    val Ink = Paper                      // a card
    val Raised = Fill
    val Separator = Line
    val Blue = Night                     // what can be tapped: ink
    val Violet = Night
    val Cyan = Night
    val Scrim = Color(0x66141414)
    val Accent: Brush = SolidColor(Night)
    fun glassFill(): Brush = SolidColor(Paper)
    fun glassEdge(): Brush = SolidColor(Color.Transparent)
    fun tint(c: Color): Brush = SolidColor(c.copy(alpha = 0.12f))

    /** The soft shadow every paper card casts on the bone. */
    val ShadowAmbient = Color(0x14402A10)
    val ShadowSpot = Color(0x1F402A10)
}

@Composable
fun GalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Gal.Night, onPrimary = Color.White, secondary = Gal.Night, background = Gal.Bone, surface = Gal.Paper,
            surfaceVariant = Gal.Fill, onBackground = Gal.Text, onSurface = Gal.Text, outline = Gal.Line, error = Gal.Rose,
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

/** A soft card shadow under [shape]. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 14.dp): Modifier =
    this.shadow(elevation, shape, clip = false, ambientColor = Gal.ShadowAmbient, spotColor = Gal.ShadowSpot)

/** The canvas: bone, nothing behind the content. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") drift: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Gal.Bone), content = content)
}

/** A paper card on the bone: deep rounding, a soft shadow, no border. [tint] washes it faintly with a state color. */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 28.dp, padding: Dp = 20.dp, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(shape)
            .clip(shape)
            .background(Gal.Paper)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = 0.08f)) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The ink card: the one dark surface on a screen, for what matters most there. */
@Composable
fun NightCard(modifier: Modifier = Modifier, radius: Dp = 32.dp, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.fillMaxWidth().softShadow(shape, 22.dp).clip(shape).background(Gal.Night).padding(padding),
        content = content,
    )
}

/** The type scale: a heavy display for the one big word on a screen, then title, headline, body, footnote, label. */
object Type {
    private val F = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = F, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, color = Gal.Text, letterSpacing = (-1.1).sp)
    val Title = TextStyle(fontFamily = F, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.5).sp)
    val Headline = TextStyle(fontFamily = F, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text, letterSpacing = (-0.2).sp)
    val Body = TextStyle(fontFamily = F, fontSize = 16.sp, lineHeight = 22.sp, color = Gal.Text, letterSpacing = (-0.1).sp)
    val Caption = TextStyle(fontFamily = F, fontSize = 13.sp, lineHeight = 18.sp, color = Gal.Dim)
    val Label = TextStyle(fontFamily = F, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Gal.Dim, letterSpacing = 0.9.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = Gal.Text, lineHeight = 16.sp)
}

/** A section's name above its card. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.Label, modifier = modifier.padding(start = 20.dp, bottom = 10.dp))
}

/** The primary action: an ink pill with white text. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 56.dp) {
    PillButton(text, onClick, modifier.fillMaxWidth(), enabled, icon, height, if (enabled) Gal.Night else Gal.Line, if (enabled) Color.White else Gal.Faint)
}

/** The accent action: the signal-yellow pill, for the one action a screen is about. */
@Composable
fun SignalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 56.dp) {
    PillButton(text, onClick, modifier.fillMaxWidth(), enabled, icon, height, if (enabled) Gal.Signal else Gal.NightRaised, if (enabled) Gal.Night else Gal.OnNightDim)
}

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

/** A secondary action: ink text on a quiet fill. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val fg = if (enabled) Gal.Text else Gal.Faint
    Row(
        modifier
            .height(42.dp).pressable(interaction, enabled).clip(RoundedCornerShape(21.dp)).background(Gal.Fill)
            .clickable(interaction, null, enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = Type.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg))
    }
}

/** A round action on the night card, its label under it. */
@Composable
fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).pressable(interaction, enabled).clip(CircleShape).background(Gal.NightRaised)
                .clickable(interaction, null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (enabled) Gal.OnNight else Gal.OnNightDim, modifier = Modifier.size(22.dp)) }
        VSpace(8.dp)
        Text(label, style = Type.Caption.copy(color = Gal.OnNightDim, fontWeight = FontWeight.Medium))
    }
}

enum class Mark { OK, WAIT, BAD, OFF }

/**
 * One row of a list: a round wash with its symbol, the title and its value, and the state at the end: an ink disc with a
 * check when it is in place, a clock while it waits, red when it is wrong, an empty ring when it does not apply yet.
 */
@Composable
fun StatusRow(icon: ImageVector, title: String, value: String, mark: Mark, modifier: Modifier = Modifier, tile: Color = Gal.Night) {
    Row(modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tile.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tile, modifier = Modifier.size(20.dp))
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
        Mark.OK -> Box(Modifier.size(26.dp).clip(CircleShape).background(Gal.Night), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "In place", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Mark.WAIT -> Icon(Icons.Rounded.Schedule, "Waiting", tint = Gal.Amber, modifier = Modifier.size(24.dp))
        Mark.BAD -> Icon(Icons.Rounded.Error, "Needs attention", tint = Gal.Rose, modifier = Modifier.size(24.dp))
        Mark.OFF -> Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, Gal.Line, CircleShape))
    }
}

/** The hairline between rows, inset to start under the text. */
@Composable
fun RowDivider(inset: Dp = 54.dp) = Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(Gal.Line))

/** A progress track; on the night card pass [onNight] for the signal fill. */
@Composable
fun GalProgress(fraction: Float, modifier: Modifier = Modifier, onNight: Boolean = false) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(if (onNight) Gal.NightRaised else Gal.Fill)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(CircleShape).background(if (onNight) Gal.Signal else Gal.Night))
    }
}

/** An activity ring, the only thing that loops, and only while work runs. */
@Composable
fun Spinner(size: Dp = 20.dp, stroke: Dp = 2.5.dp, color: Color = Gal.Night) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(Modifier.size(size)) { drawArc(color, startAngle = angle, sweepAngle = 280f, useCenter = false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round)) }
}

/** A state chip: a dot and the word, on a wash of the state's color. */
@Composable
fun Pill(text: String, color: Color = Gal.Night, onNight: Boolean = false) {
    Row(
        Modifier.clip(CircleShape).background(if (onNight) color.copy(alpha = 0.22f) else color.copy(alpha = 0.12f)).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.Caption.copy(color = if (onNight) Color.White else color, fontWeight = FontWeight.SemiBold))
    }
}

/** The entrance: a short fade and a small rise on a spring, [delayMillis] staggering a screen's blocks. */
@Composable
fun Appear(modifier: Modifier = Modifier, delayMillis: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        shown, modifier,
        enter = fadeIn(tween(260, delayMillis)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 12 },
    ) { content() }
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
