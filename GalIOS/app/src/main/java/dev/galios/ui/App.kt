package dev.galios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
)

@Composable
fun GalIosApp(s: UiState, a: AppActions, startTab: Tab = Tab.BOOT, showConfirm: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var confirm by remember { mutableStateOf(showConfirm) }
    var about by remember { mutableStateOf(false) }
    GalTheme {
        Backdrop {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 22.dp),
                ) {
                    Header(tab, s) { about = true }
                    VSpace(16.dp)
                    s.update?.let { UpdateCard(it.version, a.update, a.dismissUpdate); VSpace(14.dp) }
                    s.message?.let { MessageCard(it.text, it.error, a.dismissMessage); VSpace(14.dp) }
                    Crossfade(tab, animationSpec = tween(320), label = "tab") { t ->
                        Column {
                            when (t) {
                                Tab.BOOT -> BootScreen(s, onBoot = { confirm = true }, onSetup = { tab = Tab.SETUP }, onReturn = a.returnToOneUi)
                                Tab.SETUP -> SetupScreen(s, a.setup)
                                Tab.LOGS -> LogsScreen(s.logs, a.loadLogs, a.openLog, a.saveLog)
                            }
                        }
                    }
                }
                NavBar(tab) { t -> tab = t; if (t == Tab.LOGS) a.loadLogs() }
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

@Composable
private fun Header(tab: Tab, s: UiState, onAbout: () -> Unit) {
    Appear {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Gal.Accent))
                    Spacer(Modifier.width(7.dp))
                    Text("GALIOS", style = Type.Label.copy(color = Gal.Dim))
                }
                Text(tab.title, style = Type.Display)
            }
            if (s.refreshing || s.busy != null) { Spinner(); Spacer(Modifier.width(12.dp)) }
            IconButton(onAbout, Modifier.size(42.dp).clip(CircleShape).background(Gal.glassFill()).border(1.dp, Gal.glassEdge(), CircleShape)) {
                Icon(Icons.Rounded.Info, "About", tint = Gal.Text, modifier = Modifier.size(20.dp))
            }
        }
    }
    s.busy?.let { Text("$it…", style = Type.Caption.copy(color = Gal.Cyan), modifier = Modifier.padding(top = 4.dp)) }
}

/** The bottom bar: a dark glass pill with a gradient indicator that slides to the selected tab. */
@Composable
private fun NavBar(tab: Tab, onTab: (Tab) -> Unit) {
    val count = Tab.entries.size
    val bar = 62.dp
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(26.dp)).background(Color(0xCC07080F)).border(1.dp, Gal.glassEdge(), RoundedCornerShape(26.dp))) {
            val slot = maxWidth / count
            val index = Tab.entries.indexOf(tab)
            val offset by animateDpAsState(slot * index, tween(320), label = "ind")
            Box(Modifier.offset(x = offset).width(slot).height(bar).padding(6.dp).clip(RoundedCornerShape(21.dp)).background(Gal.glassFill()).border(1.dp, Gal.glassEdge(), RoundedCornerShape(21.dp)))
            Row(Modifier.fillMaxWidth().height(bar)) {
                for (t in Tab.entries) {
                    val on = t == tab
                    Column(
                        Modifier.width(slot).height(bar).clip(RoundedCornerShape(21.dp)).clickable { onTab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(t.icon, null, tint = if (on) Gal.Cyan else Gal.Faint, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(t.title, style = Type.Caption.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Gal.Text else Gal.Faint))
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(version: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Appear {
        Glass(tint = Gal.Violet, padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Update available", style = Type.Headline)
                    Text("GalIOS $version is ready to install.", style = Type.Caption)
                }
                Spacer(Modifier.width(10.dp))
                GlassButton("Update", onUpdate)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Close, "Later", tint = Gal.Dim, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
            }
        }
    }
}

@Composable
private fun MessageCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    val c = if (error) Gal.Rose else Gal.Mint
    Appear {
        Glass(tint = c, padding = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.TaskAlt, null, tint = c, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = Type.Body, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, "Dismiss", tint = Gal.Dim, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
            }
        }
    }
}

@Composable
fun BootConfirm(s: UiState, onCancel: () -> Unit, onBoot: () -> Unit) {
    var ack by remember { mutableStateOf(false) }
    Dialog(onCancel) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xF5090A12)).border(1.dp, Gal.glassEdge(), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Text("Boot iOS 27?", style = Type.Title)
            VSpace(12.dp)
            Bullet("One UI stops in about 40 seconds. iOS's first screen comes about a minute and a half later; the first boot of a new image can take several minutes more.")
            Bullet("To come back, hold Volume Up + Volume Down for 3 seconds.")
            Bullet("If the way back fails its safety checks, the phone restarts into One UI. Your temporary root is gone after a restart.")
            Bullet("Experimental, and never run on a ${s.device.label} before. Keep the phone plugged in.")
            VSpace(8.dp)
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { ack = !ack }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(ack, { ack = it }, colors = CheckboxDefaults.colors(checkedColor = Gal.Violet, uncheckedColor = Gal.Dim))
                Text("I understand it is experimental", style = Type.Body)
            }
            VSpace(14.dp)
            GradientButton("Boot now", onBoot, enabled = ack, height = 52.dp)
            VSpace(6.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Not now", style = Type.Body.copy(color = Gal.Dim), modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onCancel).padding(10.dp))
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Gal.Cyan))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.Body.copy(fontSize = 14.sp, color = Gal.Dim))
    }
}

@Composable
private fun Switching() {
    Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Gal.Violet.copy(alpha = 0.5f), Color.Transparent))).blur(24.dp))
                Spinner(size = 60.dp, stroke = 5.dp)
            }
            VSpace(26.dp)
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
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xF5090A12)).border(1.dp, Gal.glassEdge(), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Text("GalIOS", style = Type.Title)
            VSpace(10.dp)
            Text(
                "A personal front end for booting iOS 27 next to One UI on a Galaxy S25 Ultra or S24 Ultra.\n\n" +
                    "The boot itself is done by OneiOS (akshaynexus/OneiOS: xnuhost, a source-built XNU running as a process on One UI's kernel) " +
                    "through the OneOS app's iOS 27 engine (akshaynexus/OneOS, Apache License 2.0), which GalIOS reads from the installed OneOS app. " +
                    "GalIOS ships no part of OneOS, OneiOS or iOS.\n\n" +
                    "Not affiliated with Apple or Samsung. iOS is a trademark of Apple Inc.; Galaxy is a trademark of Samsung Electronics.",
                style = Type.Caption,
            )
            VSpace(16.dp)
            GradientButton("Close", onClose, height = 50.dp)
        }
    }
}
