package org.peercast.pecaviewer.chat.adapter

import androidx.databinding.BaseObservable
import kotlinx.coroutines.flow.MutableStateFlow
import org.peercast.pecaviewer.chat.net.IThreadInfo

class ThreadViewModel : BaseObservable() {
    val number = MutableStateFlow<CharSequence>("")
    val title = MutableStateFlow<CharSequence>("")
    val count = MutableStateFlow<CharSequence>("")
    val isSelected = MutableStateFlow(false)

    fun setThreadInfo(info: IThreadInfo, position: Int, selected: Boolean) {
        number.value = "% 2d".format(position + 1)
        title.value = info.title
        count.value = "${info.numMessages}"
        isSelected.value = selected
    }
}