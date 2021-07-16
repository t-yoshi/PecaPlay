package org.peercast.pecaviewer.chat.thumbnail

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import okhttp3.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.util.ISquareHolder
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

open class DefaultImageLoader(
    protected val c: Context,
    protected val vm: ItemViewModel,
    protected val target: Target<Drawable>
) {
    private val requestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            Timber.w(e)
            val eCause = e?.rootCauses?.firstOrNull()
            if (eCause is TooLargeFileException) {
                vm.error.value = eCause.message
                vm.isTooLargeFileSize.value = true
                target.onLoadFailed(
                    ContextCompat.getDrawable(
                        c,
                        R.drawable.ic_help_outline_gray_24dp
                    )
                )
                return true
            } else {
                vm.isTooLargeFileSize.value = false
                vm.error.value = e?.rootCauses?.firstOrNull()?.message ?: e?.message ?: "error..."
                vm.isAnimation.value = false
                return false
            }
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            vm.isAnimation.value = resource is Animatable
            vm.error.value = null
            vm.isTooLargeFileSize.value = false
            return false
        }
    }

    open fun loadImage(u: String, maxFileSize: Int = 0) {
        val hb = LazyHeaders.Builder()

        if (maxFileSize > 0)
            hb.addHeader(LimitSizeInterceptor.X_HEADER_MAX_SIZE, "$maxFileSize")

        Glide
            .with(c)
            .load(GlideUrl(u, hb.build()))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
//            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .error(R.drawable.ic_warning_gray_24dp)
            //.placeholder(R.drawable.ic_help_outline_gray_24dp)
            .listener(requestListener)
            .into(target)
    }

    open fun cancelLoad() {
        Glide.with(c).clear(target)
    }
}

class NicoImageLoader(
    c: Context,
    vm: ItemViewModel,
    target: Target<Drawable>
) : DefaultImageLoader(c, vm, target), Callback, KoinComponent {
    private var prevCall: Call? = null

    private val squareHolder by inject<ISquareHolder>()

    /**
     * @see ThumbnailUrl.NicoVideo
     * */
    override fun loadImage(u: String, maxFileSize: Int) {
        require(u.startsWith("https://ext.nicovideo.jp/api/getthumbinfo/"))
//        vm.src.value =
//            ContextCompat.getDrawable(c,
//            R.drawable.ic_help_outline_gray_24dp
//        )
        val req = Request.Builder()
            .url(u)
            .cacheControl(MAX_STALE_10DAYS)
            .build()
        prevCall?.cancel()
        prevCall = squareHolder.okHttpClient.newCall(req).also {
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
            ContextCompat.getDrawable(
                c,
                R.drawable.ic_warning_gray_24dp
            )
        )
        vm.error.value = "error: nicovideo getthumbinfo"
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
