package org.peercast.pecaplay

import android.content.res.Resources
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**PecaPlayActivityのインテント*/
const val EXTRA_NAVIGATION_CATEGORY = "navigation-category"

/**プレーヤー起動時のインテント*/
const val EXTRA_IS_LAUNCH_FROM_PECAPLAY = "is-launch-from-pecaplay"





object ExceptionUtils {
    /**
     * ソケット関連のIOExceptionからローカライズされたOSのエラーメッセージを得る。
     * @see FileNotFoundException
     * @see SocketTimeoutException
     * @see UnknownHostException
     * その他は localizedMessage
     * */
    fun localizedSystemMessage(e: Throwable): String {
        val name = when (e) {
            is FileNotFoundException -> "httpErrorFileNotFound"
            is SocketTimeoutException -> "httpErrorTimeout"
            is UnknownHostException -> "httpErrorLookup"
            else -> null
        }
        return name?.run {
            val res = Resources.getSystem()
            val id = res.getIdentifier(name, "string", "android")
            res.getString(id)
        } ?: e.localizedMessage ?: e.message ?: toString()
    }
}


object ObjectUtils {
    fun <T : Any> hashCode(clazz: KClass<T>, vararg objects: Any?): Int {
        return arrayOf(clazz, *objects).map {
            it?.hashCode() ?: 0
        }.reduce { acc, hash -> acc * 31 + hash }
    }

    fun <T : Any> equals(this_: T, other: Any?, vararg field: (T) -> Any?): Boolean {
        return other != null &&
                this_::class === other::class &&
                field.all {
                    @Suppress("UNCHECKED_CAST")
                    it(this_) == it(other as T)
                }
    }
}

object ViewUtils {
    fun menuItems(menu: Menu) = menu.run {
        (0 until size()).map { getItem(it) }
    }

    fun children(vg: ViewGroup, recursive: Boolean = false): List<View> = vg.run {
        (0 until childCount).map {
            getChildAt(it)
        }.flatMap {
            when {
                recursive && it is ViewGroup -> {
                    children(it, true)
                }
                else -> listOf(it)
            }
        }
    }
}


object SquareUtils {
    private const val HTTP_USER_AGENT = "PecaPlay (Linux; U; Android)"
    private const val HTTP_CONNECT_TIMEOUT = 12 * 1000L
    private const val HTTP_READ_TIMEOUT = 8 * 1000L

    private val httpClient = OkHttpClient.Builder()
            .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor {
                it.proceed(
                        it.request().newBuilder()
                                .header("User-Agent", HTTP_USER_AGENT)
                                .build()
                )
            }
            .readTimeout(HTTP_READ_TIMEOUT, TimeUnit.SECONDS)
            .build()!!

    val SIMPLE_XML_CONVERTER_FACTORY = SimpleXmlConverterFactory.create()!!

    fun retrofitBuilder() = Retrofit.Builder().client(httpClient)!!

    val MOSHI = Moshi.Builder().build()!!
}

