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
import dev.galios.core.PhoneBuild
import dev.galios.core.PhoneChecks
import dev.galios.core.AutoRootShell
import dev.galios.core.ProcessRootShell
import dev.galios.core.QueueRootShell
import dev.galios.core.RootFileStream
import dev.galios.core.RootShell
import dev.galios.core.LogSaver
import dev.galios.core.RuntimeRelease
import dev.galios.core.StageClient
import dev.galios.core.StageStatus
import dev.galios.core.UpdateInfo
import dev.galios.core.UpdateInstaller
import dev.galios.core.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Everything the screens show. */
data class UiState(
    val device: Device = Device(Build.MODEL ?: "", Build.DEVICE ?: "", Build.SOC_MODEL ?: ""),
    val checks: PhoneChecks? = null,
    /** "su", or "helper" while root is lent by GalIOS Studio's root helper. */
    val rootVia: String? = null,
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
    /** The id of the log in the viewer (null for the phone build's log). */
    val openLogId: String? = null,
    val switching: Boolean = false,
    val refreshing: Boolean = false,
    /** A newer GalIOS release found on GitHub, when one exists and the user has not dismissed it. */
    val update: UpdateInfo? = null,
    /** The on-phone image build: its state, step and log (null until root has been asked). */
    val phoneBuild: PhoneBuild.Status? = null,
    /** Getting the on-phone build ready (downloading Ubuntu's base system), with a 0..1 fraction when known. */
    val phoneBuildPreparing: Pair<String, Float?>? = null,
    /** Apple restore files found on the phone, the pinned one first. */
    val ipsws: List<ImageFinder.Ipsw> = emptyList(),
    /** Free bytes on /data, where the on-phone build works. */
    val dataFree: Long? = null,
) {
    val phoneBuildRunning get() = phoneBuild?.state == PhoneBuild.Status.State.RUNNING

    val rootOk get() = checks?.root == true
    /** Root is active and OneOS's iOS engine is installed: what adding anything to the phone needs. */
    val canStage get() = rootOk && engine?.hasIos == true
    /** Why files cannot be added to the phone yet, in words; null when they can. */
    val stageBlocker: String?
        get() = when {
            checks == null -> "Checking root…"
            !rootOk -> "Root is not active: start your temporary root. If your root works only over USB, connect the phone to GalIOS Studio on your PC once: it lends GalIOS its root until the phone restarts."
            engine == null -> "OneOS (iOS 27 edition) is not installed: it is the boot engine (step 2)."
            !engine.hasIos -> "This OneOS build is not the iOS 27 edition (step 2)."
            else -> null
        }
    val readyToBoot get() = device.supported && checks?.allGood == true && engine?.hasIos == true && status?.ready == true && import == null
    val bootBlocker: String?
        get() = when {
            !device.supported -> "GalIOS boots iOS on the Galaxy S25 Ultra and S24 Ultra (Snapdragon) only; this is ${device.model}."
            checks == null -> "Checking the phone…"
            !checks.root -> "Root is not active. Start your temporary root (or connect the phone to GalIOS Studio once, if your root works only over USB), then pull to refresh."
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
    shellOverride: RootShell? = null,
) {
    /** su when it gives GalIOS root; otherwise the root GalIOS Studio lends over USB (its root helper). */
    private val queue = QueueRootShell(java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "rootq").apply { mkdirs() })
    private val auto = AutoRootShell(ProcessRootShell(rootPrefix), queue)
    private val shell: RootShell = shellOverride ?: auto
    /** "helper" while GalIOS uses the root lent by GalIOS Studio. */
    val rootVia: String? get() = if (shell === auto) auto.via() else "su"

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
        val build = if (checks.root) runCatching { PhoneBuild.status(shell, scripts()) }.getOrNull() else null
        val free = if (checks.root) runCatching { PhoneBuild.dataFree(shell) }.getOrNull() else null
        _state.update {
            it.copy(
                engine = engine, checks = checks, status = status, rootVia = rootVia,
                found = scan?.found ?: emptyList(), scannedFolders = scan?.scanned ?: emptyList(),
                engineInstalled = status?.stagePresent == true, refreshing = false,
                runtimeFile = downloadedRuntime()?.path,
                phoneBuild = build, ipsws = scan?.ipsws ?: emptyList(), dataFree = free,
            )
        }
        if (build?.state == PhoneBuild.Status.State.RUNNING) watchPhoneBuild()
        if (checks.root) autoSaveSessionLog(status)
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
            val found = RuntimeRelease.newest()
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
    fun importFound(f: ImageFinder.Found) {
        if (rootVia == "helper") {
            _state.update { it.copy(message = Message("With root lent by GalIOS Studio, add the image from Studio on your PC (Boot page, step 3).", true)) }
            return
        }
        startImport(f.kind, f.label) { RootFileStream(rootPrefix, f.path) to f.size }
    }

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

    // ---- The image build on the phone itself ----------------------------------------------------------------------

    /** The two build scripts, unpacked from the APK's assets (each time, so they match this GalIOS). */
    private fun scripts(): PhoneBuild.Scripts {
        val dir = File(context.filesDir, "galios-build").apply { mkdirs() }
        fun out(name: String) = File(dir, name).also { f ->
            context.assets.open("galios-build/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
            f.setReadable(true, false); f.setExecutable(true, false)
        }
        return PhoneBuild.Scripts(out("phone-build.sh"), out("build-ios-image-wsl.sh"))
    }

    @Volatile private var watcher: Job? = null

    /**
     * Starts (or resumes) building the image on this phone, from [ipsw] on the phone or, when null, from Apple's
     * servers. Downloads Ubuntu's base system the first time (checked against its SHA-256).
     */
    fun startPhoneBuild(ipsw: String?) = scope.launch {
        val s0 = _state.value
        if (!s0.rootOk) { _state.update { it.copy(message = Message("Start your temporary root first.", true)) }; return@launch }
        if (s0.phoneBuildRunning) return@launch
        val resuming = s0.phoneBuild?.state in setOf(PhoneBuild.Status.State.FAILED, PhoneBuild.Status.State.STOPPED)
        val free = PhoneBuild.dataFree(shell)
        if (!resuming && free != null && free < PhoneBuild.NEEDED_BYTES) {
            _state.update { it.copy(message = Message("The build needs about 75 GB free on the phone; ${dev.galios.ui.bytes(free)} is free.", true)) }
            return@launch
        }
        try {
            val scripts = scripts()
            var rootfs: File? = null
            if (!PhoneBuild.rootfsReady(shell)) {
                _state.update { it.copy(phoneBuildPreparing = "Downloading Ubuntu's base system" to 0f, message = null) }
                val f = File(context.cacheDir, PhoneBuild.ROOTFS_NAME)
                val sha = dev.galios.core.Http.download(PhoneBuild.ROOTFS_URL, f) { done, total ->
                    if (total > 0) _state.update { it.copy(phoneBuildPreparing = "Downloading Ubuntu's base system" to (done.toFloat() / total)) }
                }
                if (sha != PhoneBuild.ROOTFS_SHA256) { f.delete(); throw IllegalStateException("Ubuntu's base system did not match its SHA-256; try again") }
                f.setReadable(true, false)
                rootfs = f
            }
            _state.update { it.copy(phoneBuildPreparing = "Starting the build" to null) }
            val r = PhoneBuild.start(shell, scripts, rootfs, ipsw)
            if (!r.ok) throw IllegalStateException(r.message)
            rootfs?.delete()                                   // unpacked now; 30 MB back
            _state.update { it.copy(phoneBuildPreparing = null, message = Message("The build has started. It runs on its own; you can leave GalIOS and come back.", false)) }
            watchPhoneBuild()
        } catch (e: Exception) {
            _state.update { it.copy(phoneBuildPreparing = null, message = Message("Could not start the build: ${e.message}", true)) }
        }
    }

    /** Opens the whole on-phone build log in the log viewer (with Share). */
    fun openPhoneBuildLog() = scope.launch {
        val text = runCatching { PhoneBuild.fullLog(shell, scripts()) }.getOrElse { it.message ?: "" }
        _state.update { it.copy(openLog = "Phone build log" to text.ifBlank { "(the log is empty)" }) }
    }

    fun stopPhoneBuild() = scope.launch {
        runCatching { PhoneBuild.stop(shell, scripts()) }
        watcher?.cancel()
        refresh()
    }

    /** Reads the build's status every few seconds while it runs; imports the image when it is done. */
    private fun watchPhoneBuild() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            val scripts = scripts()
            while (true) {
                val st = runCatching { PhoneBuild.status(shell, scripts) }.getOrNull()
                if (st != null) _state.update { it.copy(phoneBuild = st) }
                if (st == null || st.state != PhoneBuild.Status.State.RUNNING) break
                delay(4000)
            }
            val end = _state.value.phoneBuild
            refresh().join()
            if (end?.state == PhoneBuild.Status.State.FAILED) {
                _state.update { it.copy(message = Message("The build stopped: ${end.failedStep ?: end.step ?: "see the log"}. Tap Show full log in step 4 and send it to me.", true)) }
            }
            if (end?.state == PhoneBuild.Status.State.DONE) {
                val image = _state.value.found.firstOrNull { it.kind == StageClient.Kind.IMAGE && it.recognised }
                when {
                    image == null -> _state.update { it.copy(message = Message("The build finished, but its image was not found in Download. Tap Look again.", true)) }
                    _state.value.canStage -> {
                        _state.update { it.copy(message = Message("Your iOS 27 image is built. Importing it now.", false)) }
                        importFound(image)
                    }
                    else -> _state.update { it.copy(message = Message("Your iOS 27 image is built. ${it.stageBlocker ?: ""} Then tap Import.".trim(), false)) }
                }
            }
        }
    }

    fun loadLogs() = scope.launch {
        val logs = try { stage.logs() } catch (_: Exception) { emptyList() }
        _state.update { it.copy(logs = logs) }
    }

    // The viewer shows the newest 128 KiB (a whole megabyte of text made the screen crawl); Save writes all of it.
    fun openLog(id: String, title: String) = scope.launch {
        val text = try { stage.logTail(id, 128 shl 10) } catch (e: Exception) { e.message ?: "" }
        _state.update { it.copy(openLog = title to text.ifBlank { "(empty)" }, openLogId = id) }
    }

    /** Writes the whole of log [id] to Downloads/GalIOS; [then] gets the file (to share it) when that worked. */
    fun saveLog(id: String, title: String, then: ((LogSaver.Saved) -> Unit)? = null) = scope.launch {
        _state.update { it.copy(message = Message("Saving \"$title\" to Downloads…", false)) }
        val msg = try {
            val saved = LogSaver.save(context, id, stage.logFull(id))
            then?.let { f -> withContext(Dispatchers.Main) { f(saved) } }
            Message("Saved: ${saved.path} (${saved.bytes / 1024} KB). Open the Files app, Downloads, GalIOS folder.", false)
        } catch (e: Exception) {
            Message("Could not save the log: ${e.message ?: e.javaClass.simpleName}", true)
        }
        _state.update { it.copy(message = msg) }
    }

    /** Save (and with [then], share) what the viewer shows: a stage log in full, the phone build's log as shown. */
    fun saveOpenLog(then: ((LogSaver.Saved) -> Unit)? = null) {
        val (title, text) = _state.value.openLog ?: return
        val id = _state.value.openLogId
        if (id != null) { saveLog(id, title, then); return }
        scope.launch {
            val msg = try {
                val saved = LogSaver.save(context, "phone-build", text)
                then?.let { f -> withContext(Dispatchers.Main) { f(saved) } }
                Message("Saved: ${saved.path} (${saved.bytes / 1024} KB)", false)
            } catch (e: Exception) {
                Message("Could not save the log: ${e.message ?: e.javaClass.simpleName}", true)
            }
            _state.update { it.copy(message = msg) }
        }
    }

    /**
     * After a session ends, its xnuhost log is saved to Downloads by itself, once per session (the log's time tells
     * them apart), so it is there even when the phone was too slow to open it.
     */
    private fun autoSaveSessionLog(status: StageStatus?) {
        if (status == null || status.sessionOpen) return
        val prefs = context.getSharedPreferences("galios", Context.MODE_PRIVATE)
        val log = runCatching { stage.logs() }.getOrNull()?.firstOrNull { it.id == "session" } ?: return
        if (log.size <= 0 || log.modified <= prefs.getLong("autosaved_session", 0)) return
        runCatching { LogSaver.save(context, "session", stage.logFull("session"), java.util.Date(log.modified * 1000)) }
            .onSuccess { saved ->
                prefs.edit().putLong("autosaved_session", log.modified).apply()
                _state.update { it.copy(message = Message("The last iOS session's log is saved: ${saved.path}", false)) }
            }
    }

    fun closeLog() = _state.update { it.copy(openLog = null, openLogId = null) }
}

object BuildConfigCompat {
    fun versionCode(context: Context): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
}
