package org.peercast.pecaplay.yp4g

import org.peercast.pecaplay.core.app.Yp4gChannel


/**詳細が空のとき、ジャンルを返す。「たつひと」*/
val Yp4gChannel.descriptionOrGenre: String
    get() {
        return description.ifEmpty { genre }
    }


typealias YpChannelSelector = (YpChannel) -> Boolean


private const val TAG = "Yp4g"



