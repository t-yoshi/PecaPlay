package org.peercast.pecaviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
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
    @JvmStatic
    @BindingAdapter("listItemBackground")
            /**color=0のとき、selectableItemBackgroundをセットする。*/
    fun bindListItemBackground(view: ViewGroup, @ColorInt color: Int) {
        if (color != 0) {
            view.setBackgroundColor(color)
        } else {
            val c = view.context
            val tv = TypedValue()
            c.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            view.setBackgroundResource(tv.resourceId)
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
    @BindingAdapter("fabOpaque")
            /**狭いスマホではボタンが邪魔でテキストが読めないので半透明にする*/
    fun bindFabOpaque(fab: FloatingActionButton, b: Boolean) {
        class SavedProps(
            val backgroundTintList: ColorStateList?,
            val elevation: Float,
            val compatElevation: Float,
        )

        if (fab.getTag(R.string.tag_fab_opaque) == null) {
            fab.setTag(R.string.tag_fab_opaque, SavedProps(
                fab.backgroundTintList, fab.elevation, fab.compatElevation
            ))
        }

        if (b) {
            fab.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            val e = 0.2f * fab.context.resources.displayMetrics.density
            fab.elevation = e
            fab.compatElevation = e
        } else {
            val savedProps = fab.getTag(R.string.tag_fab_opaque) as SavedProps
            fab.backgroundTintList = savedProps.backgroundTintList
            fab.elevation = savedProps.elevation
            fab.compatElevation = savedProps.compatElevation
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