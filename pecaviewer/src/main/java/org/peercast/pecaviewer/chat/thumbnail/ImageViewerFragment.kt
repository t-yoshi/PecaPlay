package org.peercast.pecaviewer.chat.thumbnail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import org.peercast.pecaviewer.R

class ImageViewerFragment : DialogFragment() {

    init {
        isCancelable = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState)

        val v = LayoutInflater.from(context).inflate(R.layout.thumbnail_viewer_fragment, null)
        d.setContentView(v)
        d.setOnShowListener {
            d.window?.setDimAmount(0.2f)
        }
        val iv = v.findViewById<ImageView>(android.R.id.icon)
        loadImage(iv)
        iv.setOnClickListener { dismiss() }

        return d
    }

    private fun loadImage(view: ImageView) {
        Glide.with(this)
            .load(requireArguments().getString(KEY_URL))
            .override(
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels * 90 / 100
            )
            .downsample(DownsampleStrategy.NONE)
            .transform(
                MinimumSizeFitCenter(
                    resources.getDimension(R.dimen.min_popup_image_width)
                        .toInt(),
                    resources.getDimension(R.dimen.min_popup_image_height)
                        .toInt()
                )
            )
            .error(R.drawable.ic_warning_gray_24dp)
            .into(view)
    }

    companion object {
        private const val KEY_URL = "url"

        fun create(u: String): ImageViewerFragment {
            return ImageViewerFragment().also {
                //it.setStyle(STYLE_NO_TITLE, android.R.style.Theme_Dialog)
                //it.set
                it.arguments = Bundle().also { b ->
                    b.putString(KEY_URL, u)
                }
            }
        }

    }
}