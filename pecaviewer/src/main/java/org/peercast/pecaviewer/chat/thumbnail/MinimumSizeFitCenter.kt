package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.min

class MinimumSizeFitCenter(
    private val minWidth: Int,
    private val minHeight: Int,
) :
    BitmapTransformation() {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int,
    ): Bitmap {
        //Timber.d(">> ${toTransform.width}, ${toTransform.height}, $outWidth, $outHeight")
        val upScale = min(
            minWidth * 100 / toTransform.width,
            minHeight * 100 / toTransform.height
        )
        return if (upScale > 100) {
            //val h = outHeight * minWidth / toTransform.width

            //Timber.d("> u$upScale")
            TransformationUtils.fitCenter(
                pool, toTransform,
                toTransform.width * upScale / 100,
                toTransform.height * upScale / 100
            )
        } else {
            toTransform
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is MinimumSizeFitCenter
    }

    override fun hashCode(): Int {
        return ID.hashCode()
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTE)
        messageDigest.update(
            ByteBuffer.allocate(8).putInt(minHeight).putInt(minHeight)
        )
    }

    companion object {
        private const val ID = "org.peercast.pecaviewer.chat.thumbnail.MinimumSizeFitCenter"
        private val ID_BYTE = ID.toByteArray(CHARSET)
    }
}