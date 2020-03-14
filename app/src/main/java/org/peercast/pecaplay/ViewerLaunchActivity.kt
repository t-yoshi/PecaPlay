package org.peercast.pecaplay

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 *  ViewerLaunchActivity (wmv, flvのインテントを受ける)
 *    -> PecaPlayActivity
 *       ->  プレーヤー
 *
 * */
class ViewerLaunchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val it = Intent(ACTION_LAUNCH_PECA_VIEWER, intent.data, this, PecaPlayActivity::class.java)
        it.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        it.putExtras(intent.extras ?: Bundle.EMPTY)
        it.putExtra(EX_LAUNCH_EXPIRE, System.currentTimeMillis() + 3_000)
        startActivity(it)
    }
    companion object {
        const val ACTION_LAUNCH_PECA_VIEWER = "org.peercast.pecaplay.launch-pecaviewer"
        const val EX_LAUNCH_EXPIRE = "launch-expire"

    }
}