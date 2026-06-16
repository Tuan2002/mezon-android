package com.mezon.mobile.home.chat.channelinfo

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.home.clans.ChannelCanvasData
import com.mezon.mobile.home.clans.ChannelCanvasItemCell

class CanvasListAdapter(
    private val theme: ThemeColors,
    private val onViewCanvas: (ChannelCanvasData) -> Unit,
    private val onCopyLink: (ChannelCanvasData) -> Unit,
) : RecyclerView.Adapter<CanvasListAdapter.CanvasViewHolder>() {

    private var items: List<ChannelCanvasData> = emptyList()

    fun setItems(newItems: List<ChannelCanvasData>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].id == newItems[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]
                return old.title == new.title && old.content == new.content && old.creatorId == new.creatorId
            }
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanvasViewHolder {
        val cell = ChannelCanvasItemCell(parent.context, theme).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                LayoutHelper.dp(50f)
            ).apply {
                val marginH = LayoutHelper.dp(12f)
                val marginV = LayoutHelper.dp(4f)
                setMargins(marginH, marginV, marginH, marginV)
            }
        }
        return CanvasViewHolder(cell)
    }

    override fun onBindViewHolder(holder: CanvasViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CanvasViewHolder(private val cell: ChannelCanvasItemCell) : RecyclerView.ViewHolder(cell) {
        fun bind(item: ChannelCanvasData) {
            val title = item.title.replace("\n", " ").ifBlank {
                cell.context.getString(com.mezon.mobile.R.string.channel_canvas_untitled)
            }
            cell.bind(title)
            cell.onItemClick = { onViewCanvas(item) }
            cell.onCopyLink = { onCopyLink(item) }
        }
    }
}
