package org.peercast.pecaviewer

import android.app.PictureInPictureParams
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val appPreference by inject<ViewerPreference>()
    private var onServiceConnect: (PlayerService) -> Unit = {}
    private var service: PlayerService? = null
    private val isLandscapeMode = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLandscapeMode.value = resources.configuration.isLandscapeMode

        requestedOrientation = when (appPreference.isFullScreenMode) {
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
                appPreference.isFullScreenMode = it
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

        appViewModel.isImmersiveMode.observe(this) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscapeMode.value && insetsController != null) {
                when (it) {
                    true -> insetsController::hide
                    else -> insetsController::show
                }(WindowInsetsCompat.Type.systemBars())
            }
        }

        isLandscapeMode.onEach {
            Timber.d("isLandscapeMode: $it")
            val anchorPercentage: Int
            if (it) {
                anchorPercentage =
                    resources.getInteger(R.integer.sliding_up_panel_anchor_point_land)
                initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)
            } else {
                anchorPercentage =
                    resources.getInteger(R.integer.sliding_up_panel_anchor_point_port)
                initPanelState(appPreference.initPanelState)
            }
            binding.vSlidingUpPanel.anchorPoint = anchorPercentage / 100f
        }.launchIn(lifecycleScope)
    }

    override fun onUserLeaveHint() {
        //TODO: pip mode
        if (false &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            Timber.d("-> onUserLeaveHint()")
            val params = PictureInPictureParams.Builder()
                //.setActions()
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        isLandscapeMode.value = newConfig.isLandscapeMode
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
                if (!isLandscapeMode.value)
                    appPreference.initPanelState = newState
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

    override fun onBackPressed() {
        NavUtils.navigateUpFromSameTask(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
        if (service != null)
            unbindService(this)
    }

    companion object {
        private val Configuration.isLandscapeMode: Boolean
            get() = orientation == Configuration.ORIENTATION_LANDSCAPE

        private const val STATE_PLAYING = "STATE_PLAYING"
    }
}
