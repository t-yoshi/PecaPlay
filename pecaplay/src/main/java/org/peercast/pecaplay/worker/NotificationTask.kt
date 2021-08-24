package org.peercast.pecaplay.worker

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.BuildConfig
import org.peercast.pecaplay.PecaPlayActivity
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.core.app.PecaPlayIntent
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import timber.log.Timber

class NotificationTask(worker: ListenableWorker) : LoadingWorker.Task(worker), KoinComponent {

    private val database by inject<AppRoomDatabase>()
    private val appPrefs by inject<AppPreferences>()

    private val c = worker.applicationContext
    private val manager = c.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager


    private fun createActivityIntent(): PendingIntent {
        val i = Intent().also {
            it.setClass(c, PecaPlayActivity::class.java)
            it.putExtra(PecaPlayIntent.EX_NAVIGATION_ITEM, "notified")
        }
        return PendingIntent.getActivity(
            c, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override suspend fun invoke(): Boolean {
        if (!appPrefs.isNotificationEnabled)
            return true

        val channels = database.ypChannelDao.query().first()
        val favorites = database.favoriteDao.query().first()

        val favoNotify = favorites.filter { it.flags.isNotification }
        val favoNG = favorites.filter { it.flags.isNG }

        val newChannels = channels.filter { ch ->
            ch.numLoaded == 1 &&
                    favoNotify.any { it.matches(ch) } &&
                    !favoNG.any { it.matches(ch) }
        }

        if (newChannels.isNotEmpty()) {
            appPrefs.notificationNewlyChannelsId += newChannels.map { it.id }
            val ids = appPrefs.notificationNewlyChannelsId
            onNewChannels(
                channels.filter {
                    it.id in ids
                }.sortedWith(YpDisplayOrder.AGE_ASC.comparator)
            )
        }

        return true
    }

    private fun onNewChannels(channels: List<YpLiveChannel>) {
        Timber.d("onNewChannels(%s)", channels)

        val title = c.getString(
            R.string.notification_new_channels_were_found,
            channels.size
        )
        var content = ""
        val inbox = NotificationCompat.InboxStyle()

        channels.take(5).forEach { ch ->
            val ssb = SpannableStringBuilder().apply {
                append(ch.run { "$name $genre $description $comment" })
                setSpan(StyleSpan(Typeface.BOLD), 0, ch.name.length, 0)
            }
            content += " ${ch.name}"
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
            .setDefaults(NotificationCompat.DEFAULT_SOUND)

        //.setDeleteIntent(createDeleteIntent())
        //builder.addAction(R.drawable.ic_notifications_white_24dp, "Disable", createDisableIntent())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(c)
        }
        manager.notify(NOTIFY_ID, builder.build())
    }

    companion object {
        @TargetApi(Build.VERSION_CODES.O)
        fun createNotificationChannel(c: Context) {
            val manager = c.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                c.getString(R.string.notification_channel_new_channels_found),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        /**新着*/
        const val NOTIFICATION_CHANNEL_ID = "pecaplay_id"
        private const val NOTIFY_ID = 7145
    }
}