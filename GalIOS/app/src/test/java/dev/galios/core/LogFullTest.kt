package dev.galios.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/**
 * StageClient.logFull against OneOS's real root script (its logfile verb, 1 MiB a call) on a log of a few MiB: every
 * line back, in order, none twice. The script runs as `nobody` in its test mode (ONEIOS_TEST_ROOT), which it allows
 * only when not root. Runs when the build is given the script (-Dgalios.rootScript) and this machine has `su nobody`.
 */
class LogFullTest {
    private val rootScript = System.getProperty("galios.rootScript")?.let(::File)

    private class Nobody(val testRoot: String) : RootShell {
        val inner = ProcessRootShell(listOf("su", "nobody", "-s", "/bin/sh", "-c"))
        override fun exec(command: String, stdin: InputStream?) = inner.exec("export ONEIOS_TEST_ROOT='$testRoot'; $command", stdin)
    }

    @Test
    fun theWholeLogComesBackInOrder() {
        assumeTrue(rootScript?.isFile == true && File("/usr/bin/su").canExecute() && System.getProperty("user.name") == "root")
        val root = Files.createTempDirectory("galios-logfull").toFile().apply { setReadable(true, false); setExecutable(true, false) }
        assertEquals(0, ProcessBuilder("chown", "nobody", root.path).start().waitFor())
        val shell = Nobody(root.path)
        val stage = "${root.path}/data/local/tmp/oneios"
        // The stage, the script and a 3.5 MiB session log, all nobody's (the script checks they are its owner's).
        val lines = (1..40000).map { "[%9.3f] xnuhost I line %06d of the session, some text to make it about ninety bytes long".format(it / 10.0, it) }
        val log = File(root, "xnuhost.log").apply { writeText(lines.joinToString("\n") + "\n"); setReadable(true, false) }
        File(root, "r.sh").apply { writeBytes(rootScript!!.readBytes()); setReadable(true, false) }
        val setup = shell.inner.exec(
            "umask 077; mkdir -p '$stage/bin' '$stage/session' && cp '${root.path}/r.sh' '$stage/bin/oneios_root.sh' && " +
                "cp '${log.path}' '$stage/session/xnuhost.log' && echo done", null,
        )
        assertTrue(setup.lines.joinToString("\n"), setup.lines.contains("done"))
        val text = StageClient(shell, stage).logFull("session")
        val got = text.split("\n").filter { it.isNotEmpty() }
        assertEquals(lines.size, got.size)
        assertEquals(lines, got)
        assertTrue(log.length() > 3L shl 20)
    }
}
