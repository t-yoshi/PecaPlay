package org.peercast.pecaviewer.chat.thumbnail.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap


class LimitSizeInterceptor : Interceptor {
    init {
        Timber.d("LimitSizeInterceptor installed")
    }

    private val errorCache = ConcurrentHashMap<HttpUrl, TooLargeFileException>()

    override fun intercept(chain: Interceptor.Chain): Response {
        Timber.d("request=${chain.request()}")
        val maxSize = chain.request().header(X_HEADER_MAX_SIZE)?.toIntOrNull() ?: -1
        val request = chain.request().newBuilder().removeHeader(X_HEADER_MAX_SIZE).build()
        if (maxSize <= 0) {
            errorCache.remove(request.url)
            return chain.proceed(request)
        }
        errorCache[request.url]?.let { throw it }

        val response = chain.proceed(request)
        if (response.isSuccessful) {
            val body = response.body ?: throw IOException("body is null")
            val size = body.contentLength()
            Timber.d(" size=$size")

            if (size < 0 || size > maxSize) {
                throw TooLargeFileException(size).also {
                    errorCache[request.url] = it
                }
            }
        }
        return response
    }

    companion object {
        const val X_HEADER_MAX_SIZE = "x-app-max-size"
    }
}