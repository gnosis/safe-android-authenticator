package io.gnosis.safe.authenticator.utils

import io.gnosis.safe.authenticator.data.JsonRpcApi
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddressString

suspend fun JsonRpcApi.performCall(to: Solidity.Address, data: String, from: Solidity.Address? = null) =
    post(
        JsonRpcApi.JsonRpcRequest(
            method = "eth_call",
            params = listOf(
                mutableMapOf(
                    "to" to to.asEthereumAddressString(),
                    "data" to data
                ).apply {
                    from?.let { put("from", from.asEthereumAddressString()) }
                },
                "latest"
            )
        )
    ).result!!
