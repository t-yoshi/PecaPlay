package org.peercast.pecaplay

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
import androidx.lifecycle.MutableLiveData
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.util.exAwait
import org.peercast.pecaplay.yp4g.*
import timber.log.Timber
import java.io.IOException
import java.util.*


class LoadingWorkerLiveData : MutableLiveData<LoadingWorker.Event>()


class LoadingWorker(c: Context, workerParams: WorkerParameters) :
    CoroutineWorker(c, workerParams), KoinComponent {

    private val peerCastServiceEventLiveData: PeerCastServiceEventLiveData by inject()
    private val appPrefs: AppPreferences by inject()
    private val database: AppRoomDatabase by inject()
    private val eventLiveData: LoadingWorkerLiveData by inject()


    abstract class Task {
        /**trueなら次のタスクを実行する*/
        abstract suspend operator fun invoke(): Boolean
    }

    private inner class LoadingTask : Task() {
        override suspend fun invoke(): Boolean {
            if (database.ypChannelDao.getLastLoadedSince() < 15) {
                //Timber.i("")
                return false
            }

            peerCastServiceEventLiveData.bind()
            //PeerCastServiceの開始を待つ。
            peerCastServiceEventLiveData.exAwait().let {
                Timber.d("PeerCastService bound: %s", it)
            }

            val yellowPages = database.yellowPageDao.queryAwait()

            Timber.d("start loading: %s", yellowPages)

            val port = appPrefs.peerCastUrl.port
            val lines = ArrayList<Yp4gRawField>(256)

            yellowPages.map { yp ->
                yp to createYp4gService(yp).getIndex("localhost:$port")
            }.forEach { (yp, call) ->
                try {
                    val res = call.exAwait()
                    val url = res.raw().request().url().toString()
                    res.body()?.mapNotNull {
                        try {
                            it.create(yp, url)
                        } catch (e: Yp4gFormatException) {
                            Timber.w("YpParseError: %s", e.message)
                            null
                        }
                    }?.let(lines::addAll)
                } catch (e: IOException) {
                    eventLiveData.postValue(Event.OnException(id, yp, e))
                }
            }

            var ret = false
            database.runInTransaction {
                database.compileStatement("UPDATE YpLiveChannel SET isLatest=0").use {
                    it.executeUpdateDelete()
                }
                if (lines.isNotEmpty()) {
                    storeToYpLiveChannelTable(lines)
                    ret = true
                }
            }

            return ret
        }

        private fun storeToYpLiveChannelTable(lines: List<Yp4gRawField>) {
            val sql = "REPLACE INTO YpLiveChannel (" +
                    YpLiveChannel.COLUMNS.joinToString(",") +
                    ") VALUES(" +
                    Yp4gColumn.values().joinToString(",", transform = { "?" }) +
                    """,
                    1, --> isLatest
                    CURRENT_TIMESTAMP, --> lastLoadedTime
                    IFNULL((SELECT numLoaded+1 FROM YpLiveChannel WHERE name=? AND id=?), 1) -->numLoaded
                )""".trimIndent()

            val columnNames = Yp4gColumn.values().toList() + Yp4gColumn.Name + Yp4gColumn.Id

            database.compileStatement(sql).use { statement ->
                lines.forEach { line ->
                    //Timber.d("->%s",line)
                    line.bindTo(statement, columnNames)

                    val r = statement.executeInsert()
                    Timber.d("## $r")
                }
            }

            //database.compileStatement("SELECT COUNT(*) FROM YpLiveChannel").use {
            //    Timber.d("num=%d", it.simpleQueryForLong())
            //}
        }
    }


    private inner class NotificationTask : Task() {
        private val manager = applicationContext.getSystemService(
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
                it.setClass(applicationContext, PecaPlayActivity::class.java)
                it.putExtra(PecaPlayIntent.EXTRA_IS_NOTIFICATED, true)
            }
            return PendingIntent.getActivity(
                applicationContext, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        override suspend fun invoke(): Boolean {
            if (!appPrefs.isNotificationEnabled)
                return true

            val channels = database.ypChannelDao.queryAwait()
            val favorites = database.favoriteDao.queryAwait()
            val favoNotify = favorites.filter { it.flags.isNotification }
            val favoNG = favorites.filter { it.flags.isNG }

            val newChannels = channels.filter { ch ->
                ch.numLoaded == 1 &&
                        favoNotify.any { it.matches(ch) } &&
                        !favoNG.any { it.matches(ch) }
            }

            if (newChannels.isNotEmpty()) {
                appPrefs.notificationNewlyChannelsId += newChannels.map { it.yp4g.id }
                val ids = appPrefs.notificationNewlyChannelsId
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

            val title = applicationContext.getString(
                R.string.notification_new_channels,
                channels.size
            )
            var content = ""
            val inbox = NotificationCompat.InboxStyle()

            channels.take(5).forEach { ch ->
                val name = ch.yp4g.name
                val desc = ch.yp4g.description
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
                applicationContext,
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

            appPrefs.notificationSoundUrl.let {
                if (it != Uri.EMPTY)
                    builder.setSound(it)
            }

            //.setDeleteIntent(createDeleteIntent())
            //builder.addAction(R.drawable.ic_notifications_white_24dp, "Disable", createDisableIntent())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                createNotificationChannel()
            manager.notify(NOTIFY_ID, builder.build())
        }
    }

    override suspend fun doWork(): Result {
        eventLiveData.postValue(Event.OnStart(id))
        val tasks = listOf(
            LoadingTask(),
            NotificationTask()
        )
        try {
            for (t in tasks) {
                if (!t())
                    return Result.failure()
            }
        } finally {
            eventLiveData.postValue(Event.OnFinished(id))
        }
        return Result.success()
    }

    sealed class Event(val id: UUID) {
        class OnStart(id: UUID) : Event(id)
        class OnException(id: UUID, val yp: YellowPage, val ex: IOException) : Event(id)
        class OnFinished(id: UUID) : Event(id)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "pecaplay_id"
        private const val NOTIFY_ID = 7145
    }
}

