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

    class Cancelable(text: CharSequence, private val job: Job) :
        SnackbarFactory(text, Snackbar.LENGTH_INDEFINITE) {

        override fun create(view: View): Snackbar {
            return super.create(view).also {
                it.setAction(android.R.string.cancel) {
                    job.cancel()
                }
            }
        }
    }

    open fun create(view: View): Snackbar {
        return Snackbar.make(view, text, duration).also { bar ->
            textColor?.let { bar.setTextColor(it) }
        }
    }

}