package org.peercast.pecaviewer.chat.thumbnail.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap


class LimitSizeInterceptor : Interceptor {
    init {
        Timber.d("LimitSizeInterceptor installed")
    }

    private val errorCache = ConcurrentHashMap<HttpUrl, Int>()

    override fun intercept(chain: Interceptor.Chain): Response {
        Timber.d("request=${chain.request()}")
        val maxSize = chain.request().header(X_HEADER_MAX_SIZE)?.toIntOrNull() ?: -1
        val request = chain.request().newBuilder().removeHeader(X_HEADER_MAX_SIZE).build()
        if (maxSize <= 0) {
            errorCache.remove(request.url)
            return chain.proceed(request)
        }
        errorCache[request.url]?.let {
            throw TooLargeFileException(it)
        }

        val reqHead = request.newBuilder().head().build()
        val resHead = chain.proceed(reqHead)
        if (!resHead.isSuccessful)
            return resHead

        val size = resHead.header("Content-Length")?.toIntOrNull() ?: -1
        Timber.d(" size=$size")

        if (size < 0 || size > maxSize) {
            errorCache[request.url] = size
            throw TooLargeFileException(size)
        }
        return chain.proceed(request)
    }

    companion object {
        const val X_HEADER_MAX_SIZE = "x-app-max-size"
    }
}