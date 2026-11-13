package dev.galios.core

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Finds OneiOS archives already on the phone, so they can be imported in one tap: the user's own iOS image sets
 * (`OneiOS_iOS<version>_<build>_<device>.tar.zst`, as OneiOS's image build names them) and runtime packages
 * (`oneios-runtime-<version>-arm64.tar.zst`). It downloads nothing and only looks at files the user put there.
 *
 * It lists **every** `.tar.zst` it finds, not only ones whose name matches exactly: root checks the manifest when it
 * is imported, so a file that is not a real set fails then with a clear reason. A name it recognises is labelled with
 * its version; any other `.tar.zst` is offered by its file name. This is on purpose -- a slightly different name, or a
 * file the user renamed, still shows up.
 */
object ImageFinder {
    /**
     * Where people keep big files. Both `/sdcard/...` and the `/storage/emulated/0/...` it points at are listed,
     * because on some phones one works where the other does not, and the top of each is scanned a few levels down.
     * `/data/local/tmp` is where `adb push` lands. Duplicates (the same file by two paths) are merged in [parse].
     */
    val DEFAULT_FOLDERS = listOf(
        "/sdcard/Download", "/sdcard/Documents", "/sdcard",
        "/storage/emulated/0/Download", "/storage/emulated/0/Documents", "/storage/emulated/0",
        "/data/local/tmp",
    )

    data class Found(val path: String, val size: Long, val kind: StageClient.Kind, val version: String?, val build: String?) {
        val name get() = path.substringAfterLast('/')
        val recognised get() = version != null
        val label: String
            get() = when {
                kind == StageClient.Kind.RUNTIME && version != null -> "OneiOS runtime $version"
                kind == StageClient.Kind.RUNTIME -> name
                version != null -> "iOS $version" + (build?.let { " ($it)" } ?: "")
                else -> name
            }
    }

    /** An Apple restore file on the phone: what the on-phone build can start from. */
    data class Ipsw(val path: String, val size: Long) {
        val name get() = path.substringAfterLast('/')
        /** The one OneiOS's profile pins: iPhone 17 Pro Max, iOS 27.0.1, build 24A446. */
        val isPinned get() = name.equals(PINNED_IPSW, ignoreCase = true)
    }

    const val PINNED_IPSW = "iPhone18,2_27.0.1_24A446_Restore.ipsw"

    /** What a scan did, for showing the user when nothing was found. */
    data class Scan(val found: List<Found>, val scanned: List<String>, val error: String?, val ipsws: List<Ipsw> = emptyList())

    private val IMAGE = Regex("""OneiOS_iOS([0-9][0-9.]*)_([0-9A-Za-z]+)_.*\.tar\.zst""", RegexOption.IGNORE_CASE)
    private val RUNTIME = Regex("""oneios-runtime-(.+)-arm64\.tar\.zst""", RegexOption.IGNORE_CASE)

    /**
     * Lists the folders that exist, then every `*.tar.zst` under them (three levels), each as `size<TAB>path`, with a
     * `DIR<TAB>` line for each folder it looked in. No `find` flag beyond the common ones; the trailing slash makes
     * `find` follow a folder that is a symlink (`/sdcard` is one), and each file's size comes from `stat` in a loop,
     * which every Android `stat` supports (the `-exec ... {} +` form does not always work).
     */
    fun script(folders: List<String>): String {
        val sb = StringBuilder()
        for (d in folders) {
            val q = shq(d)
            sb.append("if [ -d $q ]; then echo \"DIR\t$d\"; ")
            sb.append("find $q/ -maxdepth 3 -type f \\( -name '*.tar.zst' -o -name '*.ipsw' \\) 2>/dev/null | while IFS= read -r f; do ")
            sb.append("s=\$(stat -c %s \"\$f\" 2>/dev/null || stat -f %z \"\$f\" 2>/dev/null); echo \"\$s\t\$f\"; done; fi\n")
        }
        return sb.toString()
    }

    fun scan(shell: RootShell, folders: List<String> = DEFAULT_FOLDERS): Scan {
        val r = try {
            shell.exec(script(folders))
        } catch (e: Exception) {
            return Scan(emptyList(), emptyList(), e.message ?: "the search could not run")
        }
        return parseScan(r.lines)
    }

    /** The parsed result: the archives, the folders looked in, and an error only if the shell said nothing at all. */
    fun parseScan(lines: List<String>): Scan {
        val scanned = lines.filter { it.startsWith("DIR\t") }.map { it.removePrefix("DIR\t") }
        return Scan(parse(lines), scanned, null, parseIpsws(lines))
    }

    /** The .ipsw files, the pinned one first; the same file seen by two paths once. */
    fun parseIpsws(lines: List<String>): List<Ipsw> {
        val out = LinkedHashMap<String, Ipsw>()
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0 || line.startsWith("DIR\t")) continue
            val size = line.substring(0, tab).trim().toLongOrNull() ?: continue
            val path = line.substring(tab + 1).trim()
            if (!path.endsWith(".ipsw", ignoreCase = true)) continue
            out.putIfAbsent("${path.substringAfterLast('/')}:$size", Ipsw(path, size))
        }
        return out.values.sortedWith(compareByDescending<Ipsw> { it.isPinned }.thenBy { it.name })
    }

    /** The archives in `size<TAB>path` lines, recognised ones first then by name; the same file seen twice once. */
    fun parse(lines: List<String>): List<Found> {
        val out = LinkedHashMap<String, Found>()
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0 || line.startsWith("DIR\t")) continue
            val size = line.substring(0, tab).trim().toLongOrNull() ?: continue
            val path = line.substring(tab + 1).trim()
            if (!path.endsWith(".tar.zst")) continue
            val name = path.substringAfterLast('/')
            val key = "$name:$size"
            if (key in out) continue
            val runtime = RUNTIME.find(name)
            val image = IMAGE.find(name)
            out[key] = when {
                runtime != null -> Found(path, size, StageClient.Kind.RUNTIME, runtime.groupValues[1], null)
                image != null -> Found(path, size, StageClient.Kind.IMAGE, image.groupValues[1], image.groupValues[2])
                name.contains("runtime", ignoreCase = true) -> Found(path, size, StageClient.Kind.RUNTIME, null, null)
                else -> Found(path, size, StageClient.Kind.IMAGE, null, null)
            }
        }
        return out.values.sortedWith(
            compareBy<Found> { it.kind }.thenByDescending { it.recognised }.thenByDescending { versionKey(it.version) }.thenBy { it.name },
        )
    }

    private fun versionKey(v: String?): String = v?.split('.', '-')?.joinToString(".") { it.filter(Char::isDigit).padStart(6, '0') } ?: ""
}

/** Reads a file as root, as a stream (`cat`): for archives the app itself may not open. */
class RootFileStream(prefix: List<String>, path: String) : FilterInputStream(null) {
    // stderr goes nowhere in the shell itself: an unread error stream could fill its pipe and stall cat.
    private val process: Process = ProcessBuilder(prefix + "cat ${shq(path)} 2>/dev/null").start()

    init {
        `in` = process.inputStream
        process.outputStream.close()
    }

    private var ended = false

    override fun read(): Int = super.read().also { if (it < 0) end() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it < 0) end() }

    /** The end of the stream is only an end when `cat` exited 0; otherwise the file could not be read whole. */
    private fun end() {
        if (ended) return
        ended = true
        val code = process.waitFor()
        if (code != 0) throw IOException("root could not read the file (cat exited $code)")
    }

    override fun close() {
        process.destroy()
        super.close()
    }
}
