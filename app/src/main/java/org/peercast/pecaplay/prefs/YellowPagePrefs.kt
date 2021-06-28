package org.peercast.pecaplay.prefs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.databinding.PrefYellowpageEditorBinding
import org.peercast.pecaplay.util.LiveDataUtils


class YellowPagePrefsFragment : EntityPreferenceFragmentBase<YellowPage>() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        val iconColor = AppTheme.getIconColor(requireContext())

        database.yellowPageDao.query(false).observe(this, Observer {
            preferenceScreen.removeAll()
            it?.forEach { yp ->
                createCheckBoxPreference(yp).let { p ->
                    p.setIcon(R.drawable.ic_peercast)
                    p.icon.setTint(iconColor)
                    p.summary = yp.url
                    preferenceScreen.addPreference(p)
                }
            }
        })
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

    override fun createEditDialogFragment(): EntityEditDialogFragmentBase<YellowPage> {
        return YellowPageEditorDialogFragment()
    }

    companion object {
        private const val TAG = "YellowPagePrefsFragment"
    }
}


class YellowPageEditorDialogFragment : EntityEditDialogFragmentBase<YellowPage>() {

    class ViewModel : DialogViewModelBase() {
        val name = MutableLiveData("NewYP")
        val url = MutableLiveData("http://")

        fun toYellowPage() = YellowPage(name.value!!, url.value!!, true)
    }

    override val viewModel = ViewModel()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //編集途中 or 編集
        (savedInstanceState?.getParcelable(STATE_EDITING_ITEM) ?: editSource)?.let {
            viewModel.name.value = it.name
            viewModel.url.value = it.url
        }

        val ypLd = database.yellowPageDao.query(false)

        //検証: nameとurlが正しいか。新規作成なら既に存在していないか。
        LiveDataUtils.combineLatest(ypLd, viewModel.name, viewModel.url) { yellowPages, name, url ->
            val existsNames = yellowPages.map { it.name }
            val existsUrls = yellowPages.map { it.url }

            name.isNotBlank() && YellowPage.isValidUrl(url) &&
                    (isEditMode || (name !in existsNames && url !in existsUrls))
        }.observe(this, Observer<Boolean> {
            viewModel.isOkButtonEnabled.value = it
        })
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        //Timber.d("onBuildDialog $builder")
        val inflater = LayoutInflater.from(builder.context)
        val binding = PrefYellowpageEditorBinding.inflate(inflater)
        binding.vName.run {
            val adapter = AutoCompleteAdapter(context)
            setAdapter(adapter)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
                adapter.getUrl(viewModel.name.value!!)?.let {
                    viewModel.url.value = it
                }
            }
        }
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        builder.setView(binding.root)
    }

    //TP,SP
    private class AutoCompleteAdapter(c: Context) : ArrayAdapter<String>(
        c, android.R.layout.simple_dropdown_item_1line
    ) {
        private val yp = c.resources.getStringArray(R.array.default_yp_names).zip(
            c.resources.getStringArray(R.array.default_yp_urls)
        ).toMap()

        init {
            addAll(yp.keys)
        }

        fun getUrl(name: String): String? = yp[name]
    }

    override fun onOkButtonClicked() {
        presenter.replaceItem(editSource, viewModel.toYellowPage())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //編集途中を保存
        outState.putParcelable(STATE_EDITING_ITEM, viewModel.toYellowPage())
    }

    companion object {
        private const val TAG = "YellowPageEditorDialogFragment"
        private const val STATE_EDITING_ITEM = "$TAG#editing-item"
    }
}



