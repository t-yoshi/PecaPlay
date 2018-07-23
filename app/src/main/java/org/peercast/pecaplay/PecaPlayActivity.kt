package org.peercast.pecaplay

import android.app.NotificationManager
import android.app.SearchManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.arch.lifecycle.Observer
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.support.v4.view.ActionProvider
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import kotlinx.android.synthetic.main.pacaplay_activity.*
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.prefs.SettingsActivity
import org.peercast.pecaplay.yp4g.YpOrder
import timber.log.Timber
import java.lang.ref.WeakReference

/*
 *vDrawerLayout(縦長時のみ)
 * |                   |                      |
 * |   vNavigation     |   YpIndexFragment    |
 * |     (260dp)       |                      |
 * |                   |                      |
 * */
class PecaPlayActivity : AppCompatActivity() {

    private var drawerToggle: ActionBarDrawerToggle? = null //縦長時のみ
    private lateinit var searchViewPresenter: SearchViewPresenter
    private lateinit var viewModel: PecaViewModel
    private lateinit var navigationPresenter: NavigationPresenter
    private lateinit var loaderScheduler: LoaderScheduler


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.pacaplay_activity)

        vDrawerLayout?.let {
            drawerToggle = ActionBarDrawerToggle(
                    this, it,
                    R.string.drawer_open,
                    R.string.drawer_close).apply {
                it.addDrawerListener(this)
            }
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        viewModel = PecaViewModel.get(this)

        searchViewPresenter = SearchViewPresenter(this) {
            viewModel.searchText = it
        }

        navigationPresenter = NavigationPresenter(savedInstanceState, vNavigation) { title, tag, filter ->
            viewModel.navigate(tag, filter)

            vDrawerLayout?.closeDrawers()
            if (vDrawerLayout != null) {
                supportActionBar?.title = when {
                    title.isEmpty() -> getString(R.string.app_name)
                    else -> title
                }
            } else {
                supportActionBar?.setTitle(R.string.app_name)
            }
        }
        navigationPresenter.register(viewModel.database, this)

        loaderScheduler = LoaderScheduler(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        PecaPlayApplication.of(this).bindPeerCastService()
        drawerToggle?.syncState()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.extras?.getString(EXTRA_NAVIGATION_CATEGORY)?.let {
            navigationPresenter.navigate(it)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.cancelLoading()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val iconTint = AppTheme(this).actionBarIconTint
        ViewUtils.menuItems(menu).forEach {
            MenuItemCompat.setIconTintList(it, iconTint)
        }

        menu.findItem(R.id.menu_notification).let {
            it.isEnabled = loaderScheduler.isSchedulable
            val b = viewModel.appPrefs.isNotificationEnabled
            it.setIcon(when (b) {
                true -> R.drawable.ic_notifications_active_24dp
                false -> R.drawable.ic_notifications_off_24dp
            })
            it.isChecked = b
        }

        menu.findItem(R.id.menu_sort_order).let {
            MenuItemCompat.setActionProvider(it, DisplayOrderMenuProvider())
        }

        menu.findItem(R.id.menu_search).let {
            searchViewPresenter.register(it)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                viewModel.startLoading()
            }

            R.id.menu_notification -> {
                item.isChecked = !item.isChecked
                viewModel.appPrefs.isNotificationEnabled = item.isChecked
                loaderScheduler.setScheduler()
                invalidateOptionsMenu()
            }

            R.id.menu_sort_age_desc,
            R.id.menu_sort_age_asc,
            R.id.menu_sort_listener_desc,
            R.id.menu_sort_listener_asc -> {
                val order = YpOrder.fromOrdinal(item.order)
                viewModel.displayOrder = order
                item.isChecked = true
            }

            R.id.menu_speed_test -> {
                SpeedTestFragment().show(supportFragmentManager, "SpeedTestFragment")
            }

            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
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
        if (searchViewPresenter.onBackPressed() ||
                navigationPresenter.onBackPressed())
            return

        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navigationPresenter.onSaveInstanceState(outState)
    }

    //ソート順のサブメニュー
    private inner class DisplayOrderMenuProvider : ActionProvider(this) {
        override fun hasSubMenu(): Boolean = true
        override fun onCreateActionView(): View? = null

        override fun onPrepareSubMenu(menu: SubMenu) {
            val ordinal = viewModel.appPrefs.displayOrder.ordinal
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


}

/**
 * 通知用にYP読み込みJobをスケジュールする。
 * */
private class LoaderScheduler(private val activity: AppCompatActivity) {
    private val viewModel = PecaViewModel.get(activity)
    private var existsFavoNotify = false
    private val jobScheduler = activity.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    init {
        val favDao = viewModel.database.getFavoriteDao().getEnabled()
        favDao.observe(activity, Observer<List<Favorite>> {
            val newExists = it?.firstOrNull {
                it.flags.isNotification
            } != null

            if (existsFavoNotify != newExists) {
                existsFavoNotify = newExists
                activity.invalidateOptionsMenu()
                setScheduler()
            }
        })
    }

    fun setScheduler() {
        if (viewModel.appPrefs.isNotificationEnabled && existsFavoNotify) {
            val name = ComponentName(activity, PecaPlayService::class.java)
            val ji = JobInfo.Builder(JOB_ID, name)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    //.setPersisted(true)
                    .setPeriodic(15 * 60 * 1000L) // 15分間隔以上
                    .build()
            val scheduled = jobScheduler.allPendingJobs.any { it.id == JOB_ID }
            if (!scheduled) {
                val r = jobScheduler.schedule(ji)
                Timber.d("schedule($r")
            }
        } else {
            jobScheduler.cancel(JOB_ID)
        }
    }

    /***/
    val isSchedulable get() = existsFavoNotify

    companion object {
        private const val JOB_ID = 0x01
    }
}

/**
 * 検索窓の作成とイベント処理。
 */
private class SearchViewPresenter(private val activity: PecaPlayActivity,
                                  private val onTextChange: (String) -> Unit) :
        SearchView.OnQueryTextListener,
        SearchView.OnSuggestionListener {

    private val searchView = SearchView(activity)

    init {
        val manager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager

        searchView.let {
            it.setSearchableInfo(manager.getSearchableInfo(activity.componentName))
            it.setOnSuggestionListener(this)
            it.setOnQueryTextListener(this)
        }
    }

    /**
     * 検索窓を閉じる。
     * @return 閉じた場合true
     */
    var onBackPressed: () -> Boolean = { false }
        private set

    fun register(item: MenuItem) {
        item.actionView = searchView
        val weakItem = WeakReference(item)
        onBackPressed = {
            weakItem.get().let {
                it != null && it.isActionViewExpanded && it.collapseActionView()
            }
        }
    }

    /** */
    override fun onQueryTextChange(newText: String): Boolean {
        onTextChange(newText)
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



