package io.gnosis.safe.authenticator.ui.transactions

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.nullOnThrow
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_confirm_tx.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ViewModelParameters
import org.koin.androidx.viewmodel.getViewModel
import org.koin.core.parameter.parametersOf
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.getColorCompat
import java.lang.ref.WeakReference
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class TransactionConfirmationContract : LoadingViewModel<TransactionConfirmationContract.State>() {
    abstract fun confirmTransaction()
    data class State(
        val loading: Boolean,
        val fees: BigInteger?,
        val confirmed: Boolean,
        val txInfo: SafeRepository.TransactionInfo?,
        val txState: TransactionState?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class TransactionState(val confirmations: Int, val threshold: Int, val canSubmit: Boolean)
}

@ExperimentalCoroutinesApi
class TransactionConfirmationViewModel(
    private val safe: Solidity.Address,
    private val transactionHash: String,
    private val transaction: SafeRepository.SafeTx,
    private val executionInfo: SafeRepository.SafeTxExecInfo,
    private val safeRepository: SafeRepository
) : TransactionConfirmationContract() {

    override val state = liveData {
        loadFees()
        loadTransactionInfo()
        loadTransactionState()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private fun loadFees() {
        safeLaunch {
            updateState { copy(fees = executionInfo.fees) }
        }
    }

    private fun loadTransactionInfo() {
        safeLaunch {
            val info = safeRepository.loadTransactionInformation(safe, transaction)
            updateState { copy(txInfo = info) }
        }
    }

    private fun loadTransactionState() {
        loadingLaunch {
            updateState { copy(loading = true) }
            val deviceId = safeRepository.loadDeviceId()
            val transactionTask = async { safeRepository.loadPendingTransaction(transactionHash) }
            val safeInfo = safeRepository.loadSafeInfo(safe)
            val transactionInfo = nullOnThrow { transactionTask.await() }
            val confirmationCount = transactionInfo?.confirmations?.size ?: 0
            val isOwner = safeInfo.owners.contains(deviceId)
            val hasConfirmed = transactionInfo?.let { it.confirmations.find { (address, _) -> address == deviceId } != null } ?: false
            val canSubmit = isOwner && !hasConfirmed && !(transactionInfo?.executed ?: true) && executionInfo.nonce >= safeInfo.currentNonce
            val state = TransactionState(
                confirmationCount, safeInfo.threshold.toInt(), canSubmit
            )
            updateState { copy(loading = false, txState = state) }
        }
    }

    override fun confirmTransaction() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            safeRepository.confirmSafeTransaction(safe, transaction, executionInfo)
            updateState { copy(loading = false, confirmed = true) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, false, null, null, null)

}

@ExperimentalCoroutinesApi
class TransactionConfirmationDialog(
    activity: AppCompatActivity,
    safe: Solidity.Address,
    transactionHash: String,
    transaction: SafeRepository.SafeTx,
    executionInfo: SafeRepository.SafeTxExecInfo? = null
) : BottomSheetDialog(activity), LifecycleOwner, ViewModelStoreOwner {

    private val callback: WeakReference<Callback>? = (activity as? Callback)?.let { WeakReference(it) }

    private val lifecycle = LifecycleRegistry(this)
    override fun getLifecycle() = lifecycle
    override fun getViewModelStore() = ViewModelStore()

    private val viewModel = activity.getKoin().getViewModel(
        ViewModelParameters(
            TransactionConfirmationContract::class, activity, null, { this },
            parameters = { parametersOf(safe, transactionHash, transaction, executionInfo) }
        )
    )

    private val picasso: Picasso by activity.getKoin().inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_confirm_tx)
        confirm_tx_submit_btn.setOnClickListener {
            viewModel.confirmTransaction()
        }
        viewModel.state.observe(this, Observer {
            confirm_tx_submit_btn.isEnabled = !it.loading
            if (it.confirmed) {
                callback?.get()?.onConfirmed()
                dismiss()
            }
            confirm_tx_fee_value.text = it.fees?.shiftedString(18)
            confirm_tx_asset_label.text = it.txInfo?.assetLabel
            confirm_tx_target_label.text = it.txInfo?.recipientLabel
            confirm_tx_target_icon.setAddress(it.txInfo?.recipient)
            confirm_tx_description.text = it.txInfo?.additionalInfo
            confirm_tx_submit_btn.isVisible = it.txState?.canSubmit ?: false
            confirm_tx_confirmations_indicator.max = it.txState?.threshold ?: 0
            confirm_tx_confirmations_indicator.progress = it.txState?.confirmations ?: 0
            confirm_tx_asset_icon.setPadding(0)
            confirm_tx_asset_icon.background = null
            confirm_tx_asset_icon.setImageDrawable(null)
            confirm_tx_asset_icon.colorFilter = null
            when {
                it.txInfo?.assetIcon == "local::ethereum" -> {
                    confirm_tx_asset_icon.setPadding(context.resources.getDimension(R.dimen.icon_padding).toInt())
                    confirm_tx_asset_icon.setBackgroundResource(R.drawable.circle_background)
                    confirm_tx_asset_icon.setImageResource(R.drawable.ic_ethereum_logo)
                }
                it.txInfo?.assetIcon == "local::settings" -> {
                    confirm_tx_asset_icon.setColorFilter(context.getColorCompat(R.color.white))
                    confirm_tx_asset_icon.setPadding(context.resources.getDimension(R.dimen.icon_padding).toInt())
                    confirm_tx_asset_icon.setBackgroundResource(R.drawable.circle_background)
                    confirm_tx_asset_icon.setImageResource(R.drawable.ic_settings_24dp)
                }
                it.txInfo?.assetIcon?.startsWith("local::") == true -> { }
                !it.txInfo?.assetIcon.isNullOrBlank() ->
                    picasso.load(it.txInfo!!.assetIcon).into(confirm_tx_asset_icon)
            }
        })
        lifecycle.currentState = Lifecycle.State.CREATED
        setOnDismissListener {
            lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycle.currentState = Lifecycle.State.CREATED
    }

    override fun onStart() {
        super.onStart()
        // IDK why this is required
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        lifecycle.currentState = Lifecycle.State.RESUMED
    }

    interface Callback {
        fun onConfirmed()
    }

}