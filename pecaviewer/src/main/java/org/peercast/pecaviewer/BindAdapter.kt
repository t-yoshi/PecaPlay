package org.peercast.pecaviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton


internal object BindAdapter {
    private inline fun <reified T> View.setDefaultTag(id: Int, createTag: () -> T): T {
        getTag(id)?.let { return it as T }
        return createTag().also {
            setTag(id, it)
        }
    }

    @JvmStatic
    @BindingAdapter("listItemBackgroundColor")
            /**color=0のとき、android:backgroundを使う。*/
    fun bindListItemBackgroundColor(view: ViewGroup, @ColorInt color: Int) {
        val tag = view.setDefaultTag(R.id.tag_list_item_background_color) {
            object {
                val background = view.background
            }
        }

        if (color != 0) {
            view.setBackgroundColor(color)
        } else {
            view.background = tag.background
        }
    }

    @JvmStatic
    @BindingAdapter("imageTintList")
    fun bindImageTintList(view: ImageView, @AttrRes attrColor: Int) {
        val c = view.context
        val tv = TypedValue()
        c.theme.resolveAttribute(attrColor, tv, true)
        view.imageTintList = ContextCompat.getColorStateList(c, tv.resourceId)
    }

    @JvmStatic
    @BindingAdapter("fabOpaqueMode")
            /**狭いスマホではボタンが邪魔でテキストが読めないので半透明にする*/
    fun bindFabOpaqueMode(fab: FloatingActionButton, b: Boolean) {
        val tag = fab.setDefaultTag(R.id.tag_fab_opaque){
            object {
                val backgroundTintList = fab.backgroundTintList
                val elevation = fab.elevation
                val compatElevation = fab.compatElevation
            }
        }

        if (b) {
            fab.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            val e =
                fab.context.resources.getDimension(R.dimen.post_dialog_button_elevation_on_opaque_mode)
            fab.elevation = e
            fab.compatElevation = e
        } else {
            fab.backgroundTintList = tag.backgroundTintList
            fab.elevation = tag.elevation
            fab.compatElevation = tag.compatElevation
        }
    }

    @JvmStatic
    @BindingAdapter("underline")
    fun bindUnderline(view: TextView, b: Boolean) {
        if (b) {
            view.paintFlags = view.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        } else {
            view.paintFlags = view.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        }
    }


    private fun ViewPropertyAnimator.onEnd(action: (Animator) -> Unit) {
        setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = action(animation)
        })
    }

    @JvmStatic
    @BindingAdapter("visibleAnimate")
            /**アニメーションしながら visible<->gone*/
    fun bindVisibleAnimate(view: View, visibility: Boolean) {
        when {
            visibility && !view.isVisible -> {
                view.animate()
                    .setDuration(100)
                    .translationY(0f)
                    .alpha(1f)
                    .onEnd { view.isVisible = true }
            }
            !visibility && !view.isGone -> {
                view.animate()
                    .setDuration(150)
                    .translationY(view.height.toFloat())
                    .alpha(0f)
                    .onEnd { view.isVisible = false }
            }
        }
    }

}