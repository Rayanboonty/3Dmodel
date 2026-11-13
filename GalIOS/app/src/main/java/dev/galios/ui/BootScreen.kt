package dev.galios.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.R
import dev.galios.UiState

@Composable
fun BootScreen(s: UiState, onBoot: () -> Unit, onSetup: () -> Unit, onReturn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Appear { Hero(s) }
        Appear { Readiness(s) }
        if (s.status?.sessionOpen == true) {
            GradientButton("Return to One UI", onReturn, icon = Icons.Rounded.Undo)
        } else {
            GradientButton("Boot iOS 27", onBoot, enabled = s.readyToBoot && s.busy == null, icon = Icons.Rounded.PowerSettingsNew)
            val blocker = s.bootBlocker
            if (blocker != null) {
                Text(blocker, style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GlassButton("Open Setup", onSetup) }
            }
        }
        Glass(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Gal.Cyan.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VolumeUp, null, tint = Gal.Cyan, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("The way back", style = Type.Headline)
                    Text("Hold Volume Up + Volume Down for 3 seconds and One UI returns, apps and data as you left them.", style = Type.Caption)
                }
            }
        }
        s.status?.lastOutcome?.let { o ->
            Text(
                when {
                    o.startsWith("inplace") -> "Last session returned to One UI in place."
                    o.startsWith("failsafe") -> "Last session ended with a safety restart. Start temporary root again."
                    o.startsWith("reboot") -> "Last session ended with a restart. Start temporary root again."
                    else -> o
                },
                style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Hero(s: UiState) {
    Glass(padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            // The glow art, blurred, drifting light behind the wordmark.
            Image(
                painterResource(R.drawable.glow_orb), null,
                modifier = Modifier.align(Alignment.CenterEnd).size(260.dp).blur(8.dp),
            )
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(s.device.label, style = Type.Title)
                Text(s.device.chip, style = Type.Caption)
                Spacer(Modifier.height(14.dp))
                val ready = s.readyToBoot
                Pill(
                    if (ready) "Ready to boot" else if (s.status?.sessionOpen == true) "iOS session open" else "Setup needed",
                    if (ready) Gal.Mint else Gal.Amber,
                )
                Spacer(Modifier.weight(1f))
                Text("iOS", style = Type.Display.copy(fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp))
                Text(s.status?.imageVersion?.let { "$it · ${s.status.imageBuild}" } ?: "27 · no image yet", style = Type.Caption)
            }
        }
    }
}

@Composable
private fun Readiness(s: UiState) {
    Glass(padding = 16.dp) {
        val c = s.checks
        StatusRow(Icons.Rounded.Key, "Root", when {
            c == null -> "Checking…"
            c.root -> "Active" + if (c.busybox == null || c.resetprop == null) " · tools missing" else ""
            else -> "Not active: start temporary root"
        }, when { c == null -> Mark.WAIT; c.root && c.busybox != null && c.resetprop != null -> Mark.OK; c.root -> Mark.WAIT; else -> Mark.BAD })
        Divider()
        StatusRow(Icons.Rounded.Extension, "Boot engine (OneOS)", when {
            s.engine == null -> "Not installed"
            !s.engine.hasIos -> "OneOS ${s.engine.versionName} · no iOS engine"
            else -> "OneOS ${s.engine.versionName}" + if (s.engineInstalled) " · installed" else " · not installed"
        }, when { s.engine?.hasIos == true && s.engineInstalled -> Mark.OK; s.engine?.hasIos == true -> Mark.WAIT; else -> Mark.BAD })
        Divider()
        StatusRow(Icons.Rounded.Inventory2, "OneiOS runtime", s.status?.runtimeVersion?.let { "Version $it" } ?: "Not added", if (s.status?.runtimeVersion != null) Mark.OK else Mark.WAIT)
        Divider()
        StatusRow(Icons.Rounded.Layers, "iOS 27 image", s.status?.imageVersion?.let { "iOS $it (${s.status.imageBuild}) · ${bytes(s.status.imageBytes)}" } ?: "Not added", if (s.status?.imageVersion != null) Mark.OK else Mark.WAIT)
        Divider()
        StatusRow(Icons.Rounded.Bolt, "Boot check", when {
            s.status == null -> "Runs once the engine is installed"
            s.status.ready -> "Passed"
            else -> s.status.notReadyReason ?: "Not passed"
        }, when { s.status?.ready == true -> Mark.OK; s.status == null -> Mark.OFF; else -> Mark.WAIT })
        if (s.readyToBoot) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Gal.Mint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Everything is in place.", style = Type.Caption.copy(color = Gal.Mint))
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 52.dp).background(Color.White.copy(alpha = 0.05f)))
