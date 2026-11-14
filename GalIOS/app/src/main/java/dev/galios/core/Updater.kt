package dev.galios.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/**
 * A GalIOS release newer than the one installed, found on GitHub. [sumUrl] is the release's `GalIOS-<code>.apk.sha256`
 * when it has one: an automatic install needs it.
 */
data class UpdateInfo(
    val version: String,
    val versionCode: Long,
    val assetName: String,
    val url: String,
    val size: Long,
    val notes: String,
    val sumUrl: String? = null,
)

/**
 * Checks GitHub Releases of the GalIOS repository for a build with a higher versionCode than this one, and provides
 * the APK download. The release's APK asset is named `GalIOS-<versionCode>.apk` so the newest is found by its code,
 * not by a version string. With root, [RootUpdateInstaller] installs a download that matched its SHA-256 by itself;
 * without root, [UpdateInstaller] hands the file to Android's package installer, which asks the user.
 */
object Updater {
    /** owner/repo that publishes GalIOS releases. Overridable so a fork can point at its own. */
    const val REPO = "Rayanboonty/3Dmodel"
    private val ASSET = Regex("""GalIOS-(\d+)\.apk""", RegexOption.IGNORE_CASE)
    private val APP_TAG = Regex("""^galios-app-(\d+(?:\.\d+)*)""")

    // 100 a page: the repository also publishes other projects' releases, which would push GalIOS's off a short page.
    fun check(currentCode: Long, repo: String = REPO, api: String = "https://api.github.com/repos/$repo/releases?per_page=100"): UpdateInfo? =
        pick(Http.text(api, accept = "application/vnd.github+json"), currentCode)

    /** The newest release asset whose versionCode is higher than [currentCode]; null when none is. */
    fun pick(json: String, currentCode: Long): UpdateInfo? {
        val releases = Json.parseToJsonElement(json) as? JsonArray ?: return null
        var best: UpdateInfo? = null
        for (r in releases) {
            val o = r.jsonObject
            if ((o["draft"] as? JsonPrimitive)?.content == "true") continue
            val assets = o["assets"] as? JsonArray ?: continue
            val urls = assets.mapNotNull { a ->
                val ao = a.jsonObject
                val n = (ao["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val u = (ao["browser_download_url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                n to u
            }.toMap()
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
                // Release tags are galios-app-<versionName>-studio-<version> (the app and Studio of one build).
                val version = APP_TAG.find(tag)?.groupValues?.get(1) ?: tag.removePrefix("galios-").removePrefix("v")
                best = UpdateInfo(version, code, name, dl, size, notes, urls["$name.sha256"])
            }
        }
        return best
    }

    /** Downloads the APK to [dest]; [onProgress] gets bytes done and the total when known. */
    fun download(info: UpdateInfo, dest: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        Http.download(info.url, dest, onProgress = onProgress)
    }

    /** The SHA-256 a `.sha256` file names (its first word), lowercase; null when it is not one. */
    fun parseSum(text: String): String? =
        text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    /**
     * Downloads the APK and checks it against the release's SHA-256; [dest] is deleted and an error thrown when the
     * release has no sum or the file does not match it.
     */
    fun downloadChecked(info: UpdateInfo, dest: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val sumUrl = info.sumUrl ?: throw IOException("GalIOS ${info.version} has no SHA-256 beside it")
        val want = parseSum(Http.text(sumUrl)) ?: throw IOException("GalIOS ${info.version}'s SHA-256 file is not a SHA-256")
        val got = Http.download(info.url, dest, onProgress = onProgress)
        if (got != want) {
            dest.delete()
            throw IOException("GalIOS ${info.version} did not match its SHA-256: try again later")
        }
    }
}

/**
 * Installs a checked update with root (`pm install` reading the APK from its standard input), so it needs no
 * confirmation. Android still checks that the new APK is signed with the same key as the installed GalIOS: a GalIOS
 * built on another PC is refused ([Result.signedDifferently]); GalIOS Studio's Install GalIOS replaces it once.
 */
object RootUpdateInstaller {
    data class Result(val ok: Boolean, val signedDifferently: Boolean, val message: String)

    fun install(shell: RootShell, apk: File): Result {
        val r = apk.inputStream().use { input -> shell.exec("pm install -r -S ${apk.length()}", input) }
        return interpret(r)
    }

    fun interpret(r: RootShell.Result): Result {
        val text = r.lines.joinToString(" ").trim()
        return when {
            r.exitCode == 0 && r.lines.any { it.trim() == "Success" } -> Result(true, false, "installed")
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text || "signatures do not match" in text.lowercase() ->
                Result(false, true, text)
            else -> Result(false, false, text.ifEmpty { "pm install ended with code ${r.exitCode}" })
        }
    }
}
