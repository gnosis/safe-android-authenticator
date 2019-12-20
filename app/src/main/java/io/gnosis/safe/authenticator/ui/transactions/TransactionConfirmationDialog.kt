package io.gnosis.safe.authenticator.ui.transactions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.GnosisSafe
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.createUrlIntent
import io.gnosis.safe.authenticator.utils.nullOnThrow
import io.gnosis.safe.authenticator.utils.setTransactionIcon
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_confirm_tx.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.utils.*
import java.lang.ref.WeakReference
import java.math.BigInteger

abstract class TransactionConfirmationContract(context: Context) : LoadingViewModel<TransactionConfirmationContract.State>(context) {
    abstract fun submitConfirmation(confirmationTxHash: String)
    abstract fun confirmTransaction()
    data class State(
        val loading: Boolean,
        val fees: BigInteger?,
        val signedHash: String?,
        val deeplink: Pair<String, String>?,
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

    private var cachedExecInfo: SafeRepository.SafeTxExecInfo? = null
    private var deferredExecInfo: Deferred<SafeRepository.SafeTxExecInfo>? = null
    private suspend fun getExecInfoAsync() =
        if (deferredExecInfo?.isActive == true)
            deferredExecInfo!!
        else
            viewModelScope.async {
                cachedExecInfo
                    ?: executionInfo
                    ?: safeRepository.loadSafeTransactionExecutionInformation(safe, transaction).also {
                        cachedExecInfo = it
                    }
            }.also { deferredExecInfo = it }

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
            val deeplink =
                if (submissionState != SubmissionState.EXECUTED && submissionState != SubmissionState.CANCELED) {
                    val execInfo = getExecInfoAsync().await()
                    if (safeInfo.threshold.toInt() <= confirmationCount) {
                        if (safeInfo.currentNonce != execInfo.nonce)
                            null // Cannot execute
                        else {
                            // TODO: how to get the address of the other wallet
                            val signatureString = transactionInfo?.confirmations
                                ?.sortedBy { it.first.value }
                                ?.joinToString(separator = "") { (owner, signature) ->
                                    signature?.removeHexPrefix() ?: (owner.encode() + Solidity.UInt256(BigInteger.ZERO).encode() + "01")
                                } ?: ""
                            val executeData = GnosisSafe.ExecTransaction.encode(
                                transaction.to,
                                Solidity.UInt256(transaction.value),
                                Solidity.Bytes(transaction.data.hexToByteArray()),
                                Solidity.UInt8(transaction.operation.id.toBigInteger()),
                                Solidity.UInt256(execInfo.txGas),
                                Solidity.UInt256(execInfo.baseGas),
                                Solidity.UInt256(execInfo.gasPrice),
                                execInfo.gasToken,
                                execInfo.refundReceiver,
                                Solidity.Bytes(signatureString.hexToByteArray())
                            )
                            context.getString(R.string.action_execute_external) to "ethereum:${safe.asEthereumAddressString()}?data=$executeData"
                        }
                    } else {
                        val safeTxHash = safeRepository.calculateSafeTransactionHash(safe, transaction, execInfo)
                        val confirmData = GnosisSafe.ApproveHash.encode(Solidity.Bytes32(safeTxHash.hexToByteArray()))
                        context.getString(R.string.action_confirm_external) to "ethereum:${safe.asEthereumAddressString()}?data=$confirmData"
                    }
                } else null
            updateState {
                copy(
                    loading = false,
                    txState = state,
                    deeplink = deeplink
                )
            }
        }
    }

    override fun submitConfirmation(confirmationTxHash: String) {
        safeLaunch {
            updateState { copy(loading = true) }
            val execInfo = getExecInfoAsync().await()
            val hash = safeRepository.submitOnChainConfirmationTransactionHash(safe, transaction, execInfo, confirmationTxHash)
            updateState { copy(loading = false, signedHash = hash) }
        }
    }

    override fun confirmTransaction() {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val execInfo = getExecInfoAsync().await()
            val hash = safeRepository.confirmSafeTransaction(safe, transaction, execInfo).addHexPrefix()
            updateState { copy(loading = false, signedHash = hash) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(false, null, null, null, null, null, null)

}

class TransactionConfirmationDialog : BottomSheetDialogFragment() {

    private val viewModel: TransactionConfirmationContract by viewModel(
        parameters = {
            parametersOf(
                arguments!!.getString(ARG_SAFE)!!.asEthereumAddress()!!,
                arguments!!.getString(ARG_TX_HASH),
                arguments!!.getParcelable(ARG_TX)!!,
                arguments!!.getParcelable(ARG_EXEC_INFO)
            )
        }
    )

    private val picasso: Picasso by inject()

    private var callback: WeakReference<Callback>? = null

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = ((parentFragment ?: context) as? Callback)?.let { WeakReference(it) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resp: Intent?) {
        if (requestCode == REQUEST_CODE_CONFIRM_TX && resultCode == Activity.RESULT_OK) {
            resp?.data?.host?.let {
                viewModel.submitConfirmation(it)
            } ?: Toast.makeText(context, "Invalid response received", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.screen_confirm_tx, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.apply {
            behavior.peekHeight = context.resources.getDimension(R.dimen.confirmPeekHeight).toInt()
            behavior.addBottomSheetCallback(bottomSheetCallback)
        }
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
            it.deeplink?.let { (label, link) ->
                confirm_tx_confirm_via_deeplink.isVisible = true
                confirm_tx_confirm_via_deeplink.text = label
                confirm_tx_confirm_via_deeplink.setOnClickListener {
                    try {
                        startActivityForResult(context?.createUrlIntent(link), REQUEST_CODE_CONFIRM_TX)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not find an external wallet", Toast.LENGTH_SHORT).show()
                    }
                }
            } ?: run {
                confirm_tx_confirm_via_deeplink.isVisible = false
                confirm_tx_confirm_via_deeplink.setOnClickListener(null)
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
        dialog?.setOnDismissListener {
            if (arguments!!.getBoolean(ARG_REJECT_ON_DISMISS)) callback?.get()?.onRejected()
        }
    }

    private fun setStatus(message: String, color: Int) {
        confirm_tx_submit_btn.isVisible = false
        confirm_tx_status.isVisible = true
        confirm_tx_status.text = message
        confirm_tx_status.setBackgroundColor(context!!.getColorCompat(color))
    }

    override fun onStart() {
        super.onStart()
        // IDK why this is required
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    interface Callback {
        fun onConfirmed(hash: String)
        fun onRejected()
    }

    companion object {
        private const val REQUEST_CODE_CONFIRM_TX = 666
        private const val ARG_SAFE = "argument.string.safe"
        private const val ARG_TX_HASH = "argument.string.transaction_hash"
        private const val ARG_TX = "argument.parcelable.transaction"
        private const val ARG_EXEC_INFO = "argument.parcelable.execution_info"
        private const val ARG_REJECT_ON_DISMISS = "argument.boolean.reject_on_dismiss"
        fun show(
            fragmentManager: FragmentManager,
            safe: Solidity.Address,
            transactionHash: String?,
            transaction: SafeRepository.SafeTx,
            executionInfo: SafeRepository.SafeTxExecInfo? = null
        ) {
            TransactionConfirmationDialog()
                .apply {
                    arguments = Bundle().apply {
                        putString(ARG_SAFE, safe.asEthereumAddressString())
                        putString(ARG_TX_HASH, transactionHash)
                        putParcelable(ARG_TX, transaction)
                        putParcelable(ARG_EXEC_INFO, executionInfo)
                        putBoolean(ARG_REJECT_ON_DISMISS, executionInfo == null)
                    }
                }
                .show(fragmentManager, null)
        }
    }

}
