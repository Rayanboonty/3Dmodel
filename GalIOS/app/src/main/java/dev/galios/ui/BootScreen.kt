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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.UiState

@Composable
fun BootScreen(s: UiState, onBoot: () -> Unit, onSetup: () -> Unit, onReturn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Appear { Hero(s) }
        Column {
            if (s.status?.sessionOpen == true) {
                GradientButton("Return to One UI", onReturn, icon = Icons.Rounded.Undo)
            } else {
                GradientButton("Boot iOS 27", onBoot, enabled = s.readyToBoot && s.busy == null, icon = Icons.Rounded.PowerSettingsNew)
                s.bootBlocker?.let { blocker ->
                    VSpace(12.dp)
                    Text(blocker, style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    VSpace(10.dp)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GlassButton("Open Setup", onSetup) }
                }
            }
        }
        VSpace(12.dp)
        Column {
            SectionHeader("Status")
            Appear { Readiness(s) }
        }
        VSpace(12.dp)
        Column {
            SectionHeader("Coming back")
            Glass(padding = 16.dp) {
                InfoRow(Icons.Rounded.VolumeUp, Gal.Graphite, "Volume Up + Volume Down", "Hold both for 3 seconds: One UI returns with your apps and data as you left them.")
            }
            s.status?.lastOutcome?.let { o ->
                Text(
                    when {
                        o.startsWith("inplace") -> "The last session came back to One UI in place."
                        o.startsWith("failsafe") -> "The last session ended with a safety restart. Start temporary root again."
                        o.startsWith("reboot") -> "The last session ended with a restart. Start temporary root again."
                        else -> o
                    },
                    style = Type.Caption, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp),
                )
            }
        }
    }
}

/** The state of the phone at a glance: what boots, from which image, and whether it can now. */
@Composable
private fun Hero(s: UiState) {
    val open = s.status?.sessionOpen == true
    Glass(padding = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("iOS 27", style = Type.Display.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.2).sp), modifier = Modifier.weight(1f))
            Pill(
                when { open -> "Running"; s.readyToBoot -> "Ready"; else -> "Setup needed" },
                when { open -> Gal.Blue; s.readyToBoot -> Gal.Mint; else -> Gal.Amber },
            )
        }
        VSpace(6.dp)
        Text(
            s.status?.imageVersion?.let { "Version $it (${s.status.imageBuild}) · ${bytes(s.status.imageBytes)}" } ?: "No image on this phone yet",
            style = Type.Body.copy(color = Gal.Dim),
        )
        Text(s.device.chip, style = Type.Caption.copy(color = Gal.Faint))
    }
}

@Composable
private fun Readiness(s: UiState) {
    Glass(padding = 16.dp) {
        val c = s.checks
        StatusRow(Icons.Rounded.Key, "Root", when {
            c == null -> "Checking…"
            c.root -> "Active" + (if (s.rootVia == "helper") " · lent by GalIOS Studio" else "") +
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
        } ?: "Not added", if (s.status?.runtimeVersion != null) Mark.OK else Mark.WAIT, tile = Gal.Blue)
        RowDivider()
        StatusRow(Icons.Rounded.Layers, "iOS image", s.status?.imageVersion?.let { "iOS $it (${s.status.imageBuild})" } ?: "Not added",
            if (s.status?.imageVersion != null) Mark.OK else Mark.WAIT, tile = Gal.Purple)
        RowDivider()
        StatusRow(Icons.Rounded.Bolt, "Boot check", when {
            s.status == null -> "Runs once the engine is installed"
            s.status.ready -> "Passed"
            else -> s.status.notReadyReason ?: "Not passed"
        }, when { s.status?.ready == true -> Mark.OK; s.status == null -> Mark.OFF; else -> Mark.WAIT }, tile = Gal.Teal)
    }
}

/** A row that only informs: a tile, a title, a sentence. */
@Composable
private fun InfoRow(icon: ImageVector, tile: Color, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Type.Body.copy(fontWeight = FontWeight.Medium))
            Text(text, style = Type.Caption)
        }
    }
}
