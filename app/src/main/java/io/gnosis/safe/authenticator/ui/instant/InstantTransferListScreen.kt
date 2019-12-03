package io.gnosis.safe.authenticator.ui.instant

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
import io.gnosis.safe.authenticator.ui.transactions.TransactionsContract
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import kotlinx.android.synthetic.main.screen_instant_transfers.*
import kotlinx.android.synthetic.main.screen_transactions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.removeHexPrefix

@ExperimentalCoroutinesApi
abstract class InstantTransferListContract : LoadingViewModel<InstantTransferListContract.State>() {
    abstract fun loadTransfers()

    data class State(
        val loading: Boolean,
        val transactions: List<SafeRepository.InstantTransfer>,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

@ExperimentalCoroutinesApi
class InstantTransferListViewModel(
    private val safeRepository: SafeRepository
) : InstantTransferListContract() {

    override val state = liveData {
        loadTransfers()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun loadTransfers() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val transfers = safeRepository.loadInstantTransfers()
            updateState { copy(loading = false, transactions = transfers) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, emptyList(), null)

}

@ExperimentalCoroutinesApi
class InstantTransferListScreen : BaseFragment<InstantTransferListContract.State, InstantTransferListContract>() {
    override val viewModel: InstantTransferListContract by viewModel()
    private lateinit var adapter: TransactionAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.screen_instant_transfers, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TransactionAdapter()
        layoutManager = LinearLayoutManager(context)
        instant_transfers_list.adapter = adapter
        instant_transfers_list.layoutManager = layoutManager
        //instant_transfers_back_btn.setOnClickListener { onBackPressed() }
        instant_transfers_refresh.setOnRefreshListener {
            viewModel.loadTransfers()
        }
    }

    override fun updateState(state: InstantTransferListContract.State) {
        instant_transfers_refresh.isRefreshing = state.loading
        adapter.submitList(state.transactions)
    }

    inner class TransactionAdapter : ListAdapter<SafeRepository.InstantTransfer, ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending_tx, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: SafeRepository.InstantTransfer) {
            itemView.tx_info_target.setAddress(item.to)
            itemView.tx_info_value.text = "${item.amount.shiftedString(item.tokenInfo?.decimals ?: 0)} ${item.tokenInfo?.symbol ?: ""}"
            itemView.tx_info_description.isVisible = false
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SafeRepository.InstantTransfer>() {
        override fun areItemsTheSame(oldItem: SafeRepository.InstantTransfer, newItem: SafeRepository.InstantTransfer) =
            oldItem.txHash == newItem.txHash

        override fun areContentsTheSame(oldItem: SafeRepository.InstantTransfer, newItem: SafeRepository.InstantTransfer) =
            oldItem == newItem

    }

    companion object {
        fun newInstance() = InstantTransferListScreen()
    }

}
