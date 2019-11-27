package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.AllowanceModule
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.transactions.TransactionConfirmationDialog
import kotlinx.android.synthetic.main.screen_set_allowance.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.*
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class SetAllowanceContract : LoadingViewModel<SetAllowanceContract.State>() {
    abstract fun setAllowance(
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

@ExperimentalCoroutinesApi
class SetAllowanceViewModel(
    private val safeRepository: SafeRepository
) : SetAllowanceContract() {

    override val state = liveData {
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun setAllowance(
        token: String,
        allowance: String,
        resetPeriod: String
    ) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val deviceId = safeRepository.loadDeviceId()
            val tokenAddress = token.asEthereumAddress()!!
            val allowanceValue = allowance.decimalAsBigInteger()
            val resetPeriodValue = resetPeriod.decimalAsBigInteger()
            val setAllowanceData = AllowanceModule.SetAllowance.encode(
                deviceId, tokenAddress, Solidity.UInt96(allowanceValue), Solidity.UInt16(resetPeriodValue)
            )
            val tx = SafeRepository.SafeTx(
                SafeRepository.ALLOWANCE_MODULE_ADDRESS, BigInteger.ZERO, setAllowanceData, SafeRepository.SafeTx.Operation.CALL
            )
            updateState { copy(loading = false, viewAction = ConfirmTransaction(safe, tx)) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null)

}

@ExperimentalCoroutinesApi
class SetAllowanceActivity : BaseActivity<SetAllowanceContract.State, SetAllowanceContract>(), TransactionConfirmationDialog.Callback {

    override val viewModel: SetAllowanceContract by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_set_allowance)
        set_allowance_back_btn.setOnClickListener { onBackPressed() }
        set_allowance_submit_btn.setOnClickListener {
            viewModel.setAllowance(
                set_allowance_token_input.text.toString(),
                set_allowance_amount_input.text.toString(),
                set_allowance_period_input.text.toString()
            )
        }
    }

    override fun updateState(state: SetAllowanceContract.State) {
        set_allowance_submit_btn.isEnabled = !state.loading
    }

    override fun performAction(viewAction: BaseViewModel.ViewAction) {
        when (viewAction) {
           is SetAllowanceContract.ConfirmTransaction ->
               TransactionConfirmationDialog(this, viewAction.safe, null, viewAction.tx, null).show()
            else -> super.performAction(viewAction)
        }
    }

    override fun onConfirmed() {
        finish()
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, SetAllowanceActivity::class.java)
    }

}
