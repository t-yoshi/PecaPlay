package org.peercast.pecaplay.io

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


suspend fun <T> Call<T>.await(): Response<T> = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback<T> {
        override fun onFailure(call: Call<T>, t: Throwable) {
            if (!cont.isCancelled) {
                cont.resumeWithException(t)
            }
        }

        override fun onResponse(call: Call<T>, response: Response<T>) {
            cont.resume(response)
        }
    })

    cont.invokeOnCancellation {
        cancel()
    }
}