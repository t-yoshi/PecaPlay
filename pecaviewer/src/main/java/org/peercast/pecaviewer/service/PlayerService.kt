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
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.util.EventLogger
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
import org.peercast.pecaplay.core.io.isLoopbackAddress
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
            Timber.d("received: $intent")
            when (intent?.action) {
                PecaViewerIntent.ACTION_PLAY -> {
                    player.prepare()
                    player.play()
                }
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
            .setUseLazyPreparation(true)
            .setLoadControl(loadControl)
            .setRenderersFactory(DefaultRenderersFactory(this).also {
                it.setEnableDecoderFallback(true)
                it.setMediaCodecSelector(codecSelector)
            })
            .build()

        player.run {
            addAnalyticsListener(analyticsListener)
            addAnalyticsListener(retryHandler)
            addAnalyticsListener(EventLogger())
            repeatMode = Player.REPEAT_MODE_ALL
        }

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

    private val codecSelector =
        MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val info = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            Timber.d("codecSelector ($mimeType, $requiresSecureDecoder, $requiresTunnelingDecoder): " + info.joinToString { it.name })
            return@MediaCodecSelector info
        }

    private fun PlayerServiceEvent.emit() {
        lifecycleScope.launch {
            eventFlow.emit(this@emit)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        private var jBuf: Job? = null

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
                            eventFlow.emit(PlayerBufferingEvent(player.bufferedPercentage))
                            delay(5_000)
                        }
                    }
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
            notificationHelper.isPlaying = isPlaying
            //Timber.d("state -> $state")
        }

        private fun sendPlayerErrorEvent(errorType: String, e: Exception) {
            if (e is ExoPlaybackException && e.type == ExoPlaybackException.TYPE_RENDERER) {
                Timber.w(e, "$errorType -> $e")
                return
            }
            Timber.e(e, "$errorType -> $e")
            jBuf?.cancel()
            PlayerErrorEvent(errorType, e).emit()
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException
        ) {
            sendPlayerErrorEvent("PlayerError", error)
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

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            Timber.d("onVideoDecoderInitialized: $decoderName")
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters
        ) {
            Timber.d("onVideoDisabled: $decoderCounters")
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

    //バッファー状態でフリーズすることを防ぐ
    private val retryHandler = object : AnalyticsListener {
        private var j: Job? = null
        private var n = 0

        fun reset() {
            j?.cancel()
            n = 0
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            Timber.d("onPlaybackStateChanged: $state")
            when (state) {
                Player.STATE_BUFFERING -> {
                    j = lifecycleScope.launch {
                        do {
                            delay(15_000)
                        } while (doReconnect())
                    }
                }
                Player.STATE_READY -> {
                    reset()
                }
                else -> {
                    j?.cancel()
                }
            }
        }

        private fun doReconnect(): Boolean {
            val b = n++ < MAX_RECONNECT
            if (b) {
                lifecycleScope.launch {
                    Timber.i("try to reconnect.")
                    player.stop()
                    delay(500)
                    player.prepare()
                }
            }
            return b
        }

        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            videoCodecError: Exception,
        ) {
            doReconnect()
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException
        ) {
            if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_SOURCE) {
                val se = error.sourceException
                if (se is HttpDataSource.InvalidResponseCodeException && se.responseCode == 404) {
                    Timber.i("404: stop reconnecting.")
                    reset()
                }
            }
        }
    }

    private val loadControl = DefaultLoadControl.Builder()
        //.setPrioritizeTimeOverSizeThresholds(true)
        //.setTargetBufferBytes(256 * 1024)
        .setBackBuffer(10000, true)
        .setBufferDurationsMs(5000, 50000, 5000, 5000)
        .build()

    private val loadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
//        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
//            return 0
//        }

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            Timber.d("-> getRetryDelayMsFor ${loadErrorInfo.exception} @${loadErrorInfo.errorCount}")
            val e = loadErrorInfo.exception
            if (
                e is HttpDataSource.InvalidResponseCodeException &&
                e.responseCode == 404
            ) {
                return C.TIME_UNSET
            }
            return 3_000
        }
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

        val sourceFactory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(square.okHttpClient))
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        val mediaSource = sourceFactory.createMediaSource(MediaItem.fromUri(u))

        player.addMediaSource(mediaSource)

        if (u.isLoopbackAddress())
            bindPeerCastService()

        player.playWhenReady = false
        player.prepare()
    }

    val isPlaying get() = player.isPlaying

    val isBuffering get() = player.playbackState == Player.STATE_BUFFERING

    fun play() {
        Timber.d("play!")
        player.playWhenReady = true
        player.prepare()
    }

    fun stop() {
        Timber.d("stop!")
        retryHandler.reset()
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

    private inner class DelegatedPlayer(view: StyledPlayerView) : Player by player, Player.Listener {
        //pauseボタンの挙動をstopに変更する。
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) {
                this@PlayerService.play()
            } else {
                this@PlayerService.stop()
            }
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

    fun attachView(view: StyledPlayerView) {
        view.player = DelegatedPlayer(view)
    }

    companion object {
        private val AA_MEDIA_MOVIE = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        private const val MAX_RECONNECT = 3
    }
}


