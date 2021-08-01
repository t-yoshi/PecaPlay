package org.peercast.pecaplay.prefs

import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.annotation.CallSuper
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.ManageableEntity

/**
 * YP/お気に入り編集用のPreferenceFragment
 *
 * 通常クリックで編集ダイアログを出す。
 * 長押しで削除。
 * チェックボックスで有効/無効。
 * */
abstract class BaseEntityPreferenceFragment<ME : ManageableEntity>
    : PreferenceFragmentCompat() {

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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
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
    protected abstract fun createEditDialogFragment(): BaseEntityEditDialogFragment<ME>

    //item==null 新規
    private fun showEditDialog(item: ME?) {
        val f = createEditDialogFragment()
        f.arguments = Bundle().also {
            it.putParcelable(BaseEntityEditDialogFragment.ARG_EDIT_SOURCE, item)
        }
        f.setTargetFragment(this, 0)
        f.show(parentFragmentManager, "EditDialog@$item")
    }

    //  削除するか確認する
    private fun confirmRemoveItem(item: ME) {
        MaterialAlertDialogBuilder(requireContext())
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
        val onLongClick: () -> Unit,
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


