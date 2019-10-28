package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
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
import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.GnosisSafe
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.SafeRepository.Companion.TRANSFER_LIMIT_MODULE_ADDRESS
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.transactions.TransactionConfirmationDialog
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.item_transfer_limit.view.*
import kotlinx.android.synthetic.main.screen_manage_limit_transfer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class ManageLimitTransferContract : LoadingViewModel<ManageLimitTransferContract.State>() {
    abstract fun loadLimits()

    data class State(
        val loading: Boolean,
        val enabled: Boolean,
        val changeTx: SafeRepository.SafeTx?,
        val safe: Solidity.Address?,
        val limits: List<WrappedLimit>,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class WrappedLimit(
        val tokenInfo: SafeRepository.TokenInfo,
        val limit: SafeRepository.Limit
    )
}

@ExperimentalCoroutinesApi
class ManageLimitTransferViewModel(
    private val safeRepository: SafeRepository
) : ManageLimitTransferContract() {

    override val state = liveData {
        loadLimits()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun loadLimits() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            updateState { copy(safe = safe) }
            val modules = safeRepository.loadModules(safe)
            val index = modules.indexOf(TRANSFER_LIMIT_MODULE_ADDRESS)
            val data =
                if (index < 0)
                    GnosisSafe.EnableModule.encode(BuildConfig.TRANSFER_LIMIT_MODULE.asEthereumAddress()!!)
                else {
                    val prevModule = if (index == 0) "0x1".asEthereumAddress()!! else modules[index]
                    GnosisSafe.DisableModule.encode(prevModule, BuildConfig.TRANSFER_LIMIT_MODULE.asEthereumAddress()!!)
                }
            val changeTx = SafeRepository.SafeTx(
                safe, BigInteger.ZERO, data, SafeRepository.SafeTx.Operation.CALL
            )
            updateState { copy(enabled = index >= 0, changeTx = changeTx) }
            val limits = safeRepository.loadLimits(safe).map {
                val tokenInfo =
                    if (it.token == Solidity.Address(BigInteger.ZERO)) SafeRepository.ETH_TOKEN_INFO else safeRepository.loadTokenInfo(it.token)
                WrappedLimit(tokenInfo, it)
            }
            updateState { copy(loading = false, limits = limits) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, false, null, null, emptyList(), null)

}

@ExperimentalCoroutinesApi
class ManageLimitTransferActivity : BaseActivity<ManageLimitTransferContract.State, ManageLimitTransferContract>(),
    TransactionConfirmationDialog.Callback {
    override val viewModel: ManageLimitTransferContract by viewModel()
    private val adapter = TransactionAdapter()
    private val layoutManager = LinearLayoutManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_manage_limit_transfer)
        manage_limit_transfer_list.adapter = adapter
        manage_limit_transfer_list.layoutManager = layoutManager
        manage_limit_transfer_back_btn.setOnClickListener { onBackPressed() }
        manage_limit_transfer_refresh.setOnRefreshListener {
            viewModel.loadLimits()
        }
    }

    override fun updateState(state: ManageLimitTransferContract.State) {
        manage_limit_transfer_enable_label.text = if (state.enabled) "Click to disable" else "Click to enable"
        manage_limit_transfer_enable_btn.isChecked = state.enabled
        manage_limit_transfer_enable_bg.setOnClickListener {
            state.safe ?: return@setOnClickListener
            state.changeTx?.let {
                TransactionConfirmationDialog(this, state.safe, null, it).show()
            }
        }
        manage_limit_transfer_refresh.isRefreshing = state.loading
        adapter.submitList(state.limits)
    }

    inner class TransactionAdapter : ListAdapter<ManageLimitTransferContract.WrappedLimit, ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transfer_limit, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ManageLimitTransferContract.WrappedLimit) {
            itemView.transfer_limit_target.isVisible = false
            itemView.transfer_limit_token.text = item.tokenInfo.symbol
            itemView.transfer_limit_amount.text =
                "${item.limit.spent.shiftedString(item.tokenInfo.decimals)}/${item.limit.amount.shiftedString(item.tokenInfo.decimals)}"
        }
    }

    override fun onConfirmed() {
        viewModel.loadLimits()
    }

    class DiffCallback : DiffUtil.ItemCallback<ManageLimitTransferContract.WrappedLimit>() {
        override fun areItemsTheSame(oldItem: ManageLimitTransferContract.WrappedLimit, newItem: ManageLimitTransferContract.WrappedLimit) =
            oldItem.limit.token == newItem.limit.token

        override fun areContentsTheSame(oldItem: ManageLimitTransferContract.WrappedLimit, newItem: ManageLimitTransferContract.WrappedLimit) =
            oldItem == newItem

    }

    companion object {
        fun createIntent(context: Context) = Intent(context, ManageLimitTransferActivity::class.java)
    }

}
