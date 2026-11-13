package dev.galios.core

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The few HTTP calls GalIOS makes (GitHub's release list, release files). Every header is set before the
 * connection opens; redirects (GitHub sends file downloads to its file host) are followed by hand, up to five.
 */
object Http {
    fun get(url: String, accept: String? = null): HttpURLConnection {
        var target = URL(url)
        repeat(6) {
            val c = target.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 30_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "GalIOS")
            if (accept != null) c.setRequestProperty("Accept", accept)
            val code = c.responseCode
            when {
                code == 200 -> return c
                code in 300..399 -> {
                    val next = c.getHeaderField("Location") ?: throw IOException("${target.host} sent a redirect without a target")
                    c.disconnect()
                    target = URL(target, next)
                }
                code == 403 || code == 429 -> throw IOException("GitHub is limiting requests from this network right now (HTTP $code). Try again in a few minutes.")
                else -> throw IOException("${target.host} answered HTTP $code")
            }
        }
        throw IOException("too many redirects")
    }

    fun text(url: String, accept: String? = null): String = get(url, accept).inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

    /** Downloads [url] to [dest] (via a .part file), reporting bytes done and the total when known. Returns the SHA-256. */
    fun download(url: String, dest: File, cancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        val c = get(url)
        val total = c.contentLengthLong
        val part = File(dest.path + ".part")
        val md = MessageDigest.getInstance("SHA-256")
        var done = 0L
        c.inputStream.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    if (cancelled()) throw CancelledException()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
        if (total > 0 && done != total) { part.delete(); throw IOException("the download stopped at $done of $total bytes") }
        if (!part.renameTo(dest)) { part.delete(); throw IOException("could not save ${dest.name}") }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
