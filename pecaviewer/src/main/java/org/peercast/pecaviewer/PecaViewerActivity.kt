package org.peercast.pecaviewer

import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.PostMessageDialogFragment
import org.peercast.pecaviewer.databinding.ActivityMainBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.NotificationHelper
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber

class PecaViewerActivity : AppCompatActivity(),
    ServiceConnection {

    private lateinit var binding: ActivityMainBinding
    private val playerViewModel by viewModel<PlayerViewModel>()
    private val chatViewModel by viewModel<ChatViewModel>()
    private val appViewModel by viewModel<ViewerViewModel> {
        parametersOf(
            playerViewModel,
            chatViewModel
        )
    }
    private val viewerPrefs by inject<ViewerPreference>()
    private var onServiceConnect: (PlayerService) -> Unit = {}
    private var service: PlayerService? = null
    private val isLandscapeMode get() = resources.getBoolean(R.bool.isLandscapeMode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = when (viewerPrefs.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        binding = ActivityMainBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            binding.chatViewModel = chatViewModel
            binding.playerViewModel = playerViewModel
            binding.appViewModel = appViewModel
            binding.lifecycleOwner = this
        }

        onViewCreated()

        playerViewModel.isFullScreenMode.let { ld ->
            ld.value = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ld.observe(this) {
                viewerPrefs.isFullScreenMode = it
                requestedOrientation = when (it) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }

        val streamUrl = checkNotNull(intent.data)
        val channel = checkNotNull(
            intent.getParcelableExtra(PecaViewerIntent.EX_YP4G_CHANNEL) as? Yp4gChannel
        )

        onServiceConnect = {
            it.prepareFromUri(streamUrl, channel)
            if (savedInstanceState?.getBoolean(STATE_PLAYING) != false) {
                Timber.d(" -> play")
                it.play()
            }
        }

        binding.toolbar.title.text = channel.name
        binding.toolbar.text1.text = channel.run { "$genre $description $comment".trim() }

        lifecycleScope.launch {
            chatViewModel.presenter.loadUrl(channel.url.toString())
        }

        registerReceiver(receiver, IntentFilter(NotificationHelper.ACTION_STOP))

        bindPlayerService(this)
    }

    private fun onViewCreated() {
        binding.vPostDialogButton.setOnClickListener {
            //フルスクリーン時には一時的にコントロールボタンを
            //表示させないとOSのナビゲーションバーが残る
            if (playerViewModel.isFullScreenMode.value == true)
                playerViewModel.isControlsViewVisible.value = true
            val f = PostMessageDialogFragment()
            f.show(supportFragmentManager, "tag#PostMessageDialogFragment")
        }

        binding.vSlidingUpPanel.addPanelSlideListener(panelSlideListener)

        playerViewModel.isFullScreenMode.observe(this) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (it) {
                insetsController?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                insetsController?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }

        val anchorPercentage = if (isLandscapeMode) {
            initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)
            resources.getInteger(R.integer.sliding_up_panel_anchor_point_land)
        } else {
            initPanelState(viewerPrefs.initPanelState)
            resources.getInteger(R.integer.sliding_up_panel_anchor_point_port)
        }
        binding.vSlidingUpPanel.anchorPoint = anchorPercentage / 100f
    }

    //通知バーの停止ボタンが押されたとき
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationHelper.ACTION_STOP -> finish()
            }
        }
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

    fun navigateToParentActivity() {
        if (intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0 ||
            intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0
        ) {
            //通知バーから復帰した場合
            NavUtils.navigateUpFromSameTask(this)
        } else {
            //PecaPlayActivityから起動した場合
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
        if (service != null)
            unbindService(this)
    }

    companion object {
        private const val STATE_PLAYING = "STATE_PLAYING"
    }
}
