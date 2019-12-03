package io.gnosis.safe.authenticator.ui.instant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.TokensRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity
import io.gnosis.safe.authenticator.utils.shiftedString
import io.gnosis.safe.authenticator.utils.useAsAddress
import kotlinx.android.synthetic.main.screen_instant_transfer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import java.math.BigDecimal

@ExperimentalCoroutinesApi
abstract class NewInstantTransferContract : LoadingViewModel<NewInstantTransferContract.State>() {
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
        val token: Solidity.Address,
        val tokenInfo: TokensRepository.TokenInfo,
        val allowance: SafeRepository.Allowance
    ) {
        override fun toString(): String {
            return label
        }
    }
}

@ExperimentalCoroutinesApi
class NewInstantTransferViewModel(
    private val safeRepository: SafeRepository,
    private val tokensRepository: TokensRepository
) : NewInstantTransferContract() {

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
                val tokenInfo = tokensRepository.loadTokenInfo(it.token)
                WrappedAllowance(
                    "${tokenInfo.symbol} (${(it.amount - it.spent).shiftedString(
                        tokenInfo.decimals
                    )})", it.token, tokenInfo, it
                )
            }
            updateState { copy(loading = false, allowances = allowances) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, emptyList(), false, null)

}

@ExperimentalCoroutinesApi
class NewInstantTransferActivity : BaseActivity<NewInstantTransferContract.State, NewInstantTransferContract>() {
    override val viewModel: NewInstantTransferContract by viewModel()
    private lateinit var spinnerAdapter: ArrayAdapter<NewInstantTransferContract.WrappedAllowance>
    private var selectedToken: Solidity.Address? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!QRCodeScanActivity.handleResult(requestCode, resultCode, data, { scanned ->
                instant_transfer_recipient_input.useAsAddress(scanned)
            }))
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_instant_transfer)
        selectedToken = intent.getStringExtra(EXTRA_SELECTED_TOKEN)?.asEthereumAddress()
        instant_transfer_back_btn.setOnClickListener { onBackPressed() }
        instant_transfer_submit_btn.setOnClickListener {
            viewModel.submitInstantTransfer(
                (instant_transfer_list.selectedItem as? NewInstantTransferContract.WrappedAllowance),
                instant_transfer_recipient_input.text.toString(),
                instant_transfer_value_input.text.toString()
            )
        }
        instant_transfer_recipient_scan.setOnClickListener {
            QRCodeScanActivity.startForResult(this, "Please scan an Ethereum address")
        }
        spinnerAdapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, mutableListOf()
        )
        instant_transfer_list.adapter = spinnerAdapter
        instant_transfer_list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p0: AdapterView<*>?) {
                selectedToken = null
            }

            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                selectedToken = (instant_transfer_list.selectedItem as? NewInstantTransferContract.WrappedAllowance)?.token
            }

        }
    }

    override fun updateState(state: NewInstantTransferContract.State) {
        if (state.done) {
            finish()
            return
        }
        instant_transfer_submit_btn.isEnabled = !state.loading
        spinnerAdapter.clear()
        spinnerAdapter.addAll(state.allowances)
        spinnerAdapter.notifyDataSetChanged()
        selectedToken?.let { token ->
            val selected = state.allowances.indexOfFirst { it.token == token }
            if (selected >= 0) instant_transfer_list.setSelection(selected)
        }

    }

    companion object {
        private const val EXTRA_SELECTED_TOKEN = "extra.string.selected_token"
        fun createIntent(context: Context, selected: Solidity.Address? = null) = Intent(context, NewInstantTransferActivity::class.java).apply {
            putExtra(EXTRA_SELECTED_TOKEN, selected?.asEthereumAddressString())
        }
    }

}
