package org.peercast.pecaplay.util

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object LiveDataUtils {
    private class CombinedLiveData<R> : MediatorLiveData<R>() {
        private val a = ArrayList<Any>()
        private lateinit var combine: suspend (List<*>) -> R

        fun <T> addCombineSource(src: LiveData<T>): CombinedLiveData<R> {
            val index = a.size
            a.add(NONE)
            addSource(src) {
                a[index] = it as Any
                if (NONE !in a) {
                    GlobalScope.launch(Dispatchers.Default) {
                        postValue(combine(a))
                    }
                }
            }
            return this
        }

        fun setCombine(f: suspend (List<*>) -> R): CombinedLiveData<R> {
            combine = f
            return this
        }

        companion object {
            private val NONE = Any()
        }
    }


    fun <R, T0, T1> combineLatest(
        src0: LiveData<T0>,
        src1: LiveData<T1>,
        combine: suspend (T0, T1) -> R
    ): LiveData<R> {
        return CombinedLiveData<R>()
            .addCombineSource(src0)
            .addCombineSource(src1)
            .setCombine {
                @Suppress("unchecked_cast")
                combine(it[0] as T0, it[1] as T1)
            }
    }

    fun <T0, T1, T2, R> combineLatest(
        src0: LiveData<T0>, src1: LiveData<T1>, src2: LiveData<T2>,
        combine: suspend (T0, T1, T2) -> R
    ): LiveData<R> {
        return CombinedLiveData<R>()
            .addCombineSource(src0)
            .addCombineSource(src1)
            .addCombineSource(src2)
            .setCombine {
                @Suppress("unchecked_cast")
                combine(it[0] as T0, it[1] as T1, it[2] as T2)
            }
    }

    fun <R, T0, T1, T2, T3> combineLatest(
        src0: LiveData<T0>, src1: LiveData<T1>, src2: LiveData<T2>, src3: LiveData<T3>,
        combine: suspend (T0, T1, T2, T3) -> R
    ): LiveData<R> {
        return CombinedLiveData<R>()
            .addCombineSource(src0)
            .addCombineSource(src1)
            .addCombineSource(src2)
            .addCombineSource(src3)
            .setCombine {
                @Suppress("unchecked_cast")
                combine(it[0] as T0, it[1] as T1, it[2] as T2, it[3] as T3)
            }
    }
}


suspend fun <T> LiveData<T>.exAwait(): T = suspendCancellableCoroutine { cont ->
    val mainHandler = Handler(Looper.getMainLooper())
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            removeObserver(this)
            value?.let { cont.resume(it)} ?: cont.resumeWithException(NullPointerException("value is null"))
        }
    }

    cont.invokeOnCancellation {
        mainHandler.post {
            //Timber.d("Canceled")
            removeObserver(observer)
        }
    }

    mainHandler.post {
        observeForever(observer)
    }
}

