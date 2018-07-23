package org.peercast.pecaplay

import org.greenrobot.eventbus.EventBus

val EVENT_BUS : EventBus get() = EventBus.getDefault()

/**EventBusイベントの基底*/
interface AppEvent



/**PeerCastServiceにbindした (sticky)*/
class OnPeerCastStart(
        /**稼働中のポート (外部で動作するPeerCastを使用する場合は-1)*/
        val localPort: Int
) : AppEvent
