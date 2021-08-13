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
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.PostMessageDialogFragment
import org.peercast.pecaviewer.databinding.PecaViewerFragmentBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber

internal class PecaViewerFragment : Fragment() {

    private lateinit var binding: PecaViewerFragmentBinding
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val viewerViewModel by sharedViewModel<PecaViewerViewModel>()
    private val viewerPrefs by inject<PecaViewerPreference>()

    private val isPortraitMode = MutableStateFlow(false)
    private lateinit var channel: Yp4gChannel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = checkNotNull(
            requireArguments().getParcelable<Intent>(PecaViewerActivity.ARG_INTENT)
        )
        val streamUrl = checkNotNull(intent.data)
        channel = checkNotNull(
            intent.getParcelableExtra(PecaViewerIntent.EX_YP4G_CHANNEL)
        )

        lifecycleScope.launch {
            viewerViewModel.playerService.filterNotNull().collect {
                it.prepareFromUri(streamUrl, channel)
                if (savedInstanceState?.getBoolean(STATE_PLAYING) != false) {
                    Timber.d(" -> play")
                    it.play()
                }
            }
        }

        lifecycleScope.launch {
            chatViewModel.presenter.loadUrl(channel.url.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return PecaViewerFragmentBinding.inflate(layoutInflater).also {
            binding = it
            it.chatViewModel = chatViewModel
            it.playerViewModel = playerViewModel
            it.appViewModel = viewerViewModel
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

        isPortraitMode.value = resources.configuration.isPortraitMode
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            isPortraitMode.collect {
                binding.vSlidingUpPanel.anchorPoint = 0.01f * if (it) {
                    resources.getInteger(R.integer.sliding_up_panel_anchor_point_port)
                } else {
                    resources.getInteger(R.integer.sliding_up_panel_anchor_point_land)
                }
                if (it) {
                    initPanelState(viewerPrefs.initPanelState)
                } else {
                    initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)
                }
            }
        }

        binding.vSlidingUpPanel.addPanelSlideListener(panelSlideListener)

        binding.toolbar.title.text = channel.name
        binding.toolbar.text1.text = channel.run { "$genre $description $comment".trim() }
    }

    private fun initPanelState(state: SlidingUpPanelLayout.PanelState) {
        binding.vSlidingUpPanel.panelState = state
        binding.vSlidingUpPanel.doOnLayout {
            panelSlideListener.onPanelStateChanged(
                binding.vSlidingUpPanel, state, state
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

            if (newState in setOf(
                    SlidingUpPanelLayout.PanelState.EXPANDED,
                    SlidingUpPanelLayout.PanelState.COLLAPSED,
                    SlidingUpPanelLayout.PanelState.ANCHORED
                )
            ) {
                if (viewerViewModel.slidingPanelState.value != newState.ordinal) {
                    viewerViewModel.slidingPanelState.value = newState.ordinal
                }
                if (isPortraitMode.value && newState != previousState) {
                    viewerPrefs.initPanelState = newState
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PLAYING, viewerViewModel.playerService.value?.isPlaying ?: true)
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        isPortraitMode.value = newConfig.isPortraitMode
//        view?.requestLayout()
//    }


    companion object {
        private const val STATE_PLAYING = "STATE_PLAYING"

        private val Configuration.isPortraitMode get() = orientation == Configuration.ORIENTATION_PORTRAIT

    }
}
