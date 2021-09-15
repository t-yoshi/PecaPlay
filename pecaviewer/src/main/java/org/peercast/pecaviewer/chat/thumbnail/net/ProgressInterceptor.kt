package org.peercast.pecaviewer.chat.thumbnail.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber


class ProgressInterceptor : Interceptor {
    init {
        Timber.d("ProgressInterceptor installed")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val res = chain.proceed(chain.request())

        return res.body?.let {
            res.newBuilder()
                .body(ProgressResponseBody(res.request.url, it))
                .build()
        } ?: res
    }

    private class ProgressResponseBody(
        private val url: HttpUrl,
        private val body: ResponseBody
    ) : ResponseBody(), KoinComponent {

        private val loadingEventFlow by inject<ImageLoadingEventFlow>()

        private val source = object : ForwardingSource(body.source()) {
            private var total = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                total += n.coerceAtLeast(0)
                loadingEventFlow.tryEmit(
                    ImageLoadingEvent(
                        url.toString(), total, contentLength(), n == -1L
                    )
                )
                return n
            }
        }.buffer()

        override fun contentType() = body.contentType()

        override fun contentLength() = body.contentLength()

        override fun source() = source

        override fun close() = body.close()
    }
}