package org.peercast.pecaplay

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.arch.lifecycle.*
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.support.v4.app.NotificationCompat
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.greenrobot.eventbus.Subscribe
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.app.YpIndex
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.YellowPageLiveLoader
import org.peercast.pecaplay.yp4g.Yp4gColumn
import org.peercast.pecaplay.yp4g.YpIndexLine
import timber.log.Timber

/**
 * Ypへ接続し、チャンネルのリストをYpIndexテーブルに収める。
 * 　Foregroundモード:
 *
 *   Jobモード:
 *
 * **/
class PecaPlayService : JobService(), LifecycleOwner {

    private val registry = LifecycleRegistry(this)
    private lateinit var database: AppRoomDatabase
    private lateinit var appPrefs: AppPreferences

    object OnLoadFinished : AppEvent
    object OnLoadStarted : AppEvent
    class OnLoadException(val exceptions: Map<YellowPage, Throwable>) : AppEvent


    override fun getLifecycle() = registry

    override fun onCreate() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()
        Timber.d("onCreate")

        database = PecaPlayApplication.of(this).database

        val ypLiveData = database.getYellowPageDao().getEnabled()
        appPrefs = AppPreferences(this)

        YellowPageLiveLoader(ypLiveData) {
            EVENT_BUS.post(OnLoadStarted)
            appPrefs.peerCastUrl.port
        }.observe(this, Observer<YellowPageLiveLoader.Result> { ret ->
            launch(UI) {
                //Log.d(TAG, "ret=$ret")
                if (ret!!.lines.isNotEmpty())
                    async { storeToYpIndex(ret) }.await()

                if (ret.exceptions.isNotEmpty())
                    EVENT_BUS.post(OnLoadException(ret.exceptions))

                if (appPrefs.isNotificationEnabled && ret.lines.isNotEmpty())
                    startService(Intent(this@PecaPlayService, NotifierService::class.java))

                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
        })
        registry.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            fun onStop() {
                //Log.d(TAG, "# onStop()")
                EVENT_BUS.post(OnLoadFinished)
            }
        })
    }

    override fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        Timber.d("onDestroy")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand($intent")

        when (intent.action) {
            ACTION_START -> {
                if (checkReloadInterval())
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                else
                    EVENT_BUS.post(OnLoadFinished)
            }
            ACTION_CANCEL -> {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            else -> {
                throw IllegalArgumentException("Invalid intent.action")
            }
        }
        return Service.START_NOT_STICKY
    }

    private inner class JobSubscriber(val params: JobParameters) {
        @Subscribe(sticky = true)
        fun onPeerCastStart(e: OnPeerCastStart) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        @Subscribe
        fun onLoadFinished(e: OnLoadFinished) {
            onStopJob(params)
            EVENT_BUS.unregister(this)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Timber.d("onStartJob($params)")

        if (!checkReloadInterval())
            return false

        PecaPlayApplication.of(this).bindPeerCastService()

        EVENT_BUS.register(JobSubscriber(params))
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Timber.d("onStopJob($params)")
        jobFinished(params, false)
        return false
    }

    private fun checkReloadInterval(): Boolean {
        val lastLoadedSince = runBlocking {
            async { database.getYpIndexDao().getLastLoadedSince() }.await()
        }
        if (lastLoadedSince < RETRY_INTERVAL_SEC) {
            val msg = "Please retry ${RETRY_INTERVAL_SEC - lastLoadedSince} seconds after"
            Timber.w(msg)
            return false
        }
        return true
    }

    private fun storeToYpIndex(ret: YellowPageLiveLoader.Result) {
        database.runInTransaction {
            database.compileStatement("UPDATE YpIndex SET isLatest=0").use {
                it.executeUpdateDelete()
            }
            storeToYpIndexRows(ret.lines)
        }
    }

    private fun storeToYpIndexRows(lines: List<YpIndexLine>) {
        val sql = "REPLACE INTO YpIndex (" +
                YpIndex.COLUMNS.joinToString(",") +
                ") VALUES(" +
                Yp4gColumn.values().joinToString(",", transform = { "?" }) +
                """,
                    1, --> isLatest
                    CURRENT_TIMESTAMP, --> lastLoadedTime
                    IFNULL((SELECT numLoaded+1 FROM YpIndex WHERE name=? AND id=?), 1) -->numLoaded
                )""".trimIndent()

        database.compileStatement(sql).use { state ->
            lines.forEach {
                it.bindTo(state, Yp4gColumn.values().toList() + listOf(Yp4gColumn.Name, Yp4gColumn.Id))
                val r = state.executeInsert()
                //Timber.d( "## $r")
            }
        }

        database.compileStatement("SELECT COUNT(*) FROM YpIndex").use {
            Timber.d("num=%d", it.simpleQueryForLong())
        }
    }

    companion object {
        private const val TAG = "PecaPlayService"
        private const val RETRY_INTERVAL_SEC = 15

        const val ACTION_START = "org.peercast.pecaplay.PecaPlayService.ACTION_START"
        const val ACTION_CANCEL = "org.peercast.pecaplay.PecaPlayService.ACTION_CANCEL"
    }

}


//新着を通知する。
class NotifierService : LifecycleService() {
    private lateinit var notifyManager: NotificationManager
    private lateinit var appPrefs: AppPreferences

    override fun onCreate() {
        super.onCreate()
        notifyManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        appPrefs = AppPreferences(this)

        val database = (application as PecaPlayApplication).database

        MediatorLiveData<List<YpIndex>>().also {
            val settle = booleanArrayOf(false, false)
            var favoNotify = emptyList<Favorite>()
            var favoNG = emptyList<Favorite>()
            var channels = emptyList<YpIndex>()

            fun onChanged() {
                if (false in settle)
                    return
                it.value = channels.filter { ch ->
                    ch.numLoaded == 1 &&
                            favoNotify.any { it.matches(ch) } &&
                            !favoNG.any { it.matches(ch) }
                }
            }

            it.addSource(database.getFavoriteDao().getEnabled()) {
                favoNotify = it!!.filter { it.flags.isNotification }
                favoNG = it.filter { it.flags.isNG }
                settle[0] = true
                onChanged()
            }
            it.addSource(database.getYpIndexDao().get()) {
                channels = it ?: emptyList()
                settle[1] = true
                onChanged()
            }

        }.observe(this, Observer {
            if (it!!.isNotEmpty())
                notifyNewChannel(it)
            stopSelf()
        })

    }

    private fun createActivityIntent(): PendingIntent {
        val i = Intent(Intent.ACTION_VIEW).also {
            it.setClass(this, PecaPlayActivity::class.java)
            it.putExtra(EXTRA_NAVIGATION_CATEGORY, CATEGORY_NOTIFICATED)
        }
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun notifyNewChannel(newChannels: List<YpIndex>) {
        Timber.d("notifyNewChannel($newChannels")
        val title = getString(
                R.string.notification_new_channels,
                newChannels.size)
        var content = ""
        val inbox = android.support.v4.app.NotificationCompat.InboxStyle()

        newChannels.take(5).forEach { ch ->
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
        //val tag = NOTIFY_TAG_PREFIX + content

        //        Bitmap largeIcon = BitmapFactory.decodeResource(
        //                context.getResources(),
        //                R.drawable.ic_peercast);

        val builder = NotificationCompat.Builder(this)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                //.setAutoCancel(true)
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
        appPrefs.notificationSoundUrl?.let {
            builder.setSound(it)
        }

        //.setDeleteIntent(createDeleteIntent())
        //builder.addAction(R.drawable.ic_notifications_white_24dp, "Disable", createDisableIntent())

        notifyManager.notify(null, NOTIFY_ID, builder.build())
    }

    companion object {
        private const val NOTIFY_ID = 7144
        private const val TAG = "NotifierService"
    }
}

