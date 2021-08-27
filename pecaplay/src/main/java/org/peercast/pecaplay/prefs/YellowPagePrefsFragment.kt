package org.peercast.pecaplay.prefs

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.util.AppTheme


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

    override lateinit var presenter: YellowPagePresenter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        presenter = YellowPagePresenter(requireActivity())
    }

    override fun createEditDialogFragment(): BaseEntityEditDialogFragment<YellowPage> {
        return YellowPageEditorDialogFragment()
    }

    companion object {
        private const val TAG = "YellowPagePrefsFragment"
    }
}



