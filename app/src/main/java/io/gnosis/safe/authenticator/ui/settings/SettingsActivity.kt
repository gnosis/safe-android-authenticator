package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.transactions.NewTransactionActivity
import io.gnosis.safe.authenticator.ui.walletconnect.WalletConnectStatusActivity
import io.gnosis.safe.authenticator.utils.asMiddleEllipsized
import io.gnosis.safe.authenticator.utils.copyToClipboard
import kotlinx.android.synthetic.main.screen_settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity

abstract class SettingsContract(context: Context) : BaseViewModel<SettingsContract.State>(context) {
    data class State(
        val safe: Solidity.Address?,
        val formattedSafe: String?,
        val deviceId: Solidity.Address?,
        val formattedId: String?,
        override var viewAction: ViewAction?
    ) : BaseViewModel.State
}

class SettingsViewModel(
    context: Context,
    private val safeRepository: SafeRepository
) : SettingsContract(context) {

    override fun onStart() {
        loadDeviceData()
    }

    private fun loadDeviceData() {
        safeLaunch {
            val deviceId = safeRepository.loadDeviceId()
            val formattedId = deviceId.asEthereumAddressChecksumString()
            val safe = safeRepository.loadSafeAddress()
            val formattedSafe = safe.asEthereumAddressChecksumString()
            updateState { copy(safe = safe, formattedSafe = formattedSafe, deviceId = deviceId, formattedId = formattedId) }
        }
    }

    override fun initialState() = State(null, null, null, null, null)

}

class SettingsActivity : BaseActivity<SettingsContract.State, SettingsContract>() {
    override val viewModel: SettingsContract by viewModel()

    override fun updateState(state: SettingsContract.State) {
        if (state.formattedId != null) {
            val clickListener = View.OnClickListener {
                copyToClipboard("Device Id", state.formattedId) {
                    Toast.makeText(this@SettingsActivity, "Copied device id to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
            settings_device_id_img.setOnClickListener(clickListener)
            settings_device_id_txt.setOnClickListener(clickListener)
        }
        settings_device_id_txt.text = state.formattedId?.asMiddleEllipsized(4)
        settings_device_id_img.setAddress(state.deviceId)
        if (state.formattedSafe != null) {
            val clickListener = View.OnClickListener {
                copyToClipboard("Safe Address", state.formattedSafe) {
                    Toast.makeText(this@SettingsActivity, "Copied Safe address to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
            settings_safe_img.setOnClickListener(clickListener)
            settings_safe_txt.setOnClickListener(clickListener)
        }
        settings_safe_txt.text = state.formattedSafe?.asMiddleEllipsized(4)
        settings_safe_img.setAddress(state.safe)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_settings)

        settings_back_btn.setOnClickListener { onBackPressed() }
        settings_custom_tx_txt.setOnClickListener { startActivity(NewTransactionActivity.createIntent(this)) }
        settings_manage_allowance_module_txt.setOnClickListener { startActivity(ManageAllowancesActivity.createIntent(this)) }
        settings_wallet_connect_txt.setOnClickListener { startActivity(WalletConnectStatusActivity.createIntent(this)) }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
