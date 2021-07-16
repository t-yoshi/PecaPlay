package org.peercast.pecaviewer.util

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**MutableLiveData.postValue()だと
 * タイミングによっては全てのvalueが送信されない。*/
suspend fun <T> MutableLiveData<T>.asyncPost(newValue: T?) {
    withContext(Dispatchers.Main) {
        value = newValue
    }
}

