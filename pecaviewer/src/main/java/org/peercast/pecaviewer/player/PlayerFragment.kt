package org.peercast.pecaviewer.player

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.peercast.pecaviewer.PecaViewerActivity
import org.peercast.pecaviewer.PecaViewerPreference
import org.peercast.pecaviewer.PecaViewerViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.PlayerFragmentBinding
import org.peercast.pecaviewer.util.takeScreenShot
import timber.log.Timber

@Suppress("unused")
class PlayerFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private val viewerViewModel by activityViewModel<PecaViewerViewModel>()
    private val playerViewModel by activityViewModel<PlayerViewModel>()
    private val viewerPrefs by inject<PecaViewerPreference>()
    private lateinit var binding: PlayerFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return PlayerFragmentBinding.inflate(layoutInflater, container, false).let {
            it.playerViewModel = playerViewModel
            it.lifecycleOwner = viewLifecycleOwner
            binding = it
            it.root
        }
    }

    private lateinit var vPlayerControlBar: Toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vPlayerControlBar = view.findViewById<Toolbar>(R.id.vPlayerControlBar).also {
            it.menu.clear()
            it.inflateMenu(R.menu.menu_player_control)
            it.setOnMenuItemClickListener(this)
            it.menu.findItem(R.id.menu_background).isChecked = viewerPrefs.isBackgroundPlaying
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewerViewModel.isFullScreenMode.collect {
                vPlayerControlBar.menu.run {
                    findItem(R.id.menu_enter_fullscreen).isVisible = !it
                    findItem(R.id.menu_exit_fullscreen).isVisible = it
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewerViewModel.playerService.collect { sv->
                     sv?.attachView(binding.vPlayer) ?: kotlin.run {
                         binding.vPlayer.player = null
                     }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            playerViewModel.isToolbarVisible.collect {
                binding.vPlayerToolbar.requestLayout()
            }
        }

        binding.vPlayer.setControllerVisibilityListener(StyledPlayerView.ControllerVisibilityListener {
            playerViewModel.isPlayerControlsVisible.value = it == View.VISIBLE
        })

        vPlayerControlBar.setNavigationOnClickListener {
            binding.vPlayer.hideController()
            (requireActivity() as PecaViewerActivity).quitOrEnterPipMode()
        }

        binding.vPlayer.setOnTouchListener(DoubleTapDetector())
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        binding.vPlayerToolbar.isVisible = !isInPictureInPictureMode
        binding.vPlayer.useController = !isInPictureInPictureMode
    }

    private inner class DoubleTapDetector : View.OnTouchListener,
        GestureDetector.SimpleOnGestureListener() {
        private val detector = GestureDetector(requireContext(), this)

        override fun onDoubleTap(e: MotionEvent): Boolean {
            viewerViewModel.isFullScreenMode.let {
                it.value = it.value != true
                viewerPrefs.isFullScreenMode = it.value == true
            }
            return true
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            return detector.onTouchEvent(event)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_enter_fullscreen -> {
                viewerViewModel.isFullScreenMode.value = true
                viewerPrefs.isFullScreenMode = true
            }
            R.id.menu_exit_fullscreen -> {
                viewerViewModel.isFullScreenMode.value = false
                viewerPrefs.isFullScreenMode = false
            }

            R.id.menu_background -> {
                item.isChecked = !item.isChecked
                viewerPrefs.isBackgroundPlaying = item.isChecked
            }
        }

        return true
    }

    override fun onPause() {
        super.onPause()
        val sv = viewerViewModel.playerService.value ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sv.isPlaying) {
            viewLifecycleOwner.lifecycleScope.launch {
                kotlin.runCatching {
                    takeScreenShot(binding.vPlayer.videoSurfaceView as SurfaceView, 256)
                }.onSuccess {
                    sv.setThumbnail(it)
                }.onFailure(Timber::w)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.vPlayer.player = null
    }

}
