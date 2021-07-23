package org.peercast.pecaplay.chanlist.filter

import org.peercast.pecaplay.yp4g.YpChannel

typealias YpChannelPredicate = (YpChannel) -> Boolean

object YpChannelPredicates {
    val TRUE: YpChannelPredicate = { true }
    val FALSE: YpChannelPredicate = { false }
}