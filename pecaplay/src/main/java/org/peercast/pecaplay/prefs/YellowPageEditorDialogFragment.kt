package org.peercast.pecaplay.prefs

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.databinding.PrefYellowpageEditorBinding

class YellowPageEditorDialogFragment : BaseEntityEditDialogFragment<YellowPage>() {

    private val yellowPage = MutableStateFlow(
        YellowPage("NewYP", "http://")
    )

    inner class BindingPresenter {
        fun onNameChanged(name: Editable) {
            yellowPage.run {
                value = value.copy(name = name.toString())
            }
        }

        fun onUrlChanged(url: Editable) {
            yellowPage.run {
                value = value.copy(url = url.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //編集途中 or 編集
        (savedInstanceState?.getParcelable(STATE_EDITING_ITEM) ?: editSource)?.let {
            yellowPage.value = it
        }

        combine(
            database.yellowPageDao.query(false),
            yellowPage,
        ) { yellowPages, yp ->
            val existsNames = yellowPages.map { it.name }
            val existsUrls = yellowPages.map { it.url }

            yp.name.isNotBlank() && YellowPage.isValidUrl(yp.url) &&
                    (isEditMode || (yp.name !in existsNames && yp.url !in existsUrls))
        }
            .onEach {
                isOkButtonEnabled.value = it
            }.launchIn(lifecycleScope)
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        //Timber.d("onBuildDialog $builder")
        val inflater = LayoutInflater.from(builder.context)
        PrefYellowpageEditorBinding.inflate(inflater).also { b ->
            b.lifecycleOwner = this
            b.root.setViewTreeLifecycleOwner(this)

            b.pr = BindingPresenter()
            b.yp = yellowPage
            b.vName.onDefaultYellowPageSelected = {
                yellowPage.value = it
            }
            b.vName.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    b.vName.post {
                        b.vName.showDropDown()
                    }
                }
            }
            builder.setView(b.root)
        }
    }

    override fun onOkButtonClicked() {
        YellowPagePresenter(requireActivity())
            .replaceItem(editSource, yellowPage.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //編集途中を保存
        outState.putParcelable(STATE_EDITING_ITEM, yellowPage.value)
    }


    companion object {
        private const val TAG = "YellowPageEditorDialogFragment"
        private const val STATE_EDITING_ITEM = "$TAG#editing-item"
    }
}