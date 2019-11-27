package io.gnosis.safe.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.liveData
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.repositories.SafeRepository
import io.gnosis.safe.authenticator.ui.base.BaseActivity
import io.gnosis.safe.authenticator.ui.base.BaseViewModel
import io.gnosis.safe.authenticator.ui.transactions.NewTransactionActivity
import io.gnosis.safe.authenticator.utils.asMiddleEllipsized
import io.gnosis.safe.authenticator.utils.copyToClipboard
import kotlinx.android.synthetic.main.screen_settings.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.androidx.viewmodel.ext.android.viewModel
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity

@ExperimentalCoroutinesApi
abstract class SettingsContract : BaseViewModel<SettingsContract.State>() {
    data class State(val deviceId: Solidity.Address?, val formattedId: String?, override var viewAction: ViewAction?) : BaseViewModel.State
}

@ExperimentalCoroutinesApi
class SettingsViewModel(
    private val safeRepository: SafeRepository
) : SettingsContract() {
    override val state = liveData {
        loadDeviceData()
        for (event in stateChannel.openSubscription()) emit(event)
    }

    private fun loadDeviceData() {
        safeLaunch {
            val deviceId = safeRepository.loadSafeAddress()
            val formattedId = deviceId.asEthereumAddressChecksumString()
            updateState { copy(deviceId = deviceId, formattedId = formattedId) }
        }
    }

    override fun initialState() = State(null, null, null)

}

@ExperimentalCoroutinesApi
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_settings)

        settings_back_btn.setOnClickListener { onBackPressed() }
        settings_custom_tx_txt.setOnClickListener { startActivity(NewTransactionActivity.createIntent(this)) }
        settings_manage_allowance_module_txt.setOnClickListener { startActivity(ManageAllowancesActivity.createIntent(this)) }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
