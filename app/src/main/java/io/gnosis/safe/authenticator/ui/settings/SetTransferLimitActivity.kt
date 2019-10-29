package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.TransferLimitModule
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.transactions.TransactionConfirmationDialog
import kotlinx.android.synthetic.main.screen_new_transaction.*
import kotlinx.android.synthetic.main.screen_set_transfer_limit.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.utils.*
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class SetTransferLimitContract : LoadingViewModel<SetTransferLimitContract.State>() {
    abstract fun setTransferLimit(
        token: String,
        limit: String,
        resetPeriod: String
    )

    data class State(
        val loading: Boolean,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class ConfirmTransaction(val safe: Solidity.Address, val tx: SafeRepository.SafeTx) : ViewAction
}

@ExperimentalCoroutinesApi
class SetTransferLimitViewModel(
    private val safeRepository: SafeRepository
) : SetTransferLimitContract() {

    override val state = liveData {
        for (event in stateChannel.openSubscription()) emit(event)
    }

    override fun setTransferLimit(
        token: String,
        limit: String,
        resetPeriod: String
    ) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val tokenAddress = token.asEthereumAddress()!!
            val limitValue = limit.decimalAsBigInteger()
            val resetPeriodValue = resetPeriod.decimalAsBigInteger()
            val setLimitData = TransferLimitModule.SetLimit.encode(
                tokenAddress, Solidity.UInt96(limitValue), Solidity.UInt16(resetPeriodValue)
            )
            val tx = SafeRepository.SafeTx(
                SafeRepository.TRANSFER_LIMIT_MODULE_ADDRESS, BigInteger.ZERO, setLimitData, SafeRepository.SafeTx.Operation.CALL
            )
            updateState { copy(loading = false, viewAction = ConfirmTransaction(safe, tx)) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null)

}

@ExperimentalCoroutinesApi
class SetTransferLimitActivity : BaseActivity<SetTransferLimitContract.State, SetTransferLimitContract>(), TransactionConfirmationDialog.Callback {

    override val viewModel: SetTransferLimitContract by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_set_transfer_limit)
        set_transfer_limit_back_btn.setOnClickListener { onBackPressed() }
        set_transfer_limit_submit_btn.setOnClickListener {
            viewModel.setTransferLimit(
                set_transfer_limit_token_input.text.toString(),
                set_transfer_limit_amount_input.text.toString(),
                set_transfer_limit_period_input.text.toString()
            )
        }
    }

    override fun updateState(state: SetTransferLimitContract.State) {
        set_transfer_limit_submit_btn.isEnabled = !state.loading
    }

    override fun performAction(viewAction: BaseViewModel.ViewAction) {
        when (viewAction) {
           is SetTransferLimitContract.ConfirmTransaction ->
               TransactionConfirmationDialog(this, viewAction.safe, null, viewAction.tx, null).show()
            else -> super.performAction(viewAction)
        }
    }

    override fun onConfirmed() {
        finish()
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, SetTransferLimitActivity::class.java)
    }

}