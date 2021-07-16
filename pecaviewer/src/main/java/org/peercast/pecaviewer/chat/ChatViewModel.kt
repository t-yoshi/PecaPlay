package org.peercast.pecaviewer.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.peercast.pecaviewer.chat.net.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net.IMessage
import org.peercast.pecaviewer.chat.net.IThreadInfo


class ChatViewModel(a: Application) : AndroidViewModel(a) {
    val presenter = ChatPresenter(this)

    /**n秒後に自動的にfalse*/
    val isToolbarVisible = MutableLiveData<Boolean>(true)
    val isThreadListVisible = MutableLiveData<Boolean>(false)

    val chatToolbarTitle = MutableLiveData<CharSequence>("")
    val chatToolbarSubTitle = MutableLiveData<CharSequence>("")

    val isThreadListRefreshing = MutableLiveData(false)
    val isMessageListRefreshing = MutableLiveData(false)

    val messageLiveData = MutableLiveData<List<IMessage>>()
    val threadLiveData = MutableLiveData<List<IThreadInfo>>()

    /**現在選択されているスレッド。されていなければnull*/
    val selectedThread = MutableLiveData<IThreadInfo?>()

    /**現在選択されているスレッドへの書き込み。不可ならnull*/
    val selectedThreadPoster = MutableLiveData<IBoardThreadPoster?>()

    /**下書き (URL/内容)*/
    val messageDraft = HashMap<String, String>()

    /**スナックバーに表示する*/
    val snackbarMessage = MutableLiveData<SnackbarMessage>()

    /**自動リロードへの残り秒(%)*/
    val reloadRemain = MutableLiveData(-1)

    init {
        isThreadListVisible.observeForever {
            presenter.updateChatToolbarTitle()
        }
//        isToolbarVisible.observeForever {
//            if (it) {
//
////                handler.removeCallbacks(invisibleToolbarRunnable)
////                handler.postDelayed(invisibleToolbarRunnable, 8000)
//            }
//        }
    }
}

