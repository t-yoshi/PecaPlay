package org.peercast.pecaviewer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.PostMessageDialogFragment
import org.peercast.pecaviewer.databinding.ActivityMainBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber

internal class MainFragment : Fragment(), ServiceConnection {

    private lateinit var binding: ActivityMainBinding
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val appViewModel by sharedViewModel<PecaViewerViewModel>()
    private val viewerPrefs by inject<PecaViewerPreference>()
    private var onServiceConnect: (PlayerService) -> Unit = {}
    private lateinit var channel: Yp4gChannel
    private var service: PlayerService? = null
    private val isLandscapeMode get() = resources.getBoolean(R.bool.isLandscapeMode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent =
            checkNotNull(requireArguments().getParcelable<Intent>(PecaViewerActivity.ARG_INTENT))
        val streamUrl = checkNotNull(intent.data)
        channel = checkNotNull(
            intent.getParcelableExtra(PecaViewerIntent.EX_YP4G_CHANNEL)
        )

        onServiceConnect = {
            it.prepareFromUri(streamUrl, channel)
            if (savedInstanceState?.getBoolean(STATE_PLAYING) != false) {
                Timber.d(" -> play")
                it.play()
            }
        }

        lifecycleScope.launchWhenCreated {
            chatViewModel.presenter.loadUrl(channel.url.toString())
        }



        requireContext().bindPlayerService(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ActivityMainBinding.inflate(layoutInflater).also {
            binding = it
            it.chatViewModel = chatViewModel
            it.playerViewModel = playerViewModel
            it.appViewModel = appViewModel
            it.lifecycleOwner = viewLifecycleOwner
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.vPostDialogButton.setOnClickListener {
            //フルスクリーン時には一時的にコントロールボタンを
            //表示させないとOSのナビゲーションバーが残る
            if (playerViewModel.isFullScreenMode.value == true)
                playerViewModel.isControlsViewVisible.value = true
            val f = PostMessageDialogFragment()
            f.show(parentFragmentManager, "tag#PostMessageDialogFragment")
        }

        binding.vSlidingUpPanel.addPanelSlideListener(panelSlideListener)

        val anchorPoint = if (isLandscapeMode) {
            initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)
            resources.getInteger(R.integer.sliding_up_panel_anchor_point_land)
        } else {
            initPanelState(viewerPrefs.initPanelState)
            resources.getInteger(R.integer.sliding_up_panel_anchor_point_port)
        }

        binding.vSlidingUpPanel.anchorPoint = anchorPoint / 100f
        binding.toolbar.title.text = channel.name
        binding.toolbar.text1.text = channel.run { "$genre $description $comment".trim() }
    }

    private fun initPanelState(state: SlidingUpPanelLayout.PanelState) {
        binding.vSlidingUpPanel.panelState = state
        binding.vSlidingUpPanel.doOnLayout {
            panelSlideListener.onPanelStateChanged(
                binding.vSlidingUpPanel,
                SlidingUpPanelLayout.PanelState.COLLAPSED,
                state
            )
        }
    }

    private val panelSlideListener = object : SlidingUpPanelLayout.PanelSlideListener {
        override fun onPanelSlide(panel: View, __slideOffset: Float) {
            val b = binding.vPlayerFragmentContainer.bottom
            binding.vPlayerFragmentContainer.updatePadding(top = panel.height - b)
            binding.vChatFragmentContainer.updatePadding(bottom = b - binding.toolbar.vPlayerToolbar.height)
        }

        override fun onPanelStateChanged(
            panel: View,
            previousState: SlidingUpPanelLayout.PanelState,
            newState: SlidingUpPanelLayout.PanelState,
        ) {
            when (newState) {
                //パネル位置・中間
                SlidingUpPanelLayout.PanelState.ANCHORED -> {
                    //Timber.d("vPlayerFragmentContainer=$vPlayerFragmentContainer")
                    //Timber.d("vChatFragmentContainer=$vChatFragmentContainer")
                    onPanelSlide(panel, 0f)
                    chatViewModel.isToolbarVisible.value = true
                }
                SlidingUpPanelLayout.PanelState.EXPANDED,
                SlidingUpPanelLayout.PanelState.COLLAPSED,
                -> {
                    binding.vPlayerFragmentContainer.updatePadding(top = 0)
                    binding.vChatFragmentContainer.updatePadding(bottom = 0)
                }
                else -> {
                }
            }

            if (newState in listOf(
                    SlidingUpPanelLayout.PanelState.EXPANDED,
                    SlidingUpPanelLayout.PanelState.COLLAPSED,
                    SlidingUpPanelLayout.PanelState.ANCHORED
                )
            ) {
                appViewModel.slidingPanelState.value = newState.ordinal
                if (!isLandscapeMode) {
                    viewerPrefs.initPanelState = newState
                }
            }
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as PlayerService.Binder).service.also(onServiceConnect)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PLAYING, service?.isPlaying ?: true)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (service != null)
            requireContext().unbindService(this)
    }

    companion object {
        private const val STATE_PLAYING = "STATE_PLAYING"
    }
}
