package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Drawable
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField

class ItemViewModel {
    val src = ObservableField<Drawable>()
    val background = ObservableField<Drawable>()
    val error = ObservableField<CharSequence>("loading..")
    val isTooLargeFileSize = ObservableBoolean(false)
    val isLinkUrl = ObservableBoolean(false)
    val isAnimation = ObservableBoolean(false)
}

var <T> ObservableField<T>.value: T?
    get() = this.get()
    set(value) = set(value)

var ObservableBoolean.value: Boolean
    get() = this.get()
    set(value) = set(value)