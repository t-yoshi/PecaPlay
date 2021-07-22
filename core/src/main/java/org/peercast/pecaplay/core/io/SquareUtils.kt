package org.peercast.pecaplay.core.io

import androidx.annotation.WorkerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


/**Callback#onResponse内でfを実行し、その結果を返す*/
suspend fun <T> Call.await(@WorkerThread f: (Response) -> T): T {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
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

            override fun onFailure(call: Call, e: IOException) {
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

suspend fun Call.await(): String {
    return await { it.body?.string() ?: throw IOException("body is null") }
}

