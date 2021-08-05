package org.peercast.pecaplay.core.app

import android.content.ComponentName

object PecaViewerIntent {
    /**(Yp4gChannel)*/
    const val EX_YP4G_CHANNEL = "yp4g_channel"

    /**通知バーの再生ボタン*/
    const val ACTION_PLAY = "org.peercast.pecaviewer.ACTION_PLAY"

    /**通知バーの一時停止ボタン*/
    const val ACTION_PAUSE = "org.peercast.pecaviewer.ACTION_PAUSE"

    /**通知バーの停止ボタン*/
    const val ACTION_STOP = "org.peercast.pecaviewer.ACTION_STOP"

    internal val COMPONENT_NAME = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaviewer.PecaViewerActivity"
    )
}