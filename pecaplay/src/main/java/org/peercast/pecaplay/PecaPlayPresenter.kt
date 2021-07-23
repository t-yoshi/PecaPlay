package org.peercast.pecaplay

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.peercast.core.lib.LibPeerCast
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.saveRecentQuery
import org.peercast.pecaplay.core.app.stream
import org.peercast.pecaplay.prefs.PecaPlayPreferences
import org.peercast.pecaplay.worker.LoadingWorkerManager
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.core.app.PecaViewerIntent
import timber.log.Timber


class PecaPlayPresenter(
    private val viewModel: PecaPlayViewModel,
    private val pecaPlayPrefs: PecaPlayPreferences,
    private val database: AppRoomDatabase,
) {
    private val a = viewModel.getApplication<Application>()
    private val workerManager = LoadingWorkerManager(a)

    /**YP読み込みを開始する*/
    fun startLoading() {
        stopLoading()

        if (pecaPlayPrefs.isNotificationEnabled) {
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
    fun startPlay(f: Fragment, ch: YpChannel) {
        Timber.i("startPlay(%s)", ch)

        val searchQuery = viewModel.channelFilter.params.searchQuery
        if (searchQuery.isNotBlank()) {
            saveRecentQuery(a, searchQuery)
        }
        val streamUrl = ch.stream(pecaPlayPrefs.peerCastUrl)

        val intent = if (pecaPlayPrefs.isViewerEnabled(ch.type)) {
            PecaViewerIntent.create(streamUrl, ch)
        } else {
            Intent().also {
                it.action = Intent.ACTION_VIEW
                it.setDataAndTypeAndNormalize(streamUrl, "video/${ch.type.lowercase()}")
                it.putExtra(LibPeerCast.EXTRA_CONTACT_URL, ch.url.toString())
                it.putExtra(LibPeerCast.EXTRA_NAME, ch.name)
                it.putExtra(LibPeerCast.EXTRA_DESCRIPTION, "${ch.genre} ${ch.description}")
                it.putExtra(LibPeerCast.EXTRA_COMMENT, ch.comment)
            }
        }

        try {
            f.startActivity(intent)

            viewModel.viewModelScope.launch {
                database.ypHistoryDao.addHistory(YpHistoryChannel(ch))
            }
        } catch (e: RuntimeException) {
            val s = if (e is ActivityNotFoundException) {
                "Plaese install player app."
            } else {
                e.localizedMessage
            }
            Toast.makeText(a, s, Toast.LENGTH_LONG).show()
        }
    }

}
