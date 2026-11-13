package dev.galios.core

import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

/** Runs shell commands as root. The app uses `su -c`; the tests use a plain `sh -c` on a stand-in phone. */
interface RootShell {
    /**
     * Runs [command] and returns its output lines (stdout and stderr together) and exit code. [stdin], when given,
     * is copied to the command's standard input and then the input is closed; [stdin] itself is never closed.
     */
    fun exec(command: String, stdin: InputStream? = null): Result

    data class Result(val exitCode: Int, val lines: List<String>)
}

class ProcessRootShell(val prefix: List<String> = listOf("su", "-c")) : RootShell {
    override fun exec(command: String, stdin: InputStream?): RootShell.Result {
        val process = ProcessBuilder(prefix + command).redirectErrorStream(true).start()
        val feedError = AtomicReference<Throwable?>(null)
        val feeder = Thread {
            try {
                process.outputStream.use { out ->
                    if (stdin != null) {
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val n = stdin.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                }
            } catch (t: Throwable) {
                feedError.set(t)
                // The reader below ends when the command does; a command that stopped reading has refused already.
            }
        }.apply { name = "galios-root-stdin"; isDaemon = true; start() }
        val lines = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).readLines()
        val code = process.waitFor()
        feeder.join()
        feedError.get()?.let { t ->
            // An input that failed for its own reasons (a cancel, a broken download) is the caller's error, not root's.
            if (t is CancelledException || t !is IOException) throw t
            if (lines.none { it.startsWith("@@") }) throw IOException("root stopped reading its input: ${t.message}", t)
        }
        return RootShell.Result(code, lines)
    }

    companion object {
        /** True when `su` gives uid 0. */
        fun works(shell: RootShell): Boolean = try {
            shell.exec("id -u").lines.lastOrNull()?.trim() == "0"
        } catch (_: Exception) {
            false
        }
    }
}

class CancelledException : IOException("cancelled")

/** An input stream that counts what was read, can be cancelled, and is never closed by its reader. */
class MeteredStream(input: InputStream, private val onBytes: (Long) -> Unit, private val cancelled: () -> Boolean) :
    FilterInputStream(input) {
    override fun read(): Int {
        if (cancelled()) throw CancelledException()
        val b = super.read()
        if (b >= 0) onBytes(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (cancelled()) throw CancelledException()
        val n = super.read(b, off, len)
        if (n > 0) onBytes(n.toLong())
        return n
    }

    override fun close() {}
}

/** One shell word: single-quoted, with any single quote inside closed and escaped. */
fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

/**
 * Root lent by GalIOS Studio's root helper (tools/galios-rootd.sh), for temporary roots that give root to adb's shell
 * but not to apps. GalIOS writes a script into [dir] (its own folder on the shared storage, which only GalIOS, adb and
 * root can write to); the helper, started as root over USB by Studio, runs it and leaves <id>.out and <id>.rc beside
 * it, and touches [dir]/alive every second while it runs (until the phone restarts).
 */
class QueueRootShell(val dir: java.io.File, private val freshMillis: Long = 15_000) : RootShell {
    fun alive(): Boolean {
        val f = java.io.File(dir, "alive")
        return f.exists() && System.currentTimeMillis() - f.lastModified() < freshMillis
    }

    override fun exec(command: String, stdin: InputStream?): RootShell.Result {
        if (!alive()) throw IOException("GalIOS Studio's root helper is not running (connect the phone to Studio once)")
        val id = "q" + System.nanoTime().toString(36) + (100..999).random()
        val input = java.io.File(dir, "$id.in")
        var script = command
        try {
            if (stdin != null) {
                input.outputStream().use { out -> stdin.copyTo(out, 1 shl 20) }
                script = "( $command ) < ${shq(input.absolutePath)}"
            }
            val tmp = java.io.File(dir, "$id.sh.tmp")
            tmp.writeText(script + "\n")
            if (!tmp.renameTo(java.io.File(dir, "$id.sh"))) throw IOException("could not queue the root command in $dir")
            val rc = java.io.File(dir, "$id.rc")
            var wait = 50L
            while (!rc.exists()) {
                if (!alive() && !java.io.File(dir, "$id.run").exists() && !rc.exists()) {
                    // The helper stopped (the phone restarted, or Studio stopped it) before it took the command.
                    if (java.io.File(dir, "$id.sh").delete()) throw IOException("GalIOS Studio's root helper stopped")
                }
                Thread.sleep(wait)
                if (wait < 500) wait += 50
            }
            val code = rc.readText().trim().toIntOrNull() ?: -1
            val lines = java.io.File(dir, "$id.out").takeIf { it.exists() }?.readLines() ?: emptyList()
            return RootShell.Result(code, lines)
        } finally {
            for (ext in listOf("in", "sh", "sh.tmp", "out", "rc", "run")) java.io.File(dir, "$id.$ext").delete()
        }
    }

    companion object {
        fun works(shell: QueueRootShell): Boolean = shell.alive() && ProcessRootShell.works(shell)
    }
}

/** su when it gives root to GalIOS; otherwise Studio's root helper while it runs. Chosen again every 20 s. */
class AutoRootShell(private val su: RootShell, private val queue: QueueRootShell) : RootShell {
    @Volatile private var chosen: RootShell? = null
    @Volatile private var chosenAt = 0L

    /** Which root GalIOS uses: "su", "helper" (lent by Studio), or null (none). */
    fun via(): String? = when (pick()) {
        null -> null
        queue -> "helper"
        else -> "su"
    }

    private fun pick(): RootShell? {
        val now = System.currentTimeMillis()
        chosen?.let { if (now - chosenAt < 20_000 && (it !== queue || queue.alive())) return it }
        val c = when {
            ProcessRootShell.works(su) -> su
            queue.alive() && ProcessRootShell.works(queue) -> queue
            else -> null
        }
        chosen = c
        chosenAt = now
        return c
    }

    override fun exec(command: String, stdin: InputStream?): RootShell.Result = (pick() ?: su).exec(command, stdin)
}
