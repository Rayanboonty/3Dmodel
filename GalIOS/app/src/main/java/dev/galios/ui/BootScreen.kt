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
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.absoluteValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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
    onOpenVersion: (String) -> Unit = {},
    onVersionShown: (String) -> Unit = {},
    menu: CardMenuActions = CardMenuActions(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeviceRow(s, onSetup, onLogs, onRefresh, onPickPicture, onClearPicture)
        VersionCards(s, onBoot, onReturn, onSetup, onOpenVersion, onVersionShown, menu)
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
private fun VersionCards(s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit, onOpen: (String) -> Unit, onShown: (String) -> Unit, menu: CardMenuActions) {
    val pager = rememberPagerState(initialPage = versions.lastIndex) { versions.size }
    LaunchedEffect(pager.currentPage) { onShown(versions[pager.currentPage].number) }
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
                VersionCard(v, s, onBoot, onReturn, onSetup, menu) { onOpen(v.number) }
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
private fun VersionCard(v: IosVersion, s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit, menu: CardMenuActions, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    val open = s.status?.sessionOpen == true
    val press = rememberGlassPress()
    // The card's wallpaper is the backdrop its glass button refracts.
    val art = rememberLayerBackdrop()
    Box(
        Modifier.fillMaxWidth().height(400.dp).then(press.gesture).then(press.effect(1.03f)).clip(shape).border(1.dp, Color(0x22FFFFFF), shape)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onOpen),
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(art)) { CardBackground(v, s.wallpapers[v.number], Modifier.fillMaxSize()) }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Pill(
                        when {
                            !v.bootable -> "Coming soon"
                            open -> "Running now"
                            s.readyToBoot -> "Optimized for S25 Ultra"
                            else -> "Not set up yet"
                        },
                        when { !v.bootable -> Gal.Gray; open -> Gal.Teal; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
                    )
                }
                CardMenu(v, s, art, menu)
            }
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
                v.bootable && open -> GlassAction("Return to One UI", Icons.Rounded.Undo, art, onReturn)
                v.bootable && s.readyToBoot -> GlassAction("Boot iOS ${v.number}", Icons.Rounded.PowerSettingsNew, art, onBoot, enabled = s.busy == null)
                v.bootable -> GlassAction("Set up", Icons.Rounded.Tune, art, onSetup)
                else -> Spacer(Modifier.height(54.dp))
            }
        }
    }
}

/** What a card's menu can do (OneOS-style: details, status, the wallpaper, space, start fresh, delete). */
class CardMenuActions(
    val details: (String) -> Unit = {},
    val status: () -> Unit = {},
    val wallpaper: (String) -> Unit = {},
    val freeUpSpace: () -> Unit = {},
    val eraseData: () -> Unit = {},
    val deleteIos: () -> Unit = {},
)

/**
 * The card's menu: a small glass disc with "more" that opens a dark menu. The destructive choices (erase iOS's data,
 * delete iOS) ask first, with what goes and what stays.
 */
@Composable
private fun CardMenu(v: IosVersion, s: UiState, art: LayerBackdrop, menu: CardMenuActions) {
    var open by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<String?>(null) }
    val press = rememberGlassPress()
    val canChange = s.rootOk && s.busy == null && s.status?.sessionOpen != true
    Box {
        Box(
            Modifier.size(38.dp).then(press.gesture)
                .liquidBackdrop(art, CircleShape, press = press, blurDp = 6f, lensDp = 10f, tint = Color(0x33000000))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreHoriz, "More for iOS ${v.number}", tint = Color.White, modifier = Modifier.size(20.dp)) }
        androidx.compose.material3.DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            containerColor = Color(0xF21C1C1E), shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x29FFFFFF)),
        ) {
            @Composable
            fun item(text: String, icon: ImageVector, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) =
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text, style = Type.Body.copy(color = if (!enabled) Gal.Faint else if (danger) Gal.Rose else Gal.Text)) },
                    leadingIcon = { Icon(icon, null, tint = if (!enabled) Gal.Faint else if (danger) Gal.Rose else Gal.Text, modifier = Modifier.size(20.dp)) },
                    enabled = enabled, onClick = { open = false; onClick() },
                )
            item("System details", Icons.Rounded.Info) { menu.details(v.number) }
            item(if (s.wallpapers[v.number] == null) "Choose wallpaper" else "Change wallpaper", Icons.Rounded.Image) { menu.wallpaper(v.number) }
            if (v.bootable) {
                item("Phone status", Icons.Rounded.Tune) { menu.status() }
                item("Free up space", Icons.Rounded.CleaningServices, enabled = canChange && !s.phoneBuildRunning) { ask = "space" }
                item("Erase iOS data", Icons.Rounded.RestartAlt, enabled = canChange && s.status?.imageVersion != null, danger = true) { ask = "erase" }
                item("Delete iOS ${v.number}", Icons.Rounded.DeleteOutline, enabled = canChange && s.engineInstalled, danger = true) { ask = "delete" }
            }
        }
    }
    ask?.let { what ->
        val (title, body, action) = when (what) {
            "space" -> Triple("Free up space?", "Deletes the on-phone build's work files (Ubuntu, sources, firmware it read): often tens of GB. iOS, its image and its data stay.", menu.freeUpSpace)
            "erase" -> Triple("Erase iOS's data?", "iOS starts fresh at its next boot: its apps, settings and files are erased. The image stays, and One UI is untouched. This cannot be undone.", menu.eraseData)
            else -> Triple("Delete iOS ${v.number}?", "Deletes everything iOS uses on this phone: the boot engine, the runtime, the image and iOS's data and logs. One UI and its apps are untouched. To use iOS again, run Setup again. This cannot be undone.", menu.deleteIos)
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = null },
            containerColor = Gal.Paper, titleContentColor = Gal.Text, textContentColor = Gal.Dim, shape = RoundedCornerShape(28.dp),
            title = { Text(title, style = Type.Title) },
            text = { Text(body, style = Type.Body.copy(color = Gal.Dim)) },
            confirmButton = {
                androidx.compose.material3.TextButton({ ask = null; action() }) {
                    Text(if (what == "space") "Free up space" else if (what == "erase") "Erase" else "Delete", style = Type.Label.copy(color = if (what == "space") Gal.Text else Gal.Rose))
                }
            },
            dismissButton = { androidx.compose.material3.TextButton({ ask = null }) { Text("Cancel", style = Type.Label.copy(color = Gal.Dim)) } },
        )
    }
}

/**
 * A card's background: the owner's own wallpaper for this iOS when they chose one (on this phone, or sent by GalaxyIOS
 * Studio), cropped to the card and darkened at the bottom so the text and the button read; else the drawn art.
 */
@Composable
private fun CardBackground(v: IosVersion, wallpaper: Pair<String, Long>?, modifier: Modifier) {
    val bitmap = remember(wallpaper) { wallpaper?.let { (path, _) -> decodeScaled(path, 1440) } }
    if (bitmap == null) {
        CardArt(v, modifier)
        return
    }
    Box(modifier) {
        androidx.compose.foundation.Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, Color(0xB3000000)), startY = 0f, endY = size.height))
        }
    }
}

/** An action on a card: a capsule of liquid glass that refracts the card's wallpaper under it, and answers touch. */
@Composable
private fun GlassAction(text: String, icon: ImageVector, backdrop: LayerBackdrop, onClick: () -> Unit, enabled: Boolean = true) {
    val press = rememberGlassPress()
    Row(
        Modifier.fillMaxWidth().height(54.dp)
            .then(if (enabled) press.gesture else Modifier)
            .liquidBackdrop(backdrop, RoundedCornerShape(27.dp), press = press, blurDp = 6f, lensDp = 14f, tint = Color(0x40FFFFFF), aberration = true)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) Color.White else Gal.Faint
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, style = Type.Headline.copy(color = fg))
    }
}

/**
 * A version card popped out: its wallpaper (or art) large at the top with the numeral, then what this iOS is on this
 * phone and what can be done with it, on liquid glass.
 */
@Composable
fun VersionDetail(
    number: String,
    s: UiState,
    onClose: () -> Unit,
    onBoot: () -> Unit,
    onSetup: () -> Unit,
    onReturn: () -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
) {
    val v = versions.firstOrNull { it.number == number } ?: versions.last()
    val open = s.status?.sessionOpen == true
    val shape = RoundedCornerShape(36.dp)
    val art = rememberLayerBackdrop()
    Column(
        Modifier.padding(horizontal = 18.dp, vertical = 40.dp).fillMaxWidth().clip(shape).background(Gal.Paper)
            .border(1.dp, Brush.linearGradient(listOf(Color(0x80FFFFFF), Color(0x10FFFFFF), Color(0x40FFFFFF))), shape)
            // a tap inside the card stays inside it (the scrim behind closes)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Box(Modifier.fillMaxSize().layerBackdrop(art)) { CardBackground(v, s.wallpapers[v.number], Modifier.fillMaxSize()) }
            Text(
                v.number,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 6.dp),
                style = TextStyle(
                    fontSize = 120.sp, lineHeight = 112.sp, fontWeight = FontWeight.Black, letterSpacing = (-8).sp,
                    brush = Brush.verticalGradient(listOf(Color.White, v.bandB.copy(alpha = 0.85f))),
                ),
            )
            // close: a small glass disc over the wallpaper
            val closePress = rememberGlassPress()
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(40.dp)
                    .then(closePress.gesture)
                    .liquidBackdrop(art, CircleShape, press = closePress, blurDp = 6f, lensDp = 10f, tint = Color(0x33000000))
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Column(Modifier.padding(20.dp)) {
            Text("iOS ${v.number}", style = Type.Title.copy(fontSize = 26.sp))
            VSpace(6.dp)
            Pill(
                when {
                    !v.bootable -> "Coming soon"
                    open -> "Running now"
                    s.readyToBoot -> "Optimized for S25 Ultra"
                    else -> "Not set up yet"
                },
                when { !v.bootable -> Gal.Gray; open -> Gal.Teal; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
            )
            VSpace(14.dp)
            if (v.bootable) {
                DetailRow("Version", s.status?.imageVersion?.let { "$it (${s.status.imageBuild})" } ?: "Build or import its image in Setup")
                DetailRow("Image", s.status?.imageBytes?.takeIf { it > 0 }?.let { bytes(it) } ?: "Not added")
                DetailRow("Runtime", s.status?.runtimeVersion ?: "Not added")
                DetailRow("iPhone screen features", if (s.iphoneIdentity) "On" else "Off")
                DetailRow("Coming back", "Hold Volume Up and Volume Down for 3 seconds")
                VSpace(16.dp)
                when {
                    open -> GradientButton("Return to One UI", onReturn, icon = Icons.Rounded.Undo)
                    s.readyToBoot -> GradientButton("Boot iOS ${v.number}", onBoot, enabled = s.busy == null, icon = Icons.Rounded.PowerSettingsNew)
                    else -> GradientButton("Set up", onSetup, icon = Icons.Rounded.Tune)
                }
            } else {
                Text("Not available on this phone", style = Type.Body.copy(color = Gal.Dim))
            }
            VSpace(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (s.wallpapers[v.number] == null) "Choose wallpaper" else "Change wallpaper", onPickWallpaper, icon = Icons.Rounded.Image)
                if (s.wallpapers[v.number] != null) GlassButton("Remove", onClearWallpaper, icon = Icons.Rounded.HideImage)
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(title, style = Type.Body.copy(color = Gal.Dim), modifier = Modifier.width(150.dp))
        Text(value, style = Type.Body, modifier = Modifier.weight(1f))
    }
}

/**
 * The colour behind the whole app: the version's wallpaper (the owner's, when chosen) blurred deep and dimmed so text
 * reads on it, or its own colour world as soft washes; it crossfades as the cards are swiped. The liquid glass refracts it.
 */
@Composable
fun Ambient(number: String, s: UiState, modifier: Modifier) {
    androidx.compose.animation.Crossfade(number, modifier, animationSpec = androidx.compose.animation.core.tween(600), label = "ambient") { n ->
        val v = versions.firstOrNull { it.number == n } ?: versions.last()
        val wallpaper = s.wallpapers[n]
        val bitmap = remember(wallpaper) { wallpaper?.let { (path, _) -> decodeScaled(path, 360) } }
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
                        .blur(70.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(v.top)
                    drawCircle(Brush.radialGradient(listOf(v.bandB.copy(alpha = 0.55f), Color.Transparent), center = Offset(w * 0.9f, h * 0.08f), radius = w * 1.1f), radius = w * 1.1f, center = Offset(w * 0.9f, h * 0.08f))
                    drawCircle(Brush.radialGradient(listOf(v.bandA.copy(alpha = 0.40f), Color.Transparent), center = Offset(w * 0.05f, h * 0.55f), radius = w * 0.9f), radius = w * 0.9f, center = Offset(w * 0.05f, h * 0.55f))
                }
            }
            // dim it so white text reads, and deepen towards the bottom
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0x80000000))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)), startY = size.height * 0.35f, endY = size.height))
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
