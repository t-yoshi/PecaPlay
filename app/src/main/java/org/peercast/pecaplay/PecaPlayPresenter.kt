package org.peercast.pecaplay

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.peercast.core.lib.LibPeerCast
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.saveRecentQuery
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.worker.LoadingWorkerManager
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.descriptionOrGenre
import timber.log.Timber


class PecaPlayPresenter(
    private val viewModel: PecaPlayViewModel,
    private val appPrefs: AppPreferences,
    private val database: AppRoomDatabase,
) {
    private val a = viewModel.getApplication<Application>()
    private val workerManager = LoadingWorkerManager(a)

    /**YP読み込みを開始する*/
    fun startLoading() {
        stopLoading()

        if (appPrefs.isNotificationEnabled){
            //15分毎にYP読み込みを行う
            workerManager.enqueuePeriodic()
        } else {
            //1回限り
            workerManager.enqueueOneshot()
        }
    }

    /**YP読み込みを停止する*/
    fun stopLoading() {
        workerManager.cancel()
    }

    /**プレーヤーを起動する*/
    fun startPlayerActivity(
        streamUrl: Uri,
        isLaunchPecaViewer: Boolean,
        extras: Bundle?,
        refStartActivity: (Intent) -> Unit,
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
            val s = if (e is ActivityNotFoundException){
                "Plaese install player app."
            } else {
                e.localizedMessage
            }
            Toast.makeText(a, s, Toast.LENGTH_LONG).show()
        }
    }

    /**再生する*/
    fun startPlay(ch: YpChannel, refStartActivity: (Intent) -> Unit) {
        Timber.i("startPlay(%s)", ch)

        if (viewModel.searchQuery.isNotBlank()) {
            saveRecentQuery(a, viewModel.searchQuery)
        }
        val streamUrl = ch.stream(appPrefs.peerCastUrl)
        val extras = Bundle().also {
            it.putString(LibPeerCast.EXTRA_CONTACT_URL, ch.yp4g.url.toString())
            it.putString(LibPeerCast.EXTRA_NAME, ch.yp4g.name)
            it.putString(LibPeerCast.EXTRA_DESCRIPTION, ch.yp4g.descriptionOrGenre)
            it.putString(LibPeerCast.EXTRA_COMMENT, ch.yp4g.comment)
            //Timber.d("extras=${it.extras}")
        }

        val isLaunchPecaViewer = appPrefs.isViewerEnabled(ch.yp4g.type)
        startPlayerActivity(streamUrl, isLaunchPecaViewer, extras) {
            refStartActivity(it)
            viewModel.viewModelScope.launch {
                database.ypHistoryDao.addHistory(YpHistoryChannel(ch))
            }
        }
    }

}
