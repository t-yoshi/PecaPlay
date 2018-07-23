package org.peercast.pecaplay.prefs

import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import android.content.Context
import android.content.DialogInterface
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.support.annotation.CallSuper
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AlertDialog
import android.support.v7.widget.AppCompatCheckBox
import android.view.*
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import org.peercast.pecaplay.PecaPlayApplication
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.ManageableEntity
import timber.log.Timber


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
abstract class ManageablePreferenceFragment<E : ManageableEntity>
    : PreferenceFragment(), LifecycleOwner {

    val database get() = PecaPlayApplication.of(activity).database

    private val registry = LifecycleRegistry(this)

    override fun getLifecycle() = registry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val vg = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup

        if (isLandscapeMode) {
            val fab = inflater.inflate(R.layout.pref_fragment_floatingactionbutton, vg, false)
                    as FloatingActionButton
            fab.setOnClickListener { showEditorDialog(null) }
            vg.addView(fab)
        }

        val listView = vg.findViewById<ListView>(android.R.id.list)
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = listView.adapter.getItem(position)
                    as? CheckablePreference ?: return@setOnItemLongClickListener false

            //削除するか確認する
            AlertDialog.Builder(context)
                    .setTitle(R.string.delete)
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setItems(arrayOf(item.title)) { _, _ ->
                        onRemoveItem(item.extras.getParcelable(EXT_TARGET))
                    }
                    .show()
            true
        }

        return vg
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        if (isLandscapeMode)
            return

        //"新規" メニュー
        menu.findItem(R.id.menu_add).apply {
            isVisible = true
            setOnMenuItemClickListener {
                showEditorDialog(null)
                true
            }
        }
    }

    /** 編集の完了     */
    abstract fun onReplaceItem(oldItem: E?, newItem: E)

    /**確認済みなので削除する。*/
    abstract fun onRemoveItem(item: E)

    /**チェックボックスを押して有効/無効が変化した。　*/
    abstract fun onUpdateItem(item: E, enabled: Boolean)

    /**編集ダイアログの作成*/
    protected abstract fun createEditorFragment(): ManageableEditorFragment<E, *>

    //null==新規
    private fun showEditorDialog(target: ManageableEntity?) {
        val f = createEditorFragment()
        f.arguments = Bundle().apply {
            putParcelable(ManageableEditorFragment.ARG_EDIT_SOURCE, target)
        }
        f.setTargetFragment(this, 0)
        f.show(fragmentManager, "EditorFragment@$target")
    }

    fun addPreferenceFrom(manageable: E, group: PreferenceGroup = preferenceScreen): Preference {
        return CheckablePreference(context) {
            it.isChecked = manageable.isEnabled
            it.setOnCheckedChangeListener { _, b ->
                onUpdateItem(manageable, b)
            }
        }.also {
            it.title = manageable.name
            it.extras.putParcelable(EXT_TARGET, manageable)
            it.setOnPreferenceClickListener {
                showEditorDialog(manageable)
                true
            }
            group.addPreference(it)
        }
    }


    private class CheckablePreference(c: Context,
                                      private val onBindCheckBox: (CheckBox) -> Unit) : Preference(c) {

        override fun onBindView(view: View) {
            super.onBindView(view)

            val widgetFrame = view.findViewById(android.R.id.widget_frame) as LinearLayout
            val checkBox: CheckBox =
                    widgetFrame.findViewById(android.R.id.checkbox)
                            ?: AppCompatCheckBox(context).also {
                                it.isFocusable = false
                                widgetFrame.addView(it)
                                widgetFrame.visibility = View.VISIBLE
                                it.id = android.R.id.checkbox
                            }
            onBindCheckBox(checkBox)
        }
    }

    override fun onPause() {
        super.onPause()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onResume() {
        super.onResume()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStart() {
        super.onStart()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onStop() {
        super.onStop()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    companion object {
        private const val EXT_TARGET = "target"
    }
}


abstract class ManageableEditorFragment<E : ManageableEntity,
        B : ViewDataBinding> : DialogFragment(), LifecycleOwner,
        DialogInterface.OnShowListener {

    private val registry = LifecycleRegistry(this)

    override fun getLifecycle() = registry


    enum class PaneMode {
        BASIC, ADVANCED
    }

    protected lateinit var paneMode: PaneMode
        private set

    val database get() = PecaPlayApplication.of(activity).database

    /**neutralButtonで詳細モードへ切り替えるか*/
    protected var isDualPane = false

    private lateinit var okButton: Button
    //private lateinit var cancelButton: Button
    private lateinit var neutralButton: Button
    protected lateinit var viewBinding: B
        private set

    protected abstract fun onCreateViewBinding(inflater: LayoutInflater): B

    protected fun setOkButtonEnabled(b: Boolean) {
        okButton.isEnabled = b
    }

    /**編集対象*/
    protected var editSource: E? = null
        private set

    val isEditMode get() = editSource != null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        isCancelable = false
        editSource = arguments!!.getParcelable(ARG_EDIT_SOURCE)
        paneMode = PaneMode.values()[savedInstanceState?.getInt(STATE_KEY_PANE_MODE) ?: 0]
        Timber.d("editSource = $editSource")
    }

    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = context
        viewBinding = onCreateViewBinding(LayoutInflater.from(c))
        val dialog = AlertDialog.Builder(c)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onOkClick()
                }
                .setNegativeButton(android.R.string.cancel) { d, _ ->
                    d.cancel()
                }
                .apply {
                    val icon: Int
                    if (isEditMode) {
                        setTitle(R.string.edit)
                        icon = R.drawable.ic_mode_edit_36dp
                    } else {
                        setTitle(R.string.new_item)
                        icon = R.drawable.ic_add_circle_36dp
                    }
                    setIcon(AppTheme(c).getIcon(icon))
                    if (isDualPane)
                        setNeutralButton(R.string.manageable_editor_options, null)
                }
                .setView(viewBinding.root)
                .setOnKeyListener(DialogInterface.OnKeyListener { _, _, keyEvent ->
                    if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
                        return@OnKeyListener onBackPressed()
                    }
                    false
                })
                .create()
        dialog.setOnShowListener(this)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_KEY_PANE_MODE, paneMode.ordinal)
    }

    private fun togglePane() {
        paneMode = PaneMode.values()[(paneMode.ordinal + 1) % 2]
        when (paneMode) {
            PaneMode.ADVANCED -> {
                neutralButton.setText(R.string.manageable_editor_back)
                //cancelButton.isEnabled = false
            }
            PaneMode.BASIC -> {
                neutralButton.setText(R.string.manageable_editor_options)
                //cancelButton.isEnabled = true
            }
        }
        onChangePane(paneMode)
    }

    /**  targetFragment.onChangeItem を実装する。*/
    protected abstract fun onOkClick()


    protected open fun onChangePane(mode: PaneMode) {}

    private fun onBackPressed(): Boolean {
        if (paneMode == PaneMode.BASIC) {
            dismiss()
        } else {
            togglePane()
        }
        return true
    }

    @CallSuper
    override fun onShow(d: DialogInterface) {
        val dialog = d as AlertDialog
        okButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        //cancelButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
            neutralButton = it as Button
            togglePane()
        }

        if (!isLandscapeMode) {
            val params = dialog.window!!.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            //params.height = WindowManager.LayoutParams.MATCH_PARENT;
            dialog.window.attributes = params
        }
    }

    override fun onPause() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStart() {
        super.onStart()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onStop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }


    companion object {
        const val TAG = "ManageableEditor"
        const val ARG_EDIT_SOURCE = "ARG_EDIT_SOURCE"
        private const val STATE_KEY_PANE_MODE = "$TAG#pane-mode"
    }
}

