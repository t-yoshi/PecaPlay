package org.peercast.pecaplay.core.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

object PecaPlayIntent {
    @Deprecated("")
    const val ACTION_VIEW_NOTIFIED = "ACTION_VIEW_NOTIFIED"

    const val EX_SELECT_NOTIFIED = "select-notified" //boolean

    internal val COMPONENT_NAME = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaplay.PecaPlayActivity"
    )


}
