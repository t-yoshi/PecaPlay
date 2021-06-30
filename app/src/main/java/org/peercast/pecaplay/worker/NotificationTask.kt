package org.peercast.pecaplay.worker

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.peercast.pecaplay.LoadingWorker
import org.peercast.pecaplay.PecaPlayActivity
import org.peercast.pecaplay.PecaPlayIntent
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import org.peercast.pecaplay.yp4g.descriptionOrGenre
import timber.log.Timber

class NotificationTask(private val worker: LoadingWorker) : LoadingWorker.Task() {
    private val c = worker.applicationContext
    private val manager = c.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, "PecaPlay",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private fun createActivityIntent(): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW).also {
            it.setClass(c, PecaPlayActivity::class.java)
            it.putExtra(PecaPlayIntent.EXTRA_IS_NOTIFICATED, true)
        }
        return PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override suspend fun invoke(): Boolean {
        if (!worker.appPrefs.isNotificationEnabled)
            return true

        val channels = worker.database.ypChannelDao.query().first()
        val favorites = worker.database.favoriteDao.query().first()

        val favoNotify = favorites.filter { it.flags.isNotification }
        val favoNG = favorites.filter { it.flags.isNG }

        val newChannels = channels.filter { ch ->
            ch.numLoaded == 1 &&
                    favoNotify.any { it.matches(ch) } &&
                    !favoNG.any { it.matches(ch) }
        }

        if (newChannels.isNotEmpty()) {
            worker.appPrefs.notificationNewlyChannelsId += newChannels.map { it.yp4g.id }
            val ids = worker.appPrefs.notificationNewlyChannelsId
            onNewChannels(
                channels.filter {
                    it.yp4g.id in ids
                }.sortedWith(YpDisplayOrder.AGE_ASC.comparator)
            )
        }

        return true
    }

    private fun onNewChannels(channels: List<YpLiveChannel>) {
        Timber.d("onNewChannels(%s)", channels)

        val title = c.getString(
            R.string.notification_new_channels,
            channels.size
        )
        var content = ""
        val inbox = NotificationCompat.InboxStyle()

        channels.take(5).forEach { ch ->
            val name = ch.yp4g.name
            val desc = ch.yp4g.descriptionOrGenre
            val comment = ch.yp4g.comment
            val ssb = SpannableStringBuilder().apply {
                append("$name   $desc $comment")
                setSpan(StyleSpan(Typeface.BOLD), 0, name.length, 0)
            }
            content += " $name"
            inbox.addLine(ssb)
        }

        inbox.setBigContentTitle(title)
        inbox.setSummaryText(" ")
        //val categoryTag = NOTIFY_TAG_PREFIX + content

        //        Bitmap largeIcon = BitmapFactory.decodeResource(
        //                context.getResources(),
        //                R.drawable.ic_peercast);

        val builder = NotificationCompat.Builder(
            c,
            NOTIFICATION_CHANNEL_ID
        )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            //.setLargeIcon(largeIcon)
            .setSmallIcon(R.drawable.ic_notifications_active_24dp)
            //.setColor(ContextCompat.getColor(context, R.color.green_A100))
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(inbox)
            .setGroup("pacaplay")
            .setContentIntent(createActivityIntent())

        worker.appPrefs.notificationSoundUrl.let {
            if (it != Uri.EMPTY)
                builder.setSound(it)
        }

        //.setDeleteIntent(createDeleteIntent())
        //builder.addAction(R.drawable.ic_notifications_white_24dp, "Disable", createDisableIntent())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel()
        manager.notify(NOTIFY_ID, builder.build())
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pecaplay_id"
        private const val NOTIFY_ID = 7145
    }
}