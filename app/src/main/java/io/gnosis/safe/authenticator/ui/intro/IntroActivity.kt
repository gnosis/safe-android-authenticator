package io.gnosis.safe.authenticator.ui.intro

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.settings.SettingsActivity
import io.gnosis.safe.authenticator.ui.transactions.TransactionsActivity
import io.gnosis.safe.authenticator.utils.asMiddleEllipsized
import io.gnosis.safe.authenticator.utils.copyToClipboard
import io.gnosis.safe.authenticator.utils.generateQrCode
import io.gnosis.safe.authenticator.utils.nullOnThrow
import kotlinx.android.synthetic.main.item_pending_tx.view.*
import kotlinx.android.synthetic.main.screen_intro.*
import kotlinx.android.synthetic.main.screen_new_transaction.*
import kotlinx.android.synthetic.main.screen_transactions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.walleth.khex.toHexString
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.*
import pm.gnosis.utils.toHexString
import java.math.BigInteger

@ExperimentalCoroutinesApi
abstract class IntroContract : LoadingViewModel<IntroContract.State>() {
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
class IntroViewModel(
    private val safeRepository: SafeRepository
) : IntroContract() {

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
class IntroActivity : BaseActivity<IntroContract.State, IntroContract>() {
    override val viewModel: IntroContract by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_intro)
        intro_submit_btn.setOnClickListener {
            viewModel.setSafe(
                intro_address_input.text.toString()
            )
        }
    }

    override fun updateState(state: IntroContract.State) {
        if (state.done) {
            startActivity(TransactionsActivity.createIntent(this))
            finish()
            return
        }
        intro_content_scroll.isVisible = !state.loading
        intro_progress.isVisible = state.loading
        intro_device_id_qr.setImageBitmap(state.deviceIdQR)
        intro_device_id.text = state.deviceId?.asMiddleEllipsized(4)
        if (state.deviceId != null) {
            intro_device_id_qr.setOnClickListener {
                copyToClipboard("Your authenticator", state.deviceId) {
                    Toast.makeText(this@IntroActivity, "Copied device id to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
            intro_device_id.setOnClickListener {
                copyToClipboard("Your authenticator", state.deviceId) {
                    Toast.makeText(this@IntroActivity, "Copied device id to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            intro_device_id_qr.setOnClickListener(null)
            intro_device_id.setOnClickListener(null)
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, IntroActivity::class.java)
    }

}