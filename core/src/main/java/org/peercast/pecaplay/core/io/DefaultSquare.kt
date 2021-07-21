package org.peercast.pecaplay.core.io

import android.app.Application
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import org.peercast.pecaplay.core.BuildConfig
import java.util.concurrent.TimeUnit

internal class DefaultSquare(private val a: Application) : Square {
    override val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
    .addInterceptor {
        it.proceed(
            it.request().newBuilder()
                .header("User-Agent", HTTP_USER_AGENT)
                .build()
        )
    }
    .connectionSpecs(CONNECTION_SPECS)
    .readTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
    .writeTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
    .also { b ->
        if (BuildConfig.DEBUG) {
            b.addNetworkInterceptor(HttpLoggingInterceptor().also {
                it.level = HttpLoggingInterceptor.Level.HEADERS
            })
        }
    }
    .cache(Cache(a.cacheDir, MAX_CACHE_SIZE))
    .build()

    companion object {
        private const val HTTP_USER_AGENT = "Mozilla/5.0 (Linux; U; Android) PecaPlay"
        private const val HTTP_CONNECT_TIMEOUT = 12L
        private const val HTTP_RW_TIMEOUT = 40L
        private const val MAX_CACHE_SIZE = 128 * 1024 * 1024L
        private val CONNECTION_SPECS = listOf(
            ConnectionSpec.CLEARTEXT,
            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()
        )
    }
}