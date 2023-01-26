package org.peercast.pecaviewer.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.service.*


class PlayerViewModel(a: Application, eventFlow: PlayerServiceEventFlow) : AndroidViewModel(a) {
    /**Ch名、詳細を表示するツールバーを表示するか*/
    val isToolbarVisible = MutableStateFlow(true)

    /**警告メッセージ。エラー、バッファ発生など。数秒後に消える。*/
    val channelWarning = MutableStateFlow<CharSequence>("")

    val channelTitle = MutableStateFlow<CharSequence>("")

    val channelComment = MutableStateFlow<CharSequence>("")

    val isPlaying = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            var j: Job? = null
            eventFlow.collect { ev ->
                val s = when (ev) {
                    is PeerCastNotifyMessageEvent -> {
                        ev.message
                    }

                    is PlayerBufferingEvent -> {
                        if (ev.percentage > 0)
                            a.getString(R.string.buffering_p, ev.percentage)
                        else
                            a.getString(R.string.buffering)
                    }

                    is PlayerLoadErrorEvent -> {
                        a.getString(R.string.error_loading, ev.e.message ?: "(null)")
                    }

                    is PlayerLoadStartEvent -> {
                        a.getString(R.string.start_loading)
                    }

                    is PlayerErrorEvent -> {
                        "${ev.errorType} ${ev.e.message?.take(20)}"
                    }

                    else -> return@collect
                }
                channelWarning.value = s

                //5秒後に""を送出
                if (s.isNotBlank()) {
                    j?.cancel()
                    j = launch {
                        delay(5_000)
                        channelWarning.value = ""
                    }
                }
            }
        }

    }

}