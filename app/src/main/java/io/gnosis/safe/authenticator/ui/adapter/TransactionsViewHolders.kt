package io.gnosis.safe.authenticator.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.utils.asMiddleEllipsized
import io.gnosis.safe.authenticator.utils.setTransactionIcon
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.item_header.view.*
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.removeHexPrefix


sealed class ListEntry {
    abstract val id: String
    abstract val type: Int

    data class Header(val text: String, override val id: String = text, override val type: Int = R.id.entry_type_header) : ListEntry()

    data class TransactionMeta(
        val hash: String,
        val info: SafeRepository.TransactionInfo?,
        val tx: SafeRepository.SafeTx,
        val execInfo: SafeRepository.SafeTxExecInfo,
        val state: State,
        override val type: Int,
        override val id: String = hash
    ) : ListEntry() {
        enum class State {
            EXECUTED,
            CANCELED,
            CONFIRMED,
            PENDING
        }
    }

    data class InstantTransferMeta(
        val info: SafeRepository.InstantTransfer,
        override val type: Int,
        override val id: String = info.txHash
    ) : ListEntry()
}

fun Int.typeToViewHolder(parent: ViewGroup, picasso: Picasso, onSelected: ((ListEntry.TransactionMeta) -> Unit)? = null) = when (this) {
    R.id.entry_type_header -> HeaderViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.item_header,
            parent,
            false
        )
    )
    R.id.entry_type_pending_tx ->
        TransactionViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_pending_tx,
                parent,
                false
            ), picasso, onSelected
        )
    R.id.entry_type_executed_tx ->
        TransactionViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_executed_tx,
                parent,
                false
            ), picasso, onSelected
        )
    R.id.entry_type_pending_instant_transfer ->
        InstantTransferViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_pending_tx,
                parent,
                false
            ), picasso
        )
    R.id.entry_type_executed_instant_transfer ->
        InstantTransferViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_executed_tx,
                parent,
                false
            ), picasso
        )
    else -> throw IllegalStateException()
}

abstract class ListEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(entry: ListEntry)
}

class HeaderViewHolder(itemView: View) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is ListEntry.Header) return
        itemView.header_text.text = entry.text
    }
}

class TransactionViewHolder(
    itemView: View,
    private val picasso: Picasso,
    private val onClick: ((ListEntry.TransactionMeta) -> Unit)? = null
) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is ListEntry.TransactionMeta) return
        itemView.setOnClickListener {
            onClick?.invoke(entry)
        }
        itemView.tx_info_target.setAddress(entry.info?.recipient ?: entry.tx.to)
        itemView.tx_info_target_address.text = entry.info?.recipientLabel ?: entry.tx.to.asEthereumAddressString().asMiddleEllipsized(4)
        itemView.tx_info_execution_state.setImageResource(R.drawable.ic_arrow_forward_24dp)
        itemView.tx_info_execution_state.setColorFilter(itemView.context.getColorCompat(R.color.colorPrimary))
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (entry.state) {
            ListEntry.TransactionMeta.State.CONFIRMED -> itemView.tx_info_confirmation_state.visible(true)
            ListEntry.TransactionMeta.State.PENDING -> itemView.tx_info_confirmation_state.visible(false)
            ListEntry.TransactionMeta.State.CANCELED -> {
                itemView.tx_info_execution_state.setImageResource(R.drawable.ic_canceled_24dp)
                itemView.tx_info_execution_state.setColorFilter(itemView.context.getColorCompat(R.color.error))
            }
        }
        itemView.tx_info_value.text =
            entry.info?.assetLabel ?: if (entry.tx.data.removeHexPrefix().isBlank()) "ETH transfer" else "Contract interaction"
        itemView.tx_info_type.setTransactionIcon(picasso, entry.info?.assetIcon)
    }
}

class InstantTransferViewHolder(
    itemView: View,
    private val picasso: Picasso
) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is ListEntry.InstantTransferMeta) return
        itemView.tx_info_target.setAddress(entry.info.to)
        // TODO: move to viewmodel
        itemView.tx_info_target_address.text = entry.info.to.asEthereumAddressChecksumString().asMiddleEllipsized(4)
        itemView.tx_info_value.text =
            "${entry.info.amount.shiftedString(entry.info.tokenInfo?.decimals ?: 0)} ${entry.info.tokenInfo?.symbol ?: ""}"
        itemView.tx_info_type.setTransactionIcon(picasso, entry.info.tokenInfo?.icon)
    }
}


class DiffCallback : DiffUtil.ItemCallback<ListEntry>() {
    override fun areItemsTheSame(oldItem: ListEntry, newItem: ListEntry) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: ListEntry, newItem: ListEntry) =
        when (oldItem) {
            is ListEntry.Header -> (newItem as? ListEntry.Header) == oldItem
            is ListEntry.TransactionMeta -> (newItem as? ListEntry.TransactionMeta) == oldItem
            is ListEntry.InstantTransferMeta -> (newItem as? ListEntry.InstantTransferMeta) == oldItem
        }
}


fun MutableList<ListEntry>.maybeAddWithHeader(title: String, entries: List<ListEntry>) = this.apply {
    if (entries.isNotEmpty()) {
        this += ListEntry.Header(title)
        this += entries
    }
}
