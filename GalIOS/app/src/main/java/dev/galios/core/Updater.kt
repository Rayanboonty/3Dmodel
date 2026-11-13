package dev.galios.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** A GalIOS release newer than the one installed, found on GitHub. */
data class UpdateInfo(val version: String, val versionCode: Long, val assetName: String, val url: String, val size: Long, val notes: String)

/**
 * Checks GitHub Releases of the GalIOS repository for a build with a higher versionCode than this one, and provides
 * the APK download. The release's APK asset is named `GalIOS-<versionCode>.apk` so the newest is found by its code,
 * not by a version string. Nothing is installed automatically: [UpdateInstaller] hands the file to Android's package
 * installer, which asks the user.
 */
object Updater {
    /** owner/repo that publishes GalIOS releases. Overridable so a fork can point at its own. */
    const val REPO = "Rayanboonty/3Dmodel"
    private val API = "https://api.github.com/repos/$REPO/releases?per_page=30"
    private val ASSET = Regex("""GalIOS-(\d+)\.apk""", RegexOption.IGNORE_CASE)

    fun check(currentCode: Long, repo: String = REPO): UpdateInfo? {
        val url = "https://api.github.com/repos/$repo/releases?per_page=30"
        val c = open(url)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        return pick(body, currentCode)
    }

    /** The newest release asset whose versionCode is higher than [currentCode]; null when none is. */
    fun pick(json: String, currentCode: Long): UpdateInfo? {
        val releases = Json.parseToJsonElement(json) as? JsonArray ?: return null
        var best: UpdateInfo? = null
        for (r in releases) {
            val o = r.jsonObject
            if ((o["draft"] as? JsonPrimitive)?.content == "true") continue
            val assets = o["assets"] as? JsonArray ?: continue
            for (a in assets) {
                val ao = a.jsonObject
                val name = (ao["name"] as? JsonPrimitive)?.content ?: continue
                val m = ASSET.matchEntire(name) ?: continue
                val code = m.groupValues[1].toLongOrNull() ?: continue
                if (code <= currentCode) continue
                if (best != null && code <= best.versionCode) continue
                val dl = (ao["browser_download_url"] as? JsonPrimitive)?.content ?: continue
                val size = (ao["size"] as? JsonPrimitive)?.longOrNull ?: 0
                val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: "$code"
                val notes = ((o["body"] as? JsonPrimitive)?.content ?: "").take(600)
                best = UpdateInfo(tag.removePrefix("galios-").removePrefix("v"), code, name, dl, size, notes)
            }
        }
        return best
    }

    fun download(info: UpdateInfo): Pair<InputStream, Long> {
        val c = open(info.url)
        return c.inputStream to (c.contentLengthLong.takeIf { it > 0 } ?: info.size)
    }

    private fun open(url: String): HttpURLConnection {
        var target = URL(url)
        repeat(5) {
            val c = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000; readTimeout = 60_000
                setRequestProperty("User-Agent", "GalIOS")
            }
            when (val code = c.responseCode) {
                200 -> return c
                in 300..399 -> {
                    val next = c.getHeaderField("Location") ?: throw IOException("redirect without a target")
                    c.disconnect(); target = URL(target, next)
                }
                else -> throw IOException("GitHub answered HTTP $code")
            }
        }
        throw IOException("too many redirects")
    }
}
