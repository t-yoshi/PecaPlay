package org.peercast.pecaviewer.chat.adapter

import androidx.annotation.MainThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class ItemsHolder<T> {

    private inner class ItemsHolderCallback(
        val newItems: List<T>,
        private val oldItems: List<T>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return areContentsTheSame(oldItems[oldItemPosition], newItems[newItemPosition])
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            return getChangePayload(oldItems[oldItemPosition], newItems[newItemPosition])
        }
    }

    private var callback = ItemsHolderCallback(emptyList(), emptyList())

    protected open fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem == newItem
    }

    protected open fun getChangePayload(oldItem: T, newItem: T): Any? = null

    @MainThread
    fun update(items: List<T>, adapter: RecyclerView.Adapter<*>, detectMoves: Boolean = false) {
        callback = ItemsHolderCallback(items, callback.newItems)
        DiffUtil.calculateDiff(callback, detectMoves).dispatchUpdatesTo(adapter)
    }

    suspend fun asyncUpdate(
        items: List<T>,
        adapter: RecyclerView.Adapter<*>,
        detectMoves: Boolean = false
    ) {
        val cb = ItemsHolderCallback(items, callback.newItems)
        val res = withContext(Dispatchers.Default) {
            DiffUtil.calculateDiff(cb, detectMoves)
        }
        withContext(Dispatchers.Main) {
            callback = cb
            res.dispatchUpdatesTo(adapter)
        }
    }

    @MainThread
    fun clear(adapter: RecyclerView.Adapter<*>) {
        callback = ItemsHolderCallback(emptyList(), emptyList())
        adapter.notifyDataSetChanged()
    }

    operator fun get(index: Int) = callback.newItems[index]

    val size: Int get() = callback.newItems.size
}