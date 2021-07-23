package org.peercast.pecaplay.chanlist

interface ListItemEventListener {
    fun onStarClicked(m: ListItemViewModel, isChecked: Boolean, position: Int)

    fun onItemClick(m: ListItemViewModel, position: Int)

    /**
     * @return イベントを消費したか。コンテキストメニューを出す必要があるならfalseを返すこと。
     * */
    fun onItemLongClick(m: ListItemViewModel, position: Int): Boolean
}