package dev.galios

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.galios.core.OneosEngine
import dev.galios.core.RuntimeRelease
import dev.galios.core.StageClient
import dev.galios.ui.AppActions
import dev.galios.ui.GalIosApp
import dev.galios.ui.SetupActions

class MainActivity : ComponentActivity() {
    private val controller by lazy { (application as GalIosApp).controller }

    private val pickRuntime = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { controller.importFile(StageClient.Kind.RUNTIME, it) } }
    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { controller.importFile(StageClient.Kind.IMAGE, it) } }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val actions = AppActions(
            setup = SetupActions(
                refresh = { controller.refresh() },
                installEngine = { controller.installEngine() },
                getOneos = { open(OneosEngine.RELEASES_URL) },
                findRelease = { controller.findRelease() },
                downloadRuntime = { controller.downloadRuntime() },
                pickRuntime = { pickRuntime.launch(arrayOf("*/*")) },
                pickImage = { pickImage.launch(arrayOf("*/*")) },
                howToBuild = { open("https://github.com/akshaynexus/OneiOS/blob/main/docs/building.md") },
                cancelImport = { controller.cancelImport() },
                importFound = { controller.importFound(it) },
                addRuntime = { controller.addRuntimeToPhone() },
                startPhoneBuild = { controller.startPhoneBuild(it) },
                stopPhoneBuild = { controller.stopPhoneBuild() },
                openPhoneBuildLog = { controller.openPhoneBuildLog() },
            ),
            boot = { controller.boot() },
            returnToOneUi = { controller.returnToOneUi() },
            loadLogs = { controller.loadLogs() },
            openLog = { controller.openLog(it.id, it.title) },
            closeLog = { controller.closeLog() },
            shareLog = { title, text -> share(title, text) },
            dismissMessage = { controller.dismissMessage() },
            update = { controller.installUpdate() },
            dismissUpdate = { controller.dismissUpdate() },
        )
        setContent {
            val s by controller.state.collectAsStateWithLifecycle()
            GalIosApp(s, actions)
        }
    }

    override fun onResume() {
        super.onResume()
        controller.refresh()
    }

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun share(title: String, text: String) = startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "GalIOS log: $title").putExtra(Intent.EXTRA_TEXT, text), "Share log"),
    )

    @Suppress("unused")
    private val releasesPage = RuntimeRelease.RELEASES_PAGE
}
