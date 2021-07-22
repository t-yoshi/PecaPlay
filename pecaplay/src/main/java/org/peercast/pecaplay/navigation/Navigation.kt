package org.peercast.pecaplay.navigation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.get
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.navigation.NavigationView
import java.util.*
import kotlin.properties.Delegates


//
// ナビゲーションメニューの拡張
//

/*
class PecaNavigationViewExtension(
    private val view: NavigationView,
    savedInstanceState: Bundle?,
    scope: LifecycleCoroutineScope,
    private val onItemClick: (NavigationItem) -> Unit,
) {

    private val inflater = LayoutInflater.from(view.context)
    private val model: INavigationModel = NavigationModel(view.context)

    private val invisiblePrefs = view.context.getSharedPreferences(
        "navigation_v5", Context.MODE_PRIVATE
    )

    //Activityが再生成された場合
    private var restoredCheckedItemId = savedInstanceState
        ?.getInt(STATE_CHECKED_ITEM_ID, NOT_CHECKED) ?: NOT_CHECKED



    init {
        view.tag = this
        model.setOnChangedObserver(::rebuildMenu)
        scope.launchWhenResumed {
            model.collect()
        }
    }


    /**Home以外を選択している場合はHomeに戻ってtrueを返す。*/
    fun backToHome(): Boolean {
        if (view.menu.size() > 0 && model.items.isNotEmpty()
            && view.checkedItem != view.menu[0]
        ) {
            view.setCheckedItem(view.menu[0])
            onItemClick(model.items[0])
            return true
        }
        return false
    }

    fun navigate(tag: String) {
        val i = model.items.indexOfFirst { it.tag == tag }
        if (i != -1) {
            view.setCheckedItem(view.menu[i])
            onItemClick(model.items[i])
        }
    }






    fun onSaveInstanceState(state: Bundle) {
        state.putBoolean(STATE_IS_EDIT_MODE, isEditMode)
        state.putInt(STATE_CHECKED_ITEM_ID, view.checkedItem?.itemId ?: NOT_CHECKED)
    }

    companion object {
        private const val TAG = "PecaNavigationViewExtension"
        private const val STATE_IS_EDIT_MODE = "$TAG#isEditMode"
        private const val STATE_CHECKED_ITEM_ID = "$TAG#checkedItemId"
        private const val NOT_CHECKED = 0
    }
}


private const val GID_TOP = Menu.FIRST + 0
private const val GID_FAVORITE = Menu.FIRST + 1
private const val GID_HISTORY = Menu.FIRST + 2
private const val GID_YP = Menu.FIRST + 3
private const val GID_GENRE = Menu.FIRST + 4

private const val TAG_HOME = "home"
private const val TAG_NEWLY = "newly"
private const val TAG_NOTIFICATED = "notificated"
private const val TAG_HISTORY = "history"

*/

