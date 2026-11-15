package dev.galios.core

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * iPhone screen features without a PC: the iPhone18,2 device tree read straight from the owner's IPSW on the phone.
 * An IPSW is a zip; the tree for this iPhone's board (V54) is Firmware/all_flash/DeviceTree.v54ap.im4p, an IM4P: a DER
 * SEQUENCE { "IM4P", type, description, OCTET STRING payload, ... }. The payload is the flattened device tree (the
 * format xnuhost's loader reads) when it is neither compressed nor encrypted; anything else is refused with the reason,
 * never guessed at. The result is the same file the PC build makes (OneiOS's kernel-inputs.sh, im4p extract).
 */
object IdentityFromIpsw {
    /** The board of iPhone18,2 (the target-type in its device tree: V54, V54AP). */
    const val BOARD = "v54ap"

    /** The device tree entry of [names] for [BOARD], or null when the IPSW has none. */
    fun findEntry(names: List<String>, board: String = BOARD): String? {
        val trees = names.filter { n -> n.substringAfterLast('/').let { it.startsWith("DeviceTree.", true) && it.endsWith(".im4p", true) } }
        return trees.firstOrNull { it.substringAfterLast('/').equals("DeviceTree.$board.im4p", true) }
    }

    /** Reads the device tree out of the IPSW at [ipswPath] (a path, or /proc/self/fd/N of an opened document). */
    fun extract(ipswPath: String): ByteArray = ZipFile(ipswPath).use { zip ->
        val names = zip.entries().asSequence().map { it.name }.toList()
        val name = findEntry(names) ?: throw IOException(
            if (names.none { it.contains("DeviceTree", true) }) "This file is not an iPhone IPSW (it has no device trees)."
            else "This IPSW has no device tree for iPhone18,2 (board V54): it is for another iPhone. Use iPhone18,2_27.0.1_24A446_Restore.ipsw.",
        )
        val entry = zip.getEntry(name)
        if (entry.size > 8L shl 20) throw IOException("$name is ${entry.size} bytes: not a device tree")
        val im4p = zip.getInputStream(entry).use { it.readBytes() }
        val payload = im4pPayload(im4p)
        when {
            payload.size >= 4 && String(payload, 0, 4, Charsets.ISO_8859_1) == "bvx2" ->
                throw IOException("This IPSW's device tree is compressed (LZFSE). Make it with GalaxyIOS Studio's Do everything instead.")
            !looksLikeAdt(payload) -> throw IOException("This IPSW's device tree is encrypted or not in the expected format. Make it with GalaxyIOS Studio's Do everything instead.")
        }
        payload
    }

    /** The payload OCTET STRING of an IM4P (DER). Throws on anything that is not an IM4P. */
    fun im4pPayload(der: ByteArray): ByteArray {
        val r = Der(der)
        val seq = r.next()
        if (seq.tag != 0x30) throw IOException("not an IM4P (no SEQUENCE)")
        val inner = Der(der, seq.start, seq.start + seq.length)
        val magic = inner.next()
        if (magic.tag != 0x16 || String(der, magic.start, magic.length, Charsets.ISO_8859_1) != "IM4P") throw IOException("not an IM4P")
        inner.next()                                     // the type ("dtre")
        inner.next()                                     // the description
        val payload = inner.next()
        if (payload.tag != 0x04) throw IOException("the IM4P has no payload")
        if (inner.hasNext()) {
            val more = inner.next()
            // A keybag (an OCTET STRING of key SEQUENCEs) means the payload is encrypted.
            if (more.tag == 0x04) throw IOException("This IPSW's device tree is encrypted. Make it with GalaxyIOS Studio's Do everything instead.")
        }
        return der.copyOfRange(payload.start, payload.start + payload.length)
    }

    /**
     * A flattened device tree: the root node is { u32 nprops, u32 nchildren, props... }, each prop a 32-byte NUL-padded
     * name, a u32 length (high bit: a placeholder) and the value padded to 4 bytes. Checks the root's shape and that it
     * names a model or compatible, which an encrypted or compressed blob does not.
     */
    fun looksLikeAdt(b: ByteArray): Boolean {
        if (b.size < 8 + 36) return false
        fun u32(at: Int) = (b[at].toLong() and 0xff) or ((b[at + 1].toLong() and 0xff) shl 8) or ((b[at + 2].toLong() and 0xff) shl 16) or ((b[at + 3].toLong() and 0xff) shl 24)
        val nprops = u32(0)
        val nchildren = u32(4)
        if (nprops !in 1..512 || nchildren !in 1..256) return false
        var at = 8
        var named = false
        for (i in 0 until nprops.toInt()) {
            if (at + 36 > b.size) return false
            val nameEnd = (at until at + 32).firstOrNull { b[it] == 0.toByte() } ?: (at + 32)
            if (nameEnd == at) return false
            val name = String(b, at, nameEnd - at, Charsets.ISO_8859_1)
            if (!name.all { it.code in 0x20..0x7e }) return false
            if (name == "compatible" || name == "model" || name == "target-type") named = true
            val len = (u32(at + 32) and 0x7fffffffL).toInt()
            if (len < 0 || len > b.size) return false
            at += 36 + ((len + 3) and 3.inv())
        }
        return named
    }

    /** Writes [adt] to [dest] (via a .tmp file). */
    fun save(adt: ByteArray, dest: File) {
        val tmp = File(dest.path + ".tmp")
        tmp.writeBytes(adt)
        if (!tmp.renameTo(dest)) { tmp.delete(); throw IOException("could not save ${dest.name}") }
    }

    /** A minimal DER reader: one element at a time, its tag and where its contents are. */
    private class Der(private val b: ByteArray, private var at: Int = 0, private val end: Int = b.size) {
        data class El(val tag: Int, val start: Int, val length: Int)

        fun hasNext() = at < end

        fun next(): El {
            if (at + 2 > end) throw IOException("the IM4P ends early")
            val tag = b[at].toInt() and 0xff
            var len = b[at + 1].toInt() and 0xff
            var p = at + 2
            if (len and 0x80 != 0) {
                val n = len and 0x7f
                if (n == 0 || n > 4 || p + n > end) throw IOException("the IM4P has a bad length")
                len = 0
                repeat(n) { len = (len shl 8) or (b[p++].toInt() and 0xff) }
            }
            if (len < 0 || p + len > end) throw IOException("the IM4P has a bad length")
            at = p + len
            return El(tag, p, len)
        }
    }
}
