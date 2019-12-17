package io.gnosis.safe.authenticator.ui.instant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import io.gnosis.safe.authenticator.R
import io.gnosis.safe.authenticator.ui.address.AddressInputActivity
import io.gnosis.safe.authenticator.ui.qr.QRCodeScanActivity
import io.gnosis.safe.authenticator.utils.parseEthereumAddress
import kotlinx.android.synthetic.main.screen_instant_transfer_address_input.*
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString

class NewInstantTransferAddressInputActivity : AppCompatActivity() {
    private var selectedToken: Solidity.Address? = null
    private var selectedAddress: Solidity.Address? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!QRCodeScanActivity.handleResult(requestCode, resultCode, data) { scanned ->
                scanned.parseEthereumAddress()?.let {
                    onAddress(it)
                } ?: Toast.makeText(this, getString(R.string.invalid_ethereum_address), Toast.LENGTH_SHORT).show()
            } && !AddressInputActivity.handleResult(requestCode, resultCode, data) { onAddress(it) })
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_instant_transfer_address_input)
        // use android.R.id.content to check keyboard
        selectedToken = intent.getStringExtra(EXTRA_SELECTED_TOKEN)?.asEthereumAddress()
        instant_transfer_address_input_back_btn.setOnClickListener { onBackPressed() }
        instant_transfer_address_input_continue_btn.setOnClickListener {
            val token = selectedToken ?: return@setOnClickListener
            val address = selectedAddress ?: return@setOnClickListener
            startActivity(NewInstantTransferValueInputActivity.createIntent(this, token, address))
        }
        instant_transfer_address_input_recipient_scan.setOnClickListener {
            QRCodeScanActivity.startForResult(this, "Please scan an Ethereum address")
        }
        instant_transfer_address_input_recipient_input.setOnClickListener {
            AddressInputActivity.startForResult(this)
        }
        savedInstanceState?.getString(EXTRA_SELECTED_ADDRESS)?.asEthereumAddress()?.let {
            onAddress(it)
        }
        instant_transfer_address_input_steps.defaultCount = 3
        instant_transfer_address_input_steps.setSelectedPage(0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_SELECTED_ADDRESS, selectedAddress?.asEthereumAddressString())
    }

    private fun onAddress(address: Solidity.Address) {
        selectedAddress = address
        instant_transfer_address_input_continue_btn.isEnabled = true
        instant_transfer_address_input_recipient_ident.setAddress(address)
        instant_transfer_address_input_recipient_ident.isVisible = true
        instant_transfer_address_input_recipient_input.text = address.asEthereumAddressChecksumString()
        instant_transfer_address_input_recipient_input.setTextColor(getColorCompat(R.color.darkText))
    }

    companion object {
        private const val EXTRA_SELECTED_TOKEN = "extra.string.selected_token"
        private const val EXTRA_SELECTED_ADDRESS = "extra.string.selected_address"
        fun createIntent(context: Context, selected: Solidity.Address) = Intent(context, NewInstantTransferAddressInputActivity::class.java).apply {
            putExtra(EXTRA_SELECTED_TOKEN, selected.asEthereumAddressString())
        }
    }

}
