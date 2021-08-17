package org.peercast.pecaplay.chanlist.filter

data class TaggedList<T>(
    val tag: String,
    private val list: List<T>,
) : List<T> by list