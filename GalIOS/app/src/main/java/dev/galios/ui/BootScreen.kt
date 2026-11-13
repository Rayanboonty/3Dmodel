package dev.galios.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.UiState

@Composable
fun BootScreen(s: UiState, onBoot: () -> Unit, onSetup: () -> Unit, onReturn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroCard(s)
        ReadinessCard(s)
        val blocker = s.bootBlocker
        if (s.status?.sessionOpen == true) {
            GradientButton("Return to One UI", onReturn, icon = Icons.Rounded.Undo)
        } else {
            GradientButton("Boot iOS 27", onBoot, enabled = s.readyToBoot && s.busy == null, icon = Icons.Rounded.PowerSettingsNew)
        }
        if (blocker != null && s.status?.sessionOpen != true) {
            Text(blocker, style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GlassButton("Open Setup", onSetup) }
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeUp, null, tint = Gal.Cyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("The way back", style = Type.Headline)
                    Text("Hold Volume Up + Volume Down for 3 seconds and One UI comes back, apps and data as you left them.", style = Type.Caption)
                }
            }
        }
        s.status?.lastOutcome?.let { o ->
            val text = when {
                o.startsWith("inplace") -> "Last session returned to One UI in place."
                o.startsWith("failsafe") -> "Last session ended with a safety restart. Start your temporary root again."
                o.startsWith("reboot") -> "Last session ended with a restart. Start your temporary root again."
                else -> o
            }
            Text(text, style = Type.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeroCard(s: UiState) {
    GlassCard(padding = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.device.label, style = Type.Title)
                Text(s.device.chip, style = Type.Caption)
                Spacer(Modifier.height(14.dp))
                val ready = s.readyToBoot
                Pill(if (ready) "Ready to boot" else if (s.status?.sessionOpen == true) "iOS session open" else "Setup needed", if (ready) Gal.Mint else Gal.Amber)
                Spacer(Modifier.height(14.dp))
                Text("iOS", style = Type.Display.copy(fontSize = 44.sp, fontWeight = FontWeight.Black))
                Text(s.status?.imageVersion?.let { "$it · ${s.status.imageBuild}" } ?: "27 · no image yet", style = Type.Caption)
            }
            PhoneArt(Modifier.size(width = 104.dp, height = 190.dp), lit = s.readyToBoot)
        }
    }
}

/** A phone drawn in lines: a rounded body, a glowing screen, a camera dot. Original art, no logos. */
@Composable
private fun PhoneArt(modifier: Modifier, lit: Boolean) {
    Canvas(modifier) {
        val r = CornerRadius(26.dp.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF242A4D), Color(0xFF12152B))), cornerRadius = r)
        drawRoundRect(Color(0x33FFFFFF), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        val inset = 6.dp.toPx()
        val screen = if (lit) Brush.linearGradient(listOf(Gal.Violet, Gal.Cyan), Offset.Zero, Offset(size.width, size.height))
        else Brush.linearGradient(listOf(Color(0xFF2A2F55), Color(0xFF1A1E3A)))
        drawRoundRect(screen, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(21.dp.toPx()))
        drawCircle(Color(0xCC000000), radius = 3.5.dp.toPx(), center = Offset(size.width / 2, inset + 9.dp.toPx()))
        // A soft open ring on the screen: GalIOS's mark.
        val c = Offset(size.width / 2, size.height * 0.52f)
        drawArc(Color.White.copy(alpha = if (lit) 0.95f else 0.35f), 40f, 290f, false, Offset(c.x - 20.dp.toPx(), c.y - 20.dp.toPx()), Size(40.dp.toPx(), 40.dp.toPx()), style = Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(size.width / 2 - 18.dp.toPx(), size.height - inset - 8.dp.toPx()), Size(36.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

@Composable
private fun ReadinessCard(s: UiState) {
    GlassCard(padding = 16.dp) {
        val c = s.checks
        StatusRow(Icons.Rounded.Key, "Root", when {
            c == null -> "Checking…"
            c.root -> "Active" + if (c.busybox == null || c.resetprop == null) " · tools missing" else ""
            else -> "Not active: start your temporary root"
        }, when { c == null -> Mark.WAIT; c.root && c.busybox != null && c.resetprop != null -> Mark.OK; c.root -> Mark.WAIT; else -> Mark.BAD })
        StatusRow(Icons.Rounded.Extension, "Boot engine (OneOS)", when {
            s.engine == null -> "Not installed"
            !s.engine.hasIos -> "OneOS ${s.engine.versionName} · no iOS 27 engine"
            else -> "OneOS ${s.engine.versionName}" + if (s.engineInstalled) " · installed" else " · not installed yet"
        }, when { s.engine?.hasIos == true && s.engineInstalled -> Mark.OK; s.engine?.hasIos == true -> Mark.WAIT; else -> Mark.BAD })
        StatusRow(Icons.Rounded.Inventory2, "OneiOS runtime", s.status?.runtimeVersion?.let { "Version $it" } ?: "Not added", if (s.status?.runtimeVersion != null) Mark.OK else Mark.WAIT)
        StatusRow(Icons.Rounded.Layers, "iOS 27 image", s.status?.imageVersion?.let { "iOS $it (${s.status.imageBuild}) · ${bytes(s.status.imageBytes)}" } ?: "Not added", if (s.status?.imageVersion != null) Mark.OK else Mark.WAIT)
        StatusRow(Icons.Rounded.Bolt, "Boot check", when {
            s.status == null -> "Runs once the engine is installed"
            s.status.ready -> "Passed"
            else -> s.status.notReadyReason ?: "Not passed"
        }, when { s.status?.ready == true -> Mark.OK; s.status == null -> Mark.OFF; else -> Mark.WAIT })
        if (s.readyToBoot) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Gal.Mint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Everything is in place.", style = Type.Caption.copy(color = Gal.Mint))
            }
        }
    }
}
