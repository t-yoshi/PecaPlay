package org.peercast.pecaviewer

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Rational
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import com.google.android.exoplayer2.video.VideoSize
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.parameter.parametersOf
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaplay.core.app.backToPecaPlay
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber

class PecaViewerActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var viewerViewModel: PecaViewerViewModel
    private val viewerPrefs by inject<PecaViewerPreference>()
    private var service: PlayerService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerViewModel = getViewModel()
        chatViewModel = getViewModel()
        viewerViewModel = getViewModel {
            parametersOf(
                playerViewModel,
                chatViewModel
            )
        }

        requestedOrientation = when (viewerPrefs.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (savedInstanceState == null) {
            replaceMainFragment()
        }

        playerViewModel.isFullScreenMode.let { ld ->
            ld.value = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ld.observe(this) {
                requestedOrientation = when (it) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }

        playerViewModel.isFullScreenMode.observe(this) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (it) {
                controller?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        }

        bindPlayerService(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        replaceMainFragment()
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

    /**Android8以降でプレーヤーをPIP化する。*/
    private fun enterPipMode(): Boolean {
        if (isApi26AtLeast &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            (service?.isPlaying == true || service?.isBuffering == true)
        ) {
            Timber.i("enterPipMode")
            val b = PictureInPictureParams.Builder()
            val size = service?.videoSize ?: VideoSize.UNKNOWN
            if (size != VideoSize.UNKNOWN) {
                b.setAspectRatio(Rational(size.width, size.height))
            }
            return enterPictureInPictureMode(b.build())
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

    override fun onBackPressed() {
        val hasEnteredPip = viewerPrefs.isBackgroundPlaying && enterPipMode()
        //hasEnteredPip:
        // true: プレーヤーをPIP化 & PecaPlay起動
        // false: (再生してないので) PIP化せず、単にPecaPlayへ戻る
        backToPecaPlay(this, !hasEnteredPip)
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            viewerViewModel.isImmersiveMode.value = true
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as PlayerService.Binder).service
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onStop() {
        super.onStop()

        //PIPモードの終了イベントを得る
        //https://stackoverflow.com/questions/47066517/detect-close-and-maximize-clicked-event-in-picture-in-picture-mode-in-android
        if (isApi26AtLeast && isInPictureInPictureMode) {
            Timber.i("PipWindow closed.")
            service?.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (service != null) {
            unbindService(this)
            onServiceDisconnected(null)
        }
    }


    companion object {
        const val ARG_INTENT = "intent"

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
        private val isApi26AtLeast = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}
