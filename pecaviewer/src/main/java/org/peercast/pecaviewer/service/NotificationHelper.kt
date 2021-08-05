package org.peercast.pecaviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.R
import kotlin.properties.Delegates


internal class NotificationHelper(private val service: PlayerService) {
    private val notificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun <T> updateNotifyWhenChanged(initialValue: T) =
        Delegates.observable(initialValue) { _, oldValue, newValue ->
            if (oldValue != newValue)
                updateNotification()
        }

    var isPlaying by updateNotifyWhenChanged(true)

    private var isForeground = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    private val playAction = NotificationCompat.Action(
        R.drawable.ic_play_arrow_black_24dp,
        "play",
        buildActionPendingIntent(PecaViewerIntent.ACTION_PLAY)
    )

    private val pauseAction = NotificationCompat.Action(
        R.drawable.ic_pause_black_24dp,
        "pause",
        buildActionPendingIntent(PecaViewerIntent.ACTION_PAUSE)
    )

    private val stopAction = NotificationCompat.Action(
        R.drawable.ic_stop_black_24dp,
        "stop",
        buildActionPendingIntent(PecaViewerIntent.ACTION_STOP)
    )

    private fun buildActionPendingIntent(act: String): PendingIntent {
        return PendingIntent.getBroadcast(
            service,
            0,
            Intent(act).also { it.setPackage(service.packageName) },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    //タスクバーから復帰する
    private fun buildPendingIntent(): PendingIntent {
        val i = Intent(service.playingIntent)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            service, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun startForeground() {
        if (!isForeground) {
            isForeground = true
            service.startForeground(ID, buildNotification())
            service.startService(Intent(service, service.javaClass))
        }
    }

    fun stopForeground() {
        if (isForeground) {
            isForeground = false
            service.playingIntent.removeExtra(EX_THUMBNAIL)
            service.stopForeground(true)
            service.stopSelf()
        }
    }

    fun updateNotification() {
        if (isForeground)
            notificationManager.notify(ID, buildNotification())
    }


    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(
            service, NOW_PLAYING_CHANNEL
        )

        when (isPlaying) {
            true -> {
                builder.addAction(stopAction)
                builder.addAction(pauseAction)
            }
            else -> builder.addAction(playAction)
        }

        androidx.media.app.NotificationCompat.MediaStyle(builder)
            .setCancelButtonIntent(stopAction.actionIntent)
            //.setShowActionsInCompactView(0)
            .setShowCancelButton(true)
        val ch =
            service.playingIntent.getParcelableExtra<Yp4gChannel>(PecaViewerIntent.EX_YP4G_CHANNEL)

        return builder
            .setContentIntent(buildPendingIntent())
            .setContentTitle("PecaPlayViewer")
            .setSmallIcon(R.drawable.ic_play_circle_outline_black_24dp)
            .setLargeIcon(service.playingIntent.getParcelableExtra(EX_THUMBNAIL))
            .setContentTitle(ch?.name)
            .setContentText(ch?.run { "$genre $description $comment".trim() })
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(stopAction.actionIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(NOW_PLAYING_CHANNEL) != null)
            return

        val ch = NotificationChannel(
            NOW_PLAYING_CHANNEL,
            "PecaPlayViewer",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(ch)
    }

    companion object {
        private const val NOW_PLAYING_CHANNEL = "PecaPlayViewer"

        /**(Bitmap)*/
        const val EX_THUMBNAIL = "thumbnail"


        const val ID = 0x1
    }
}