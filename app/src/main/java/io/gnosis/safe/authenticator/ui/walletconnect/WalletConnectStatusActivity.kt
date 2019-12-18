package io.gnosis.safe.authenticator.ui.walletconnect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import com.squareup.picasso.Picasso
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.repositories.WalletConnectRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.base.LoadingViewModel
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity.Companion.handleResult
import io.gnosis.safe.authenticator.ui.transactions.TransactionConfirmationDialog
import kotlinx.android.synthetic.main.screen_wallet_connect_status.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.openUrl
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.hexAsBigIntegerOrNull
import pm.gnosis.utils.toHexString
import java.math.BigInteger

abstract class WalletConnectStatusContract(context: Context) : LoadingViewModel<WalletConnectStatusContract.State>(context) {

    abstract fun confirmTransaction(referenceId: Long?, hash: String)
    abstract fun rejectTransaction(referenceId: Long?)
    abstract fun createSession(uri: String)
    abstract fun disconnectSession()

    data class State(
        val loading: Boolean,
        val connected: Boolean,
        val dappName: String?,
        val dappIcon: String?,
        val dappUrl: String?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

class WalletConnectStatusViewModel(
    context: Context,
    private val walletConnectRepository: WalletConnectRepository
) : WalletConnectStatusContract(context) {

    override fun onStart() {
        observeRepo()
    }

    private fun observeRepo() {
        safeLaunch {
            for (event in walletConnectRepository.stateChannel()) {
                val session = walletConnectRepository.currentSession()
                updateState {
                    copy(
                        loading = false,
                        connected = session != null,
                        dappIcon = session?.icon,
                        dappName = session?.name,
                        dappUrl = session?.url
                    )
                }
            }
        }
    }

    override fun confirmTransaction(referenceId: Long?, hash: String) {
        referenceId ?: return
        walletConnectRepository.watchSafeTransaction(referenceId, hash)
    }

    override fun rejectTransaction(referenceId: Long?) {
        referenceId ?: return
        walletConnectRepository.rejectRequest(referenceId)
    }

    override fun createSession(uri: String) {
        safeLaunch {
            walletConnectRepository.createSession(uri)
        }
    }

    override fun disconnectSession() {
        safeLaunch {
            walletConnectRepository.closeSession()
        }
    }

    override fun initialState(): State = State(false, connected = false, dappName = null, dappIcon = null, dappUrl = null, viewAction = null)

    override fun onLoadingError(state: State, e: Throwable): State = state.copy(loading = false)

}

class WalletConnectStatusActivity : BaseActivity<WalletConnectStatusContract.State, WalletConnectStatusContract>(),
    TransactionConfirmationDialog.Callback {
    private val picasso: Picasso by inject()
    override val viewModel: WalletConnectStatusContract by viewModel()

    private var currentReferenceId: Long? = null

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.handleTxExtra()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleResult(requestCode, resultCode, data) {
            viewModel.createSession(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_wallet_connect_status)
        wallet_connect_status_back_btn.setOnClickListener { onBackPressed() }
        wallet_connect_status_scan_btn.setOnClickListener { QRCodeScanActivity.startForResult(this, "Scan a WalletConnect QR code") }
        wallet_connect_status_dapp_disconnect.setOnClickListener { viewModel.disconnectSession() }
        intent?.handleTxExtra()
    }

    override fun onConfirmed(hash: String) {
        viewModel.confirmTransaction(currentReferenceId, hash)
    }

    override fun onRejected() {
        viewModel.rejectTransaction(currentReferenceId)
    }

    private fun Intent.handleTxExtra() {
        val safe = getStringExtra(EXTRA_TRANSACTION_SAFE)?.asEthereumAddress() ?: return
        val to = getStringExtra(EXTRA_TRANSACTION_TO)?.asEthereumAddress() ?: return
        val value = getStringExtra(EXTRA_TRANSACTION_VALUE)?.hexAsBigIntegerOrNull() ?: BigInteger.ZERO
        val data = getStringExtra(EXTRA_TRANSACTION_DATA) ?: "0x"
        val referenceId = getLongExtra(EXTRA_TRANSACTION_REFERENCE_ID, 0)
        val tx = SafeRepository.SafeTx(to, value, data, SafeRepository.SafeTx.Operation.CALL)
        intent.removeExtra(EXTRA_TRANSACTION_SAFE)
        intent.removeExtra(EXTRA_TRANSACTION_TO)
        intent.removeExtra(EXTRA_TRANSACTION_VALUE)
        intent.removeExtra(EXTRA_TRANSACTION_DATA)
        intent.removeExtra(EXTRA_TRANSACTION_REFERENCE_ID)
        currentReferenceId?.let {
            currentReferenceId = null
            viewModel.rejectTransaction(it)
        }
        currentReferenceId = referenceId
        TransactionConfirmationDialog.show(
            supportFragmentManager,
            safe,
            null,
            tx
        )
    }

    override fun updateState(state: WalletConnectStatusContract.State) {
        wallet_connect_status_dapp_name.text = state.dappName ?: "Unknown dapp"
        wallet_connect_status_dapp_url.text = state.dappUrl
        if (state.dappUrl != null) {
            wallet_connect_status_dapp_url.setOnClickListener { openUrl(state.dappUrl) }
        } else {
            wallet_connect_status_dapp_url.setOnClickListener(null)
        }
        if (!state.dappIcon.isNullOrBlank()) {
            picasso.load(state.dappIcon).placeholder(R.drawable.circle_background).into(wallet_connect_status_dapp_icon)
        } else {
            wallet_connect_status_dapp_icon.setImageResource(R.drawable.circle_background)
        }
        wallet_connect_status_progress.isVisible = state.loading
        wallet_connect_status_dapp_info_group.isVisible = !state.loading && state.connected
        wallet_connect_status_hint.isVisible = !state.loading && !state.connected
    }

    companion object {
        private const val EXTRA_TRANSACTION_SAFE = "extra.string.safe"
        private const val EXTRA_TRANSACTION_TO = "extra.string.to"
        private const val EXTRA_TRANSACTION_VALUE = "extra.string.value"
        private const val EXTRA_TRANSACTION_DATA = "extra.string.data"
        private const val EXTRA_TRANSACTION_REFERENCE_ID = "extra.long.reference_id"

        fun createIntent(
            context: Context,
            safe: Solidity.Address? = null,
            to: Solidity.Address? = null,
            value: BigInteger? = null,
            data: String? = null,
            referenceId: Long? = null
        ) =
            Intent(context, WalletConnectStatusActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_SAFE, safe?.asEthereumAddressString())
                putExtra(EXTRA_TRANSACTION_TO, to?.asEthereumAddressString())
                putExtra(EXTRA_TRANSACTION_VALUE, value?.toHexString())
                putExtra(EXTRA_TRANSACTION_DATA, data)
                referenceId?.let { putExtra(EXTRA_TRANSACTION_REFERENCE_ID, it) }
            }
    }

}
