package org.peercast.pecaplay

import android.app.NotificationManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.text.HtmlCompat
import androidx.core.view.ActionProvider
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import com.google.android.material.appbar.AppBarLayout
import kotlinx.android.synthetic.main.pacaplay_activity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.viewModel
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.prefs.SettingsActivity
import org.peercast.pecaplay.util.localizedSystemMessage
import org.peercast.pecaplay.yp4g.SpeedTestFragment
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import retrofit2.HttpException
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/*
 *vDrawerLayout(縦長時のみ)
 * |                   |                      |
 * |   vNavigation     |   YpChannelFragment    |
 * |     (260dp)       |                      |
 * |                   |                      |
 * */
class PecaPlayActivity : AppCompatActivity(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val appPrefs: AppPreferences by inject()
    private val viewModel: PecaPlayViewModel by viewModel()
    private val presenter = PecaPlayPresenter(this)
    private var drawerToggle: ActionBarDrawerToggle? = null //縦長時のみ
    private var lastLoadedERTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            delegate.localNightMode = AppCompatDelegate.getDefaultNightMode()
        }
        super.onCreate(savedInstanceState)
        lastLoadedERTime = savedInstanceState?.getLong(STATE_LAST_LOADED_ER_TIME) ?: 0L

        setContentView(R.layout.pacaplay_activity)

        setSupportActionBar(vToolbar)
        invalidateOptionsMenu()

        vDrawerLayout?.let { drawer ->
            drawerToggle = ActionBarDrawerToggle(
                this, drawer,
                R.string.drawer_open,
                R.string.drawer_close
            ).also(drawer::addDrawerListener)

            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            // AppBarLayoutのオフセット変化からドロワーの表示位置を調節する
            val defaultTopMargin = (vNavigation.layoutParams as DrawerLayout.LayoutParams).topMargin
            vAppBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                val p = vNavigation.layoutParams as DrawerLayout.LayoutParams
                p.setMargins(p.leftMargin, defaultTopMargin + verticalOffset, p.rightMargin, p.bottomMargin)
                vNavigation.layoutParams = p
            })
        }

        PecaNavigationViewExtension(vNavigation, savedInstanceState, this) { item ->
            Timber.d("onItemClick()")
            viewModel.run {
                order = when (item.tag) {
                    "history" -> YpDisplayOrder.NONE
                    "newly" -> {
                        removeNotification()
                        YpDisplayOrder.AGE_ASC
                    }
                    else -> appPrefs.displayOrder
                }
                selector = item.selector
                source = when (item.tag) {
                    "history" -> YpChannelSource.HISTORY
                    else -> YpChannelSource.LIVE
                }
                notifyChange()
            }

            vDrawerLayout?.let {
                it.closeDrawers()
                supportActionBar?.title = when {
                    item.title.isEmpty() -> getString(R.string.app_name)
                    else -> item.title
                }
            } ?: run {
                supportActionBar?.setTitle(R.string.app_name)
            }
        }


        get<PeerCastServiceEventLiveData>().observe(this, Observer { ev ->
            when {
                ev is PeerCastServiceBindEvent.OnBind && ev.localServicePort > 0 -> {
                    if (savedInstanceState == null) {
                        //回転後の再生成時には表示しない
                        val msg = "PeerCast running. port=${ev.localServicePort}"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        get<LoadingWorkerLiveData>().observe(this, Observer { ev ->
            when (ev) {
                is LoadingWorker.Event.OnException -> {
                    val s = when (ev.ex) {
                        is HttpException -> ev.ex.response().message()
                        else -> ev.ex.localizedSystemMessage()
                    }
                    val msg = HtmlCompat.fromHtml("<font color=red>${ev.yp.name}: $s", 0)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                is LoadingWorker.Event.OnFinished -> {
                    lastLoadedERTime = SystemClock.elapsedRealtime()
                }
            }
        })

        viewModel.isNotificationIconEnabled.observe(this, Observer {
            if (!it) {
                presenter.setScheduledLoading(false)
            }
            invalidateOptionsMenu()
        })

        onCreateOrNewIntent()
    }

    private fun removeNotification(){
        appPrefs.notificationNewlyChannelsId = emptyList()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        onCreateOrNewIntent()
    }

    private fun onCreateOrNewIntent(){
        if (intent.hasExtra(PecaPlayIntent.EXTRA_IS_NEWLY)) {
            removeNotification()
        }
        if (appPrefs.isNotificationEnabled) {
            lastLoadedERTime = SystemClock.elapsedRealtime()
            presenter.setScheduledLoading(true)
        }
    }


    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        drawerToggle?.syncState()
    }

    override fun onPause() {
        super.onPause()
        presenter.stopLoading()
    }

    override fun onResume() {
        super.onResume()
        //前回の読み込みからN分以上経過している場合は読み込む
        if (lastLoadedERTime < SystemClock.elapsedRealtime() - 10 * 60_000)
            presenter.startLoading()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        vNavigation.extension.onSaveInstanceState(outState)
        outState.putLong(STATE_LAST_LOADED_ER_TIME, lastLoadedERTime)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        menu.findItem(R.id.menu_notification).let {
            it.isEnabled =
                viewModel.isNotificationIconEnabled.value == true  // #schedulePresenter.isNotificationIconEnabled
            val b = appPrefs.isNotificationEnabled
            it.setIcon(
                when (b) {
                    true -> R.drawable.ic_notifications_active_24dp
                    false -> R.drawable.ic_notifications_off_24dp
                }
            )
            it.isChecked = b
        }

        menu.findItem(R.id.menu_sort_order).let {
            MenuItemCompat.setActionProvider(it, DisplayOrderMenuProvider())
        }

        menu.findItem(R.id.menu_search).let { mi ->
            (mi.actionView as SearchView?)?.let { v ->
                SearchViewEventHandler(v, componentName) { text ->
                    Timber.d("onTextChange(%s)", text)
                    viewModel.searchString = text
                    viewModel.notifyChange()
                }
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                presenter.startLoading()
            }

            R.id.menu_notification -> {
                //item.isChecked = !item.isChecked
                val enabled = !item.isChecked
                //viewModel.isNotificationIconEnabled.value = enabled
                appPrefs.isNotificationEnabled = enabled
                presenter.setScheduledLoading(enabled)
                //schedulePresenter.resetScheduler()
                invalidateOptionsMenu()
            }

            R.id.menu_sort_age_desc,
            R.id.menu_sort_age_asc,
            R.id.menu_sort_listener_desc,
            R.id.menu_sort_listener_asc -> {
                val order = YpDisplayOrder.fromOrdinal(item.order)
                viewModel.order = order
                viewModel.notifyChange()

                appPrefs.displayOrder = order
                item.isChecked = true
            }

            R.id.menu_speed_test -> {
                SpeedTestFragment().show(supportFragmentManager, "SpeedTestFragment")
            }

            R.id.menu_favorite -> {
                SettingsActivity.startFavoritePrefs(this)
            }

            R.id.menu_settings -> {
                SettingsActivity.startGeneralPrefs(this)
            }

            else -> {
                return drawerToggle.let { it != null && it.onOptionsItemSelected(item) } ||
                        super.onOptionsItemSelected(item)
            }
        }

        return true
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        vNavigation.extension.let {
            if (it.isEditMode) {
                it.isEditMode = false
                return
            }
            if (it.backToHome())
                return
        }

        vDrawerLayout?.let { v ->
            if (v.isDrawerOpen(Gravity.LEFT)) {
                v.closeDrawers()
                return
            }
        }

        super.onBackPressed()
    }

    //ソート順のサブメニュー
    private inner class DisplayOrderMenuProvider : ActionProvider(this) {
        override fun hasSubMenu(): Boolean = true
        override fun onCreateActionView(): View? = null

        override fun onPrepareSubMenu(menu: SubMenu) {
            val ordinal = appPrefs.displayOrder.ordinal
            (0 until menu.size()).map {
                menu.getItem(it).apply {
                    isCheckable = false
                }
            }.getOrNull(ordinal)?.run {
                isCheckable = true
                isChecked = true
            }
        }
    }

    companion object {
        private const val STATE_LAST_LOADED_ER_TIME = "lastLoadedERTime"
    }
}

/**
 * 検索窓のイベント処理。
 */
private class SearchViewEventHandler(
    private val searchView: SearchView,
    private val componentName: ComponentName,
    private val onTextChange: (String) -> Unit
) :
    SearchView.OnQueryTextListener,
    SearchView.OnSuggestionListener {

    init {
        val manager = searchView.context.getSystemService(Context.SEARCH_SERVICE) as SearchManager

        searchView.let {
            it.setSearchableInfo(manager.getSearchableInfo(componentName))
            it.setOnSuggestionListener(this)
            it.setOnQueryTextListener(this)
        }
    }

    private var oldText = ""
    /** */
    override fun onQueryTextChange(newText: String): Boolean {
        if (newText != oldText)
            onTextChange(newText)
        oldText = newText
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    override fun onSuggestionSelect(position: Int): Boolean {
        return false
    }

    override fun onSuggestionClick(position: Int): Boolean {
        val cursor = searchView.suggestionsAdapter.getItem(position) as Cursor
        // Log.d(TAG, "onSuggestionClick: "+cursor);
        searchView.setQuery(cursor.getString(2), false)
        return true
    }
}



