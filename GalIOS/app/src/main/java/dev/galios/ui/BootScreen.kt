package dev.galios.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.UiState
import kotlin.math.absoluteValue

/**
 * An iOS version as a card. Only the one OneiOS boots (27.0.1) can be booted here; the others say "Coming soon" and
 * "Not available on this phone", nothing more. Each card's colours and art are GalaxyIOS's own.
 */
private data class IosVersion(val number: String, val bootable: Boolean, val top: Color, val bottom: Color, val bandA: Color, val bandB: Color)

private val versions = listOf(
    IosVersion("16", false, Color(0xFF06123A), Color(0xFF030616), Color(0xFF3B5BFF), Color(0xFF6FE3FF)),
    IosVersion("18", false, Color(0xFF2A0716), Color(0xFF0D0208), Color(0xFFFF5A3C), Color(0xFFFF2E93)),
    IosVersion("26", false, Color(0xFF032A2A), Color(0xFF010C0E), Color(0xFF19C9B6), Color(0xFFB8F5E8)),
    IosVersion("27", true, Color(0xFF1B0A3D), Color(0xFF070312), Color(0xFFFF7A45), Color(0xFF9B5CFF)),
)

@Composable
fun BootScreen(
    s: UiState,
    onBoot: () -> Unit,
    onSetup: () -> Unit,
    onReturn: () -> Unit,
    onLogs: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onPickPicture: () -> Unit = {},
    onClearPicture: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeviceRow(s, onSetup, onLogs, onRefresh, onPickPicture, onClearPicture)
        VersionCards(s, onBoot, onReturn, onSetup)
        s.bootBlocker?.takeIf { s.status?.sessionOpen != true }?.let { blocker ->
            Appear {
                Glass(padding = 18.dp, tint = Gal.Amber) {
                    Text(blocker, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                    VSpace(12.dp)
                    GlassButton("Open Setup", onSetup, icon = Icons.Rounded.Tune)
                }
            }
        }
        Column {
            SectionHeader("What's in place")
            Readiness(s)
        }
        Column {
            SectionHeader("Coming back to One UI")
            Glass(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Gal.Graphite.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.VolumeUp, null, tint = Gal.Graphite, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Hold Volume Up and Volume Down", style = Type.Body.copy(fontWeight = FontWeight.Medium))
                        Text("For 3 seconds. One UI comes back with your apps and data as you left them.", style = Type.Caption)
                    }
                }
            }
            s.status?.lastOutcome?.let { o ->
                Text(
                    when {
                        o.startsWith("inplace") -> "The last session came back to One UI in place."
                        o.startsWith("failsafe") -> "The last session ended with a safety restart. Start temporary root again."
                        o.startsWith("reboot") -> "The last session ended with a restart. Start temporary root again."
                        else -> o
                    },
                    style = Type.Caption, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
                )
            }
        }
    }
}

/** The phone in one compact row: its picture, its name and state, and three round actions. */
@Composable
private fun DeviceRow(s: UiState, onSetup: () -> Unit, onLogs: () -> Unit, onRefresh: () -> Unit, onPick: () -> Unit, onClear: () -> Unit) {
    val open = s.status?.sessionOpen == true
    Glass(padding = 12.dp, radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroPicture(s.heroPicture, onPick, onClear, Modifier.width(52.dp).height(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.device.label, style = Type.Headline, maxLines = 1)
                VSpace(4.dp)
                Pill(
                    when { open -> "iOS running"; s.readyToBoot -> "Ready to boot"; else -> "Setup needed" },
                    when { open -> Gal.Teal; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
                )
            }
            RoundAction(Icons.Rounded.Tune, "Setup", onSetup, size = 40.dp, showLabel = false)
            Spacer(Modifier.width(6.dp))
            RoundAction(Icons.Rounded.ListAlt, "Logs", onLogs, size = 40.dp, showLabel = false)
            Spacer(Modifier.width(6.dp))
            RoundAction(Icons.Rounded.Refresh, "Refresh", onRefresh, enabled = !s.refreshing, size = 40.dp, showLabel = false)
        }
    }
}

/**
 * The version cards: swipe left and right; the neighbours peek in, smaller and dimmer, and grow as they come to the
 * centre. iOS 27 is in front when the screen opens, and the cards rise in once: the screen's one entrance moment.
 */
@Composable
private fun VersionCards(s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit) {
    val pager = rememberPagerState(initialPage = versions.lastIndex) { versions.size }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 180f)) }
    Column(Modifier.graphicsLayer { translationY = rise.value * 60f * density; alpha = 1f - rise.value }) {
        HorizontalPager(
            pager, contentPadding = PaddingValues(horizontal = 34.dp), pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val v = versions[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(Modifier.graphicsLayer {
                val k = 1f - offset * 0.10f
                scaleX = k; scaleY = k
                alpha = 1f - offset * 0.45f
            }) {
                VersionCard(v, s, onBoot, onReturn, onSetup)
            }
        }
        VSpace(12.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            versions.forEachIndexed { i, _ ->
                val on = i == pager.currentPage
                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(CircleShape).background(if (on) Color.White else Gal.Faint))
            }
        }
    }
}

@Composable
private fun VersionCard(v: IosVersion, s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    val open = s.status?.sessionOpen == true
    Box(Modifier.fillMaxWidth().height(400.dp).clip(shape).border(1.dp, Color(0x22FFFFFF), shape)) {
        CardArt(v, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Pill(
                when {
                    !v.bootable -> "Coming soon"
                    open -> "Running now"
                    s.readyToBoot -> "Optimized for S25 Ultra"
                    else -> "Not set up yet"
                },
                when { !v.bootable -> Gal.Gray; open -> Gal.Teal; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
            )
            Spacer(Modifier.weight(1f))
            Text(
                v.number,
                style = TextStyle(
                    fontSize = 150.sp, lineHeight = 140.sp, fontWeight = FontWeight.Black, letterSpacing = (-10).sp,
                    brush = Brush.verticalGradient(listOf(Color.White, v.bandB.copy(alpha = 0.85f))),
                ),
            )
            Text("iOS ${v.number}", style = Type.Title.copy(fontSize = 24.sp))
            Text(
                if (v.bootable) s.status?.imageVersion?.let { "Version $it (${s.status.imageBuild})" } ?: "Build or import its image in Setup"
                else "Not available on this phone",
                style = Type.Caption.copy(color = Color(0xCCFFFFFF)), maxLines = 2,
            )
            Spacer(Modifier.weight(0.5f))
            when {
                v.bootable && open -> GradientButton("Return to One UI", onReturn, icon = Icons.Rounded.Undo)
                v.bootable && s.readyToBoot -> GradientButton("Boot iOS ${v.number}", onBoot, enabled = s.busy == null, icon = Icons.Rounded.PowerSettingsNew)
                v.bootable -> GradientButton("Set up", onSetup, icon = Icons.Rounded.Tune)
                else -> Spacer(Modifier.height(52.dp))
            }
        }
    }
}

/** A card's own art: a deep vertical wash, two broad bands of light sweeping across it, and a soft glow. Drawn, not a picture. */
@Composable
private fun CardArt(v: IosVersion, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(v.top, v.bottom)))
        drawCircle(Brush.radialGradient(listOf(v.bandB.copy(alpha = 0.35f), Color.Transparent), center = Offset(w * 0.85f, h * 0.18f), radius = w * 0.7f),
            radius = w * 0.7f, center = Offset(w * 0.85f, h * 0.18f))
        fun band(y0: Float, y1: Float, lift: Float, thick: Float, brush: Brush) {
            val p = Path().apply {
                moveTo(-w * 0.1f, h * y0)
                cubicTo(w * 0.3f, h * (y0 - lift), w * 0.6f, h * (y1 + lift), w * 1.1f, h * y1)
                lineTo(w * 1.1f, h * y1 + h * thick)
                cubicTo(w * 0.6f, h * (y1 + lift) + h * thick, w * 0.3f, h * (y0 - lift) + h * thick, -w * 0.1f, h * y0 + h * thick)
                close()
            }
            drawPath(p, brush)
        }
        band(0.30f, 0.52f, 0.18f, 0.16f, Brush.horizontalGradient(listOf(v.bandA.copy(alpha = 0.0f), v.bandA.copy(alpha = 0.75f), v.bandB.copy(alpha = 0.55f))))
        band(0.46f, 0.70f, 0.10f, 0.08f, Brush.horizontalGradient(listOf(v.bandB.copy(alpha = 0.55f), v.bandA.copy(alpha = 0.35f), Color.Transparent)))
        // the lower part darkens so the text and the button read on it
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)), startY = h * 0.45f, endY = h))
    }
}

@Composable
private fun Readiness(s: UiState) {
    Glass(padding = 16.dp) {
        val c = s.checks
        StatusRow(Icons.Rounded.Key, "Root", when {
            c == null -> "Checking…"
            c.root -> "Active" + (if (s.rootVia == "helper") ", lent by GalaxyIOS Studio" else "") +
                if (c.busybox == null || c.resetprop == null) ", tools missing" else ""
            else -> "Not active: start temporary root"
        }, when { c == null -> Mark.WAIT; c.root && c.busybox != null && c.resetprop != null -> Mark.OK; c.root -> Mark.WAIT; else -> Mark.BAD }, tile = Gal.Graphite)
        RowDivider()
        StatusRow(Icons.Rounded.Extension, "Boot engine", when {
            s.engine == null -> "OneOS is not installed"
            !s.engine.hasIos -> "OneOS ${s.engine.versionName} has no iOS engine"
            else -> "OneOS ${s.engine.versionName}" + if (s.engineInstalled) ", installed" else ", not installed yet"
        }, when { s.engine?.hasIos == true && s.engineInstalled -> Mark.OK; s.engine?.hasIos == true -> Mark.WAIT; else -> Mark.BAD }, tile = Gal.Indigo)
        RowDivider()
        StatusRow(Icons.Rounded.Inventory2, "OneiOS runtime", s.status?.runtimeVersion?.let { v ->
            v + (s.release?.takeIf { it.version != v }?.let { ", ${it.version} is out" } ?: "")
        } ?: "Not added", if (s.status?.runtimeVersion != null) Mark.OK else Mark.WAIT, tile = Gal.Teal)
        RowDivider()
        StatusRow(Icons.Rounded.Layers, "iOS image", s.status?.imageVersion?.let { "iOS $it (${s.status.imageBuild}), ${bytes(s.status.imageBytes)}" } ?: "Not added",
            if (s.status?.imageVersion != null) Mark.OK else Mark.WAIT, tile = Gal.Purple)
        RowDivider()
        StatusRow(Icons.Rounded.Bolt, "Boot check", when {
            s.status == null -> "Runs once the engine is installed"
            s.status.ready -> "Passed"
            else -> s.status.notReadyReason ?: "Not passed"
        }, when { s.status?.ready == true -> Mark.OK; s.status == null -> Mark.OFF; else -> Mark.WAIT }, tile = Gal.Amber)
    }
}

/**
 * The phone's picture: the owner's own, when they chose one on this phone (tap to choose another, press and hold to go
 * back to the drawing), else the drawing (tap to choose a picture). Decoded once per saved file, scaled to its size.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HeroPicture(picture: Pair<String, Long>?, onPick: () -> Unit, onClear: () -> Unit, modifier: Modifier) {
    val bitmap = remember(picture) { picture?.let { (path, _) -> decodeScaled(path, 480) } }
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onPick, onLongClick = { if (bitmap != null) onClear() }, onClickLabel = "Choose your picture"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap, "Your picture", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        } else {
            PhoneArt(Modifier.fillMaxSize())
        }
    }
}

/** A picture file decoded at about [maxSide] pixels on its longer side; null if it is not one. */
private fun decodeScaled(path: String, maxSide: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return bmp.asImageBitmap()
}
