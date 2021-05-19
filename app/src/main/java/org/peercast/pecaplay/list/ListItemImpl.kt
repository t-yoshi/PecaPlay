package org.peercast.pecaplay.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.cardview.widget.CardView
import androidx.core.view.children
import androidx.databinding.Bindable
import androidx.databinding.Observable
import kotlinx.android.synthetic.main.ch_item.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.dsl.module
import org.peercast.pecaplay.BR
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.databinding.ChItemBinding
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.descriptionOrGenre
import java.text.DateFormat
import kotlin.properties.Delegates

val listItemModule = module {
    single<IListItemViewHolderFactory> {
        ListItemViewHolderFactoryImpl(get())
    }
}


private class ListItemViewHolderFactoryImpl(private val appPrefs: AppPreferences) :
    IListItemViewHolderFactory {
    override fun createViewHolder(
        parent: ViewGroup, viewType: Int
    ): BaseListItemViewHolder {
        return HOLDER_FACTORIES[viewType](parent)
    }

    override fun getViewType(model: ListItemModel): Int {
        return when {
            !model.isNg -> TYPE_DEFAULT
            appPrefs.isNgHidden -> TYPE_NG_HIDDEN
            else -> TYPE_NG
        }
    }

    companion object {
        private val HOLDER_FACTORIES = listOf<(ViewGroup)->BaseListItemViewHolder>(
            ListItemViewHolder::Default,
            ListItemViewHolder::Ng,
            ListItemViewHolder::NgHidden
        )

        const val TYPE_DEFAULT = 0
        const val TYPE_NG = 1
        const val TYPE_NG_HIDDEN = 2
    }
}

private class ListItemViewModelImpl : BaseListItemViewModel(), KoinComponent {
    private val c: Context by inject()

    override var name: String = ""
    override var listener: String = ""
    override var description: CharSequence = ""
    override var comment: CharSequence = ""
    override var age: CharSequence = ""

    override var isEnabled = true
    override var isStarChecked = false

    override var isStarEnabled = false
    override var isNewlyVisible = false
    override var isAgeVisible = false
    override var isNewlyChecked = false
    override val isNotificatedVisible = false

    private lateinit var model_: ListItemModel

    @set:SuppressLint("StringFormatMatches")
    override var model: ListItemModel
        get() = model_
        set(value) {
            model_ = value

            val ch = value.ch
            name = ch.yp4g.name
            listener = when {
                ch is YpHistoryChannel -> ""
                else -> ch.yp4g.let {
                    c.getString(R.string.ch_listeners_fmt, it.listeners, it.relays, it.type)
                }
            }
            comment = SpannableStringBuilder().also {
                it.append(ch.yp4g.comment)
                //末尾にnbspを加えてlistener表示と重ならないようにする
                it.append(
                    "\u00A0".repeat(listener.length),
                    SPAN_MONOSPACE, 0
                )
            }
            description = SpannableStringBuilder().also {
                val playing = if (ch is YpHistoryChannel && !ch.isEnabled)
                    "Played:   "
                else
                    "Playing:  "
                it.append(playing, SPAN_ITALIC, 0)
                it.append(ch.yp4g.descriptionOrGenre)
            }
            age = if (ch is YpHistoryChannel) {
                DATE_FORMAT.format(ch.lastPlay)
            } else {
                ch.yp4g.age
            }

            isEnabled = ch.isEnabled
            isAgeVisible = !ch.isEmptyId
            isNewlyVisible = !ch.isEmptyId && ch is YpLiveChannel && ch.numLoaded <= 2
            isNewlyChecked = ch is YpLiveChannel && ch.numLoaded == 1

            isStarChecked = value.star != null
            isStarEnabled = !ch.isEmptyId

            notifyPropertyChanged(BR._all)
        }

    companion object {
        private val SPAN_ITALIC = StyleSpan(Typeface.ITALIC)
        private val SPAN_MONOSPACE = TypefaceSpan("monospace")
        private val DATE_FORMAT = DateFormat.getDateInstance()
    }

}

private sealed class ListItemViewHolder(itemView: View) :
    BaseListItemViewHolder(itemView) {

    private var eventListener: IListItemEventListener? = null

    override val viewModel = ListItemViewModelImpl()

    init {
        //コンテキストメニューを出すにはfalseを返す。
        itemView.setOnLongClickListener {
            eventListener?.onItemLongClick(viewModel.model, adapterPosition) ?: false
        }
        itemView.setOnClickListener {
            eventListener?.onItemClick(viewModel.model, adapterPosition)
        }
    }

    @CallSuper
    override fun setItemEventListener(listener: IListItemEventListener?) {
        eventListener = listener
    }

    class Default(parent: ViewGroup) : ListItemViewHolder(
        inflateCardView(
            parent
        )
    ) {
        private val binding: ChItemBinding

        init {
            val inflater = LayoutInflater.from(parent.context)
            binding = ChItemBinding.inflate(inflater, itemView as CardView, true)
            binding.viewModel = viewModel
        }

        override fun setItemEventListener(listener: IListItemEventListener?) {
            super.setItemEventListener(listener)
            binding.itemEventListener = listener
        }

        override fun executePendingBindings() {
            binding.executePendingBindings()
        }
    }

    class Ng(parent: ViewGroup) : ListItemViewHolder(
        inflateCardView(
            parent
        )
    ) {
        init {
            val inflater = LayoutInflater.from(parent.context)
            inflater.inflate(R.layout.ch_ng, itemView as CardView, true)
        }
    }

    class NgHidden(parent: ViewGroup) : ListItemViewHolder(FrameLayout(parent.context))


    companion object {
        private fun inflateCardView(parent: ViewGroup): View {
            val inflater = LayoutInflater.from(parent.context)
            return inflater.inflate(R.layout.ch_root, parent, false)
        }
    }
}