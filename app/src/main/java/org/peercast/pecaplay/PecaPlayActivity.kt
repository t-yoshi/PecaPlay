package org.peercast.pecaplay

import android.app.NotificationManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import androidx.core.view.ActionProvider
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
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
 * |   vNavigation     |  YpChannelFragment   |
 * |     (260dp)       |                      |
 * |                   |                      |
 * */
class PecaPlayActivity : AppCompatActivity(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val appPrefs: AppPreferences by inject()
    private val viewModel: PecaPlayViewModel by viewModel()
    private var drawerToggle: ActionBarDrawerToggle? = null //縦長時のみ
    private var lastLoadedERTime = 0L

    private lateinit var vToolbar: Toolbar
    private var vDrawerLayout: DrawerLayout? = null
    private lateinit var vNavigation: NavigationView
    private lateinit var vAppBarLayout: AppBarLayout
    private lateinit var vYpChannelFragmentContainer: FragmentContainerView


    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            delegate.localNightMode = AppCompatDelegate.getDefaultNightMode()
        }
        super.onCreate(savedInstanceState)
        lastLoadedERTime = savedInstanceState?.getLong(STATE_LAST_LOADED_ER_TIME) ?: 0L

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

        PecaNavigationViewExtension(vNavigation, savedInstanceState, this) { item ->
            Timber.d("onItemClick(${item.tag})")
            viewModel.run {
                order = when (item.tag) {
                    "history" -> YpDisplayOrder.NONE
                    "notificated",
                    "newly",
                    -> {
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

        //回転後の再生成時には表示しない
        if (savedInstanceState == null) {
            lifecycleScope.launchWhenResumed {
                viewModel.rpcClient.collect { client ->
                    if (client != null) {
                        Timber.i("--> service connected!")
                        val u = Uri.parse(client.rpcEndPoint)
                        val s = getString(R.string.peercast_has_started, u.port)
                        Snackbar.make(vYpChannelFragmentContainer, s, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        get<LoadingWorkerLiveData>().observe(this,
            SnackbarObserver(vYpChannelFragmentContainer) { ev ->
                when (ev) {
                    is LoadingWorker.Event.OnException -> {
                        val s = when (ev.ex) {
                            is HttpException -> ev.ex.response()?.message()
                                ?: ev.ex.localizedSystemMessage()
                            else -> ev.ex.localizedSystemMessage()
                        }
                        return@SnackbarObserver HtmlCompat.fromHtml("<font color=red>${ev.yp.name}: $s",
                            0)
                    }
                    is LoadingWorker.Event.OnFinished -> {
                        lastLoadedERTime = SystemClock.elapsedRealtime()
                    }
                }
                null
            })

        viewModel.isNotificationIconEnabled.observe(this)  {
            if (!it) {
                viewModel.presenter.setScheduledLoading(false)
            }
            invalidateOptionsMenu()
        }

        if (appPrefs.isNotificationEnabled) {
            lastLoadedERTime = SystemClock.elapsedRealtime()
            viewModel.presenter.setScheduledLoading(true)
        }

        if (intent.hasExtra(PecaPlayIntent.EXTRA_IS_NOTIFICATED)) {
            removeNotification()
            launch {
                vNavigation.extension?.navigate("notificated")
            }
        }

        //各activity-aliasからのPecaPlayViewer起動用インテントを処理する
        if (intent.action == ViewerLaunchActivity.ACTION_LAUNCH_PECA_VIEWER &&
            intent.getLongExtra(
                ViewerLaunchActivity.EX_LAUNCH_EXPIRE,
                0
            ) > System.currentTimeMillis()
        ) {
            viewModel.presenter.startPlayerActivity(intent.data!!, true, intent.extras) {
                startActivity(it)
            }
        }

        viewModel.bindService()
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
        if (lastLoadedERTime < SystemClock.elapsedRealtime() - 10 * 60_000)
            viewModel.presenter.startLoading()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        vNavigation.extension?.onSaveInstanceState(outState)
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
                viewModel.presenter.startLoading()
            }

            R.id.menu_notification -> {
                //item.isChecked = !item.isChecked
                val enabled = !item.isChecked
                //viewModel.isNotificationIconEnabled.value = enabled
                appPrefs.isNotificationEnabled = enabled
                viewModel.presenter.setScheduledLoading(enabled)
                //schedulePresenter.resetScheduler()
                invalidateOptionsMenu()
            }

            R.id.menu_sort_age_desc,
            R.id.menu_sort_age_asc,
            R.id.menu_sort_listener_desc,
            R.id.menu_sort_listener_asc,
            -> {
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
                startActivityForResult(
                    Intent(this, SettingsActivity::class.java),
                    REQ_SETTING_ACTIVITY
                )
            }

            else -> {
                return drawerToggle.let { it != null && it.onOptionsItemSelected(item) } ||
                        super.onOptionsItemSelected(item)
            }
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.d("requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQ_SETTING_ACTIVITY && resultCode == SettingsActivity.RESULT_NIGHT_MODE_CHANGED)
            recreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        vNavigation.extension?.let {
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
        private const val REQ_SETTING_ACTIVITY = 0x1234
    }
}

/**
 * 検索窓のイベント処理。
 */
private class SearchViewEventHandler(
    private val searchView: SearchView,
    private val componentName: ComponentName,
    private val onTextChange: (String) -> Unit,
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

private class SnackbarObserver<T>(
    private val view: View,
    private val translate: (T?) -> CharSequence?,
) : Observer<T> {
    override fun onChanged(t: T?) {
        val text = translate(t) ?: return
        Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
    }
}


