package org.peercast.pecaviewer.service

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import org.peercast.core.lib.notify.NotifyMessageType
import org.peercast.core.lib.rpc.ChannelInfo
import java.io.IOException
import java.util.*


class PlayerServiceEventLiveData : MutableLiveData<PlayerServiceEvent>() {

    /**
     * MutableLiveData#postValueはディスパッチ直前の値だけが送信されるので
     * データが失われることがある。本来はイベント用ではない?
     * */
    @Deprecated("")
    override fun postValue(value: PlayerServiceEvent?) {
        super.postValue(value)
    }
}


/**
 * PecaViewerServiceで発行するイベント
 * */
sealed class PlayerServiceEvent


data class PeerCastNotifyMessageEvent(
    val types: EnumSet<NotifyMessageType>, val message: String,
) : PlayerServiceEvent()

data class PlayerBufferingEvent(val percentage: Int = 0) : PlayerServiceEvent()
data class PlayerLoadStartEvent(val url: Uri) : PlayerServiceEvent()
data class PlayerLoadErrorEvent(val url: Uri, val e: IOException) : PlayerServiceEvent()
data class PlayerErrorEvent(val errorType: String, val e: Exception) : PlayerServiceEvent()
