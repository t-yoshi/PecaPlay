package org.peercast.pecaviewer.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.chat.net.IThreadInfo
import org.peercast.pecaviewer.databinding.BbsThreadItemBinding

class ThreadAdapter : RecyclerView.Adapter<ThreadAdapter.ViewHolder>() {
    var items = emptyList<IThreadInfo>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var onSelectThread: (IThreadInfo) -> Unit = {}

    var selected: IThreadInfo? = null
        set(value) {
            if (field == value)
                return
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(val binding: BbsThreadItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val viewModel = ThreadViewModel()

        init {
            binding.viewModel = viewModel
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = BbsThreadItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = items[position]
        holder.itemView.setOnClickListener {
            selected = thread
            onSelectThread(thread)
            notifyDataSetChanged()
        }
        holder.viewModel.setThreadInfo(thread, position, thread == selected)
        holder.binding.executePendingBindings()
    }

}
