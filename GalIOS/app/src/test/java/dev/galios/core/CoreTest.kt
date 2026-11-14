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
        val out = when (r) {
            is SwitchScriptAdapter.Result.Adapted -> r.script
            is SwitchScriptAdapter.Result.AlreadySupports -> r.script
            else -> ByteArray(0)
        }
        // The read-only step's fallback for the two script files (S25 Ultra: status 70) is in.
        assertTrue(SwitchScriptAdapter.RO_MARKER in out.toString(Charsets.UTF_8))
        assertTrue(SwitchScriptAdapter.adapt(out) is SwitchScriptAdapter.Result.AlreadySupports)
        // Byte for byte what GalIOS Studio stages, when given Studio's output.
        System.getProperty("galios.studioSwitch")?.let { assertTrue("differs from Studio's", java.io.File(it).readBytes().contentEquals(out)) }
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
    fun galiosRuntimeTakesTheHighestVersionOnItsRelease() {
        val json = """[
            {"tag_name":"v9-other","draft":false,"assets":[{"name":"oneios-runtime-9.0-arm64.tar.zst","browser_download_url":"https://x/o","size":1}]},
            {"tag_name":"galios-gpu-20261114-4","name":"GalIOS GPU pack","draft":false,"assets":[
                {"name":"galios-gpu-pack.tar.gz","browser_download_url":"https://x/p","size":5},
                {"name":"oneios-runtime-0.1.8-galios.2-arm64.tar.zst","browser_download_url":"https://x/2","size":2},
                {"name":"oneios-runtime-0.1.8-galios.2-arm64.tar.zst.sha256","browser_download_url":"https://x/2s","size":1},
                {"name":"oneios-runtime-0.1.8-galios.1-arm64.tar.zst","browser_download_url":"https://x/1","size":1}]}
        ]"""
        val r = RuntimeRelease.pick(json, RuntimeRelease.GALIOS_TAG)!!
        assertEquals("0.1.8-galios.2", r.version)
        assertEquals("https://x/2", r.url)
        assertEquals("https://x/2s", r.shaUrl)
        assertTrue(RuntimeRelease.newer("0.1.8-galios.10", "0.1.8-galios.9"))
        assertTrue(RuntimeRelease.newer("0.1.8-galios.1", "0.1.8"))
        assertFalse(RuntimeRelease.newer("0.1.8", "0.1.8"))
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
    fun theUpdaterPicksAHigherVersionCodeOnly() {
        val json = """[
            {"tag_name":"v1.4","draft":true,"assets":[{"name":"GalIOS-4.apk","browser_download_url":"https://x/4","size":4}]},
            {"tag_name":"v1.3","draft":false,"body":"notes","assets":[
                {"name":"GalIOS-3.apk.sha256","browser_download_url":"https://x/s","size":1},
                {"name":"GalIOS-3.apk","browser_download_url":"https://x/3","size":2600000}]},
            {"tag_name":"v1.2","draft":false,"assets":[{"name":"GalIOS-2.apk","browser_download_url":"https://x/2","size":2}]}
        ]"""
        // From code 2: the newest non-draft with a higher code is 3.
        val up = Updater.pick(json, 2)!!
        assertEquals(3L, up.versionCode)
        assertEquals("1.3", up.version)
        assertEquals("https://x/3", up.url)
        // From code 3: nothing higher (the 4 is a draft), so no update.
        assertNull(Updater.pick(json, 3))
        assertNull(Updater.pick("[]", 0))
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

    @Test
    fun thePhoneBuildStatusIsRead() {
        val running = PhoneBuild.Status.parse(listOf(
            "state=running", "step=Building the XNU kernel (10-20 minutes the first time)", "exit=", "started=1794564125",
            "| ==> Building the XNU kernel", "| [12/980] CC osfmk/kern/sched.o", "@@OK status",
        ))
        assertEquals(PhoneBuild.Status.State.RUNNING, running.state)
        assertEquals(1794564125L, running.startedSec)
        assertNull(running.exit)
        assertEquals(listOf("==> Building the XNU kernel", "[12/980] CC osfmk/kern/sched.o"), running.log)
        val failed = PhoneBuild.Status.parse(listOf("state=failed", "step=failed: Building the XNU kernel (exit 2)", "exit=2"))
        assertEquals(PhoneBuild.Status.State.FAILED, failed.state)
        assertEquals("Building the XNU kernel (exit 2)", failed.failedStep)
        assertEquals(PhoneBuild.Status.State.IDLE, PhoneBuild.Status.parse(listOf("state=idle", "@@OK status")).state)
        assertEquals(PhoneBuild.Status.State.STOPPED, PhoneBuild.Status.parse(listOf("state=stopped")).state)
    }

    @Test
    fun ipswFilesAreFoundPinnedFirst() {
        val scan = ImageFinder.parseScan(listOf(
            "DIR\t/sdcard/Download",
            "9000000000\t/sdcard/Download/iPhone17,1_26.4_23E000_Restore.ipsw",
            "11400000000\t/sdcard/Download/iPhone18,2_27.0.1_24A446_Restore.ipsw",
            "11400000000\t/storage/emulated/0/Download/iPhone18,2_27.0.1_24A446_Restore.ipsw",
            "11150000000\t/sdcard/Download/OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst",
        ))
        assertEquals(2, scan.ipsws.size)
        assertTrue(scan.ipsws[0].isPinned)
        assertEquals("iPhone18,2_27.0.1_24A446_Restore.ipsw", scan.ipsws[0].name)
        assertFalse(scan.ipsws[1].isPinned)
        // The image archive is still an image, not an IPSW.
        assertEquals(1, scan.found.size)
        assertTrue(ImageFinder.script(listOf("/sdcard/Download")).contains("-name '*.ipsw'"))
    }
}
