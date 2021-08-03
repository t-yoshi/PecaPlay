package org.peercast.pecaplay.core.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

object PecaViewerIntent {
    /**(Yp4gChannel)*/
    const val EX_YP4G_CHANNEL = "yp4g_channel"

    /**(Bitmap)*/
    const val EX_THUMBNAIL = "thumbnail"

    const val EX_LAUNCHED_FROM = "pevaviewer-launched-from"

//    fun create(streamUri: Uri, ch: Yp4gChannel): Intent {
//        return Intent().also {
//            it.data = streamUri
//            it.component = COMPONENT_NAME
//            it.putExtra(EX_YP4G_CHANNEL, ch)
//        }
//    }

    internal val COMPONENT_NAME = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaviewer.PecaViewerActivity"
    )
}