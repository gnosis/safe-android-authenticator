package io.gnosis.safe.authenticator.ui.transactions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.liveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.asMiddleEllipsized
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import kotlinx.android.synthetic.main.screen_pending_txs.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress

@ExperimentalCoroutinesApi
abstract class TransactionsContract: LoadingViewModel<TransactionsContract.State>() {
    abstract fun loadTransactions()

    data class State(val loading: Boolean, val transactions: List<SafeRepository.ServiceSafeTx>, override var viewAction: ViewAction?) :
        BaseViewModel.State
}

@ExperimentalCoroutinesApi
class TransactionsViewModel(
    private val safeRepository: SafeRepository
): TransactionsContract() {

    private val safe: Solidity.Address = "0xf11333E6558F64dBe99647AEB0D6D27a1b45E81c".asEthereumAddress()!!

    override val state = liveData {
        loadTransactions()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun loadTransactions() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val txs = safeRepository.loadPendingTransactions(safe)
            updateState { copy(loading = false, transactions = txs) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, emptyList(), null)

}

@ExperimentalCoroutinesApi
class TransactionsActivity: BaseActivity<TransactionsContract.State, TransactionsContract>() {
    override val viewModel: TransactionsContract by viewModel()
    private val adapter = TransactionAdapter()
    private val layoutManager = LinearLayoutManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_pending_txs)
        pending_txs_list.adapter = adapter
        pending_txs_list.layoutManager = layoutManager
        //pending_txs_back_btn.setOnClickListener { onBackPressed() }
        pending_txs_refresh.setOnRefreshListener {
            viewModel.loadTransactions()
        }
    }

    override fun updateState(state: TransactionsContract.State) {
        pending_txs_refresh.isRefreshing = state.loading
        adapter.submitList(state.transactions)
    }

    inner class TransactionAdapter : ListAdapter<SafeRepository.ServiceSafeTx, ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending_tx, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: SafeRepository.ServiceSafeTx) {
            itemView.setOnClickListener {
                //TransactionConfirmationDialog(this@TransactionActivity, item.tx, item.execInfo, item.confirmations).show()
            }
            itemView.pending_tx_target.setAddress(item.tx.to)
            itemView.pending_tx_confirmations.text = if (item.executed) null else "Pending"
            itemView.pending_tx_description.text = item.hash.asMiddleEllipsized(6)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SafeRepository.ServiceSafeTx>() {
        override fun areItemsTheSame(oldItem: SafeRepository.ServiceSafeTx, newItem: SafeRepository.ServiceSafeTx) =
            oldItem.hash == newItem.hash

        override fun areContentsTheSame(oldItem: SafeRepository.ServiceSafeTx, newItem: SafeRepository.ServiceSafeTx) =
            oldItem == newItem

    }

    companion object {
        fun createIntent(context: Context) = Intent(context, TransactionsActivity::class.java)
    }

}