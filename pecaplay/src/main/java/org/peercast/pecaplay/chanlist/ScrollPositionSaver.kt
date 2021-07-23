package org.peercast.pecaplay.chanlist

import android.os.Bundle
import android.os.Parcelable
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

//スクロール位置を保存する。
class ScrollPositionSaver(
    savedInstanceState: Bundle?,
    private val recyclerView: RecyclerView,
) {
    private val bundle = savedInstanceState?.getBundle(STATE_SCROLL_POSITIONS) ?: Bundle()

    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    storeScrollPosition()
                }
            }
        })
    }

    fun clear() {
        bundle.clear()
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(STATE_SCROLL_POSITIONS, bundle)
    }

    private val listId: String?
        get() {
            val adapter = (recyclerView.adapter as? ChannelListAdapter)
            if (adapter == null || adapter.itemCount == 0)
                return null
            return adapter.items.joinToString { it.name }
        }

    /**スクロール位置の再現*/
    fun restoreScrollPosition() {
        val id = listId ?: return
        Timber.d("restoreScrollPosition: key=$id")

        bundle.getParcelable<Parcelable>(id)?.let {
            recyclerView.layoutManager?.onRestoreInstanceState(it)
        } ?: run {
            recyclerView.layoutManager?.scrollToPosition(0)
        }
    }

    //スクロール位置の保存
    private fun storeScrollPosition() {
        val id = listId ?: return
        Timber.d("storeScrollPosition: key=$id")
        bundle.putParcelable(
            id,
            recyclerView.layoutManager?.onSaveInstanceState()
        )
    }

    companion object {
        private const val STATE_SCROLL_POSITIONS = "scroll-positions"
    }

}