package org.peercast.pecaplay.prefs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.databinding.PrefYellowpageEditorBinding

class YellowPageEditorDialogFragment : BaseEntityEditDialogFragment<YellowPage>() {

    class ViewModel {
        val name = MutableStateFlow("NewYP")
        val url = MutableStateFlow("http://")

        fun toYellowPage() = YellowPage(
            name.value, url.value
        )
    }

    private val viewModel = ViewModel()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //編集途中 or 編集
        (savedInstanceState?.getParcelable(STATE_EDITING_ITEM) ?: editSource)?.let {
            viewModel.name.value = it.name
            viewModel.url.value = it.url
        }

        combine(
            database.yellowPageDao.query(false),
            viewModel.name, viewModel.url,
        ) { yellowPages, name, url ->
            val existsNames = yellowPages.map { it.name }
            val existsUrls = yellowPages.map { it.url }

            name.isNotBlank() && YellowPage.isValidUrl(url) &&
                    (isEditMode || (name !in existsNames && url !in existsUrls))
        }
            .onEach {
                //Timber.d("--> $it")
                isOkButtonEnabled.value = it
            }.launchIn(lifecycleScope)
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        //Timber.d("onBuildDialog $builder")
        val inflater = LayoutInflater.from(builder.context)
        val binding = PrefYellowpageEditorBinding.inflate(inflater)
        binding.vName.run {
            val adapter = AutoCompleteAdapter(context)
            setAdapter(adapter)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
                adapter.getUrl(viewModel.name.value)?.let {
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