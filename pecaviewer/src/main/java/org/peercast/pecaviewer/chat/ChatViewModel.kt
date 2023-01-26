package org.peercast.pecaviewer.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.chat.net.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net.IMessage
import org.peercast.pecaviewer.chat.net.IThreadInfo
import org.peercast.pecaviewer.util.SnackbarFactory


class ChatViewModel(a: Application) : AndroidViewModel(a) {
    val urlLoader = ChatUrlLoader(this)

    /**n秒後に自動的にfalse*/
    val isToolbarVisible = MutableStateFlow(true)
    val isThreadListVisible = MutableStateFlow(false)

    val chatToolbarTitle = MutableStateFlow<CharSequence>("")
    val chatToolbarSubTitle = MutableStateFlow<CharSequence>("")

    val isThreadListLoading = MutableStateFlow(false)
    val isMessageListLoading = MutableStateFlow(false)

    val messages = MutableStateFlow<List<IMessage>>(emptyList())
    val threads = MutableStateFlow<List<IThreadInfo>>(emptyList())

    /**現在選択されているスレッド。されていなければnull*/
    val selectedThread = MutableStateFlow<IThreadInfo?>(null)

    /**現在選択されているスレッドへの書き込み。不可ならnull*/
    val selectedThreadPoster = MutableStateFlow<IBoardThreadPoster?>(null)

    /**下書き (URL/内容)*/
    val messageDraft = HashMap<String, String>()

    /**スナックバーに表示する*/
    val snackbarFactory = Channel<SnackbarFactory>(1, BufferOverflow.DROP_LATEST)

    /**自動リロードへの残り秒(%)*/
    val reloadRemain = MutableLiveData(-1)

    init {
        viewModelScope.launch {
            isThreadListVisible.collect {
                urlLoader.updateChatToolbarTitle()
            }
        }
    }

    private var loadJob: Job? = null

    fun loadUrl(url: Uri) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            urlLoader.loadUrl(url.toString())
        }
    }
}

