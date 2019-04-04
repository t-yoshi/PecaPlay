package org.peercast.pecaplay.list

import android.view.View
import android.view.ViewGroup
import androidx.databinding.BaseObservable
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.yp4g.YpChannel

//View
abstract class BaseListItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract val viewModel: IListItemViewModel
    abstract fun setItemEventListener(listener: IListItemEventListener?)
}

interface IListItemViewHolderFactory {
    fun createViewHolder(
        parent: ViewGroup, viewType: Int
    ): BaseListItemViewHolder

    fun getViewType(model: ListItemModel): Int
}


interface IListItemViewModel {
    val name: String
    val listener: String
    val description: CharSequence
    val comment: CharSequence
    val age: CharSequence

    val isStarChecked: Boolean
    val isStarEnabled: Boolean

    val isEnabled: Boolean
    val isNewlyVisible: Boolean
    val isAgeVisible: Boolean
    val isNewlyChecked: Boolean
    val isNotificatedVisible: Boolean

    var model: ListItemModel
    fun notifyChange()
}

//ch_item.xmlへ
abstract class BaseListItemViewModel : BaseObservable(), IListItemViewModel


interface IListItemEventListener {
    fun onStarClicked(m: ListItemModel, isChecked: Boolean)

    fun onItemClick(m: ListItemModel, position: Int)
    /**
     * @return イベントを消費したか。コンテキストメニューを出す必要があるならfalseを返すこと。
     * */
    fun onItemLongClick(m: ListItemModel, position: Int): Boolean
}

data class ListItemModel(
    val ch: YpChannel,
    val star: Favorite?,
    val isNg: Boolean,
    val isNotification: Boolean
) {
    override fun toString() = "$ch"
}

