package org.peercast.pecaplay.util

import android.content.res.Resources
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ソケット関連のIOExceptionからローカライズされたOSのエラーメッセージを得る。
 * @see FileNotFoundException
 * @see SocketTimeoutException
 * @see UnknownHostException
 * */
fun IOException.localizedSystemMessage(): String {
    val name = when (this) {
        is FileNotFoundException -> "httpErrorFileNotFound"
        is SocketTimeoutException -> "httpErrorTimeout"
        is UnknownHostException -> "httpErrorLookup"
        else -> null
    }
    return name?.run {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "string", "android")
        res.getString(id)
    } ?: localizedMessage ?: message ?: toString()
}


object SquareUtils {
    private const val HTTP_USER_AGENT = "PecaPlay (Linux; U; Android)"
    private const val HTTP_CONNECT_TIMEOUT = 10L
    private const val HTTP_RW_TIMEOUT = 20L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor {
            it.proceed(
                it.request().newBuilder()
                    .header("User-Agent", HTTP_USER_AGENT)
                    //.header("Connection", "close")
                    .build()
            )
        }
        .readTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
        .build()

    val SIMPLE_XML_CONVERTER_FACTORY = SimpleXmlConverterFactory.create()!!

    fun retrofitBuilder(): Retrofit.Builder = Retrofit.Builder().client(httpClient)

    val MOSHI: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()).build()

}

@Deprecated("")
suspend fun <T> Call<T>.exAwait(): Response<T> = suspendCancellableCoroutine { cont ->
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