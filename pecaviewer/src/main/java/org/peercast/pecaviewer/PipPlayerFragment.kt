package org.peercast.pecaviewer

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.databinding.PipPlayerFragmentBinding
import org.peercast.pecaviewer.service.bindPlayerView

class PipPlayerFragment : Fragment() {

    private val viewerViewModel by sharedViewModel<PecaViewerViewModel>()
    private lateinit var binding: PipPlayerFragmentBinding
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
        return PipPlayerFragmentBinding.inflate(inflater, container, false).also {
            binding = it
            it.title = channel.name
            it.isVisibleTitleBar = isVisibleTitleBar
            it.lifecycleOwner = viewLifecycleOwner
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            isVisibleTitleBar.filter { it }.collect {
                delay(6_000)
                isVisibleTitleBar.value = false
            }
        }

        viewerViewModel.playerService.bindPlayerView(binding.vPlayer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isVisibleTitleBar.value = true
    }
}