package org.peercast.pecaplay.core.io

import androidx.annotation.WorkerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


/**Callback#onResponse内でfを実行し、その結果を返す*/
suspend fun <T> okhttp3.Call.await(@WorkerThread f: (okhttp3.Response) -> T): T {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                kotlin.runCatching {
                    response.use { f(it) }
                }
                    .onSuccess<T>(continuation::resume)
                    .onFailure(::onFailure)
            }

            private fun onFailure(t: Throwable) {
                if (continuation.isCancelled)
                    return
                continuation.resumeWithException(t)
            }

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onFailure(e)
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
            }
        }
    }
}

suspend fun okhttp3.Call.await(): String {
    return await { it.body?.string() ?: throw IOException("body is null") }
}


suspend fun <T> retrofit2.Call<T>.await(): retrofit2.Response<T> =
    suspendCancellableCoroutine { cont ->
        enqueue(object : retrofit2.Callback<T> {
            override fun onFailure(call: retrofit2.Call<T>, t: Throwable) {
                if (!cont.isCancelled) {
                    cont.resumeWithException(t)
                }
            }

            override fun onResponse(call: retrofit2.Call<T>, response: retrofit2.Response<T>) {
                cont.resume(response)
            }
        })

        cont.invokeOnCancellation {
            cancel()
        }
    }