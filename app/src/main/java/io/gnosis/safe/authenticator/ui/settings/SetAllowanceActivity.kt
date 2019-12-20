package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.gnosis.safe.authenticator.AllowanceModule
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity
import io.gnosis.safe.authenticator.ui.transactions.TransactionConfirmationDialog
import io.gnosis.safe.authenticator.utils.MultiSendTransactionBuilder
import io.gnosis.safe.authenticator.utils.useAsAddress
import kotlinx.android.synthetic.main.screen_set_allowance.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.decimalAsBigInteger
import java.math.BigInteger

abstract class SetAllowanceContract(context: Context) : LoadingViewModel<SetAllowanceContract.State>(context) {
    abstract fun setAllowance(
        delegate: String,
        token: String,
        allowance: String,
        resetPeriod: String
    )

    data class State(
        val loading: Boolean,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class ConfirmTransaction(val safe: Solidity.Address, val tx: SafeRepository.SafeTx) : ViewAction
}

class SetAllowanceViewModel(
    context: Context,
    private val safeRepository: SafeRepository
) : SetAllowanceContract(context) {

    private inline fun <T> parseInput(errorMsg: String, func: () -> T?): T = try {
        func.invoke()!!
    } catch (e: Exception) {
        throw IllegalArgumentException(errorMsg, e)
    }

    override fun setAllowance(
        delegate: String,
        token: String,
        allowance: String,
        resetPeriod: String
    ) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val delegateAddress =
                if (delegate.isBlank()) safeRepository.loadDeviceId()
                else parseInput("Invalid delegate address") { delegate.asEthereumAddress() }
            val tokenAddress = parseInput("Invalid token address") { token.asEthereumAddress() }
            val allowanceValue = parseInput("Invalid allowance value") { allowance.decimalAsBigInteger() }
            val resetPeriodValue = parseInput("Invalid reset period") { resetPeriod.decimalAsBigInteger() }
            val setAllowanceData = AllowanceModule.SetAllowance.encode(
                delegateAddress, tokenAddress, Solidity.UInt96(allowanceValue), Solidity.UInt16(resetPeriodValue), Solidity.UInt32(resetPeriodValue)
            )
            val delegates = safeRepository.loadAllowancesDelegates(safe)
            val allowanceTx = SafeRepository.SafeTx(
                SafeRepository.ALLOWANCE_MODULE_ADDRESS, BigInteger.ZERO, setAllowanceData, SafeRepository.SafeTx.Operation.CALL
            )
            val tx = if (!delegates.contains(delegateAddress)) {
                val delegateData = AllowanceModule.AddDelegate.encode(delegateAddress)
                val delegateTx = SafeRepository.SafeTx(
                    SafeRepository.ALLOWANCE_MODULE_ADDRESS, BigInteger.ZERO, delegateData, SafeRepository.SafeTx.Operation.CALL
                )
                MultiSendTransactionBuilder.build(listOf(delegateTx, allowanceTx))
            } else allowanceTx
            updateState { copy(loading = false, viewAction = ConfirmTransaction(safe, tx)) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null)

}

class SetAllowanceActivity : BaseActivity<SetAllowanceContract.State, SetAllowanceContract>(), TransactionConfirmationDialog.Callback {

    override val viewModel: SetAllowanceContract by viewModel()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!QRCodeScanActivity.handleResult(requestCode, resultCode, data) { scanned ->
                set_allowance_delegate_input.useAsAddress(scanned)
            })
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_set_allowance)
        set_allowance_back_btn.setOnClickListener { onBackPressed() }
        set_allowance_submit_btn.setOnClickListener {
            viewModel.setAllowance(
                set_allowance_delegate_input.text.toString(),
                set_allowance_token_input.text.toString(),
                set_allowance_amount_input.text.toString(),
                set_allowance_period_input.text.toString()
            )
        }
        set_allowance_delegate_scan.setOnClickListener {
            QRCodeScanActivity.startForResult(this, "Please scan an Ethereum address")
        }
    }

    override fun updateState(state: SetAllowanceContract.State) {
        set_allowance_submit_btn.isEnabled = !state.loading
    }

    override fun performAction(viewAction: BaseViewModel.ViewAction): Boolean {
        return when (viewAction) {
            is SetAllowanceContract.ConfirmTransaction -> {
                TransactionConfirmationDialog.show(supportFragmentManager, viewAction.safe, null, viewAction.tx, null)
                true
            }
            else -> super.performAction(viewAction)
        }
    }

    override fun onConfirmed(hash: String) {
        finish()
    }

    override fun onRejected() {}

    companion object {
        fun createIntent(context: Context) = Intent(context, SetAllowanceActivity::class.java)
    }

}
