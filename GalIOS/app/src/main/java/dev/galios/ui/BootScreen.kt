package dev.galios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.UiState

@Composable
fun BootScreen(
    s: UiState,
    onBoot: () -> Unit,
    onSetup: () -> Unit,
    onReturn: () -> Unit,
    onLogs: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Appear { Hero(s, onBoot, onReturn, onSetup, onLogs, onRefresh) }
        s.bootBlocker?.takeIf { s.status?.sessionOpen != true }?.let { blocker ->
            Appear(delayMillis = 60) {
                Glass(padding = 18.dp, tint = Gal.Amber) {
                    Text(blocker, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                    VSpace(12.dp)
                    GlassButton("Open Setup", onSetup, icon = Icons.Rounded.Tune)
                }
            }
        }
        VSpace(6.dp)
        Appear(delayMillis = 90) {
            Column {
                SectionHeader("Status")
                Readiness(s)
            }
        }
        VSpace(6.dp)
        Appear(delayMillis = 140) {
            Column {
                SectionHeader("Coming back")
                Glass(padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Gal.Graphite.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.VolumeUp, null, tint = Gal.Graphite, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Volume Up + Volume Down", style = Type.Body.copy(fontWeight = FontWeight.Medium))
                            Text("Hold both for 3 seconds: One UI returns with your apps and data as you left them.", style = Type.Caption)
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
                        style = Type.Caption, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    )
                }
            }
        }
    }
}

/** The ink hero: the phone and the iOS on it, the state, three quick actions, and the one signal-yellow action. */
@Composable
private fun Hero(s: UiState, onBoot: () -> Unit, onReturn: () -> Unit, onSetup: () -> Unit, onLogs: () -> Unit, onRefresh: () -> Unit) {
    val open = s.status?.sessionOpen == true
    NightCard(padding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.device.label, style = Type.Caption.copy(color = Gal.OnNightDim, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
            Pill(
                when { open -> "Running"; s.readyToBoot -> "Ready"; else -> "Setup needed" },
                when { open -> Gal.Signal; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
                onNight = true,
            )
        }
        VSpace(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("iOS 27", style = Type.Display.copy(fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-2.0).sp, color = Gal.OnNight))
                VSpace(6.dp)
                Text(
                    s.status?.imageVersion?.let { "Version $it (${s.status.imageBuild})" } ?: "No image on this phone yet",
                    style = Type.Body.copy(fontSize = 15.sp, color = Gal.OnNightDim),
                )
                s.status?.imageBytes?.let { Text(bytes(it), style = Type.Caption.copy(color = Gal.OnNightDim)) }
            }
            PhoneArt(Modifier.width(104.dp).height(164.dp))
        }
        VSpace(22.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Rounded.Tune, "Setup", onSetup)
            RoundAction(Icons.Rounded.ListAlt, "Logs", onLogs)
            RoundAction(Icons.Rounded.Refresh, "Refresh", onRefresh, enabled = !s.refreshing)
        }
        VSpace(22.dp)
        if (open) SignalButton("Return to One UI", onReturn, icon = Icons.Rounded.Undo)
        else SignalButton("Boot iOS 27", onBoot, enabled = s.readyToBoot && s.busy == null, icon = Icons.Rounded.PowerSettingsNew)
    }
}

@Composable
private fun Readiness(s: UiState) {
    Glass(padding = 18.dp) {
        val c = s.checks
        StatusRow(Icons.Rounded.Key, "Root", when {
            c == null -> "Checking…"
            c.root -> "Active" + (if (s.rootVia == "helper") " · lent by GalaxyIOS Studio" else "") +
                if (c.busybox == null || c.resetprop == null) " · tools missing" else ""
            else -> "Not active: start temporary root"
        }, when { c == null -> Mark.WAIT; c.root && c.busybox != null && c.resetprop != null -> Mark.OK; c.root -> Mark.WAIT; else -> Mark.BAD }, tile = Gal.Graphite)
        RowDivider()
        StatusRow(Icons.Rounded.Extension, "Boot engine", when {
            s.engine == null -> "OneOS is not installed"
            !s.engine.hasIos -> "OneOS ${s.engine.versionName} has no iOS engine"
            else -> "OneOS ${s.engine.versionName}" + if (s.engineInstalled) " · installed" else " · not installed yet"
        }, when { s.engine?.hasIos == true && s.engineInstalled -> Mark.OK; s.engine?.hasIos == true -> Mark.WAIT; else -> Mark.BAD }, tile = Gal.Indigo)
        RowDivider()
        StatusRow(Icons.Rounded.Inventory2, "OneiOS runtime", s.status?.runtimeVersion?.let { v ->
            v + (s.release?.takeIf { it.version != v }?.let { " · ${it.version} is out" } ?: "")
        } ?: "Not added", if (s.status?.runtimeVersion != null) Mark.OK else Mark.WAIT, tile = Gal.Teal)
        RowDivider()
        StatusRow(Icons.Rounded.Layers, "iOS image", s.status?.imageVersion?.let { "iOS $it (${s.status.imageBuild})" } ?: "Not added",
            if (s.status?.imageVersion != null) Mark.OK else Mark.WAIT, tile = Gal.Purple)
        RowDivider()
        StatusRow(Icons.Rounded.Bolt, "Boot check", when {
            s.status == null -> "Runs once the engine is installed"
            s.status.ready -> "Passed"
            else -> s.status.notReadyReason ?: "Not passed"
        }, when { s.status?.ready == true -> Mark.OK; s.status == null -> Mark.OFF; else -> Mark.WAIT }, tile = Gal.Amber)
    }
}
