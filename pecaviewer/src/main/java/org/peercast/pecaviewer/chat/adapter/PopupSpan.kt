package org.peercast.pecaviewer.chat.adapter

import android.text.Spannable
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.text.set
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.R
import timber.log.Timber

/**
アンカーをクリックしてポップアップ
 */
class PopupSpan private constructor(private val resNumber: Int) : ClickableSpan() {
    override fun onClick(widget: View) {
        //Timber.d("--> #$resNumber $widget")
        val c = widget.context
        val rv = findParentRecyclerView(widget) ?: return Timber.w("RecyclerView not found")
        val adapter =
            rv.adapter as? SupportAdapter ?: return Timber.w("adapter not as SupportAdapter")
        val view = adapter.createViewForPopupWindow(resNumber, rv)
            ?: return Timber.w("createViewForPopupWindow returned null")
        val bg = ContextCompat.getDrawable(c, R.drawable.frame_bg_blue)

        PopupWindow(
            view, rv.width,
            WindowManager.LayoutParams.WRAP_CONTENT, true
        ).also {
            it.setBackgroundDrawable(bg)
            it.isOutsideTouchable = true
        }.showAsDropDown(widget, 0, 0)
    }

    interface SupportAdapter {
        /**該当レス番号を表示するcontentViewを作成する。不可ならnull*/
        fun createViewForPopupWindow(resNumber: Int, parent: ViewGroup): View?
    }

    companion object {
        private val RE_ANCHOR = """[>＞]{2}([1-9]\d{0,3})""".toRegex()

        /**
         * テキスト内のアンカーにPopupSpanを適用する
         */
        fun Spannable.applyPopupSpanForAnchors(): Spannable {
            RE_ANCHOR.findAll(this).forEach { mr ->
                this[mr.range.first, mr.range.last + 1] =
                    PopupSpan(mr.groupValues[1].toInt())
            }
            return this
        }

        private fun findParentRecyclerView(widget: View): RecyclerView? {
            var w = widget.parent
            while (w != null) {
                if (w is RecyclerView)
                    return w
                w = w.parent
            }
            return null
        }
    }
}