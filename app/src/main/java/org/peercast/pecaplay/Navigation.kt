package org.peercast.pecaplay

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import androidx.core.view.get
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.navigation_action_view_checkbox.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.util.LiveDataUtils
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpChannelSelector
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList
import kotlin.properties.Delegates


//
// ナビゲーションメニューの拡張
//

val NavigationView.extension: PecaNavigationViewExtension?
    get() = tag as PecaNavigationViewExtension?

class NavigationItem(
    val title: CharSequence,
    /**MenuItemのgroupId*/
    val groupId: Int,

    /**グループ内での表示順*/
    val order: Int,

    @DrawableRes val icon: Int,

    val selector: YpChannelSelector,

    tag_: String? = null
) {
    /**非表示プリファレンスのキー*/
    val tag = tag_ ?: "$title groupId=$groupId"

    /**バッジ用のテキスト*/
    var badge: String = ""

    var isVisible = true

    override fun toString(): String = tag
}

interface INavigationModel {
    val items: List<NavigationItem>
    fun setOnChangedObserver(observer: () -> Unit)
    fun setLifecycleOwner(owner: LifecycleOwner)
}


class PecaNavigationViewExtension(
    private val view: NavigationView,
    savedInstanceState: Bundle?,
    owner: LifecycleOwner,
    private val onItemClick: (NavigationItem) -> Unit
) {

    private val inflater = LayoutInflater.from(view.context)
    private val model: INavigationModel = NavigationModelImpl(view.context)

    private val invisiblePrefs = view.context.getSharedPreferences(
        "navigation_v5", Context.MODE_PRIVATE
    )

    //Activityが再生成された場合
    private var restoredCheckedItemId = savedInstanceState
        ?.getInt(STATE_CHECKED_ITEM_ID, NOT_CHECKED) ?: NOT_CHECKED

    var isEditMode by Delegates.observable(
        savedInstanceState?.getBoolean(STATE_IS_EDIT_MODE) ?: false
    ) { _, _, _ ->
        rebuildMenu()
    }

    init {
        view.tag = this
        model.setOnChangedObserver(::rebuildMenu)
        model.setLifecycleOwner(owner)
    }

    private fun rebuildMenu() {
        val items = model.items
        if (items.isEmpty()) {
            view.menu.clear()
            return
        }

        //チェックされたアイテムの保存
        val checkedItemId = view.checkedItem?.itemId ?: restoredCheckedItemId

        view.menu.clear()
        items.forEach(::addMenu)

        addFooterMenuItem()

        if (isEditMode) {
            //addMenu(NavigationItem("**genre", GID_GENRE, 0, R.drawable.ic_bookmark_border_36dp, YpIndexQuery.UNDEFINED, "genre_all"))
        }

        if (checkedItemId == NOT_CHECKED) {
            //起動時: HOMEを選択しイベントを飛ばす
            view.setCheckedItem(view.menu[0].itemId)
            onItemClick(items[0])
        } else {
            view.setCheckedItem(checkedItemId)
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

    private fun addMenu(item: NavigationItem) {
        val mi = view.menu.add(
            item.groupId, item.tag.hashCode(),
            item.groupId * 0xff + item.order, item.title
        )
        mi.setIcon(item.icon)
        mi.isVisible = isEditMode || (item.isVisible && item.tag !in invisiblePrefs)

        if (isEditMode) {
            mi.isEnabled = false
            inflateEditAction(mi, item)
        } else {
            mi.isCheckable = true
            mi.setOnMenuItemClickListener {
                view.setCheckedItem(it)
                onItemClick(item)
                false
            }
            inflateNormalAction(mi, item)
        }
    }

    private fun inflateNormalAction(mi: MenuItem, it: NavigationItem) {
        val v = inflater.inflate(
            R.layout.navigation_action_view_badge,
            view, false
        ) as TextView
        mi.actionView = v
        v.text = it.badge
    }

    private fun inflateEditAction(mi: MenuItem, it: NavigationItem) {
        if (it.tag == TAG_HOME)
            return

        mi.actionView = inflater.inflate(R.layout.navigation_action_view_checkbox, view, false)
        mi.setOnMenuItemClickListener {
            false
        }

        mi.actionView.vCheckbox.let { cb ->
            cb.isChecked = it.tag !in invisiblePrefs
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    invisiblePrefs.edit { remove(it.tag) }
                } else {
                    //NG
                    invisiblePrefs.edit { putBoolean(it.tag, true) }
                }
            }
        }
    }

    //設定ボタン用
    private fun addFooterMenuItem() {
        val mi = view.menu.add(0, 0, 0xfff0, "")
        mi.isEnabled = false
        val v = LayoutInflater.from(view.context)
            .inflate(R.layout.navigation_setting_button, view, false)
        mi.actionView = v

        //編集モードに
        v.setOnClickListener {
            isEditMode = !isEditMode
        }

        //非表示をリセット
        v.setOnLongClickListener {
            android.widget.PopupMenu(view.context, v).apply {
                menu.add(R.string.reset).setOnMenuItemClickListener {
                    invisiblePrefs.edit { clear() }
                    rebuildMenu()
                    true
                }
            }.show()
            true
        }
        mi.setOnMenuItemClickListener { true }
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


private class NavigationModelImpl(private val c: Context) : INavigationModel, KoinComponent {
    private val database by inject<AppRoomDatabase>()
    private val appPrefs by inject<AppPreferences>()

    override var items = emptyList<NavigationItem>()


    private fun parseGenre(channels: List<YpChannel>): List<String> {
        val rS = Regex("[\\s\\-：:]+")
        val tm = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        channels.flatMap { it.yp4g.genre.split(rS) }
            .filter { it.isNotBlank() }
            .forEach {
                tm[it] = tm.getOrElse(it) { 0 } + 1
            }
        val g = tm.entries.sortedWith(kotlin.Comparator { a, b ->
            b.value - a.value
        }).filter { it.value > 1 }.map { it.key }
        //Timber.d("--> $g")
        return g
    }

    private var onChangedObserver: () -> Unit = {}

    override fun setOnChangedObserver(observer: () -> Unit) {
        onChangedObserver = observer
    }

    override fun setLifecycleOwner(owner: LifecycleOwner) {
        LiveDataUtils.combineLatest(
            database.yellowPageDao.query(),
            database.favoriteDao.query(),
            database.ypChannelDao.query(),
            ::toNavigationItem
        ).observe(owner, Observer {
            items = it
            onChangedObserver()
        })
    }

    private suspend fun toNavigationItem(
        yellowPages: List<YellowPage>,
        favorites: List<Favorite>,
        channels: List<YpChannel>
    ): List<NavigationItem> {
        Timber.d("onUpdate()")
        val items = ArrayList<NavigationItem>(30)

        var topOrder = 0

        items += NavigationItem(
            c.getString(R.string.navigate_all),
            GID_TOP, topOrder++,
            R.drawable.ic_home_36dp,
            { true }, TAG_HOME
        )

        items += NavigationItem(
            c.getString(R.string.navigate_newer),
            GID_TOP, topOrder++,
            R.drawable.ic_new_releases_36dp,
            { ch ->
                !ch.isEmptyId && ch is YpLiveChannel && (ch.numLoaded <= 2)
            }//ch.ageAsMinutes < 15 ||
        )

        val stars = favorites.filter { it.isStar && !it.flags.isNG }
        items += NavigationItem(
            c.getString(R.string.navigate_favorite),
            GID_FAVORITE, topOrder++,
            R.drawable.ic_star_36dp,
            { ch ->
                stars.any { it.matches(ch) }
            })

        val favoNotify = favorites.filter { it.flags.isNotification && !it.flags.isNG }
        if (appPrefs.isNotificationEnabled) {
            items += NavigationItem(
                c.getString(R.string.notificated),
                GID_FAVORITE, topOrder++,
                R.drawable.ic_notifications_36dp,
                { ch ->
                    favoNotify.any {
                        //ch is YpIndex && ch.numLoaded < 3 &&
                        it.matches(ch)
                    }
                },
                TAG_NOTIFICATED
            )
        }

        val favoTaggable = favorites.filter { !it.isStar && !it.flags.isNG }
        favoTaggable.forEachIndexed { i, favo ->
            items += NavigationItem(
                favo.name,
                GID_FAVORITE, i + 10,
                R.drawable.ic_bookmark_36dp,
                { ch ->
                    favo.matches(ch)
                })
        }


        items += NavigationItem(
            c.getString(R.string.navigate_history),
            GID_HISTORY, topOrder++,
            R.drawable.ic_history_36dp,
            { true },
            TAG_HISTORY
        )

        yellowPages.forEachIndexed { i, yp ->
            items += NavigationItem(
                yp.name,
                GID_YP, i + 1,
                R.drawable.ic_peercast,
                {
                    it.yp4g.ypName == yp.name
                })
        }

        parseGenre(channels).take(6).mapIndexed { i, t ->
            items += NavigationItem(
                t, GID_GENRE, i + 1,
                R.drawable.ic_bookmark_border_36dp,
                {
                    it.yp4g.genre.contains(t, true)
                })
        }

        val badgeInvisibleItems = listOf(TAG_HOME, TAG_HISTORY)

        withContext(Dispatchers.Default) {
            items.filter { it.tag !in badgeInvisibleItems }.forEach {
                val n = channels.count(it.selector)
                it.badge = when {
                    n > 99 -> "99+"
                    n > 0 -> "$n"
                    else -> {
                        it.isVisible = false
                        ""
                    }
                }
            }
        }

        return items
    }

}

