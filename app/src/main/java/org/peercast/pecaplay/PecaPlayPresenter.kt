package org.peercast.pecaplay

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.launch
import org.peercast.core.lib.LibPeerCast
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.saveRecentQuery
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.YpChannel
import timber.log.Timber
import java.util.concurrent.TimeUnit


class PecaPlayPresenter(
    private val viewModel: PecaPlayViewModel,
    private val appPrefs: AppPreferences,
    private val database: AppRoomDatabase
) {
    private val a = viewModel.getApplication<Application>()

    /**YP読み込みを開始する*/
    fun startLoading() {
        WorkManager.getInstance(a).beginUniqueWork(
            WORK_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(LoadingWorker::class.java)
                .build()
        ).enqueue()
    }

    /**YP読み込みを停止する*/
    fun stopLoading() {
        WorkManager.getInstance(a).cancelUniqueWork(WORK_NAME)
    }

    /**プレーヤーを起動する*/
    fun startPlayerActivity(
        streamUrl: Uri,
        isLaunchPecaViewer: Boolean,
        extras: Bundle?,
        refStartActivity: (Intent) -> Unit
    ) {
        val intent = Intent(Intent.ACTION_VIEW, streamUrl)
        intent.putExtras(extras ?: Bundle.EMPTY)

        //PecaViewer
        if (isLaunchPecaViewer) {
            intent.putExtra(PecaPlayIntent.EXTRA_IS_LAUNCH_FROM_PECAPLAY, true)
            intent.putExtra(PecaPlayIntent.EXTRA_NIGHT_MODE, appPrefs.isNightMode)
            intent.setClassName(
                "org.peercast.pecaviewer",
                "org.peercast.pecaviewer.MainActivity"
            )
        }

        try {
            refStartActivity(intent)
        } catch (e: RuntimeException) {
            Toast.makeText(a, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    /**再生する*/
    fun startPlay(ch: YpChannel, refStartActivity: (Intent) -> Unit) {
        Timber.i("startPlay(%s)", ch)

        if (viewModel.searchString.isNotBlank()) {
            saveRecentQuery(a, viewModel.searchString)
        }
        val streamUrl = ch.stream(appPrefs.peerCastUrl)
        val extras = Bundle().also {
            it.putString(LibPeerCast.EXTRA_CONTACT_URL, ch.yp4g.url.toString())
            it.putString(LibPeerCast.EXTRA_NAME, ch.yp4g.name)
            it.putString(LibPeerCast.EXTRA_DESCRIPTION, ch.yp4g.description)
            it.putString(LibPeerCast.EXTRA_COMMENT, ch.yp4g.comment)
            //Timber.d("extras=${it.extras}")
        }

        val isLaunchPecaViewer = appPrefs.isViewerEnabled(ch.yp4g.type)
        startPlayerActivity(streamUrl, isLaunchPecaViewer, extras){
            refStartActivity(it)
            viewModel.viewModelScope.launch {
                database.ypHistoryDao.addHistory(YpHistoryChannel(ch))
            }
        }
    }

    /**15分毎にYP読み込みを行う/行わない (通知用)*/
    fun setScheduledLoading(enabled: Boolean) {
        val manager = WorkManager.getInstance(a)
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
