package org.peercast.pecaviewer.chat

import androidx.annotation.ColorRes
import kotlinx.coroutines.Job

class SnackbarMessage(
    val text: CharSequence,
    @ColorRes val textColor: Int = 0,
    val cancelJob: Job? = null,
    val cancelText: CharSequence? = null
)
