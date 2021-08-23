package org.peercast.pecaviewer.player

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.PecaViewerPreference
import org.peercast.pecaviewer.PecaViewerViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.service.bindPlayerView
import org.peercast.pecaviewer.util.takeScreenShot
import timber.log.Timber

@Suppress("unused")
class PlayerFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private val viewerViewModel by sharedViewModel<PecaViewerViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val viewerPrefs by inject<PecaViewerPreference>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    private val vPlayer: PlayerView get() = requireView() as PlayerView
    private lateinit var vPlayerControlBar: Toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vPlayerControlBar = view.findViewById<Toolbar>(R.id.vPlayerControlBar).also {
            it.menu.clear()
            it.inflateMenu(R.menu.menu_player_control)
            it.setOnMenuItemClickListener(this)
            it.menu.findItem(R.id.menu_background).isChecked = viewerPrefs.isBackgroundPlaying
        }

        playerViewModel.isFullScreenMode.observe(viewLifecycleOwner) {
            vPlayerControlBar.menu.run {
                findItem(R.id.menu_enter_fullscreen).isVisible = !it
                findItem(R.id.menu_exit_fullscreen).isVisible = it
            }
        }

        vPlayer.setControllerVisibilityListener {
            playerViewModel.isControlsViewVisible.value = it == View.VISIBLE
        }

        vPlayerControlBar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        view.setOnTouchListener(DoubleTapDetector(view, ::onFullScreenClicked))

        viewerViewModel.playerService.bindPlayerView(vPlayer)
    }

    private fun onFullScreenClicked(v__: View) {
        playerViewModel.isFullScreenMode.let {
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
                playerViewModel.isFullScreenMode.value = true
                viewerPrefs.isFullScreenMode = true
            }
            R.id.menu_exit_fullscreen -> {
                playerViewModel.isFullScreenMode.value = false
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
            lifecycleScope.launch {
                kotlin.runCatching {
                    takeScreenShot(vPlayer.videoSurfaceView as SurfaceView, 256)
                }.onSuccess {
                    sv.setThumbnail(it)
                }.onFailure(Timber::w)
            }
        }
    }
}
