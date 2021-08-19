package org.peercast.pecaplay.prefs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.peercast.pecaplay.BR
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.databinding.PrefFavoriteEditorBinding
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class FavoriteEditorDialogFragment : BaseEntityEditDialogFragment<Favorite>() {
    private lateinit var viewModel: ViewModel
    private var existsNames = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            database.favoriteDao.query(false)
                .collect {
                    existsNames = it.map { it.name }
                }
        }

        //編集途中 or 編集 or 新規
        viewModel = ViewModel(
            savedInstanceState?.getParcelable(STATE_EDITING_ITEM) ?: editSource ?: Favorite(
                "", "",
                Favorite.Flags(isName = true, isComment = true, isDescription = true)
            )
        )

        viewModel.isAdvancedMode.value = savedInstanceState?.getBoolean(STATE_ADVANCE_MODE) ?: false

        viewModel.run {
            addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
                override fun onPropertyChanged(sender: Observable, propertyId: Int) {
                    isOkButtonEnabled.value =
                        (flagName || flagDescription || flagComment) &&
                                isValid && (name !in existsNames || isEditMode)
                    //Timber.d("-> ${viewModel.fav}")
                }
            })
        }
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        val inflater = LayoutInflater.from(builder.context)

        val binding = PrefFavoriteEditorBinding.inflate(inflater)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        builder.setView(binding.root)
        builder.setNeutralButton(R.string.manageable_editor_options, null)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //編集途中を保存
        outState.putParcelable(STATE_EDITING_ITEM, viewModel.fav)
        outState.putBoolean(STATE_ADVANCE_MODE, viewModel.isAdvancedMode.value)
    }


    override fun onShow(d: DialogInterface) {
        super.onShow(d)

        //詳細<->戻る
        dialog.neutralButton.setOnClickListener {
            viewModel.isAdvancedMode.value = !viewModel.isAdvancedMode.value
        }

        lifecycleScope.launchWhenStarted {
            viewModel.isAdvancedMode.collect {
                if (it) {
                    dialog.neutralButton.setText(R.string.manageable_editor_back)
                    dialog.cancelButton.isEnabled = false
                } else {
                    dialog.neutralButton.setText(R.string.manageable_editor_options)
                    dialog.cancelButton.isEnabled = true
                }
            }
        }
    }

    override fun onOkButtonClicked() {
        presenter.replaceItem(editSource, viewModel.fav)
    }

    override fun onBackPressed(): Boolean {
        if (viewModel.isAdvancedMode.value) {
            viewModel.isAdvancedMode.value = false
            return true
        }

        dismiss()
        return true
    }

    companion object {
        const val TAG = "FavoriteEditorDialog"
        private const val STATE_ADVANCE_MODE = "$TAG#advance-mode"
        private const val STATE_EDITING_ITEM = "$TAG#editing-item"
    }

    class ViewModel(var fav: Favorite) : BaseObservable() {
        private fun validRegExp() {
            errorRegex = try {
                Pattern.compile(pattern)
                ""
            } catch (e: PatternSyntaxException) {
                e.description
            }
        }

        val isValid: Boolean
            get() = name.isNotEmpty() && errorRegex.isEmpty()


        @get:Bindable
        var name: String
            get() = fav.name
            set(value) {
                fav = fav.copy(name = value)
                notifyPropertyChanged(BR.name)
            }

        @get:Bindable
        var pattern: String
            get() = fav.pattern
            set(value) {
                fav = fav.copy(pattern = value)
                errorRegex = ""
                if (flagRegex)
                    validRegExp()
                notifyPropertyChanged(BR.pattern)
            }

        /** 正規表現編集時のエラー表示*/
        @get:Bindable
        var errorRegex = ""
            set(value) {
                if (field == value)
                    return
                field = value
                notifyPropertyChanged(BR.errorRegex)
            }

        @get:Bindable
        var flagNotification: Boolean
            get() = fav.flags.isNotification
            set(value) {
                fav = fav.copyFlags { it.copy(isNotification = value) }
                notifyPropertyChanged(BR.flagNotification)
                notifyPropertyChanged(BR.flagNG)
            }

        @get:Bindable
        var flagNG: Boolean
            get() = fav.flags.isNG
            set(value) {
                fav = fav.copyFlags { it.copy(isNG = value) }
                if (value) {
                    fav = fav.copyFlags { it.copy(isNotification = false) }
                }
                notifyPropertyChanged(BR.flagNotification)
                notifyPropertyChanged(BR.flagNG)
            }

        @get:Bindable
        var flagName: Boolean
            get() = fav.flags.isName
            set(value) {
                fav = fav.copyFlags { it.copy(isName = value) }
                notifyPropertyChanged(BR.flagName)
            }

        @get:Bindable
        var flagDescription: Boolean
            get() = fav.flags.isDescription
            set(value) {
                fav = fav.copyFlags { it.copy(isDescription = value) }
                notifyPropertyChanged(BR.flagDescription)
            }

        @get:Bindable
        var flagComment: Boolean
            get() = fav.flags.isComment
            set(value) {
                fav = fav.copyFlags { it.copy(isComment = value) }
                notifyPropertyChanged(BR.flagComment)
            }

        @get:Bindable
        var flagGenre: Boolean
            get() = fav.flags.isGenre
            set(value) {
                fav = fav.copyFlags { it.copy(isGenre = value) }
                notifyPropertyChanged(BR.flagGenre)
            }

        @get:Bindable
        var flagExactMatch: Boolean
            get() = fav.flags.isExactMatch
            set(value) {
                fav = fav.copyFlags { it.copy(isExactMatch = value) }
                notifyPropertyChanged(BR.flagExactMatch)
            }

        @get:Bindable
        var flagRegex: Boolean
            get() = fav.flags.isRegex
            set(value) {
                fav = fav.copyFlags { it.copy(isRegex = value) }
                notifyPropertyChanged(BR.flagRegex)
                if (value)
                    validRegExp()
            }

        val isAdvancedMode = MutableStateFlow(false)
    }
}