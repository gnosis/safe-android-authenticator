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
import kotlinx.android.synthetic.main.screen_limit_transfer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigDecimal
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class LimitTransferContract : LoadingViewModel<LimitTransferContract.State>() {
    abstract fun submitLimitTransfer(
        limit: WrappedLimit?,
        to: String,
        value: String
    )

    data class State(
        val loading: Boolean,
        val limits: List<WrappedLimit>,
        val done: Boolean,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class WrappedLimit(
        val label: String,
        val tokenInfo: SafeRepository.TokenInfo,
        val limit: SafeRepository.Limit
    ) {
        override fun toString(): String {
            return label
        }
    }
}

@ExperimentalCoroutinesApi
class LimitTransferViewModel(
    private val safeRepository: SafeRepository
) : LimitTransferContract() {

    override val state = liveData {
        loadLimits()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun submitLimitTransfer(
        limit: WrappedLimit?,
        to: String,
        value: String
    ) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val toAddress = to.asEthereumAddress() ?: throw IllegalArgumentException("Invalid address provided!")
            val valueNumber = value.toBigDecimal().multiply(BigDecimal.TEN.pow(limit!!.tokenInfo.decimals)).toBigInteger()
            safeRepository.performTransferLimit(safe, limit.limit, toAddress, valueNumber)
            updateState { copy(loading = false, done = true) }
        }
    }

    private fun loadLimits() {
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val limits = safeRepository.loadLimits(safe).map {
                val tokenInfo =
                    if (it.token == Solidity.Address(BigInteger.ZERO)) SafeRepository.ETH_TOKEN_INFO else safeRepository.loadTokenInfo(it.token)
                WrappedLimit("${tokenInfo.symbol} (${(it.amount - it.spent).shiftedString(tokenInfo.decimals)})", tokenInfo, it)
            }
            updateState { copy(loading = false, limits = limits) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, emptyList(), false, null)

}

@ExperimentalCoroutinesApi
class LimitTransferActivity : BaseActivity<LimitTransferContract.State, LimitTransferContract>() {
    override val viewModel: LimitTransferContract by viewModel()
    private lateinit var spinnerAdapter: ArrayAdapter<LimitTransferContract.WrappedLimit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_limit_transfer)
        limit_transfer_back_btn.setOnClickListener { onBackPressed() }
        limit_transfer_submit_btn.setOnClickListener {
            viewModel.submitLimitTransfer(
                (limit_transfer_list.selectedItem as? LimitTransferContract.WrappedLimit),
                limit_transfer_recipient_input.text.toString(),
                limit_transfer_value_input.text.toString()
            )
        }
        spinnerAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, mutableListOf()
        )
        limit_transfer_list.adapter = spinnerAdapter
    }

    override fun updateState(state: LimitTransferContract.State) {
        if (state.done) {
            finish()
            return
        }
        limit_transfer_submit_btn.isEnabled = !state.loading
        spinnerAdapter.clear()
        spinnerAdapter.addAll(state.limits)
        spinnerAdapter.notifyDataSetChanged()
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, LimitTransferActivity::class.java)
    }

}
