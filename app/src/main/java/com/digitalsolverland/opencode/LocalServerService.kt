package com.digitalsolverland.opencode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.digitalsolverland.opencode.runtime.LocalPhase
import com.digitalsolverland.opencode.runtime.LocalServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Maintient le serveur OpenCode embarqué (proot + Debian) en vie en arrière-plan.
 */
class LocalServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground("Préparation…")

        when (intent?.action) {
            ACTION_STOP -> {
                LocalServer.stop()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STATUS -> {
                LocalServer.refresh(this)
            }
            else -> {
                acquireWakeLock()
                LocalServer.start(this)
            }
        }

        if (statusJob == null) {
            statusJob = scope.launch {
                LocalServer.phase.collectLatest { phase ->
                    updateNotification(textFor(phase, LocalServer.message.value))
                }
            }
        }
        return START_STICKY
    }

    private var statusJob: kotlinx.coroutines.Job? = null

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun textFor(phase: LocalPhase, message: String): String = when (phase) {
        LocalPhase.RUNNING -> "Actif · ${LocalServer.baseUrl(this)}"
        LocalPhase.STARTING -> "Démarrage…"
        LocalPhase.INSTALLING -> "Installation du runtime…"
        LocalPhase.ERROR -> "Erreur · ouvrez le journal"
        LocalPhase.STOPPED -> "Arrêté"
        LocalPhase.NOT_INSTALLED -> "Runtime non installé"
    }

    // ---------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Serveur OpenCode local", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocalServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("OpenCode · serveur local")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stop)
            .build()
    }

    private fun goForeground(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "opencode:local").apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.digitalsolverland.opencode.local.START"
        const val ACTION_STOP = "com.digitalsolverland.opencode.local.STOP"
        const val ACTION_STATUS = "com.digitalsolverland.opencode.local.STATUS"
        private const val CHANNEL_ID = "opencode_local_server"
        private const val NOTIF_ID = 4343

        fun intent(ctx: Context, action: String): Intent =
            Intent(ctx, LocalServerService::class.java).setAction(action)
    }
}
