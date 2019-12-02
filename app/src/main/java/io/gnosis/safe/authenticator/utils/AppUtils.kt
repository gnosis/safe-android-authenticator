package io.gnosis.safe.authenticator.utils

import android.widget.EditText
import android.widget.Toast
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.utils.asEthereumAddress

//Map functions that throw exceptions into optional types
inline fun <T> nullOnThrow(func: () -> T): T? = try {
    func.invoke()
} catch (e: Exception) {
    null
}

fun EditText.useAsAddress(address: String) {
    address.removePrefix("ethereum:").asEthereumAddress()?.let { setText(it.asEthereumAddressChecksumString()) }
        ?: Toast.makeText(context, "Invalid address provided!", Toast.LENGTH_SHORT).show()
}
