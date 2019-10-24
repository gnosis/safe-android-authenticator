package io.gnosis.safe.authenticator.ui.transactions

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.utils.shiftedString
import kotlinx.android.synthetic.main.screen_confirm_tx.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.android.getKoin
import org.koin.androidx.viewmodel.ViewModelParameters
import org.koin.androidx.viewmodel.getViewModel
import org.koin.core.parameter.parametersOf
import pm.gnosis.model.Solidity
import java.lang.ref.WeakReference
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class TransactionConfirmationContract : LoadingViewModel<TransactionConfirmationContract.State>() {
    abstract fun confirmTransaction()
    data class State(val loading: Boolean, val fees: BigInteger?, val confirmed: Boolean, override var viewAction: ViewAction?) : BaseViewModel.State
}

@ExperimentalCoroutinesApi
class TransactionConfirmationViewModel(
    private val safe: Solidity.Address,
    private val transaction: SafeRepository.SafeTx,
    private val executionInfo: SafeRepository.SafeTxExecInfo,
    private val safeRepository: SafeRepository
) : TransactionConfirmationContract() {

    override val state = liveData {
        loadFees()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private fun loadFees() {
        safeLaunch {
            updateState { copy(fees = executionInfo.fees) }
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

    override fun initialState() = State(false, null, false, null)

}

@ExperimentalCoroutinesApi
class TransactionConfirmationDialog(
    activity: AppCompatActivity,
    safe: Solidity.Address,
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
            parameters = { parametersOf(safe, transaction, executionInfo) }
        )
    )

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