package io.gnosis.safe.authenticator.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.gnosis.safe.authenticator.R
import kotlinx.android.synthetic.main.item_address_info.view.*
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddressString

data class AddressEntry(
    val address: Solidity.Address,
    val addressString: String,
    val label: String?,
    override val type: Int = R.id.entry_type_address,
    override val id: String = address.asEthereumAddressString()
) : ListEntry()

fun Int.typeToAddressViewHolder(parent: ViewGroup, onSelected: ((AddressEntry) -> Unit)? = null) = when (this) {
    R.id.entry_type_address ->
        AddressViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_address_info,
                parent,
                false
            ), onSelected
        )
    else -> typeToViewHolder(parent) ?: throw IllegalStateException()
}

class AddressViewHolder(
    itemView: View,
    private val onClick: ((AddressEntry) -> Unit)? = null
) : ListEntryViewHolder(itemView) {
    override fun bind(entry: ListEntry) {
        if (entry !is AddressEntry) return
        itemView.setOnClickListener {
            onClick?.invoke(entry)
        }
        itemView.address_info_ident.setAddress(entry.address)
        itemView.address_info_display.text = entry.addressString
        itemView.address_info_label.text = entry.label
        itemView.address_info_label.isVisible = entry.label != null
    }
}
