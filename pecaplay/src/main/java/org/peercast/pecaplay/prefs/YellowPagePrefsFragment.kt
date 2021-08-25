package org.peercast.pecaplay.prefs

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.peercast.pecaplay.R
import org.peercast.pecaplay.util.AppTheme
import org.peercast.pecaplay.app.YellowPage


class YellowPagePrefsFragment : BaseEntityPreferenceFragment<YellowPage>() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        val iconColor = AppTheme.getIconColor(requireContext())

        lifecycleScope.launch {
            database.yellowPageDao.query(false)
                .onEach {
                    preferenceScreen.removeAll()
                    it.forEach { yp ->
                        createCheckBoxPreference(yp).let { p ->
                            p.setIcon(R.drawable.ic_peercast)
                            p.icon.setTint(iconColor)
                            p.summary = yp.url
                            preferenceScreen.addPreference(p)
                        }
                    }
                }
                .collect()
        }
    }

    override val presenter = object : IPresenter<YellowPage> {
        override fun removeItem(item: YellowPage) {
            lifecycleScope.launch {
                database.yellowPageDao.remove(item)
            }
        }

        override fun replaceItem(oldItem: YellowPage?, newItem: YellowPage) {
            lifecycleScope.launch {
                database.yellowPageDao.run {
                    oldItem?.let { remove(it) }
                    add(newItem)
                }
            }
        }

        override fun updateItem(item: YellowPage, enabled: Boolean) {
            lifecycleScope.launch {
                database.yellowPageDao.update(item.copy(isEnabled = enabled))
            }
        }
    }

    override fun createEditDialogFragment(): BaseEntityEditDialogFragment<YellowPage> {
        return YellowPageEditorDialogFragment()
    }

    companion object {
        private const val TAG = "YellowPagePrefsFragment"
    }
}



