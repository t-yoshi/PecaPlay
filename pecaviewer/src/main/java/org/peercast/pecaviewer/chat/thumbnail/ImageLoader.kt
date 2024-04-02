package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.HttpException
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.core.io.Square
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.thumbnail.net.ImageLoadingEventFlow
import org.peercast.pecaviewer.chat.thumbnail.net.LimitSizeInterceptor
import org.peercast.pecaviewer.chat.thumbnail.net.TooLargeFileException
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

open class DefaultImageLoader(
    private val view: View,
    protected val vm: ItemViewModel,
    protected val target: Target<Drawable>,
) : KoinComponent {
    private val loadingEventFlow by inject<ImageLoadingEventFlow>()
    private var jEvent: Job? = null

    private val requestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            Timber.w(e)
            jEvent?.cancel()
            vm.isAnimation.value = false

            return when (val eCause = e?.rootCauses?.firstOrNull()) {
                is TooLargeFileException -> {
                    vm.message.value = eCause.message
                    vm.isTooLargeFileSize.value = true
                    target.onLoadFailed(
                        getDrawable(R.drawable.ic_help_outline_gray_24dp)
                    )
                    true
                }

                is HttpException -> {
                    vm.isTooLargeFileSize.value = false
                    vm.message.value = eCause.run { "$statusCode: $message" }
                    false
                }

                else -> {
                    vm.isTooLargeFileSize.value = false
                    vm.message.value = eCause?.message ?: e?.message ?: "error..."
                    false
                }
            }
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            jEvent?.cancel()
            vm.isAnimation.value = resource is Animatable
            vm.message.value = null
            vm.isTooLargeFileSize.value = false
            return false
        }
    }

    open fun loadImage(u: String, maxFileSize: Long = 0) {
        val hb = LazyHeaders.Builder()

        if (maxFileSize > 0)
            hb.addHeader(LimitSizeInterceptor.X_HEADER_MAX_SIZE, "$maxFileSize")

        jEvent?.cancel()
        jEvent = loadingEventFlow
            .filter { it.url == u }
            .onEach {
                val s = "loaded: ${it.bytesRead / 1024}KB"
                Timber.d("$u: $s")
                vm.message.value = s

                delay(100)
            }
            .flowOn(Dispatchers.Default)
            .launchIn(checkNotNull(view.findViewTreeLifecycleOwner()).lifecycleScope)

        Glide
            .with(view)
            .load(GlideUrl(u, hb.build()))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
//            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .error(R.drawable.ic_warning_gray_24dp)
            //.placeholder(R.drawable.ic_help_outline_gray_24dp)
            .listener(requestListener)
            .into(target)
    }

    open fun cancelLoad() {
        jEvent?.cancel()
        Glide.with(view).clear(target)
    }

    protected fun getDrawable(@DrawableRes id: Int) = ContextCompat.getDrawable(view.context, id)
}

class NicoImageLoader(
    view: View,
    vm: ItemViewModel,
    target: Target<Drawable>,
) : DefaultImageLoader(view, vm, target), Callback, KoinComponent {
    private var prevCall: Call? = null

    private val square by inject<Square>()

    /**
     * @see ThumbnailUrl.NicoVideo
     * */
    override fun loadImage(u: String, maxFileSize: Long) {
        require(u.startsWith("https://ext.nicovideo.jp/api/getthumbinfo/"))

        val req = Request.Builder()
            .url(u)
            .cacheControl(MAX_STALE_10DAYS)
            .build()
        prevCall?.cancel()
        prevCall = square.okHttpClient.newCall(req).also {
            it.enqueue(this)
        }
    }

    override fun cancelLoad() {
        super.cancelLoad()
        prevCall?.cancel()
    }

    override fun onFailure(call: Call, e: IOException) {
        Timber.w(e)
        target.onLoadFailed(
            getDrawable(R.drawable.ic_warning_gray_24dp)
        )
        vm.message.value = "error: nicovideo getthumbinfo"
        prevCall = null
    }

    override fun onResponse(call: Call, response: Response) {
        try {
            val s = response.body?.string() ?: throw IOException("body is null")
            val u = RE_THUMBNAIL_URL.find(s)?.groupValues?.get(1)
                ?: throw IOException("missing <thumbnail_url>")
            super.loadImage(u, 0)
        } catch (e: IOException) {
            onFailure(call, e)
        }
        prevCall = null
    }

    companion object {
        private val RE_THUMBNAIL_URL =
            """<thumbnail_url>(https?://.+)</thumbnail_url>""".toRegex()
        private val MAX_STALE_10DAYS =
            CacheControl.Builder().maxStale(10, TimeUnit.DAYS).build()
    }
}
