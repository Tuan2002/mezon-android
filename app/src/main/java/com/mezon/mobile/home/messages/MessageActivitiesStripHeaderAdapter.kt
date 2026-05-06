package com.mezon.mobile.home.messages

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.RecyclerListView
import com.mezon.mobile.core.ThemeColors

class MessageActivitiesStripHeaderAdapter(
    private val themeColors: ThemeColors,
    private val onActivityRowClick: (MessageActivityRow) -> Unit
) : RecyclerView.Adapter<MessageActivitiesStripHeaderAdapter.Holder>() {

    companion object {
        private const val HEADER_STABLE_ID = -911_820_501L
    }

    private var items: List<MessageActivityRow> = emptyList()
    private var stripRecycler: RecyclerListView? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = HEADER_STABLE_ID

    fun setStripItems(rows: List<MessageActivityRow>) {
        items = rows
        notifyDataSetChanged()
    }

    fun scrollStripToStart() {
        stripRecycler?.post {
            stripRecycler?.stopScroll()
            (stripRecycler?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        }
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val inner = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(LayoutHelper.dp(16), LayoutHelper.dp(6), LayoutHelper.dp(16), LayoutHelper.dp(4))
        }
        val stripAdapter = MessageActivitiesAdapter(themeColors)
        inner.adapter = stripAdapter
        inner.setOnItemClickListener(RecyclerListView.OnItemClickListener { view, _ ->
            val cell = view as? MessageActivityStripCell ?: return@OnItemClickListener
            val row = cell.row ?: return@OnItemClickListener
            onActivityRowClick(row)
        })
        val wrap = FrameLayout(context)
        wrap.addView(
            inner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return Holder(wrap, inner, stripAdapter)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        stripRecycler = holder.inner
        holder.stripAdapter.setData(items)
    }

    override fun onViewRecycled(holder: Holder) {
        if (stripRecycler === holder.inner) stripRecycler = null
        super.onViewRecycled(holder)
    }

    class Holder(
        wrap: FrameLayout,
        val inner: RecyclerListView,
        val stripAdapter: MessageActivitiesAdapter
    ) : RecyclerView.ViewHolder(wrap)
}
