package io.gnosis.safe.authenticator.ui.instant

import android.content.Context
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
import io.gnosis.safe.authenticator.ui.base.BaseFragment
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import kotlinx.android.synthetic.main.screen_instant_transfers.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

abstract class InstantTransferListContract(context: Context) : LoadingViewModel<InstantTransferListContract.State>(context) {
    abstract fun loadTransfers()

    data class State(
        val loading: Boolean,
        val transactions: List<ListEntry>?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

class InstantTransferListViewModel(
    context: Context,
    private val safeRepository: SafeRepository
) : InstantTransferListContract(context) {

    override fun onStart() {
        loadTransfers()
    }

    override fun loadTransfers() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val executedTransfers = safeRepository.loadExecutedInstantTransfers().map {
                InstantTransferMetaEntry(it, R.id.entry_type_executed_instant_transfer)
            }
            val pendingTransfers = safeRepository.loadPendingInstantTransfers().map {
                InstantTransferMetaEntry(it, R.id.entry_type_pending_instant_transfer)
            }
            val transfers = mutableListOf<ListEntry>()
                .maybeAddWithHeader("Pending", pendingTransfers)
                .maybeAddWithHeader("History", executedTransfers)
            updateState { copy(loading = false, transactions = transfers) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, null)

}

class InstantTransferListScreen : BaseFragment<InstantTransferListContract.State, InstantTransferListContract>() {
    override val viewModel: InstantTransferListContract by viewModel()
    private val picasso: Picasso by inject()
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
        instant_transfers_empty_views.isVisible = state.transactions?.isEmpty() == true
    }

    inner class TransactionAdapter : ListAdapter<ListEntry, ListEntryViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            viewType.typeToTransactionViewHolder(parent, picasso)

        override fun getItemViewType(position: Int): Int {
            return getItem(position).type
        }

        override fun onBindViewHolder(holder: ListEntryViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    companion object {
        fun newInstance() = InstantTransferListScreen()
    }

}
