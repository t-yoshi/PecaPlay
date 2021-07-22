package org.peercast.pecaviewer

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel


internal class ViewerViewModel(
    private val a: Application,
    private val playerViewModel: PlayerViewModel,
    private val chatViewModel: ChatViewModel,
) : AndroidViewModel(a) {
    /**
     * スライディングパネルの状態
    EXPANDED=0,
    COLLAPSED=1,
    ANCHORED=2
     * @see com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
     */
    val slidingPanelState = MutableLiveData<Int>()

    /**
     * 没入モード。フルスクリーンかつコントロール類が表示されていない状態。
     * -> systemUiVisibilityを変える。
     * */
    val isImmersiveMode: LiveData<Boolean> = MediatorLiveData<Boolean>().also { ld ->
        val o = Observer<Any> {
            ld.value = playerViewModel.isFullScreenMode.value == true &&
                    playerViewModel.isControlsViewVisible.value != true &&
                    slidingPanelState.value == 0 // EXPANDED
        }
        ld.addSource(playerViewModel.isFullScreenMode, o)
        ld.addSource(playerViewModel.isControlsViewVisible, o)
        ld.addSource(slidingPanelState, o)
    }

    /**狭いスマホの画面ではスクロール時に数秒FABを引っ込める*/
    val isPostDialogButtonFullVisible: MutableLiveData<Boolean> =
        MediatorLiveData<Boolean>().also { ld ->
            val onVisible = Observer<Any> {
                ld.value = true
            }
            //これらのイベントが発生したとき、引っ込んでいたFABを再表示する
            ld.addSource(playerViewModel.isFullScreenMode, onVisible)
            ld.addSource(playerViewModel.isControlsViewVisible, onVisible)
            ld.addSource(slidingPanelState, onVisible)

            val onHide = Observer<Any> {
                if (a.resources.getBoolean(R.bool.isNarrowScreen))
                    ld.value = false
            }
            //これらのイベントが発生したとき、一時的にFABを引っ込める
            ld.addSource(chatViewModel.messageLiveData, onHide)

            //n秒後に再表示する
            var j: Job? = null
            ld.observeForever {
                j?.cancel()
                val sec = a.resources.getInteger(R.integer.post_button_show_after_sec)
                if (!it) {
                    j = viewModelScope.launch {
                        delay(sec * 1000L)
                        ld.value = true
                    }
                }
            }

        }
}
