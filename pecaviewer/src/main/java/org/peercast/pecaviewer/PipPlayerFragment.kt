package org.peercast.pecaviewer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber

class PipPlayerFragment : Fragment(), ServiceConnection {

    private var service: PlayerService? = null
    private var vPlayer: PlayerView? = null
    private lateinit var channel: Yp4gChannel
    private val isVisibleTitleBar = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = checkNotNull(
            requireArguments().getParcelable<Intent>(PecaViewerActivity.ARG_INTENT)
        )
        val streamUrl = checkNotNull(intent.data)
        channel = checkNotNull(
            intent.getParcelableExtra(PecaViewerIntent.EX_YP4G_CHANNEL)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.pip_player_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vPlayer = view.findViewById(R.id.vPlayer)
        val vTitle = view.findViewById<TextView>(R.id.vTitle)
        val vTitleBar = view.findViewById<ViewGroup>(R.id.vTitleBar)
        vTitle.text = channel.name

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            isVisibleTitleBar.filter { it }.collect {
                delay(6_000)
                vTitleBar.startAnimation(AlphaAnimation(1f, 0f).apply {
                    duration = 200
                    fillAfter = true
                })
                isVisibleTitleBar.value = false
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isVisibleTitleBar.value = true
    }


    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        Timber.d("onServiceConnected")
        service = (binder as PlayerService.Binder).service.also {
            vPlayer?.let(it::attachPlayerView)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Timber.d("onServiceDisconnected")
        service = null
        vPlayer?.player = null
    }

    override fun onStart() {
        super.onStart()

        requireContext().bindPlayerService(this)
    }

    override fun onStop() {
        super.onStop()

        if (service != null) {
            requireContext().unbindService(this)
            onServiceDisconnected(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vPlayer = null
    }

}