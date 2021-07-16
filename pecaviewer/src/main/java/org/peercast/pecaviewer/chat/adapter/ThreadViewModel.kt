package org.peercast.pecaviewer.chat.adapter

import androidx.databinding.BaseObservable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import org.peercast.pecaviewer.chat.net.IThreadInfo

class ThreadViewModel : BaseObservable() {
    val number = ObservableField<CharSequence>()
    val title = ObservableField<CharSequence>()
    val count = ObservableField<CharSequence>()
    val isSelected = ObservableBoolean()

    fun setThreadInfo(info: IThreadInfo, position: Int, selected: Boolean) {
        number.set("% 2d".format(position + 1))
        title.set(info.title)
        count.set("${info.numMessages}")
        isSelected.set(selected)
    }
}