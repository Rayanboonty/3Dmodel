package dev.galios.core

import com.github.luben.zstd.ZstdInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** A runtime package's `runtime.json` or an image set's `manifest.json`: the files it lists and the facts finish needs. */
data class PackageManifest(val kind: StageClient.Kind, val raw: ByteArray, val files: List<Entry>, val facts: List<String>, val label: String) {
    data class Entry(val path: String, val size: Long, val sha256: String, val mode: String)

    val totalBytes: Long get() = files.sumOf { it.size }

    companion object {
        private val HEX64 = Regex("^[0-9a-f]{64}$")
        private val PATH = Regex("^[A-Za-z0-9._+,-]+(/[A-Za-z0-9._+,-]+)*$")

        fun parse(kind: StageClient.Kind, raw: ByteArray): PackageManifest {
            val j = try {
                Json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
            } catch (e: Exception) {
                throw ImportException("${kind.manifest} is not valid JSON")
            }
            val files = (j["files"] as? JsonArray ?: throw ImportException("${kind.manifest} lists no files")).map { e ->
                val o = e.jsonObject
                val path = o.str("path") ?: throw ImportException("a file in ${kind.manifest} has no path")
                val size = (o["size"] as? JsonPrimitive)?.longOrNull ?: throw ImportException("$path has no size")
                val sha = o.str("sha256") ?: throw ImportException("$path has no SHA-256")
                val mode = o.str("mode") ?: "0644"
                if (!PATH.matches(path) || path.split('/').any { it == "." || it == ".." }) throw ImportException("$path is not a plain relative path")
                if (!HEX64.matches(sha)) throw ImportException("$path: the SHA-256 is not 64 lowercase hex digits")
                if (mode != "0644" && mode != "0755") throw ImportException("$path: mode $mode")
                if (path == kind.manifest) throw ImportException("${kind.manifest} lists itself")
                Entry(path, size, sha, mode)
            }
            if (files.isEmpty()) throw ImportException("${kind.manifest} lists no files")
            if (files.map { it.path }.toSet().size != files.size) throw ImportException("${kind.manifest} lists a file twice")
            val facts: List<String>
            val label: String
            when (kind) {
                StageClient.Kind.RUNTIME -> {
                    val version = j.str("version") ?: throw ImportException("runtime.json has no version")
                    val abi = j.num("session_abi") ?: throw ImportException("runtime.json has no session_abi")
                    val xnuhost = j.str("xnuhost") ?: throw ImportException("runtime.json names no xnuhost")
                    val formats = (j["image_formats"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.longOrNull } ?: emptyList()
                    if (formats.isEmpty()) throw ImportException("runtime.json names no image formats")
                    val placeholder = (j["placeholder"] as? JsonPrimitive)?.content == "true"
                    facts = listOf(version, abi.toString(), xnuhost, formats.joinToString(","), if (placeholder) "1" else "0")
                    label = "OneiOS runtime $version"
                }
                StageClient.Kind.IMAGE -> {
                    val format = j.num("format") ?: throw ImportException("manifest.json has no format")
                    val version = j.str("version") ?: throw ImportException("manifest.json has no version")
                    val build = j.str("build") ?: throw ImportException("manifest.json has no build")
                    facts = listOf(format.toString(), version, build)
                    label = "iOS $version ($build)"
                }
            }
            return PackageManifest(kind, raw, files, facts, label)
        }

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.longOrNull
    }
}

class ImportException(message: String) : IOException(message)

/**
 * Imports a OneiOS runtime package or image set (`.tar.zst`, its manifest the first file, as OneiOS packs both) into
 * the stage in one pass: each file goes from the archive straight to root's `receive`, which checks its size and
 * SHA-256; `finish` then puts the whole set in place, or nothing. A set that fails half way leaves the staged one as
 * it was.
 */
class PackageImport(
    private val stage: StageClient,
    private val kind: StageClient.Kind,
    private val onProgress: (Progress) -> Unit = {},
    private val cancelled: () -> Boolean = { false },
) {
    data class Progress(val label: String, val file: String?, val fileIndex: Int, val fileCount: Int, val bytesDone: Long, val bytesTotal: Long)

    /** [archive] is the raw `.tar.zst`. Returns the manifest that was staged. */
    fun run(archive: InputStream): PackageManifest {
        val tar = TarArchiveInputStream(ZstdInputStream(BufferedInputStream(archive, 1 shl 20)))
        // The manifest first (at the top, or in one wrapping folder).
        var first = tar.nextEntry ?: throw ImportException("the archive is empty")
        while (first.isDirectory) first = tar.nextEntry ?: throw ImportException("the archive holds only folders")
        val firstName = first.name.removePrefix("./")
        val prefix = when {
            firstName == kind.manifest -> ""
            firstName.endsWith("/" + kind.manifest) && firstName.count { it == '/' } == 1 -> firstName.substringBefore('/') + "/"
            else -> throw ImportException(
                "this is not a ${if (kind == StageClient.Kind.RUNTIME) "OneiOS runtime package" else "iOS image set"}: " +
                    "its first file is $firstName, not ${kind.manifest}",
            )
        }
        if (first.size > (4 shl 20)) throw ImportException("${kind.manifest} is too large")
        val manifest = PackageManifest.parse(kind, tar.readNBytes(first.size.toInt()))
        val wanted = manifest.files.associateBy { it.path }
        val done = HashSet<String>()
        var bytesDone = 0L
        onProgress(Progress(manifest.label, null, 0, manifest.files.size, 0, manifest.totalBytes))

        stage.begin(kind).orThrow()
        while (true) {
            if (cancelled()) throw CancelledException()
            val e = tar.nextEntry ?: break
            if (e.isDirectory) continue
            val name = e.name.removePrefix("./")
            if (!name.startsWith(prefix)) throw ImportException("$name is outside the set's folder")
            val path = name.removePrefix(prefix)
            val f = wanted[path] ?: throw ImportException("$path is in the archive but not in ${kind.manifest}")
            if (!done.add(path)) throw ImportException("$path is in the archive twice")
            if (e.size != f.size) throw ImportException("$path: ${e.size} bytes in the archive, ${f.size} in ${kind.manifest}")
            val index = done.size
            val base = bytesDone
            var last = 0L
            val metered = MeteredStream(tar, { n ->
                bytesDone += n
                if (bytesDone - last >= (8 shl 20) || bytesDone - base == f.size) {
                    last = bytesDone
                    onProgress(Progress(manifest.label, path, index, manifest.files.size, bytesDone, manifest.totalBytes))
                }
            }, cancelled)
            stage.receive(kind, path, f.size, f.sha256, f.mode, metered).orThrow()
            bytesDone = base + f.size
        }
        val missing = manifest.files.map { it.path }.filterNot { it in done }
        if (missing.isNotEmpty()) throw ImportException("the archive ended without ${missing.first()}" + if (missing.size > 1) " and ${missing.size - 1} more" else "")
        stage.receive(kind, kind.manifest, manifest.raw.size.toLong(), StageClient.sha256(manifest.raw), "0644", ByteArrayInputStream(manifest.raw)).orThrow()
        onProgress(Progress(manifest.label, "Checking and putting it in place", manifest.files.size, manifest.files.size, manifest.totalBytes, manifest.totalBytes))
        stage.finish(kind, manifest.files.size + 1, manifest.facts).orThrow()
        return manifest
    }
}

fun StageClient.Reply.orThrow(): StageClient.Reply = if (ok) this else throw ImportException(message)
