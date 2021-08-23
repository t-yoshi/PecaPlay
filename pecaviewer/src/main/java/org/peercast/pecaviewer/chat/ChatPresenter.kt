package org.peercast.pecaviewer.chat

import android.app.Application
import android.content.Context
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.peercast.pecaplay.core.io.localizedSystemMessage
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.net.*
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

class ChatPresenter(private val chatViewModel: ChatViewModel) {
    private val a = chatViewModel.getApplication<Application>()
    private val prefs = BbsThreadPreference(a)
    private var boardConn by Delegates.observable<IBoardConnection?>(null) { _, _, _ ->
        updateChatToolbarTitle()
    }

    //コンタクトURL。配信者が更新しないかぎり変わらない。
    private val contactUrl get() = boardConn?.info?.url ?: ""

    /**スレッドのリストとメッセージを再読込する*/
    suspend fun reload() {
        loadUrl(contactUrl, true)
    }

    /**スレッドのリストを再読込する*/
    suspend fun reloadThreadList() {
        try {
            chatViewModel.isThreadListLoading.postValue(true)
            clearSnackMessage()

            val conn = boardConn ?: return
            val threads = conn.loadThreads()
            Timber.d("threads=$threads")
            chatViewModel.threadLiveData.postValue(threads)
        } catch (e: IOException) {
            chatViewModel.threadLiveData.postValue(emptyList())
            threadSelect(null)
            postSnackErrorMessage(e)
        } finally {
            chatViewModel.isThreadListLoading.postValue(false)
        }
    }

    /**
     * スレッドのメッセージを再読込する。
     * */
    suspend fun reloadThread() {
        if (chatViewModel.isMessageListLoading.value == true) {
            Timber.w("already loading.")
            return
        }

        chatViewModel.isMessageListLoading.postValue(true)
        clearSnackMessage()
        try {
            val conn = boardConn
            //Timber.d("$boardConn")
            if (conn is IBoardThreadConnection) {
                chatViewModel.selectedThreadPoster.postValue(
                    if (conn.info.isPostable && conn is IBoardThreadPoster) conn else null
                )
                val messages = conn.loadMessages()
                chatViewModel.messageLiveData.postValue(messages)
            }
        } catch (e: IOException) {
            postSnackErrorMessage(e)
        } finally {
            chatViewModel.isMessageListLoading.postValue(false)
        }
    }

    /**
     * コンタクトURLを読込む。
     * */
    suspend fun loadUrl(url: String, isForce: Boolean = false) {
        Timber.d("loadUrl=$url, isForce=$isForce")
        when {
            url.isBlank() -> {
                chatViewModel.threadLiveData.value = emptyList()
            }
            !url.matches("""^https?://.+""".toRegex()) -> {
                Timber.w("invalid url: $url")
            }
            url == contactUrl && !isForce -> {
                //何もしない
                return
            }
            else -> {
                return doLoadUrl(url)
            }
        }
        boardConn = null
        threadSelect(null)
    }

    private suspend fun doLoadUrl(url: String) {
        Timber.d("doLoadUrl: $url")

        try {
            chatViewModel.isThreadListLoading.postValue(true)
            clearSnackMessage()

            val conn = openBoardConnection(url)
            boardConn = conn

            val threads = conn.loadThreads()
            Timber.d("threads=$threads")
            chatViewModel.threadLiveData.postValue(threads)

            //スレッド選択の復元
            val threadSelectFilter = prefs.restoreSelectedThread(conn.info)
            val selectedThread = when {
                threadSelectFilter != null -> {
                    conn.loadThreads().firstOrNull(threadSelectFilter)
                }
                conn is IBoardThreadConnection -> {
                    conn.info
                }
                else -> {
                    null
                }
            }
            threadSelect(selectedThread)
        } catch (e: IOException) {
            threadSelect(null)
            postSnackErrorMessage(e)
        } catch (t: Throwable) {
            Timber.w(t)
            throw t
        } finally {
            chatViewModel.isThreadListLoading.postValue(false)
        }
    }

    suspend fun threadSelect(info: IThreadInfo?) {
        chatViewModel.selectedThread.postValue(info)

        if (info == null) {
            //boardConn = null
            chatViewModel.selectedThreadPoster.postValue(null)
            chatViewModel.messageLiveData.postValue(emptyList())
            Timber.w("Thread not selected: $contactUrl")
            return
        }

        try {
            val threadConn = boardConn?.openThreadConnection(info) ?: return

            //スレッドの選択を保存する
            prefs.storeSelectedThread(info)

            boardConn = threadConn
            reloadThread()

            chatViewModel.isThreadListVisible.postValue(false)

        } catch (e: IOException) {
            postSnackErrorMessage(e)
        }
    }

    @MainThread
    fun updateChatToolbarTitle() {
        val title = when (chatViewModel.isThreadListVisible.value) {
            true -> boardConn?.info?.boardTopTitle
            else -> boardConn?.info?.title
        }
        chatViewModel.chatToolbarTitle.value = title
    }

    /**掲示板に書き込む*/
    fun postMessage(poster: IBoardThreadPoster, msg: PostMessage): Job {
        return chatViewModel.viewModelScope.launch {
            val d = async {
                val r = kotlin.runCatching { poster.postMessage(msg) }
                postSnackMessage(r)
                r.isSuccess
            }
            postSnackMessage(a.getText(R.string.sending), d)

            if (!d.isCancelled && d.await()) {
                //送信成功したのでスレッドを再読み込み
                try {
                    reload()
                    clearSnackMessage()
                } catch (e: IOException) {
                    postSnackErrorMessage(e)
                }
            }
        }
    }

    /**スナックバー表示をクリアする*/
    fun clearSnackMessage() {
        chatViewModel.snackbarMessage.postValue(null)
    }

    /**スナックバーに表示する*/
    fun postSnackMessage(
        text: CharSequence,
        cancelJob: Job? = null,
        cancelText: CharSequence? = null,
    ) {
        val m = SnackbarMessage(text, cancelJob = cancelJob, cancelText = cancelText)
        chatViewModel.snackbarMessage.postValue(m)
    }

    /**スナックバーに成功またはエラーを表示する*/
    fun postSnackMessage(result: Result<CharSequence>) {
        result.onSuccess {
            postSnackMessage(it)
        }.onFailure {
            Timber.d(it)
            if (it !is IOException)
                throw it
            postSnackErrorMessage(it)
        }
    }

    /**スナックバーにエラーを表示する*/
    fun postSnackErrorMessage(e: IOException) {
        val m = SnackbarMessage(e.localizedSystemMessage(), R.color.red_800)
        chatViewModel.snackbarMessage.postValue(m)
    }

}

//スレッドの選択を保存する
private class BbsThreadPreference(c: Context) {
    private val pref = c.getSharedPreferences(
        "chat.thread",
        Context.MODE_PRIVATE
    )

    init {
        //古いものを削除
        val now = System.currentTimeMillis()
        val expired = pref.all.filter { (_, v) ->
            val m = RE_EXPIRE.find(v.toString()) ?: return@filter true
            m.groupValues[1].toLong() < now
        }
        pref.edit {
            expired.forEach { remove(it.key) }
        }
    }

    fun storeSelectedThread(thread: IThreadInfo) {
        if (thread !is BaseBbsThreadInfo) {
            Timber.w("$thread is not stored.")
            return
        }
        //避難所ではスレ選択を記憶しない
        if (isInShelter(thread))
            return

        val expire = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        pref.edit {
            val u = "${thread.url}#!expire=$expire"
//            Timber.d("${thread.board.url} -> $u")
            putString(thread.board.url, u)
        }
    }

    fun restoreSelectedThread(boardInfo: IBoardInfo): ((IThreadInfo) -> Boolean)? {
        //避難所ではスレ選択を記憶しない
        if (isInShelter(boardInfo))
            return null

        val boardUrl = boardInfo.boardTopUrl
        return pref.getString(boardUrl, null)
            ?.replace(RE_EXPIRE, "")
//            ?.also { Timber.d("$boardUrl <- $it") }
            ?.let { u -> { it.url == u } }
    }

    //避難所にいるかどうか
    private fun isInShelter(boardInfo: IBoardInfo): Boolean {
        //避難所板 || 避難所スレ
        return "避難所" in boardInfo.boardTopTitle ||
                "避難所" in boardInfo.title
    }

    companion object {
        private val RE_EXPIRE = """#!expire=(\d+)$""".toRegex()
    }
}
