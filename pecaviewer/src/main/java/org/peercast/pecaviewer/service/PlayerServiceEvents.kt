package org.peercast.pecaviewer.service

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.peercast.core.lib.notify.NotifyMessageType
import java.io.IOException
import java.util.*

class PlayerServiceEventFlow : MutableSharedFlow<PlayerServiceEvent>
by MutableSharedFlow(1, 0, BufferOverflow.DROP_OLDEST)


/**
 * PlayerServiceで発行するイベント
 * */
sealed class PlayerServiceEvent


data class PeerCastNotifyMessageEvent(
    val types: EnumSet<NotifyMessageType>, val message: String,
) : PlayerServiceEvent()

data class PlayerBufferingEvent(val percentage: Int = 0) : PlayerServiceEvent()
data class PlayerLoadStartEvent(val url: Uri) : PlayerServiceEvent()
data class PlayerLoadErrorEvent(val url: Uri, val e: IOException) : PlayerServiceEvent()
data class PlayerErrorEvent(val errorType: String, val e: Exception) : PlayerServiceEvent()
data class PlayerWhenReadyChangedEvent(
    val playWhenReady: Boolean,
    val reason: Int
) : PlayerServiceEvent()