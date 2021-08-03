package org.peercast.pecaplay.chanlist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaplay.AppViewModel
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.core.app.chatUrl
import org.peercast.pecaplay.core.app.statisticsUrl
import org.peercast.pecaplay.view.MenuableRecyclerView
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import timber.log.Timber

@Suppress("unused")
class YpChannelFragment : Fragment() {

    private val favoriteDao
        get() = get<AppRoomDatabase>().favoriteDao
    private val viewModel by sharedViewModel<AppViewModel>()
    private lateinit var listAdapter: ChannelListAdapter

    //スクロール位置を保存する。
    private lateinit var scrollPositionSaver: ScrollPositionSaver

    private lateinit var vRecycler: RecyclerView
    private lateinit var vSwipeRefresh: SwipeRefreshLayout
    private val loadingEvent by inject<LoadingEventFlow>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        listAdapter = ChannelListAdapter(get(), listItemEventListener)

        val viewModelsFlow = combine(
            viewModel.channelFilter.filteredChannel,
            favoriteDao.query()
        ) { channels, favorites ->
            val (favNg, favo) = favorites.partition { it.flags.isNG }
            channels.map { ch ->
                val star = favo.firstOrNull { it.isStar && it.matches(ch) }
                val isNg = star == null && favNg.any { it.matches(ch) }
                val isNotification = favo.filter { it.flags.isNotification }.any { it.matches(ch) }
                ListItemViewModel(requireContext(), ch, star, isNg, isNotification)
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModelsFlow.collect {
                listAdapter.items = it
                //Timber.d("-> $it")
                scrollPositionSaver.restoreScrollPosition()
            }
        }

        lifecycleScope.launchWhenResumed {
            loadingEvent.collect { ev ->
                //Timber.d("ev=$ev")
                when (ev) {
                    is LoadingEvent.OnStart -> {
                        vSwipeRefresh.isRefreshing = true
                        scrollPositionSaver.clear()
                    }
                    else -> {
                        vSwipeRefresh.isRefreshing = false
                    }
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
        scrollPositionSaver = ScrollPositionSaver(savedInstanceState, vRecycler)

        registerForContextMenu(vRecycler)

        with(vRecycler) {
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter
            (itemAnimator as DefaultItemAnimator?)?.let { a ->
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


    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
    ) {
        val info = menuInfo as MenuableRecyclerView.ContextMenuInfo? ?: return
        val item = listAdapter.items[info.position]
        val ch = item.ch

        menu.setHeaderTitle(item.ch.name)
        ContextMenuBuilder(menu).run {
            addNotificationItem(item)
            addItem(R.string.contact, ch.url)
            addItem(R.string.yp4g_chat, ch.chatUrl(), !ch.isEmptyId)
            addItem(R.string.yp4g_statistics, ch.statisticsUrl(), !ch.isEmptyId)
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        scrollPositionSaver.onSaveInstanceState(outState)
    }

    private inner class ContextMenuBuilder(val menu: ContextMenu) {
        fun addNotificationItem(item: ListItemViewModel) {
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


    private val listItemEventListener = object : ListItemEventListener {
        override fun onStarClicked(m: ListItemViewModel, isChecked: Boolean, position: Int) {
            Timber.d("onStarClicked(%s, %s)", m, isChecked)
            lifecycleScope.launch {
                m.star?.let {
                    favoriteDao.remove(it)
                } ?: Favorite.Star(m.ch).let {
                    favoriteDao.add(it)
                }
            }
        }

        override fun onItemClick(m: ListItemViewModel, position: Int) {
            if (m.ch.isPlayable && !m.isNg) {
                viewModel.presenter.startPlay(requireActivity(), m.ch)
            }
        }

        override fun onItemLongClick(m: ListItemViewModel, position: Int): Boolean {
            if (m.isNg) {
                //対象NGを一時的に解除
                m.isNg = false
                listAdapter.notifyItemChanged(position)
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

