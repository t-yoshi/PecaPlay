package org.peercast.pecaviewer

import android.content.Context
import android.content.Intent
import android.net.Uri

object PecaViewerIntent {
    const val ACTION_LAUNCH_VIEWER = "ACTION_LAUNCH_VIEWER"

    const val EX_TITLE = "title" // CharSequence
    const val EX_COMMENT = "comment" //CharSequence
    const val EX_CONTACT = "contact" //String

    fun create(
        c: Context, streamUri: Uri,
        title: CharSequence, comment: CharSequence,
        contact: Uri,
    ): Intent {
        return Intent(c, PecaViewerActivity::class.java).also {
            it.data = streamUri
            it.action = ACTION_LAUNCH_VIEWER
            it.putExtra(EX_TITLE, title)
            it.putExtra(EX_COMMENT, comment)
            it.putExtra(EX_CONTACT, contact.toString())
        }
    }

}