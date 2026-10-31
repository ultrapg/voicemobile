package io.github.ultrapg.voicemobile.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.github.ultrapg.voicemobile.MainActivity

/**
 * Keeps long-running Hugging Face GGUF model downloads and multi-chunk audiobook synthesis
 * alive cleanly on Android 15-17 (API 35-37) with explicit foreground service types.
 */
class SynthesisService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Voice Mobile"
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Running on-device neural synthesis…"
                val isMedia = intent.getBooleanExtra(EXTRA_IS_MEDIA, false)
                acquireWakeLockIfNeeded()

                val fgsType = if (isMedia) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                runCatching {
                    startForeground(NOTIFICATION_ID, buildNotification(title, detail), fgsType)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceMobile::ComputeLock").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // max 30 min safety timeout
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Synthesis & Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active status for on-device Qwen3-TTS inference and GGUF downloads"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, detail: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voicemobile_active_tasks"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_START = "io.github.ultrapg.voicemobile.START_TASK"
        private const val ACTION_UPDATE = "io.github.ultrapg.voicemobile.UPDATE_TASK"
        private const val ACTION_STOP = "io.github.ultrapg.voicemobile.STOP_TASK"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_DETAIL = "detail"
        private const val EXTRA_IS_MEDIA = "is_media"

        fun startOrUpdate(context: Context, title: String, detail: String, isMedia: Boolean = false) {
            runCatching {
                val intent = Intent(context, SynthesisService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_DETAIL, detail)
                    putExtra(EXTRA_IS_MEDIA, isMedia)
                }
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, SynthesisService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }
}
