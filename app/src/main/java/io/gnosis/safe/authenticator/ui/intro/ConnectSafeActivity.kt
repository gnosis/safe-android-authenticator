package io.gnosis.safe.authenticator.ui.intro

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.overview.MainActivity
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity
import io.gnosis.safe.authenticator.utils.*
import kotlinx.android.synthetic.main.screen_connect_safe.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.utils.asEthereumAddress

@ExperimentalCoroutinesApi
abstract class ConnectSafeContract : LoadingViewModel<ConnectSafeContract.State>() {
    abstract fun setSafe(address: String)

    data class State(
        val deviceId: String?,
        val deviceIdQR: Bitmap?,
        val done: Boolean,
        val loading: Boolean,
        override var viewAction: ViewAction?
    ) :
        BaseViewModel.State
}

@ExperimentalCoroutinesApi
class ConnectSafeViewModel(
    private val safeRepository: SafeRepository
) : ConnectSafeContract() {

    override val state = liveData {
        checkState()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private fun checkState() {
        loadingLaunch {
            updateState { copy(loading = true) }
            val deviceId = safeRepository.loadDeviceId()
            updateState {
                copy(
                    deviceId = deviceId.asEthereumAddressChecksumString(),
                    deviceIdQR = deviceId.asEthereumAddressChecksumString().generateQrCode(200, 200)
                )
            }
            val isReady = nullOnThrow { safeRepository.loadSafeAddress() } != null
            updateState { copy(loading = false, done = isReady) }
        }
    }

    override fun setSafe(address: String) {
        if (currentState().loading) return
        loadingLaunch {
            updateState { copy(loading = true) }
            val address = address.asEthereumAddress()!!
            safeRepository.setSafeAddress(address)
            updateState { copy(loading = false, done = true) }
        }
    }

    override fun onLoadingError(state: State, e: Throwable) = state.copy(loading = false)

    override fun initialState() = State(null, null, false, false, null)

}

@ExperimentalCoroutinesApi
class ConnectSafeActivity : BaseActivity<ConnectSafeContract.State, ConnectSafeContract>() {
    override val viewModel: ConnectSafeContract by viewModel()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!QRCodeScanActivity.handleResult(requestCode, resultCode, data, { scanned ->
                connect_safe_address_input.useAsAddress(scanned)
            }))
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_connect_safe)
        connect_safe_submit_btn.setOnClickListener {
            viewModel.setSafe(
                connect_safe_address_input.text.toString()
            )
        }
        connect_safe_address_scan.setOnClickListener {
            QRCodeScanActivity.startForResult(this, "Please scan a Safe address")
        }
    }

    override fun updateState(state: ConnectSafeContract.State) {
        if (state.done) {
            startActivity(MainActivity.createIntent(this))
            finishAffinity()
            return
        }
        connect_safe_content_scroll.isVisible = !state.loading
        connect_safe_progress.isVisible = state.loading
        connect_safe_device_id_qr.setImageBitmap(state.deviceIdQR)
        connect_safe_device_id.text = state.deviceId?.asMiddleEllipsized(4)
        if (state.deviceId != null) {
            connect_safe_device_id_qr.setOnClickListener {
                copyToClipboard("Your authenticator", state.deviceId) {
                    Toast.makeText(this@ConnectSafeActivity, "Copied device id to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
            connect_safe_device_id.setOnClickListener {
                copyToClipboard("Your authenticator", state.deviceId) {
                    Toast.makeText(this@ConnectSafeActivity, "Copied device id to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            connect_safe_device_id_qr.setOnClickListener(null)
            connect_safe_device_id.setOnClickListener(null)
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, ConnectSafeActivity::class.java)
    }

}
