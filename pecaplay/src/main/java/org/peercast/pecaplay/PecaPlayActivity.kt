package org.peercast.pecaplay

import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ActionProvider
import androidx.core.view.GravityCompat
import androidx.core.view.MenuItemCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.peercast.pecaplay.core.app.PecaPlayIntent
import org.peercast.pecaplay.databinding.PecaPlayActivityBinding
import org.peercast.pecaplay.navigation.NavigationNotifiedItem
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.prefs.SettingsActivity
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import org.peercast.pecaplay.yp4g.SpeedTestFragment
import org.peercast.pecaplay.yp4g.YpDisplayOrder
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

    private lateinit var binding: PecaPlayActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastLoadedET = savedInstanceState?.getLong(STATE_LAST_LOADED_ER_TIME) ?: 0L

        binding = DataBindingUtil.setContentView(this, R.layout.peca_play_activity)

        setTitle(R.string.app_name)

        setSupportActionBar(binding.vToolbar)
        invalidateOptionsMenu()

        binding.vDrawerLayout?.let { drawer ->
            drawerToggle = ActionBarDrawerToggle(
                this, drawer,
                R.string.drawer_open,
                R.string.drawer_close
            ).also(drawer::addDrawerListener)

            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            // AppBarLayoutのオフセット変化からドロワーの表示位置を調節する
            val defaultTopMargin =
                (binding.vNavigation.layoutParams as DrawerLayout.LayoutParams).topMargin
            binding.vAppBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                val p = binding.vNavigation.layoutParams as DrawerLayout.LayoutParams
                p.setMargins(
                    p.leftMargin,
                    defaultTopMargin + verticalOffset,
                    p.rightMargin,
                    p.bottomMargin
                )
                binding.vNavigation.layoutParams = p
            })
            drawer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        }

        binding.vNavigation.onItemClick = { item ->
            viewModel.channelFilter.navigationItem.tryEmit(item)

            binding.vDrawerLayout?.let {
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
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.message.emit("")
            }.collect()
        }

        lifecycleScope.launchWhenResumed {
            viewModel.rpcClient.filterNotNull().collect {
                //PeerCastの起動を知らせる。プレーヤーからの復帰時は表示しない。
                if (savedInstanceState == null && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                    viewModel.message.emit(
                        getString(R.string.peercast_has_started, it.rpcEndPoint.port)
                    )
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModel.notificationIconEnabled.collect {
                invalidateOptionsMenu()
            }
        }

        lifecycleScope.launch {
            loadingEvent.filterIsInstance<LoadingEvent.OnFinished>().collect {
                lastLoadedET = SystemClock.elapsedRealtime()
                Timber.i("loading finished: $lastLoadedET")
            }
        }

        binding.vNavigation.model.repository.collectIn(lifecycleScope)

        viewModel.bindService()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun removeNotification() {
        appPrefs.notificationNewlyChannelsId = emptyList()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        Timber.d("intent=$intent, extras=${intent.extras?.keySet()?.toList()}")
        if (intent.action == PecaPlayIntent.ACTION_VIEW_NOTIFIED ||
            intent.getBooleanExtra(PecaPlayIntent.EX_SELECT_NOTIFIED, false)
        ) {
            removeNotification()
            binding.vNavigation.navigate { it is NavigationNotifiedItem }
        }

        drawerToggle?.syncState()
    }

    override fun onPause() {
        super.onPause()

        if (!appPrefs.isNotificationEnabled)
            viewModel.presenter.stopLoading()
    }

    override fun onResume() {
        super.onResume()
        //前回の読み込みからN分以上経過している場合は読み込む
        if (lastLoadedET == 0L || lastLoadedET < SystemClock.elapsedRealtime() - 5 * 60_000)
            viewModel.presenter.startLoading()

        //縦長、resume時にツールバーを表示する
        if (drawerToggle != null) {
            binding.vAppBarLayout.setExpanded(true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_LAST_LOADED_ER_TIME, lastLoadedET)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        menu.findItem(R.id.menu_notification).let {
            it.isEnabled = viewModel.notificationIconEnabled.value
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
                viewModel.channelFilter.displayOrder.value = order
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
        if (binding.vNavigation.onBackPressed())
            return

        binding.vDrawerLayout?.let { v ->
            if (v.isDrawerOpen(GravityCompat.START)) {
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
            viewModel.channelFilter.searchQuery.value = newText
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
    }
}

