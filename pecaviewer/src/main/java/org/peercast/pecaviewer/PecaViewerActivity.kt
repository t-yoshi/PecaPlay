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
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.video.VideoSize
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaplay.core.app.backToPecaPlay
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.PlayerServiceEventFlow
import org.peercast.pecaviewer.service.PlayerWhenReadyChangedEvent
import timber.log.Timber

class PecaViewerActivity : AppCompatActivity() {

    private val playerViewModel by viewModel<PlayerViewModel>()
    private val chatViewModel by viewModel<ChatViewModel>()
    private val viewerViewModel by viewModel<PecaViewerViewModel> {
        parametersOf(
            playerViewModel,
            chatViewModel
        )
    }
    private val viewerPrefs by inject<PecaViewerPreference>()
    private val service: PlayerService? get() = viewerViewModel.playerService.value
    private val eventFlow by inject<PlayerServiceEventFlow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = when (viewerPrefs.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        viewerViewModel.toString() // instantiate

        if (savedInstanceState == null) {
            replaceMainFragment()
        }

        playerViewModel.isFullScreenMode.value =
            requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        lifecycleScope.launch {
            playerViewModel.isFullScreenMode.collect {
                requestedOrientation = when (it) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }

        lifecycleScope.launch {
            playerViewModel.isFullScreenMode.collect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (it) {
                    controller?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    controller?.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                }
            }
        }

        lifecycleScope.launch {
            chatViewModel.snackbarFactory.consumeEach {
                it.show(
                    findViewById(android.R.id.content),
                    findViewById(R.id.vPostDialogButton)
                )
            }
        }

        if (isApi26AtLeast) {
            lifecycleScope.launch {
                eventFlow.filterIsInstance<PlayerWhenReadyChangedEvent>().collect { ev ->
                    if (isInPictureInPictureMode)
                        setPictureInPictureParams(createPipParams())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        replaceMainFragment()
    }

    override fun onStart() {
        super.onStart()
        viewerViewModel.bindPlayerService()
    }

    private fun replaceMainFragment() {
        checkNotNull(intent.data)
        checkNotNull(
            intent.getParcelableExtra(PecaViewerIntent.EX_YP4G_CHANNEL) as? Yp4gChannel
        )

        val f = when (isApi26AtLeast && isInPictureInPictureMode) {
            true -> PipPlayerFragment()
            else -> PecaViewerFragment()
        }
        f.arguments = Bundle(1).also {
            it.putParcelable(ARG_INTENT, intent)
        }

        supportFragmentManager.commit {
            replace(android.R.id.content, f)
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createPipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
        val size = service?.videoSize ?: VideoSize.UNKNOWN
        if (size != VideoSize.UNKNOWN) {
            b.setAspectRatio(Rational(size.width, size.height))
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
        if (isApi26AtLeast &&
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
            isApi26AtLeast && !isInPictureInPictureMode
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

    override fun onBackPressed() {
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            super.onBackPressed()
        } else {
            quitOrEnterPipMode()
        }
    }

    override fun onPause() {
        super.onPause()

        val isInPipMode = isApi26AtLeast && isInPictureInPictureMode
        if (!(viewerPrefs.isBackgroundPlaying || isInPipMode)) {
            service?.stop()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        replaceMainFragment()
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        //PIPの閉じるボタンのイベントをなんとか得る
        if (isInPictureInPictureMode) {
            lifecycle.addObserver(pipWindowCloseObserver)
        } else {
            lifecycle.removeObserver(pipWindowCloseObserver)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewerViewModel.unbindPlayerService()
    }


    companion object {
        const val ARG_INTENT = "intent"

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
        private val isApi26AtLeast = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}
