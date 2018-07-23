package org.peercast.pecaplay

import android.app.Activity
import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.ViewModelProviders
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.support.v4.app.FragmentActivity
import android.widget.Toast
import kotlinx.coroutines.experimental.launch
import org.greenrobot.eventbus.Subscribe
import org.peercast.pecaplay.app.YpHistory
import org.peercast.pecaplay.app.YpIndex
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.yp4g.Yp4gChannel
import org.peercast.pecaplay.yp4g.YpOrder
import org.peercast.pecaplay.yp4g.toNormalizedJapanese
import timber.log.Timber


class PecaViewModel(a: Application) : AndroidViewModel(a) {
    val database = (a as PecaPlayApplication).database
    val appPrefs = AppPreferences(a)

    private val sourceYpIndexLiveData = database.getYpIndexDao().get()
    private val historyLiveData = MediatorLiveData<List<YpHistory>>().also {
        var channels = emptyList<YpIndex>()
        var histories = emptyList<YpHistory>()

        fun onChangedAsync() {
            launch {
                histories.forEach { his ->
                    channels.any(his::equalsIdName)
                    //現在存在して再生可能か
                    his.isPlayAvailable = channels.any(his::equalsIdName)
                }
                it.postValue(histories)
            }
        }

        it.addSource(sourceYpIndexLiveData) {
            channels = it ?: emptyList()
            onChangedAsync()
        }

        it.addSource(database.getHistoryDao().get()) {
            histories = it ?: emptyList()
            onChangedAsync()
        }
    }


    var category = ""
        private set

    fun navigate(category_: String, filter: (Yp4gChannel) -> Boolean) {
        if (category == category_)
            return
        category = category_
        ypIndexLiveData_.applyFilter(filter)
    }

    var searchText = ""
        set(value) {
            if (field == value)
                return
            field = value
            ypIndexLiveData_.onChanged()
        }

    var displayOrder: YpOrder = appPrefs.displayOrder
        set(value) {
            if (field == value)
                return
            field = value
            appPrefs.displayOrder = field
            ypIndexLiveData_.onChanged()
        }


    private inner class FilteredYpIndexLiveData : MediatorLiveData<List<Yp4gChannel>>() {
        private var naveFilter: (Yp4gChannel) -> Boolean = { true }

        fun applyFilter(filter: (Yp4gChannel) -> Boolean) {
            naveFilter = filter

            removeSource(sourceYpIndexLiveData)
            removeSource(historyLiveData)

            if (category == CATEGORY_HISTORY) {
                addSource(historyLiveData) {
                    srcChannels = it!!
                    onChanged()
                }
            } else {
                addSource(sourceYpIndexLiveData) {
                    srcChannels = it!!
                    onChanged()
                }
            }
        }

        private var srcChannels = emptyList<Yp4gChannel>()

        fun onChanged() {
            launch {
                val isHistory = srcChannels.isNotEmpty() && srcChannels[0] is YpHistory
                var filtered = srcChannels.filter(naveFilter)
                if (searchText.isNotEmpty()) {
                    val constraints = toNormalizedJapanese(searchText).split("[\\s　]+".toRegex())
                    filtered = filtered.filter { ch ->
                        constraints.all { ch.searchText.contains(it) }
                    }
                }
                if (!isHistory) {
                    val cmp = when (category) {
                        CATEGORY_NOTIFICATED,
                        CATEGORY_NEWLY -> YpOrder.AGE_ASC
                        else -> displayOrder
                    }.comparator
                    filtered = filtered.sortedWith(cmp)
                }
                postValue(filtered)
            }
        }
    }

    private val ypIndexLiveData_ = FilteredYpIndexLiveData()
    val ypIndexLiveData: LiveData<List<Yp4gChannel>> = ypIndexLiveData_


    init {
        EVENT_BUS.register(this)
    }

    @Subscribe(sticky = true)
    fun onPeerCastStart(e: OnPeerCastStart) {
        if (e.localPort > 0) {
            val msg = "PeerCast running. port=${e.localPort}"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
        }
        startLoading()
    }

    fun startLoading() {
        val c: Context = getApplication()
        c.startService(Intent().apply {
            action = PecaPlayService.ACTION_START
            setClass(c, PecaPlayService::class.java)
        })
    }

    fun cancelLoading() {
        val c: Context = getApplication()
        c.startService(Intent().apply {
            action = PecaPlayService.ACTION_CANCEL
            setClass(c, PecaPlayService::class.java)
        })
    }

    fun startPlay(a: Activity, ch: Yp4gChannel) {
        Timber.i("startPlay($ch)")
        if (searchText.isNotEmpty()) {
            saveRecentQuery(a, searchText)
        }
        val streamUrl = ch.stream(appPrefs.peerCastUrl)
        try {
            val i = Intent(Intent.ACTION_VIEW, streamUrl)
            i.putExtra(EXTRA_IS_LAUNCH_FROM_PECAPLAY, true)
            a.startActivity(i)
            launch {
                database.getHistoryDao().add(YpHistory.from(ch))
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(a, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() {
        val c = getApplication() as Context
        c.stopService(Intent(c, PecaPlayService::class.java))

        EVENT_BUS.unregister(this)
    }


    companion object {
        fun get(a: FragmentActivity) = ViewModelProviders.of(a).get(PecaViewModel::class.java)
    }
}



