package org.peercast.pecaplay.chanlist.filter

import org.peercast.pecaplay.yp4g.YpChannel

data class FilteredChannelList(
    private val list: List<YpChannel>,
    val filterParams: FilterParams,
) : List<YpChannel> by list