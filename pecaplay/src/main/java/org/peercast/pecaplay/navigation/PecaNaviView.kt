package org.peercast.pecaplay.navigation

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.forEach
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
        "navigation_v8", Context.MODE_PRIVATE
    )

    // 表示/非表示をチェックボタンで切り替える。
    var isEditMode = false
        private set

    init {
        //起動時だけ選択の復元
        model.onChanged = {
            rebuildMenu()
            selectNavigationItem(prefs.getInt(KEY_SELECTED, 0))
            //2回目以降
            model.onChanged = ::rebuildMenuAndReselect
        }
    }

    private fun selectNavigationItem(item: NavigationItem, isFireEvent: Boolean = true) {
        setCheckedItem(item.itemId)
        prefs.edit {
            putInt(KEY_SELECTED, item.itemId)
        }

        menu.forEach { (it.actionView as? TextView)?.isSelected = false }
        (menu.findItem(item.itemId)?.actionView as? TextView)?.isSelected = true

        if (isFireEvent)
            onItemClick(item)
    }

    private fun selectNavigationItem(itemId: Int, isFireEvent: Boolean = true) {
        val item = model.items.firstOrNull {
            it.itemId == itemId
        } ?: model.items[0] //home

        selectNavigationItem(item, isFireEvent)
    }

    private fun addMenu(item: NavigationItem) {
        val mi = menu.add(
            item.groupId, item.itemId,
            item.groupId * 0xff + item.order, item.title
        )
        mi.setIcon(item.icon)
        mi.isVisible = isEditMode || (item.isVisible && item.key !in prefs)

        if (isEditMode) {
            mi.isEnabled = false
            inflateEditAction(mi, item)
        } else {
            mi.isCheckable = true
            mi.setOnMenuItemClickListener {
                post { selectNavigationItem(item) }
                true
            }
            if (item is BadgeableNavigationItem)
                inflateBadge(mi, item)
        }
    }

    private fun inflateEditAction(mi: MenuItem, it: NavigationItem) {
        if (it is NavigationHomeItem)
            return

        mi.actionView = inflater.inflate(R.layout.navigation_action_view_checkbox, this, false)
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
        if (isEditMode) {
            //非表示をリセット
            val miReset = menu.add(0, 0, 0xfff0, R.string.reset)
            miReset.icon = TRANSPARENT
            miReset.isEnabled = true
            miReset.setOnMenuItemClickListener {
                prefs.edit { clear() }
                isEditMode = false
                post { rebuildMenu() }
                true
            }
        }

        val miEdit = menu.add(0, 0, 0xfff1, "")
        miEdit.isEnabled = false
        miEdit.icon = TRANSPARENT
        val v = inflater.inflate(R.layout.navigation_setting_button, this, false)
        miEdit.actionView = v
        //編集モードに
        v.setOnClickListener {
            isEditMode = !isEditMode
            post { rebuildMenu() }
        }
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
        selectNavigationItem(checkedId, false)
    }

    private fun inflateBadge(mi: MenuItem, it: BadgeableNavigationItem) {
        val v = inflater.inflate(
            R.layout.navigation_action_view_badge,
            this, false
        ) as TextView
        mi.actionView = v
        v.text = it.badge
    }

    /**選択するアイテム*/
    fun navigate(predicate: (NavigationItem) -> Boolean) {
        model.items.firstOrNull(predicate)?.let {
            model.onChanged = ::rebuildMenuAndReselect
            selectNavigationItem(it)
            return
        }

        //選択したいアイテムが未作成の場合、次のイベント時に選択を試みる
        model.onChanged = {
            rebuildMenu()
            model.onChanged = ::rebuildMenuAndReselect
            model.items.firstOrNull(predicate)?.let(::selectNavigationItem)
        }
    }

    override fun onRestoreInstanceState(savedState: Parcelable) {
        val state = savedState as State
        super.onRestoreInstanceState(state.parent)
        isEditMode = state.isEditMode
    }

    override fun onSaveInstanceState(): Parcelable {
        return State(
            checkNotNull(super.onSaveInstanceState()),
            isEditMode,
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
    ) : Parcelable


    companion object {
        private const val TAG = "PecaNaviView"

        /**itemId(Int)*/
        private const val KEY_SELECTED = "#selected#"

        private val TRANSPARENT = ColorDrawable(0)
    }
}