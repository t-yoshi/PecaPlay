package org.peercast.pecaplay.prefs

import android.arch.lifecycle.Observer
import android.content.DialogInterface
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.Observable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.peercast.pecaplay.BR
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.FavoriteDao
import org.peercast.pecaplay.databinding.PrefFavoriteEditorBinding
import timber.log.Timber
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private fun FavoriteDao.asyncDao(block: FavoriteDao.() -> Unit) {
    launch(UI) {
        async { block() }.await()
    }
}

//class TabHostFavoritePrefsFragment : Fragment() {
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        return inflater.inflate(R.layout.tabhost_favorite_prefs, container, false)
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        vPager.adapter = object : FragmentStatePagerAdapter(fragmentManager) {
//            override fun getCount() = TABS.size
//
//            override fun getItem(position: Int): Fragment {
//                return FavoritePrefsFragment().apply {
//                    arguments = Bundle().apply {
//                        putString(ARGS_TAB_TAG, TABS[position])
//                    }
//                    //setTargetFragment(this@TabHostFavoritePrefsFragment, 0)
//                }
//            }
//        }
//
//        val appTheme = AppTheme(context)
//
//        vTabLayout.apply {
//            setupWithViewPager(vPager)
//
//            getTabAt(0)?.let {
//                it.icon = appTheme.getIcon(R.drawable.ic_star_24px)
//            }
//
//            getTabAt(1)?.let {
//                it.icon = appTheme.getIcon(R.drawable.ic_notifications_24px)
//            }
//
//            getTabAt(2)?.let {
//                it.text = "NG"
//            }
//        }
//    }
//}

private const val ARGS_TAB_TAG = "tab-tag"
private const val TAB_FAVORITE = "tab-favorite"
private const val TAB_NOTIFY = "tab-notify"
private const val TAB_NG = "tab-flagNG"
private val TABS = listOf(TAB_FAVORITE,
        //TAB_NOTIFY,
        TAB_NG)


class FavoritePrefsFragment : ManageablePreferenceFragment<Favorite>() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        addPreferencesFromResource(R.xml.empty_preference_screen)

//        val tabTag = arguments.getString(ARGS_TAB_TAG)
//        val filter: (Favorite) -> Boolean = when (tabTag) {
//            TAB_FAVORITE -> {
//                { !it.flags.isNG }
//            }
//            TAB_NOTIFY -> {
//                { it.flags.isNotification }
//            }
//            TAB_NG -> {
//                { it.flags.isNG }
//            }
//            else -> throw IllegalArgumentException()
//        }

        database.getFavoriteDao().get().observe(this, Observer {
            preferenceScreen.removeAll()
            it!!.sortedWith(comparator).forEach { f ->
                addPreferenceFrom(f).run {
                    title = title.removePrefix("[star]")
                    icon = getIconDrawable(f)
                }
            }
        })
    }

    override fun createEditorFragment(): ManageableEditorFragment<Favorite, *> {
        return FavoriteEditorFragment()
    }

    override fun onReplaceItem(oldItem: Favorite?, newItem: Favorite) {
        database.getFavoriteDao().asyncDao {
            oldItem?.let { remove(it) }
            add(newItem)
        }
    }

    override fun onRemoveItem(item: Favorite) {
        database.getFavoriteDao().asyncDao { remove(item) }
    }

    override fun onUpdateItem(item: Favorite, enabled: Boolean) {
        database.getFavoriteDao().asyncDao {
            update(item.copy(isEnabled = enabled))
        }
    }

    private val comparator = Comparator<Favorite> { a, b ->
        if (a.flags.isNG != b.flags.isNG)
            return@Comparator if (a.flags.isNG) 1 else -1
        if (a.isStarred != b.isStarred)
            return@Comparator if (a.isStarred) -1 else 1
        a.name.compareTo(b.name)
    }

    private fun getIconDrawable(fav: Favorite): Drawable {
        val ic1 = when {
            fav.flags.isNG -> R.drawable.ic_ng
            fav.isStarred -> R.drawable.ic_star_36dp
            else -> R.drawable.ic_bookmark_36dp
        }

        val ic2 = when {
            fav.flags.isNotification -> R.drawable.ic_notifications_active_16dp
            else -> android.R.color.transparent
        }

        val c = context!!
        val res = resources
        val theme = c.theme

        val icons = arrayOf(
                res.getDrawable(ic1, theme),
                res.getDrawable(ic2, theme)
        )
        icons[0].setTint(AppTheme(c).textColorSecondary)
        icons[1].setTint(res.getColor(R.color.colorIconAlarm, theme))

        val ld = LayerDrawable(icons)

        ld.setLayerGravity(1, Gravity.BOTTOM or Gravity.RIGHT)
        return ld
    }

    companion object {
        private const val TAG = "FavoritePrefsFragment"
    }
}


class FavoriteEditorFragment : ManageableEditorFragment<Favorite, PrefFavoriteEditorBinding>() {
    private lateinit var viewModel: FavoriteEditorViewModel
    private var existsNames = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database.getFavoriteDao().get().observe(this, Observer {
            existsNames = it?.map { it.name } ?: emptyList()
        })
        isDualPane = true

        viewModel = editSource?.let {
            FavoriteEditorViewModel(it)
        } ?: kotlin.run {
            FavoriteEditorViewModel(
                    Favorite("", "",
                            Favorite.Flags(isName = true, isComment = true, isDescription = true)))
        }
    }

    override fun onCreateViewBinding(inflater: LayoutInflater): PrefFavoriteEditorBinding {
        return PrefFavoriteEditorBinding.inflate(inflater).also {
            it.viewModel = viewModel
            it.isAdvancedMode = paneMode == PaneMode.ADVANCED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //編集途中を保存
        arguments.putParcelable(ARG_EDIT_SOURCE, viewModel.fav)
    }

    override fun onShow(d: DialogInterface) {
        super.onShow(d)
        viewModel.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(p0: Observable?, p1: Int) {
                updateOkButton()
                Timber.d("-> ${viewModel.fav}")
            }
        })
        setOkButtonEnabled(false)
    }

    private fun updateOkButton() {
        val b = viewModel.let {
            (it.flagName || it.flagDescription || it.flagComment) &&
                    it.isValid && (it.name !in existsNames || isEditMode)
        }
        setOkButtonEnabled(b)
    }

    override fun onChangePane(mode: PaneMode) {
        viewBinding.isAdvancedMode = paneMode == PaneMode.ADVANCED
    }

    override fun onOkClick() {
        database.getFavoriteDao().asyncDao {
            editSource?.let { remove(it) }
            add(viewModel.fav)
        }
    }

    companion object {
        const val TAG = "FavoriteEditorDialog"
    }
}

class FavoriteEditorViewModel(var fav: Favorite) : BaseObservable() {
    @get:Bindable
    @set:Bindable
    var name: String
        get() = fav.name
        set(value) {
            fav = fav.copy(name = value)
            notifyPropertyChanged(BR.name)
        }

    @get:Bindable
    @set:Bindable
    var pattern: String
        get() = fav.pattern
        set(value) {
            fav = fav.copy(pattern = value)
            errorRegex = ""
            if (flagRegex)
                validRegExp()
            notifyPropertyChanged(BR.pattern)
        }

    private fun validRegExp(){
        try {
            Pattern.compile(pattern)
            errorRegex = ""
        } catch (e: PatternSyntaxException) {
            errorRegex = e.description
        }
    }

    /** 正規表現編集時のエラー表示*/
    @get:Bindable
    @set:Bindable
    var errorRegex = ""
        set(value) {
            if (field == value)
                return
            field = value
            notifyPropertyChanged(BR.errorRegex)
        }

    val isValid: Boolean
        get() {
            return name.isNotEmpty() && errorRegex.isEmpty()
        }

    @set:Bindable
    @get:Bindable
    var flagNotification: Boolean
        get() = fav.flags.isNotification
        set(value) {
            fav = fav.copyFlags { it.copy(isNotification = value) }
            notifyPropertyChanged(BR.flagNotification)
            notifyPropertyChanged(BR.flagNG)
        }

    @get:Bindable
    @set:Bindable
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
    @set:Bindable
    var flagName: Boolean
        get() = fav.flags.isName
        set(value) {
            fav = fav.copyFlags { it.copy(isName = value) }
            notifyPropertyChanged(BR.flagName)
        }

    @get:Bindable
    @set:Bindable
    var flagDescription: Boolean
        get() = fav.flags.isDescription
        set(value) {
            fav = fav.copyFlags { it.copy(isDescription = value) }
            notifyPropertyChanged(BR.flagDescription)
        }

    @get:Bindable
    @set:Bindable
    var flagComment: Boolean
        get() = fav.flags.isComment
        set(value) {
            fav = fav.copyFlags { it.copy(isComment = value) }
            notifyPropertyChanged(BR.flagComment)
        }

    @get:Bindable
    @set:Bindable
    var flagGenre: Boolean
        get() = fav.flags.isGenre
        set(value) {
            fav = fav.copyFlags { it.copy(isGenre = value) }
            notifyPropertyChanged(BR.flagGenre)
        }

    @get:Bindable
    @set:Bindable
    var flagExactMatch: Boolean
        get() = fav.flags.isExactMatch
        set(value) {
            fav = fav.copyFlags { it.copy(isExactMatch = value) }
            notifyPropertyChanged(BR.flagExactMatch)
        }

    @get:Bindable
    @set:Bindable
    var flagRegex: Boolean
        get() = fav.flags.isRegex
        set(value) {
            fav = fav.copyFlags { it.copy(isRegex = value) }
            notifyPropertyChanged(BR.flagRegex)
            if (value)
                validRegExp()
        }

}