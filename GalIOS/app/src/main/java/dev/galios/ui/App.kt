package dev.galios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.galios.UiState
import dev.galios.core.LogInfo

enum class Tab(val title: String, val icon: ImageVector) {
    BOOT("Boot", Icons.Rounded.PowerSettingsNew), SETUP("Setup", Icons.Rounded.Tune), LOGS("Logs", Icons.Rounded.ListAlt)
}

class AppActions(
    val setup: SetupActions,
    val boot: () -> Unit,
    val returnToOneUi: () -> Unit,
    val loadLogs: () -> Unit,
    val openLog: (LogInfo) -> Unit,
    val closeLog: () -> Unit,
    val shareLog: (String, String) -> Unit,
    val dismissMessage: () -> Unit,
    val update: () -> Unit = {},
    val dismissUpdate: () -> Unit = {},
    /** Saves a log, whole, to Downloads/GalIOS. */
    val saveLog: (LogInfo) -> Unit = {},
    /** Saves the log in the viewer to Downloads/GalIOS. */
    val saveOpenLog: () -> Unit = {},
    /** Chooses the owner's own picture for the Boot card; [clearPicture] goes back to the drawing. */
    val pickPicture: () -> Unit = {},
    val clearPicture: () -> Unit = {},
    /** Chooses (or clears) the owner's wallpaper for one iOS card ("16", "18", "26", "27"). */
    val pickWallpaper: (String) -> Unit = {},
    val clearWallpaper: (String) -> Unit = {},
    /** The card menu: free the build's work files; delete iOS (the whole stage) from this phone. */
    val freeUpSpace: () -> Unit = {},
    val deleteIos: () -> Unit = {},
)

@Composable
fun GalIosApp(s: UiState, a: AppActions, startTab: Tab = Tab.BOOT, showConfirm: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var confirm by remember { mutableStateOf(showConfirm) }
    var about by remember { mutableStateOf(false) }
    GalTheme {
        Backdrop {
            // The page fills the screen and scrolls under the tab bar, which floats on top over a soft bone fade:
            // no hard edge where the content meets the bar. The bottom padding lets the last card clear the bar.
            // The page is the backdrop the liquid glass refracts (Backdrop records it, black included, into a layer).
            val page = rememberLayerBackdrop()
            var detail by remember { mutableStateOf<String?>(null) }
            var lastDetail by remember { mutableStateOf("27") }
            // the colour behind everything: the card in front on Boot (its wallpaper, blurred deep), iOS 27 elsewhere
            var ambient by remember { mutableStateOf("27") }
            androidx.activity.compose.BackHandler(enabled = detail != null) { detail = null }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(page).background(Gal.Black)) {
                Ambient(if (tab == Tab.BOOT) ambient else "27", s, Modifier.fillMaxSize())
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                        .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 118.dp),
                ) {
                    Header(tab, s) { about = true }
                    VSpace(24.dp)
                    s.update?.let { UpdateCard(it.version, a.update, a.dismissUpdate); VSpace(14.dp) }
                    s.message?.let { MessageCard(it.text, it.error, a.dismissMessage); VSpace(14.dp) }
                    // A tab expands into place: it grows from a little smaller at the top on a spring and fades in, while the
                    // one it replaces swells away and fades.
                    androidx.compose.animation.AnimatedContent(
                        tab, label = "tab",
                        transitionSpec = {
                            val grow = spring<Float>(dampingRatio = 0.78f, stiffness = 320f)
                            (scaleIn(grow, initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 0f)) + fadeIn(tween(240))) togetherWith
                                (scaleOut(spring(stiffness = 500f), targetScale = 1.04f, transformOrigin = TransformOrigin(0.5f, 0f)) + fadeOut(tween(140)))
                        },
                    ) { t ->
                        Column {
                            when (t) {
                                Tab.BOOT -> BootScreen(s, onBoot = { confirm = true }, onSetup = { tab = Tab.SETUP }, onReturn = a.returnToOneUi, onLogs = { tab = Tab.LOGS; a.loadLogs() }, onRefresh = a.setup.refresh, onPickPicture = a.pickPicture, onClearPicture = a.clearPicture, onOpenVersion = { v -> lastDetail = v; detail = v }, onVersionShown = { ambient = it },
                                    menu = CardMenuActions(
                                        details = { v -> lastDetail = v; detail = v }, status = { tab = Tab.SETUP },
                                        wallpaper = a.pickWallpaper, freeUpSpace = a.freeUpSpace, eraseData = a.setup.resetIosData, deleteIos = a.deleteIos,
                                    ))
                                Tab.SETUP -> SetupScreen(s, a.setup)
                                Tab.LOGS -> LogsScreen(s.logs, a.loadLogs, a.openLog, a.saveLog)
                            }
                        }
                    }
                }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    NavBar(tab, page) { t -> tab = t; if (t == Tab.LOGS) a.loadLogs() }
                }
                // A version card pops out into its detail over the page, which frosts behind it.
                AnimatedVisibility(detail != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
                    Box(
                        Modifier.fillMaxSize()
                            .drawBackdrop(page, shape = { androidx.compose.ui.graphics.RectangleShape }, effects = { blur(24.dp.toPx()) }, highlight = null, shadow = null,
                                onDrawSurface = { drawRect(Color(0x99000000)) })
                            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { detail = null },
                    )
                }
                AnimatedVisibility(
                    detail != null, modifier = Modifier.align(Alignment.Center),
                    enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 300f), initialScale = 0.7f) + fadeIn(tween(160)),
                    exit = scaleOut(spring(stiffness = 600f), targetScale = 0.85f) + fadeOut(tween(140)),
                ) {
                    VersionDetail(
                        lastDetail, s, onClose = { detail = null },
                        onBoot = { detail = null; confirm = true }, onSetup = { detail = null; tab = Tab.SETUP },
                        onReturn = { detail = null; a.returnToOneUi() },
                        onPickWallpaper = { a.pickWallpaper(lastDetail) }, onClearWallpaper = { a.clearWallpaper(lastDetail) },
                    )
                }
            }
            AnimatedVisibility(s.switching, enter = fadeIn(), exit = fadeOut()) { Switching() }
        }
        if (confirm) BootConfirm(s, onCancel = { confirm = false }, onBoot = { confirm = false; a.boot() })
        if (about) About { about = false }
        s.openLog?.let { (title, text) ->
            Dialog(a.closeLog, DialogProperties(usePlatformDefaultWidth = false)) {
                LogViewer(title, text, a.closeLog, onShare = { a.shareLog(title, text) }, onSave = a.saveOpenLog, status = s.message?.text)
            }
        }
    }
}

/** One UI's roomy header (the title sits low, with air above it) in iOS's large-title type. */
@Composable
private fun Header(tab: Tab, s: UiState, onAbout: () -> Unit) {
    Column(Modifier.padding(top = 36.dp, start = 4.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tab.title, style = Type.Display, modifier = Modifier.weight(1f))
            if (s.refreshing || s.busy != null) { Box(Modifier.padding(bottom = 10.dp)) { Spinner() }; Spacer(Modifier.width(14.dp)) }
            IconButton(onAbout, Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Info, "About", tint = Gal.Blue, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            when (tab) {
                Tab.BOOT -> "Swipe the cards to choose an iOS"
                Tab.SETUP -> "Everything iOS needs on this phone"
                Tab.LOGS -> "What each session wrote"
            },
            style = Type.Body.copy(color = Gal.Dim),
        )
        s.busy?.let { Text("$it…", style = Type.Caption.copy(color = Gal.Blue), modifier = Modifier.padding(top = 6.dp)) }
    }
}

/**
 * The tab bar: a floating capsule of liquid glass (Kyant's Backdrop) that really refracts the page scrolling under it.
 * The selected tab is a glass lens: tap a tab and it springs there; hold it and it swells, its edge bends the light
 * with chromatic aberration and an inner shadow; drag it and it follows the finger, stretching with its speed, and
 * settles on the nearest tab when let go.
 */
@Composable
private fun NavBar(tab: Tab, page: LayerBackdrop, onTab: (Tab) -> Unit) {
    val count = Tab.entries.size
    val bar = 64.dp
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(tab.ordinal.toFloat()) }
    val press = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragSpeed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(tab) { if (!dragging) pos.animateTo(tab.ordinal.toFloat(), spring(dampingRatio = 0.7f, stiffness = 380f)) }
    val barPress = rememberGlassPress()
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(bar)
                .then(barPress.gesture)
                .liquidBackdrop(page, RoundedCornerShape(32.dp), press = barPress, blurDp = 8f, lensDp = 22f, tint = Color(0x66121212)),
        ) {
            val slotPx = with(LocalDensity.current) { maxWidth.toPx() } / count
            val slot = maxWidth / count
            fun settle() {
                dragging = false
                dragSpeed = 0f
                val target = pos.value.let { kotlin.math.round(it) }.coerceIn(0f, count - 1f)
                scope.launch { press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
                scope.launch { pos.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 340f)) }
                Tab.entries[target.toInt()].let { if (it != tab) onTab(it) }
            }
            // the lens on the selected tab
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = pos.value * slotPx
                        val p = press.value
                        val swell = lerp(1f, 1.22f, p)
                        val v = (if (dragging) dragSpeed else pos.velocity / 12f).coerceIn(-0.25f, 0.25f)
                        scaleX = swell * (1f + kotlin.math.abs(v) * 0.6f)
                        scaleY = swell * (1f - kotlin.math.abs(v) * 0.25f)
                    }
                    .width(slot).height(bar).padding(6.dp)
                    .drawBackdrop(
                        backdrop = page,
                        shape = { RoundedCornerShape(26.dp) },
                        effects = {
                            vibrancy()
                            if (glassShaders) {
                                val p = press.value
                                lens((8f + 6f * p).dp.toPx(), (10f + 10f * p).dp.toPx(), chromaticAberration = true)
                            }
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * press.value) },
                        shadow = { Shadow(alpha = press.value) },
                        innerShadow = { press.value.takeIf { it > 0.01f }?.let { InnerShadow(radius = 8.dp * it, alpha = it) } },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.16f * (1f - press.value) + 0.04f)) },
                    ),
            )
            Row(
                Modifier.fillMaxWidth().height(bar).pointerInput(count) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            scope.launch { press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                        },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                    ) { change, dx ->
                        change.consume()
                        dragSpeed = dx / slotPx
                        scope.launch { pos.snapTo((pos.value + dx / slotPx).coerceIn(-0.15f, count - 0.85f)) }
                    }
                },
            ) {
                for (t in Tab.entries) {
                    val on = t == tab
                    Row(
                        Modifier.width(slot).height(bar).clip(RoundedCornerShape(25.dp))
                            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onTab(t) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, t.title, tint = if (on) Color.White else Gal.Dim, modifier = Modifier.size(21.dp))
                        if (on) {
                            Spacer(Modifier.width(7.dp))
                            Text(t.title, style = Type.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun UpdateCard(version: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Appear {
        Glass(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("GalaxyIOS $version", style = Type.Headline)
                    Text("Ready to install", style = Type.Caption)
                }
                GlassButton("Update", onUpdate)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Close, "Later", tint = Gal.Gray, modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onDismiss))
            }
        }
    }
}

@Composable
private fun MessageCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    val c = if (error) Gal.Rose else Gal.Mint
    Appear {
        Glass(padding = 16.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.TaskAlt, null, tint = c, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(text, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Close, "Dismiss", tint = Gal.Gray, modifier = Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onDismiss))
            }
        }
    }
}

@Composable
fun BootConfirm(s: UiState, onCancel: () -> Unit, onBoot: () -> Unit) {
    var ack by remember { mutableStateOf(false) }
    Dialog(onCancel) {
        Column(Modifier.clip(RoundedCornerShape(32.dp)).background(Gal.Paper).padding(24.dp)) {
            Text("Boot iOS 27?", style = Type.Title)
            VSpace(12.dp)
            Bullet("One UI stops in about 40 seconds. iOS's first screen comes about a minute and a half later; the first boot of a new image can take several minutes more.")
            Bullet("To come back, hold Volume Up + Volume Down for 3 seconds.")
            Bullet("If the way back fails its safety checks, the phone restarts into One UI. Your temporary root is gone after a restart.")
            Bullet("Experimental, and never run on a ${s.device.label} before. Keep the phone plugged in.")
            VSpace(8.dp)
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { ack = !ack }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(ack, { ack = it }, colors = CheckboxDefaults.colors(checkedColor = Gal.Blue, uncheckedColor = Gal.Gray, checkmarkColor = Color.White))
                Text("I understand it is experimental", style = Type.Body)
            }
            VSpace(14.dp)
            GradientButton("Boot now", onBoot, enabled = ack, height = 50.dp)
            VSpace(4.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Not now", style = Type.Headline.copy(color = Gal.Blue, fontWeight = FontWeight.Normal), modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onCancel).padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Gal.Dim))
        Spacer(Modifier.width(12.dp))
        Text(text, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = Gal.Dim))
    }
}

@Composable
private fun Switching() {
    Box(Modifier.fillMaxSize().background(Gal.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(36.dp)) {
            Spinner(size = 36.dp, stroke = 3.dp)
            VSpace(28.dp)
            Text("Starting iOS", style = Type.Title)
            VSpace(10.dp)
            Text(
                "One UI will stop in about 40 seconds and the screen goes dark for a moment. Leave the phone plugged in.\n\nTo come back: Volume Up + Volume Down for 3 seconds.",
                style = Type.Caption, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun About(onClose: () -> Unit) {
    Dialog(onClose) {
        Column(Modifier.clip(RoundedCornerShape(32.dp)).background(Gal.Paper).padding(24.dp)) {
            Text("GalaxyIOS", style = Type.Title)
            VSpace(10.dp)
            Text(
                "A personal front end for booting iOS 27 next to One UI on a Galaxy S25 Ultra or S24 Ultra.\n\n" +
                    "The boot itself is done by OneiOS (akshaynexus/OneiOS: xnuhost, a source-built XNU running as a process on One UI's kernel) " +
                    "through the OneOS app's iOS 27 engine (akshaynexus/OneOS, Apache License 2.0), which GalaxyIOS reads from the installed OneOS app. " +
                    "GalaxyIOS ships no part of OneOS, OneiOS or iOS.\n\n" +
                    "Not affiliated with Apple or Samsung. iOS is a trademark of Apple Inc.; Galaxy is a trademark of Samsung Electronics.",
                style = Type.Caption,
            )
            VSpace(16.dp)
            GradientButton("Close", onClose, height = 50.dp)
        }
    }
}
