package dev.galios.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Hands a downloaded APK to Android's package installer, which asks the user to confirm. */
object UpdateInstaller {
    fun apkFile(context: Context): File = File(context.cacheDir, "galios-update.apk")

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system settings screen where the user allows GalIOS to install apps (needed once). */
    fun allowInstallIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
