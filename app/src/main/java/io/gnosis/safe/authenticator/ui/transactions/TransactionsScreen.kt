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
import io.gnosis.safe.authenticator.ui.instant.NewInstantTransferActivity
import io.gnosis.safe.authenticator.utils.nullOnThrow
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import kotlinx.android.synthetic.main.screen_transactions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.removeHexPrefix

@ExperimentalCoroutinesApi
abstract class TransactionsContract : LoadingViewModel<TransactionsContract.State>() {
    abstract fun loadTransactions()

    data class State(
        val loading: Boolean,
        val safe: Solidity.Address?,
        val transactions: List<TransactionMeta>,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class TransactionMeta(
        val hash: String,
        val info: SafeRepository.TransactionInfo?,
        val tx: SafeRepository.SafeTx,
        val execInfo: SafeRepository.SafeTxExecInfo,
        val state: State
    ) {
        enum class State {
            EXECUTED,
            CANCELED,
            CONFIRMED,
            PENDING
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
            val transactions = txs.map {
                val transactionInfo = nullOnThrow { safeRepository.loadTransactionInformation(safe, it.tx) }
                val txState = when {
                    it.executed -> TransactionMeta.State.EXECUTED
                    it.execInfo.nonce < nonce -> TransactionMeta.State.CANCELED
                    it.confirmations.find { (address, _) -> address == deviceId } != null -> TransactionMeta.State.CONFIRMED
                    else -> TransactionMeta.State.PENDING
                }
                TransactionMeta(it.hash, transactionInfo, it.tx, it.execInfo, txState)
            }
            updateState { copy(loading = false, safe = safe, transactions = transactions) }
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

    inner class TransactionAdapter : ListAdapter<TransactionsContract.TransactionMeta, ViewHolder>(DiffCallback()) {
        var safe: Solidity.Address? = null
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending_tx, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(safe, getItem(position))
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(safe: Solidity.Address?, item: TransactionsContract.TransactionMeta) {
            itemView.setOnClickListener {
                safe ?: return@setOnClickListener
                activity?.let { TransactionConfirmationDialog(it, safe, item.hash, item.tx, item.execInfo, this@TransactionsScreen).show() }
            }
            itemView.pending_tx_target.setAddress(item.info?.recipient ?: item.tx.to)
            itemView.pending_tx_confirmations.text = when (item.state) {
                TransactionsContract.TransactionMeta.State.EXECUTED -> "Executed"
                TransactionsContract.TransactionMeta.State.CANCELED -> "Canceled"
                TransactionsContract.TransactionMeta.State.CONFIRMED -> "Confirmed"
                TransactionsContract.TransactionMeta.State.PENDING -> "Pending"
            }
            itemView.pending_tx_value.text =
                item.info?.assetLabel ?: if (item.tx.data.removeHexPrefix().isBlank()) "ETH transfer" else "Contract interaction"
            itemView.pending_tx_description.text = item.info?.additionalInfo
            itemView.pending_tx_description.isVisible = item.info?.additionalInfo != null
        }
    }

    override fun onConfirmed() {
        viewModel.loadTransactions()
    }

    class DiffCallback : DiffUtil.ItemCallback<TransactionsContract.TransactionMeta>() {
        override fun areItemsTheSame(oldItem: TransactionsContract.TransactionMeta, newItem: TransactionsContract.TransactionMeta) =
            oldItem.hash == newItem.hash

        override fun areContentsTheSame(oldItem: TransactionsContract.TransactionMeta, newItem: TransactionsContract.TransactionMeta) =
            oldItem == newItem

    }

    companion object {
        fun newInstance() = TransactionsScreen()
    }

}
