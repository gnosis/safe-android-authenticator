package io.gnosis.safe.authenticator.ui.transactions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_instant_transfer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigDecimal
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class InstantTransferContract : LoadingViewModel<InstantTransferContract.State>() {
    abstract fun submitInstantTransfer(
        allowance: WrappedAllowance?,
        to: String,
        value: String
    )

    data class State(
        val loading: Boolean,
        val allowances: List<WrappedAllowance>,
        val done: Boolean,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class WrappedAllowance(
        val label: String,
        val tokenInfo: SafeRepository.TokenInfo,
        val allowance: SafeRepository.Allowance
    ) {
        override fun toString(): String {
            return label
        }
    }
}

@ExperimentalCoroutinesApi
class InstantTransferViewModel(
    private val safeRepository: SafeRepository
) : InstantTransferContract() {

    override val state = liveData {
        loadAllowances()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun submitInstantTransfer(
        allowance: WrappedAllowance?,
        to: String,
        value: String
    ) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val deviceId = safeRepository.loadDeviceId()
            val toAddress = to.asEthereumAddress() ?: throw IllegalArgumentException("Invalid address provided!")
            val valueNumber = value.toBigDecimal().multiply(BigDecimal.TEN.pow(allowance!!.tokenInfo.decimals)).toBigInteger()
            safeRepository.performInstantTransfer(safe, deviceId, allowance.allowance, toAddress, valueNumber)
            updateState { copy(loading = false, done = true) }
        }
    }

    private fun loadAllowances() {
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val allowances = safeRepository.loadAllowances(safe).map {
                val tokenInfo =
                    if (it.token == Solidity.Address(BigInteger.ZERO)) SafeRepository.ETH_TOKEN_INFO else safeRepository.loadTokenInfo(it.token)
                WrappedAllowance("${tokenInfo.symbol} (${(it.amount - it.spent).shiftedString(tokenInfo.decimals)})", tokenInfo, it)
            }
            updateState { copy(loading = false, allowances = allowances) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, emptyList(), false, null)

}

@ExperimentalCoroutinesApi
class InstantTransferActivity : BaseActivity<InstantTransferContract.State, InstantTransferContract>() {
    override val viewModel: InstantTransferContract by viewModel()
    private lateinit var spinnerAdapter: ArrayAdapter<InstantTransferContract.WrappedAllowance>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_instant_transfer)
        instant_transfer_back_btn.setOnClickListener { onBackPressed() }
        instant_transfer_submit_btn.setOnClickListener {
            viewModel.submitInstantTransfer(
                (instant_transfer_list.selectedItem as? InstantTransferContract.WrappedAllowance),
                instant_transfer_recipient_input.text.toString(),
                instant_transfer_value_input.text.toString()
            )
        }
        spinnerAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, mutableListOf()
        )
        instant_transfer_list.adapter = spinnerAdapter
    }

    override fun updateState(state: InstantTransferContract.State) {
        if (state.done) {
            finish()
            return
        }
        instant_transfer_submit_btn.isEnabled = !state.loading
        spinnerAdapter.clear()
        spinnerAdapter.addAll(state.allowances)
        spinnerAdapter.notifyDataSetChanged()
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, InstantTransferActivity::class.java)
    }

}
