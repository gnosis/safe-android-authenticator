package io.gnosis.safe.authenticator.ui.instant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.TokensRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.overview.MainActivity
import io.gnosis.safe.authenticator.ui.overview.OverviewTypeSwitchCallback
import io.gnosis.safe.authenticator.utils.nullOnThrow
import io.gnosis.safe.authenticator.utils.setTransactionIcon
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_instant_transfer_review.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.hexAsBigInteger
import pm.gnosis.utils.toHexString
import java.math.BigInteger

abstract class NewInstantTransferReviewContract(context: Context) : LoadingViewModel<NewInstantTransferReviewContract.State>(context) {

    abstract fun submitInstantTransfer()

    data class State(
        val loading: Boolean,
        val tokenInfo: TokensRepository.TokenInfo?,
        val amountText: String?,
        val recipient: Solidity.Address?,
        val recipientText: String?,
        val done: Boolean,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

class NewInstantTransferReviewViewModel(
    context: Context,
    private val safeRepository: SafeRepository,
    private val tokensRepository: TokensRepository,
    private val token: Solidity.Address,
    private val recipient: Solidity.Address,
    private val amount: BigInteger
) : NewInstantTransferReviewContract(context) {

    override fun onStart() {
        loadTransferInfo()
    }

    private fun loadTransferInfo() {
        loadingLaunch {
            updateState { copy(loading = true) }
            val formattedRecipient = recipient.asEthereumAddressChecksumString().splitWithNewline()
            val recipientAddress = recipient
            val tokenInfo = nullOnThrow { tokensRepository.loadTokenInfo(token) }
            val symbol = tokenInfo?.symbol ?: ""
            val amountText = "${amount.shiftedString(tokenInfo?.decimals ?: 0)} $symbol".trim()
            updateState {
                copy(
                    loading = false,
                    recipient = recipientAddress,
                    recipientText = formattedRecipient,
                    tokenInfo = tokenInfo,
                    amountText = amountText
                )
            }
        }
    }

    private fun String.splitWithNewline() =
        "${substring(0, length / 2)}\n${substring(length / 2)}"


    override fun submitInstantTransfer() {
        if (state.value?.loading == true) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val safe = safeRepository.loadSafeAddress()
            val deviceId = safeRepository.loadDeviceId()
            val allowance = safeRepository.loadAllowance(safe, token)
            safeRepository.performInstantTransfer(safe, deviceId, allowance, recipient, amount)
            updateState { copy(loading = false, done = true) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable): State = state.copy(loading = false)

    override fun initialState() = State(false, null, null, null, null, false, null)

}

class NewInstantTransferReviewActivity : BaseActivity<NewInstantTransferReviewContract.State, NewInstantTransferReviewContract>() {

    private val picasso: Picasso by inject()

    override val viewModel: NewInstantTransferReviewContract by viewModel(
        parameters = {
            parametersOf(
                intent.getStringExtra(EXTRA_SELECTED_TOKEN)?.asEthereumAddress()!!,
                intent.getStringExtra(EXTRA_SELECTED_ADDRESS)?.asEthereumAddress()!!,
                intent.getStringExtra(EXTRA_SELECTED_AMOUNT)?.hexAsBigInteger()
            )
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_instant_transfer_review)
        instant_transfer_review_back_btn.setOnClickListener { onBackPressed() }
        instant_transfer_review_continue_btn.setOnClickListener {
            viewModel.submitInstantTransfer()
        }
        instant_transfer_review_steps.defaultCount = 3
        instant_transfer_review_steps.setSelectedPage(2)
    }

    override fun updateState(state: NewInstantTransferReviewContract.State) {
        instant_transfer_review_progress.isVisible = state.loading
        instant_transfer_review_continue_btn.isVisible = !state.loading
        instant_transfer_review_recipient_text.text = state.recipientText
        instant_transfer_review_recipient_ident.setAddress(state.recipient)
        instant_transfer_review_amount_text.text = state.amountText
        instant_transfer_review_amount_icon.setTransactionIcon(picasso, state.tokenInfo?.icon)
        if (state.done) {
            startActivity(MainActivity.createIntent(this, R.id.navigation_transactions, OverviewTypeSwitchCallback.Type.ALLOWANCE))
            finish()
        }
    }

    companion object {
        private const val EXTRA_SELECTED_TOKEN = "extra.string.selected_token"
        private const val EXTRA_SELECTED_ADDRESS = "extra.string.selected_address"
        private const val EXTRA_SELECTED_AMOUNT = "extra.string.selected_amount"
        fun createIntent(context: Context, token: Solidity.Address, to: Solidity.Address, amount: BigInteger) =
            Intent(context, NewInstantTransferReviewActivity::class.java).apply {
                putExtra(EXTRA_SELECTED_TOKEN, token.asEthereumAddressString())
                putExtra(EXTRA_SELECTED_ADDRESS, to.asEthereumAddressString())
                putExtra(EXTRA_SELECTED_AMOUNT, amount.toHexString())
            }
    }

}
