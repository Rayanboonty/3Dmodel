package dev.galios.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TaskAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.galios.UiState
import dev.galios.core.LogInfo

enum class Tab(val title: String, val icon: ImageVector) { BOOT("Boot", Icons.Rounded.PowerSettingsNew), SETUP("Setup", Icons.Rounded.Tune), LOGS("Logs", Icons.Rounded.ListAlt) }

class AppActions(
    val setup: SetupActions,
    val boot: () -> Unit,
    val returnToOneUi: () -> Unit,
    val loadLogs: () -> Unit,
    val openLog: (LogInfo) -> Unit,
    val closeLog: () -> Unit,
    val shareLog: (String, String) -> Unit,
    val dismissMessage: () -> Unit,
)

@Composable
fun GalIosApp(s: UiState, a: AppActions, startTab: Tab = Tab.BOOT, showConfirm: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var confirm by remember { mutableStateOf(showConfirm) }
    var about by remember { mutableStateOf(false) }
    GalTheme {
        AuroraBackground {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 20.dp),
                ) {
                    Header(tab, s, onAbout = { about = true })
                    VSpace(18.dp)
                    s.message?.let { MessageCard(it.text, it.error, a.dismissMessage); VSpace(14.dp) }
                    when (tab) {
                        Tab.BOOT -> BootScreen(s, onBoot = { confirm = true }, onSetup = { tab = Tab.SETUP }, onReturn = a.returnToOneUi)
                        Tab.SETUP -> SetupScreen(s, a.setup)
                        Tab.LOGS -> LogsScreen(s.logs, a.loadLogs, a.openLog)
                    }
                }
                TabBar(tab) { t -> tab = t; if (t == Tab.LOGS) a.loadLogs() }
            }
            AnimatedVisibility(s.switching, enter = fadeIn(), exit = fadeOut()) { Switching() }
        }
        if (confirm) BootConfirm(s, onCancel = { confirm = false }, onBoot = { confirm = false; a.boot() })
        if (about) About { about = false }
        s.openLog?.let { (title, text) -> Dialog(onDismissRequest = a.closeLog, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { LogViewer(title, text, a.closeLog) { a.shareLog(title, text) } } }
    }
}

@Composable
private fun Header(tab: Tab, s: UiState, onAbout: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("GalIOS", style = Type.Label.copy(color = Gal.Cyan))
            Text(
                when (tab) { Tab.BOOT -> "Boot"; Tab.SETUP -> "Setup"; Tab.LOGS -> "Logs" },
                style = Type.Display,
            )
        }
        if (s.refreshing || s.busy != null) { Spinner(); Spacer(Modifier.width(10.dp)) }
        IconButton(onAbout, Modifier.size(40.dp).clip(CircleShape).background(Gal.Glass)) { Icon(Icons.Rounded.Info, "About", tint = Gal.Text) }
    }
    s.busy?.let { Text("$it…", style = Type.Caption.copy(color = Gal.Cyan), modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
private fun TabBar(tab: Tab, onTab: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(30.dp)).background(Color(0xCC0E1230)).border(1.dp, Gal.Edge, RoundedCornerShape(30.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (t in Tab.entries) {
            val on = t == tab
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (on) Gal.GlassStrong else Color.Transparent).clickable { onTab(t) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, null, tint = if (on) Gal.Cyan else Gal.Dim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(t.title, style = Type.Body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Gal.Text else Gal.Dim))
            }
        }
    }
}

@Composable
private fun MessageCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background((if (error) Gal.Rose else Gal.Mint).copy(alpha = 0.14f))
            .border(1.dp, (if (error) Gal.Rose else Gal.Mint).copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.TaskAlt, null, tint = if (error) Gal.Rose else Gal.Mint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.Body, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Close, "Dismiss", tint = Gal.Dim, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
    }
}

@Composable
fun BootConfirm(s: UiState, onCancel: () -> Unit, onBoot: () -> Unit) {
    var ack by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF151938)).border(1.dp, Gal.Edge, RoundedCornerShape(30.dp)).padding(24.dp),
        ) {
            Text("Boot iOS 27?", style = Type.Title)
            VSpace(12.dp)
            Bullet("One UI stops in about 40 seconds. iOS's first screen comes about a minute and a half later; the first boot of a new image can take several minutes more.")
            Bullet("To come back, hold Volume Up + Volume Down for 3 seconds.")
            Bullet("If the way back fails its safety checks, the phone restarts into One UI. Your temporary root is gone after a restart.")
            Bullet("This is experimental and has never run on a ${s.device.label} before. Keep the phone plugged in.")
            VSpace(8.dp)
            Row(Modifier.clickable { ack = !ack }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(ack, { ack = it }, colors = CheckboxDefaults.colors(checkedColor = Gal.Violet, uncheckedColor = Gal.Dim))
                Text("I understand it is experimental", style = Type.Body)
            }
            VSpace(14.dp)
            GradientButton("Boot now", onBoot, enabled = ack, height = 54.dp)
            VSpace(8.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Not now", style = Type.Body.copy(color = Gal.Dim), modifier = Modifier.clickable(onClick = onCancel).padding(10.dp)) }
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
    Box(Modifier.fillMaxSize().background(Color(0xEE05060F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Spinner(size = 64.dp, stroke = 6.dp)
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
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.clip(RoundedCornerShape(30.dp)).background(Color(0xFF151938)).border(1.dp, Gal.Edge, RoundedCornerShape(30.dp)).padding(24.dp)) {
            Text("GalIOS 1.0", style = Type.Title)
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
