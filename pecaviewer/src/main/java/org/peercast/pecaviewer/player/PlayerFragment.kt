package org.peercast.pecaviewer.player

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.PecaViewerActivity
import org.peercast.pecaviewer.PecaViewerPreference
import org.peercast.pecaviewer.PecaViewerViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.PlayerFragmentBinding
import org.peercast.pecaviewer.util.takeScreenShot
import timber.log.Timber

@Suppress("unused")
class PlayerFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private val viewerViewModel by sharedViewModel<PecaViewerViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
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

        viewLifecycleOwner.lifecycleScope.run {
            launch {
                viewerViewModel.isFullScreenMode.collect {
                    vPlayerControlBar.menu.run {
                        findItem(R.id.menu_enter_fullscreen).isVisible = !it
                        findItem(R.id.menu_exit_fullscreen).isVisible = it
                    }
                }
            }
            launch {
                viewerViewModel.playerService.filterNotNull().collect {
                    it.attachView(binding.vPlayer)
                }
            }
            launch {
                playerViewModel.isToolbarVisible.collect {
                    binding.vPlayerToolbar.requestLayout()
                }
            }
        }

        binding.vPlayer.setControllerVisibilityListener {
            viewerViewModel.isPlayerControlsVisible.value = it == View.VISIBLE
        }

        vPlayerControlBar.setNavigationOnClickListener {
            (requireActivity() as PecaViewerActivity).quitOrEnterPipMode()
        }

        view.setOnTouchListener(DoubleTapDetector(view, ::onFullScreenClicked))
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        binding.vPlayerToolbar.isVisible = !isInPictureInPictureMode
        binding.vPlayer.useController = !isInPictureInPictureMode
    }

    private fun onFullScreenClicked(v__: View) {
        viewerViewModel.isFullScreenMode.let {
            it.value = it.value != true
            viewerPrefs.isFullScreenMode = it.value == true
        }
    }

    private class DoubleTapDetector(
        private val view: View,
        private val onDoubleTap: (View) -> Unit,
    ) : View.OnTouchListener, GestureDetector.SimpleOnGestureListener() {
        private val detector = GestureDetector(view.context, this)

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            onDoubleTap(view)
            return true
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
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

        super.onPause()
    }

}
