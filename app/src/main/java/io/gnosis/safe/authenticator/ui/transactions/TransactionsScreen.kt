package io.gnosis.safe.authenticator.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.adapter.*
import io.gnosis.safe.authenticator.ui.adapter.ListEntry.TransactionMeta
import io.gnosis.safe.authenticator.ui.base.BaseFragment
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.nullOnThrow
import kotlinx.android.synthetic.main.screen_transactions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity

@ExperimentalCoroutinesApi
abstract class TransactionsContract : LoadingViewModel<TransactionsContract.State>() {
    abstract fun loadTransactions()

    data class State(
        val loading: Boolean,
        val safe: Solidity.Address?,
        val transactions: List<ListEntry>?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
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

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, null, null)

}

@ExperimentalCoroutinesApi
class TransactionsScreen : BaseFragment<TransactionsContract.State, TransactionsContract>(), TransactionConfirmationDialog.Callback {
    override val viewModel: TransactionsContract by viewModel()
    private val picasso: Picasso by inject()
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
        transactions_empty_views.isVisible = state.transactions?.isEmpty() == true
    }

    override fun onConfirmed() {
        viewModel.loadTransactions()
    }

    inner class TransactionAdapter : ListAdapter<ListEntry, ListEntryViewHolder>(DiffCallback()) {
        var safe: Solidity.Address? = null
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            viewType.typeToViewHolder(parent, picasso, ::onSelected)

        private fun onSelected(entry: TransactionMeta) {
            val safe = safe ?: return
            activity?.let { TransactionConfirmationDialog(it, safe, entry.hash, entry.tx, entry.execInfo, this@TransactionsScreen).show() }
        }

        override fun getItemViewType(position: Int): Int {
            return getItem(position).type
        }

        override fun onBindViewHolder(holder: ListEntryViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    companion object {
        fun newInstance() = TransactionsScreen()
    }

}
