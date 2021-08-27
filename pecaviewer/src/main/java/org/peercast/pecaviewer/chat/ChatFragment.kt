package org.peercast.pecaviewer.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.stfalcon.imageviewer.StfalconImageViewer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.PecaViewerViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.adapter.MessageAdapter
import org.peercast.pecaviewer.chat.adapter.ThreadAdapter
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailUrl
import org.peercast.pecaviewer.databinding.FragmentChatBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import timber.log.Timber
import kotlin.properties.Delegates

@Suppress("unused")
class ChatFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val appViewModel by sharedViewModel<PecaViewerViewModel>()
    private lateinit var chatPrefs: SharedPreferences

    private lateinit var binding: FragmentChatBinding
    private val threadAdapter = ThreadAdapter(this)
    private val messageAdapter = MessageAdapter(this)
    private var loadingJob: Job? = null

    /**読込中である*/
    private val isLoading = MutableStateFlow(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatPrefs = requireContext().getSharedPreferences("chat", Context.MODE_PRIVATE)
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

        val messageLayoutManager = LinearLayoutManager(view.context)
        messageLayoutManager.stackFromEnd = true
        binding.vMessageList.layoutManager = messageLayoutManager
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
            private var isBottom by Delegates.observable(false) { _, old, new ->
                if (old == new)
                    return@observable
                //Timber.d("--> $new")
                if (new && chatViewModel.selectedThreadPoster.value != null) {
                    scheduleAutoReload()
                } else {
                    cancelAutoReload()
                }
            }

            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                val size = messageAdapter.itemCount
                val last = messageLayoutManager.findLastVisibleItemPosition()
                isBottom = size > 0 && last == size - 1
            }
        })

        threadAdapter.onSelectThread = { info ->
            startLoading {
                threadSelect(info)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    chatViewModel.threads.collect {
                        threadAdapter.items = it
                    }
                }

                launch {
                    chatViewModel.selectedThread.collect {
                        threadAdapter.selected = it
                        if (it == null)
                            chatViewModel.isThreadListVisible.value = true
                    }
                }

                launch {
                    chatViewModel.messages.collect {
                        messageAdapter.messages = it
                        scheduleAutoReload()
                        scrollToBottom()
                    }
                }

                launch {
                    chatViewModel.isThreadListVisible.collect {
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
                    }
                }

                launch {
                    combine(
                        isLoading, chatViewModel.isThreadListVisible
                    ) { b, _ ->
                        with(binding.vChatToolbar.menu) {
                            findItem(R.id.menu_reload).isVisible = !b
                            findItem(R.id.menu_abort).isVisible = b
                        }
                    }.collect()
                }

                launch {
                    combine(
                        chatViewModel.isThreadListVisible,
                        chatViewModel.threads,
                    ) { b, t ->
                        backPressedCallback.isEnabled = b && t.isNotEmpty()
                    }.collect()
                }
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                startLoading {
                    when (chatViewModel.isThreadListVisible.value) {
                        true -> reloadThreadList()
                        else -> reloadThread()
                    }
                }
            }
            R.id.menu_abort -> {
                loadingJob?.cancel("abort button clicked")
            }
            R.id.menu_top -> {
                scrollToTop()
            }
            R.id.menu_bottom -> {
                scrollToBottom()
            }
            R.id.menu_auto_reload_enabled -> {
                val b = !item.isChecked
                item.isChecked = b
                chatPrefs.isAutoReloadEnabled = b
                scheduleAutoReload()
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

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            chatViewModel.isThreadListVisible.value = false
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun scrollToTop() {
        binding.vMessageList.scrollToPosition(0)
    }

    private fun scrollToBottom() {
        val n = messageAdapter.itemCount
        if (n > 0) {
            val manager = binding.vMessageList.layoutManager as LinearLayoutManager
            manager.scrollToPosition(n - 1)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        //復帰時に再描画
        messageAdapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        cancelAutoReload()
        loadingJob?.cancel()
    }

    private fun startLoading(action: suspend ChatPresenter.() -> Unit) {
        if (loadingJob?.run { isActive && !isCancelled } == true) {
            Timber.d("loadingJob [$loadingJob] is still active.")
            return
        }

        loadingJob = lifecycleScope.launchWhenResumed {
            isLoading.value = true
            try {
                chatViewModel.presenter.action()
            } finally {
                isLoading.value = false
            }
        }
    }

    private var jAutoReload: Job? = null

    /**
     * 自動読込のキャンセル。
     * */
    private fun cancelAutoReload() {
        //Timber.d("cancelAutoReload")
        jAutoReload?.cancel()
        chatViewModel.reloadRemain.value = -1
    }

    /**
     * スレッドの自動読込。
     * */
    private fun scheduleAutoReload() {
        cancelAutoReload()

        if (!chatPrefs.isAutoReloadEnabled || chatViewModel.selectedThreadPoster.value == null)
            return

        jAutoReload = lifecycleScope.launchWhenResumed {
            try {
                Timber.d("Set auto-reloading every ${AUTO_RELOAD_SEC}seconds.")
                while (isActive) {
                    for (i in AUTO_RELOAD_SEC downTo 1) {
                        chatViewModel.reloadRemain.value = i * 100 / AUTO_RELOAD_SEC
                        delay(1000L)
                    }
                    chatViewModel.reloadRemain.value = -1

                    Timber.d("Start auto-reloading.")
                    startLoading {
                        reloadThread()
                    }
                }
            } finally {
                chatViewModel.reloadRemain.value = -1
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
        private const val AUTO_RELOAD_SEC = 30

        private const val KEY_AUTO_RELOAD = "key_chat_auto_reload"
        private const val KEY_SIMPLE_DISPLAY = "key_simple_display"
    }
}


