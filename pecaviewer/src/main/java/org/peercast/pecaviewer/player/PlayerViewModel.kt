package org.peercast.pecaviewer.player

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.service.*


class PlayerViewModel(private val a: Application) : AndroidViewModel(a), KoinComponent {
    //サービスからのイベントを元に表示する
    private val eventFlow by inject<PlayerServiceEventFlow>()

    /**コントロールボタンの表示。タッチして数秒後に消える*/
    val isControlsViewVisible = MutableStateFlow(false)

    val isFullScreenMode = MutableStateFlow(false)

    /**警告メッセージ。エラー、バッファ発生など。数秒後に消える。*/
    val channelWarning: StateFlow<CharSequence> = MutableStateFlow<CharSequence>("").also { f ->
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
                f.value = s

                //5秒後に""を送出
                if (s.isNotBlank()) {
                    j?.cancel()
                    j = launch {
                        delay(5_000)
                        f.value = ""
                    }
                }
            }
        }

    }
}