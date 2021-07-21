package org.peercast.pecaplay.yp4g.net

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.peercast.pecaplay.app.YellowPage
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import timber.log.Timber
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


private object Yp4gChannelBinderConverter : Converter<ResponseBody, List<Yp4gChannelBinder>> {
    override fun convert(body: ResponseBody): List<Yp4gChannelBinder> {
        return body.use {
            it.charStream().buffered()
                .lineSequence().take(MAX_LINE)
                .mapIndexedNotNull { n, line ->
                    try {
                        Yp4gChannelBinder.parse(line)
                    } catch (e: IllegalArgumentException) {
                        Timber.w(e, "format error: line=$n, ${e.message}")
                        null
                    }
                }
                .toList()
        }
    }

    private const val MAX_LINE = 1024
}


private object YpConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type, annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        Timber.d(" -> $type, $annotations, $retrofit")
        if (type is ParameterizedType &&
            getRawType(type) === List::class.java &&
            getParameterUpperBound(0, type) === Yp4gChannelBinder::class.java
        ) {
            return Yp4gChannelBinderConverter
        }

        return null
    }
}

fun createYp4gService(httpClient: OkHttpClient, yp: YellowPage): Yp4gService {
    return createYp4gService(httpClient, yp.url)
}

fun createYp4gService(httpClient: OkHttpClient, baseUrl: String): Yp4gService {
    return Retrofit.Builder()
        .client(httpClient)
        .baseUrl(baseUrl)
        .addConverterFactory(YpConverterFactory)
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()
        .create(Yp4gService::class.java)
}


private const val TAG = "Yp4gNet"