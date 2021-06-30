package org.peercast.pecaplay.prefs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.ManageableEntity
import timber.log.Timber

/**編集ダイアログ*/
abstract class BaseEntityEditDialogFragment<ME : ManageableEntity>
    : DialogFragment(), DialogInterface.OnShowListener {

    protected val isOkButtonEnabled = MutableStateFlow(false)

    protected val database by inject<AppRoomDatabase>()
    protected val dialog: AlertDialog get() = super.getDialog() as AlertDialog

    /**編集対象*/
    val editSource: ME?
        get() = arguments?.getParcelable(ARG_EDIT_SOURCE)

    /**編集or新規*/
    val isEditMode get() = editSource != null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        Timber.d("editSource = $editSource")
    }

    protected val presenter: BaseEntityPreferenceFragment.IPresenter<ME>
        @Suppress("unchecked_cast")
        get() = (targetFragment as BaseEntityPreferenceFragment<ME>).presenter


    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = requireContext()
        return MaterialAlertDialogBuilder(c)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                @Suppress("unchecked_cast")
                onOkButtonClicked()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .apply {
                val icon: Int
                if (isEditMode) {
                    setTitle(R.string.edit)
                    icon = R.drawable.ic_mode_edit_36dp
                } else {
                    setTitle(R.string.new_item)
                    icon = R.drawable.ic_add_circle_36dp
                }
                ContextCompat.getDrawable(c, icon)?.let { d ->
                    d.setTint(AppTheme.getIconColor(c))
                    setIcon(d)
                }
            }
            .setOnKeyListener(DialogInterface.OnKeyListener { _, _, keyEvent ->
                if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
                    return@OnKeyListener onBackPressed()
                }
                false
            })
            .also(::onBuildDialog)
            .create()
            .also {
                it.setOnShowListener(this)
            }
    }

    protected open fun onBuildDialog(builder: AlertDialog.Builder) {}

    protected abstract fun onOkButtonClicked()

    protected open fun onBackPressed(): Boolean = false


    @CallSuper
    override fun onShow(d: DialogInterface) {
        lifecycleScope.launchWhenResumed {
            isOkButtonEnabled.collect {
                dialog.okButton.isEnabled = it
            }
        }

        if (!isLandscapeMode) {
            dialog.window?.let { w ->
                w.attributes = w.attributes.also {
                    it.width = WindowManager.LayoutParams.MATCH_PARENT
                    //it.height = WindowManager.LayoutParams.MATCH_PARENT;
                }
            }
        }
    }

    /**w600dp 以上 */
    val isLandscapeMode: Boolean
        get() = resources.getBoolean(R.bool.landscape_mode)

    companion object {
        private const val TAG = "EntityEditDialogFragmentBase"
        const val ARG_EDIT_SOURCE = "ARG_EDIT_SOURCE"

        /**Positive button*/
        @JvmStatic
        protected val AlertDialog.okButton: Button
            get() =
                getButton(DialogInterface.BUTTON_POSITIVE)

        /**Negative button*/
        @JvmStatic
        protected val AlertDialog.cancelButton: Button
            get() =
                getButton(DialogInterface.BUTTON_NEGATIVE)

        @JvmStatic
        protected val AlertDialog.neutralButton: Button
            get() =
                getButton(DialogInterface.BUTTON_NEUTRAL)


    }
}