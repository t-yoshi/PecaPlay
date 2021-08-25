package org.peercast.pecaplay.prefs

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.peercast.pecaplay.R
import org.peercast.pecaplay.util.AppTheme
import org.peercast.pecaplay.app.Favorite


class FavoritePrefsFragment : BaseEntityPreferenceFragment<Favorite>() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        lifecycleScope.launch {
            database.favoriteDao.query(false)
                .onEach {
                    preferenceScreen.removeAll()
                    it.sortedWith(COMPARATOR).forEach { f ->
                        val p = createCheckBoxPreference(f)
                        p.title = p.title.removePrefix("[star]")
                        p.icon = getIconDrawable(f)
                        preferenceScreen.addPreference(p)
                    }
                }
                .collect()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.title =
            getString(R.string.pref_header_favorites)
    }

    override fun createEditDialogFragment(): BaseEntityEditDialogFragment<Favorite> {
        return FavoriteEditorDialogFragment()
    }

    override val presenter = object : IPresenter<Favorite> {
        override fun replaceItem(oldItem: Favorite?, newItem: Favorite) {
            lifecycleScope.launch {
                database.favoriteDao.run {
                    oldItem?.let { remove(it) }
                    add(newItem)
                }
            }
        }

        override fun removeItem(item: Favorite) {
            lifecycleScope.launch {
                database.favoriteDao.remove(item)
            }
        }

        override fun updateItem(item: Favorite, enabled: Boolean) {
            lifecycleScope.launch {
                database.favoriteDao.update(item.copy(isEnabled = enabled))
            }
        }
    }


    private fun getIconDrawable(fav: Favorite): Drawable {
        val ic1 = when {
            fav.flags.isNG -> R.drawable.ic_ng
            fav.isStar -> R.drawable.ic_star_36dp
            else -> R.drawable.ic_bookmark_36dp
        }

        val ic2 = when {
            fav.flags.isNotification -> R.drawable.ic_notifications_active_16dp
            else -> android.R.color.transparent
        }

        val c = requireContext()
        val res = resources
        val theme = c.theme

        val icons = arrayOf(
            requireNotNull(ResourcesCompat.getDrawable(res, ic1, theme)),
            requireNotNull(ResourcesCompat.getDrawable(res, ic2, theme))
        )
        icons[0].setTint(AppTheme.getIconColor(c))
        icons[1].setTint(ResourcesCompat.getColor(res, R.color.colorIconAlarm, theme))

        val ld = LayerDrawable(icons)
        ld.setLayerGravity(1, Gravity.BOTTOM or Gravity.RIGHT)
        return ld
    }

    companion object {
        private const val TAG = "FavoritePrefsFragment"

        private val COMPARATOR = Comparator<Favorite> { a, b ->
            if (a.flags.isNG != b.flags.isNG)
                return@Comparator if (a.flags.isNG) 1 else -1
            if (a.isStar != b.isStar)
                return@Comparator if (a.isStar) 1 else -1
            a.name.compareTo(b.name)
        }
    }
}


