package io.gnosis.safe.authenticator.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseFragment
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.transactions.TransactionsContract.ListEntry.Header
import io.gnosis.safe.authenticator.ui.transactions.TransactionsContract.ListEntry.TransactionMeta
import io.gnosis.safe.authenticator.utils.asMiddleEllipsized
import io.gnosis.safe.authenticator.utils.nullOnThrow
import kotlinx.android.synthetic.main.item_header.view.*
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import kotlinx.android.synthetic.main.screen_transactions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.removeHexPrefix
import java.lang.IllegalStateException

@ExperimentalCoroutinesApi
abstract class TransactionsContract : LoadingViewModel<TransactionsContract.State>() {
    abstract fun loadTransactions()

    data class State(
        val loading: Boolean,
        val safe: Solidity.Address?,
        val transactions: List<ListEntry>,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

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
    }
}

@ExperimentalCoroutinesApi
class TransactionsViewModel(
    private val safeRepository: SafeRepository
) : TransactionsContract() {

    override val state = liveData {
        loadTransactions()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun loadTransactions() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val txs = safeRepository.loadPendingTransactions(safe)
            val deviceId = safeRepository.loadDeviceId()
            val nonce = safeRepository.loadSafeNonce(safe)
            val pendingTx = mutableListOf<TransactionMeta>()
            val executedTx = mutableListOf<TransactionMeta>()
            txs.forEach {
                val transactionInfo = nullOnThrow { safeRepository.loadTransactionInformation(safe, it.tx) }
                val txState = when {
                    it.executed -> TransactionMeta.State.EXECUTED
                    it.execInfo.nonce < nonce -> TransactionMeta.State.CANCELED
                    it.confirmations.find { (address, _) -> address == deviceId } != null -> TransactionMeta.State.CONFIRMED
                    else -> TransactionMeta.State.PENDING
                }
                when (txState) {
                    TransactionMeta.State.EXECUTED, TransactionMeta.State.CANCELED ->
                        executedTx += TransactionMeta(it.hash, transactionInfo, it.tx, it.execInfo, txState, R.id.entry_type_executed_tx)
                    TransactionMeta.State.CONFIRMED, TransactionMeta.State.PENDING ->
                        pendingTx += TransactionMeta(it.hash, transactionInfo, it.tx, it.execInfo, txState, R.id.entry_type_pending_tx)
                }
            }
            val transactions = mutableListOf<ListEntry>()
                .maybeAddWithHeader("Pending", pendingTx)
                .maybeAddWithHeader("History", executedTx)
            updateState { copy(loading = false, safe = safe, transactions = transactions) }
        }
    }

    private fun MutableList<ListEntry>.maybeAddWithHeader(title: String, entries: List<ListEntry>) = this.apply {
        if (entries.isNotEmpty()) {
            this += Header(title)
            this += entries
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, emptyList(), null)

}

@ExperimentalCoroutinesApi
class TransactionsScreen : BaseFragment<TransactionsContract.State, TransactionsContract>(), TransactionConfirmationDialog.Callback {
    override val viewModel: TransactionsContract by viewModel()
    private lateinit var adapter: TransactionAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.screen_transactions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TransactionAdapter()
        layoutManager = LinearLayoutManager(context)
        transactions_list.adapter = adapter
        transactions_list.layoutManager = layoutManager
        //transactions_back_btn.setOnClickListener { onBackPressed() }
        transactions_refresh.setOnRefreshListener {
            viewModel.loadTransactions()
        }
    }

    override fun updateState(state: TransactionsContract.State) {
        transactions_refresh.isRefreshing = state.loading
        adapter.safe = state.safe
        adapter.submitList(state.transactions)
    }

    override fun onConfirmed() {
        viewModel.loadTransactions()
    }

    inner class TransactionAdapter : ListAdapter<TransactionsContract.ListEntry, ListEntryViewHolder>(DiffCallback()) {
        var safe: Solidity.Address? = null
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            when (viewType) {
                R.id.entry_type_header -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false))
                R.id.entry_type_pending_tx ->
                    ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending_tx, parent, false))
                R.id.entry_type_executed_tx ->
                    ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_executed_tx, parent, false))
                else -> throw IllegalStateException()
            }

        override fun getItemViewType(position: Int): Int {
            return getItem(position).type
        }

        override fun onBindViewHolder(holder: ListEntryViewHolder, position: Int) {
            holder.bind(safe, getItem(position))
        }
    }

    abstract class ListEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(safe: Solidity.Address?, entry: TransactionsContract.ListEntry)
    }

    inner class HeaderViewHolder(itemView: View) : ListEntryViewHolder(itemView) {
        override fun bind(safe: Solidity.Address?, entry: TransactionsContract.ListEntry) {
            if (entry !is Header) return
            itemView.header_text.text = entry.text
        }

    }

    inner class ViewHolder(itemView: View) : ListEntryViewHolder(itemView) {
        override fun bind(safe: Solidity.Address?, entry: TransactionsContract.ListEntry) {
            if (entry !is TransactionMeta) return
            itemView.setOnClickListener {
                safe ?: return@setOnClickListener
                activity?.let { TransactionConfirmationDialog(it, safe, entry.hash, entry.tx, entry.execInfo, this@TransactionsScreen).show() }
            }
            itemView.tx_info_target.setAddress(entry.info?.recipient ?: entry.tx.to)
            itemView.tx_info_target_address.text = entry.info?.recipientLabel ?: entry.tx.to.asEthereumAddressString().asMiddleEllipsized(4)
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (entry.state) {
                TransactionMeta.State.CONFIRMED -> itemView.tx_info_confirmation_state.visible(true)
                TransactionMeta.State.PENDING -> itemView.tx_info_confirmation_state.visible(false)
            }
            itemView.tx_info_value.text =
                entry.info?.assetLabel ?: if (entry.tx.data.removeHexPrefix().isBlank()) "ETH transfer" else "Contract interaction"
            itemView.tx_info_description.text = entry.info?.additionalInfo
            itemView.tx_info_description.isVisible = entry.info?.additionalInfo != null
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TransactionsContract.ListEntry>() {
        override fun areItemsTheSame(oldItem: TransactionsContract.ListEntry, newItem: TransactionsContract.ListEntry) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TransactionsContract.ListEntry, newItem: TransactionsContract.ListEntry) =
            when (oldItem) {
                is Header -> (newItem as? Header) == oldItem
                is TransactionMeta -> (newItem as? TransactionMeta) == oldItem
            }

    }

    companion object {
        fun newInstance() = TransactionsScreen()
    }

}
