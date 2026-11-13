package dev.galios.core

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Finds OneiOS archives already on the phone, so they can be imported in one tap: the user's own iOS image sets
 * (`OneiOS_iOS<version>_<build>_<device>.tar.zst`, as OneiOS's image build names them) and runtime packages
 * (`oneios-runtime-<version>-arm64.tar.zst`). It only looks at files the user put there; it downloads nothing.
 * Root does the looking and the reading, so no storage permission is needed for an 11 GB file.
 */
object ImageFinder {
    /** Where people put big files: Download, Documents and the top of the shared storage, a little way down. */
    val DEFAULT_FOLDERS = listOf("/sdcard/Download", "/sdcard/Documents", "/sdcard")

    data class Found(val path: String, val size: Long, val kind: StageClient.Kind, val version: String?, val build: String?) {
        val name get() = path.substringAfterLast('/')
        val label: String
            get() = when (kind) {
                StageClient.Kind.IMAGE -> "iOS ${version ?: "?"}" + (build?.let { " ($it)" } ?: "")
                StageClient.Kind.RUNTIME -> "OneiOS runtime ${version ?: "?"}"
            }
    }

    private val IMAGE = Regex("""^OneiOS_iOS([0-9][0-9.]*)_([0-9A-Za-z]+)_.*\.tar\.zst$""")
    private val RUNTIME = Regex("""^oneios-runtime-(.+)-arm64\.tar\.zst$""")

    fun script(folders: List<String>): String {
        val dirs = folders.joinToString(" ") { shq(it) }
        // One line per archive: its size and path. find stays on one file system and two levels down.
        return "for d in $dirs; do [ -d \"\$d\" ] && find \"\$d\" -xdev -maxdepth 2 -type f -name '*.tar.zst' -exec stat -c '%s %n' {} + 2>/dev/null; done"
    }

    fun scan(shell: RootShell, folders: List<String> = DEFAULT_FOLDERS): List<Found> =
        parse(shell.exec(script(folders)).lines)

    /** The archives in `stat` lines, newest version first within a kind; the same file seen twice once. */
    fun parse(lines: List<String>): List<Found> {
        val out = LinkedHashMap<String, Found>()
        for (line in lines) {
            val sp = line.indexOf(' ')
            if (sp <= 0) continue
            val size = line.substring(0, sp).toLongOrNull() ?: continue
            val path = line.substring(sp + 1).trim()
            // /sdcard and /storage/emulated/0 are one folder: keep one row per file name and size.
            val name = path.substringAfterLast('/')
            val key = "$name:$size"
            if (key in out) continue
            IMAGE.matchEntire(name)?.let { m -> out[key] = Found(path, size, StageClient.Kind.IMAGE, m.groupValues[1], m.groupValues[2]) }
            RUNTIME.matchEntire(name)?.let { m -> out[key] = Found(path, size, StageClient.Kind.RUNTIME, m.groupValues[1], null) }
        }
        return out.values.sortedWith(compareBy<Found> { it.kind }.thenByDescending { versionKey(it.version) })
    }

    private fun versionKey(v: String?): String = v?.split('.', '-')?.joinToString(".") { it.padStart(6, '0') } ?: ""
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
