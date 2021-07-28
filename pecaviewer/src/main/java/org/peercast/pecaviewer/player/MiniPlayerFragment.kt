package org.peercast.pecaviewer.player

import android.content.ComponentName
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
    private var vPlayer: PlayerView? = null
    private var vTitle: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.miniplayer_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vPlayer = view.findViewById(R.id.vPlayer)
        vTitle = view.findViewById(R.id.vTitle)

        view.findViewById<ImageView>(R.id.vResume)?.setOnClickListener {
            service?.resumeIntent?.let {
                startActivity(it)
            }
        }

        view.findViewById<ImageView>(R.id.vClose).setOnClickListener {
            service?.stop()
            unbindPlayerService()
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().bindPlayerService(this)
    }

    private fun unbindPlayerService() {
        requireContext().unbindService(this)
        onServiceDisconnected(null)
    }

    override fun onStop() {
        super.onStop()

        if (service != null)
            unbindPlayerService()
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        Timber.d("onServiceConnected")
        service = (binder as PlayerService.Binder).service.also {
            if (it.isPlaying) {
                view?.isVisible = true
                vPlayer?.let(it::attachPlayerView)
                val ch =
                    it.resumeIntent.getParcelableExtra<Yp4gChannel>(PecaViewerIntent.EX_YP4G_CHANNEL)
                vTitle?.text = ch?.name
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.d("onServiceDisconnected")
        service = null
        view?.isVisible = false
        vPlayer?.player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vPlayer = null
        vTitle = null
    }

}