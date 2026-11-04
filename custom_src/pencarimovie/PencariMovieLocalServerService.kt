package com.mts.pencarimovie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R

class PencariMovieLocalServerService : Service() {

    companion object {
        const val ACTION_START = "com.mts.pencarimovie.action.START"
        const val ACTION_STOP = "com.mts.pencarimovie.action.STOP"
        const val CHANNEL_ID = "pencarimovie_local_server"
        const val NOTIFICATION_ID = 8088

        fun start(context: Context) {
            val intent = Intent(context, PencariMovieLocalServerService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Fallback to async direct start
                PencariMovieServerManager.startServerAsync(context)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PencariMovieLocalServerService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                PencariMovieServerManager.stopServer()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                PencariMovieServerManager.stopServer()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                PencariMovieServerManager.startServerAsync(this) { success ->
                    if (success) {
                        updateNotification("Pelayan Penstriman Port 8088 Aktif")
                    } else {
                        updateNotification("Gagal memulakan pelayan port 8088")
                    }
                }
                return START_STICKY
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PencariMovie Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pelayan penstriman tempatan PencariMovie"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String = "Sedang memulakan enjin penstriman tempatan..."): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PencariMovie Enjin Penstriman")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_player_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        PencariMovieServerManager.stopServer()
        super.onDestroy()
    }
}
