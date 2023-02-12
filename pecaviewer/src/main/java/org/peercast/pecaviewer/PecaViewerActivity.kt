package org.peercast.pecaviewer

import android.annotation.TargetApi
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.*
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.video.VideoSize
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaplay.core.app.backToPecaPlay
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.PostMessageDialogFragment
import org.peercast.pecaviewer.databinding.PecaViewerActivityBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.PlayerServiceEventFlow
import timber.log.Timber

class PecaViewerActivity : AppCompatActivity() {

    private val viewerViewModel by viewModel<PecaViewerViewModel>()
    private val playerViewModel by viewModel<PlayerViewModel>()
    private val chatViewModel by viewModel<ChatViewModel>()
    private val viewerPrefs by inject<PecaViewerPreference>()
    private lateinit var binding: PecaViewerActivityBinding
    private val service: PlayerService? get() = viewerViewModel.playerService.value
    private val stateKeyPlaying get() = "STATE_PLAYING#${intent.data}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewerViewModel.initViewModels(playerViewModel, chatViewModel)

        requestedOrientation = when (viewerPrefs.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        binding = DataBindingUtil.setContentView(this, R.layout.peca_viewer_activity)
        binding.appViewModel = viewerViewModel
        binding.lifecycleOwner = this

        binding.vPostDialogButton.setOnClickListener {
            //フルスクリーン時には一時的にコントロールボタンを
            //表示させないとOSのナビゲーションバーが残る
            if (viewerViewModel.isFullScreenMode.value)
                playerViewModel.isPlayerControlsVisible.value = true
            PostMessageDialogFragment.show(supportFragmentManager)
        }

        viewerViewModel.isFullScreenMode.value =
            requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        lifecycleScope.run {
            launch {
                viewerViewModel.isFullScreenMode.collect {
                    requestedOrientation = when (it) {
                        true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }

            launch {
                viewerViewModel.isFullScreenMode.collect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (it) {
                        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    }
                }
            }

            launch {
                chatViewModel.snackbarFactory.consumeEach {
                    it.show(
                        findViewById(android.R.id.content),
                        findViewById(R.id.vPostDialogButton)
                    )
                }
            }

            launch {
                isPortraitMode.collect {
                    val ap = 0.01f * if (it) {
                        resources.getInteger(R.integer.sliding_up_panel_anchor_point_port)
                    } else {
                        resources.getInteger(R.integer.sliding_up_panel_anchor_point_land)
                    }
                    binding.vSlidingUpPanel.anchorPoint = ap
                    binding.vSlidingUpPanel
                    if (it) {
                        initPanelState(viewerPrefs.initPanelState)
                    } else {
                        initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)
                    }
                }
            }

            launch {
                playerViewModel.isPlaying.collect {
                    updatePictureInPictureParams()
                }
            }
        }

        isPortraitMode.value = resources.configuration.isPortraitMode
        binding.vSlidingUpPanel.addPanelSlideListener(panelSlideListener)

        onBackPressedDispatcher.addCallback {
            quitOrEnterPipMode()
        }

        if (savedInstanceState?.getBoolean(stateKeyPlaying) != false)
            startPlay()
    }

    private fun initPanelState(state: SlidingUpPanelLayout.PanelState) {
        binding.vSlidingUpPanel.panelState = state
        binding.vSlidingUpPanel.doOnLayout {
            panelSlideListener.onPanelStateChanged(
                binding.vSlidingUpPanel, state, state
            )
        }
    }

    private val isPortraitMode = MutableStateFlow(false)

    private val panelSlideListener = object : SlidingUpPanelLayout.PanelSlideListener {
        override fun onPanelSlide(panel: View, __slideOffset: Float) {
            val b = binding.vPlayerFragmentContainer.bottom
            binding.vPlayerFragmentContainer.updatePadding(top = panel.height - b)
            //val toolbarHeight = resources.getDimension(R.dimen.player_toolbar_height).toInt()
            val toolbarHeight = findViewById<View>(R.id.vPlayerToolbar)?.height ?: 0
            binding.vChatFragmentContainer.updatePadding(bottom = b - toolbarHeight)
        }

        override fun onPanelStateChanged(
            panel: View,
            previousState: SlidingUpPanelLayout.PanelState,
            newState: SlidingUpPanelLayout.PanelState,
        ) {
            when (newState) {
                //パネル位置・中間
                SlidingUpPanelLayout.PanelState.ANCHORED -> {
                    onPanelSlide(panel, 0f)
                    chatViewModel.isToolbarVisible.value = true
                }
                //プレーヤーのみ表示
                SlidingUpPanelLayout.PanelState.EXPANDED -> {
                    binding.vPlayerFragmentContainer.updatePadding(top = 0)
                }
                //チャットのみ表示
                SlidingUpPanelLayout.PanelState.COLLAPSED -> {
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
                if (isPortraitMode.value && newState != previousState &&
                    (!API26 || !isInPictureInPictureMode)
                ) {
                    viewerPrefs.initPanelState = newState
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        startPlay()
    }

    private fun startPlay() {
        val streamUrl = checkNotNull(intent.data)
        val channel = checkNotNull(
            IntentCompat.getParcelableExtra(
                intent,
                PecaViewerIntent.EX_YP4G_CHANNEL,
                Yp4gChannel::class.java
            )
        )

        viewerViewModel.startPlay(streamUrl, channel)
        chatViewModel.loadUrl(channel.url)

        playerViewModel.channelTitle.value = channel.name
        playerViewModel.channelComment.value = channel.run { "$genre $description $comment".trim() }
    }

    override fun onStart() {
        super.onStart()
        viewerViewModel.bindPlayerService()
    }

    private fun updatePictureInPictureParams() {
        if (API26)
            setPictureInPictureParams(createPipParams())
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createPipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
        val size = service?.videoSize ?: VideoSize.UNKNOWN
        val ratio = Rational(size.width, size.height)
        if (size != VideoSize.UNKNOWN && ratio.toFloat() in 0.5f..2f) {
            b.setAspectRatio(ratio)
        }
        val action = if (service?.isPlaying != true) {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_play_circle_filled_black_96dp),
                "play",
                "play",
                PecaViewerIntent.createActionPendingIntent(this, PecaViewerIntent.ACTION_PLAY)
            )
        } else {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pause_circle_filled_black_96dp),
                "pause",
                "pause",
                PecaViewerIntent.createActionPendingIntent(this, PecaViewerIntent.ACTION_PAUSE)
            )
        }
        return b.setActions(listOf(action)).build()
    }

    /**Android8以降でプレーヤーをPIP化する。*/
    private fun enterPipMode(): Boolean {
        if (API26 &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            (service?.isPlaying == true || service?.isBuffering == true)
        ) {
            Timber.i("enterPipMode")
            return enterPictureInPictureMode(createPipParams())
        }
        return false
    }

    override fun onUserLeaveHint() {
        //ホームボタンが押された。
        Timber.d("onUserLeaveHint() isFinishing=$isFinishing")
        if (!isFinishing && viewerPrefs.isBackgroundPlaying &&
            API26 && !isInPictureInPictureMode
        ) {
            enterPipMode()
        }
    }

    fun quitOrEnterPipMode() {
        val hasEnteredPip = viewerPrefs.isBackgroundPlaying && enterPipMode()
        //hasEnteredPip:
        // true: プレーヤーをPIP化 & PecaPlay起動
        // false: (再生してないので) PIP化せず、単にPecaPlayへ戻る
        backToPecaPlay(this, !hasEnteredPip)
    }

    override fun onPause() {
        super.onPause()

        if (viewerPrefs.isBackgroundPlaying){
            val isInPipMode = API26 && isInPictureInPictureMode
            if (!isInPipMode)
                service?.enterBackgroundMode()
        } else {
            service?.stop()
        }
    }

    //PIPモードの終了イベントを得る
    //https://stackoverflow.com/questions/47066517/detect-close-and-maximize-clicked-event-in-picture-in-picture-mode-in-android
    private val pipWindowCloseObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            Timber.i("PipWindow closed.")
            service?.stop()
            lifecycle.removeObserver(this)
            finishAffinity()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isPortraitMode.value = newConfig.isPortraitMode
        updatePictureInPictureParams()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewerViewModel.isPipMode.value = isInPictureInPictureMode

        //PIPの閉じるボタンのイベントをなんとか得る
        if (isInPictureInPictureMode) {
            lifecycle.addObserver(pipWindowCloseObserver)
            binding.vSlidingUpPanel.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
        } else {
            lifecycle.removeObserver(pipWindowCloseObserver)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(stateKeyPlaying, viewerViewModel.playerService.value?.isPlaying ?: true)
    }

    override fun onDestroy() {
        viewerViewModel.unbindPlayerService()
        super.onDestroy()
    }


    companion object {
        private val Configuration.isPortraitMode get() = orientation == Configuration.ORIENTATION_PORTRAIT

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
        private val API26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}
