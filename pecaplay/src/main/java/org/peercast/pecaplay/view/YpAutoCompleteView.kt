package org.peercast.pecaplay.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.YellowPage
import timber.log.Timber
import kotlin.properties.Delegates

class YpAutoCompleteView : AppCompatAutoCompleteTextView, KoinComponent {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val database by inject<AppRoomDatabase>()

    var onDefaultYellowPageSelected: (YellowPage) -> Unit = {}

    private val adapter = object : BaseAdapter(), Filterable {
        var yellowPages by Delegates.observable(emptyList<YellowPage>()) { _, old, new ->
            if (new.isEmpty())
                notifyDataSetChanged()
            else if (old != new)
                notifyDataSetChanged()
        }

        override fun getCount() = yellowPages.size

        override fun getItem(position: Int) = get(position).name

        operator fun get(position: Int) = yellowPages[position]

        override fun getItemId(position: Int) = 0L

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: kotlin.run {
                LayoutInflater.from(context).inflate(
                    android.R.layout.simple_dropdown_item_1line, parent, false
                )
            }
            val text1 = v.findViewById<TextView>(android.R.id.text1)
            text1.text = get(position).name
            return v
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults()
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val defaultYps = context.resources.getStringArray(R.array.default_yp_names).zip(
            context.resources.getStringArray(R.array.default_yp_urls)
        ).map { (n, u) ->
            YellowPage(n, u)
        }

        checkNotNull(findViewTreeLifecycleOwner()).lifecycleScope.launch {
            database.yellowPageDao.query(false).collect { existsYps ->
                adapter.yellowPages = defaultYps
                    .filter { it.url !in existsYps.map { it.url } }
                    .filter { it.name !in existsYps.map { it.name } }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    init {
        setAdapter(this.adapter)
        maxLines = 1
        inputType = EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
        onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            //Timber.d("--> $pos")
            onDefaultYellowPageSelected(adapter[pos])
        }
    }

}