package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.gnosis.safe.authenticator.AllowanceModule
import io.gnosis.safe.authenticator.GnosisSafe
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.SafeRepository.Companion.ALLOWANCE_MODULE_ADDRESS
import io.gnosis.safe.authenticator.repositories.TokensRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.transactions.TransactionConfirmationDialog
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.item_allowance.view.*
import kotlinx.android.synthetic.main.screen_manage_allowances.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger

abstract class ManageAllowancesContract(context: Context) : LoadingViewModel<ManageAllowancesContract.State>(context) {
    abstract fun loadAllowances()

    data class State(
        val loading: Boolean,
        val enabled: Boolean,
        val accessTx: SafeRepository.SafeTx?,
        val changeTx: SafeRepository.SafeTx?,
        val safe: Solidity.Address?,
        val allowances: List<WrappedAllowance>,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class WrappedAllowance(
        val tokenInfo: TokensRepository.TokenInfo,
        val allowance: SafeRepository.Allowance
    )
}

class ManageAllowancesViewModel(
    context: Context,
    private val safeRepository: SafeRepository,
    private val tokensRepository: TokensRepository
) : ManageAllowancesContract(context) {

    override fun onStart() {
        loadAllowances()
    }

    override fun loadAllowances() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            updateState { copy(safe = safe) }
            val modules = safeRepository.loadModules(safe)
            val index = modules.indexOf(ALLOWANCE_MODULE_ADDRESS)
            val data =
                if (index < 0)
                    GnosisSafe.EnableModule.encode(ALLOWANCE_MODULE_ADDRESS)
                else {
                    val prevModule = if (index == 0) "0x1".asEthereumAddress()!! else modules[index]
                    GnosisSafe.DisableModule.encode(prevModule, ALLOWANCE_MODULE_ADDRESS)
                }
            val changeTx = SafeRepository.SafeTx(
                safe, BigInteger.ZERO, data, SafeRepository.SafeTx.Operation.CALL
            )
            val enabled = index >= 0
            updateState { copy(enabled = enabled, changeTx = changeTx) }
            if (enabled) {
                val deviceId = safeRepository.loadDeviceId()
                val delegates = safeRepository.loadAllowancesDelegates(safe)
                val accessTx = if (!delegates.contains(deviceId)) {
                    SafeRepository.SafeTx(
                        ALLOWANCE_MODULE_ADDRESS, BigInteger.ZERO, AllowanceModule.AddDelegate.encode(deviceId), SafeRepository.SafeTx.Operation.CALL
                    )
                } else null
                updateState { copy(accessTx = accessTx) }
            } else updateState { copy(accessTx = null) }
            val allowances = safeRepository.loadAllowances(safe).map {
                val tokenInfo = tokensRepository.loadTokenInfo(it.token)
                WrappedAllowance(tokenInfo, it)
            }
            updateState { copy(loading = false, allowances = allowances) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, false, null, null, null, emptyList(), null)

}

class ManageAllowancesActivity : BaseActivity<ManageAllowancesContract.State, ManageAllowancesContract>(),
    TransactionConfirmationDialog.Callback {
    override val viewModel: ManageAllowancesContract by viewModel()
    private val adapter = TransactionAdapter()
    private val layoutManager = LinearLayoutManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_manage_allowances)
        manage_allowances_list.adapter = adapter
        manage_allowances_list.layoutManager = layoutManager
        manage_allowances_back_btn.setOnClickListener { onBackPressed() }
        manage_allowances_refresh.setOnRefreshListener {
            viewModel.loadAllowances()
        }
        manage_allowances_add_btn.setOnClickListener {
            startActivity(SetAllowanceActivity.createIntent(this))
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAllowances()
    }

    override fun updateState(state: ManageAllowancesContract.State) {
        manage_allowances_request_access_btn.isVisible = state.accessTx != null
        manage_allowances_request_access_btn.setOnClickListener {
            state.safe ?: return@setOnClickListener
            state.accessTx?.let {
                TransactionConfirmationDialog.show(supportFragmentManager, state.safe, null, it)
            }
        }
        manage_allowances_enable_label.text = if (state.enabled) "Click to disable" else "Click to enable"
        manage_allowances_enable_btn.isChecked = state.enabled
        manage_allowances_enable_bg.setOnClickListener {
            state.safe ?: return@setOnClickListener
            state.changeTx?.let {
                TransactionConfirmationDialog.show(supportFragmentManager, state.safe, null, it)
            }
        }
        manage_allowances_refresh.isRefreshing = state.loading
        adapter.submitList(state.allowances)
    }

    inner class TransactionAdapter : ListAdapter<ManageAllowancesContract.WrappedAllowance, ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_allowance, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ManageAllowancesContract.WrappedAllowance) {
            itemView.allowance_target.isVisible = false
            itemView.allowance_token.text = item.tokenInfo.symbol
            itemView.allowance_amount.text =
                "${item.allowance.spent.shiftedString(item.tokenInfo.decimals)}/${item.allowance.amount.shiftedString(item.tokenInfo.decimals)}"
        }
    }

    override fun onConfirmed(hash: String) {
        viewModel.loadAllowances()
    }

    override fun onRejected() {}

    class DiffCallback : DiffUtil.ItemCallback<ManageAllowancesContract.WrappedAllowance>() {
        override fun areItemsTheSame(oldItem: ManageAllowancesContract.WrappedAllowance, newItem: ManageAllowancesContract.WrappedAllowance) =
            oldItem.allowance.token == newItem.allowance.token

        override fun areContentsTheSame(oldItem: ManageAllowancesContract.WrappedAllowance, newItem: ManageAllowancesContract.WrappedAllowance) =
            oldItem == newItem

    }

    companion object {
        fun createIntent(context: Context) = Intent(context, ManageAllowancesActivity::class.java)
    }

}
