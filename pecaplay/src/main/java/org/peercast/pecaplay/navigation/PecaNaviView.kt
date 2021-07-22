package org.peercast.pecaplay.navigation

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.get
import com.google.android.material.navigation.NavigationView
import kotlinx.parcelize.Parcelize
import org.peercast.pecaplay.R
import kotlin.properties.Delegates

class PecaNaviView : NavigationView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context,
        attrs,
        defStyleAttr)

    private val savedInstanceState = Bundle()

    private val inflater = LayoutInflater.from(context)

    var model = NavigationModel(context)

    init {
        model.onChanged = { rebuildMenu() }
        rebuildMenu()
    }

    var onItemClick: ((NavigationItem) -> Unit)? = null

    //非表示アイテム
    private val prefs = context.getSharedPreferences(
        "navigation_v5", Context.MODE_PRIVATE
    )

    var isEditMode by Delegates.observable(
        savedInstanceState.getBoolean(STATE_IS_EDIT_MODE)
    ) { _, _, _ ->
        rebuildMenu()
    }

    private fun addMenu(item: NavigationItem) {
        val mi = menu.add(
            item.groupId, item.itemId,
            item.groupId * 0xff + item.order, item.title
        )
        mi.setIcon(item.icon)
        mi.isVisible = isEditMode || (item.isVisible && item.tag !in prefs)

        if (isEditMode) {
            mi.isEnabled = false
            inflateEditAction(mi, item)
        } else {
            mi.isCheckable = true
            mi.setOnMenuItemClickListener {
                setCheckedItem(it)
                onItemClick?.invoke(item)
                false
            }
            inflateNormalAction(mi, item)
        }
    }

    private fun inflateEditAction(mi: MenuItem, it: NavigationItem) {
        if (it.tag == TAG_HOME)
            return

        mi.actionView = inflater.inflate(R.layout.navigation_action_view_checkbox, this, false)
        mi.setOnMenuItemClickListener {
            false
        }

        mi.actionView.findViewById<CheckBox>(R.id.vCheckbox).let { cb ->
            cb.isChecked = it.tag !in prefs
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    prefs.edit { remove(it.tag) }
                } else {
                    //NG
                    prefs.edit { putBoolean(it.tag, true) }
                }
            }
        }
    }

    //設定ボタン用
    private fun addFooterMenuItem() {
        val mi = menu.add(0, 0, 0xfff0, "")
        mi.isEnabled = false
        val v = inflater.inflate(R.layout.navigation_setting_button, this, false)
        mi.actionView = v

        //編集モードに
        v.setOnClickListener {
            isEditMode = !isEditMode
        }

        //非表示をリセット
        v.setOnLongClickListener {
            android.widget.PopupMenu(context, v).apply {
                menu.add(R.string.reset).setOnMenuItemClickListener {
                    prefs.edit { clear() }
                    rebuildMenu()
                    true
                }
            }.show()
            true
        }
        mi.setOnMenuItemClickListener { true }
    }

    private fun rebuildMenu() {
        val items = model.items
        if (items.isEmpty()) {
            menu.clear()
            return
        }

        //チェックされたアイテムの保存
        val checkedItemId = checkedItem?.itemId ?: savedInstanceState.getInt(
            STATE_CHECKED_ITEM_ID, NOT_CHECKED
        )

        menu.clear()
        items.forEach(::addMenu)

        addFooterMenuItem()

        if (isEditMode) {
            //addMenu(NavigationItem("**genre", GID_GENRE, 0, R.drawable.ic_bookmark_border_36dp, YpIndexQuery.UNDEFINED, "genre_all"))
        }

        if (checkedItemId == NOT_CHECKED) {
            //起動時: HOMEを選択しイベントを飛ばす
            if (items.isNotEmpty()) {
                setCheckedItem(menu[0].itemId)
                onItemClick?.invoke(items[0])
            }
        } else {
            setCheckedItem(checkedItemId)
        }
    }

    private fun inflateNormalAction(mi: MenuItem, it: NavigationItem) {
        val v = inflater.inflate(
            R.layout.navigation_action_view_badge,
            this, false
        ) as TextView
        mi.actionView = v
        v.text = it.badge
    }

    override fun onRestoreInstanceState(savedState: Parcelable) {
        val state = savedState as State
        super.onRestoreInstanceState(state.parent)
        savedInstanceState.putAll(state.instanceState)
    }

    override fun onSaveInstanceState(): Parcelable {
        savedInstanceState.putInt(STATE_CHECKED_ITEM_ID, checkedItem?.itemId ?: NOT_CHECKED)
        return State(
            checkNotNull(super.onSaveInstanceState()),
            savedInstanceState
        )
    }

    @Parcelize
    private data class State(
        val parent: Parcelable,
        val instanceState: Bundle,
    ) : Parcelable


    companion object {
        //グループ
        const val GID_TOP = Menu.FIRST + 0
        const val GID_FAVORITE = Menu.FIRST + 1
        const val GID_HISTORY = Menu.FIRST + 2
        const val GID_YP = Menu.FIRST + 3
        const val GID_GENRE = Menu.FIRST + 4

        const val TAG_HOME = "home"
        const val TAG_NEWLY = "newly"
        const val TAG_NOTIFICATED = "notificated"
        const val TAG_HISTORY = "history"

        private const val TAG = "PecaNaviView"
        private const val STATE_IS_EDIT_MODE = "$TAG#isEditMode"
        private const val STATE_CHECKED_ITEM_ID = "$TAG#checkedItemId"
        private const val NOT_CHECKED = 0


    }


}