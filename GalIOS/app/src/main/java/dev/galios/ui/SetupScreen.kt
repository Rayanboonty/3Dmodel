package dev.galios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.galios.ImportState
import dev.galios.UiState
import dev.galios.core.ImageFinder
import dev.galios.core.StageClient

class SetupActions(
    val refresh: () -> Unit,
    val installEngine: () -> Unit,
    val getOneos: () -> Unit,
    val findRelease: () -> Unit,
    val downloadRuntime: () -> Unit,
    val pickRuntime: () -> Unit,
    val pickImage: () -> Unit,
    val howToBuild: () -> Unit,
    val cancelImport: () -> Unit,
    val importFound: (ImageFinder.Found) -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(s: UiState, a: SetupActions) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        s.import?.let { ImportCard(it, a.cancelImport) }

        val c = s.checks
        Step(1, "Root", "GalIOS needs root for every step. Start your temporary root first, then check.", done = c?.allGood == true) {
            if (c != null) {
                Check("Root shell", if (c.root) "uid 0" else "not active", c.root)
                Check("busybox", c.busybox ?: "missing: your root tool must provide it at /data/adb/ksu/bin, /data/adb/ap/bin or /data/adb/magisk", c.busybox != null)
                Check("resetprop", c.resetprop ?: "missing: needed to put One UI's boot counters back on the return", c.resetprop != null)
                Check("Volume keys", if (c.volumeUp && c.volumeDown) "both found: the way back works" else "not found", c.volumeUp && c.volumeDown)
                Check("Free space", c.freeBytes?.let { bytes(it) + " on /data (the image needs about 22 GB while it is copied)" } ?: "?", (c.freeBytes ?: 0) > 22L * (1L shl 30))
                c.gpu?.let { Check("GPU", it, true) }
            }
            VSpace(10.dp)
            GlassButton("Check again", a.refresh, icon = Icons.Rounded.Refresh)
        }

        val e = s.engine
        Step(2, "Boot engine", "OneOS's iOS 27 edition does the actual boot. GalIOS installs its engine and adapts its phone check for your ${s.device.label}.", done = e?.hasIos == true && s.engineInstalled) {
            when {
                e == null -> {
                    Text("OneOS is not installed.", style = Type.Body)
                    VSpace(10.dp)
                    GlassButton("Get OneOS (iOS 27 edition)", a.getOneos, icon = Icons.Rounded.OpenInNew)
                }
                !e.hasIos -> {
                    Text("OneOS ${e.versionName} is installed, but it is not the iOS 27 edition.", style = Type.Body)
                    VSpace(10.dp)
                    GlassButton("Get the iOS 27 edition", a.getOneos, icon = Icons.Rounded.OpenInNew)
                }
                else -> {
                    Check("OneOS", "${e.versionName} (${e.versionCode})", true)
                    Check("Engine in the stage", if (s.engineInstalled) "installed" else "not yet", s.engineInstalled)
                    VSpace(10.dp)
                    GlassButton(if (s.engineInstalled) "Install again" else "Install engine", a.installEngine, enabled = s.rootOk && s.busy == null && s.import == null, icon = Icons.Rounded.InstallMobile)
                }
            }
        }

        Step(3, "OneiOS runtime", "The program that runs iOS next to One UI's kernel. OneiOS's own code: GalIOS downloads it from OneiOS's releases.", done = s.status?.runtimeVersion != null) {
            s.status?.runtimeVersion?.let { Check("On the phone", "version $it", true) }
            s.release?.let { Check("Newest release", "${it.name} · ${bytes(it.size)}", true) }
            FoundList(s, StageClient.Kind.RUNTIME, a)
            VSpace(10.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val can = s.rootOk && s.busy == null && s.import == null && e?.hasIos == true
                if (s.release == null) GlassButton("Find newest", a.findRelease, enabled = s.busy == null, icon = Icons.Rounded.CloudDownload)
                else GlassButton("Download ${s.release.tag}", a.downloadRuntime, enabled = can, icon = Icons.Rounded.CloudDownload)
                GlassButton("Choose file", a.pickRuntime, enabled = can, icon = Icons.Rounded.FolderOpen)
            }
        }

        Step(4, "iOS 27 image", "Built by you from Apple's own firmware with OneiOS's scripts on a computer (about 11 GB). Nobody can send you a copy: copy your .tar.zst to the phone and choose it here.", done = s.status?.imageVersion != null) {
            s.status?.imageVersion?.let { Check("Imported", "iOS $it (${s.status.imageBuild}) · ${bytes(s.status.imageBytes)}", true) }
            FoundList(s, StageClient.Kind.IMAGE, a)
            if (s.rootOk && s.found.none { it.kind == StageClient.Kind.IMAGE }) {
                VSpace(6.dp)
                Text(
                    "No .tar.zst found yet. Put your image in the phone's Download folder, then tap Look again." +
                        if (s.scannedFolders.isNotEmpty()) "\nLooked in: ${s.scannedFolders.joinToString(", ")}." else "",
                    style = Type.Caption.copy(color = Gal.Faint),
                )
            }
            VSpace(10.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Look again", a.refresh, enabled = s.rootOk && !s.refreshing, icon = Icons.Rounded.TravelExplore)
                GlassButton("Choose image", a.pickImage, enabled = s.rootOk && s.busy == null && s.import == null && e?.hasIos == true, icon = Icons.Rounded.FolderOpen)
                GlassButton("How to build it", a.howToBuild, icon = Icons.Rounded.OpenInNew)
            }
        }
    }
}

/** Archives of one kind found on the phone, each with one-tap import. The one already imported is marked. */
@Composable
private fun FoundList(s: UiState, kind: StageClient.Kind, a: SetupActions) {
    val list = s.found.filter { it.kind == kind }
    if (list.isEmpty()) return
    VSpace(8.dp)
    Text("FOUND ON THIS PHONE", style = Type.Label.copy(fontSize = 11.sp()))
    for (f in list) {
        val imported = when (kind) {
            StageClient.Kind.IMAGE -> f.version == s.status?.imageVersion && f.build == s.status?.imageBuild
            StageClient.Kind.RUNTIME -> f.version == s.status?.runtimeVersion
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Gal.Glass).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (kind == StageClient.Kind.IMAGE) Icons.Rounded.Download else Icons.Rounded.CloudDownload, null, tint = Gal.Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.label, style = Type.Body.copy(fontWeight = FontWeight.Medium))
                Text("${bytes(f.size)} · ${f.path.removePrefix("/sdcard/")}", style = Type.Caption, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            if (imported) Pill("Imported", Gal.Mint)
            else GlassButton("Import", { a.importFound(f) }, enabled = s.rootOk && s.busy == null && s.import == null && s.engine?.hasIos == true)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, text: String, done: Boolean, content: @Composable () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) Gal.Mint.copy(alpha = 0.2f) else Gal.GlassStrong),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = Gal.Mint, modifier = Modifier.size(18.dp))
                else Text("$n", style = Type.Body.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = Type.Title.copy(fontSize = 19.sp()))
        }
        VSpace(8.dp)
        Text(text, style = Type.Caption)
        VSpace(10.dp)
        content()
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun Check(title: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (ok) Gal.Mint else Gal.Amber))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = Type.Body.copy(fontWeight = FontWeight.Medium))
            Text(value, style = Type.Caption)
        }
    }
}

@Composable
fun ImportCard(i: ImportState, onCancel: () -> Unit) {
    GlassCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, style = Type.Headline)
                Text(
                    when {
                        i.total == 0L -> "Reading the archive…"
                        i.file != null -> "File ${i.fileIndex} of ${i.fileCount}: ${i.file}"
                        else -> "Starting…"
                    },
                    style = Type.Caption, maxLines = 1,
                )
            }
            Text("${(i.fraction * 100).toInt()}%", style = Type.Headline.copy(color = Gal.Cyan))
        }
        VSpace(14.dp)
        GalProgress(i.fraction)
        VSpace(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${bytes(i.done)} of ${bytes(i.total)} · root checks every file", style = Type.Caption, modifier = Modifier.weight(1f))
            GlassButton("Stop", onCancel)
        }
        Spacer(Modifier.height(2.dp))
        Text("Keep GalIOS open or in the background; the phone stays awake until it is done.", style = Type.Caption.copy(color = Gal.Faint))
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
