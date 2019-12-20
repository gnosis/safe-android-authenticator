package io.gnosis.safe.authenticator.ui.intro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.overview.MainActivity
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity
import io.gnosis.safe.authenticator.utils.ExceptionUtils
import io.gnosis.safe.authenticator.utils.nullOnThrow
import io.gnosis.safe.authenticator.utils.useAsAddress
import kotlinx.android.synthetic.main.screen_connect_safe.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.hideSoftKeyboard
import pm.gnosis.utils.asEthereumAddress

abstract class ConnectSafeContract(context: Context) : LoadingViewModel<ConnectSafeContract.State>(context) {
    abstract fun checkAddress(address: String, immediate: Boolean = false)
    abstract fun setSafe(safe: Solidity.Address)

    data class State(
        val safe: Solidity.Address?,
        val done: Boolean,
        val loading: Boolean,
        override var viewAction: ViewAction?
    ) :
        BaseViewModel.State
}

class ConnectSafeViewModel(
    context: Context,
    private val safeRepository: SafeRepository
) : ConnectSafeContract(context) {

    override fun onStart() {
        checkState()
    }

    private fun checkState() {
        safeLaunch {
            nullOnThrow { safeRepository.loadSafeAddress() }?.let {
                updateState { copy(done = true) }
            }
        }
    }

    private var checkJob: Job? = null
    override fun checkAddress(address: String, immediate: Boolean) {
        loadingLaunch {
            updateState { copy(loading = true, safe = null) }
            checkJob?.cancel()
            checkJob = launch {
                delay(1000)
                val safe = address.asEthereumAddress() ?: throw IllegalArgumentException(context.getString(R.string.invalid_ethereum_address))
                try {
                    safeRepository.loadTokenBalances(safe)
                    updateState { copy(loading = false, safe = safe) }
                } catch (e: Exception) {
                    ExceptionUtils.rethrowWithMessage(context, e, ERROR_MAPPING)
                }
            }
        }
    }

    override fun setSafe(safe: Solidity.Address) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            safeRepository.setSafeAddress(safe)
            updateState { copy(loading = false, done = true) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(null, done = false, loading = false, viewAction = null)

    companion object {
        private val ERROR_MAPPING = mapOf(404 to R.string.unknown_safe_address)
    }
}

class ConnectSafeActivity : BaseActivity<ConnectSafeContract.State, ConnectSafeContract>() {
    override val viewModel: ConnectSafeContract by viewModel()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!QRCodeScanActivity.handleResult(requestCode, resultCode, data) { scanned ->
                connect_safe_address_input.useAsAddress(scanned)
            })
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_connect_safe)
        connect_safe_back_btn.setOnClickListener {
            onBackPressed()
        }
        connect_safe_address_scan.setOnClickListener {
            QRCodeScanActivity.startForResult(this, "Please scan a Safe address")
        }
        connect_safe_address_input.addTextChangedListener {
            viewModel.checkAddress(it.toString())
        }
    }

    override fun updateState(state: ConnectSafeContract.State) {
        if (state.done) {
            startActivity(MainActivity.createIntent(this))
            finishAffinity()
            return
        }
        connect_safe_submit_btn.isEnabled = !state.loading
        state.safe?.let { safe ->
            hideSoftKeyboard()
            connect_safe_address_ident.setAddress(safe)
            connect_safe_address_ident.isVisible = true
            connect_safe_submit_btn.text = getString(R.string.action_confirm)
            connect_safe_submit_btn.setOnClickListener {
                viewModel.setSafe(safe)
            }
        } ?: run {
            connect_safe_address_ident.setAddress(null)
            connect_safe_address_ident.isVisible = false
            connect_safe_submit_btn.text = getString(R.string.action_check_address)
            connect_safe_submit_btn.setOnClickListener {
                viewModel.checkAddress(connect_safe_address_input.text.toString(), true)
            }
        }
        connect_safe_progress.isVisible = state.loading
        val validSafe = !state.loading && state.safe != null
        connect_safe_address_status_icon.isVisible = validSafe
        connect_safe_address_status_text.isVisible = validSafe
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, ConnectSafeActivity::class.java)
    }

}
