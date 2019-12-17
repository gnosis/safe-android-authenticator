package io.gnosis.safe.authenticator.ui.transactions

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.nullOnThrow
import io.gnosis.safe.authenticator.utils.setTransactionIcon
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_confirm_tx.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.ViewModelParameters
import org.koin.androidx.viewmodel.getViewModel
import org.koin.core.parameter.parametersOf
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.utils.addHexPrefix
import java.lang.ref.WeakReference
import java.math.BigInteger

abstract class TransactionConfirmationContract(context: Context) : LoadingViewModel<TransactionConfirmationContract.State>(context) {
    abstract fun confirmTransaction()
    data class State(
        val loading: Boolean,
        val fees: BigInteger?,
        val signedHash: String?,
        val txInfo: SafeRepository.TransactionInfo?,
        val txState: TransactionState?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State

    data class TransactionState(val confirmations: Int, val threshold: Int, val submissionState: SubmissionState)

    enum class SubmissionState {
        PENDING, // User is not an owner
        AWAITING_CONFIRMATION, // User can confirm
        CONFIRMED, // User has confirmed
        CANCELED, // Tx has been canceled
        EXECUTED, // Tx has been executed
    }
}

class TransactionConfirmationViewModel(
    context: Context,
    private val safe: Solidity.Address,
    private val transactionHash: String?,
    private val transaction: SafeRepository.SafeTx,
    private val executionInfo: SafeRepository.SafeTxExecInfo?,
    private val safeRepository: SafeRepository
) : TransactionConfirmationContract(context) {

    override fun onStart() {
        loadFees()
        loadTransactionInfo()
        loadTransactionState()
    }

    private fun loadFees() {
        safeLaunch {
            updateState { copy(fees = executionInfo?.fees) }
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
            val transactionTask = async { transactionHash?.let { safeRepository.loadPendingTransaction(it) } }
            val safeInfo = safeRepository.loadSafeInfo(safe)
            val transactionInfo = nullOnThrow { transactionTask.await() }
            val confirmationCount = transactionInfo?.confirmations?.size ?: 0
            val isOwner = safeInfo.owners.contains(deviceId)
            val hasConfirmed = transactionInfo?.let { it.confirmations.find { (address, _) -> address == deviceId } != null } ?: false
            val executed = !(transactionInfo?.executed == false || transactionHash == null)
            val txNonce = executionInfo?.nonce ?: safeInfo.currentNonce
            val submissionState = when {
                executed -> SubmissionState.EXECUTED
                txNonce < safeInfo.currentNonce -> SubmissionState.CANCELED
                hasConfirmed -> SubmissionState.CONFIRMED
                isOwner -> SubmissionState.AWAITING_CONFIRMATION
                else -> SubmissionState.PENDING
            }
            val state = TransactionState(
                confirmationCount, safeInfo.threshold.toInt(), submissionState
            )
            updateState { copy(loading = false, txState = state) }
        }
    }

    override fun confirmTransaction() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val execInfo = executionInfo ?: safeRepository.loadSafeTransactionExecutionInformation(safe, transaction)
            val hash = safeRepository.confirmSafeTransaction(safe, transaction, execInfo).addHexPrefix()
            updateState { copy(loading = false, signedHash = hash) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, null, null, null, null)

}

class TransactionConfirmationDialog(
    activity: FragmentActivity,
    safe: Solidity.Address,
    transactionHash: String?,
    transaction: SafeRepository.SafeTx,
    executionInfo: SafeRepository.SafeTxExecInfo? = null,
    callback: Callback? = null
) : BottomSheetDialog(activity), LifecycleOwner, ViewModelStoreOwner {

    private val rejectOnDismiss = executionInfo == null
    private val callback: WeakReference<Callback>? = (callback ?: (activity as? Callback))?.let { WeakReference(it) }

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {}

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            @SuppressLint("SwitchIntDef")
            when (newState) {
                STATE_EXPANDED -> confirm_tx_title.text = "Pull down to close"
                STATE_COLLAPSED -> confirm_tx_title.text = "Pull up for details"
            }
        }
    }

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
        behavior.peekHeight = context.resources.getDimension(R.dimen.confirmPeekHeight).toInt()
        behavior.addBottomSheetCallback(bottomSheetCallback)
        setContentView(R.layout.screen_confirm_tx)
        confirm_tx_submit_btn.setOnClickListener {
            viewModel.confirmTransaction()
        }
        viewModel.state.observe(this, Observer {
            confirm_tx_submit_btn.isEnabled = !it.loading
            if (it.signedHash != null) {
                callback?.get()?.onConfirmed(it.signedHash)
                callback?.clear()
                dismiss()
            }
            confirm_tx_fee_value.text = it.fees?.shiftedString(18)
            confirm_tx_asset_label.text = it.txInfo?.assetLabel
            confirm_tx_target_label.text = it.txInfo?.recipientLabel
            confirm_tx_target_icon.setAddress(it.txInfo?.recipient)
            confirm_tx_description.text = it.txInfo?.additionalInfo
            when (it.txState?.submissionState) {
                TransactionConfirmationContract.SubmissionState.AWAITING_CONFIRMATION -> {
                    confirm_tx_submit_btn.isVisible = true
                    confirm_tx_status.isVisible = false
                }
                TransactionConfirmationContract.SubmissionState.PENDING -> setStatus("Pending", R.color.pending)
                TransactionConfirmationContract.SubmissionState.CONFIRMED -> setStatus("Confirmed", R.color.confirmed)
                TransactionConfirmationContract.SubmissionState.CANCELED -> setStatus("Canceled", R.color.rejected)
                TransactionConfirmationContract.SubmissionState.EXECUTED -> setStatus("Executed", R.color.confirmed)
                null -> setStatus("Loading", R.color.pending)
            }
            confirm_tx_confirmations_indicator.max = it.txState?.threshold ?: 0
            confirm_tx_confirmations_indicator.progress = it.txState?.confirmations ?: 0
            confirm_tx_asset_icon.setTransactionIcon(picasso, it.txInfo?.assetIcon)
        })
        lifecycle.currentState = Lifecycle.State.CREATED
        setOnDismissListener {
            if (rejectOnDismiss) callback?.get()?.onRejected()
            lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun setStatus(message: String, color: Int) {
        confirm_tx_submit_btn.isVisible = false
        confirm_tx_status.isVisible = true
        confirm_tx_status.text = message
        confirm_tx_status.setBackgroundColor(context.getColorCompat(color))
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
        fun onConfirmed(hash: String)
        fun onRejected()
    }

}
