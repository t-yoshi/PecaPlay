package org.peercast.pecaviewer.util

import android.app.Application
import androidx.annotation.WorkerThread
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.peercast.pecaviewer.BuildConfig
import org.peercast.pecaviewer.chat.thumbnail.LimitSizeInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ISquareHolder {
    val okHttpClient: OkHttpClient
    val moshi: Moshi
}

class DefaultSquareHolder(private val a: Application) : ISquareHolder {
    private val cacheDir = File(a.filesDir, "okhttp")

    override val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor {
            it.proceed(
                it.request().newBuilder()
                    .header("User-Agent", HTTP_USER_AGENT)
                    .build()
            )
        }
        .addInterceptor(LimitSizeInterceptor())
        .connectionSpecs(connectionSpecs)
        //.dispatcher(Dispatcher(AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService))
        .readTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)

        .also { b ->
            if (BuildConfig.DEBUG) {
                b.addNetworkInterceptor(HttpLoggingInterceptor().also {
                    it.level = HttpLoggingInterceptor.Level.HEADERS
                })
            }
        }
        .cache(Cache(cacheDir, MAX_CACHE_SIZE))
        .build()

    override val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val HTTP_USER_AGENT = "Mozilla/5.0 (Linux; U; Android) PecaPlay"
        private const val HTTP_CONNECT_TIMEOUT = 12L
        private const val HTTP_RW_TIMEOUT = 40L
        private const val MAX_CACHE_SIZE = 2 * 1024 * 1024L
        private val connectionSpecs = listOf(
            ConnectionSpec.CLEARTEXT,
            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()
        )
    }
}


/**Callback#onResponse内でfを実行し、その結果を返す*/
suspend fun <T> Call.runAwait(@WorkerThread f: (Response) -> T): T {
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

suspend fun <T> Call.await(): String {
    return runAwait { it.body?.string() ?: throw IOException("body is null") }
}
