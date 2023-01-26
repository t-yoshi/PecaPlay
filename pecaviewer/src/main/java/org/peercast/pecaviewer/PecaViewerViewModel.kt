package org.peercast.pecaviewer

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.peercast.pecaplay.core.app.Yp4gChannel
import org.peercast.pecaviewer.service.PlayerService
import org.peercast.pecaviewer.service.bindPlayerService


class PecaViewerViewModel(
    private val a: Application,
) : AndroidViewModel(a) {

    /**
     * スライディングパネルの状態
    EXPANDED=0  プレーヤーのみ,
    COLLAPSED=1 チャットのみ,
    ANCHORED=2  両方表示,
     * @see com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
     */
    val slidingPanelState = MutableStateFlow(0)

    /**書き込みボタンが有効か*/
    val isPostDialogButtonEnabled = MutableStateFlow(false)

    /**プレーヤーの再生/停止ボタンの表示。タッチして数秒後に消える*/
    val isPlayerControlsVisible = MutableStateFlow(false)

    /**フルスクリーンモードか*/
    val isFullScreenMode = MutableStateFlow(false)

    /**小窓モードか*/
    val isPipMode = MutableStateFlow(false)

    /**スマホの画面では邪魔なので半透明にする*/
    val isPostDialogButtonOpaque = slidingPanelState.map { state ->
        a.resources.getBoolean(R.bool.isPhoneScreen) && state != 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**書き込みボタンを表示するか*/
    val isPostDialogButtonVisible = combine(
        isFullScreenMode,
        isPlayerControlsVisible,
        isPipMode,
    ) { full, ctrl, pip ->
        (!full || ctrl) && !pip
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)


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

    fun startPlay(streamUrl: Uri, channel: Yp4gChannel) {
        viewModelScope.launch {
            playerService.filterNotNull().collect {
                it.prepareFromUri(streamUrl, channel)
                it.play()
            }
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
