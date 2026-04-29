package com.mezon.mobile.home.chat.channelinfo

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import java.util.Calendar

sealed class ChannelFilesRow {
    data class Header(val year: String, val dayTitle: String, val showYearLine: Boolean) : ChannelFilesRow()
    data class Doc(val item: ChannelDocumentItem) : ChannelFilesRow()
}

fun buildChannelFileRows(
    documents: List<ChannelDocumentItem>,
    searchQuery: String,
    isVietnamese: Boolean
): List<ChannelFilesRow> {
    val q = normalizeSearchQuery(searchQuery)
    val filtered = if (q.isEmpty()) {
        documents
    } else {
        documents.filter { normalizeSearchQuery(it.filename).contains(q) }
    }
    val grouped = groupByYearDay(filtered) { ChannelDocumentItemUtil.parseItemDate(it) }
    val rows = ArrayList<ChannelFilesRow>()
    for (g in grouped) {
        val cal = Calendar.getInstance().apply { timeInMillis = g.dayTs }
        val title = formatDateHeader(cal, isVietnamese)
        rows.add(ChannelFilesRow.Header(g.year, title, g.isFirstOfYear))
        for (doc in g.items) {
            rows.add(ChannelFilesRow.Doc(doc))
        }
    }
    return rows
}

interface FilesTabRowResolver {
    fun resolveSharerName(item: ChannelDocumentItem): String
    fun formatTime(createTimeSeconds: Int): String
    fun formatSharedByLine(displayName: String): String
    fun openUrl(url: String)
}

class ChannelFilesAdapter(
    private val theme: ThemeColors,
    private val resolver: FilesTabRowResolver
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_HEADER = 1
        const val VIEW_DOC = 2
    }

    private var rows: List<ChannelFilesRow> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun setRows(newRows: List<ChannelFilesRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun getRows(): List<ChannelFilesRow> = rows

    override fun getItemCount(): Int = rows.size

    override fun getItemId(position: Int): Long {
        return when (val r = rows[position]) {
            is ChannelFilesRow.Header -> -(position + 1).toLong()
            is ChannelFilesRow.Doc -> {
                val h = r.item.stableId.hashCode().toLong()
                if (h == 0L) -2L else h
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ChannelFilesRow.Header -> VIEW_HEADER
        is ChannelFilesRow.Doc -> VIEW_DOC
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        return when (viewType) {
            VIEW_HEADER -> HeaderVH(ChannelFileSectionHeaderView(ctx, theme))
            VIEW_DOC -> {
                val v = ChannelFileDocumentRowView(ctx, theme)
                v.layoutParams =
                    RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = LayoutHelper.dp(4f)
                        bottomMargin = LayoutHelper.dp(4f)
                    }
                DocVH(v)
            }
            else -> {
                val v = ChannelFileDocumentRowView(ctx, theme)
                v.layoutParams =
                    RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = LayoutHelper.dp(4f)
                        bottomMargin = LayoutHelper.dp(4f)
                    }
                DocVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderVH -> {
                val h = rows[position] as ChannelFilesRow.Header
                holder.view.bind(h.year, h.dayTitle, h.showYearLine)
            }
            is DocVH -> {
                val d = rows[position] as ChannelFilesRow.Doc
                val name = resolver.resolveSharerName(d.item)
                val time = resolver.formatTime(d.item.createTimeSeconds)
                val shared = resolver.formatSharedByLine(name)
                holder.view.bind(d.item, shared, time)
                holder.view.setOnClickListener {
                    val url = d.item.url
                    if (url.isNotEmpty()) {
                        resolver.openUrl(url)
                    }
                }
            }
        }
    }

    class HeaderVH(val view: ChannelFileSectionHeaderView) : RecyclerView.ViewHolder(view)
    class DocVH(val view: ChannelFileDocumentRowView) : RecyclerView.ViewHolder(view)
}
