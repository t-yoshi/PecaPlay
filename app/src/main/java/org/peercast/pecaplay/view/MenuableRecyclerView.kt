package org.peercast.pecaplay.view

import android.content.Context
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * コンテキストメニューの実装
 *
 * @see Fragment.registerForContextMenu
 *
 * (注) ロングクリックイベントをRecyclerViewまで透過させること。
 * holder.itemView.setOnLongClickListener { false }
 * **/
class MenuableRecyclerView : RecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    data class ContextMenuInfo(
            val position: Int,
            val id: Long
    ) : ContextMenu.ContextMenuInfo

    private var menuInfo: ContextMenuInfo? = null

    override fun getContextMenuInfo() = menuInfo

    override fun showContextMenuForChild(originalView: View?): Boolean {
        if (originalView == null || originalView.layoutParams !is RecyclerView.LayoutParams)
            return false

        val pos = getChildAdapterPosition(originalView)
        if (pos >= 0) {
            val id = adapter?.getItemId(pos) ?: return false
            menuInfo = ContextMenuInfo(pos, id)
            return super.showContextMenuForChild(originalView)
        }
        return false
    }

    companion object {
        private const val TAG = "MenuableRecyclerView"
    }

}