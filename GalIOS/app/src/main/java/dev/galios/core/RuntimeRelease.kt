package dev.galios.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The OneiOS runtime (xnuhost and its helpers: OneiOS's own code, no Apple files) from OneiOS's GitHub releases.
 * The iOS image is never downloaded: it is built by its owner from Apple's firmware.
 */
data class RuntimeRelease(val tag: String, val name: String, val assetName: String, val url: String, val size: Long) {
    /** Opens the download (GitHub answers with a redirect to its file host, which this follows). */
    fun open(): Pair<InputStream, Long> {
        val c = connect(url)
        return c.inputStream to (c.contentLengthLong.takeIf { it > 0 } ?: size)
    }

    companion object {
        const val REPO = "akshaynexus/OneiOS"
        const val RELEASES_PAGE = "https://github.com/$REPO/releases"
        private const val API = "https://api.github.com/repos/$REPO/releases?per_page=20"
        private val ASSET = Regex("""^oneios-runtime-.+-arm64\.tar\.zst$""")

        /** The newest release (prereleases included) that carries an arm64 runtime package; null when none does. */
        fun latest(): RuntimeRelease? {
            val c = connect(API)
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return pick(body)
        }

        /** The choice, apart from the network (tested on its own). */
        fun pick(json: String): RuntimeRelease? {
            val releases = Json.parseToJsonElement(json) as? JsonArray ?: return null
            for (r in releases) {
                val o = r.jsonObject
                if ((o["draft"] as? JsonPrimitive)?.content == "true") continue
                val assets = o["assets"] as? JsonArray ?: continue
                for (a in assets) {
                    val ao = a.jsonObject
                    val name = (ao["name"] as? JsonPrimitive)?.content ?: continue
                    if (!ASSET.matches(name)) continue
                    val url = (ao["browser_download_url"] as? JsonPrimitive)?.content ?: continue
                    val size = (ao["size"] as? JsonPrimitive)?.longOrNull ?: 0
                    val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: ""
                    val title = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: tag
                    return RuntimeRelease(tag, title, name, url, size)
                }
            }
            return null
        }

        private fun connect(url: String): HttpURLConnection {
            var target = URL(url)
            repeat(5) {
                val c = target.openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 30_000
                c.readTimeout = 60_000
                c.setRequestProperty("User-Agent", "GalIOS")
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: throw IOException("GitHub sent a redirect without a target")
                    c.disconnect()
                    target = URL(target, next)
                    return@repeat
                }
                if (code != 200) throw IOException("GitHub answered HTTP $code for ${target.host}")
                return c
            }
            throw IOException("too many redirects")
        }
    }
}
