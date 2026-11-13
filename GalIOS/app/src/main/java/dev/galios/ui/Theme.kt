package dev.galios.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Gal {
    val Ink = Color(0xFF060814)
    val Ink2 = Color(0xFF0C1030)
    val Violet = Color(0xFF6C4BFF)
    val Cyan = Color(0xFF19C6E6)
    val Mint = Color(0xFF34E0A1)
    val Amber = Color(0xFFFFB547)
    val Rose = Color(0xFFFF5C7A)
    val Text = Color(0xFFF4F6FF)
    val Dim = Color(0xFF9AA3C7)
    val Faint = Color(0xFF5E6790)
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x22FFFFFF)
    val Edge = Color(0x1FFFFFFF)
    val Accent = Brush.linearGradient(listOf(Violet, Cyan))
    val AccentDim = Brush.linearGradient(listOf(Violet.copy(alpha = 0.35f), Cyan.copy(alpha = 0.35f)))
}

@Composable
fun GalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gal.Violet, secondary = Gal.Cyan, background = Gal.Ink, surface = Gal.Ink2,
            onPrimary = Color.White, onBackground = Gal.Text, onSurface = Gal.Text, error = Gal.Rose,
        ),
        content = content,
    )
}

/** The backdrop: deep ink with two soft light fields, violet above and cyan below. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Gal.Ink2, Gal.Ink, Gal.Ink)))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Gal.Violet.copy(alpha = 0.40f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.08f), radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = Offset(size.width * 0.15f, size.height * 0.08f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Gal.Cyan.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.85f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width * 0.95f, size.height * 0.85f),
            )
        }
        content()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Gal.Glass)
            .border(1.dp, Gal.Edge, RoundedCornerShape(28.dp))
            .padding(padding),
        content = content,
    )
}

object Type {
    val Display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Gal.Text, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text)
    val Headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Gal.Text)
    val Body = TextStyle(fontSize = 15.sp, color = Gal.Text, lineHeight = 21.sp)
    val Caption = TextStyle(fontSize = 13.sp, color = Gal.Dim, lineHeight = 18.sp)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gal.Faint, letterSpacing = 1.2.sp)
    val Mono = TextStyle(fontSize = 11.5.sp, color = Gal.Text, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 16.sp)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.Label, modifier = modifier.padding(start = 6.dp, bottom = 10.dp, top = 6.dp))
}

/** The main action: a gradient pill. Dimmed and inert when [enabled] is false. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, height: Dp = 60.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (enabled) Gal.Accent else Gal.AccentDim)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.6f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = Type.Headline.copy(fontSize = 18.sp, color = Color.White.copy(alpha = if (enabled) 1f else 0.6f)))
        }
    }
}

/** A quiet action: a glass pill. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Gal.GlassStrong)
            .border(1.dp, Gal.Edge, RoundedCornerShape(23.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) Gal.Text else Gal.Faint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = Type.Body.copy(fontWeight = FontWeight.Medium, color = if (enabled) Gal.Text else Gal.Faint))
        }
    }
}

enum class Mark { OK, WAIT, BAD, OFF }

/** A row of the readiness list: an icon in a tinted circle, a title and its value, and its state. */
@Composable
fun StatusRow(icon: ImageVector, title: String, value: String, mark: Mark, modifier: Modifier = Modifier) {
    val tint = when (mark) { Mark.OK -> Gal.Mint; Mark.WAIT -> Gal.Amber; Mark.BAD -> Gal.Rose; Mark.OFF -> Gal.Faint }
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body.copy(fontWeight = FontWeight.Medium))
            Text(value, style = Type.Caption, maxLines = 2)
        }
        Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
    }
}

/** A thin gradient progress bar. */
@Composable
fun GalProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Gal.GlassStrong)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Gal.Accent))
    }
}

/** A turning gradient ring, for work in progress. */
@Composable
fun Spinner(size: Dp = 22.dp, stroke: Dp = 3.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(Modifier.size(size)) {
        drawArc(Gal.Accent, startAngle = angle, sweepAngle = 270f, useCenter = false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun Pill(text: String, color: Color = Gal.Cyan) {
    Text(
        text, style = Type.Caption.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

fun bytes(n: Long?): String = when {
    n == null || n < 0 -> "?"
    n >= 1L shl 30 -> "%.1f GB".format(n / 1073741824.0)
    n >= 1L shl 20 -> "%.0f MB".format(n / 1048576.0)
    n >= 1L shl 10 -> "%.0f KB".format(n / 1024.0)
    else -> "$n B"
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Centered(content: @Composable () -> Unit) = Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
