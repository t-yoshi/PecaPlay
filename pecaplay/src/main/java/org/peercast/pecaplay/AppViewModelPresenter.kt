package org.peercast.pecaplay

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.saveRecentQuery
import org.peercast.pecaplay.core.app.launchPecaViewer
import org.peercast.pecaplay.core.app.stream
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.worker.LoadingWorkerManager
import org.peercast.pecaplay.yp4g.YpChannel
import timber.log.Timber


class AppViewModelPresenter(
    private val viewModel: AppViewModel,
    private val appPrefs: AppPreferences,
    private val database: AppRoomDatabase,
) {
    private val a = viewModel.getApplication<Application>()
    private val workerManager = LoadingWorkerManager(a)

    /**YP読み込みを開始する*/
    fun startLoading() {
        stopLoading()

        if (appPrefs.isNotificationEnabled) {
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


    /**再生する*/
    fun startPlay(a: Activity, ch: YpChannel) {
        Timber.i("startPlay(%s)", ch)

        val searchQuery = viewModel.channelFilter.params.searchQuery
        if (searchQuery.isNotBlank()) {
            saveRecentQuery(a, searchQuery)
        }
        val streamUrl = ch.stream(appPrefs.peerCastUrl)

        if (appPrefs.isViewerEnabled(ch.type)) {
            launchPecaViewer(a, streamUrl, ch)
        } else {
            try {
                val i = Intent(Intent.ACTION_VIEW, streamUrl)
                a.startActivity(i)
            } catch (e: RuntimeException) {
                val s = when (e) {
                    is SecurityException,
                    is ActivityNotFoundException,
                    -> {
                        a.getString(R.string.please_install_player_app, ch.type)
                    }
                    else -> throw e
                }
                Toast.makeText(a, s, Toast.LENGTH_LONG).show()
                return
            }
        }

        viewModel.viewModelScope.launch {
            database.ypHistoryDao.addHistory(YpHistoryChannel(ch))
        }
    }

}
