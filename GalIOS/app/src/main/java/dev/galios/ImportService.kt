package dev.galios

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps GalIOS alive while a runtime or an image is copied to the phone's stage (an image takes several minutes). */
class ImportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Copying to the phone", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GalIOS:import").apply { acquire(3 * 60 * 60 * 1000L) }
        }
        val controller = (application as GalIosApp).controller
        watch?.cancel()
        watch = scope.launch {
            controller.state.map { it.import }.distinctUntilChanged { a, b -> a?.let { (it.fraction * 100).toInt() } == b?.let { (it.fraction * 100).toInt() } && a?.file == b?.file }
                .collect { imp ->
                    if (imp == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        nm.notify(ID, notification(imp))
                    }
                }
        }
        return START_NOT_STICKY
    }

    private fun notification(imp: ImportState?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(imp?.title ?: "Preparing")
            .setContentText(imp?.file?.let { "${(imp.fraction * 100).toInt()}% · $it" } ?: "Reading the archive")
            .setProgress(1000, ((imp?.fraction ?: 0f) * 1000).toInt(), imp == null || imp.total == 0L)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        watch?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "imports"
        private const val ID = 1
    }
}
