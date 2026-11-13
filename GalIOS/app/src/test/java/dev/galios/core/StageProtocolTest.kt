package dev.galios.core

import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * GalIOS against OneOS's real root script on a stand-in phone: this machine as root, with getprop and getevent
 * stand-ins on PATH that report a Galaxy S25 Ultra, and a stand-in busybox at KernelSU's path. Runs only when the
 * build is given OneOS's scripts (-Dgalios.rootScript, -Dgalios.switchScript, -Dgalios.returnScript) and the stand-ins'
 * folder (-Dgalios.fakePath); OneOS's scripts are never part of GalIOS.
 */
class StageProtocolTest {
    private val rootScript = System.getProperty("galios.rootScript")?.let(::File)
    private val switchScript = System.getProperty("galios.switchScript")?.let(::File)
    private val returnScript = System.getProperty("galios.returnScript")?.let(::File)

    private fun ready() = rootScript?.isFile == true && switchScript?.isFile == true && returnScript?.isFile == true &&
        File("/data/adb/ksu/bin/busybox").canExecute() && File("/system/bin/sh").exists() &&
        ProcessRootShell.works(ProcessRootShell(listOf("sh", "-c")))

    /** A OneOS APK as far as GalIOS reads it: its assets. */
    private fun fakeOneos(dir: File): OneosEngine {
        val apk = File(dir, "oneos.apk")
        ZipOutputStream(apk.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry("assets/$name")); z.write(bytes); z.closeEntry() }
            put(OneosEngine.ROOT_SCRIPT, rootScript!!.readBytes())
            put(OneosEngine.SWITCH, switchScript!!.readBytes())
            put(OneosEngine.RETURN, returnScript!!.readBytes())
            for (a in OneosEngine.BOOT_SCREEN) put(a, "boot screen stand-in: $a\n".toByteArray())
        }
        return OneosEngine(apk, "0.4.86-test", 115)
    }

    /** A .tar.zst with its manifest first, as OneiOS packs both packages; [folder] wraps it in one folder. */
    private fun archive(kind: StageClient.Kind, files: Map<String, ByteArray>, facts: String, folder: String = "", shaOf: (String, ByteArray) -> String = { _, b -> StageClient.sha256(b) }): ByteArray {
        val list = files.entries.joinToString(",") { (p, b) ->
            val mode = if (p.startsWith("bin/")) ""","mode":"0755"""" else ""
            """{"path":"$p","size":${b.size},"sha256":"${shaOf(p, b)}"$mode}"""
        }
        val manifest = "{$facts,\"files\":[$list]}".toByteArray()
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(ZstdOutputStream(out)).use { t ->
            t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            fun put(name: String, b: ByteArray) {
                t.putArchiveEntry(TarArchiveEntry(folder + name).apply { size = b.size.toLong() }); t.write(b); t.closeArchiveEntry()
            }
            put(kind.manifest, manifest)
            for ((p, b) in files) put(p, b)
        }
        return out.toByteArray()
    }

    private val runtimeFiles = mapOf("bin/xnuhost" to "#!/bin/sh\necho stand-in\n".toByteArray(), "lib/readme.txt" to "runtime stand-in\n".toByteArray())
    private val runtimeFacts = """"format":1,"version":"0.1.8-test","session_abi":1,"xnuhost":"bin/xnuhost","image_formats":[1]"""
    private val imageFiles = mapOf("os.img" to ByteArray(300_000) { (it * 7).toByte() }, "overlay/userland/xnuhost.conf" to "test=1\n".toByteArray())
    private val imageFacts = """"format":1,"name":"oneios-image","os":"iOS","version":"27.0.1","build":"24A446","device":"iPhone18,2""""

    @Test
    fun galiosStagesAndTheAdaptedSwitchAcceptsTheS25Ultra() {
        assumeTrue("needs OneOS's scripts and the stand-in phone", ready())
        val dir = kotlin.io.path.createTempDirectory("galios").toFile()
        val stage = StageClient(ProcessRootShell(listOf("sh", "-c")))
        val engine = fakeOneos(dir)
        assertTrue(engine.hasIos())

        // A clean stage, then the engine as GalIOS installs it.
        stage.installRootScript(engine.read(OneosEngine.ROOT_SCRIPT)).orThrow()
        stage.verb("remove").orThrow()
        val done = EngineSetup.install(engine, stage)
        assertTrue("the stock switch script is adapted", done.switchAdapted)

        // The runtime, then the image (wrapped in a folder, as some archives are).
        val progress = mutableListOf<PackageImport.Progress>()
        val rt = PackageImport(stage, StageClient.Kind.RUNTIME, { progress += it }).run(ByteArrayInputStream(archive(StageClient.Kind.RUNTIME, runtimeFiles, runtimeFacts)))
        assertEquals("OneiOS runtime 0.1.8-test", rt.label)
        assertEquals(rt.totalBytes, progress.last().bytesDone)
        PackageImport(stage, StageClient.Kind.IMAGE).run(ByteArrayInputStream(archive(StageClient.Kind.IMAGE, imageFiles, imageFacts, folder = "OneiOS_iOS27/")))

        val s = stage.status()
        assertEquals("0.1.8-test", s.runtimeVersion)
        assertEquals("27.0.1", s.imageVersion)
        assertEquals("24A446", s.imageBuild)
        assertFalse(s.sessionOpen)
        // Past the phone check: the preflight now stops at the stand-in xnuhost, which is a shell script, not arm64.
        assertFalse(s.ready)
        assertEquals("xnuhost is not an arm64 program", s.notReadyReason)

        // OneOS's own (S24-only) switch script refuses this phone at its check: the adaptation is what lets it by.
        stage.stageScript("oneios_switch.sh", switchScript!!.readBytes()).orThrow()
        val stock = stage.status()
        assertTrue(stock.notReadyReason, stock.notReadyReason!!.contains("S24 Ultra") && stock.notReadyReason!!.contains("SM-S938B"))
        EngineSetup.stageScripts(engine, stage)
        assertEquals("xnuhost is not an arm64 program", stage.status().notReadyReason)

        // A damaged set is refused by root, and the staged image stays as it was.
        val bad = archive(StageClient.Kind.IMAGE, imageFiles, imageFacts.replace("27.0.1", "27.0.2")) { p, b -> if (p == "os.img") "0".repeat(64) else StageClient.sha256(b) }
        val err = runCatching { PackageImport(stage, StageClient.Kind.IMAGE).run(ByteArrayInputStream(bad)) }.exceptionOrNull()
        assertTrue("$err", err is ImportException && err.message!!.contains("did not arrive intact"))
        assertEquals("27.0.1", stage.status().imageVersion)

        // A cancel half way leaves it as it was too.
        var reads = 0
        val cancelled = runCatching {
            PackageImport(stage, StageClient.Kind.IMAGE, cancelled = { ++reads > 3 }).run(ByteArrayInputStream(archive(StageClient.Kind.IMAGE, imageFiles, imageFacts.replace("27.0.1", "27.0.3"))))
        }.exceptionOrNull()
        assertTrue("$cancelled", cancelled is CancelledException)
        assertEquals("27.0.1", stage.status().imageVersion)

        // Found on the phone and imported in one tap: a set in a "Download" folder, read through root by cat.
        val download = File(dir, "Download").apply { mkdirs() }
        val file = File(download, "OneiOS_iOS27.0.4_24A999_iPhone18-2.tar.zst")
        file.writeBytes(archive(StageClient.Kind.IMAGE, imageFiles, imageFacts.replace("27.0.1", "27.0.4").replace("24A446", "24A999")))
        val shell = ProcessRootShell(listOf("sh", "-c"))
        val scan = ImageFinder.scan(shell, listOf(dir.path, download.path))
        assertTrue(scan.scanned.contains(download.path))
        val found = scan.found.single { it.kind == StageClient.Kind.IMAGE }
        assertEquals("27.0.4", found.version)
        assertEquals(file.length(), found.size)
        RootFileStream(shell.prefix, found.path).use { PackageImport(stage, found.kind).run(it) }
        assertEquals("27.0.4", stage.status().imageVersion)
        assertEquals("24A999", stage.status().imageBuild)
        // A file root cannot read is an error, not an empty archive.
        assertTrue(runCatching { RootFileStream(shell.prefix, File(dir, "missing.tar.zst").path).use { PackageImport(stage, StageClient.Kind.IMAGE).run(it) } }.isFailure)
        assertEquals("27.0.4", stage.status().imageVersion)

        // The logs verb answers (no session has run on the stand-in).
        assertTrue(stage.verb("logs").ok)
        dir.deleteRecursively()
    }
}
