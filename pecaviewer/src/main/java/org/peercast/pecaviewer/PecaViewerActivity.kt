package org.peercast.pecaviewer

import android.app.PictureInPictureParams
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.util.Rational
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.exoplayer2.video.VideoSize
import okhttp3.internal.toHexString
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.parameter.parametersOf
import org.peercast.pecaplay.core.app.AppActivityLauncher
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.NotificationHelper
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService
import timber.log.Timber
import kotlin.properties.Delegates

class PecaViewerActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var viewerViewModel: PecaViewerViewModel
    private val viewerPrefs by inject<PecaViewerPreference>()
    private val launcher by inject<AppActivityLauncher>()
    private var service: PlayerService? = null

    //通知バーの停止ボタンが押されたとき
    private var isStopPushed by Delegates.notNull<Boolean>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationHelper.ACTION_STOP -> {
                    isStopPushed = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isStopPushed = savedInstanceState?.getBoolean(STATE_STOP_PUSHED) ?: false

        playerViewModel = getViewModel()
        chatViewModel = getViewModel()
        viewerViewModel = getViewModel<PecaViewerViewModel> {
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
                viewerPrefs.isFullScreenMode = it
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

        registerReceiver(receiver, IntentFilter(NotificationHelper.ACTION_STOP))
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
            true -> PictureInPictureFragment()
            else -> MainFragment()
        }
        f.arguments = Bundle(1).also {
            it.putParcelable(ARG_INTENT, intent)
        }
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, f)
            .commit()
    }

    /**ユーザーがPipを要求した*/
    fun requestEnterPipMode(){
        if (enterPipMode()) {
            //プレーヤーをPIP化 & PecaPlay起動
            launcher.launchPecaPlay(this)
        } else {
            //(停止中なので) PIP化せず、単にPecaPlayへ戻る
            launcher.backToPecaPlay(this)
        }
    }

    //PIPモードの終了イベントを得る方法はない
    //https://stackoverflow.com/questions/47066517/detect-close-and-maximize-clicked-event-in-picture-in-picture-mode-in-android
    private val pipWindowCloseObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun onStop() {
            Timber.i("PipWindow closed.")
            service?.stop()
            lifecycle.removeObserver(this)
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
            intent.removeExtra(PecaViewerIntent.EX_LAUNCHED_FROM)
            return enterPictureInPictureMode(b.build())
        }
        return false
    }

    override fun onUserLeaveHint() {
        //ホームボタンが押された。
        Timber.d("onUserLeaveHint() isFinishing=$isFinishing")
        if (!isFinishing && isApi26AtLeast && !isInPictureInPictureMode) {
            enterPipMode()
        }
    }

    override fun onBackPressed() {
        launcher.backToPecaPlay(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Timber.d("onPictureInPictureModeChanged $isInPictureInPictureMode")

        //PIPの閉じるボタンのイベントをなんとか得る
        if (isInPictureInPictureMode) {
            lifecycle.addObserver(pipWindowCloseObserver)
        } else {
            lifecycle.removeObserver(pipWindowCloseObserver)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //回転時に再生成
        replaceMainFragment()
    }

    override fun onPause() {
        super.onPause()

        val isInPipMode = isApi26AtLeast && isInPictureInPictureMode
        if (!(viewerPrefs.isBackgroundPlaying || isInPipMode)) {
            service?.stop()
        }
    }

    override fun onResume() {
        super.onResume()

        if (isStopPushed) {
            finish()
            //navigateToParentActivity()
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as PlayerService.Binder).service
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putBoolean(STATE_STOP_PUSHED, isStopPushed)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)

        if (service != null) {
            unbindService(this)
            onServiceDisconnected(null)
        }
    }


    companion object {
        const val ARG_INTENT = "intent"

        private const val STATE_STOP_PUSHED = "stop-pushed"

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
        private val isApi26AtLeast = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}
