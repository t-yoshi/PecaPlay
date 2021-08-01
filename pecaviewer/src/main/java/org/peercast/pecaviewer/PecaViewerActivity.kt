package org.peercast.pecaviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.peercast.pecaplay.core.app.PecaViewerIntent
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.NotificationHelper
import timber.log.Timber

class PecaViewerActivity : AppCompatActivity() {

    private val playerViewModel by viewModel<PlayerViewModel>()
    private val viewerPrefs by inject<ViewerPreference>()

    //通知バーの停止ボタンが押されたとき
    private var isStopPushed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = when (viewerPrefs.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        checkNotNull(intent.data)
        checkNotNull(
            intent.getParcelableExtra(PecaViewerIntent.EX_YP4G_CHANNEL) as? Yp4gChannel
        )

        if (savedInstanceState == null) {
            val f = PecaViewerFragment()
            f.arguments = Bundle().also {
                it.putParcelable(PecaViewerFragment.ARG_INTENT, intent)
            }
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, f)
                .commit()
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
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationHelper.ACTION_STOP -> {
                    isStopPushed = true
                }
            }
        }
    }

    fun navigateToParentActivity() {
        //Timber.d("navigateToParentActivity: ${intent.flags}")
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

    override fun onResume() {
        super.onResume()

        if (isStopPushed) {
            navigateToParentActivity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }

}
