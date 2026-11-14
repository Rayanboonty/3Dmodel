package dev.galios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.galios.core.LogInfo
import java.text.DateFormat
import java.util.Date

@Composable
fun LogsScreen(logs: List<LogInfo>, onRefresh: () -> Unit, onOpen: (LogInfo) -> Unit, onSave: (LogInfo) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("After every try, these show how far iOS got and where it stopped. Save puts the whole log in Downloads/GalIOS; the last iOS session's log is saved there by itself.", style = Type.Caption, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            GlassButton("Refresh", onRefresh, icon = Icons.Rounded.Refresh)
        }
        if (logs.isEmpty()) {
            Glass {
                Centered {
                    Icon(Icons.Rounded.Description, null, tint = Gal.Faint, modifier = Modifier.size(40.dp))
                    VSpace(8.dp)
                    Text("No logs yet", style = Type.Headline)
                    Text("They appear after your first boot.", style = Type.Caption)
                }
            }
        }
        for (l in logs) {
            Glass(Modifier.clickable { onOpen(l) }, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.title, style = Type.Headline)
                        Text("${bytes(l.size)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(l.modified * 1000))}", style = Type.Caption)
                    }
                    GlassButton("Save", { onSave(l) }, icon = Icons.Rounded.Download)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = Gal.Dim)
                }
                if (l.tail.isNotEmpty()) {
                    VSpace(10.dp)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Gal.Ink.copy(alpha = 0.6f)).padding(12.dp)) {
                        for (line in l.tail.takeLast(4)) Text(line, style = Type.Mono, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One log, full screen: its newest part, monospaced, scrollable both ways, with Save (the whole log) and Share. */
@Composable
fun LogViewer(title: String, text: String, onClose: () -> Unit, onShare: () -> Unit, onSave: () -> Unit = {}, status: String? = null) {
    Backdrop(drift = false) {
        Column(Modifier.fillMaxSize().padding(top = 44.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.Close, "Close", tint = Gal.Text) }
                Text(title, style = Type.Title, modifier = Modifier.weight(1f))
                IconButton(onSave) { Icon(Icons.Rounded.Download, "Save to Downloads", tint = Gal.Text) }
                IconButton(onShare) { Icon(Icons.Rounded.Share, "Share", tint = Gal.Text) }
            }
            status?.let { VSpace(4.dp); Text(it, style = Type.Caption.copy(color = Gal.Cyan)) }
            VSpace(4.dp)
            Text("The newest part is shown here. Save writes the whole log to Downloads/GalIOS.", style = Type.Caption)
            VSpace(8.dp)
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Gal.Ink.copy(alpha = 0.75f))
                    .verticalScroll(rememberScrollState(Int.MAX_VALUE)).horizontalScroll(rememberScrollState()).padding(14.dp),
            ) {
                Text(text, style = Type.Mono)
            }
        }
    }
}
