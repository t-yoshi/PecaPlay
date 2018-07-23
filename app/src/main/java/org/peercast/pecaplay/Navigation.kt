package org.peercast.pecaplay

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.Observer
import android.content.Context
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.support.design.widget.NavigationView
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.PopupMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.navigation_action_view_edit.view.*
import kotlinx.android.synthetic.main.navigation_action_view_normal.view.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YpIndex
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.Yp4gChannel
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap


/**ホーム*/
const val CATEGORY_HOME = "home"

/**履歴*/
const val CATEGORY_HISTORY = "history"

const val CATEGORY_FAVORITE_STARRED = "starred"

const val CATEGORY_NEWLY = "newly"

const val CATEGORY_NOTIFICATED = "notificated"


/**
 * ナビゲーションメニューの作成/イベント処理
 * */
class NavigationPresenter(
        instanceState: Bundle?,
        private val vNavigation: NavigationView,
        /**(title, category, filter)*/
        private val onNavigate: (String, String, (Yp4gChannel) -> Boolean) -> Unit) {

    private val context = vNavigation.context

    //アイテムの表示/非表示。 key=category, boolean=visible
    private val naviPrefs = context.getSharedPreferences("navigation_v5", Context.MODE_PRIVATE)

    private var checkedTag = instanceState?.getString(KEY_CHECKED_TAG) ?: ""

    private var channels = emptyList<YpIndex>()
    private val filters = HashMap<String, (Yp4gChannel) -> Boolean>()
    private val inflater = LayoutInflater.from(context)
    private val iconTint = context.getColorStateList(R.color.navigation_item)
    private var isEditMode = false
        set(value) {
            field = value
            recreateMenu()
        }

    private var genres = emptyList<String>()
    private var yellowPages = emptyList<String>()

    private var favoNotify = emptyList<Favorite>()
    private var favoStarred = emptyList<Favorite>()
    private var favoTaggable = emptyList<Favorite>()
    private var filterNotNG: (Yp4gChannel)->Boolean = { true }

    private val itemBinders = ConcurrentHashMap<@IdRes Int, MenuItemBinder>()
    private val visibleItemTags
        get() = ViewUtils.menuItems(vNavigation.menu)
                .dropLast(1) // footer-item
                .filter { it.isVisible }
                .map { itemBinders.getValue(it.itemId).category }

    private var idGenerator = MenuIdGenerator()

    private val onItemSelected = NavigationView.OnNavigationItemSelectedListener { item ->
        if (isEditMode)
            return@OnNavigationItemSelectedListener false

        vNavigation.setCheckedItem(item.itemId)
        checkedTag = itemBinders.getValue(item.itemId).category
        naviPrefs.edit().putString(KEY_CHECKED_TAG, checkedTag).apply()

        val tag = itemBinders.getValue(item.itemId).category
        onNavigate(
                item.title.toString(), tag, filters.getValue(tag)
        )
        true
    }

    fun register(db: AppRoomDatabase, lo: LifecycleOwner) {
        db.getYellowPageDao().getEnabled().observe(lo, Observer {
            yellowPages = it?.map { it.name } ?: emptyList()
            recreateMenu()
        })

        db.getFavoriteDao().get().observe(lo, Observer {
            val (favoNG, favoGood) =
                    it?.partition { it.flags.isNG } ?: Pair(emptyList(), emptyList())
            favoGood.partition {
                it.isStarred
            }.let {
                favoStarred = it.first
                favoTaggable = it.second
            }
            favoNotify = favoGood.filter { it.flags.isNotification }

            filterNotNG = { ch->
                !favoNG.any { it.matches(ch) }
            }

            recreateMenu()
        })

        db.getYpIndexDao().getGenre().observe(lo, Observer {
            val rS = Regex("[\\s\\-：:]+")
            val tm = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
            it?.let {
                it.flatMap {
                    it.split(rS)
                }
                        .filter(String::isNotEmpty)
                        .forEach {
                            tm[it] = tm.getOrElse(it, { 0 }) + 1
                        }
            }
            //Log.d(TAG, "--> $tm")
            genres = tm.entries.sortedWith(kotlin.Comparator { a, b ->
                b.value - a.value
            }).filter { it.value > 1 }.map { it.key }
            Timber.d("--> $genres")
            recreateMenu()
        })

        db.getYpIndexDao().get().observe(lo, Observer { it ->
            channels = it ?: emptyList()
            bindActionViews()
        })

        vNavigation.setNavigationItemSelectedListener(onItemSelected)
    }


    private fun bindActionViews(onFinished: () -> Unit = {}) {
        launch(UI) {
            itemBinders.entries.forEach {
                it.value.bindActionView(
                        this@NavigationPresenter,
                        vNavigation.menu.findItem(it.key))
            }
            onFinished()
        }
    }

    /**メニューの再生成*/
    private fun recreateMenu() {
        vNavigation.menu.clear()

        filters.clear()
        itemBinders.clear()
        idGenerator.reset()

        var topOrder = 0

        addItem(context.getString(R.string.navigate_all),
                R.drawable.ic_home_36dp, GID_TOP, topOrder++, tag = CATEGORY_HOME)

        addItem(context.getString(R.string.navigate_newer),
                R.drawable.ic_new_releases_36dp, GID_TOP, topOrder++, { ch ->
            !ch.isEmptyId && ch is YpIndex && (ch.ageAsMinutes < 15 || ch.numLoaded <= 2)
        }, CATEGORY_NEWLY)

        addItem(context.getString(R.string.navigate_favorite),
                R.drawable.ic_star_36dp, GID_FAVORITE, topOrder++, { ch ->
            favoStarred.any { it.matches(ch) }
        }, CATEGORY_FAVORITE_STARRED)

        if (AppPreferences(context).isNotificationEnabled) {
            addItem(context.getString(R.string.notificated),
                    R.drawable.ic_notifications_36dp, GID_FAVORITE, topOrder++, { ch ->
                favoNotify.any {
                    //ch is YpIndex && ch.numLoaded < 3 &&
                    it.matches(ch)
                }
            }, CATEGORY_NOTIFICATED)
        }

        //if (naviPrefs.getBoolean("favorite_all", true)) {
        favoTaggable.forEachIndexed { i, favo ->
            addItem(favo.name,
                    R.drawable.ic_bookmark_36dp, GID_FAVORITE, i + 10, { ch ->
                favo.matches(ch)
            })
        }
        //}

        addItem(context.getString(R.string.navigate_history),
                R.drawable.ic_history_36dp, GID_HISTORY, topOrder++,
                THROUGH, CATEGORY_HISTORY)

        //if (naviPrefs.getBoolean("yp_all", true)) {
        yellowPages.forEachIndexed { i, yp ->
            addItem(yp, R.drawable.ic_peercast, GID_YP, i + 1, {
                it.yp4g.ypName == yp
            })
        }
        //}

        if (naviPrefs.getBoolean("genre_all", true)) {
            genres.asSequence().mapIndexed { i, t ->
                addItem(t, R.drawable.ic_bookmark_border_36dp, GID_GENRE, i + 1, {
                    it.yp4g.genre.contains(t, true)
                })
            }.filter { it.isVisible }.take(5).toList()
        }

        if (isEditMode) {
            //addItem("*Favorite", R.drawable.ic_mode_edit_36dp, GID_FAVORITE, 0, category = "favorite_all")
            //addItem("*YP", R.drawable.ic_mode_edit_36dp, GID_YP, 0, category = "yp_all")
            addItem("*GENRE", R.drawable.ic_mode_edit_36dp, GID_GENRE, 0, tag = "genre_all")
        }

        addFooterMenuItem()

        bindActionViews(::restoreCheckItem)
    }

    //選択状態の復元
    private fun restoreCheckItem() {
        Timber.d("restoreCheckItem($checkedTag) ")
        if (checkedTag == "") {
            //起動時はホームを選択する
            navigate(CATEGORY_HOME)
        } else {
            itemBinders.entries.firstOrNull {
                it.value.category == checkedTag
            }?.let {
                navigate(it.value.category)
            }
        }
    }

    //設定ボタン用
    private fun addFooterMenuItem() {
        vNavigation.menu.add(GID_TOP, idGenerator.next(), 0xfff0, "").let {
            val v = inflater.inflate(R.layout.navigation_setting_button, vNavigation, false)
            it.actionView = v

            //編集モードに
            v.setOnClickListener {
                isEditMode = !isEditMode
            }

            //非表示をリセット
            v.setOnLongClickListener {
                PopupMenu(context, v).apply {
                    menu.add(R.string.reset).setOnMenuItemClickListener {
                        naviPrefs.edit().clear().apply()
                        recreateMenu()
                        true
                    }
                }.show()
                true
            }
            it.setOnMenuItemClickListener { true }
        }
    }

    @IdRes
    private fun getCheckedItemId(): Int? {
        return ViewUtils.menuItems(vNavigation.menu).firstOrNull {
            it.isChecked
        }?.itemId
    }

    fun navigate(category: String): Boolean {
        //Timber.d("navigate($category, ${itemBinders.values.map { it.category }}")
        return itemBinders.entries.firstOrNull {
            it.value.category == category
        }?.let {
            onItemSelected.onNavigationItemSelected(vNavigation.menu.findItem(it.key))
        } ?: false
    }

    fun navigateNext() {
        val i = visibleItemTags.indexOf(checkedTag)
        val t = visibleItemTags.getOrElse(i + 1) {
            visibleItemTags.first()
        }

        navigate(t)
    }

    fun navigatePrev() {
        val i = visibleItemTags.indexOf(checkedTag)
        val t = visibleItemTags.getOrElse(i - 1) {
            visibleItemTags.last()
        }
        navigate(t)
    }

    fun onBackPressed(): Boolean {
        if (isEditMode) {
            isEditMode = false
            return true
        }
        if (getCheckedItemId() != ID_HOME) {
            navigate(CATEGORY_HOME)
            return true
        }
        return false
    }

    fun onSaveInstanceState(state: Bundle) {
        state.putString(KEY_CHECKED_TAG, checkedTag)
    }

    private fun addItem(title: String, @DrawableRes icon: Int,
                        grId: Int, order: Int,
                        filter: (Yp4gChannel) -> Boolean = THROUGH,
                        tag: String = "$title (group=$grId)"): MenuItem {
        val visible = naviPrefs.getBoolean(tag, true)
        val item = vNavigation.menu.add(grId, idGenerator.next(), grId * 100 + order, title)

        val binder = when (isEditMode) {
            true -> EditMenuItemBinder(tag)
            false -> NormalItemBinder(tag)
        }
        itemBinders[item.itemId] = binder
        return item.also {
            item.isCheckable = true
            item.isChecked = false
            item.setIcon(icon)
            MenuItemCompat.setIconTintList(it, iconTint)
            item.actionView = inflater.inflate(binder.actionViewRes, vNavigation, false)
            item.isVisible = visible
            filters[tag] = { ch ->
                filter(ch) && filterNotNG(ch)
            }
        }
    }

    private abstract class MenuItemBinder(val category: String, @LayoutRes val actionViewRes: Int) {
        abstract suspend fun bindActionView(np: NavigationPresenter, item: MenuItem)
    }

    //通常時。バッジを表示する
    private inner class NormalItemBinder(category: String)
        : MenuItemBinder(category, R.layout.navigation_action_view_normal) {

        override suspend fun bindActionView(np: NavigationPresenter, item: MenuItem) {
            if (!item.isVisible || category == CATEGORY_HISTORY)
                return
            val filter = filters[category] ?: return
            val n = async {
                np.channels.count {ch->
                    filter(ch) && filterNotNG(ch)
                }
            }.await()
            if (item.groupId == GID_TOP || n > 0) {
                item.isVisible = true
                with(item.actionView.vBadge) {
                    text = if (n > 99) "+99" else "$n"
                }
            } else {
                item.isVisible = false
            }
        }
    }

    //編集モード時
    private class EditMenuItemBinder(category: String)
        : MenuItemBinder(category, R.layout.navigation_action_view_edit) {
        override suspend fun bindActionView(np: NavigationPresenter, item: MenuItem) {
            item.actionView.vCheckbox.let {
                it.isEnabled = item.itemId !in arrayOf(ID_HOME, ID_FAVORITE)
                it.isChecked = np.naviPrefs.getBoolean(category, true)
                it.setOnCheckedChangeListener { _, isChecked ->
                    np.naviPrefs.edit().run {
                        when (isChecked) {
                            true -> remove(category)
                            false -> putBoolean(category, false)
                        }
                    }.apply()
                }
            }
        }
    }

    companion object {
        private const val TAG = "NavigationPresenter"

        private const val ID_HOME = Menu.FIRST
        private const val ID_FAVORITE = ID_HOME + 1

        private const val GID_TOP = Menu.FIRST + 0
        private const val GID_FAVORITE = Menu.FIRST + 1
        private const val GID_HISTORY = Menu.FIRST + 2
        private const val GID_YP = Menu.FIRST + 3
        private const val GID_GENRE = Menu.FIRST + 4


        private val THROUGH: (Yp4gChannel) -> Boolean = { true }

        private const val KEY_CHECKED_TAG = "$TAG#checked-category"

    }
}


private class MenuIdGenerator {
    private var id = Menu.FIRST

    @IdRes
    fun next() = id++

    fun reset() {
        id = Menu.FIRST
    }
}
