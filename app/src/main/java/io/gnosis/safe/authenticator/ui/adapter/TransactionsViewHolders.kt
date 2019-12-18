package io.gnosis.safe.authenticator.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.utils.setTransactionIcon
import io.gnosis.safe.authenticator.utils.shiftedString
import io.gnosis.safe.authenticator.utils.shortChecksumString
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.svalinn.common.utils.openUrl
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.removeHexPrefix

data class TransactionMetaEntry(
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

data class InstantTransferMetaEntry(
    val info: SafeRepository.InstantTransfer,
    override val type: Int,
    override val id: String = info.txHash
) : ListEntry()

fun Int.typeToTransactionViewHolder(parent: ViewGroup, picasso: Picasso, onSelected: ((TransactionMetaEntry) -> Unit)? = null) = when (this) {
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
    else -> typeToViewHolder(parent) ?: throw IllegalStateException()
}

class TransactionViewHolder(
    itemView: View,
    private val picasso: Picasso,
    private val onClick: ((TransactionMetaEntry) -> Unit)? = null
) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is TransactionMetaEntry) return
        itemView.setOnClickListener {
            onClick?.invoke(entry)
        }
        itemView.tx_info_target.setAddress(entry.info?.recipient ?: entry.tx.to)
        itemView.tx_info_target_address.text = entry.info?.recipientLabel ?: entry.tx.to.shortChecksumString()
        itemView.tx_info_execution_state.setImageResource(R.drawable.ic_arrow_forward_24dp)
        itemView.tx_info_execution_state.setColorFilter(itemView.context.getColorCompat(R.color.colorPrimary))
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (entry.state) {
            TransactionMetaEntry.State.CONFIRMED -> itemView.tx_info_confirmation_state.visible(true)
            TransactionMetaEntry.State.PENDING -> itemView.tx_info_confirmation_state.visible(false)
            TransactionMetaEntry.State.CANCELED -> {
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
        if (entry !is InstantTransferMetaEntry) return
        itemView.setOnClickListener {
            itemView.context.openUrl(BuildConfig.BLOCK_EXPLORER_TX.format(entry.info.txHash))
        }
        itemView.tx_info_target.setAddress(entry.info.to)
        // TODO: move to viewmodel
        itemView.tx_info_target_address.text = entry.info.to.shortChecksumString()
        itemView.tx_info_value.text =
            "${entry.info.amount.shiftedString(entry.info.tokenInfo?.decimals ?: 0)} ${entry.info.tokenInfo?.symbol ?: ""}"
        itemView.tx_info_type.setTransactionIcon(picasso, entry.info.tokenInfo?.icon)
    }
}
