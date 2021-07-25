package org.peercast.pecaplay.navigation

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.edit
import com.google.android.material.navigation.NavigationView
import kotlinx.parcelize.Parcelize
import org.peercast.pecaplay.R

class PecaNaviView : NavigationView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context,
        attrs,
        defStyleAttr)

    private val inflater = LayoutInflater.from(context)

    val model = NavigationModel(context)

    var onItemClick: (NavigationItem) -> Unit = {}

    //非表示アイテム
    private val prefs = context.getSharedPreferences(
        "navigation_v5", Context.MODE_PRIVATE
    )

    // 表示/非表示をチェックボタンで切り替える。
    var isEditMode = false
        private set

    init {
        model.onChanged = ::rebuildMenuAndReselect
        rebuildMenu()

        setCheckedItem(model.items[0].itemId)
    }

    private fun selectNavigationItem(item: NavigationItem) {
        setCheckedItem(item.itemId)
        onItemClick(item)
    }

    private fun addMenu(item: NavigationItem) {
        val mi = menu.add(
            item.groupId, item.itemId,
            item.groupId * 0xff + item.order, item.title
        )
        mi.setIcon(item.icon)
        mi.isVisible = isEditMode || item.key !in prefs
        //mi.isEnabled = isEditMode || item.isEnabled

        if (isEditMode) {
            mi.isEnabled = false
            inflateEditAction(mi, item)
        } else {
            mi.isCheckable = true
            mi.setOnMenuItemClickListener {
                selectNavigationItem(item)
                true
            }
            if (item is BadgeableNavigationItem)
                inflateNormalAction(mi, item)
        }
    }

    private fun inflateEditAction(mi: MenuItem, it: NavigationItem) {
        if (it is NavigationHomeItem)
            return

        mi.actionView = inflater.inflate(R.layout.navigation_action_view_checkbox, this, false)
        mi.setOnMenuItemClickListener {
            false
        }

        mi.actionView.findViewById<CheckBox>(R.id.vCheckbox).let { cb ->
            cb.isChecked = it.key !in prefs
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    prefs.edit { remove(it.key) }
                } else {
                    //非表示にする
                    prefs.edit { putBoolean(it.key, true) }
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
            rebuildMenu()
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

    //メニュー再生成
    private fun rebuildMenu() {
        //少なくともHomeは存在すること
        check(model.items[0].key == "home")

        val items = model.items
        if (items.isEmpty()) {
            menu.clear()
            return
        }

        menu.clear()
        items.forEach(::addMenu)

        addFooterMenuItem()
    }

    //メニュー再生成と再選択
    private fun rebuildMenuAndReselect() {
        val checkedId = checkedItem?.itemId ?: 0
        rebuildMenu()
        model.items.firstOrNull {
            it.itemId == checkedId
        }?.let(::selectNavigationItem)
    }

    private fun inflateNormalAction(mi: MenuItem, it: BadgeableNavigationItem) {
        val v = inflater.inflate(
            R.layout.navigation_action_view_badge,
            this, false
        ) as TextView
        mi.actionView = v
        v.text = it.badge
    }

    /**選択するアイテム*/
    fun navigate(predicate: (NavigationItem) -> Boolean) {
        model.items.firstOrNull(predicate)?.let(::selectNavigationItem)
    }

    override fun onRestoreInstanceState(savedState: Parcelable) {
        val state = savedState as State
        super.onRestoreInstanceState(state.parent)
        isEditMode = state.isEditMode
        state.checkedItemId?.let {
            setCheckedItem(it)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return State(
            checkNotNull(super.onSaveInstanceState()),
            isEditMode, checkedItem?.itemId
        )
    }

    fun onBackPressed(): Boolean {
        if (isEditMode) {
            isEditMode = false
            rebuildMenu()
            return true
        }

        val naviHome = model.items[0]
        //Timber.d("$homeId, ${checkedItem?.itemId}")
        if (naviHome.itemId != checkedItem?.itemId) {
            selectNavigationItem(naviHome)
            return true
        }

        return false
    }

    @Parcelize
    private data class State(
        val parent: Parcelable,
        val isEditMode: Boolean,
        val checkedItemId: Int?,
    ) : Parcelable


    companion object {
        private const val TAG = "PecaNaviView"
    }
}