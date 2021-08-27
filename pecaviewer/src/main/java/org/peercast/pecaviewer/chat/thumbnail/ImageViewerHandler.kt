package org.peercast.pecaviewer.chat.thumbnail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.stfalcon.imageviewer.StfalconImageViewer
import com.stfalcon.imageviewer.loader.ImageLoader
import org.peercast.pecaviewer.R
import timber.log.Timber

class ImageViewerHandler(private val fragment: Fragment) :
    ThumbnailView.OnThumbnailClickedListener {

    //動画サイトへ
    private fun launchBrowser(url: ThumbnailUrl.HasLinked) {
        try {
            fragment.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.linkUrl)))
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
        }
    }

    private val loadImage = ImageLoader<ThumbnailUrl> { v, u ->
        Glide.with(fragment)
            .load(u.imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(v)
    }

    //画像拡大表示
    private fun showImageViewer(thumbnail: ImageView, urls: List<ThumbnailUrl>, position: Int) {
        //これらは動画ではない
        val imageUrls = urls.filter { it !is ThumbnailUrl.HasLinked }
        val c = fragment.requireContext()

        val vOverlay = LayoutInflater.from(c).inflate(R.layout.image_viewer_overlay, null, false)
        val vClose = vOverlay.findViewById<ImageView>(R.id.vClose)

        val viewer = StfalconImageViewer.Builder(
            c, imageUrls, loadImage
        )
            .withOverlayView(vOverlay)
            .withStartPosition(imageUrls.indexOf(urls[position]))
            .withTransitionFrom(thumbnail)
            .build()

        vClose.setOnClickListener {
            viewer.close()
        }

        viewer.show()
    }

    override fun onThumbnailClicked(thumbnail: ImageView, urls: List<ThumbnailUrl>, position: Int) {
        val url = urls[position]
        if (url is ThumbnailUrl.HasLinked) {
            launchBrowser(url)
        } else {
            showImageViewer(thumbnail, urls, position)
        }
    }

}