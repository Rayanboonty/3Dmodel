package dev.galios.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** GalIOS with root lent by Studio's root helper: the real tools/galios-rootd.sh, run here with sh. */
class RootHelperTest {
    private fun helper(): File = listOf(File("../tools/galios-rootd.sh"), File("tools/galios-rootd.sh")).first { it.exists() }

    @Test
    fun rootLentByTheHelperRunsCommandsAndStopsCleanly() {
        val q = Files.createTempDirectory("rootq").toFile()
        val state = Files.createTempDirectory("rootd").toFile()
        val shell = QueueRootShell(q)
        assertFalse("no helper yet", shell.alive())
        try {
            shell.exec("id -u")
            fail("ran without the helper")
        } catch (_: IOException) {
        }
        val pb = ProcessBuilder("sh", helper().absolutePath).redirectErrorStream(true)
        pb.environment()["GALIOS_ROOTQ"] = q.absolutePath
        pb.environment()["GALIOS_ROOTD_STATE"] = state.absolutePath
        val p = pb.start()
        try {
            var waited = 0
            while (!shell.alive() && waited < 5000) { Thread.sleep(100); waited += 100 }
            assertTrue("the helper says it is alive", shell.alive())

            val r = shell.exec("echo one; echo two >&2; exit 3")
            assertEquals(3, r.exitCode)
            assertEquals(listOf("one", "two"), r.lines.sorted())

            val data = "line a\nline b\n".repeat(10000)
            val c = shell.exec("wc -c", data.byteInputStream())
            assertEquals(data.length.toString(), c.lines.single().trim())

            // AutoRootShell picks the helper when su does not give root.
            val noSu = object : RootShell {
                override fun exec(command: String, stdin: java.io.InputStream?) = RootShell.Result(1, listOf("Permission denied"))
            }
            val auto = AutoRootShell(noSu, shell)
            assertEquals("helper", auto.via())
            assertEquals(listOf("ok"), auto.exec("echo ok").lines)
            assertTrue("nothing left behind", q.listFiles()!!.all { it.name == "alive" })

            File(state, "rootd.stop").writeText("")
            assertTrue("the helper stops when asked", p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
            q.resolve("alive").setLastModified(System.currentTimeMillis() - 60_000)
            assertFalse(shell.alive())
        } finally {
            p.destroy()
        }
    }
}
