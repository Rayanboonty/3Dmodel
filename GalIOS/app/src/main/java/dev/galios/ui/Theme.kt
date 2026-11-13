package dev.galios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.R

/** The design system: true-black canvas, violet→cyan accent, frosted glass, soft motion. */
object Gal {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF05060B)
    val Violet = Color(0xFF7C56FF)
    val Cyan = Color(0xFF19C6E6)
    val Mint = Color(0xFF3DE0A6)
    val Amber = Color(0xFFFFB64A)
    val Rose = Color(0xFFFF5C7A)

    val Text = Color(0xFFF3F5FF)
    val Dim = Color(0xFFA7ADCC)
    val Faint = Color(0xFF6B7196)

    // Glass surfaces: a faint top-lit fill and a hairline gradient edge over the imagery.
    val GlassTop = Color(0x14FFFFFF)
    val GlassBottom = Color(0x08FFFFFF)
    val EdgeTop = Color(0x33FFFFFF)
    val EdgeBottom = Color(0x0AFFFFFF)
    val Scrim = Color(0xB3000000)

    val Accent = Brush.linearGradient(listOf(Violet, Cyan))
    fun glassFill() = Brush.verticalGradient(listOf(GlassTop, GlassBottom))
    fun glassEdge() = Brush.verticalGradient(listOf(EdgeTop, EdgeBottom))
    fun tint(c: Color) = Brush.verticalGradient(listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.06f)))
}

@Composable
fun GalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gal.Violet, secondary = Gal.Cyan, background = Gal.Black, surface = Gal.Ink,
            onPrimary = Color.White, onBackground = Gal.Text, onSurface = Gal.Text, error = Gal.Rose,
        ),
        content = content,
    )
}

/** Black canvas with the nebula art drifting slowly behind everything. */
@Composable
fun Backdrop(modifier: Modifier = Modifier, drift: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "drift")
    val dy by if (drift) {
        t.animateFloat(-18f, 18f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse), label = "dy")
    } else remember { mutableStateOf(0f) }
    val scale by if (drift) {
        t.animateFloat(1.06f, 1.14f, infiniteRepeatable(tween(32000, easing = LinearEasing), RepeatMode.Reverse), label = "sc")
    } else remember { mutableStateOf(1.08f) }
    Box(modifier.fillMaxSize().background(Gal.Black)) {
        Image(
            painterResource(R.drawable.bg_nebula), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationY = dy
            },
        )
        // A soft black vignette so edges and text keep contrast.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x00000000), Color(0x99000000)))))
        content()
    }
}

/** A frosted-glass card: faint fill, hairline gradient edge, deep rounding. Reads as glass over the art. */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 26.dp, padding: Dp = 20.dp, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Gal.Scrim.copy(alpha = 0.38f))
            .background(tint?.let { Gal.tint(it) } ?: Gal.glassFill())
            .border(1.dp, Gal.glassEdge(), shape)
            .padding(padding),
        content = content,
    )
}

object Type {
    private val F = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = F, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.6).sp)
    val Title = TextStyle(fontFamily = F, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text, letterSpacing = (-0.2).sp)
    val Headline = TextStyle(fontFamily = F, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text)
    val Body = TextStyle(fontFamily = F, fontSize = 15.sp, color = Gal.Text, lineHeight = 21.sp)
    val Caption = TextStyle(fontFamily = F, fontSize = 13.sp, color = Gal.Dim, lineHeight = 18.sp)
    val Label = TextStyle(fontFamily = F, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gal.Faint, letterSpacing = 1.6.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = Gal.Text, lineHeight = 16.sp)
}

/** Primary action: a gradient pill with a slow sheen moving across it. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 58.dp) {
    val shape = RoundedCornerShape(height / 2)
    val t = rememberInfiniteTransition(label = "sheen")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "x")
    Box(
        modifier
            .fillMaxWidth().height(height).clip(shape)
            .background(if (enabled) Gal.Accent else Brush.linearGradient(listOf(Gal.Violet.copy(alpha = 0.3f), Gal.Cyan.copy(alpha = 0.3f))))
            .then(if (enabled) Modifier.drawBehind {
                val w = size.width
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent), startX = x * w - w * 0.3f, endX = x * w + w * 0.3f),
                )
            } else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.6f), modifier = Modifier.size(21.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = Type.Headline.copy(fontSize = 17.sp, color = Color.White.copy(alpha = if (enabled) 1f else 0.6f)))
        }
    }
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(44.dp).clip(shape).background(Gal.glassFill()).border(1.dp, Gal.glassEdge(), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) Gal.Text else Gal.Faint, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = Type.Body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (enabled) Gal.Text else Gal.Faint))
    }
}

enum class Mark { OK, WAIT, BAD, OFF }

@Composable
fun StatusRow(icon: ImageVector, title: String, value: String, mark: Mark, modifier: Modifier = Modifier) {
    val tint = when (mark) { Mark.OK -> Gal.Mint; Mark.WAIT -> Gal.Amber; Mark.BAD -> Gal.Rose; Mark.OFF -> Gal.Faint }
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body.copy(fontWeight = FontWeight.Medium))
            Text(value, style = Type.Caption, maxLines = 2)
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(tint).blur(0.5.dp))
    }
}

@Composable
fun GalProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(Gal.Accent))
    }
}

/** A turning gradient ring. */
@Composable
fun Spinner(size: Dp = 22.dp, stroke: Dp = 3.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
    Canvas(Modifier.size(size)) { drawArc(Gal.Accent, startAngle = angle, sweepAngle = 270f, useCenter = false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round)) }
}

@Composable
fun Pill(text: String, color: Color = Gal.Cyan) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.3f), CircleShape).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.Caption.copy(color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp))
    }
}

/** Fades and lifts its content in on first composition — the quiet entrance used across the app. */
@Composable
fun Appear(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, modifier, enter = fadeIn(tween(420)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 10 }) { content() }
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
