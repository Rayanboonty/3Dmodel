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
import androidx.compose.material.icons.rounded.PhoneAndroid
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
 * An iOS version as a card, with its own wallpaper (the owner's choice, built in: res/drawable-nodpi/card_iosN.jpg).
 * Only the one OneiOS boots (27.0.1) can be booted here; the others say "Coming soon" and "Not available on this phone".
 */
private data class IosVersion(val number: String, val bootable: Boolean, val top: Color, val bottom: Color, val bandA: Color, val bandB: Color, val wallpaper: Int) {
    /** The card's one flat colour, behind the wallpaper. */
    val flat: Color get() = when (number) { "16" -> Color(0xFF2F5BDE); "18" -> Color(0xFFD9566B); "26" -> Color(0xFF169C8C); else -> Color(0xFF6A4BE0) }
}

private val versions = listOf(
    IosVersion("16", false, Color(0xFF06123A), Color(0xFF030616), Color(0xFF3B5BFF), Color(0xFF6FE3FF), dev.galios.R.drawable.card_ios16),
    IosVersion("18", false, Color(0xFF2A0716), Color(0xFF0D0208), Color(0xFFFF5A3C), Color(0xFFFF2E93), dev.galios.R.drawable.card_ios18),
    IosVersion("26", false, Color(0xFF032A2A), Color(0xFF010C0E), Color(0xFF19C9B6), Color(0xFFB8F5E8), dev.galios.R.drawable.card_ios26),
    IosVersion("27", true, Color(0xFF1B0A3D), Color(0xFF070312), Color(0xFFFF7A45), Color(0xFF9B5CFF), dev.galios.R.drawable.card_ios27),
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
    openVersion: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeviceRow(s, onSetup, onLogs, onRefresh)
        VersionCards(s, onBoot, onReturn, onSetup, onOpenVersion, onVersionShown, menu, openVersion)
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

/**
 * The phone's card, as it was before the compact row: the Galaxy S25 Ultra's photo (built in), its name and state, and
 * the three actions with their names. The photo floats on its own layer: it moves a little with the phone's tilt.
 */
@Composable
private fun DeviceRow(s: UiState, onSetup: () -> Unit, onLogs: () -> Unit, onRefresh: () -> Unit) {
    val open = s.status?.sessionOpen == true
    val tilt by rememberTilt()
    Glass(padding = 18.dp, radius = 30.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.device.label, style = Type.Title.copy(fontSize = 24.sp), maxLines = 1)
                VSpace(8.dp)
                Pill(
                    when { open -> "iOS running"; s.readyToBoot -> "Ready to boot"; else -> "Setup needed" },
                    when { open -> Gal.Teal; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
                )
                VSpace(6.dp)
                Text(
                    s.status?.imageVersion?.let { "iOS $it (${s.status.imageBuild}) on this phone" } ?: "iOS 27.0.1 (24A446)",
                    style = Type.Caption, maxLines = 1,
                )
                VSpace(16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundAction(Icons.Rounded.Tune, "Setup", onSetup, size = 46.dp)
                    RoundAction(Icons.Rounded.ListAlt, "Logs", onLogs, size = 46.dp)
                    RoundAction(Icons.Rounded.Refresh, "Refresh", onRefresh, enabled = !s.refreshing, size = 46.dp)
                }
            }
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(dev.galios.R.drawable.phone_s25_ultra), "Galaxy S25 Ultra",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(100.dp).height(176.dp).graphicsLayer {
                    translationX = tilt.x * 6.dp.toPx(); translationY = tilt.y * 6.dp.toPx()
                    rotationZ = tilt.x * 1.5f
                },
            )
        }
    }
}

/**
 * The version cards: swipe left and right; the neighbours peek in, smaller and dimmer, and grow as they come to the
 * centre. iOS 27 is in front when the screen opens, and the cards rise in once: the screen's one entrance moment.
 */
@Composable
private fun VersionCards(s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit, onOpen: (String) -> Unit, onShown: (String) -> Unit, menu: CardMenuActions, openVersion: String?) {
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
                // while its details are open the card is the details: it leaves here and grows into them
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    // the slot keeps the card's wallpaper while the card itself is away in its details: no empty cut-out
                    CardBackground(v, Modifier.fillMaxSize().clip(RoundedCornerShape(34.dp)))
                    androidx.compose.animation.AnimatedVisibility(
                        openVersion != v.number,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                    ) {
                        VersionCard(
                            v, s, onBoot, onReturn, onSetup, menu, Modifier.sharedContainer("card-${v.number}", this, RoundedCornerShape(34.dp)),
                            pageOffset = { (pager.currentPage - page) + pager.currentPageOffsetFraction },
                        ) { onOpen(v.number) }
                    }
                }
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
private fun VersionCard(
    v: IosVersion, s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit, menu: CardMenuActions, modifier: Modifier,
    pageOffset: () -> Float = { 0f }, onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(34.dp)
    val open = s.status?.sessionOpen == true
    val press = rememberGlassPress()
    val tilt by rememberTilt()
    // The card's wallpaper is the backdrop its glass button refracts.
    val art = rememberLayerBackdrop()
    Box(
        modifier.fillMaxWidth().height(400.dp).then(press.gesture).then(press.effect(1.03f)).clip(shape).border(1.dp, Color(0x22FFFFFF), shape)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onOpen),
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(art)) { CardBackground(v, Modifier.fillMaxSize(), pageOffset = pageOffset, tilt = { tilt }) }
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
            // The numeral and the title float above the wallpaper: a soft shadow under them, and they move a little
            // against the wallpaper's own movement when the phone tilts (the wallpaper is the deeper layer).
            Column(Modifier.graphicsLayer { translationX = -tilt.x * 5.dp.toPx(); translationY = -tilt.y * 5.dp.toPx() }) {
                Text(
                    v.number,
                    style = TextStyle(
                        fontSize = 150.sp, lineHeight = 140.sp, fontWeight = FontWeight.Black, letterSpacing = (-10).sp,
                        color = Color.White,
                        shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 10f), blurRadius = 36f),
                    ),
                )
                Text("iOS ${v.number}", style = Type.Title.copy(fontSize = 24.sp, shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 3f), blurRadius = 12f)))
                Text(
                    if (v.bootable) s.status?.imageVersion?.let { "Version $it (${s.status.imageBuild})" } ?: "Build or import its image in Setup"
                    else "Not available on this phone",
                    style = Type.Caption.copy(color = Color(0xE6FFFFFF), shadow = androidx.compose.ui.graphics.Shadow(Color(0x80000000), Offset(0f, 2f), blurRadius = 8f)), maxLines = 2,
                )
            }
            Spacer(Modifier.weight(0.5f))
            when {
                v.bootable && open -> GlassAction("Return to One UI", Icons.Rounded.Undo, art, onReturn)
                v.bootable && s.readyToBoot -> GlassAction("Boot iOS ${v.number}", Icons.Rounded.PowerSettingsNew, art, onBoot, enabled = s.busy == null)
                // the one button that opens Setup by growing into it (the Setup page takes its place, frame by frame)
                v.bootable -> GlassAction(
                    "Set up", Icons.Rounded.Tune, art, onSetup,
                    modifier = Modifier.sharedContainer("setup", LocalTabScope.current, RoundedCornerShape(27.dp), inOverlay = false),
                )
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
            Modifier.size(38.dp).then(press.gesture).then(press.effect(1.08f))
                .clip(CircleShape).background(Color(0xE6000000)).border(1.dp, Color(0x26FFFFFF), CircleShape)
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
 * A card's background: its iOS's wallpaper, built in, with depth. The wallpaper is a deeper layer than the card: it is
 * drawn a little larger than the card and moves less than the card when the cards are swiped ([pageOffset], -1..1
 * across a swipe) and the other way when the phone tilts ([tilt], -1..1 each axis), as iOS's perspective wallpapers
 * do. A light dim keeps the white text readable.
 */
@Composable
private fun CardBackground(v: IosVersion, modifier: Modifier, pageOffset: () -> Float = { 0f }, tilt: () -> Offset = { Offset.Zero }) {
    Box(modifier.background(v.flat)) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(v.wallpaper), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // 1.4x: a fifth of the card spare on every side, more than the parallax ever moves it (an edge never shows)
                scaleX = 1.4f; scaleY = 1.4f
                val t = tilt()
                translationX = (pageOffset().coerceIn(-1f, 1f) * size.width * 0.10f + t.x * 10.dp.toPx()).coerceIn(-size.width * 0.18f, size.width * 0.18f)
                translationY = (t.y * 10.dp.toPx()).coerceIn(-size.height * 0.18f, size.height * 0.18f)
            },
        )
        Box(Modifier.fillMaxSize().background(Color(0x40000000)))
    }
}

/** An action on a card: a plain black capsule with white text and icon, that swells a little while pressed. */
@Composable
private fun GlassAction(text: String, icon: ImageVector, backdrop: LayerBackdrop, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val press = rememberGlassPress()
    Row(
        modifier.fillMaxWidth().height(54.dp)
            .then(if (enabled) press.gesture else Modifier).then(press.effect(1.03f))
            .clip(RoundedCornerShape(27.dp)).background(Color(0xF0000000)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(27.dp))
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
    modifier: Modifier = Modifier,
) {
    val v = versions.firstOrNull { it.number == number } ?: versions.last()
    val open = s.status?.sessionOpen == true
    val shape = RoundedCornerShape(36.dp)
    val art = rememberLayerBackdrop()
    Column(
        Modifier.padding(horizontal = 18.dp, vertical = 40.dp).then(modifier).fillMaxWidth().clip(shape).background(Gal.Paper)
            .border(1.dp, Color(0x33FFFFFF), shape)
            // a tap inside the card stays inside it (the scrim behind closes)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Box(Modifier.fillMaxSize().layerBackdrop(art)) { CardBackground(v, Modifier.fillMaxSize()) }
            Text(
                v.number,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 6.dp),
                style = TextStyle(
                    fontSize = 120.sp, lineHeight = 112.sp, fontWeight = FontWeight.Black, letterSpacing = (-8).sp,
                    color = Color.White,
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
 * The colour behind the whole app: the card in front's wallpaper, blurred deep and dimmed so text reads on it; it
 * crossfades as the cards are swiped. The liquid glass refracts it.
 */
@Composable
fun Ambient(number: String, @Suppress("UNUSED_PARAMETER") s: UiState, modifier: Modifier) {
    androidx.compose.animation.Crossfade(number, modifier, animationSpec = androidx.compose.animation.core.tween(600), label = "ambient") { n ->
        val v = versions.firstOrNull { it.number == n } ?: versions.last()
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(v.flat, Color.Black, 0.72f))) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(v.wallpaper), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
                    .blur(70.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
            )
            // dim it, flat, so white text reads
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        }
    }
}

/**
 * The phone's tilt, -1..1 on each axis, smoothed: how far it leans from where it was held when the screen opened (that
 * centre follows slowly, so holding it at any angle settles back to 0). From the game rotation vector (no compass, no
 * permission); zero where the phone has none (the JVM screenshot tests).
 */
@Composable
fun rememberTilt(): androidx.compose.runtime.State<Offset> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    androidx.compose.runtime.DisposableEffect(context) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR)
        var centre: FloatArray? = null
        val rot = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                android.hardware.SensorManager.getRotationMatrixFromVector(rot, e.values)
                android.hardware.SensorManager.getOrientation(rot, angles)           // azimuth, pitch, roll (radians)
                val c = centre ?: floatArrayOf(angles[1], angles[2]).also { centre = it }
                c[0] += (angles[1] - c[0]) * 0.02f; c[1] += (angles[2] - c[1]) * 0.02f
                val target = Offset(((angles[2] - c[1]) / 0.35f).coerceIn(-1f, 1f), ((angles[1] - c[0]) / 0.35f).coerceIn(-1f, 1f))
                val now = tilt.value
                tilt.value = Offset(now.x + (target.x - now.x) * 0.25f, now.y + (target.y - now.y) * 0.25f)
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
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
            Icon(Icons.Rounded.PhoneAndroid, "Your phone", tint = Gal.Dim, modifier = Modifier.size(30.dp))
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
