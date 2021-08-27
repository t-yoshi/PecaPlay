package org.peercast.pecaplay.prefs

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.ManageableEntity
import org.peercast.pecaplay.app.YellowPage

sealed class EntityPresenter<E : ManageableEntity>(protected val activity: FragmentActivity) {
    protected val db by activity.inject<AppRoomDatabase>()

    /** 編集の完了 (oldItem=nullなら新規)    */
    abstract fun replaceItem(oldItem: E?, newItem: E)

    /**確認済みなので削除する。*/
    abstract fun removeItem(item: E)

    /**チェックボックスを押して有効/無効が変化した。　*/
    abstract fun updateItem(item: E, enabled: Boolean)
}

class FavoritePresenter(a: FragmentActivity) : EntityPresenter<Favorite>(a) {
    override fun replaceItem(oldItem: Favorite?, newItem: Favorite) {
        activity.lifecycleScope.launch {
            db.favoriteDao.run {
                oldItem?.let { remove(it) }
                add(newItem)
            }
        }
    }

    override fun removeItem(item: Favorite) {
        activity.lifecycleScope.launch {
            db.favoriteDao.remove(item)
        }
    }

    override fun updateItem(item: Favorite, enabled: Boolean) {
        activity.lifecycleScope.launch {
            db.favoriteDao.update(item.copy(isEnabled = enabled))
        }
    }
}

class YellowPagePresenter(a: FragmentActivity) : EntityPresenter<YellowPage>(a) {
    override fun removeItem(item: YellowPage) {
        activity.lifecycleScope.launch {
            db.yellowPageDao.remove(item)
        }
    }

    override fun replaceItem(oldItem: YellowPage?, newItem: YellowPage) {
        activity.lifecycleScope.launch {
            db.yellowPageDao.run {
                oldItem?.let { remove(it) }
                add(newItem)
            }
        }
    }

    override fun updateItem(item: YellowPage, enabled: Boolean) {
        activity.lifecycleScope.launch {
            db.yellowPageDao.update(item.copy(isEnabled = enabled))
        }
    }
}

