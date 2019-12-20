package io.gnosis.safe.authenticator.ui.transactions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import kotlinx.android.synthetic.main.screen_new_transaction.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.utils.*
import java.math.BigInteger

abstract class NewTransactionContract(context: Context) : LoadingViewModel<NewTransactionContract.State>(context) {
    abstract fun submitTransaction(
        to: String,
        value: String,
        data: String,
        nonce: String
    )

    data class State(
        val loading: Boolean,
        val currentNonce: BigInteger?,
        val done: Boolean,
        override var viewAction: ViewAction?
    ) :
        BaseViewModel.State
}

class NewTransactionViewModel(
    context: Context,
    private val safeRepository: SafeRepository
) : NewTransactionContract(context) {

    override fun onStart() {
        loadNonce()
    }

    override fun submitTransaction(
        to: String,
        value: String,
        data: String,
        nonce: String
    ) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val toAddress = to.asEthereumAddress()!!
            val weiValue = value.decimalAsBigInteger()
            val dataBytes = data.hexStringToByteArray()
            val nonceValue = nonce.decimalAsBigInteger()
            val tx = SafeRepository.SafeTx(
                toAddress, weiValue, dataBytes.toHexString().addHexPrefix(), SafeRepository.SafeTx.Operation.CALL
            )
            val execInfo = safeRepository.loadSafeTransactionExecutionInformation(safe, tx, nonceValue)
            safeRepository.confirmSafeTransaction(safe, tx, execInfo)
            updateState { copy(loading = false, done = true) }
        }
    }

    private fun loadNonce() {
        safeLaunch {
            val safe = safeRepository.loadSafeAddress()
            val nonce = safeRepository.loadSafeNonce(safe)
            updateState { copy(currentNonce = nonce) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, false, null)

}

class NewTransactionActivity : BaseActivity<NewTransactionContract.State, NewTransactionContract>() {
    override val viewModel: NewTransactionContract by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_new_transaction)
        new_transaction_back_btn.setOnClickListener { onBackPressed() }
        new_transaction_submit_btn.setOnClickListener {
            viewModel.submitTransaction(
                new_transaction_recipient_input.text.toString(),
                new_transaction_value_input.text.toString(),
                new_transaction_data_input.text.toString(),
                new_transaction_nonce_input.text.toString()
            )
        }
    }

    override fun updateState(state: NewTransactionContract.State) {
        new_transaction_submit_btn.isEnabled = !state.loading
        if (new_transaction_nonce_input.text.toString().isBlank()) {
            new_transaction_nonce_input.setText(state.currentNonce?.toString())
        }
        if (state.done) finish()
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, NewTransactionActivity::class.java)
    }

}
