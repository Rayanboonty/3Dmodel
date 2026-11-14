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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val addRuntime: () -> Unit = {},
    val startPhoneBuild: (String?) -> Unit = {},
    val stopPhoneBuild: () -> Unit = {},
    val openPhoneBuildLog: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(s: UiState, a: SetupActions) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        s.import?.let { ImportCard(it, a.cancelImport) }

        val c = s.checks
        Step(1, "Root", "GalIOS needs root for every step. Start your temporary root first, then check.", done = c?.allGood == true) {
            if (c != null) {
                Check("Root shell", if (c.root) (if (s.rootVia == "helper") "uid 0 · lent by GalIOS Studio" else "uid 0") else "not active", c.root)
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

        Step(3, "OneiOS runtime", "The program that runs iOS next to One UI's kernel. OneiOS's own code (no Apple files): GalIOS downloads its own build of it, with the GPU fixes.", done = s.status?.runtimeVersion != null) {
            s.status?.runtimeVersion?.let { Check("On the phone", "version $it", true) }
            val r = s.release
            when {
                s.releaseLooking -> Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 18.dp, stroke = 2.5.dp); Spacer(Modifier.width(10.dp))
                    Text("Looking for the newest runtime on GitHub…", style = Type.Caption)
                }
                r != null -> Check(
                    "Newest release",
                    "${r.name} · ${bytes(r.size)}" + if (r.version == s.status?.runtimeVersion) " · already on the phone" else "",
                    true,
                )
                s.releaseError != null -> Check("Newest release", s.releaseError, false)
            }
            s.runtimeFile?.let { path ->
                val name = path.substringAfterLast('/')
                val onPhone = s.status?.runtimeVersion?.let { v -> name == "oneios-runtime-$v-arm64.tar.zst" } == true
                if (!onPhone) Check("Downloaded", "$name · ready to add to the phone", true)
            }
            s.runtimeDownload?.let { f ->
                VSpace(8.dp)
                Text("Downloading… ${(f * 100).toInt()}%", style = Type.Caption.copy(color = Gal.Cyan))
                VSpace(6.dp)
                GalProgress(f)
            }
            FoundList(s, StageClient.Kind.RUNTIME, a)
            VSpace(10.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val idle = s.busy == null && s.import == null && s.runtimeDownload == null
                val downloadedNotOnPhone = s.runtimeFile != null &&
                    s.status?.runtimeVersion?.let { v -> s.runtimeFile.endsWith("oneios-runtime-$v-arm64.tar.zst") } != true
                when {
                    downloadedNotOnPhone -> GradientButton("Add to phone", a.addRuntime, enabled = idle && s.canStage, height = 46.dp, modifier = Modifier.width(170.dp))
                    r != null && r.version != s.status?.runtimeVersion ->
                        GradientButton("Download ${r.version}", a.downloadRuntime, enabled = idle, height = 46.dp, modifier = Modifier.width(190.dp))
                    r == null -> GlassButton(if (s.releaseError != null) "Try again" else "Look up", a.findRelease, enabled = !s.releaseLooking, icon = Icons.Rounded.Refresh)
                }
                GlassButton("Choose file", a.pickRuntime, enabled = idle && s.canStage, icon = Icons.Rounded.FolderOpen)
            }
            // Downloading needs only the internet; adding to the phone needs root and OneOS. Say which is missing.
            if (!s.canStage) s.stageBlocker?.let { why ->
                VSpace(8.dp)
                Text(why, style = Type.Caption.copy(color = Gal.Amber))
            }
        }

        Step(4, "iOS 27 image", "Built by you from Apple's own firmware with OneiOS's scripts (about 11 GB): right here on the phone, or on a computer. Nobody can send you a copy.", done = s.status?.imageVersion != null) {
            s.status?.imageVersion?.let { Check("Imported", "iOS $it (${s.status.imageBuild}) · ${bytes(s.status.imageBytes)}", true) }
            FoundList(s, StageClient.Kind.IMAGE, a)
            PhoneBuildPanel(s, a)
            if (s.rootOk && s.found.none { it.kind == StageClient.Kind.IMAGE } && s.phoneBuild?.state != dev.galios.core.PhoneBuild.Status.State.RUNNING) {
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

/** "No PC? Build it on this phone": requirements, then live progress, then the result (imported automatically). */
@Composable
private fun PhoneBuildPanel(s: UiState, a: SetupActions) {
    val b = s.phoneBuild
    val st = b?.state
    var confirm by remember { mutableStateOf(false) }
    VSpace(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Gal.Violet.copy(alpha = 0.16f), Gal.Cyan.copy(alpha = 0.05f))))
            .border(1.dp, Gal.glassEdge(), androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Memory, null, tint = Gal.Cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("No PC? Build it on this phone", style = Type.Headline)
        }
        VSpace(6.dp)
        val prep = s.phoneBuildPreparing
        when {
            prep != null -> {
                Text(prep.first + "…", style = Type.Caption.copy(color = Gal.Cyan))
                VSpace(8.dp)
                prep.second?.let { GalProgress(it) } ?: Spinner(size = 18.dp, stroke = 2.5.dp)
            }
            st == dev.galios.core.PhoneBuild.Status.State.RUNNING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spinner(size = 18.dp, stroke = 2.5.dp); Spacer(Modifier.width(10.dp))
                    Text(b.step ?: "Working…", style = Type.Body.copy(fontWeight = FontWeight.Medium), maxLines = 2)
                }
                b.startedSec?.let { t ->
                    val m = ((System.currentTimeMillis() / 1000 - t) / 60).coerceAtLeast(0)
                    Text("Running for ${m / 60}h ${m % 60}m · keep the phone plugged in and on Wi-Fi", style = Type.Caption)
                }
                BuildLog(b.log)
                VSpace(10.dp)
                Row {
                    GlassButton("Stop", a.stopPhoneBuild, icon = Icons.Rounded.Close)
                    Spacer(Modifier.width(10.dp))
                    GlassButton("Full log", a.openPhoneBuildLog, icon = Icons.Rounded.Description)
                }
            }
            st == dev.galios.core.PhoneBuild.Status.State.DONE -> {
                Check("Built on this phone", "The image is in your Download folder" + if (s.status?.imageVersion != null) " and imported." else ": import it above.", true)
            }
            st == dev.galios.core.PhoneBuild.Status.State.FAILED || st == dev.galios.core.PhoneBuild.Status.State.STOPPED -> {
                val neverStarted = st == dev.galios.core.PhoneBuild.Status.State.FAILED && (b.step == null || b.step == "starting")
                Check(
                    when {
                        st == dev.galios.core.PhoneBuild.Status.State.STOPPED -> "Stopped"
                        neverStarted -> "Ubuntu did not start on this phone"
                        else -> "Stopped by an error"
                    },
                    if (neverStarted) "The log says why. Tap Full log, then Share, and send it to me." else b.failedStep ?: b.step ?: "",
                    false,
                )
                BuildLog(b.log, lines = 8)
                VSpace(10.dp)
                Text("Resume continues from the step that stopped; finished steps are kept.", style = Type.Caption)
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientButton("Resume", { a.startPhoneBuild(s.ipsws.firstOrNull { it.isPinned }?.path) }, enabled = s.rootOk, height = 46.dp, modifier = Modifier.width(140.dp))
                    Spacer(Modifier.width(10.dp))
                    GlassButton("Full log", a.openPhoneBuildLog, icon = Icons.Rounded.Description)
                }
            }
            else -> {
                Text(
                    "GalIOS sets up a small Ubuntu inside the phone and runs OneiOS's build there, the same one a PC runs. " +
                        "It takes about 1–3 hours; the phone gets warm. When it is done, the image is imported by itself.",
                    style = Type.Caption,
                )
                VSpace(8.dp)
                Check("Root", if (s.rootOk) "active" else "start your temporary root", s.rootOk)
                val spaceOk = (s.dataFree ?: 0) >= dev.galios.core.PhoneBuild.NEEDED_BYTES
                Check("Free space", "${bytes(s.dataFree)} free · about 75 GB needed", spaceOk)
                val ipsw = s.ipsws.firstOrNull { it.isPinned }
                Check(
                    "iOS files",
                    when {
                        ipsw != null -> "uses ${ipsw.name} from your phone"
                        s.ipsws.isNotEmpty() -> "${s.ipsws.first().name} is not iOS 27.0.1 for iPhone18,2: downloads the right one from Apple instead (10.6 GB)"
                        else -> "downloaded from Apple during the build (10.6 GB, Wi-Fi)"
                    },
                    true,
                )
                VSpace(10.dp)
                GradientButton("Build on this phone", { confirm = true }, enabled = s.rootOk && spaceOk && s.busy == null, height = 48.dp, modifier = Modifier.width(220.dp))
            }
        }
    }
    if (confirm) {
        androidx.compose.ui.window.Dialog({ confirm = false }) {
            Column(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(Color(0xF5090A12)).border(1.dp, Gal.glassEdge(), androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).padding(24.dp)) {
                Text("Build on this phone?", style = Type.Title)
                VSpace(10.dp)
                for (t in listOf(
                    "Takes about 1–3 hours. Keep the phone plugged in and on Wi-Fi; it will get warm.",
                    "Uses about 75 GB of storage while it builds.",
                    "It keeps going if you leave GalIOS. Don't restart the phone: temporary root would be lost (Resume continues later).",
                    "The image is for you only: it is built from Apple's firmware.",
                )) Row(Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Gal.Cyan)); Spacer(Modifier.width(10.dp))
                    Text(t, style = Type.Body.copy(fontSize = 14.sp, color = Gal.Dim))
                }
                VSpace(14.dp)
                GradientButton("Start build", { confirm = false; a.startPhoneBuild(s.ipsws.firstOrNull { it.isPinned }?.path) }, height = 50.dp)
                VSpace(4.dp)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Not now", style = Type.Body.copy(color = Gal.Dim), modifier = Modifier.clickable { confirm = false }.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BuildLog(log: List<String>, lines: Int = 5) {
    val tail = log.filter { it.isNotBlank() }.takeLast(lines)
    if (tail.isEmpty()) return
    VSpace(8.dp)
    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(10.dp)) {
        for (l in tail) Text(l, style = Type.Mono.copy(fontSize = 10.5.sp), maxLines = 1)
    }
}

/** Archives of one kind found on the phone, each with one-tap import. The one already imported is marked. */
@Composable
private fun FoundList(s: UiState, kind: StageClient.Kind, a: SetupActions) {
    val list = s.found.filter { it.kind == kind }
    if (list.isEmpty()) return
    VSpace(8.dp)
    Text("FOUND ON THIS PHONE", style = Type.Label.copy(fontSize = 11.sp))
    for (f in list) {
        val imported = when (kind) {
            StageClient.Kind.IMAGE -> f.version == s.status?.imageVersion && f.build == s.status?.imageBuild
            StageClient.Kind.RUNTIME -> f.version == s.status?.runtimeVersion
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Gal.glassFill()).padding(12.dp),
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
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) Gal.Mint.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = Gal.Mint, modifier = Modifier.size(18.dp))
                else Text("$n", style = Type.Body.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = Type.Title.copy(fontSize = 19.sp))
        }
        VSpace(8.dp)
        Text(text, style = Type.Caption)
        VSpace(10.dp)
        content()
    }
}


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
    Glass(padding = 20.dp) {
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

