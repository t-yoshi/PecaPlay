package org.peercast.pecaplay.prefs

import android.arch.lifecycle.Observer
import android.content.Context
import android.content.DialogInterface
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.Observable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.peercast.pecaplay.BR
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.app.YellowPageDao
import org.peercast.pecaplay.databinding.PrefYellowpageEditorBinding



private val DEFAULT_YELLOW_PAGES = linkedMapOf(
        "SP" to "http://bayonet.ddo.jp/sp/",
        "TP" to "http://temp.orz.hm/yp/"
)

private fun YellowPageDao.asyncDao(block: YellowPageDao.() -> Unit) {
    launch(UI) {
        async { block() }.await()
    }
}

class YellowPagePrefsFragment : ManageablePreferenceFragment<YellowPage>() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        addPreferencesFromResource(R.xml.pref_yp)

        val icon = AppTheme(context).getIcon(R.drawable.ic_peercast)

        database.getYellowPageDao().get().observe(this, Observer {
            preferenceScreen.removeAll()
            it?.forEach { yp ->
                addPreferenceFrom(yp).run {
                    setIcon(icon)
                    summary = yp.url
                }
            }
        })
    }

    override fun onRemoveItem(item: YellowPage) {
        database.getYellowPageDao().asyncDao { remove(item) }
    }

    override fun onReplaceItem(oldItem: YellowPage?, newItem: YellowPage) {
        database.getYellowPageDao().asyncDao {
            oldItem?.let { remove(it) }
            add(newItem)
        }
    }

    override fun onUpdateItem(item: YellowPage, enabled: Boolean) {
        database.getYellowPageDao().asyncDao {
            update(item.copy(isEnabled = enabled))
        }
    }

    override fun createEditorFragment(): ManageableEditorFragment<YellowPage, *> {
        return YellowPageEditorDialog()
    }

    companion object {
        private const val TAG = "YellowPagePrefsFragment"
    }
}


class YellowPageEditorDialog : ManageableEditorFragment<YellowPage, PrefYellowpageEditorBinding>() {

    private val viewModel = ViewModel()

    private var existsNames = emptyList<String>()
    private var existsUrls = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database.getYellowPageDao().get().observe(this, Observer {
            existsNames = it?.map { it.name } ?: emptyList()
            existsUrls = it?.map { it.url } ?: emptyList()
        })

        editSource?.let {
            viewModel.name = it.name
            viewModel.url = it.url
        }
    }

    class ViewModel : BaseObservable() {
        @Bindable
        var name = "NewYP"
            set(value) {
                field = value
                notifyPropertyChanged(BR.name)
            }

        @Bindable
        var url = "http://"
            set(value) {
                field = value
                notifyPropertyChanged(BR.url)
            }

        val isValid: Boolean
            get() = name.isNotEmpty() &&
                    YellowPage.isValidUrl(url)

        fun toYellowPage() = YellowPage(name, url, true)
    }


    //TP,SP
    private class AutoCompleteAdapter(c: Context) : ArrayAdapter<String>(
            c, android.R.layout.simple_dropdown_item_1line) {
        init {
            addAll(DEFAULT_YELLOW_PAGES.keys)
        }

        fun getUrl(name: String) = DEFAULT_YELLOW_PAGES[name]
    }

    override fun onCreateViewBinding(inflater: LayoutInflater): PrefYellowpageEditorBinding {
        return PrefYellowpageEditorBinding.inflate(inflater).also {
            it.viewModel = viewModel

            it.vName.run {
                val adapter = AutoCompleteAdapter(context)
                setAdapter(adapter)
                onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
                    adapter.getUrl(viewModel.name)?.let {
                        viewModel.url = it
                    }
                }
            }
        }
    }

    override fun onShow(d: DialogInterface) {
        super.onShow(d)

        viewModel.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(p0: Observable?, p1: Int) {
                updateEnableOkButton()
            }
        })
        updateEnableOkButton()
    }

    override fun onOkClick() {
        val newYp = viewModel.toYellowPage()
        database.getYellowPageDao().asyncDao {
            editSource?.let { remove(it) }
            add(newYp)
        }
    }

    private fun updateEnableOkButton() {
        val b = viewModel.isValid &&
                (isEditMode ||
                        (viewModel.name !in existsNames && viewModel.url !in existsUrls))

        setOkButtonEnabled(b)
    }

    companion object {
        private const val TAG = "YellowPageEditorDialog"
    }

}


