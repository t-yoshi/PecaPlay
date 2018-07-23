package org.peercast.pecaplay

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.StringRes
import android.support.v4.app.Fragment
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.*
import android.widget.Toast
import kotlinx.android.synthetic.main.yp_index_fragment.*
import kotlinx.coroutines.experimental.launch
import org.greenrobot.eventbus.Subscribe
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YpHistory
import org.peercast.pecaplay.app.YpIndex
import org.peercast.pecaplay.databinding.ChItemBinding
import org.peercast.pecaplay.view.CustomRecyclerView
import org.peercast.pecaplay.yp4g.Yp4gChannel
import timber.log.Timber
import java.text.DateFormat


class YpIndexFragment : Fragment() {

    private lateinit var viewModel: PecaViewModel
    private val adapter get() = vRecycler.adapter as Adapter

    private val favoritePresenters = HashMap<Yp4gChannel, FavoritePresenter>()
    private fun HashMap<Yp4gChannel, FavoritePresenter>.getOrCreate(ch: Yp4gChannel) =
            getOrPut(ch) { FavoritePresenter(ch) }

    private var favorites = emptyList<Favorite>()
    private var ng = emptyList<Favorite>()
    private var isNgHidden = false

    //スクロール位置を保存する。
    private lateinit var scrollPositions: Bundle

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        scrollPositions =
                savedInstanceState?.getParcelable(STATE_KEY_SCROLL_POSITIONS) as? Bundle ?: Bundle()

        viewModel = PecaViewModel.get(activity!!)

        viewModel.ypIndexLiveData.observe(this, Observer {
            adapter.channels = it ?: emptyList()
            favoritePresenters.clear()
            restoreScrollPosition()
        })

        viewModel.database.getFavoriteDao().getEnabled().observe(this, Observer {
            it?.partition { it.flags.isNG }?.let {
                ng = it.first
                favorites = it.second
            }
            favoritePresenters.clear()
            adapter.notifyDataSetChanged()
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.yp_index_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        registerForContextMenu(vRecycler)
        vRecycler.let {
            it.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE)
                        storeScrollPosition()
                }
            })
            it.layoutManager = LinearLayoutManager(it.context)
            it.adapter = Adapter()
        }
        vSwipeRefresh.setOnRefreshListener {
            viewModel.startLoading()
        }
    }


    //スクロール位置の保存
    private fun storeScrollPosition() {
        scrollPositions.putParcelable(viewModel.category,
                vRecycler.layoutManager.onSaveInstanceState()
        )
    }

    //スクロール位置の再現
    private fun restoreScrollPosition() {
        if (adapter.itemCount == 0)
            return
        scrollPositions.getParcelable<Parcelable>(viewModel.category)?.let {
            vRecycler.layoutManager.onRestoreInstanceState(it)
        } ?: kotlin.run {
            vRecycler.layoutManager.scrollToPosition(0)
        }
    }

    override fun onResume() {
        super.onResume()
        isNgHidden = viewModel.appPrefs.isNgHidden
        EVENT_BUS.register(this)
    }

    override fun onPause() {
        super.onPause()
        EVENT_BUS.unregister(this)
        vSwipeRefresh.isRefreshing = false
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val info = menuInfo as? CustomRecyclerView.ContextMenuInfo ?: return
        val ch = adapter.getItem(info.position)

        ContextMenuPresenter(menu, ch).run {
            addFavoriteItem(favoritePresenters.getOrCreate(ch))
            addItem(R.string.contact, ch.yp4g.url)
            addItem(R.string.yp4g_chat, ch.chatUrl(), !ch.isEmptyId)
            addItem(R.string.yp4g_statistics, ch.statisticsUrl(), !ch.isEmptyId)
        }
    }

    @Subscribe
    fun onLoadStart(e: PecaPlayService.OnLoadStarted) {
        //Timber.d("onLoadStart")
        vSwipeRefresh.isRefreshing = true
        scrollPositions.clear()
    }

    @Subscribe
    fun onLoadFinished(e: PecaPlayService.OnLoadFinished) {
        //Timber.d("onLoadFinished")
        vSwipeRefresh.isRefreshing = false
    }

    @Subscribe
    fun onLoadExceptionOccurred(e: PecaPlayService.OnLoadException) {
        //Timber.e("onLoadExceptionOccurred ${e.exceptions}")
        e.exceptions.entries.forEach {
            val msg = "<font color=red>${it.key.name}: ${ExceptionUtils.localizedSystemMessage(it.value)}"
            Toast.makeText(context, Html.fromHtml(msg), Toast.LENGTH_LONG).show()
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        vRecycler?.let {
            outState.putParcelable(STATE_KEY_SCROLL_POSITIONS, scrollPositions)
        }
    }


    inner class FavoritePresenter(val ch: Yp4gChannel) {
        private val matched = favorites.filter { it.matches(ch) }
        val starred = matched.firstOrNull { it.isStarred }
        val isNotificated = matched.firstOrNull { it.flags.isNotification } != null

        val isStarred get() = starred != null

        fun addStarred() {
            val favoriteDao = viewModel.database.getFavoriteDao()
            launch {
                favoriteDao.add(Favorite.createStarred(ch))
            }
        }

        fun removeStarred() {
            val favoriteDao = viewModel.database.getFavoriteDao()
            launch {
                favoriteDao.remove(starred!!)
            }
        }

        fun starred(mark: Boolean) {
            if (mark == isStarred)
                return

            Timber.d("starred($mark)")
            val favoriteDao = viewModel.database.getFavoriteDao()
            launch {
                if (mark) {
                    favoriteDao.add(Favorite.createStarred(ch))
                } else {
                    favoriteDao.remove(starred!!)
                }
            }
        }

        fun changeNotificationFlag(flagOn: Boolean) {
            val favoriteDao = viewModel.database.getFavoriteDao()
            val f = starred!!.let {
                it.copyFlags { it.copy(isNotification = flagOn) }
            }
            launch {
                favoriteDao.update(f)
            }
        }
    }

    private class ContextMenuPresenter(val menu: ContextMenu, val ch: Yp4gChannel) {
        init {
            menu.setHeaderTitle(ch.yp4g.name)
        }

        fun addItem(@StringRes label: Int, url: Uri, enabled: Boolean = true) {
            val item = menu.add(label)
            val proto = url.scheme
            if (proto != null && proto.matches("^https?".toRegex())) {
                item.intent = Intent(Intent.ACTION_VIEW, url).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                item.isEnabled = enabled
            } else {
                item.isEnabled = false
            }
        }

        fun addFavoriteItem(favoritePresenter: FavoritePresenter) {
            val item: MenuItem
            if (favoritePresenter.isStarred) {
                item = menu.add(R.string.star_delete)
                item.setOnMenuItemClickListener {
                    favoritePresenter.removeStarred()
                    true
                }

                val title: Int
                if (favoritePresenter.starred!!.flags.isNotification) {
                    menu.add(R.string.notification_delete).setOnMenuItemClickListener {
                        favoritePresenter.changeNotificationFlag(false)
                        true
                    }
                } else {
                    menu.add(R.string.notification_add).setOnMenuItemClickListener {
                        favoritePresenter.changeNotificationFlag(true)
                        true
                    }
                }

            } else {
                item = menu.add(R.string.star_add)
                item.setOnMenuItemClickListener {
                    favoritePresenter.addStarred()
                    true
                }
            }
            item.isEnabled = !ch.isEmptyId
        }
    }


    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        var channels = emptyList<Yp4gChannel>()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        fun getItem(position: Int) = channels[position]

        override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

        val holderFactories = arrayOf<(ViewGroup) -> ViewHolder>(
                ::DefaultViewHolder,
                ::NgViewHolder,
                ::NgHiddenViewHolder
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return holderFactories[viewType](parent)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item)
        }

        override fun getItemCount(): Int {
            return channels.size
        }



        override fun getItemViewType(position: Int): Int {
            val ch = getItem(position)
            val isNg = ng.any { it.matches(ch) }
            return when {
                !isNg -> TYPE_DEFAULT
                isNgHidden -> TYPE_NG_HIDDEN
                else -> TYPE_NG
            }
        }
    }


    companion object {
        private const val TAG = "YpIndexFragment"

        const val TYPE_DEFAULT = 0
        const val TYPE_NG = 1
        const val TYPE_NG_HIDDEN = 2

        private const val STATE_KEY_SCROLL_POSITIONS = "$TAG#scroll-positions"

    }

    private abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            //コンテキストメニューを出すために
            itemView.setOnLongClickListener { false }
        }

        abstract fun bind(ch: Yp4gChannel)
    }

    private inner class DefaultViewHolder(parent: ViewGroup) : ViewHolder(inflateCardView(parent)) {
        private val binding = kotlin.run {
            val inflater = LayoutInflater.from(parent.context)
            //val ch_root = inflater.inflate(R.layout.ch_root, parent, false)
            ChItemBinding.inflate(inflater, itemView as CardView, true)
        }

        init {
            itemView.setOnClickListener {
                val ch = adapter.getItem(adapterPosition)
                if (ch.isEnabled) {
                    viewModel.startPlay(activity, ch)
                }
            }
        }

        override fun bind(ch: Yp4gChannel) {
            childrenEnabled(ch.isEnabled || ch.isEmptyId)

            binding.viewModel = ItemViewModel(context, ch)
            binding.favoPresenter = favoritePresenters.getOrCreate(ch)
            binding.executePendingBindings()
        }

        private fun childrenEnabled(b: Boolean) {
            ViewUtils.children(itemView as ViewGroup, true).forEach { it.isEnabled = b }
        }
    }

    //NG。ただし、ロングクリックすると一時的に解除。
    private inner class NgViewHolder(parent: ViewGroup)
        : ViewHolder(inflateCardView(parent)) {
        init {
            val inflater = LayoutInflater.from(parent.context)
            inflater.inflate(R.layout.ch_ng, itemView as CardView, true)

            //対象NGを一時的に解除
            itemView.setOnLongClickListener {
                val ch = adapter.getItem(adapterPosition)
                ng = ng.filter { !it.matches(ch) }
                adapter.notifyDataSetChanged()
                true
            }
        }

        override fun bind(ch: Yp4gChannel) {
        }
    }

    //NG。非表示
    private class NgHiddenViewHolder(parent: ViewGroup) : ViewHolder(
            View(parent.context).apply { visibility = View.GONE }) {
        override fun bind(ch: Yp4gChannel) {}
    }

    class ItemViewModel(
            private val c: Context,
            private val ch: Yp4gChannel) {

        val name = ch.yp4g.name

        @SuppressLint("StringFormatMatches")
        val listener: String = if (ch is YpHistory) {
            ""
        } else {
            ch.yp4g.let {
                c.getString(R.string.ch_listeners_fmt, it.listeners, it.relays, it.type)
            }
        }

        val comment: CharSequence = SpannableStringBuilder().also {
            it.append(ch.yp4g.comment)
            //末尾にnbspを加えてlistener表示と重ならないようにする
            it.append("\u00A0".repeat(listener.length), SPAN_MONOSPACE, 0)
        }

        val description: CharSequence = SpannableStringBuilder().also {
            it.append("Playing:  ", SPAN_ITALIC, 0)
            it.append(ch.yp4g.description)
            android.R.color.transparent
        }

        val age: CharSequence = if (ch is YpHistory) {
            DATE_FORMAT.format(ch.lastPlay)
        } else {
            ch.yp4g.age
        }

        val ageVisible = !ch.isEmptyId

        val newlyVisible = !ch.isEmptyId && ch is YpIndex && ch.numLoaded <= 2

        val newlyChecked = ch is YpIndex && ch.numLoaded == 1

        val starEnabled = !ch.isEmptyId

        companion object {
            private val SPAN_ITALIC = StyleSpan(Typeface.ITALIC)
            private val SPAN_MONOSPACE = TypefaceSpan("monospace")
            private val DATE_FORMAT = DateFormat.getDateInstance()
        }
    }

}

private fun inflateCardView(parent: ViewGroup): View {
    val inflater = LayoutInflater.from(parent.context)
    return inflater.inflate(R.layout.ch_root, parent, false)
}

