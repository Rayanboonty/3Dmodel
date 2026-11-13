package dev.galios.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.security.MessageDigest

/**
 * The runtime lookup, the runtime download and the self-update check, end to end over HTTP against a local server
 * shaped like GitHub: a release list that needs the Accept header, and downloads that redirect to a file host.
 * (1.3's lookup set a header after connecting, which threw "Already connected" every time; this would have caught it.)
 */
class NetworkTest {
    private lateinit var server: ServerSocket
    private lateinit var base: String
    private val runtime = ByteArray(50_000) { (it * 31).toByte() }
    private val runtimeSha = MessageDigest.getInstance("SHA-256").digest(runtime).joinToString("") { "%02x".format(it) }
    @Volatile private var shaFile = "$runtimeSha  oneios-runtime-0.1.8-arm64.tar.zst\n"
    private val acceptSeen = java.util.Collections.synchronizedList(mutableListOf<String?>())

    /** A small HTTP/1.1 server: one request per connection, then close. Enough for HttpURLConnection. */
    @Before
    fun start() {
        server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        base = "http://127.0.0.1:${server.localPort}"
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) {
                    sock.use { c ->
                        val reader = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
                            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
                        val (code, extra, body) = respond(path, headers)
                        val out = c.getOutputStream()
                        val head = StringBuilder("HTTP/1.1 $code X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
                        extra.forEach { (k, v) -> head.append("$k: $v\r\n") }
                        out.write(head.append("\r\n").toString().toByteArray(Charsets.ISO_8859_1))
                        out.write(body); out.flush()
                    }
                }
            }
        }
    }

    private fun respond(path: String, h: Map<String, String>): Triple<Int, Map<String, String>, ByteArray> = when {
        path.startsWith("/releases") -> {
            acceptSeen += h["accept"]
            val body = """[
              {"tag_name":"v0.1.8","name":"OneiOS runtime 0.1.8","draft":false,"assets":[
                {"name":"oneios-runtime-0.1.8-arm64.tar.zst","browser_download_url":"$base/dl/rt","size":${runtime.size}},
                {"name":"oneios-runtime-0.1.8-arm64.tar.zst.sha256","browser_download_url":"$base/dl/sha","size":101}]},
              {"tag_name":"v0.1.7","name":"OneiOS runtime 0.1.7","draft":false,"assets":[
                {"name":"oneios-runtime-0.1.7-arm64.tar.zst","browser_download_url":"$base/dl/old","size":10}]},
              {"tag_name":"v1.4","draft":false,"assets":[{"name":"GalIOS-4.apk","browser_download_url":"$base/dl/apk","size":7}]}
            ]""".toByteArray()
            // GitHub wants a proper Accept; so does this stand-in.
            Triple(if (h["accept"] == "application/vnd.github+json") 200 else 415, emptyMap(), body)
        }
        // Downloads redirect to a "file host", as GitHub's do.
        path.startsWith("/dl/") -> Triple(302, mapOf("Location" to "$base/host" + path.removePrefix("/dl")), ByteArray(0))
        path == "/host/rt" -> Triple(200, emptyMap(), runtime)
        path == "/host/sha" -> Triple(200, emptyMap(), shaFile.toByteArray())
        path == "/host/apk" -> Triple(200, emptyMap(), "APKDATA".toByteArray())
        else -> Triple(404, emptyMap(), ByteArray(0))
    }

    @After
    fun stop() = server.close()

    @Test
    fun theNewestRuntimeIsFoundDownloadedAndChecked() {
        val r = RuntimeRelease.latest("$base/releases")
        assertNotNull(r)
        assertEquals("application/vnd.github+json", acceptSeen.single())
        assertEquals("0.1.8", r!!.version)
        assertEquals("$base/dl/sha", r.shaUrl)

        val dir = kotlin.io.path.createTempDirectory("galios-rt").toFile()
        var last = 0L
        val f = r.download(dir) { done, _ -> last = done }
        assertEquals("oneios-runtime-0.1.8-arm64.tar.zst", f.name)
        assertTrue(f.readBytes().contentEquals(runtime))
        assertEquals(runtime.size.toLong(), last)
        assertFalse(File(dir, f.name + ".part").exists())
        // A second call reuses the good file.
        assertEquals(f, r.download(dir))

        // A file that does not match the release's SHA-256 is refused and not kept.
        f.delete()
        shaFile = "${"0".repeat(64)}  oneios-runtime-0.1.8-arm64.tar.zst\n"
        val err = runCatching { r.download(dir) }.exceptionOrNull()
        assertTrue("$err", err?.message?.contains("does not match") == true)
        assertFalse(File(dir, "oneios-runtime-0.1.8-arm64.tar.zst").exists())
        dir.deleteRecursively()
    }

    @Test
    fun theSelfUpdateCheckWorksOverHttp() {
        val up = Updater.check(3, api = "$base/releases")
        assertNotNull(up)
        assertEquals(4L, up!!.versionCode)
        assertNull(Updater.check(4, api = "$base/releases"))
        val apk = File.createTempFile("galios", ".apk")
        Updater.download(up, apk)
        assertEquals("APKDATA", apk.readText())
        apk.delete()
    }

    @Test
    fun aRefusedRequestSaysWhy() {
        val err = runCatching { Http.text("$base/releases") }.exceptionOrNull()   // no Accept header: the stand-in says 415
        assertTrue("$err", err?.message?.contains("415") == true)
    }
}
