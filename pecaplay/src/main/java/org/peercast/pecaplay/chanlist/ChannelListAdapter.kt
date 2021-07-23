package org.peercast.pecaplay.chanlist

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaplay.prefs.PecaPlayPreferences
import kotlin.properties.Delegates

class ChannelListAdapter(
    private val prefs: PecaPlayPreferences,
    private val eventListener: ListItemEventListener,
) : RecyclerView.Adapter<ListItemViewHolder>() {

    var items by Delegates.observable(emptyList<ListItemViewModel>()){ _, old, new->
        if (old != new)
            notifyDataSetChanged()
    }

    inline fun updateItem(position: Int, updated: (ListItemViewModel) -> ListItemViewModel) {
        items = ArrayList(items).also {
            it[position] = updated(it[position])
        }
        notifyItemChanged(position)
    }

   // override fun getItemId(position: Int): Long = items[position].hashCode() + 0L

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemViewHolder {
        //return ListItemViewHolder(parent, ChItemBinding::inflate)
        return HOLDER_FACTORIES[viewType].create(parent, eventListener)

        //return holderFactory.createViewHolder(parent, viewType).also {
        //    it.setItemEventListener(listItemEventListener)
        //}
    }

    override fun onBindViewHolder(holder: ListItemViewHolder, position: Int) {
        holder.setViewModel(items[position])
        holder.executePendingBindings()
        //holder.viewModel.let {
        //    it.model = items[position]
        //    it.notifyChange()
        //}
        //holder.executePendingBindings()

    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        val vm = items[position]
        return when  {
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