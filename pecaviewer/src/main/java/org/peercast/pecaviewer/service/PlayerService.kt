package org.peercast.pecaviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.mediacodec.MediaCodecDecoderException
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.video.MediaCodecVideoDecoderException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.peercast.core.lib.PeerCastController
import org.peercast.core.lib.notify.NotifyMessageType
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaplay.core.io.Square
import org.peercast.pecaplay.core.io.isLoopbackAddress
import org.peercast.pecaviewer.BuildConfig
import org.peercast.pecaviewer.PecaViewerActivity
import org.peercast.pecaviewer.PecaViewerPreference
import org.peercast.pecaviewer.R
import timber.log.Timber
import java.io.IOException
import java.util.*


class PlayerService : LifecycleService() {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var player: ExoPlayer

    private var pecaController: PeerCastController? = null

    private val appPrefs by inject<PecaViewerPreference>()
    private val eventFlow by inject<PlayerServiceEventFlow>()

    private var playingIntent = Intent()
    private val eventHandler by lazy(LazyThreadSafetyMode.NONE) { EventHandler(this) }
    private val reconnectHandler by lazy(LazyThreadSafetyMode.NONE) { RecoverHandler(this) }
    private val notificationHandler by lazy(LazyThreadSafetyMode.NONE) { NotificationHandler(this) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("received: $intent")
            when (intent?.action) {
                PecaViewerIntent.ACTION_PLAY -> play()
                PecaViewerIntent.ACTION_PAUSE -> player.pause()
                PecaViewerIntent.ACTION_STOP -> stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        okHttpClient = get<Square>().okHttpClient.newBuilder()
            .addInterceptor(CorruptFlvInterceptor)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AA_MEDIA_MOVIE, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(LOAD_CONTROL)
            .setRenderersFactory(DefaultRenderersFactory(this).also {
                it.setEnableDecoderFallback(true)
                it.setMediaCodecSelector(codecSelector)
            })
            .build()

        player.also {
            it.addAnalyticsListener(eventHandler)
            it.addAnalyticsListener(reconnectHandler)
            it.addAnalyticsListener(notificationHandler)
            if (BuildConfig.DEBUG)
                it.addAnalyticsListener(EventLogger())
        }

        PeerCastController.from(this).also {
            if (it.isInstalled) {
                it.eventListener = pecaEventHandler
                pecaController = it
            }
        }

        ContextCompat.registerReceiver(this, receiver,
            IntentFilter().also {
                it.addAction(PecaViewerIntent.ACTION_PLAY)
                it.addAction(PecaViewerIntent.ACTION_STOP)
                it.addAction(PecaViewerIntent.ACTION_PAUSE)
            }, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    class Binder(val service: PlayerService) : android.os.Binder()

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)
        return Binder(this)
    }

    private fun bindPeerCastService() {
        lifecycleScope.launch {
            pecaController?.let {
                if (!it.isConnected)
                    pecaController?.tryBindService()
            }
        }
    }

    private fun emit(ev: PlayerServiceEvent) {
        lifecycleScope.launch {
            eventFlow.emit(ev)
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
            emit(PeerCastNotifyMessageEvent(types, message))
        }
    }

    private val codecSelector =
        MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            ).toMutableList()
//            if (mimeType.startsWith("video/")) {
//                Timber.i(codecs.joinToString(",", "codecs: ", transform = { it.name }))
//                codecs.sortBy { !it.name.run { startsWith("c2.android.") || startsWith("OMX.google.") } }
//                Timber.i(codecs.joinToString(",", "sorted codecs: ", transform = { it.name }))
//            }
            codecs
        }

    fun prepareFromUri(u: Uri, ch: Yp4gChannel, playWhenReady: Boolean = false) {
        if (u.isLoopbackAddress())
            bindPeerCastService()

        if (playingIntent.data == u)
            return
        playingIntent = Intent(this, PecaViewerActivity::class.java)
            .setData(u)
            .putExtra(PecaViewerIntent.EX_YP4G_CHANNEL, ch)

        player.stop()
        player.clearMediaItems()

        if (u == Uri.EMPTY)
            return


        val sf = DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient))

        player.repeatMode = Player.REPEAT_MODE_ALL
        player.addMediaSource(sf.createMediaSource(MediaItem.fromUri(u)))

        if (playWhenReady) {
            player.playWhenReady = true
            player.prepare()
        }
    }

    val isPlaying get() = player.isPlaying

    val isBuffering get() = player.playbackState == Player.STATE_BUFFERING

    fun play() {
        Timber.d("play!")
        if (isPlaying)
            return
        player.playWhenReady = true
        player.prepare()
    }

    fun stop() {
        Timber.d("stop!")
        reconnectHandler.reset()
        notificationHandler.stopForeground()
        player.playWhenReady = false
        player.stop()
    }

    var thumbnail: Bitmap?
        get() = IntentCompat.getParcelableExtra(playingIntent, EX_THUMBNAIL, Bitmap::class.java)
        set(value) {
            playingIntent.putExtra(EX_THUMBNAIL, value)
            notificationHandler.updateNotification()
        }

    val videoSize get() = player.videoSize

    override fun onDestroy() {
        super.onDestroy()

        notificationHandler.stopForeground()
        pecaController?.unbindService()
        unregisterReceiver(receiver)

        player.release()
    }

    private class DelegatedPlayer(private val sv: PlayerService) : ForwardingPlayer(sv.player) {
        override fun play() {
            sv.play()
        }

        //pauseボタンの挙動をstopに変更する。
        override fun pause() {
            sv.stop()
        }
    }

    private fun setVideoDisabled(disabled: Boolean) {
        player.trackSelectionParameters =
            player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
                .build()
    }

    fun attachView(view: StyledPlayerView) {
        Timber.d("attachView: $view")
        //setVideoDisabled(false)
        view.player = DelegatedPlayer(this)
        notificationHandler.stopForeground()
    }

    fun enterBackgroundMode() {
        //setVideoDisabled(true)

        if (isPlaying || isBuffering) {
            notificationHandler.startForeground()
        }
    }

    companion object {
        private val LOAD_CONTROL = DefaultLoadControl.Builder()
            //.setBackBuffer(10000, true)
            .setBufferDurationsMs(5000, 20000, 700, 2000)
            .build()

        private val AA_MEDIA_MOVIE = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        fun detachView(view: StyledPlayerView) {
            if (view.player == null)
                return
            Timber.d("detachView: $view")
            view.player = null
        }

        /**(Bitmap)*/
        private const val EX_THUMBNAIL = "thumbnail"
    }

    private class EventHandler(private val sv: PlayerService) : AnalyticsListener {
        private var jBuf: Job? = null

        override fun onPlayWhenReadyChanged(
            eventTime: AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int
        ) {
            sv.emit(PlayerWhenReadyChangedEvent(playWhenReady, reason))
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    jBuf = sv.lifecycleScope.launch {
                        while (isActive) {
                            sv.eventFlow.emit(PlayerBufferingEvent(sv.player.bufferedPercentage))
                            delay(500)
                        }
                    }
                }
                else -> {
                    jBuf?.cancel()
                }
            }
        }

        private fun sendPlayerErrorEvent(errorType: String, e: Exception) {
            if (e is ExoPlaybackException && e.type == ExoPlaybackException.TYPE_RENDERER) {
                Timber.w(e, "$errorType -> $e")
                return
            }
            Timber.e(e, "$errorType -> $e")
            jBuf?.cancel()
            sv.emit(PlayerErrorEvent(errorType, e))
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
            sv.emit(PlayerLoadStartEvent(loadEventInfo.uri))
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            Timber.w(error, "onLoadError -> ${loadEventInfo.uri}")

            sv.emit(PlayerLoadErrorEvent(loadEventInfo.uri, error))
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

    //バッファー状態でフリーズすることを防ぐ
    private class RecoverHandler(private val sv: PlayerService) : AnalyticsListener {
        private var jFreeze: Job? = null
        private var nFreeze = 0
        private var nDecodeError = 0

        fun reset() {
            jFreeze?.cancel()
            nFreeze = 0
            nDecodeError = 0
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            Timber.d("onPlaybackStateChanged: $state")
            when (state) {
                Player.STATE_BUFFERING -> {
                    jFreeze = sv.lifecycleScope.launch {
                        when (nFreeze) {
                            0 -> delay(15_000)
                            else -> delay(12_000)
                        }

                        if (++nFreeze <= MAX_RECOVER_FROM_FREEZE) {
                            Timber.i("freeze #$nFreeze: try to recover")
                            sv.lifecycleScope.launch {
                                sv.player.stop()
                                delay(100)
                                sv.play()
                            }
                        } else {
                            Timber.e("freeze #$nFreeze: couldn't recover.")
                            sv.stop()
                        }
                    }
                }
                Player.STATE_READY -> {
                    reset()
                }
                else -> {
                    jFreeze?.cancel()
                }
            }
        }

        private fun recoverWhenRendererError(e: Exception) {
            if (++nDecodeError > MAX_RECOVER_FROM_DECODE_ERROR) {
                Timber.e(e, "decode failed: couldn't recover.")
                return
            }
            when (e) {
                is MediaCodecVideoDecoderException -> {
                    Timber.i(e, "video decode failed #$nDecodeError: try to recover.")
                }
                is MediaCodecDecoderException -> {
                    Timber.i(e, "audio decode failed #$nDecodeError: try to recover.")
                }
                else -> return
            }
            sv.play()
        }

        //エラーが起きて再生が止まったとき
        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException
        ) {
            jFreeze?.cancel()
            if (error !is ExoPlaybackException)
                return
            if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                val se = error.sourceException
                if (se is HttpDataSource.InvalidResponseCodeException && se.responseCode == 404) {
                    Timber.i("404: don't recover.")
                }
            } else if (error.type == ExoPlaybackException.TYPE_RENDERER) {
                recoverWhenRendererError(error.rendererException)
            }
        }

        companion object {
            private const val MAX_RECOVER_FROM_FREEZE = 5
            private const val MAX_RECOVER_FROM_DECODE_ERROR = 3
        }
    }

    private class NotificationHandler(private val sv: PlayerService) : AnalyticsListener {
        private val man = sv.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        private var isForeground = false

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }
        }

        private val playAction = NotificationCompat.Action(
            R.drawable.ic_play_arrow_black_24dp,
            "play",
            PecaViewerIntent.createActionPendingIntent(sv, PecaViewerIntent.ACTION_PLAY)
        )

        private val pauseAction = NotificationCompat.Action(
            R.drawable.ic_pause_black_24dp,
            "pause",
            PecaViewerIntent.createActionPendingIntent(sv, PecaViewerIntent.ACTION_PAUSE)
        )

        private val stopAction = NotificationCompat.Action(
            R.drawable.ic_stop_black_24dp,
            "stop",
            PecaViewerIntent.createActionPendingIntent(sv, PecaViewerIntent.ACTION_STOP)
        )

        //タスクバーから復帰する
        private fun buildPendingIntent(): PendingIntent {
            val i = Intent(sv.playingIntent)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                sv, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private var jStopForeground: Job? = null

        fun startForeground() {
            jStopForeground?.cancel()
            if (!isForeground) {
                isForeground = true
                sv.startForeground(NOTIFICATION_ID, buildNotification())
                sv.startService(Intent(sv, sv.javaClass))
            }
        }

        private fun doStopForeground() {
            if (!isForeground)
                return
            isForeground = false
            sv.playingIntent.removeExtra(EX_THUMBNAIL)
            ServiceCompat.stopForeground(
                sv, ServiceCompat.STOP_FOREGROUND_REMOVE
            )
            sv.stopSelf()
        }

        fun stopForeground(delayMs: Long = 0) {
            jStopForeground?.cancel()
            if (delayMs <= 0) {
                doStopForeground()
            } else {
                jStopForeground = sv.lifecycleScope.launch {
                    delay(delayMs)
                    doStopForeground()
                }
            }
        }

        fun updateNotification() {
            if (isForeground)
                man.notify(NOTIFICATION_ID, buildNotification())
        }

        private fun buildNotification(): Notification {
            val builder = NotificationCompat.Builder(
                sv, NOTIFICATION_CHANNEL_NOW_PLAYING
            )

            when (sv.isPlaying) {
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
            val ch = IntentCompat.getParcelableExtra(
                sv.playingIntent,
                PecaViewerIntent.EX_YP4G_CHANNEL,
                Yp4gChannel::class.java
            )

            return builder
                .setContentIntent(buildPendingIntent())
                .setContentTitle("PecaPlayViewer")
                .setSmallIcon(R.drawable.ic_play_circle_outline_black_24dp)
                .setLargeIcon(
                    IntentCompat.getParcelableExtra(
                        sv.playingIntent,
                        EX_THUMBNAIL,
                        Bitmap::class.java
                    )
                )
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
            if (man.getNotificationChannel(NOTIFICATION_CHANNEL_NOW_PLAYING) != null)
                return

            val ch = NotificationChannel(
                NOTIFICATION_CHANNEL_NOW_PLAYING,
                "PecaPlayViewer",
                NotificationManager.IMPORTANCE_LOW
            )
            man.createNotificationChannel(ch)
        }

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            when (state) {
                Player.STATE_IDLE,
                Player.STATE_ENDED,
                -> {
                    //再接続中の可能性があるので
                    stopForeground(30_000)
                }
                else -> {
                    jStopForeground?.cancel()
                    updateNotification()
                }
            }
        }

        companion object {
            private const val NOTIFICATION_CHANNEL_NOW_PLAYING = "PecaPlayViewer"
            private const val NOTIFICATION_ID = 0x1
        }
    }

}

