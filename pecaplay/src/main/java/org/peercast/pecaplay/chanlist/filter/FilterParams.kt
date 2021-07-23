package org.peercast.pecaplay.chanlist.filter

import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder

abstract class FilterParams {
    /**配信中or履歴**/
    abstract val source: YpChannelSource

    /**お気に入り/ジャンル等で選別するセレクタ*/
    abstract val selector: (YpChannel) -> Boolean

    /**表示する順序*/
    abstract val displayOrder: YpDisplayOrder

    /**検索窓から*/
    abstract val searchQuery: String
}

