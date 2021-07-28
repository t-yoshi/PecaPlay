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
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.HttpDataSource
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
import org.peercast.pecaviewer.ViewerPreference
import timber.log.Timber
import java.io.IOException
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit


class PlayerService : LifecycleService() {

    private lateinit var player: SimpleExoPlayer
    private lateinit var notificationHelper: NotificationHelper
    private var peerCastController: PeerCastController? = null

    private val square by inject<Square>()
    private val appPrefs by inject<ViewerPreference>()
    private val eventFlow by inject<PlayerServiceEventFlow>()
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
        player.addAnalyticsListener(analyticsListener)

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

    private fun PlayerServiceEvent.emit(){
        lifecycleScope.launch {
            eventFlow.emit(this@emit)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        private var jBuf: Job? = null

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    jBuf = lifecycleScope.launch {
                        while (isActive) {
                            eventFlow.emit(PlayerBufferingEvent(player.bufferedPercentage))
                            delay(3_000)
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

            //Timber.d("state -> $state")
        }

         private fun sendPlayerErrorEvent(errorType: String, e: Exception){
            jBuf?.cancel()
            Timber.e(e, "$errorType -> $e")
            PlayerErrorEvent(errorType, e).emit()
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: ExoPlaybackException
        ){
            sendPlayerErrorEvent("PlayerError", error)
        }

        override fun onAudioCodecError(
            eventTime: AnalyticsListener.EventTime,
            audioCodecError: Exception
        ) {
            sendPlayerErrorEvent("AudioCodecError", audioCodecError)
        }

        override fun onAudioSinkError(
            eventTime: AnalyticsListener.EventTime,
            audioSinkError: Exception
        ) {
            sendPlayerErrorEvent("AudioSinkError", audioSinkError)
        }

        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            videoCodecError: Exception
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
            height: Int
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
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            Timber.d("onAudioInputFormatChanged -> $format")
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            Timber.d("onVideoInputFormatChanged -> $format")
        }

        override fun onIsPlayingChanged(
            eventTime: AnalyticsListener.EventTime,
            isPlaying: Boolean
        ) {
            notificationHelper.isPlaying = isPlaying
        }

        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            Timber.d("onLoadStarted -> ${loadEventInfo.uri}")
            PlayerLoadStartEvent(loadEventInfo.uri).emit()
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean
        ) {
            Timber.w(error, "onLoadError -> ${loadEventInfo.uri}")
            PlayerLoadErrorEvent(loadEventInfo.uri, error).emit()
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
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
                Timber.d("-> getRetryDelayMsFor ${loadErrorInfo.exception} @${loadErrorInfo.errorCount}")
                val e = loadErrorInfo.exception
                if (
                    e is HttpDataSource.InvalidResponseCodeException &&
                    e.responseCode == 404
                ) {
                   return C.TIME_UNSET
                }
                return 5_000
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
        player.setMediaSource(progressiveFactory.createMediaSource(item))

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


