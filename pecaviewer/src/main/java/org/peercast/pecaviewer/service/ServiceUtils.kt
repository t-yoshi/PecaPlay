package org.peercast.pecaviewer.service

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.service.PlayerService.Companion.setPlayerService


internal fun Context.bindPlayerService(conn: ServiceConnection) {
    bindService(
        Intent(this, PlayerService::class.java),
        conn, Context.BIND_AUTO_CREATE
    )
}

internal fun StateFlow<PlayerService?>.bindPlayerView(view: PlayerView) {
    val viewLifecycleOwner = checkNotNull(view.findViewTreeLifecycleOwner())
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            onCompletion {
                if (viewLifecycleOwner.lifecycle.currentState >= Lifecycle.State.CREATED)
                    view.setPlayerService(null)
            }.collect {
                view.setPlayerService(it)
            }
        }
    }
}