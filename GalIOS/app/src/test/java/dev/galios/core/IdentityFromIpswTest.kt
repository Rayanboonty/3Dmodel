package dev.galios.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** iPhone screen features from an IPSW on the phone: the entry, the IM4P, the device tree check, the whole path. */
class IdentityFromIpswTest {
    // A small flattened device tree: a root with "model", "compatible" and one child.
    private fun adt(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun prop(name: String, value: ByteArray) {
            val n = ByteArray(32); name.toByteArray().copyInto(n); out.write(n)
            u32(value.size); out.write(value); repeat((4 - value.size % 4) % 4) { out.write(0) }
        }
        u32(2); u32(1)
        prop("model", "iPhone18,2\u0000".toByteArray())
        prop("compatible", "V54AP\u0000iPhone18,2\u0000AppleARM\u0000".toByteArray())
        u32(1); u32(0)
        prop("name", "product\u0000".toByteArray())
        return out.toByteArray()
    }

    private fun der(tag: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        when {
            body.size < 0x80 -> out.write(body.size)
            body.size < 0x100 -> { out.write(0x81); out.write(body.size) }
            body.size < 0x10000 -> { out.write(0x82); out.write(body.size shr 8); out.write(body.size and 0xff) }
            else -> { out.write(0x83); out.write(body.size shr 16); out.write((body.size shr 8) and 0xff); out.write(body.size and 0xff) }
        }
        out.write(body)
        return out.toByteArray()
    }

    private fun im4p(payload: ByteArray, keybag: Boolean = false): ByteArray =
        der(0x30, der(0x16, "IM4P".toByteArray()) + der(0x16, "dtre".toByteArray()) + der(0x16, "DeviceTree".toByteArray()) +
            der(0x04, payload) + (if (keybag) der(0x04, der(0x30, der(0x02, byteArrayOf(1)))) else ByteArray(0)))

    @Test
    fun theEntryIsThisIphonesBoard() {
        val names = listOf("BuildManifest.plist", "Firmware/all_flash/DeviceTree.v53ap.im4p", "Firmware/all_flash/DeviceTree.v54ap.im4p", "kernelcache.release.v54")
        assertEquals("Firmware/all_flash/DeviceTree.v54ap.im4p", IdentityFromIpsw.findEntry(names))
        assertNull(IdentityFromIpsw.findEntry(listOf("Firmware/all_flash/DeviceTree.d84ap.im4p")))
    }

    @Test
    fun thePayloadComesOutOfTheIm4pAndIsADeviceTree() {
        val tree = adt()
        assertTrue(IdentityFromIpsw.looksLikeAdt(tree))
        assertArrayEquals(tree, IdentityFromIpsw.im4pPayload(im4p(tree)))
        // An encrypted one (a keybag after the payload) and things that are not IM4Ps are refused.
        try { IdentityFromIpsw.im4pPayload(im4p(tree, keybag = true)); fail("encrypted taken") } catch (_: IOException) {}
        try { IdentityFromIpsw.im4pPayload("hello".toByteArray()); fail("not DER taken") } catch (_: IOException) {}
        assertFalse(IdentityFromIpsw.looksLikeAdt(ByteArray(200) { (it * 37).toByte() }))
        assertFalse(IdentityFromIpsw.looksLikeAdt("bvx2".toByteArray() + ByteArray(100)))
    }

    @Test
    fun theWholePathFromAZip() {
        val dir = Files.createTempDirectory("ipsw").toFile()
        val ipsw = File(dir, "iPhone18,2_27.0.1_24A446_Restore.ipsw")
        ZipOutputStream(ipsw.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("Firmware/all_flash/DeviceTree.v53ap.im4p")); z.write(im4p(ByteArray(64))); z.closeEntry()
            z.putNextEntry(ZipEntry("Firmware/all_flash/DeviceTree.v54ap.im4p")); z.write(im4p(adt())); z.closeEntry()
        }
        val out = IdentityFromIpsw.extract(ipsw.path)
        assertArrayEquals(adt(), out)
        val dest = File(dir, "identity.adt")
        IdentityFromIpsw.save(out, dest)
        assertArrayEquals(adt(), dest.readBytes())

        // Another iPhone's IPSW, and a zip that is no IPSW at all: refused with what is wrong.
        val other = File(dir, "other.ipsw")
        ZipOutputStream(other.outputStream()).use { z -> z.putNextEntry(ZipEntry("Firmware/all_flash/DeviceTree.d84ap.im4p")); z.write(im4p(adt())); z.closeEntry() }
        try { IdentityFromIpsw.extract(other.path); fail("another iPhone taken") } catch (e: IOException) { assertTrue(e.message!!.contains("another iPhone")) }
        val notIpsw = File(dir, "photos.zip")
        ZipOutputStream(notIpsw.outputStream()).use { z -> z.putNextEntry(ZipEntry("a.jpg")); z.write(ByteArray(10)); z.closeEntry() }
        try { IdentityFromIpsw.extract(notIpsw.path); fail("not an IPSW taken") } catch (e: IOException) { assertTrue(e.message!!.contains("not an iPhone IPSW")) }
    }
}
