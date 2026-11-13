package dev.galios.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/**
 * The OneiOS runtime (xnuhost and its helpers: OneiOS's own code, no Apple files) from OneiOS's GitHub releases.
 * Each release carries `oneios-runtime-<version>-arm64.tar.zst` and its `.sha256`. The iOS image is never
 * downloaded: it is built by its owner from Apple's firmware.
 */
data class RuntimeRelease(
    val tag: String,
    val name: String,
    val assetName: String,
    val url: String,
    val size: Long,
    /** The release's `.sha256` file, when it has one: the download is checked against it. */
    val shaUrl: String? = null,
) {
    /** The version in the file name (0.1.8 from oneios-runtime-0.1.8-arm64.tar.zst). */
    val version: String get() = ASSET.matchEntire(assetName)?.groupValues?.get(1) ?: tag.removePrefix("v")

    /**
     * Downloads the package to [dir] and checks it against the release's `.sha256`. Returns the file. A file already
     * there with the right SHA-256 is reused.
     */
    fun download(dir: File, cancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        dir.mkdirs()
        val dest = File(dir, assetName)
        val want = shaUrl?.let { u ->
            Http.text(u).trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { HEX64.matches(it) }
                ?: throw IOException("the release's .sha256 file is not a SHA-256")
        }
        if (dest.isFile && want != null && sha256(dest) == want) return dest
        val got = Http.download(url, dest, cancelled, onProgress)
        if (want != null && got != want) {
            dest.delete()
            throw IOException("the downloaded runtime does not match the release's SHA-256: download it again")
        }
        return dest
    }

    companion object {
        const val REPO = "akshaynexus/OneiOS"
        const val RELEASES_PAGE = "https://github.com/$REPO/releases"
        const val API = "https://api.github.com/repos/$REPO/releases?per_page=30"
        private val ASSET = Regex("""^oneios-runtime-(.+)-arm64\.tar\.zst$""")
        private val HEX64 = Regex("^[0-9a-f]{64}$")

        /** The newest release (prereleases included) that carries an arm64 runtime package; null when none does. */
        fun latest(api: String = API): RuntimeRelease? = pick(Http.text(api, accept = "application/vnd.github+json"))

        /** The choice, apart from the network (tested on its own). */
        fun pick(json: String): RuntimeRelease? {
            val releases = Json.parseToJsonElement(json) as? JsonArray ?: return null
            for (r in releases) {
                val o = r.jsonObject
                if ((o["draft"] as? JsonPrimitive)?.content == "true") continue
                val assets = (o["assets"] as? JsonArray)?.map { it.jsonObject } ?: continue
                for (ao in assets) {
                    val name = (ao["name"] as? JsonPrimitive)?.content ?: continue
                    if (!ASSET.matches(name)) continue
                    val url = (ao["browser_download_url"] as? JsonPrimitive)?.content ?: continue
                    val size = (ao["size"] as? JsonPrimitive)?.longOrNull ?: 0
                    val sha = assets.firstOrNull { (it["name"] as? JsonPrimitive)?.content == "$name.sha256" }
                        ?.let { (it["browser_download_url"] as? JsonPrimitive)?.content }
                    val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: ""
                    val title = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: tag
                    return RuntimeRelease(tag, title, name, url, size, sha)
                }
            }
            return null
        }

        fun sha256(f: File): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
