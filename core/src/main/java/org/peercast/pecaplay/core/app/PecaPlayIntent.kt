package org.peercast.pecaplay.core.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

object PecaPlayIntent {
    const val ACTION_VIEW_NOTIFIED = "ACTION_VIEW_NOTIFIED"

    val ComponentPlayActivity = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaplay.PecaPlayActivity"
    )
}
