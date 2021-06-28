package org.peercast.pecaplay.list

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaplay.LoadingWorker
import org.peercast.pecaplay.LoadingWorkerLiveData
import org.peercast.pecaplay.PecaPlayViewModel
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.util.LiveDataUtils
import org.peercast.pecaplay.view.MenuableRecyclerView
import timber.log.Timber

@Suppress("unused")
class YpChannelFragment : Fragment() {

    private val favoriteDao
        get() = get<AppRoomDatabase>().favoriteDao
    private val viewModel: PecaPlayViewModel by sharedViewModel()
    private val adapter = ListAdapter()

    private lateinit var vRecycler: RecyclerView
    private lateinit var vSwipeRefresh: SwipeRefreshLayout

    //スクロール位置を保存する。
    private val scrollPositions = Bundle()

    private val queryTag: String get() = viewModel.run { "$source#$selector" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getBundle(STATE_SCROLL_POSITIONS)?.let {
            scrollPositions.putAll(it)
        }

        LiveDataUtils.combineLatest(
            viewModel.viewLiveData,
            favoriteDao.query()
        ) { channels, favorites ->
            val (favNg, favo) = favorites.partition { it.flags.isNG }
            channels.map { ch ->
                val star = favo.firstOrNull { it.isStar && it.matches(ch) }
                val isNg = star == null && favNg.any { it.matches(ch) }
                val isNotification = favo.filter { it.flags.isNotification }.any { it.matches(ch) }
                ListItemModel(ch, star, isNg, isNotification)
            }
        }.observe(this) {
            adapter.items = it
            //Timber.d("-> $it")
            adapter.notifyDataSetChanged()
            restoreScrollPosition()
        }

        get<LoadingWorkerLiveData>().observe(this) { ev ->
            //Timber.d("ev=$ev")
            when (ev) {
                is LoadingWorker.Event.OnStart -> {
                    vSwipeRefresh.isRefreshing = true
                    scrollPositions.clear()
                }
                is LoadingWorker.Event.OnFinished -> {
                    vSwipeRefresh.isRefreshing = false
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.yp_channel_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vRecycler = view.findViewById(R.id.vRecycler)
        vSwipeRefresh = view.findViewById(R.id.vSwipeRefresh)

        registerForContextMenu(vRecycler)

        vRecycler.let { v ->
            v.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE)
                        storeScrollPosition()
                }
            })
            v.layoutManager = LinearLayoutManager(v.context)
            v.adapter = adapter
            (v.itemAnimator as DefaultItemAnimator?)?.let { a ->
                a.moveDuration = 0
                a.changeDuration = 10
                a.addDuration = 0
                a.removeDuration = 0
            }
            //it.itemAnimator = null
        }

        vSwipeRefresh.setOnRefreshListener {
            viewModel.presenter.startLoading()
        }
    }

    //スクロール位置の保存
    private fun storeScrollPosition() {
        scrollPositions.putParcelable(
            queryTag,
            vRecycler.layoutManager?.onSaveInstanceState()
        )
    }

    //スクロール位置の再現
    private fun restoreScrollPosition() {
        if (adapter.itemCount == 0)
            return
        scrollPositions.getParcelable<Parcelable>(queryTag)?.let {
            vRecycler.layoutManager?.onRestoreInstanceState(it)
        } ?: run {
            vRecycler.layoutManager?.scrollToPosition(0)
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
    ) {
        val info = menuInfo as MenuableRecyclerView.ContextMenuInfo? ?: return
        val item = adapter.items[info.position]
        val ch = item.ch

        menu.setHeaderTitle(item.ch.yp4g.name)
        ContextMenuBuilder(menu).run {
            addNotificationItem(item)
            addItem(R.string.contact, ch.yp4g.url)
            addItem(R.string.yp4g_chat, ch.chatUrl(), !ch.isEmptyId)
            addItem(R.string.yp4g_statistics, ch.statisticsUrl(), !ch.isEmptyId)
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(STATE_SCROLL_POSITIONS, scrollPositions)
    }

    private inner class ContextMenuBuilder(val menu: ContextMenu) {
        fun addNotificationItem(item: ListItemModel) {
            val star = item.star
            if (star == null) {
                menu.add(R.string.notification_add).isEnabled = false
                return
            }

            when (item.isNotification) {
                true -> {
                    menu.add(R.string.notification_delete)
                        .setOnMenuItemClickListener { mi ->
                            lifecycleScope.launchWhenResumed {
                                favoriteDao.update(star.copyFlags { it.copy(isNotification = false) })
                            }
                            false
                        }
                }
                false -> {
                    menu.add(R.string.notification_add)
                        .setOnMenuItemClickListener { mi ->
                            lifecycleScope.launchWhenResumed {
                                favoriteDao.update(star.copyFlags { it.copy(isNotification = true) })
                            }
                            false
                        }
                }
            }
        }

        fun addItem(@StringRes title: Int, url: Uri, enabled: Boolean = true) {
            val item = menu.add(title)
            if (url.scheme?.matches("^https?".toRegex()) == true) {
                item.intent = Intent(Intent.ACTION_VIEW, url).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                item.isEnabled = enabled
            } else {
                item.isEnabled = false
            }
        }
    }


    private inner class ListAdapter : RecyclerView.Adapter<BaseListItemViewHolder>() {
        private val holderFactory: IListItemViewHolderFactory by inject()

        init {
            setHasStableIds(true)
        }

        var items = emptyList<ListItemModel>()

        inline fun updateItem(position: Int, updated: (ListItemModel) -> ListItemModel) {
            items = ArrayList(items).also {
                it[position] = updated(it[position])
            }
            notifyItemChanged(position)
        }

        override fun getItemId(position: Int): Long = items[position].hashCode() + 0L

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseListItemViewHolder {
            return holderFactory.createViewHolder(parent, viewType).also {
                it.setItemEventListener(listItemEventListener)
            }
        }

        override fun onBindViewHolder(holder: BaseListItemViewHolder, position: Int) {
            holder.viewModel.let {
                it.model = items[position]
                it.notifyChange()
            }
            holder.executePendingBindings()
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun getItemViewType(position: Int): Int {
            return holderFactory.getViewType(items[position])
        }
    }

    private val listItemEventListener = object : IListItemEventListener {
        override fun onStarClicked(m: ListItemModel, isChecked: Boolean) {
            Timber.d("onStarClicked(%s, %s)", m, isChecked)
            lifecycleScope.launchWhenResumed {
                m.star?.let {
                    favoriteDao.remove(it)
                } ?: Favorite.Star(m.ch).let {
                    favoriteDao.add(it)
                }
            }
        }

        override fun onItemClick(m: ListItemModel, position: Int) {
            if (m.ch.isEnabled && !m.isNg) {
                viewModel.presenter.startPlay(m.ch) {
                    startActivity(it)
                }
            }
        }

        override fun onItemLongClick(m: ListItemModel, position: Int): Boolean {
            if (m.isNg) {
                //対象NGを一時的に解除
                adapter.updateItem(position) {
                    it.copy(isNg = false)
                }
                adapter.notifyItemChanged(position)
                return true
            }
            return false
        }
    }

    companion object {
        private const val TAG = "YpChannelFragment"
        private const val STATE_SCROLL_POSITIONS = "$TAG#scroll-positions"
    }

}

