package org.peercast.pecaviewer.service

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection


internal fun Context.bindPlayerService(conn: ServiceConnection) {
    bindService(
        Intent(this, PlayerService::class.java),
        conn, Context.BIND_AUTO_CREATE
    )
}
