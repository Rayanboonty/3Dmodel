package dev.galios.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    // A switch script shaped like OneOS's around its phone check (written for this test, not OneOS's text).
    private val script = """
        |#!/system/bin/sh
        |# Root tools first.
        |P_MODEL=${'$'}(getprop ro.product.model)
        |P_DEVICE=${'$'}(getprop ro.product.device)
        |P_SOC=${'$'}(getprop ro.soc.model)
        |case "${'$'}P_MODEL" in SM-S928*) ;; *) refuse "S24 Ultra only; this is ${'$'}{P_MODEL:-unknown}" ;; esac
        |[ "${'$'}P_DEVICE" = e3q ] && [ "${'$'}P_SOC" = SM8650 ] ||
        |    refuse "S24 Ultra (e3q, SM8650) only; this is ${'$'}{P_DEVICE:-?}, ${'$'}{P_SOC:-?}"
        |# The stage next (SM8650 named in a comment is fine).
        |stage_checks
        |""".trimMargin()

    @Test
    fun theSwitchCheckIsAdaptedForTheS25UltraAndKeepsTheS24Ultra() {
        val r = SwitchScriptAdapter.adapt(script.toByteArray())
        assertTrue(r is SwitchScriptAdapter.Result.Adapted)
        val out = (r as SwitchScriptAdapter.Result.Adapted).script.toString(Charsets.UTF_8)
        assertTrue(out.contains("SM-S938*) [ \"\$P_DEVICE\" = pa3q ] && [ \"\$P_SOC\" = SM8750 ] ||"))
        assertTrue(out.contains("SM-S928*) [ \"\$P_DEVICE\" = e3q ] && [ \"\$P_SOC\" = SM8650 ] ||"))
        assertFalse(out.contains("SM-S928*) ;; *)"))
        // Everything around it is unchanged.
        assertTrue(out.startsWith("#!/system/bin/sh\n# Root tools first.\n"))
        assertTrue(out.endsWith("# The stage next (SM8650 named in a comment is fine).\nstage_checks\n"))
        // Adapting the result again is a no-op.
        assertTrue(SwitchScriptAdapter.adapt(out.toByteArray()) is SwitchScriptAdapter.Result.AlreadySupports)
    }

    @Test
    fun anUnknownCheckIsRefusedNotGuessed() {
        val other = script.replace("[ \"\$P_DEVICE\" = e3q ]", "[ \"\$P_BOARD\" = e3q ]")
        assertTrue(SwitchScriptAdapter.adapt(other.toByteArray()) is SwitchScriptAdapter.Result.Unrecognised)
        assertTrue(SwitchScriptAdapter.adapt("echo hi\n".toByteArray()) is SwitchScriptAdapter.Result.Unrecognised)
        val twice = script + "[ \"\$(getprop ro.soc.model)\" = SM8650 ] || refuse \"again\"\n"
        val r = SwitchScriptAdapter.adapt(twice.toByteArray())
        assertTrue(r is SwitchScriptAdapter.Result.Unrecognised && r.why.contains("line"))
    }

    /** OneOS's real switch script, when the test is given its path (never committed here). */
    @Test
    fun oneosRealSwitchScriptIsAdapted() {
        val path = System.getProperty("galios.switchScript") ?: return
        val r = SwitchScriptAdapter.adapt(java.io.File(path).readBytes())
        assertTrue("$r", r is SwitchScriptAdapter.Result.Adapted || r is SwitchScriptAdapter.Result.AlreadySupports)
    }

    @Test
    fun statusIsRead() {
        val reply = StageClient.Reply.parse(
            RootShell.Result(
                0,
                listOf(
                    "@@RESULT stage present", "@@RESULT runtime 0.1.8 1 bin/xnuhost 1 0", "@@RESULT image 1 27.0.1 24A446 11150000000",
                    "@@RESULT session none returned", "@@RESULT outcome inplace 1794526062 abc", "@@RESULT ready 0 no busybox at the root manager's paths",
                    "@@OK status",
                ),
            ),
        )
        val s = StageStatus.parse(reply)
        assertTrue(s.stagePresent)
        assertEquals("0.1.8", s.runtimeVersion)
        assertEquals("27.0.1", s.imageVersion)
        assertEquals("24A446", s.imageBuild)
        assertEquals(11150000000L, s.imageBytes)
        assertFalse(s.sessionOpen)
        assertEquals("inplace 1794526062 abc", s.lastOutcome)
        assertFalse(s.ready)
        assertEquals("no busybox at the root manager's paths", s.notReadyReason)

        val none = StageStatus.parse(StageClient.Reply.parse(RootShell.Result(0, listOf("@@RESULT stage none", "@@OK status"))))
        assertFalse(none.stagePresent)
        assertNull(none.runtimeVersion)
        val ready = StageStatus.parse(StageClient.Reply.parse(RootShell.Result(0, listOf("@@RESULT runtime none", "@@RESULT image none", "@@RESULT ready 1", "@@OK status"))))
        assertTrue(ready.ready)
        assertNull(ready.imageVersion)
    }

    @Test
    fun refusalsCarryTheirReason() {
        val r = StageClient.Reply.parse(RootShell.Result(2, listOf("@@REFUSED an iOS session is running: return to One UI first")))
        assertFalse(r.ok)
        assertEquals("an iOS session is running: return to One UI first", r.message)
        val silent = StageClient.Reply.parse(RootShell.Result(1, listOf("su: permission denied")))
        assertFalse(silent.ok)
        assertEquals("su: permission denied", silent.message)
    }

    @Test
    fun logsAreRead() {
        val r = StageClient.Reply.parse(
            RootShell.Result(
                0,
                listOf(
                    "@@RESULT session none", "@@RESULT log session 5000 1794526062", "| session cpu0: entering the kernel",
                    "| session guest ready: first frame", "@@RESULT log switch 200 1794526000", "| switch PREFLIGHT OK", "@@OK logs",
                ),
            ),
        )
        val logs = LogInfo.parse(r)
        assertEquals(listOf("session", "switch"), logs.map { it.id })
        assertEquals(listOf("cpu0: entering the kernel", "guest ready: first frame"), logs[0].tail)
        assertEquals("This session (xnuhost)", logs[0].title)
    }

    @Test
    fun manifestsAreChecked() {
        val rt = """{"format":1,"version":"0.1.8","session_abi":1,"xnuhost":"bin/xnuhost","image_formats":[1],
            "files":[{"path":"bin/xnuhost","size":10,"sha256":"${"a".repeat(64)}","mode":"0755"}]}"""
        val m = PackageManifest.parse(StageClient.Kind.RUNTIME, rt.toByteArray())
        assertEquals(listOf("0.1.8", "1", "bin/xnuhost", "1", "0"), m.facts)
        assertEquals("0755", m.files.single().mode)
        val img = """{"format":1,"version":"27.0.1","build":"24A446","files":[{"path":"os.img","size":5,"sha256":"${"b".repeat(64)}"}]}"""
        val i = PackageManifest.parse(StageClient.Kind.IMAGE, img.toByteArray())
        assertEquals(listOf("1", "27.0.1", "24A446"), i.facts)
        assertEquals("iOS 27.0.1 (24A446)", i.label)
        for (bad in listOf(
            img.replace("os.img", "../os.img"), img.replace("b".repeat(64), "B".repeat(64)), img.replace("\"build\":\"24A446\",", ""),
            "not json",
        )) {
            assertTrue(bad, runCatching { PackageManifest.parse(StageClient.Kind.IMAGE, bad.toByteArray()) }.isFailure)
        }
    }

    @Test
    fun theNewestRuntimeReleaseIsPicked() {
        val json = """[
            {"tag_name":"v0.1.9-draft","draft":true,"assets":[{"name":"oneios-runtime-0.1.9-arm64.tar.zst","browser_download_url":"https://x/9","size":9}]},
            {"tag_name":"v0.1.8","name":"OneiOS 0.1.8","draft":false,"prerelease":true,"assets":[
                {"name":"oneios-runtime-0.1.8-arm64.tar.zst.sha256","browser_download_url":"https://x/s","size":1},
                {"name":"oneios-runtime-0.1.8-arm64.tar.zst","browser_download_url":"https://x/8","size":8}]},
            {"tag_name":"v0.1.7","draft":false,"assets":[{"name":"oneios-runtime-0.1.7-arm64.tar.zst","browser_download_url":"https://x/7","size":7}]}
        ]"""
        val r = RuntimeRelease.pick(json)!!
        assertEquals("v0.1.8", r.tag)
        assertEquals("OneiOS 0.1.8", r.name)
        assertEquals("https://x/8", r.url)
        assertNull(RuntimeRelease.pick("[]"))
    }

    @Test
    fun phoneChecksAreRead() {
        val c = PhoneChecks.parse(listOf("uid=0", "busybox=/data/adb/ksu/bin/busybox", "resetprop=/data/adb/ksu/bin/resetprop", "volup=1", "voldown=1", "free=1000 4096", "gpu=Adreno830v2"))
        assertTrue(c.allGood)
        assertEquals(4096000L, c.freeBytes)
        val noTools = PhoneChecks.parse(listOf("uid=0", "volup=1", "voldown=1", "free=", "gpu="))
        assertFalse(noTools.allGood)
        assertNull(noTools.busybox)
        assertNull(noTools.freeBytes)
    }

    @Test
    fun devicesAreKnown() {
        assertTrue(Device("SM-S938B", "pa3q", "SM8750").isS25Ultra)
        assertTrue(Device("SM-S938B/DS", "pa3q", "SM8750").supported)
        assertTrue(Device("SM-S928B", "e3q", "SM8650").isS24Ultra)
        assertFalse(Device("SM-S938B", "pa3s", "s5e9955").supported)
        assertEquals("Galaxy S25 Ultra", Device("SM-S938B", "pa3q", "SM8750").label)
    }

    @Test
    fun shellWordsAreQuoted() {
        assertEquals("'a b'", shq("a b"))
        assertEquals("'it'\\''s'", shq("it's"))
    }

    @Test
    fun archivesOnThePhoneAreFound() {
        val scan = ImageFinder.parseScan(
            listOf(
                "DIR\t/sdcard/Download",
                "11150000000\t/sdcard/Download/OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst",
                "11150000000\t/storage/emulated/0/Download/OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst", // the same file again
                "10990000000\t/sdcard/Documents/OneiOS_iOS27.0_24A335_iPhone18-2.tar.zst",
                "41000000\t/sdcard/Download/oneios-runtime-0.1.8-arm64.tar.zst",
                "40000000\t/sdcard/Download/oneios-runtime-0.1.10-arm64.tar.zst",
                "9000000000\t/sdcard/Download/my-ios-build.tar.zst", // renamed: still shown, by its file name
                "not a line",
            ),
        )
        assertEquals(listOf("/sdcard/Download"), scan.scanned)
        val found = scan.found
        // Four recognised plus the renamed one, shown as an image candidate.
        assertEquals(5, found.size)
        val runtimes = found.filter { it.kind == StageClient.Kind.RUNTIME }
        assertEquals(listOf("0.1.10", "0.1.8"), runtimes.map { it.version })
        val images = found.filter { it.kind == StageClient.Kind.IMAGE }
        // Recognised first (newest version), then the renamed one by its file name.
        assertEquals(listOf("27.0.1", "27.0", null), images.map { it.version })
        assertEquals("iOS 27.0.1 (24A446)", images[0].label)
        assertEquals("my-ios-build.tar.zst", images.last().label)
        assertFalse(images.last().recognised)
        assertEquals("/sdcard/Download/OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst", images[0].path)
        // The search follows a folder that is a symlink (trailing slash) and keeps spaced names whole.
        val script = ImageFinder.script(listOf("/sdcard/My Files"))
        assertTrue(script.contains("'/sdcard/My Files'/"))
        assertTrue(script.contains("-name '*.tar.zst'"))
    }
}
