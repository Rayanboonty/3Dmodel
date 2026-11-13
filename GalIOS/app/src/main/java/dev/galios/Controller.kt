package dev.galios

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import dev.galios.core.CancelledException
import dev.galios.core.Device
import dev.galios.core.EngineSetup
import dev.galios.core.ImageFinder
import dev.galios.core.LogInfo
import dev.galios.core.OneosEngine
import dev.galios.core.PackageImport
import dev.galios.core.PhoneChecks
import dev.galios.core.ProcessRootShell
import dev.galios.core.RootFileStream
import dev.galios.core.RootShell
import dev.galios.core.RuntimeRelease
import dev.galios.core.StageClient
import dev.galios.core.StageStatus
import dev.galios.core.UpdateInfo
import dev.galios.core.UpdateInstaller
import dev.galios.core.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

/** Everything the screens show. */
data class UiState(
    val device: Device = Device(Build.MODEL ?: "", Build.DEVICE ?: "", Build.SOC_MODEL ?: ""),
    val checks: PhoneChecks? = null,
    val engine: EngineInfo? = null,
    val engineInstalled: Boolean = false,
    val status: StageStatus? = null,
    val release: RuntimeRelease? = null,
    /** Looking up the newest runtime on GitHub (no root needed). */
    val releaseLooking: Boolean = false,
    /** Why the lookup failed, shown inside the runtime step. */
    val releaseError: String? = null,
    /** 0..1 while the runtime downloads into GalIOS's own storage; null otherwise. */
    val runtimeDownload: Float? = null,
    /** The runtime package already downloaded into GalIOS's storage, ready to add to the phone. */
    val runtimeFile: String? = null,
    /** OneiOS archives already on the phone (Download, Documents, the top of the storage). */
    val found: List<ImageFinder.Found> = emptyList(),
    /** The folders the last scan actually looked in, for the "nothing found" hint. */
    val scannedFolders: List<String> = emptyList(),
    val busy: String? = null,
    val import: ImportState? = null,
    val message: Message? = null,
    val logs: List<LogInfo> = emptyList(),
    val openLog: Pair<String, String>? = null,
    val switching: Boolean = false,
    val refreshing: Boolean = false,
    /** A newer GalIOS release found on GitHub, when one exists and the user has not dismissed it. */
    val update: UpdateInfo? = null,
) {
    val rootOk get() = checks?.root == true
    /** Root is active and OneOS's iOS engine is installed: what adding anything to the phone needs. */
    val canStage get() = rootOk && engine?.hasIos == true
    /** Why files cannot be added to the phone yet, in words; null when they can. */
    val stageBlocker: String?
        get() = when {
            checks == null -> "Checking root…"
            !rootOk -> "Root is not active: start your temporary root, then come back."
            engine == null -> "OneOS (iOS 27 edition) is not installed: it is the boot engine (step 2)."
            !engine.hasIos -> "This OneOS build is not the iOS 27 edition (step 2)."
            else -> null
        }
    val readyToBoot get() = device.supported && checks?.allGood == true && engine?.hasIos == true && status?.ready == true && import == null
    val bootBlocker: String?
        get() = when {
            !device.supported -> "GalIOS boots iOS on the Galaxy S25 Ultra and S24 Ultra (Snapdragon) only; this is ${device.model}."
            checks == null -> "Checking the phone…"
            !checks.root -> "Root is not active. Start your temporary root, then pull to refresh."
            checks.busybox == null || checks.resetprop == null -> "Your root has no busybox or resetprop where the boot engine looks for them (see Setup, step 1)."
            engine == null -> "Install OneOS's iOS 27 edition: it is GalIOS's boot engine."
            !engine.hasIos -> "This OneOS build has no iOS 27 engine: install its iOS 27 edition."
            status?.runtimeVersion == null -> "Add the OneiOS runtime (Setup, step 3)."
            status.imageVersion == null -> "Add your iOS 27 image (Setup, step 4)."
            status.sessionOpen -> "An iOS session is still open."
            !status.ready -> status.notReadyReason ?: "The boot check did not pass."
            else -> null
        }
}

data class EngineInfo(val versionName: String, val versionCode: Long, val hasIos: Boolean, val apk: File)

data class ImportState(val title: String, val file: String?, val fileIndex: Int, val fileCount: Int, val done: Long, val total: Long) {
    val fraction get() = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
}

data class Message(val text: String, val error: Boolean)

class Controller(
    private val context: Context,
    private val rootPrefix: List<String> = listOf("su", "-c"),
    private val shell: RootShell = ProcessRootShell(rootPrefix),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stage = StageClient(shell)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    @Volatile private var cancelImport = false

    fun refresh() = scope.launch {
        _state.update { it.copy(refreshing = true) }
        val engine = findEngine()
        val checks = PhoneChecks.run(shell)
        val status = if (checks.root) stage.status().takeIf { it.error == null } else null
        val scan = if (checks.root) runCatching { ImageFinder.scan(shell) }.getOrNull() else null
        _state.update {
            it.copy(
                engine = engine, checks = checks, status = status,
                found = scan?.found ?: emptyList(), scannedFolders = scan?.scanned ?: emptyList(),
                engineInstalled = status?.stagePresent == true, refreshing = false,
                runtimeFile = downloadedRuntime()?.path,
            )
        }
        if (_state.value.release == null && !_state.value.releaseLooking) lookupRelease()
        checkForUpdate()
    }

    private val runtimeDir get() = File(context.filesDir, "runtime")

    /** The newest runtime package already downloaded into GalIOS's storage. */
    private fun downloadedRuntime(): File? =
        runtimeDir.listFiles { f -> f.isFile && f.name.startsWith("oneios-runtime-") && f.name.endsWith("-arm64.tar.zst") }
            ?.maxByOrNull { it.lastModified() }

    /** Finds the newest OneiOS runtime on GitHub. Needs only the internet, not root. */
    fun lookupRelease() = scope.launch {
        _state.update { it.copy(releaseLooking = true, releaseError = null) }
        val (r, err) = try {
            val found = RuntimeRelease.latest()
            found to (if (found == null) "OneiOS has no runtime release yet. Use Choose file if you have one." else null)
        } catch (e: Exception) {
            null to "Could not reach GitHub: ${e.message ?: e.javaClass.simpleName}. Check the internet connection and tap Try again."
        }
        _state.update { it.copy(release = r ?: it.release, releaseLooking = false, releaseError = err) }
    }

    private fun findEngine(): EngineInfo? = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(OneosEngine.PACKAGE, 0)
        val apk = File(info.applicationInfo!!.sourceDir)
        val e = OneosEngine(apk, info.versionName ?: "?", info.longVersionCode)
        EngineInfo(e.versionName, e.versionCode, e.hasIos(), apk)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun engine(): OneosEngine {
        val e = _state.value.engine ?: findEngine() ?: throw IllegalStateException("OneOS is not installed")
        return OneosEngine(e.apk, e.versionName, e.versionCode)
    }

    private fun run(label: String, block: suspend () -> String?) = scope.launch {
        _state.update { it.copy(busy = label, message = null) }
        val msg = try {
            block()?.let { Message(it, false) }
        } catch (e: CancelledException) {
            Message("Stopped. What was staged before is unchanged.", false)
        } catch (e: Exception) {
            Message(e.message ?: e.toString(), true)
        }
        _state.update { it.copy(busy = null, message = msg) }
        refresh()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun installEngine() = run("Installing the boot engine") {
        val done = EngineSetup.install(engine(), stage)
        if (done.switchAdapted) "Boot engine installed. OneOS's phone check now accepts your ${_state.value.device.label}." else "Boot engine installed."
    }

    fun findRelease() { lookupRelease() }

    /**
     * Downloads the runtime into GalIOS's own storage (checked against the release's SHA-256), then adds it to the
     * phone if root and OneOS are ready; otherwise it waits there for "Add to phone".
     */
    fun downloadRuntime() {
        val r = _state.value.release ?: run { lookupRelease(); return }
        if (_state.value.runtimeDownload != null) return
        cancelImport = false
        _state.update { it.copy(runtimeDownload = 0f, message = null) }
        scope.launch {
            val file = try {
                r.download(runtimeDir, { cancelImport }) { done, total ->
                    if (total > 0) _state.update { it.copy(runtimeDownload = (done.toFloat() / total).coerceIn(0f, 1f)) }
                }.also { f -> runtimeDir.listFiles()?.filter { it != f }?.forEach { it.delete() } }
            } catch (e: CancelledException) {
                _state.update { it.copy(runtimeDownload = null) }; return@launch
            } catch (e: Exception) {
                _state.update { it.copy(runtimeDownload = null, message = Message("Runtime download failed: ${e.message}", true)) }
                return@launch
            }
            _state.update { it.copy(runtimeDownload = null, runtimeFile = file.path) }
            if (_state.value.canStage) addRuntimeToPhone()
            else _state.update {
                it.copy(message = Message("OneiOS runtime ${r.version} is downloaded. ${it.stageBlocker ?: ""} Then tap Add to phone.".trim(), false))
            }
        }
    }

    /** Adds the downloaded runtime package to the phone's stage (root checks every file). */
    fun addRuntimeToPhone() {
        val f = _state.value.runtimeFile?.let(::File)?.takeIf { it.isFile } ?: return
        val version = Regex("""oneios-runtime-(.+)-arm64\.tar\.zst""").matchEntire(f.name)?.groupValues?.get(1) ?: "?"
        startImport(StageClient.Kind.RUNTIME, "OneiOS runtime $version") { java.io.FileInputStream(f) to f.length() }
    }

    fun importFile(kind: StageClient.Kind, uri: Uri) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null
        }
        startImport(kind, name?.first ?: "file") {
            (context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("could not open the file")) to (name?.second ?: -1)
        }
    }

    /** One tap: an archive found on the phone, read through root. */
    fun importFound(f: ImageFinder.Found) = startImport(f.kind, f.label) { RootFileStream(rootPrefix, f.path) to f.size }

    private fun startImport(kind: StageClient.Kind, title: String, open: () -> Pair<InputStream, Long>) {
        if (_state.value.import != null) return
        cancelImport = false
        _state.update { it.copy(import = ImportState(title, null, 0, 0, 0, 0), message = null) }
        context.startForegroundService(Intent(context, ImportService::class.java))
        scope.launch {
            val msg = try {
                // The engine first: an import needs the root script in the stage.
                if (_state.value.status?.stagePresent != true) EngineSetup.install(engine(), stage)
                val (input, _) = open()
                val m = input.use { s ->
                    PackageImport(stage, kind, { p ->
                        _state.update { it.copy(import = ImportState(p.label, p.file, p.fileIndex, p.fileCount, p.bytesDone, p.bytesTotal)) }
                    }, { cancelImport }).run(s)
                }
                Message("${m.label} is ready on the phone.", false)
            } catch (e: CancelledException) {
                Message("Stopped. What was staged before is unchanged.", false)
            } catch (e: Exception) {
                Message(e.message ?: e.toString(), true)
            }
            _state.update { it.copy(import = null, message = msg) }
            refresh()
        }
    }

    fun cancelImport() { cancelImport = true }

    fun boot() = run("Starting iOS") {
        // The session scripts again: another app may have staged its own since.
        EngineSetup.stageScripts(engine(), stage)
        val r = stage.switch(BuildConfigCompat.versionCode(context))
        if (!r.ok) throw IllegalStateException(r.message)
        _state.update { it.copy(switching = true) }
        null
    }

    fun returnToOneUi() = run("Asking for the return to One UI") {
        val r = stage.returnToOneUi()
        if (!r.ok) throw IllegalStateException(r.message)
        "The return is on its way (about 45 s)."
    }

    @Volatile private var updateDismissed = false

    /** Best-effort check of GitHub for a newer GalIOS; quiet on any failure (offline, rate limit). */
    fun checkForUpdate() = scope.launch {
        if (updateDismissed) return@launch
        val info = try {
            Updater.check(BuildConfigCompat.versionCode(context))
        } catch (_: Exception) {
            null
        }
        if (info != null && !updateDismissed) _state.update { it.copy(update = info) }
    }

    fun dismissUpdate() { updateDismissed = true; _state.update { it.copy(update = null) } }

    /** Allow installs first if needed, then download the APK and hand it to the system installer. */
    fun installUpdate() {
        val info = _state.value.update ?: return
        if (!UpdateInstaller.canInstall(context)) {
            context.startActivity(UpdateInstaller.allowInstallIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            _state.update { it.copy(message = Message("Allow GalIOS to install apps, then tap Update again.", false)) }
            return
        }
        run("Downloading GalIOS ${info.version}") {
            val apk = UpdateInstaller.apkFile(context)
            Updater.download(info, apk) { done, total ->
                if (total > 0) _state.update { it.copy(busy = "Downloading GalIOS ${info.version} · ${done * 100 / total}%") }
            }
            UpdateInstaller.install(context, apk)
            "Opening the installer for GalIOS ${info.version}."
        }
    }

    fun loadLogs() = scope.launch {
        val logs = try { stage.logs() } catch (_: Exception) { emptyList() }
        _state.update { it.copy(logs = logs) }
    }

    fun openLog(id: String, title: String) = scope.launch {
        val text = try { stage.logTail(id) } catch (e: Exception) { e.message ?: "" }
        _state.update { it.copy(openLog = title to text.ifBlank { "(empty)" }) }
    }

    fun closeLog() = _state.update { it.copy(openLog = null) }
}

object BuildConfigCompat {
    fun versionCode(context: Context): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
}
