package org.peercast.pecaplay.core.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

object PecaPlayIntent {
    const val ACTION_LAUNCH_VIEWER = "ACTION_LAUNCH_VIEWER"

    const val ACTION_VIEW_NOTIFIED = "ACTION_VIEW_NOTIFIED"

    val ComponentPlayActivity = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaplay.PecaPlayActivity"
    )

    fun createLaunchViewer(streamUri: Uri, ch: Yp4gChannel) : Intent {
        return Intent(ACTION_LAUNCH_VIEWER).also {
            it.component = ComponentPlayActivity
            it.data = streamUri
            it.putExtra(PecaViewerIntent.EX_YP4G_CHANNEL, ch)
        }
    }
}
