package org.peercast.pecaviewer.util

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * スクリーンショットを撮る
 * @throws ScreenShotFailedException 失敗した時
 * */
@RequiresApi(Build.VERSION_CODES.N)
internal suspend fun takeScreenShot(
    view: SurfaceView, maxWidthDp: Int,
): Bitmap = suspendCancellableCoroutine { cont ->
    val w: Int // DP
    val h: Int // DP
    val dm = view.context.resources.displayMetrics
    if (maxWidthDp * dm.density < view.width) {
        w = maxWidthDp
        h = (1f * view.height / view.width * maxWidthDp).toInt()
    } else {
        w = (view.width / dm.density).toInt()
        h = (view.height / dm.density).toInt()
    }
    Timber.d("Bitmap w=$w, h=$h")

    val b = Bitmap.createBitmap(dm, w, h, Bitmap.Config.ARGB_8888)

    PixelCopy.request(view.holder.surface, b, { r ->
        Timber.d("PixelCopy: $r")
        when (r) {
            PixelCopy.SUCCESS -> {
                cont.resume(b)
            }
            else -> {
                cont.resumeWithException(ScreenShotFailedException(r))
            }
        }
    }, handler)
}

@RequiresApi(Build.VERSION_CODES.N)
fun takeScreenShot(view: SurfaceView, maxWidthDp: Int, callback: (Bitmap) -> Unit) {
    val w: Int // DP
    val h: Int // DP
    val dm = view.context.resources.displayMetrics
    if (maxWidthDp * dm.density < view.width) {
        w = maxWidthDp
        h = (1f * view.height / view.width * maxWidthDp).toInt()
    } else {
        w = (view.width / dm.density).toInt()
        h = (view.height / dm.density).toInt()
    }
    Timber.d("Bitmap w=$w, h=$h")

    val b = Bitmap.createBitmap(dm, w, h, Bitmap.Config.ARGB_8888)

    PixelCopy.request(view.holder.surface, b, { r ->
        when (r) {
            PixelCopy.SUCCESS -> {
                callback(b)
            }
            else -> {
                Timber.e("PixelCopy failed:")
            }
        }
    }, handler)
}


class ScreenShotFailedException(val status: Int) :
    RuntimeException("PixelCopy.request() failed: $status")

private val handler = Handler(Looper.getMainLooper())