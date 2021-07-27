package org.peercast.pecaviewer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.peercast.core.lib.PeerCastController
import org.peercast.core.lib.notify.NotifyMessageType
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaplay.core.io.Square
import org.peercast.pecaplay.core.io.selfAndCauses
import org.peercast.pecaviewer.ViewerPreference
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.*


class PlayerService : LifecycleService() {

    private lateinit var player: SimpleExoPlayer
    private lateinit var notificationHelper: NotificationHelper
    private var peerCastController: PeerCastController? = null

    private val square by inject<Square>()
    private val appPrefs by inject<ViewerPreference>()
    private val eventLiveData by inject<PlayerServiceEventLiveData>()
    var playingUrl: Uri = Uri.EMPTY
        private set

    var thumbnail: Bitmap?
        set(value) {
            notificationHelper.thumbnail = value
        }
        get() = notificationHelper.thumbnail

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationHelper.ACTION_PLAY -> player.play()
                NotificationHelper.ACTION_PAUSE -> player.pause()
                NotificationHelper.ACTION_STOP -> player.stop()
                else -> Timber.e("$intent")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        player = SimpleExoPlayer.Builder(this)
            .setAudioAttributes(AA_MEDIA_MOVIE, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(playerListener)

        notificationHelper = NotificationHelper(this)

        PeerCastController.from(this).also {
            if (it.isInstalled) {
                it.eventListener = pecaEventHandler
                peerCastController = it
            }
        }

        registerReceiver(receiver, IntentFilter().also {
            it.addAction(NotificationHelper.ACTION_PLAY)
            it.addAction(NotificationHelper.ACTION_STOP)
            it.addAction(NotificationHelper.ACTION_PAUSE)
        })
    }

    private val playerListener = object : Player.Listener {
//        override fun onMetadata(metadata: Metadata) {
//            Timber.d("metadata -> $metadata")
//        }
//
//        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
//            Timber.d("onTimelineChanged -> $timeline : $reason")
//        }
//
//        override fun onPlayerError(error: ExoPlaybackException) {
//            Timber.e(error, "onPlayerError -> $error")
//        }

        private var jobBuffering: Job? = null

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    jobBuffering = lifecycleScope.launch {
                        while (isActive) {
                            eventLiveData.value = PlayerBufferingEvent(player.bufferedPercentage)
                            delay(10_000)
                        }
                    }
                }
                else -> {
                    jobBuffering?.cancel()
                }
            }

            when (state) {
                Player.STATE_IDLE,
                Player.STATE_ENDED,
                -> {
                    notificationHelper.stopForeground()
                }
                else -> {
                }
            }

            //Timber.d("state -> $state")
        }

//        override fun onEvents(player: Player, events: Player.Events) {
//            val s = (0 until events.size()).map {
//                events[it]
//            }.joinToString()
//            Timber.d("events -> $s")
//        }

        override fun onSurfaceSizeChanged(width: Int, height: Int) {
            Timber.d("onSurfaceSizeChanged -> $width, $height")
            if (width == 0 && height == 0 && player.isPlaying && appPrefs.isBackgroundPlaying) {
                notificationHelper.startForeground()
            } else {
                notificationHelper.stopForeground()
            }
        }

//        override fun onVideoSizeChanged(videoSize: VideoSize) {
//            Timber.d("onVideoSizeChanged -> $videoSize")
//        }
//
//        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
//            Timber.d("mediaMetadata -> $mediaMetadata")
//        }
//
//        override fun onStaticMetadataChanged(metadataList: MutableList<Metadata>) {
//            Timber.d("metadataList -> $metadataList")
//        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            notificationHelper.isPlaying = isPlaying
        }
    }

    private val mediaSourceListener = object : MediaSourceEventListener {
        override fun onLoadStarted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            eventLiveData.value = PlayerLoadStartEvent(loadEventInfo.uri)
            Timber.d("onLoadStarted -> ${loadEventInfo.uri}")
        }

        override fun onLoadError(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            eventLiveData.value = PlayerLoadErrorEvent(loadEventInfo.uri, error)
            Timber.w(error, "onLoadError -> ${loadEventInfo.uri}")
        }
    }

    class Binder(val service: PlayerService) : android.os.Binder()

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        return Binder(this)
    }

    private fun bindPeerCastService() {
        lifecycleScope.launch {
            peerCastController?.let {
                if (!it.isConnected)
                    peerCastController?.tryBindService()
            }
        }
    }

    private val pecaEventHandler = object : PeerCastController.EventListener {
        override fun onConnectService(controller: PeerCastController) {
        }

        override fun onDisconnectService() {
        }

        override fun onNotifyMessage(types: EnumSet<NotifyMessageType>, message: String) {
            Timber.d("onNotifyMessage: $types $message")
            eventLiveData.value = PeerCastNotifyMessageEvent(types, message)
        }
    }

    fun attachPlayerView(view: PlayerView) {
        //pauseボタンの挙動をstopに変更する。
        view.player = object : Player by player {
            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (!playWhenReady)
                    player.stop()
                player.playWhenReady = playWhenReady
            }
        }
    }

    private val progressiveFactory = ProgressiveMediaSource.Factory(
        OkHttpDataSource.Factory(square.okHttpClient)
    ).also { f ->
        f.setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 8
            }

            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                Timber.d("-> ${loadErrorInfo.exception} @${loadErrorInfo.errorCount}")

                //PeerCastがまだ起動されていない
                val isTimeout = loadErrorInfo.exception.let { e ->
                    //e is HttpDataSource.HttpDataSourceException &&
                    e.selfAndCauses().any { it is SocketTimeoutException }
                }
                return if (isTimeout) {
                    3000L
                } else {
                    super.getRetryDelayMsFor(loadErrorInfo)
                }
            }
        })
    }

    fun prepareFromUri(u: Uri, ch: Yp4gChannel) {
        notificationHelper.resumeIntent = PecaViewerIntent.create(u, ch)

        if (playingUrl == u)
            return
        playingUrl = u

        player.stop()
        player.clearMediaItems()

        if (u == Uri.EMPTY)
            return

        val item = MediaItem.fromUri(u)
        val src = progressiveFactory.createMediaSource(item)
        src.addEventListener(Handler(Looper.getMainLooper()), mediaSourceListener)
        player.setMediaSource(src)

        if (u.host in listOf("localhost", "127.0.0.1"))
            bindPeerCastService()

        player.prepare()
    }

    val isPlaying get() = player.isPlaying

    fun play() {
        //player.play()
        player.playWhenReady = true
    }

    fun stop() {
        player.stop()
    }

    override fun onDestroy() {
        super.onDestroy()

        peerCastController?.unbindService()
        unregisterReceiver(receiver)

        player.release()
    }

    companion object {
        private val AA_MEDIA_MOVIE = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MOVIE)
            .build()
    }
}


