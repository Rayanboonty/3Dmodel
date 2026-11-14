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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.galios.core.LogInfo
import java.text.DateFormat
import java.util.Date

@Composable
fun LogsScreen(logs: List<LogInfo>, onRefresh: () -> Unit, onOpen: (LogInfo) -> Unit, onSave: (LogInfo) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SESSIONS", style = Type.Label, modifier = Modifier.weight(1f))
            GlassButton("Refresh", onRefresh, icon = Icons.Rounded.Refresh)
        }
        if (logs.isEmpty()) {
            Glass(padding = 28.dp) {
                Centered {
                    Icon(Icons.Rounded.Description, null, tint = Gal.Gray, modifier = Modifier.size(36.dp))
                    VSpace(10.dp)
                    Text("No logs yet", style = Type.Headline)
                    VSpace(2.dp)
                    Text("They appear after your first boot.", style = Type.Caption)
                }
            }
        } else {
            Glass(padding = 0.dp) {
                logs.forEachIndexed { i, l ->
                    if (i > 0) Box(Modifier.padding(start = 16.dp)) { RowDivider(inset = 44.dp) }
                    LogRow(l, { onOpen(l) }, { onSave(l) })
                }
            }
        }
        Text(
            "Each log shows how far iOS got and where it stopped. Save puts the whole log in Downloads/GalIOS; the last iOS session's log is saved there by itself.",
            style = Type.Caption, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp),
        )
    }
}

/** One log in the list: a tile, its name and when, its last lines quietly below, Save and the way in at the end. */
@Composable
private fun LogRow(l: LogInfo, onOpen: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Gal.Graphite), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Description, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title.replaceFirstChar { it.uppercase() }, style = Type.Body)
                Text("${bytes(l.size)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(l.modified * 1000))}", style = Type.Caption)
            }
            IconButton(onSave, Modifier.size(40.dp)) { Icon(Icons.Rounded.Download, "Save to Downloads", tint = Gal.Blue, modifier = Modifier.size(22.dp)) }
            Icon(Icons.Rounded.ChevronRight, null, tint = Gal.Faint, modifier = Modifier.size(22.dp))
        }
        if (l.tail.isNotEmpty()) {
            VSpace(8.dp)
            Column(Modifier.padding(start = 44.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Gal.Black).padding(horizontal = 10.dp, vertical = 8.dp)) {
                for (line in l.tail.takeLast(3)) Text(line, style = Type.Mono.copy(fontSize = 10.5.sp, color = Gal.Dim), maxLines = 1)
            }
        }
    }
}

/** One log, full screen: its newest part, monospaced, scrollable both ways, with Save (the whole log) and Share. */
@Composable
fun LogViewer(title: String, text: String, onClose: () -> Unit, onShare: () -> Unit, onSave: () -> Unit = {}, status: String? = null) {
    Backdrop {
        Column(Modifier.fillMaxSize().padding(top = 44.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose, Modifier.clip(CircleShape)) { Icon(Icons.Rounded.Close, "Close", tint = Gal.Blue) }
                Text(title.replaceFirstChar { it.uppercase() }, style = Type.Headline, modifier = Modifier.weight(1f))
                IconButton(onSave) { Icon(Icons.Rounded.Download, "Save to Downloads", tint = Gal.Blue) }
                IconButton(onShare) { Icon(Icons.Rounded.Share, "Share", tint = Gal.Blue) }
            }
            status?.let { VSpace(4.dp); Text(it, style = Type.Caption.copy(color = Gal.Blue), modifier = Modifier.padding(horizontal = 8.dp)) }
            Text("The newest part is shown here. Save writes the whole log to Downloads/GalIOS.", style = Type.Caption, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Gal.Ink)
                    .verticalScroll(rememberScrollState(Int.MAX_VALUE)).horizontalScroll(rememberScrollState()).padding(14.dp),
            ) {
                Text(text, style = Type.Mono)
            }
        }
    }
}
