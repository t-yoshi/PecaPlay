package org.peercast.pecaviewer.player

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.service.*


class PlayerViewModel(private val a: Application) : AndroidViewModel(a), KoinComponent {
    //サービスからのイベントを元に表示する
    private val eventFlow by inject<PlayerServiceEventFlow>()

    /**コントロールボタンの表示。タッチして数秒後に消える*/
    val isControlsViewVisible = MutableLiveData(false)

    val isFullScreenMode = MutableLiveData(false)

    /**警告メッセージ。エラー、バッファ発生など。数秒後に消える。*/
    val channelWarning: LiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        var j: Job? = null
        ld.observeForever {
            if (it.isNotEmpty()) {
                j?.cancel()
                j = viewModelScope.launch {
                    delay(5_000)
                    ld.value = ""
                }
            }
        }

        ld.addSource(eventFlow.asLiveData()) { ev ->
            when (ev) {
                is PeerCastNotifyMessageEvent -> {
                    ld.value = ev.message
                }

                is PlayerBufferingEvent -> {
                    ld.value = if (ev.percentage > 0)
                        a.getString(R.string.buffering_p, ev.percentage)
                    else
                        a.getString(R.string.buffering)
                }

                is PlayerLoadErrorEvent -> {
                    ld.value = a.getString(R.string.error_loading, ev.e.message ?: "(null)")
                }

                is PlayerLoadStartEvent -> {
                    ld.value = a.getString(R.string.start_loading)
                }

                is PlayerErrorEvent -> {
                    ld.value = "${ev.errorType} ${ev.e.message?.take(20)}"
                }
            }
        }
    }

}