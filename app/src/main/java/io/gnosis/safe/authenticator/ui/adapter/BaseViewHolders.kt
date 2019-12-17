package io.gnosis.safe.authenticator.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.gnosis.safe.authenticator.R
import kotlinx.android.synthetic.main.item_header.view.*
import kotlinx.android.synthetic.main.item_notice.view.*

abstract class ListEntry {
    abstract val id: String
    abstract val type: Int
}

data class HeaderEntry(val text: String, override val id: String = text, override val type: Int = R.id.entry_type_header) : ListEntry()

data class NoticeEntry(
    val message: String,
    override val type: Int = R.id.entry_type_notice,
    override val id: String = message
) : ListEntry()

abstract class ListEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(entry: ListEntry)
}

class HeaderViewHolder(itemView: View) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is HeaderEntry) return
        itemView.header_text.text = entry.text
    }
}

class NoticeViewHolder(itemView: View) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is NoticeEntry) return
        itemView.notice_message.text = entry.message
    }
}

internal fun Int.typeToViewHolder(parent: ViewGroup) = when (this) {
    R.id.entry_type_header -> HeaderViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.item_header,
            parent,
            false
        )
    )
    R.id.entry_type_notice -> NoticeViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.item_notice,
            parent,
            false
        )
    )
    else -> null
}

fun MutableList<ListEntry>.maybeAddWithHeader(title: String, entries: List<ListEntry>) = this.apply {
    if (entries.isNotEmpty()) {
        this += HeaderEntry(title)
        this += entries
    }
}

class DiffCallback : DiffUtil.ItemCallback<ListEntry>() {
    override fun areItemsTheSame(oldItem: ListEntry, newItem: ListEntry) =
        oldItem.id == newItem.id

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ListEntry, newItem: ListEntry) =
        newItem == oldItem
}
