package io.gnosis.safe.authenticator.ui.instant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.TokensRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.utils.KeyboardEventListener
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_instant_transfer_value_input.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import java.math.BigDecimal
import java.math.BigInteger

abstract class NewInstantTransferValueInputContract(context: Context) : BaseViewModel<NewInstantTransferValueInputContract.State>(context) {

    abstract fun checkInput(value: String)

    data class State(
        val tokenInfo: TokensRepository.TokenInfo?,
        val availableTokens: BigInteger?,
        val selectedAmount: BigInteger?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

class NewInstantTransferValueInputViewModel(
    context: Context,
    private val safeRepository: SafeRepository,
    private val tokensRepository: TokensRepository,
    private val token: Solidity.Address
) : NewInstantTransferValueInputContract(context) {

    override fun onStart() {
        loadTokenInformation()
    }

    // View model caches
    private var tokenInfo: TokensRepository.TokenInfo? = null
    private var tokenBalance: BigInteger? = null
    private var tokenAllowance: BigInteger? = null
    private var tokenInfoJob: Job? = null
    private fun loadTokenInformation(): Job =
        if (tokenInfoJob?.isActive == true) tokenInfoJob!!
        else safeLaunch {
            val safe = safeRepository.loadSafeAddress()
            val infoJob = launch {
                tokenInfo ?: tokensRepository.loadTokenInfo(token).let {
                    if (state.value?.tokenInfo != it) updateState { copy(tokenInfo = it) }
                    tokenInfo = it
                }
            }
            val balanceJob = launch {
                tokenBalance ?: safeRepository.loadTokenBalances(safe).find { (address) -> address == token }!!.second.let { tokenBalance = it }
            }
            val allowanceJob = launch {
                tokenAllowance ?: safeRepository.loadAllowance(safe, token).run { tokenAllowance = amount - spent }
            }
            balanceJob.join()
            allowanceJob.join()
            tokenBalance?.let { balance ->
                tokenAllowance?.let { allowance ->
                    val availableTokens = allowance.min(balance)
                    if (state.value?.availableTokens != availableTokens) updateState { copy(availableTokens = availableTokens) }
                }
            }
            infoJob.join()
        }.apply { tokenInfoJob = this }

    private var checkJob: Job? = null
    override fun checkInput(value: String) {
        checkJob?.cancel()
        checkJob = safeLaunch {
            updateState { copy(selectedAmount = null) }
            if (value.isBlank()) return@safeLaunch
            delay(500)
            val amount = value.toBigDecimalOrNull() ?: throw IllegalArgumentException(context.getString(R.string.invalid_amount))
            loadTokenInformation().join() // Wait for required information
            val tokenInfo = tokenInfo!!
            val tokenAmount = amount.multiply(BigDecimal.TEN.pow(tokenInfo.decimals)).toBigInteger()
            val availableTokens = state.value!!.availableTokens!!
            if (tokenAmount > availableTokens) throw IllegalArgumentException(context.getString(R.string.amount_too_high))
            updateState { copy(selectedAmount = tokenAmount) }
        }
    }

    override fun initialState() = State(null, null, null, null)

}

class NewInstantTransferValueInputActivity : BaseActivity<NewInstantTransferValueInputContract.State, NewInstantTransferValueInputContract>() {

    override val viewModel: NewInstantTransferValueInputContract by viewModel(
        parameters = { parametersOf(intent.getStringExtra(EXTRA_SELECTED_TOKEN)?.asEthereumAddress()!!) }
    )

    override fun updateState(state: NewInstantTransferValueInputContract.State) {
        instant_transfer_value_input_allowance.isVisible = state.tokenInfo != null && state.availableTokens != null
        state.tokenInfo?.let { tokenInfo ->
            instant_transfer_value_input_allowance.text =
                state.availableTokens?.let { getString(R.string.available_allowance, it.shiftedString(tokenInfo.decimals)) }
            instant_transfer_value_input_hint_text.text = getString(R.string.instant_transfer_value_message, tokenInfo.symbol)
            instant_transfer_value_input_symbol.text = tokenInfo.symbol
        }
        instant_transfer_value_input_continue_btn.isEnabled = state.selectedAmount != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_instant_transfer_value_input)
        // use android.R.id.content to check keyboard
        instant_transfer_value_input_back_btn.setOnClickListener { onBackPressed() }
        instant_transfer_value_input_continue_btn.setOnClickListener {
            println("next")
            // TODO start next activity
        }
        instant_transfer_value_input_amount.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_GO -> {
                    if (instant_transfer_value_input_continue_btn.isEnabled)
                        instant_transfer_value_input_continue_btn.performClick()
                    true
                }
                else -> false
            }
        }
        instant_transfer_value_input_amount.addTextChangedListener {
            viewModel.checkInput(it.toString())
        }
        instant_transfer_value_input_steps.defaultCount = 3
        instant_transfer_value_input_steps.setSelectedPage(1)
        KeyboardEventListener(this) { isOpen ->
            instant_transfer_value_input_steps.isVisible = !isOpen
            instant_transfer_value_input_continue_btn.isVisible = !isOpen
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(mainLooper).postDelayed({ instant_transfer_value_input_amount.showKeyboardForView() }, 400)
    }

    companion object {
        private const val EXTRA_SELECTED_TOKEN = "extra.string.selected_token"
        private const val EXTRA_SELECTED_ADDRESS = "extra.string.selected_address"
        fun createIntent(context: Context, token: Solidity.Address, to: Solidity.Address) =
            Intent(context, NewInstantTransferValueInputActivity::class.java).apply {
                putExtra(EXTRA_SELECTED_TOKEN, token.asEthereumAddressString())
                putExtra(EXTRA_SELECTED_ADDRESS, to.asEthereumAddressString())
            }
    }

}
