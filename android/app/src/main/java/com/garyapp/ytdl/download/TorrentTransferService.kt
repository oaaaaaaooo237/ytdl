package com.garyapp.ytdl.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.garyapp.ytdl.R

internal class TorrentTransferService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(Channel, "Torrent", NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(Notice, Notification.Builder(this, Channel)
            .setSmallIcon(R.drawable.ic_stat_ytdl_download)
            .setContentTitle("CaptainFlash · Torrent")
            .setOngoing(true)
            .build())
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTransferTimeout?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val Channel = "torrent-transfer"
        private const val Notice = 8321
        internal var onTransferTimeout: (() -> Unit)? = null
    }
}
