package org.peercast.pecaviewer.chat.adapter

import android.annotation.SuppressLint
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.BR
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.ChatFragment
import org.peercast.pecaviewer.chat.net.IMessage
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailView
import org.peercast.pecaviewer.databinding.BbsMessageItemBasicBinding
import org.peercast.pecaviewer.databinding.BbsMessageItemSimpleBinding
import timber.log.Timber
import kotlin.properties.Delegates


@SuppressLint("NotifyDataSetChanged")
class MessageAdapter(private val fragment: ChatFragment) :
    RecyclerView.Adapter<MessageAdapter.ViewHolder>(),
    PopupSpan.SupportAdapter {

    var messages by Delegates.observable(emptyList<IMessage>()) { _, old, new ->
        if (old.isEmpty() || new.isEmpty() || old.size > new.size || old != new.take(old.size)) {
            lastMessageCount = -1
        } else {
            lastMessageCount = old.size
        }
        notifyDataSetChanged()
    }

    /**簡易表示、または詳細表示。*/
    var viewType by Delegates.observable(SIMPLE) { _, old, new ->
        require(new in arrayOf(SIMPLE, BASIC)) { "not support viewType: $new" }
        if (new != old)
            notifyDataSetChanged()
    }

    private var lastMessageCount = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(
            DATA_BINDING_INFLATES[viewType](inflater, parent, false)
        )
    }

    inner class ViewHolder(val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
        val viewModel = MessageViewModel()
        private val vBody: TextView? = itemView.findViewById(R.id.vBody)
        private val vThumbnail: ThumbnailView? = itemView.findViewById(R.id.vThumbnail)

        init {
            binding.lifecycleOwner = fragment
            vThumbnail?.let {
                ViewTreeLifecycleOwner.set(it, fragment)
            }

            if (!binding.setVariable(BR.viewModel, viewModel))
                throw RuntimeException("Not defined viewModel in layout.")
            vBody?.run {
                movementMethod = LinkMovementMethod.getInstance()
                //長押しでテキスト選択可能にする
                setOnLongClickListener {
                    setTextIsSelectable(true)
                    false
                }
            }
            vThumbnail?.let { v ->
                v.eventListener = fragment
                fragment.lifecycleScope.launchWhenCreated {
                    viewModel.thumbnails.collect {
                        v.adapter.urls = it
                    }
                }
            }
        }

        fun bind(m: IMessage, position: Int) {
            vBody?.setTextIsSelectable(false)
            viewModel.setMessage(m, binding is BbsMessageItemSimpleBinding)
            viewModel.isNew.value = position >= lastMessageCount && lastMessageCount != -1
            binding.executePendingBindings()
        }
    }

    override fun createViewForPopupWindow(resNumber: Int, parent: ViewGroup): View? {
        val m = messages.lastOrNull { it.number == resNumber }
        if (m == null) {
            Timber.w("#$resNumber is not found.")
            return null
        }

        val vh = onCreateViewHolder(parent, viewType)
        vh.viewModel.setMessage(m)
        return vh.itemView
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position], position)
    }

    companion object {
        /**簡易表示*/
        const val SIMPLE = 0

        /**詳細表示*/
        const val BASIC = 1

        private val DATA_BINDING_INFLATES = listOf<ViewDataBinding_inflate>(
            BbsMessageItemSimpleBinding::inflate,
            BbsMessageItemBasicBinding::inflate,
        )
    }
}

private typealias ViewDataBinding_inflate = (LayoutInflater, ViewGroup, Boolean) -> ViewDataBinding

