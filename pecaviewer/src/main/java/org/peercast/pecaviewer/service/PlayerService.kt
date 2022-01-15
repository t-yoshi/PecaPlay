package org.peercast.pecaviewer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.*
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.HttpDataSource
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
import org.peercast.pecaviewer.PecaViewerActivity
import org.peercast.pecaviewer.PecaViewerPreference
import timber.log.Timber
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*


class PlayerService : LifecycleService() {

    private lateinit var player: ExoPlayer
    private lateinit var notificationHelper: NotificationHelper
    private var peerCastController: PeerCastController? = null

    private val square by inject<Square>()
    private val appPrefs by inject<PecaViewerPreference>()
    private val eventFlow by inject<PlayerServiceEventFlow>()
    var playingIntent = Intent()
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PecaViewerIntent.ACTION_PLAY -> player.play()
                PecaViewerIntent.ACTION_PAUSE -> player.pause()
                PecaViewerIntent.ACTION_STOP -> player.stop()
                else -> Timber.e("$intent")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AA_MEDIA_MOVIE, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            //.setLoadControl(LOAD_CONTROL)
            .setUseLazyPreparation(true)
            .build()
        player.addAnalyticsListener(analyticsListener)
        player.repeatMode = Player.REPEAT_MODE_ONE

        notificationHelper = NotificationHelper(this)

        PeerCastController.from(this).also {
            if (it.isInstalled) {
                it.eventListener = pecaEventHandler
                peerCastController = it
            }
        }

        registerReceiver(receiver, IntentFilter().also {
            it.addAction(PecaViewerIntent.ACTION_PLAY)
            it.addAction(PecaViewerIntent.ACTION_STOP)
            it.addAction(PecaViewerIntent.ACTION_PAUSE)
        })
    }

    private fun PlayerServiceEvent.emit() {
        lifecycleScope.launch {
            eventFlow.emit(this@emit)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        private var jBuf: Job? = null
        var nMaxReconnect = 0

        override fun onPlayWhenReadyChanged(
            eventTime: AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int
        ) {
            PlayerWhenReadyChangedEvent(playWhenReady, reason).emit()
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    jBuf = lifecycleScope.launch {
                        while (isActive) {
                            for (i in 0..2) {
                                eventFlow.emit(PlayerBufferingEvent(player.bufferedPercentage))
                                delay(5_000)
                            }
                            if (nMaxReconnect-- > 0) {
                                Timber.i("try to reconnect.")
                                lifecycleScope.launch {
                                    //バッファー状態でフリーズすることを防ぐ
                                    player.stop()
                                    delay(500)
                                    player.prepare()
                                }
                                break
                            }
                        }
                    }
                }
                Player.STATE_READY -> {
                    jBuf?.cancel()
                    nMaxReconnect = MAX_RECONNECT
                }
                else -> {
                    jBuf?.cancel()
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

        private fun sendPlayerErrorEvent(errorType: String, e: Exception) {
            jBuf?.cancel()
            Timber.e(e, "$errorType -> $e")
            PlayerErrorEvent(errorType, e).emit()
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException
        ) {
            if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_SOURCE) {
                val se = error.sourceException
                if (se is HttpDataSource.InvalidResponseCodeException && se.responseCode == 404) {
                    Timber.i("404: stop reconnecting.")
                    nMaxReconnect = 0
                }
            }

            sendPlayerErrorEvent("PlayerError", error)
        }

        override fun onAudioCodecError(
            eventTime: AnalyticsListener.EventTime,
            audioCodecError: Exception,
        ) {
            sendPlayerErrorEvent("AudioCodecError", audioCodecError)
        }

        override fun onAudioSinkError(
            eventTime: AnalyticsListener.EventTime,
            audioSinkError: Exception,
        ) {
            sendPlayerErrorEvent("AudioSinkError", audioSinkError)
        }

        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            videoCodecError: Exception,
        ) {
            sendPlayerErrorEvent("VideoCodecError", videoCodecError)
        }


        override fun onEvents(player: Player, events: AnalyticsListener.Events) {
//            val s = (0 until events.size()).map {
//                events[it]
//            }.joinToString()
//            Timber.d("events -> $s")
        }

        override fun onSurfaceSizeChanged(
            eventTime: AnalyticsListener.EventTime,
            width: Int,
            height: Int,
        ) {
            Timber.d("onSurfaceSizeChanged -> $width, $height")
            if (width == 0 && height == 0 && player.isPlaying && appPrefs.isBackgroundPlaying) {
                notificationHelper.startForeground()
            } else {
                notificationHelper.stopForeground()
            }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            Timber.d("onAudioInputFormatChanged -> $format")
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            Timber.d("onVideoInputFormatChanged -> $format")
        }

        override fun onIsPlayingChanged(
            eventTime: AnalyticsListener.EventTime,
            isPlaying: Boolean,
        ) {
            notificationHelper.isPlaying = isPlaying
        }

        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            Timber.d("onLoadStarted -> ${loadEventInfo.uri}")
            PlayerLoadStartEvent(loadEventInfo.uri).emit()
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            Timber.w(error, "onLoadError -> ${loadEventInfo.uri}")
            PlayerLoadErrorEvent(loadEventInfo.uri, error).emit()
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {

            Timber.d("onBandwidthEstimate -> $totalLoadTimeMs $bitrateEstimate")
        }

        override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {

            Timber.d("onTimelineChanged -> ${reason}")
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
            Timber.d("onConnectService: ${controller.rpcEndPoint}")
        }

        override fun onDisconnectService() {
            Timber.d("onDisconnectService")
        }

        override fun onNotifyMessage(types: EnumSet<NotifyMessageType>, message: String) {
            Timber.d("onNotifyMessage: $types $message")
            PeerCastNotifyMessageEvent(types, message).emit()
        }
    }

    private val progressiveFactory = ProgressiveMediaSource.Factory(
        OkHttpDataSource.Factory(square.okHttpClient)
    ).also { f ->
//        f.setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
//            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
//                return 8
//            }
//
//            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
//                Timber.d("-> getRetryDelayMsFor ${loadErrorInfo.exception} @${loadErrorInfo.errorCount}")
//                val e = loadErrorInfo.exception
//                if (
//                    e is HttpDataSource.InvalidResponseCodeException &&
//                    e.responseCode == 404
//                ) {
//                    return C.TIME_UNSET
//                }
//                return 5_000
//            }
//        })
    }

    fun prepareFromUri(u: Uri, ch: Yp4gChannel) {
        if (playingIntent.data == u)
            return
        playingIntent = Intent(this, PecaViewerActivity::class.java)
            .setData(u)
            .putExtra(PecaViewerIntent.EX_YP4G_CHANNEL, ch)

        player.stop()
        player.clearMediaItems()

        if (u == Uri.EMPTY)
            return

        val item = MediaItem.fromUri(u)

        player.setMediaSource(progressiveFactory.createMediaSource(item))

        if (u.host in listOf("localhost", "127.0.0.1"))
            bindPeerCastService()

        analyticsListener.nMaxReconnect = MAX_RECONNECT
        player.prepare()
    }

    val isPlaying get() = player.isPlaying

    val isBuffering get() = player.playbackState == Player.STATE_BUFFERING

    fun play() {
        player.playWhenReady = true
    }

    fun stop() {
        analyticsListener.nMaxReconnect = MAX_RECONNECT
        player.stop()
    }

    fun setThumbnail(b: Bitmap?) {
        playingIntent.putExtra(NotificationHelper.EX_THUMBNAIL, b)
        notificationHelper.updateNotification()
    }

    val videoSize get() = player.videoSize

    override fun onDestroy() {
        super.onDestroy()

        peerCastController?.unbindService()
        unregisterReceiver(receiver)

        player.release()
    }

    private inner class DelegatedPlayer(view: PlayerView) : Player by player, Player.Listener {
        //pauseボタンの挙動をstopに変更する。
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (!playWhenReady)
                this@PlayerService.stop()
            player.playWhenReady = playWhenReady
        }

        private val weakView = WeakReference(view)

        //再生中は消灯しない
        override fun onPlaybackStateChanged(state: Int) {
            val v = weakView.get()
            if (v != null && v.player == this) {
                v.keepScreenOn = state == Player.STATE_READY
            } else {
                player.removeListener(this)
            }
        }

        init {
            view.keepScreenOn = this@PlayerService.run { isBuffering || isPlaying }
            player.addListener(this)
        }
    }

    companion object {
        fun PlayerView.setPlayerService(service: PlayerService?) {
            Timber.d("--> $this $service")
            player = service?.DelegatedPlayer(this)
        }

        private val AA_MEDIA_MOVIE = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MOVIE)
            .build()

        private const val MAX_RECONNECT = 3
    }
}


