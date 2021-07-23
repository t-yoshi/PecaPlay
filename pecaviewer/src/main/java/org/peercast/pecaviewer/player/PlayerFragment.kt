package org.peercast.pecaviewer.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import androidx.appcompat.widget.ActionMenuView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.ViewerPreference
import org.peercast.pecaviewer.ViewerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import org.peercast.pecaviewer.util.takeScreenShot
import timber.log.Timber

@Suppress("unused")
class PlayerFragment : Fragment(), ServiceConnection {

    private val appViewModel by sharedViewModel<ViewerViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val viewerPrefs by inject<ViewerPreference>()

    private var service: PlayerService? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    private lateinit var vPlayer: PlayerView
    private lateinit var vPlayerMenu: ActionMenuView
    private lateinit var vQuit: ImageView
    private lateinit var vFullScreen: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vPlayer = view as PlayerView
        vPlayerMenu = view.findViewById(R.id.vPlayerMenu)
        vQuit = view.findViewById(R.id.vQuit)
        vFullScreen = view.findViewById(R.id.vFullScreen)

        playerViewModel.isFullScreenMode.observe(viewLifecycleOwner) {
            val r = when (it) {
                true -> R.drawable.ic_fullscreen_exit_white_36dp
                else -> R.drawable.ic_fullscreen_white_36dp
            }
            vFullScreen.setImageResource(r)
        }

        vPlayer.setControllerVisibilityListener {
            playerViewModel.isControlsViewVisible.value = it == View.VISIBLE
        }

        vPlayerMenu.also {
            MenuInflater(it.context).inflate(R.menu.menu_player, it.menu)
            onPrepareOptionsMenu(it.menu)
            it.setOnMenuItemClickListener(::onOptionsItemSelected)
        }

        vQuit.setOnClickListener {
            activity?.finish()
        }
        vFullScreen.setOnClickListener(::onFullScreenClicked)
        view.setOnTouchListener(DoubleTabDetector(view, ::onFullScreenClicked))
    }

    private fun onFullScreenClicked(v__: View) {
        playerViewModel.isFullScreenMode.let {
            it.value = it.value != true
        }
    }

    private class DoubleTabDetector(
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

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_background).isChecked = viewerPrefs.isBackgroundPlaying
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_background -> {
                item.isChecked = !item.isChecked
                viewerPrefs.isBackgroundPlaying = item.isChecked
            }
        }

        onPrepareOptionsMenu(vPlayerMenu.menu)
        return true
    }

    override fun onResume() {
        super.onResume()
        if (service == null)
            requireContext().bindPlayerService(this)
    }

    override fun onPause() {
        super.onPause()

        val sv = service ?: return
        service = null

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (sv.isPlaying) {
                    kotlin.runCatching {
                        takeScreenShot(vPlayer.videoSurfaceView as SurfaceView, 256)
                    }.onSuccess {
                        sv.thumbnail = it
                    }.onFailure(Timber::w)
                }

                if (requireActivity().isInPictureInPictureMode)
                    return@launch
            }

            if (!viewerPrefs.isBackgroundPlaying) {
                sv.stop()
            }
            vPlayer.player = null
            requireContext().unbindService(this@PlayerFragment)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        vPlayer.useController = !isInPictureInPictureMode
        if (isInPictureInPictureMode)
            playerViewModel.isControlsViewVisible.value = false
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        //Timber.d("$binder")
        service = (binder as PlayerService.Binder).service.also { s ->
            s.attachPlayerView(vPlayer)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

}