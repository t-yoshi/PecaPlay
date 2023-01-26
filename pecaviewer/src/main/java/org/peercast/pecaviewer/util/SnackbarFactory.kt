package org.peercast.pecaviewer.util

import android.view.View
import androidx.annotation.ColorInt
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job


open class SnackbarFactory(
    private val text: CharSequence,

    @ColorInt
    private val textColor: Int? = null,

    @BaseTransientBottomBar.Duration
    private val duration: Int = Snackbar.LENGTH_SHORT,
) {
    protected open suspend fun create(view: View): Snackbar {
        return Snackbar.make(view, text, duration)
    }

    suspend fun show(view: View, anchor: View?) {
        create(view).also { bar ->
            textColor?.let(bar::setTextColor)
            bar.anchorView = anchor
        }.show()
    }
}


class CancelableSnackbarFactory(
    text: CharSequence,
    @ColorInt textColor: Int? = null,
    private val job: Job
) :
    SnackbarFactory(text, textColor, Snackbar.LENGTH_INDEFINITE) {

    override suspend fun create(view: View): Snackbar {
        return super.create(view).also { bar ->
            bar.setAction(android.R.string.cancel) {
                job.cancel()
            }
        }
    }
}