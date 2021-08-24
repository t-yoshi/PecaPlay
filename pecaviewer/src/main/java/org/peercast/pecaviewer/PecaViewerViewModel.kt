package org.peercast.pecaviewer

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService


internal class PecaViewerViewModel(
    private val a: Application,
    private val playerViewModel: PlayerViewModel,
    private val chatViewModel: ChatViewModel,
) : AndroidViewModel(a) {

    /**
     * スライディングパネルの状態
    EXPANDED=0  プレーヤーのみ,
    COLLAPSED=1 チャットのみ,
    ANCHORED=2  両方表示,
     * @see com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
     */
    val slidingPanelState = MutableStateFlow(0)


    val isPostDialogButtonVisible: StateFlow<Boolean> = MutableStateFlow(false).also { f ->
        viewModelScope.launch {
            combine(
                playerViewModel.isFullScreenMode,
                playerViewModel.isControlsViewVisible,
                slidingPanelState,
            ) { isFullscreen, controlsVisible, panelState ->
                !isFullscreen || controlsVisible || panelState != 0
            }.collect {
                f.value = it
            }
        }
    }

    /**スマホの画面では半透明にする*/
    val isPostDialogButtonOpaque = MutableStateFlow(false).also { f ->
        viewModelScope.launch {
            slidingPanelState.collect { state ->
                f.value = a.resources.getBoolean(R.bool.isPhoneScreen) && state != 0
            }
        }
    }


    private val _playerService = MutableStateFlow<PlayerService?>(null)

    val playerService: StateFlow<PlayerService?> get() = _playerService

    private val playerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            _playerService.value = (binder as PlayerService.Binder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _playerService.value = null
        }
    }

    fun bindPlayerService() {
        a.bindPlayerService(playerServiceConnection)
    }

    fun unbindPlayerService() {
        if (_playerService.value != null) {
            a.unbindService(playerServiceConnection)
            _playerService.value = null
        }
    }


}
