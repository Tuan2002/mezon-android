package com.mezon.mobile.home.chat.channelinfo

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mezon.mobile.core.LayoutHelper
import com.mezon.mobile.core.ThemeColors
import com.mezon.mobile.home.clans.ChannelCanvasData
import com.mezon.mobile.home.clans.ChannelCanvasItemCell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CanvasListAdapter(
    private val theme: ThemeColors,
    private val onViewCanvas: (ChannelCanvasData) -> Unit,
    private val onCopyLink: (ChannelCanvasData) -> Unit,
) : RecyclerView.Adapter<CanvasListAdapter.CanvasViewHolder>() {

    private var items: List<ChannelCanvasData> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var diffJob: Job? = null

    fun setItems(newItems: List<ChannelCanvasData>) {
        if (newItems === items) return
        diffJob?.cancel()
        if (items.size < 50 && newItems.size < 50) {
            applyItems(newItems, DiffUtil.calculateDiff(CanvasDiffCallback(items, newItems)))
            return
        }
        val oldItems = items
        diffJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                DiffUtil.calculateDiff(CanvasDiffCallback(oldItems, newItems))
            }
            applyItems(newItems, result)
        }
    }

    private fun applyItems(newItems: List<ChannelCanvasData>, result: DiffUtil.DiffResult) {
        items = newItems
        result.dispatchUpdatesTo(this)
    }

    fun dispose() {
        diffJob?.cancel()
        scope.cancel()
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
        init {
            cell.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onViewCanvas(items[position])
            }
            cell.onCopyLinkClick = {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onCopyLink(items[position])
            }
        }

        fun bind(item: ChannelCanvasData) {
            val title = item.title.replace("\n", " ").ifBlank {
                cell.context.getString(com.mezon.mobile.R.string.channel_canvas_untitled)
            }
            cell.bind(title)
        }
    }

    private class CanvasDiffCallback(
        private val oldItems: List<ChannelCanvasData>,
        private val newItems: List<ChannelCanvasData>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldItems.size
        override fun getNewListSize(): Int = newItems.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition].id == newItems[newItemPosition].id
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            return old.title == new.title && old.creatorId == new.creatorId && old.isDefault == new.isDefault
        }
    }
}
