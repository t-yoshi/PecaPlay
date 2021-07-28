package org.peercast.pecaplay.core.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

object PecaPlayIntent {
    const val ACTION_VIEW_NOTIFIED = "ACTION_VIEW_NOTIFIED"

    const val ACTION_MINIPLAYER = "ACTION_MINIPLAYER"

    const val EX_MINIPLAYER_ENABLED = "miniplayer-enabled" //boolean

    val ComponentPlayActivity = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaplay.PecaPlayActivity"
    )
}
