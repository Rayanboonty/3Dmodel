package dev.galios.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.galios.EngineInfo
import dev.galios.ImportState
import dev.galios.Message
import dev.galios.UiState
import dev.galios.core.Device
import dev.galios.core.ImageFinder
import dev.galios.core.StageClient
import dev.galios.core.LogInfo
import dev.galios.core.PhoneChecks
import dev.galios.core.RuntimeRelease
import dev.galios.core.StageStatus
import dev.galios.core.UpdateInfo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders each screen with made-up states, to look at the design. Images: build/screenshots/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val s25 = Device("SM-S938B", "pa3q", "SM8750")
    private val goodChecks = PhoneChecks(true, "/data/adb/ksu/bin/busybox", "/data/adb/ksu/bin/resetprop", true, true, 120L shl 30, "Adreno830v2")
    private val engine = EngineInfo("0.4.86", 115, true, File("/x"))
    private val ready = StageStatus(true, "0.1.8", "27.0.1", "24A446", 11_150_000_000, false, "returned", "inplace 1 a", true, null)
    private val noop = SetupActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    private val actions = AppActions(noop, {}, {}, {}, {}, {}, { _, _ -> }, {})

    private fun shot(name: String, s: UiState, tab: Tab = Tab.BOOT, confirm: Boolean = false, scrollToEnd: Boolean = false, scrollPx: Float = 0f) {
        compose.setContent { GalIosApp(s, actions, startTab = tab, showConfirm = confirm) }
        compose.waitForIdle()
        if (scrollPx > 0f) {
            compose.onNode(androidx.compose.ui.test.hasScrollAction()).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.9f - scrollPx, durationMillis = 600) }
            compose.waitForIdle()
        }
        if (scrollToEnd) {
            compose.onNode(androidx.compose.ui.test.hasScrollAction()).performTouchInput { swipeUp(startY = bottom * 0.95f, endY = top, durationMillis = 400) }
            compose.waitForIdle()
        }
        captureScreenRoboImage(File("build/screenshots/$name.png").absolutePath)
    }

    @Test fun bootReady() = shot("1-boot-ready", UiState(device = s25, checks = goodChecks, engine = engine, engineInstalled = true, status = ready))

    @Test fun bootNeedsSetup() = shot(
        "2-boot-setup-needed",
        UiState(device = s25, checks = goodChecks, engine = engine, engineInstalled = true, status = ready.copy(imageVersion = null, imageBuild = null, imageBytes = null, ready = false, notReadyReason = "no image")),
    )

    @Test fun bootConfirm() = shot("3-boot-confirm", UiState(device = s25, checks = goodChecks, engine = engine, engineInstalled = true, status = ready), confirm = true)

    @Test fun setupImporting() = shot(
        "4-setup-importing",
        UiState(
            device = s25, checks = goodChecks.copy(resetprop = null), engine = engine, engineInstalled = true,
            status = ready.copy(imageVersion = null, ready = false), release = RuntimeRelease("v0.1.8", "OneiOS 0.1.8", "a", "u", 41_000_000),
            import = ImportState("iOS 27.0.1 (24A446)", "os.img", 1, 6, 4_200_000_000, 11_150_000_000),
            message = Message("OneiOS runtime 0.1.8 is ready on the phone.", false),
        ),
        Tab.SETUP,
    )

    @Test fun logs() = shot(
        "5-logs",
        UiState(
            device = s25, checks = goodChecks, engine = engine, engineInstalled = true, status = ready,
            logs = listOf(
                LogInfo("session", 1_240_000, 1794526062, listOf("xnuhost I cpu0: entering the kernel", "xnuhost I guest ready: first frame", "oneios-gpu-report: 58 frames in 10 s")),
                LogInfo("switch", 18_000, 1794525990, listOf("PREFLIGHT OK", "stopping One UI's framework", "xnuhost started")),
                LogInfo("return", 9_000, 1794527000, listOf("One UI restored in place (Enforcing).")),
            ),
        ),
        Tab.LOGS,
    )

    @Test fun setupFoundImage() = shot(
        "6-setup-found",
        UiState(
            device = s25, checks = goodChecks, engine = engine, engineInstalled = true,
            status = ready.copy(imageVersion = null, imageBuild = null, imageBytes = null, ready = false),
            found = listOf(
                ImageFinder.Found("/sdcard/Download/oneios-runtime-0.1.8-arm64.tar.zst", 41_000_000, StageClient.Kind.RUNTIME, "0.1.8", null),
                ImageFinder.Found("/sdcard/Download/OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst", 11_150_000_000, StageClient.Kind.IMAGE, "27.0.1", "24A446"),
            ),
        ),
        Tab.SETUP,
        scrollToEnd = true,
    )

    @Test fun bootWithUpdate() = shot(
        "7-boot-update",
        UiState(
            device = s25, checks = goodChecks, engine = engine, engineInstalled = true, status = ready,
            update = UpdateInfo("1.3", 3, "GalIOS-3.apk", "https://x", 2_600_000, "notes"),
        ),
    )

    @Test fun setupRuntimeStates() = shot(
        "8-setup-runtime",
        UiState(
            device = s25, checks = goodChecks.copy(root = false), engine = engine, engineInstalled = false, status = null,
            release = RuntimeRelease("v0.1.8", "OneiOS runtime 0.1.8", "oneios-runtime-0.1.8-arm64.tar.zst", "u", 745_000, "s"),
            runtimeFile = "/data/user/0/dev.galios/files/runtime/oneios-runtime-0.1.8-arm64.tar.zst",
        ),
        Tab.SETUP,
        scrollPx = 1500f,
    )
}
