package org.peercast.pecaplay.core.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

object PecaViewerIntent {
    val ComponentPecaViewer = ComponentName(
        "org.peercast.pecaplay",
        "org.peercast.pecaviewer.PecaViewerActivity"
    )

    const val EX_YP4G_CHANNEL = "yp4g_channel" //Yp4gChannel

    fun create(streamUri: Uri, ch: Yp4gChannel): Intent {
        return Intent().also {
            it.data = streamUri
            it.component = ComponentPecaViewer
            it.putExtra(EX_YP4G_CHANNEL, ch)
        }
    }

}