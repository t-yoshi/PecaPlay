package org.peercast.pecaplay

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import org.peercast.pecaplay.core.app.PecaPlayIntent
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import timber.log.Timber

class PecaPlayNotification(private val c: Context) {

    private val manager = c.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    private val prefs = c.getSharedPreferences("PecaPlayNotification_v8", Context.MODE_PRIVATE)

    @TargetApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            c.getString(R.string.notification_channel_new_channels_found),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

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

    fun notifyNewYpChannelsWereFound(channels: List<YpChannel>) {
        Timber.d("notifyNewChannels(%s)", channels)
        prefs.newChannelsId += channels.map { it.id }
        doNotifyNewYpChannelsWereFound(
            channels.filter {
                it.id in prefs.newChannelsId
            }.sortedWith(YpDisplayOrder.AGE_ASC.comparator)
        )
    }

    private fun doNotifyNewYpChannelsWereFound(channels: List<YpChannel>) {
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

        val n = NotificationCompat.Builder(
            c, NOTIFICATION_CHANNEL_ID
        )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_notifications_active_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(inbox)
            .setGroup("pacaplay")
            .setContentIntent(createActivityIntent())
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        manager.notify(NOTIFY_ID, n)
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun launchSystemSettings(f: Fragment) {
        createChannel()

        val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        i.putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        i.putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID)
        f.startActivity(i)
    }

    fun clearNotifiedNewYpChannels(){
        prefs.edit {
            remove(KEY_NEW_CHANNELS_IDS)
        }
        manager.cancel(NOTIFY_ID)
    }


    private var SharedPreferences.newChannelsId: Collection<String>
        get() = getStringSet(KEY_NEW_CHANNELS_IDS, null) ?: emptyList()
        set(value) {
            edit {
                putStringSet(KEY_NEW_CHANNELS_IDS, value.toSet())
            }
        }

    companion object {
        /**新着*/
        private const val NOTIFICATION_CHANNEL_ID = "pecaplay_id"
        private const val NOTIFY_ID = 7145

        private const val KEY_NEW_CHANNELS_IDS = "pref_new_channels_ids"
    }


}