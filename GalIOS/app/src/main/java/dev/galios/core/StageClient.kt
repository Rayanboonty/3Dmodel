package dev.galios.core

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * The OneiOS stage on the phone (/data/local/tmp/oneios), driven through OneOS's own root script
 * (`oneios_root.sh`, read from the installed OneOS app). Every verb answers in `@@OK`, `@@REFUSED`, `@@FAILED`
 * and `@@RESULT` lines; root checks every file's size and SHA-256 and refuses everything during a session.
 */
class StageClient(private val shell: RootShell, val stage: String = DEFAULT_STAGE) {
    val rootScript = "$stage/bin/oneios_root.sh"

    /** One verb of the root script. [args] are words the script accepts (it validates each one again). */
    fun verb(vararg args: String, stdin: InputStream? = null): Reply {
        val command = "sh ${shq(rootScript)} " + args.joinToString(" ") { shq(it) }
        return Reply.parse(shell.exec(command, stdin))
    }

    /**
     * Puts the root script in the stage the way OneOS's launcher does: the stage and its bin/ root's own and 0700,
     * the script written from stdin, checked against its SHA-256, then moved in place. Refuses if something is
     * mounted in the stage (a session that did not end cleanly: restart the phone first).
     */
    fun installRootScript(script: ByteArray): Reply {
        val sha = sha256(script)
        val s = shq(stage)
        val command = """
            umask 077
            S=$s; F="${'$'}S/bin/oneios_root.sh"
            for m in /proc/1/mountinfo /proc/self/mountinfo; do
                awk '${'$'}5 == "/" { f = 1 } END { exit !f }' "${'$'}m" 2>/dev/null || { echo "@@REFUSED could not read ${'$'}m"; exit 2; }
            done
            if awk -v s="${'$'}S" '${'$'}5 == s || index(${'$'}5, s "/") == 1 { f = 1 } END { exit !f }' /proc/1/mountinfo /proc/self/mountinfo; then
                echo "@@REFUSED something is mounted in the stage: restart the phone first"; exit 2
            fi
            if [ -L "${'$'}S" ] || { [ -e "${'$'}S" ] && [ ! -d "${'$'}S" ]; }; then echo "@@REFUSED ${'$'}S is not a folder"; exit 2; fi
            [ -d "${'$'}S" ] || mkdir "${'$'}S" || { echo "@@FAILED could not make the stage"; exit 4; }
            chown 0:0 "${'$'}S" && chmod 0700 "${'$'}S" || { echo "@@FAILED could not make the stage root's"; exit 4; }
            if [ -L "${'$'}S/bin" ] || { [ -e "${'$'}S/bin" ] && [ ! -d "${'$'}S/bin" ]; }; then
                mv "${'$'}S/bin" "${'$'}S/bin.old-${'$'}(date +%s)" || { echo "@@FAILED could not move the old bin aside"; exit 4; }
            fi
            [ -d "${'$'}S/bin" ] || mkdir "${'$'}S/bin" || { echo "@@FAILED could not make bin"; exit 4; }
            chown 0:0 "${'$'}S/bin" && chmod 0700 "${'$'}S/bin" || { echo "@@FAILED could not make bin root's"; exit 4; }
            rm -f "${'$'}F.new"
            head -c ${script.size} > "${'$'}F.new" || { rm -f "${'$'}F.new"; echo "@@FAILED could not write the root script"; exit 4; }
            [ "${'$'}(sha256sum "${'$'}F.new" 2>/dev/null | cut -d' ' -f1)" = $sha ] || { rm -f "${'$'}F.new"; echo "@@REFUSED the root script did not arrive intact"; exit 2; }
            chown 0:0 "${'$'}F.new" && chmod 0700 "${'$'}F.new" && mv -f "${'$'}F.new" "${'$'}F" || { echo "@@FAILED could not install the root script"; exit 4; }
            echo "@@OK install"
        """.trimIndent()
        return Reply.parse(shell.exec(command, ByteArrayInputStream(script)))
    }

    fun status(): StageStatus = StageStatus.parse(verb("status"))

    /** A session script or a boot screen file (oneios_switch.sh, oneios_return.sh, boot_status.sh, ...). */
    fun stageScript(name: String, bytes: ByteArray): Reply =
        verb("script", name, bytes.size.toString(), sha256(bytes), stdin = ByteArrayInputStream(bytes))

    fun begin(kind: Kind) = verb("begin", kind.word)

    fun receive(kind: Kind, path: String, size: Long, sha256: String, mode: String, data: InputStream) =
        verb("receive", kind.word, path, size.toString(), sha256, mode, stdin = data)

    fun finish(kind: Kind, files: Int, facts: List<String>) = verb("finish", kind.word, files.toString(), *facts.toTypedArray())

    /** Starts iOS: the switch's preflight, then the session, detached. One UI stops about 40 s later. */
    fun switch(versionCode: Long) = verb("switch", versionCode.toString(), "1")

    fun returnToOneUi() = verb("return")

    fun logs(): List<LogInfo> = LogInfo.parse(verb("logs"))

    /** The last [bytes] (1 MiB at most) of log [id], whole lines. */
    fun logTail(id: String, bytes: Int = 1 shl 20): String {
        val r = verb("logfile", id, "0", bytes.coerceIn(1, 1 shl 20).toString())
        return r.lines.filter { it.startsWith("| ") || it == "|" }.joinToString("\n") { it.removePrefix("|").removePrefix(" ") }
    }

    /**
     * The whole of log [id], read 1 MiB at a time from its end backwards (the verb gives whole lines and where each
     * chunk starts). A log longer than [maxBytes] keeps its newest [maxBytes].
     */
    fun logFull(id: String, maxBytes: Long = 64L shl 20): String {
        val parts = ArrayDeque<String>()
        var end = 0L
        var total = 0L
        while (true) {
            val r = verb("logfile", id, end.toString(), (1 shl 20).toString())
            if (!r.ok && parts.isEmpty()) throw IllegalStateException(r.message.ifBlank { "could not read the log" })
            val head = r.lines.firstOrNull { it.startsWith("@@RESULT chunk ") }?.split(' ') ?: break
            val start = head.getOrNull(4)?.toLongOrNull() ?: break
            val chunkEnd = head.getOrNull(5)?.toLongOrNull() ?: break
            val text = r.lines.filter { it.startsWith("| ") || it == "|" }.joinToString("\n") { it.removePrefix("|").removePrefix(" ") }
            parts.addFirst(text)
            total += chunkEnd - start
            if (start <= 0 || total >= maxBytes || (end != 0L && start >= end)) break
            end = start
        }
        return parts.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /**
     * Erases iOS's Data store (`data/data.img`, and a half-made one), so the next boot makes a new store from the
     * image's seed, as the first boot did: what an image's seed brings that an older store lacks (the capture plug-in)
     * comes with it. The guest's chip ID beside it is kept. Never during a session (the caller checks the status).
     */
    fun resetData(): Reply {
        val d = shq("$stage/data")
        val command = """
            d=$d
            [ -d "${'$'}d" ] || { echo "@@OK reset 0"; exit 0; }
            n=0
            for f in "${'$'}d"/data.img "${'$'}d"/data.img.*; do
                [ -e "${'$'}f" ] || continue
                rm -f "${'$'}f" || { echo "@@FAILED could not delete ${'$'}f"; exit 4; }
                n=${'$'}((n + 1))
            done
            echo "@@OK reset ${'$'}n"
        """.trimIndent()
        return Reply.parse(shell.exec(command, null))
    }

    enum class Kind(val word: String, val manifest: String) { RUNTIME("runtime", "runtime.json"), IMAGE("image", "manifest.json") }

    /** What a verb answered. [ok] when its last @@ line is @@OK. */
    data class Reply(val ok: Boolean, val message: String, val results: List<String>, val lines: List<String>, val exitCode: Int) {
        companion object {
            fun parse(r: RootShell.Result): Reply {
                val tags = r.lines.filter { it.startsWith("@@") }
                val last = tags.lastOrNull { !it.startsWith("@@RESULT") }
                val ok = last != null && last.startsWith("@@OK")
                val message = when {
                    last == null -> r.lines.lastOrNull { it.isNotBlank() }?.trim() ?: "no answer from root (exit ${r.exitCode})"
                    else -> last.substringAfter(' ', "").ifBlank { last }
                }
                return Reply(ok, message, tags.filter { it.startsWith("@@RESULT ") }.map { it.removePrefix("@@RESULT ") }, r.lines, r.exitCode)
            }
        }
    }

    companion object {
        const val DEFAULT_STAGE = "/data/local/tmp/oneios"

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** `status`, read. */
data class StageStatus(
    val stagePresent: Boolean,
    val runtimeVersion: String?,
    val imageVersion: String?,
    val imageBuild: String?,
    val imageBytes: Long?,
    val sessionOpen: Boolean,
    val sessionState: String?,
    val lastOutcome: String?,
    val ready: Boolean,
    val notReadyReason: String?,
    val error: String? = null,
) {
    companion object {
        val UNKNOWN = StageStatus(false, null, null, null, null, false, null, null, false, null)

        fun parse(reply: StageClient.Reply): StageStatus {
            if (!reply.ok && reply.results.isEmpty()) return UNKNOWN.copy(error = reply.message)
            var s = UNKNOWN
            for (r in reply.results) {
                val w = r.split(' ')
                when (w.firstOrNull()) {
                    "stage" -> s = s.copy(stagePresent = w.getOrNull(1) == "present")
                    "runtime" -> s = s.copy(runtimeVersion = w.getOrNull(1)?.takeIf { it != "none" })
                    "image" -> if (w.getOrNull(1) != "none") s = s.copy(
                        imageVersion = w.getOrNull(2), imageBuild = w.getOrNull(3), imageBytes = w.getOrNull(4)?.toLongOrNull(),
                    )
                    "session" -> s = s.copy(sessionOpen = w.getOrNull(1) == "open", sessionState = w.getOrNull(2))
                    "outcome" -> s = s.copy(lastOutcome = r.removePrefix("outcome ").takeIf { it != "none" })
                    "ready" -> s = s.copy(ready = w.getOrNull(1) == "1", notReadyReason = if (w.getOrNull(1) == "1") null else r.removePrefix("ready 0").trim())
                }
            }
            return s
        }
    }
}

/** One row of `logs`: a log's id, size, the time of its last write and its last lines. */
data class LogInfo(val id: String, val size: Long, val modified: Long, val tail: List<String>) {
    val title: String
        get() = when {
            id == "session" -> "This session (xnuhost)"
            id.startsWith("xnuhost-") -> "Earlier session (xnuhost)"
            id == "switch" -> "Switch"
            id == "return" -> "Return to One UI"
            id == "display" -> "Display helper"
            id == "feeder" -> "Boot screen"
            else -> id
        }

    companion object {
        fun parse(reply: StageClient.Reply): List<LogInfo> {
            val out = mutableListOf<LogInfo>()
            for (line in reply.lines) {
                if (line.startsWith("@@RESULT log ")) {
                    val w = line.removePrefix("@@RESULT log ").split(' ')
                    if (w.size >= 3) out += LogInfo(w[0], w[1].toLongOrNull() ?: 0, w[2].toLongOrNull() ?: 0, emptyList())
                } else if (line.startsWith("| ") && out.isNotEmpty()) {
                    val last = out.last()
                    val text = line.removePrefix("| ")
                    if (text.startsWith(last.id + " ") || text == last.id) {
                        out[out.size - 1] = last.copy(tail = last.tail + text.removePrefix(last.id).removePrefix(" "))
                    }
                }
            }
            return out
        }
    }
}
