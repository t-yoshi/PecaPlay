package org.peercast.pecaplay

import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import androidx.core.view.ActionProvider
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.peercast.pecaplay.chanlist.filter.YpChannelSource
import org.peercast.pecaplay.core.app.PecaPlayIntent
import org.peercast.pecaplay.core.io.localizedSystemMessage
import org.peercast.pecaplay.navigation.*
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.prefs.SettingsActivity
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import org.peercast.pecaplay.yp4g.SpeedTestFragment
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import retrofit2.HttpException
import timber.log.Timber

/*
 *vDrawerLayout(縦長時のみ)
 * |                   |                      |
 * |   vNavigation     |  YpChannelFragment   |
 * |     (260dp)       |                      |
 * |                   |                      |
 * */
class PecaPlayActivity : AppCompatActivity() {

    private val appPrefs: AppPreferences by inject()
    private val viewModel: AppViewModel by viewModel()
    private val loadingEvent by inject<LoadingEventFlow>()
    private var drawerToggle: ActionBarDrawerToggle? = null //縦長時のみ
    private var lastLoadedET = 0L

    private lateinit var vToolbar: Toolbar
    private var vDrawerLayout: DrawerLayout? = null
    private lateinit var vNavigation: PecaNaviView
    private lateinit var vAppBarLayout: AppBarLayout
    private lateinit var vYpChannelFragmentContainer: FragmentContainerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastLoadedET = savedInstanceState?.getLong(STATE_LAST_LOADED_ER_TIME) ?: 0L

        setContentView(R.layout.pacaplay_activity)
        vToolbar = findViewById(R.id.vToolbar)
        vDrawerLayout = findViewById(R.id.vDrawerLayout)
        vNavigation = findViewById(R.id.vNavigation)
        vAppBarLayout = findViewById(R.id.vAppBarLayout)
        vYpChannelFragmentContainer = findViewById(R.id.vYpChannelFragmentContainer)
        setTitle(R.string.app_name)

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
                p.setMargins(
                    p.leftMargin,
                    defaultTopMargin + verticalOffset,
                    p.rightMargin,
                    p.bottomMargin
                )
                vNavigation.layoutParams = p
            })
        }


        vNavigation.onItemClick = { item ->
            viewModel.channelFilter.apply {
                displayOrder = when (item) {
                    is NavigationHistoryItem -> YpDisplayOrder.NONE
                    is NavigationNotifiedItem,
                    is NavigationNewItem,
                    -> {
                        removeNotification()
                        YpDisplayOrder.AGE_ASC
                    }
                    else -> appPrefs.displayOrder
                }
                selector = item.selector
                source = when (item) {
                    is NavigationHistoryItem -> YpChannelSource.HISTORY
                    else -> YpChannelSource.LIVE
                }
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

        lifecycleScope.launchWhenResumed {
            viewModel.message.filter { it.isNotEmpty() }.onEach {
                Snackbar.make(vYpChannelFragmentContainer, it, Snackbar.LENGTH_LONG).show()
                viewModel.message.value = ""
            }.collect()
        }

        lifecycleScope.launchWhenResumed {
            viewModel.existsNotification.collect {
                invalidateOptionsMenu()
            }
        }

        lifecycleScope.launch {
            loadingEvent.filterIsInstance<LoadingEvent.OnFinished>().collect {
                lastLoadedET = SystemClock.elapsedRealtime()
                Timber.i("loading finished: $lastLoadedET")
            }
        }

        onNewIntent(intent)

        viewModel.bindService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Timber.d("intent=$intent")
        when (intent.action){
            PecaPlayIntent.ACTION_VIEW_NOTIFIED -> {
                removeNotification()
                vNavigation.navigate { it is NavigationNotifiedItem }
            }
        }

        vNavigation.model.repository.collectIn(lifecycleScope)
    }

    private fun removeNotification() {
        appPrefs.notificationNewlyChannelsId = emptyList()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        drawerToggle?.syncState()
    }

    override fun onPause() {
        super.onPause()
        viewModel.presenter.stopLoading()
    }

    override fun onResume() {
        super.onResume()
        //前回の読み込みからN分以上経過している場合は読み込む
        if (lastLoadedET == 0L || lastLoadedET < SystemClock.elapsedRealtime() - 5 * 60_000)
            viewModel.presenter.startLoading()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_LAST_LOADED_ER_TIME, lastLoadedET)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        menu.findItem(R.id.menu_notification).let {
            it.isEnabled = viewModel.existsNotification.value
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
            (mi.actionView as? SearchView)?.let(::SearchViewEventHandler)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                viewModel.presenter.startLoading()
            }

            R.id.menu_notification -> {
                //item.isChecked = !item.isChecked
                val enabled = !item.isChecked
                //viewModel.isNotificationIconEnabled.value = enabled
                appPrefs.isNotificationEnabled = enabled
                if (enabled)
                    viewModel.presenter.startLoading()
                else
                    viewModel.presenter.stopLoading()

                invalidateOptionsMenu()
            }

            R.id.menu_sort_age_desc,
            R.id.menu_sort_age_asc,
            R.id.menu_sort_listener_desc,
            R.id.menu_sort_listener_asc,
            -> {
                val order = YpDisplayOrder.fromOrdinal(item.order)
                viewModel.channelFilter.apply {
                    displayOrder = order
                }
                appPrefs.displayOrder = order
                item.isChecked = true
            }

            R.id.menu_speed_test -> {
                SpeedTestFragment().show(supportFragmentManager, "SpeedTestFragment")
            }

            R.id.menu_favorite -> {
                startActivity(
                    Intent(
                        SettingsActivity.ACTION_FAVORITE_PREFS,
                        null,
                        this,
                        SettingsActivity::class.java
                    )
                )
            }

            R.id.menu_settings -> {
                startActivity(
                    Intent(this, SettingsActivity::class.java),
                )
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
        if (vNavigation.onBackPressed())
            return

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

    /**
     * 検索窓のイベント処理。
     */
    private inner class SearchViewEventHandler(
        private val searchView: SearchView,
    ) : SearchView.OnQueryTextListener,
        SearchView.OnSuggestionListener {

        init {
            val sm = getSystemService(Context.SEARCH_SERVICE) as SearchManager
            searchView.let {
                it.setSearchableInfo(sm.getSearchableInfo(componentName))
                it.setOnSuggestionListener(this)
                it.setOnQueryTextListener(this)
            }
        }

        override fun onQueryTextChange(newText: String): Boolean {
            viewModel.channelFilter.apply {
                searchQuery = newText
            }
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

    companion object {
        private const val STATE_LAST_LOADED_ER_TIME = "lastLoadedERTime"

        /**通知済みの新着を表示する*/
        const val EX_IS_NOTIFIED = "notified"
    }
}

