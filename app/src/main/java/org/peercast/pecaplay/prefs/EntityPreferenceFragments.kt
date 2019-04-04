package org.peercast.pecaplay.prefs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.databinding.BaseObservable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.ManageableEntity
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


/**w600dp 以上 */
private val Fragment.isLandscapeMode: Boolean
    get() = resources.getBoolean(R.bool.landscape_mode)


/**
 * YP/お気に入り編集用のPreferenceFragment
 *
 * 通常クリックで編集ダイアログを出す。
 * 長押しで削除。
 * チェックボックスで有効/無効。
 * */
abstract class EntityPreferenceFragmentBase<ME : ManageableEntity>
    : PreferenceFragmentCompat(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    protected val database: AppRoomDatabase by inject()
    abstract val presenter: IPresenter<ME>

    interface IPresenter<ME : ManageableEntity> {
        /** 編集の完了 (oldItem=nullなら新規)    */
        fun replaceItem(oldItem: ME?, newItem: ME)

        /**確認済みなので削除する。*/
        fun removeItem(item: ME)

        /**チェックボックスを押して有効/無効が変化した。　*/
        fun updateItem(item: ME, enabled: Boolean)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    @CallSuper
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val vg = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup

        // +ボタン
        if (isLandscapeMode) {
            val fab = inflater.inflate(R.layout.pref_fragment_floatingactionbutton, vg, false)
                    as FloatingActionButton
            fab.setOnClickListener {
                showEditDialog(null)
            }
            vg.addView(fab)
        }

        return vg
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (isLandscapeMode)
            return
        //新規メニュー
        inflater.inflate(R.menu.settings_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                showEditDialog(null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**編集ダイアログの作成*/
    protected abstract fun createEditDialogFragment(): EntityEditDialogFragmentBase<ME>

    //item==null 新規
    private fun showEditDialog(item: ME?) {
        val f = createEditDialogFragment()
        f.arguments = Bundle().also {
            it.putParcelable(EntityEditDialogFragmentBase.ARG_EDIT_SOURCE, item)
        }
        f.setTargetFragment(this, 0)
        fragmentManager?.let {
            f.show(it, "EditDialog@$item")
        }
    }

    //  削除するか確認する
    private fun confirmRemoveItem(item: ME) {
        AlertDialog.Builder(context!!)
            .setTitle(R.string.delete)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setItems(arrayOf(item.name)) { _, _ ->
                presenter.removeItem(item)
            }
            .show()
    }

    fun createCheckBoxPreference(item: ME): Preference {
        return CustomCheckBoxPreference(item.isEnabled, {
            presenter.updateItem(item, it)
        }, {
            confirmRemoveItem(item)
        }
        ).also {
            it.title = item.name
            it.extras.putParcelable(EXT_TARGET, item)
            it.setOnPreferenceClickListener {
                showEditDialog(item)
                true
            }
        }
    }

    private inner class CustomCheckBoxPreference(
        initChecked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val onLongClick: () -> Unit
    ) : Preference(preferenceScreen.context) {

        var isChecked: Boolean = initChecked
            private set

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            holder.itemView.setOnLongClickListener {
                onLongClick()
                true
            }

            val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout
            val checkBox: CheckBox =
                widgetFrame.findViewById(android.R.id.checkbox)
                    ?: LayoutInflater.from(widgetFrame.context)
                        .inflate(
                            androidx.preference.R.layout.preference_widget_checkbox,
                            widgetFrame, false
                        ).also {
                            it.isClickable = true
                            widgetFrame.addView(it)
                            widgetFrame.visibility = View.VISIBLE
                        } as CheckBox

            checkBox.let {
                //it.isFocusable = false
                it.setOnCheckedChangeListener(null)
                it.isChecked = isChecked
                it.setOnCheckedChangeListener { _, newChecked ->
                    onCheckedChange(newChecked)
                    isChecked = newChecked
                }
            }
        }
    }

    companion object {
        private const val EXT_TARGET = "target"
    }
}


/**編集ダイアログ*/
abstract class EntityEditDialogFragmentBase<ME : ManageableEntity>
    : DialogFragment(), DialogInterface.OnShowListener {

    abstract class DialogViewModelBase : BaseObservable() {
        val isOkButtonEnabled = MutableLiveData(false)
    }

    protected val database: AppRoomDatabase by inject()
    protected val dialog: AlertDialog get() = super.getDialog() as AlertDialog

    protected abstract val viewModel: DialogViewModelBase


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

    protected val presenter : EntityPreferenceFragmentBase.IPresenter<ME>
        @Suppress("unchecked_cast")
        get() = (targetFragment as EntityPreferenceFragmentBase<ME>).presenter


    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = context!!
        return AlertDialog.Builder(c)
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
                c.getDrawable(icon)?.let { d ->
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
        viewModel.isOkButtonEnabled.observe(this, Observer {
            dialog.okButton.isEnabled = it
        })

        if (!isLandscapeMode) {
            dialog.window?.let { w ->
                w.attributes = w.attributes.also {
                    it.width = WindowManager.LayoutParams.MATCH_PARENT
                    //it.height = WindowManager.LayoutParams.MATCH_PARENT;
                }
            }
        }
    }


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

