package dev.galios.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Start iOS fresh: the Data store (and a half-made one) goes, the chip ID beside it stays, an empty stage is fine. */
class ResetDataTest {
    private val shell = ProcessRootShell(listOf("sh", "-c"))

    @Test
    fun theStoreGoesTheChipIdStays() {
        val stage = Files.createTempDirectory("galios-reset").toFile()
        val data = File(stage, "data").apply { mkdirs() }
        File(data, "data.img").writeText("store")
        File(data, "data.img.new-123").writeText("half")
        File(data, "unique-chip-id").writeText("12345678")
        val r = StageClient(shell, stage.path).resetData()
        assertTrue(r.message, r.ok)
        assertEquals("reset 2", r.message)
        assertFalse(File(data, "data.img").exists())
        assertFalse(File(data, "data.img.new-123").exists())
        assertTrue(File(data, "unique-chip-id").exists())
        // Again, and on a stage that has no data folder: nothing to do, still fine.
        assertTrue(StageClient(shell, stage.path).resetData().ok)
        assertTrue(StageClient(shell, File(stage, "nothing").path).resetData().ok)
    }
}

/** Maximum performance: the switch is the file galios-max in the stage, on and off again. */
class MaxPerformanceTest {
    @Test
    fun theSwitchIsAFileInTheStage() {
        val shell = ProcessRootShell(listOf("sh", "-c"))
        val stage = Files.createTempDirectory("galios-max").toFile()
        val client = StageClient(shell, stage.path)
        assertFalse(client.maxPerformance())
        assertTrue(client.setMaxPerformance(true).ok)
        assertTrue(File(stage, "galios-max").exists() && client.maxPerformance())
        assertTrue(client.setMaxPerformance(false).ok)
        assertFalse(File(stage, "galios-max").exists() || client.maxPerformance())
        assertFalse(StageClient(shell, File(stage, "missing").path).setMaxPerformance(true).ok)
    }
}

/** iPhone screen features: the identity file is found where people keep files, copied into the stage after a size check, removed again. */
class IphoneIdentityTest {
    @Test
    fun theIdentityIsFoundCheckedCopiedAndRemoved() {
        val shell = ProcessRootShell(listOf("sh", "-c"))
        val stage = Files.createTempDirectory("galios-identity").toFile()
        val downloads = Files.createTempDirectory("galios-download").toFile()
        val client = StageClient(shell, stage.path)
        val folders = listOf(File(downloads, "Documents").path, downloads.path)

        assertFalse(client.iphoneIdentity())
        assertEquals(null, client.findIdentityFile(folders))
        // Nothing to copy: refused, nothing in the stage.
        assertFalse(client.setIphoneIdentity(true, File(downloads, StageClient.IDENTITY_FILE).path).ok)
        assertFalse(File(stage, StageClient.IDENTITY_IN_STAGE).exists())

        // A file too small to be an iPhone device tree: refused.
        val tree = File(downloads, StageClient.IDENTITY_FILE)
        tree.writeBytes(ByteArray(100) { 7 })
        val found = client.findIdentityFile(folders)
        assertEquals(tree.path, found)
        val small = client.setIphoneIdentity(true, found)
        assertFalse(small.message, small.ok)
        assertTrue(small.message, small.message.contains("not an iPhone device tree"))
        assertFalse(client.iphoneIdentity())

        // One of a real tree's size: copied whole, and the switch reads on.
        val bytes = ByteArray(200 * 1024) { (it % 251).toByte() }
        tree.writeBytes(bytes)
        val on = client.setIphoneIdentity(true, client.findIdentityFile(folders))
        assertTrue(on.message, on.ok)
        assertTrue(client.iphoneIdentity())
        assertTrue(File(stage, StageClient.IDENTITY_IN_STAGE).readBytes().contentEquals(bytes))
        assertFalse(File(stage, StageClient.IDENTITY_IN_STAGE + ".tmp").exists())

        // Off removes it; a stage that does not exist refuses on.
        assertTrue(client.setIphoneIdentity(false).ok)
        assertFalse(client.iphoneIdentity() || File(stage, StageClient.IDENTITY_IN_STAGE).exists())
        assertFalse(StageClient(shell, File(stage, "missing").path).setIphoneIdentity(true, tree.path).ok)
    }
}
