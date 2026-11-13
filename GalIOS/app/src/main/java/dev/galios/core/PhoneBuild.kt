package dev.galios.core

import java.io.File

/**
 * Building the iOS 27 image set on the phone itself, without a PC: `phone-build.sh` (an asset) sets up Ubuntu 24.04
 * for arm64 in [HOME] and runs the same build script a PC runs, detached, as root. This is the GalIOS side: what it
 * needs, how to start, stop and read it.
 */
object PhoneBuild {
    const val HOME = "/data/local/galios-build"
    const val ROOTFS_NAME = "ubuntu-base-24.04.5-base-arm64.tar.gz"
    const val ROOTFS_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$ROOTFS_NAME"
    const val ROOTFS_SHA256 = "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"

    /** Free space the build needs on /data: the iOS parts, the build, the 11 GB image and its copy. */
    const val NEEDED_BYTES = 75L shl 30

    /** The two scripts, unpacked from GalIOS's assets into [dir]. */
    class Scripts(val runner: File, val build: File)

    data class Status(
        val state: State,
        val step: String?,
        val exit: String?,
        val startedSec: Long?,
        val log: List<String>,
    ) {
        enum class State { IDLE, RUNNING, DONE, FAILED, STOPPED }

        val failedStep: String? get() = step?.takeIf { it.startsWith("failed: ") }?.removePrefix("failed: ")

        companion object {
            fun parse(lines: List<String>): Status {
                fun v(k: String) = lines.firstOrNull { it.startsWith("$k=") }?.substringAfter('=')?.trim()?.ifBlank { null }
                val state = when (v("state")) {
                    "running" -> State.RUNNING
                    "done" -> State.DONE
                    "failed" -> State.FAILED
                    "stopped" -> State.STOPPED
                    else -> State.IDLE
                }
                return Status(
                    state, v("step"), v("exit"), v("started")?.toLongOrNull(),
                    lines.filter { it.startsWith("| ") || it == "|" }.map { it.removePrefix("|").removePrefix(" ") },
                )
            }
        }
    }

    private fun env(scripts: Scripts) = "GALIOS_HOME=${shq(HOME)}"

    fun status(shell: RootShell, scripts: Scripts): Status =
        Status.parse(shell.exec("${env(scripts)} sh ${shq(scripts.runner.path)} status").lines)

    /** True once Ubuntu is unpacked: the [ROOTFS_NAME] download is then not needed again. */
    fun rootfsReady(shell: RootShell): Boolean =
        shell.exec("[ -f ${shq("$HOME/rootfs/.galios-ok")} ] && echo yes").lines.any { it.trim() == "yes" }

    /** Free bytes on /data, as root sees it. */
    fun dataFree(shell: RootShell): Long? =
        shell.exec("stat -f -c '%a %S' /data").lines.firstOrNull()?.trim()?.split(' ')
            ?.takeIf { it.size == 2 }?.let { (a, s) -> a.toLongOrNull()?.let { n -> s.toLongOrNull()?.let { n * it } } }

    /** Starts the build. [rootfs] only when Ubuntu is not set up yet; [ipsw] null downloads the parts from Apple. */
    fun start(shell: RootShell, scripts: Scripts, rootfs: File?, ipsw: String?): StageClient.Reply {
        val vars = buildList {
            add(env(scripts))
            add("BUILD_SCRIPT=${shq(scripts.build.path)}")
            rootfs?.let { add("ROOTFS_TAR=${shq(it.path)}") }
            ipsw?.let { add("IPSW=${shq(it)}") }
        }.joinToString(" ")
        return reply(shell.exec("$vars sh ${shq(scripts.runner.path)} start"))
    }

    /** The whole build log (its last 3000 lines), without terminal colours. */
    fun fullLog(shell: RootShell, scripts: Scripts): String =
        shell.exec("${env(scripts)} sh ${shq(scripts.runner.path)} log").lines
            .filter { it.startsWith("| ") || it == "|" }.joinToString("\n") { it.removePrefix("|").removePrefix(" ") }

    fun stop(shell: RootShell, scripts: Scripts): StageClient.Reply =
        reply(shell.exec("${env(scripts)} sh ${shq(scripts.runner.path)} stop"))

    /** The runner answers @@OK or @@ERROR <why>. */
    private fun reply(r: RootShell.Result): StageClient.Reply {
        val err = r.lines.lastOrNull { it.startsWith("@@ERROR ") }?.removePrefix("@@ERROR ")
        val ok = err == null && r.lines.any { it.startsWith("@@OK") }
        val message = err ?: if (ok) "ok" else (r.lines.lastOrNull { it.isNotBlank() } ?: "no answer from root (exit ${r.exitCode})")
        return StageClient.Reply(ok, message, emptyList(), r.lines, r.exitCode)
    }
}
