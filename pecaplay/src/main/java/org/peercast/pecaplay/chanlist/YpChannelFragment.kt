package org.peercast.pecaplay.chanlist

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
import kotlinx.coroutines.flow.collect
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
import org.peercast.pecaplay.databinding.YpChannelFragmentBinding
import org.peercast.pecaplay.view.MenuableRecyclerView
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import timber.log.Timber

class YpChannelFragment : Fragment() {

    private val db by inject<AppRoomDatabase>()
    private val appViewModel by sharedViewModel<AppViewModel>()
    private val loadingEvent by inject<LoadingEventFlow>()
    private lateinit var binding: YpChannelFragmentBinding
    private lateinit var listAdapter: ChannelListAdapter

    //スクロール位置を保存する。
    private lateinit var scrollStates: Bundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scrollStates = savedInstanceState?.getBundle(STATE_SCROLL_STATES) ?: Bundle()
        listAdapter = ChannelListAdapter(get(), listItemEventListener)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = YpChannelFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        registerForContextMenu(binding.vRecycler)

        val listItemViewModels = appViewModel.channelFilter.toListItemViewModels(requireContext())

        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            var scrollStateKey = ""
            listItemViewModels.collect { list ->
                scrollStates.putParcelable(
                    scrollStateKey, binding.vRecycler.layoutManager?.onSaveInstanceState()
                )

                listAdapter.items = list
                scrollStateKey = list.tag

                val scrollState = scrollStates.getParcelable<Parcelable>(scrollStateKey)
                if (scrollState != null) {
                    binding.vRecycler.layoutManager?.onRestoreInstanceState(scrollState)
                } else if (list.isNotEmpty()) {
                    binding.vRecycler.layoutManager?.scrollToPosition(0)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            loadingEvent.collect { ev ->
                //Timber.d("ev=$ev")
                when (ev) {
                    is LoadingEvent.OnStart -> {
                        binding.vSwipeRefresh.isRefreshing = true
                        scrollStates.clear()
                    }
                    else -> {
                        binding.vSwipeRefresh.isRefreshing = false
                    }
                }
            }
        }

        with(binding.vRecycler) {
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

        binding.vSwipeRefresh.setOnRefreshListener {
            appViewModel.presenter.startLoading()
        }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
    ) {
        val info = menuInfo as MenuableRecyclerView.ContextMenuInfo? ?: return
        val item = listAdapter.items[info.position]
        menu.setHeaderTitle(item.ch.name)
        ContextMenuBuilder(menu).run {
            addNotificationItem(item)
            addItem(R.string.contact, item.ch.url)
            addItem(R.string.yp4g_chat, item.ch.chatUrl(), !item.ch.isEmptyId)
            addItem(R.string.yp4g_statistics, item.ch.statisticsUrl(), !item.ch.isEmptyId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(STATE_SCROLL_STATES, scrollStates)
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
                            lifecycleScope.launch {
                                db.favoriteDao.update(star.copyFlags { it.copy(isNotification = false) })
                            }
                            false
                        }
                }
                false -> {
                    menu.add(R.string.notification_add)
                        .setOnMenuItemClickListener { mi ->
                            lifecycleScope.launch {
                                db.favoriteDao.update(star.copyFlags { it.copy(isNotification = true) })
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
                    db.favoriteDao.remove(it)
                } ?: Favorite.Star(m.ch).let {
                    db.favoriteDao.add(it)
                }
            }
        }

        override fun onItemClick(m: ListItemViewModel, position: Int) {
            if (m.ch.isPlayable && !m.isNg) {
                appViewModel.presenter.startPlay(requireActivity(), m.ch)
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
        private const val STATE_SCROLL_STATES = "$TAG#scrollStates"
    }

}

