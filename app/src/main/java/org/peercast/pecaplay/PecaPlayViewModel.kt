package org.peercast.pecaplay

import android.app.Application
import androidx.lifecycle.*
import com.googlecode.kanaxs.KanaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.util.LiveDataUtils
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import timber.log.Timber
import java.util.*
import kotlin.properties.Delegates


/**ソースの選択*/
enum class YpChannelSource {
    /**配信中*/
    LIVE,

    /**再生履歴*/
    HISTORY
}


class PecaPlayViewModel(
    a: Application,
    private val appPrefs: AppPreferences,
    private val database: AppRoomDatabase
) : AndroidViewModel(a) {


    private val liveChannelLd = database.ypChannelDao.query()
    private val historyChannelLd = LiveDataUtils.combineLatest(
        database.ypChannelDao.query(),
        database.ypHistoryDao.query()
    ) { channels, histories ->
        histories.forEach { his ->
            //現在存在して再生可能か
            his.isEnabled = channels.any(his::equalsIdName)
        }
        histories
    }

    private val selectorLiveData = object : MediatorLiveData<List<YpChannel>>() {
        private val YpChannel.searchText: String
            get() = extTag("PecaPlayViewModel#searchText") {
                toNormalizedJapanese(yp4g.run { "$name $comment $description" })
            }!!

        var srcLiveData by Delegates.observable(EMPTY_LIVEDATA) { _, oldLd, newLd ->
            removeSource(oldLd)
            addSource(newLd) { onChanged(it) }
        }

        init {
            srcLiveData = database.ypChannelDao.query()
        }

        private fun onChanged(channels: List<YpChannel>) = viewModelScope.launch(Dispatchers.Default) {
            var l = channels.filter(selector)

            if (searchString.isNotBlank()) {
                val constraints = toNormalizedJapanese(searchString).split("[\\s　]+".toRegex())
                l = l.filter { ch ->
                    constraints.all { ch.searchText.contains(it) }
                }
            }

            when (order) {
                YpDisplayOrder.NONE -> {}
                else -> l = l.sortedWith(order.comparator)
            }

            postValue(l)
        }
    }

    /**リスト表示用*/
    val viewLiveData = Transformations.distinctUntilChanged(selectorLiveData)

    /**配信中or履歴**/
    var source = YpChannelSource.LIVE

    /**お気に入り/ジャンル等で選別するセレクタ*/
    var selector: (YpChannel) -> Boolean = { true }

    /**表示する順序*/
    var order = appPrefs.displayOrder

    /**検索窓から*/
    var searchString = ""

    /**変更を[viewLiveData]に適用する*/
    fun notifyChange() {
        Timber.d("notifyChange()")
        selectorLiveData.srcLiveData = when (source) {
            YpChannelSource.LIVE -> liveChannelLd
            YpChannelSource.HISTORY -> historyChannelLd
        }
    }


    /**通知アイコン(ベルのマーク)の有効/無効*/
    val isNotificationIconEnabled: LiveData<Boolean> = Transformations.map(
        database.favoriteDao.query()
    ) { favorites ->
        favorites.firstOrNull { it.flags.run { !isNG && isNotification } } != null
    }

    companion object {
        private val EMPTY_LIVEDATA: LiveData<out List<YpChannel>> = MutableLiveData()

        /**
         * 検索用:　小文字、半角英数、 ひらがな化
         */
        private fun toNormalizedJapanese(text: String): String =
            text.let {
                var s = it.toLowerCase(Locale.JAPANESE)
                s = KanaUtil.toHanalphCase(s)
                KanaUtil.toHiraganaCase(s)
            }
    }
}

