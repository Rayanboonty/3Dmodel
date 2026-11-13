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

class ProcessRootShell(private val prefix: List<String> = listOf("su", "-c")) : RootShell {
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
