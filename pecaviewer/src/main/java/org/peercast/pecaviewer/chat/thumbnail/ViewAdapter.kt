package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.ThumbnailViewItemBinding
import kotlin.properties.Delegates

class ViewAdapter(private val view: ThumbnailView) {
    var urls: List<ThumbnailUrl> by Delegates.observable(
        emptyList()
    ) { _, oldUrls, newUrls ->
        if (oldUrls != newUrls)
            notifyChange()
    }

    private val inflater = LayoutInflater.from(view.context)
    private val viewHolders = ArrayList<ItemViewHolder>()

    private fun notifyChange() {
        check(viewHolders.size == view.childCount)

        while (urls.size - viewHolders.size > 0) {
            val b = ThumbnailViewItemBinding.inflate(
                inflater,
                view,
                false
            )
            viewHolders.add(
                ItemViewHolder(
                    view,
                    b
                )
            )
            view.addView(b.root)
        }

        urls.zip(viewHolders) { u, vh ->
            vh.showThumbnail(u)
        }

        viewHolders.drop(urls.size).forEach { vh ->
            vh.gone()
        }
    }

    private class ItemViewHolder(
        private val view: ThumbnailView,
        private val binding: ThumbnailViewItemBinding,
    ) {
        private val viewModel = ItemViewModel()
        private val target = NotAnimatedTarget(binding.icon.layoutParams, viewModel)

        init {
            binding.vm = viewModel
        }

        private var prevLoader: DefaultImageLoader? = null

        fun showThumbnail(u: ThumbnailUrl) {
            val c = view.context

            //prevLoader?.cancelLoad(binding.icon)
            val loader = when (u) {
                is ThumbnailUrl.NicoVideo -> ::NicoImageLoader
                else -> ::DefaultImageLoader
            }(c, viewModel, target)
            val bg = ContextCompat.getDrawable(
                c, when (u) {
                    is ThumbnailUrl.YouTube -> R.drawable.frame_bg_red
                    is ThumbnailUrl.NicoVideo -> R.drawable.frame_bg_grey
                    else -> R.drawable.frame_bg_blue
                }
            )

            with(viewModel) {
                loader.loadImage(u.imageUrl, 1 * 1024 * 1024)
                background.value = bg
                isLinkUrl.value = u.linkUrl.isNotEmpty()

                binding.root.setOnClickListener {
                    when {
                        u.linkUrl.isNotEmpty() || error.get().isNullOrEmpty() -> {
                            view.eventListener?.onLaunchImageViewer(u)
                        }
                        else -> {
                            loader.loadImage(u.imageUrl)
                        }
                    }
                }
            }
            binding.root.isGone = false
            prevLoader = loader
        }

        fun gone() {
            binding.root.isGone = true
            prevLoader?.cancelLoad()
            prevLoader = null
        }
    }

    //64dp
    private class NotAnimatedTarget(
        p: ViewGroup.LayoutParams,
        private val vm: ItemViewModel,
    ) : CustomTarget<Drawable>(p.width, p.height) {
        override fun onLoadFailed(errorDrawable: Drawable?) {
            vm.src.value = errorDrawable
        }

        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            vm.src.value = resource
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            vm.src.value = placeholder
        }
    }
}