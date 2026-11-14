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
