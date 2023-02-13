package org.peercast.pecaviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.peercast.pecaviewer.service.PlayerServiceEventFlow


class PecaViewerViewModel(
    private val a: Application,
    private val eventFlow: PlayerServiceEventFlow,
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

    /**フルスクリーンモードか*/
    val isFullScreenMode = MutableStateFlow(false)

    /**小窓モードか*/
    val isPipMode = MutableStateFlow(false)

    /**スマホの画面では邪魔なので半透明にする*/
    val isPostDialogButtonOpaque = slidingPanelState.map { state ->
        a.resources.getBoolean(R.bool.isPhoneScreen) && state != 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    /**書き込みボタンを表示するか*/
    val isPostDialogButtonVisible = MutableStateFlow(false)

}
