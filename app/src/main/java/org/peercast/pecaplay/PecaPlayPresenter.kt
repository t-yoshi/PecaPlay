package org.peercast.pecaplay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.viewModel
import org.peercast.core.lib.LibPeerCast
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.saveRecentQuery
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.YpChannel
import timber.log.Timber
import java.util.concurrent.TimeUnit


class PecaPlayPresenter(private val a: FragmentActivity) {
    private val appPrefs by a.inject<AppPreferences>()
    private val database by a.inject<AppRoomDatabase>()
    private val viewModel: PecaPlayViewModel by a.viewModel()

    /**YP読み込みを開始する*/
    fun startLoading() {
        WorkManager.getInstance().beginUniqueWork(
            WORK_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(LoadingWorker::class.java)
                .build()
        ).enqueue()
    }

    /**YP読み込みを停止する*/
    fun stopLoading() {
        WorkManager.getInstance().cancelUniqueWork(WORK_NAME)
    }

    private fun createViewerIntent(ch: YpChannel): Intent {
        val streamUrl = ch.stream(appPrefs.peerCastUrl)

        //一般的なプレーヤー
        if (!appPrefs.isViewerEnabled(ch.yp4g.type))
            return Intent(Intent.ACTION_VIEW, streamUrl)

        //PecaViewer
        val u = streamUrl.buildUpon().scheme("pecaplay").build()

        return Intent(Intent.ACTION_VIEW, u).also {
            it.setPackage("org.peercast.pecaviewer")
            it.putExtra(PecaPlayIntent.EXTRA_IS_LAUNCH_FROM_PECAPLAY, true)
            it.putExtra(LibPeerCast.EXTRA_CONTACT_URL, ch.yp4g.url.toString())
            it.putExtra(LibPeerCast.EXTRA_NAME, ch.yp4g.name)
            it.putExtra(LibPeerCast.EXTRA_DESCRIPTION, ch.yp4g.description)
            it.putExtra(LibPeerCast.EXTRA_COMMENT, ch.yp4g.comment)
            it.putExtra(PecaPlayIntent.EXTRA_NIGHT_MODE, appPrefs.isNightMode)

            Timber.i("extras=${it.extras}")
        }
    }

    /**再生する*/
    fun startPlay(ch: YpChannel) {
        Timber.i("startPlay(%s)", ch)

        if (viewModel.searchString.isNotBlank()) {
            saveRecentQuery(a, viewModel.searchString)
        }

        try {
            a.startActivity(createViewerIntent(ch))
            viewModel.viewModelScope.launch {
                database.ypHistoryDao.addHistory(YpHistoryChannel(ch))
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(a, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    /**15分毎にYP読み込みを行う/行わない (通知用)*/
    fun setScheduledLoading(enabled: Boolean) {
        val manager = WorkManager.getInstance()
        if (enabled) {
            val req = PeriodicWorkRequest
                .Builder(LoadingWorker::class.java, 15, TimeUnit.MINUTES)
                .addTag(LOADING_WORK_TAG)

                .setBackoffCriteria(BackoffPolicy.LINEAR, 5L, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            manager.cancelAllWorkByTag(LOADING_WORK_TAG)
            manager.enqueue(req)
        } else {
            manager.cancelAllWorkByTag(LOADING_WORK_TAG)
        }
    }

    companion object {
        private const val WORK_NAME = "yp_loading_work"
        const val LOADING_WORK_TAG = "yp_loading_work_tag"
    }
}
