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
    private val eventLiveData by inject<PlayerServiceEventLiveData>()

    /**コントロールボタンの表示。タッチして数秒後に消える*/
    val isControlsViewVisible = MutableLiveData(false)

    val isFullScreenMode = MutableLiveData(false)


    /**ステータス。配信時間など*/
    val channelStatus: LiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        ld.addSource(eventLiveData) { ev ->
            when (ev) {
//                is MediaPlayerEvent -> {
//                    when (ev.ev.type) {
////                        MediaPlayer.Event.TimeChanged -> {
////                            val t = ev.ev.timeChanged / 1000
////                            ld.value =
////                                "%d:%02d:%02d".format(t / 60 / 60, t / 60 % 60, t % 60)
////                        }
//                    }
//                }
            }
        }
    }

    /**警告メッセージ。エラー、バッファ発生など*/
    val channelWarning: LiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        //赤文字の警告は数秒後に消え、緑のステータス表示に戻る
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

        ld.addSource(eventLiveData) { ev ->
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
                    ld.value =  a.getString(R.string.start_loading)
                }
            }
        }
    }

}