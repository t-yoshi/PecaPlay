package org.peercast.pecaplay.chanlist

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaplay.prefs.AppPreferences
import kotlin.properties.Delegates

class ChannelListAdapter(
    private val prefs: AppPreferences,
    private val eventListener: ListItemEventListener,
) : RecyclerView.Adapter<ListItemViewHolder>() {

    var items by Delegates.observable(emptyList<ListItemViewModel>()) { _, old, new ->
        if (old != new)
            notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemViewHolder {
        return HOLDER_FACTORIES[viewType].create(parent, eventListener)
    }

    override fun onBindViewHolder(holder: ListItemViewHolder, position: Int) {
        holder.setViewModel(items[position])
        holder.executePendingBindings()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        val vm = items[position]
        return when {
            !vm.isNg -> TYPE_DEFAULT
            prefs.isNgHidden -> TYPE_NG_HIDDEN
            else -> TYPE_NG
        }
    }

    companion object {
        private val HOLDER_FACTORIES = listOf(
            ListItemViewHolder.Factory.Default,
            ListItemViewHolder.Factory.Ng,
            ListItemViewHolder.Factory.NgHidden
        )

        const val TYPE_DEFAULT = 0
        const val TYPE_NG = 1
        const val TYPE_NG_HIDDEN = 2
    }
}