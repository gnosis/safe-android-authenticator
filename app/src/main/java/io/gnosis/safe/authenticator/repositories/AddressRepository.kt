package io.gnosis.safe.authenticator.repositories

import android.content.Context
import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.GnosisSafe
import io.gnosis.safe.authenticator.data.JsonRpcApi
import io.gnosis.safe.authenticator.utils.performCall
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.utils.*
import java.net.IDN
import java.util.*

interface AddressRepository {

    suspend fun resolveEns(input: String): Solidity.Address
    suspend fun addRecentAddress(address: Solidity.Address)
    suspend fun recentAddresses(filter: String): List<Solidity.Address>
}

class AddressRepositoryImpl(
    private val context: Context,
    private val jsonRpcApi: JsonRpcApi
    ) : AddressRepository {
    private val recentAddressesPrefs = context.getSharedPreferences(PREFERENCES_RECENT_ADDRESSES, Context.MODE_PRIVATE)

    override suspend fun resolveEns(input: String): Solidity.Address {
        val node = normalize(input).split(".").foldRight<String, ByteArray?>(null) { part, node ->
            if (node == null && part.isEmpty()) ByteArray(32)
            else Sha3Utils.keccak((node ?: ByteArray(32)) + Sha3Utils.keccak(part.toByteArray()))
        } ?: ByteArray(32)
        val registerData = GET_RESOLVER + node.toHexString()
        val resolverAddress = jsonRpcApi.performCall(ENS_ADDRESS, registerData).asEthereumAddress()!!
        val resolverData = GET_ADDRESS + node.toHexString()
        return jsonRpcApi.performCall(resolverAddress, resolverData).asEthereumAddress()!!
    }

    private fun normalize(name: String) = IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.getDefault())

    override suspend fun addRecentAddress(address: Solidity.Address) {
        recentAddressesPrefs.edit { putLong(address.asEthereumAddressString(), System.currentTimeMillis()) }
    }

    override suspend fun recentAddresses(filter: String): List<Solidity.Address> =
        recentAddressesPrefs.all.mapNotNull { (address, lastUsed) ->
            if (!address.removeHexPrefix().startsWith(filter.trim().toLowerCase().removeHexPrefix())) return@mapNotNull null
            nullOnThrow { address.asEthereumAddress()!! to lastUsed as Long }
        }.sortedByDescending { (_, lastUsed) -> lastUsed }.take(5).map { (address) -> address }

    companion object {
        private val ENS_ADDRESS = BuildConfig.ENS_REGISTRY.asEthereumAddress()!!
        private const val PREFERENCES_RECENT_ADDRESSES = "preferences_recent_addresses"
        private const val GET_ADDRESS = "0x3b3b57de"
        private const val GET_RESOLVER = "0x0178b8bf"
    }
}
