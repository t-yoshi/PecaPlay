package org.peercast.pecaviewer.player

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.ui.PlayerView
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.ViewerPreference
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber

@Suppress("unused")
class MiniPlayerFragment : Fragment(), ServiceConnection {

    private val viewerPrefs by inject<ViewerPreference>()
    private var service: PlayerService? = null
    private val vPlayer get() = view as PlayerView?
    private val vTitle: TextView? get() = view?.findViewById(R.id.vTitle)
    private var isLaunchPecaViewerActivity = false
    private var wasPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wasPlaying = savedInstanceState?.getBoolean(STATE_PLAYING) ?: false
        Timber.d("onCreate: $savedInstanceState wasPlaying->$wasPlaying")

        requireContext().let { c ->
            c.startService(Intent(c, PlayerService::class.java))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.miniplayer_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //[]ボタン
        view.findViewById<ImageView>(R.id.exo_pause)?.setOnClickListener {
            isLaunchPecaViewerActivity = true
            service?.playingIntent?.let {
                startActivity(it)
            }
        }

        view.findViewById<ImageView>(R.id.vClose).setOnClickListener {
            service?.stop()
            it.context.let { c ->
                c.stopService(Intent(c, PlayerService::class.java))
            }
            unbindPlayerService()
        }
    }

    private fun unbindPlayerService() {
        if (service != null) {
            wasPlaying = service!!.isPlaying
            requireContext().unbindService(this)
            onServiceDisconnected(null)
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().bindPlayerService(this)
    }

    override fun onStop() {
        super.onStop()

        if (!(viewerPrefs.isBackgroundPlaying || isLaunchPecaViewerActivity)) {
            service?.stop()
        }

        unbindPlayerService()
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        Timber.d("onServiceConnected")
        service = (binder as PlayerService.Binder).service.also {
            val ch =
                it.playingIntent.getParcelableExtra<Yp4gChannel>(PecaViewerIntent.EX_YP4G_CHANNEL)
            Timber.d("wasPlaying -> $wasPlaying, ch -> $ch")
            if (ch != null) {
                if (wasPlaying)
                    it.play()
                vPlayer?.isVisible = true
                vPlayer?.let(it::attachPlayerView)
                vTitle?.text = ch.name
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.d("onServiceDisconnected")
        service = null
        vPlayer?.run {
            isVisible = false
            player = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.d("onSaveInstanceState: $wasPlaying")
        outState.putBoolean(STATE_PLAYING, wasPlaying)
    }

    companion object {
        private const val STATE_PLAYING = "playing"
    }

}