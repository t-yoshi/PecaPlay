package org.peercast.pecaviewer.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.PecaViewerViewModel
import org.peercast.pecaviewer.chat.adapter.MessageAdapter
import org.peercast.pecaviewer.chat.adapter.ThreadAdapter
import org.peercast.pecaviewer.chat.thumbnail.ImageViewerFragment
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailUrl
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailView
import org.peercast.pecaviewer.databinding.FragmentChatBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import timber.log.Timber

@Suppress("unused")
class ChatFragment : Fragment(), Toolbar.OnMenuItemClickListener,
    ThumbnailView.OnItemEventListener {

    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val appViewModel by sharedViewModel<PecaViewerViewModel>()
    private val chatPrefs by lazy(LazyThreadSafetyMode.NONE) {
        requireContext().getSharedPreferences("chat", Context.MODE_PRIVATE)
    }

    private lateinit var binding: FragmentChatBinding
    private val threadAdapter = ThreadAdapter()
    private val messageAdapter = MessageAdapter(this)
    private var isAlreadyRead = false //既読
    private val autoReload = AutoReload()
    private var loadingJob: Job? = null
    private val loadingLiveData = MutableLiveData<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoReload.isEnabled = chatPrefs.isAutoReloadEnabled
        messageAdapter.defaultViewType = when (chatPrefs.isSimpleDisplay) {
            true -> MessageAdapter.SIMPLE
            else -> MessageAdapter.BASIC
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return FragmentChatBinding.inflate(inflater, container, false).also {
            binding = it
            it.viewModel = chatViewModel
            it.lifecycleOwner = viewLifecycleOwner
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.vThreadList.layoutManager = LinearLayoutManager(view.context)
        binding.vThreadList.adapter = threadAdapter

        binding.vMessageList.layoutManager = LinearLayoutManager(view.context)
        binding.vMessageList.adapter = messageAdapter

        binding.vChatToolbar.inflateMenu(R.menu.menu_chat_thread)
        binding.vChatToolbar.setNavigationOnClickListener {
            chatViewModel.isThreadListVisible.run {
                value = value != true
            }
        }
        binding.vChatToolbar.setOnMenuItemClickListener(this)
        binding.vChatToolbar.overflowIcon = ContextCompat.getDrawable(
            binding.vChatToolbar.context,
            R.drawable.ic_more_vert_black_24dp
        )

        binding.vMessageList.setOnClickListener {
            chatViewModel.isToolbarVisible.value = true
        }
        binding.vMessageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    //最後までスクロールしたらすべて既読とみなす
                    alreadyRead()
                }

                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    if (recyclerView.context.resources.getBoolean(R.bool.isNarrowScreen)) {
                        //狭い画面ではスクロール中にFABを消す。そして数秒後に再表示される。
                        appViewModel.isPostDialogButtonFullVisible.value = false
                    }
                    //過去のレスを見ているときは自動リロードを無効にする
                    if (recyclerView.canScrollVertically(1))
                        autoReload.isEnabled = false
                }
            }
        })

        binding.vThreadListRefresh.setOnRefreshListener {
            launchLoading {
                chatViewModel.presenter.reloadThreadList()
            }
        }
        binding.vMessageListRefresh.setOnRefreshListener {
            isAlreadyRead = true
            launchLoading {
                chatViewModel.presenter.reloadThread()
            }
        }
        threadAdapter.onSelectThread = { info ->
            launchLoading {
                chatViewModel.presenter.threadSelect(info)
            }
        }

//        playerViewModel.channelContactUrl.observe(viewLifecycleOwner, Observer { u ->
//            loadingJob?.cancel("new url is coming $u")
//            launchLoading {
//                chatViewModel.presenter.loadUrl(u)
//            }
//        })
        chatViewModel.threadLiveData.observe(viewLifecycleOwner, Observer {
            threadAdapter.items = it
        })
        chatViewModel.selectedThread.observe(viewLifecycleOwner, Observer {
            threadAdapter.selected = it
            if (it == null)
                chatViewModel.isThreadListVisible.postValue(true)
        })
        chatViewModel.messageLiveData.observe(viewLifecycleOwner, Observer {
            val b = isAlreadyRead
            Timber.d("isAlreadyRead=$isAlreadyRead")
            if (b)
                messageAdapter.markAlreadyAllRead()
            isAlreadyRead = false
            lifecycleScope.launch {
                messageAdapter.setItems(it)
                if (true || b)
                    scrollToBottom()
            }
            autoReload.scheduleRun()
        })

        chatViewModel.snackbarMessage.observe(
            viewLifecycleOwner,
            SnackbarObserver(view, activity?.findViewById(R.id.vPostDialogButton))
        )

        chatViewModel.isThreadListVisible.observe(viewLifecycleOwner, Observer {
            binding.vChatToolbar.menu.clear()
            if (it) {
                binding.vChatToolbar.inflateMenu(R.menu.menu_chat_board)
                binding.vChatToolbar.menu.findItem(R.id.menu_auto_reload_enabled).isChecked =
                    chatPrefs.isAutoReloadEnabled
                binding.vChatToolbar.menu.findItem(R.id.menu_simple_display).isChecked =
                    chatPrefs.isSimpleDisplay
            } else {
                binding.vChatToolbar.inflateMenu(R.menu.menu_chat_thread)
            }
        })

        loadingLiveData.observe(viewLifecycleOwner, Observer {
            with(binding.vChatToolbar.menu) {
                findItem(R.id.menu_reload).isVisible = !it
                findItem(R.id.menu_abort).isVisible = it
            }
        })

        savedInstanceState?.let(messageAdapter::restoreInstanceState)
    }

    private fun alreadyRead() {
        Timber.d("AlreadyRead!")
        isAlreadyRead = true
        autoReload.isEnabled = chatPrefs.isAutoReloadEnabled
        autoReload.scheduleRun()
    }

    private class SnackbarObserver(val view: View, val anchor: View?) : Observer<SnackbarMessage> {
        var bar: Snackbar? = null
        override fun onChanged(msg: SnackbarMessage?) {
            if (msg == null) {
                bar?.dismiss()
                bar = null
                return
            }

            val length = when {
                msg.cancelJob != null -> Snackbar.LENGTH_INDEFINITE
                else -> Snackbar.LENGTH_LONG
            }

            bar = Snackbar.make(view, msg.text, length).also { bar ->
                val c = bar.context
                msg.cancelJob?.let { j ->
                    bar.setAction(msg.cancelText ?: c.getText(android.R.string.cancel)) {
                        j.cancel()
                    }
                }
                if (msg.textColor != 0)
                    bar.setTextColor(ContextCompat.getColor(c, msg.textColor))
                //FABの上に出すのが正統
                anchor?.let(bar::setAnchorView)
                bar.show()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                isAlreadyRead = true
                launchLoading {
                    when (chatViewModel.isThreadListVisible.value) {
                        true -> chatViewModel.presenter.reloadThreadList()
                        else -> chatViewModel.presenter.reloadThread()
                    }
                }
            }
            R.id.menu_abort -> {
                loadingJob?.cancel("abort button clicked")
            }
            R.id.menu_align_top -> {
                binding.vMessageList.scrollToPosition(0)
            }
            R.id.menu_align_bottom -> {
                scrollToBottom()
                alreadyRead()
            }
            R.id.menu_auto_reload_enabled -> {
                val b = !item.isChecked
                item.isChecked = b
                chatPrefs.isAutoReloadEnabled = b
                autoReload.isEnabled = b
            }
            R.id.menu_simple_display -> {
                val b = !item.isChecked
                item.isChecked = b
                chatPrefs.isSimpleDisplay = b
                messageAdapter.defaultViewType = when (b) {
                    true -> MessageAdapter.SIMPLE
                    else -> MessageAdapter.BASIC
                }
            }
        }
        return true
    }

    private fun scrollToBottom() {
        val n = messageAdapter.itemCount
        if (n > 0) {
            val manager = binding.vMessageList.layoutManager as LinearLayoutManager
            manager.scrollToPositionWithOffset(n - 1, 0)
        }
    }

    override fun onLaunchImageViewer(u: ThumbnailUrl) {
        if (u.linkUrl.isNotEmpty()) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.linkUrl)))
            } catch (e: ActivityNotFoundException) {
                Timber.e(e)
            }
        } else {
            ImageViewerFragment.create(u.imageUrl)
                .show(parentFragmentManager, "ImageViewerFragment")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        messageAdapter.saveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        //復帰時に再描画
        messageAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadingJob = null
    }


    private fun launchLoading(block: suspend CoroutineScope.() -> Unit) {
        if (loadingJob?.run { isActive && !isCancelled } == true) {
            Timber.d("loadingJob [$loadingJob] is still active.")
            return
        }
        autoReload.cancelScheduleRun()
        loadingJob = lifecycleScope.launch {
            loadingLiveData.postValue(true)
            try {
                block()
            } finally {
                loadingLiveData.postValue(false)
            }
        }
    }

    /**
     * スレッドの自動読込。
     * */
    private inner class AutoReload {
        private var j: Job? = null
        private var f = {}

        fun scheduleRun() = f()

        fun cancelScheduleRun() {
            chatViewModel.reloadRemain.value = -1
            j?.cancel()
        }

        var isEnabled = false
            set(value) {
                if (field == value)
                    return
                field = value
                if (value) {
                    f = {
                        j?.cancel()
                        j = lifecycleScope.launch {
                            Timber.d("Set auto-reloading after ${AUTO_RELOAD_SEC}seconds.")
                            for (i in AUTO_RELOAD_SEC downTo 1) {
                                chatViewModel.reloadRemain.value = i * 100 / AUTO_RELOAD_SEC
                                delay(1000L)
                            }
                            chatViewModel.reloadRemain.value = -1
                            Timber.d("Start auto-reloading.")
                            j = null
                            launchLoading {
                                chatViewModel.presenter.reloadThread()
                            }
                        }
                    }
                } else {
                    cancelScheduleRun()
                    f = {}
                }
            }
    }

    /**スレを自動的にリロードするか*/
    private var SharedPreferences.isAutoReloadEnabled: Boolean
        get() = getBoolean(KEY_AUTO_RELOAD, true)
        set(value) {
            edit { putBoolean(KEY_AUTO_RELOAD, value) }
        }

    private var SharedPreferences.isSimpleDisplay: Boolean
        get() = getBoolean(KEY_SIMPLE_DISPLAY, true)
        set(value) {
            edit { putBoolean(KEY_SIMPLE_DISPLAY, value) }
        }

    companion object {
        private const val AUTO_RELOAD_SEC = 40

        private const val KEY_AUTO_RELOAD = "key_chat_auto_reload"
        private const val KEY_SIMPLE_DISPLAY = "key_simple_display"
    }
}


