package io.gnosis.safe.authenticator.utils

import android.widget.EditText
import android.widget.Toast
import io.gnosis.safe.authenticator.R
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.utils.asEthereumAddress
import timber.log.Timber

//Map functions that throw exceptions into optional types
inline fun <T> nullOnThrow(func: () -> T): T? = try {
    func.invoke()
} catch (e: Exception) {
    null
}

//Map functions that throw exceptions into optional types
inline fun loggedTry(func: () -> Unit) = try {
    func()
} catch (e: Exception) {
    Timber.e(e)
}

fun String.parseEthereumAddress() =
    removePrefix("ethereum:").asEthereumAddress()

fun EditText.useAsAddress(address: String) {
    address.parseEthereumAddress()?.let { setText(it.asEthereumAddressChecksumString()) }
        ?: Toast.makeText(context, context.getString(R.string.invalid_ethereum_address), Toast.LENGTH_SHORT).show()
}
