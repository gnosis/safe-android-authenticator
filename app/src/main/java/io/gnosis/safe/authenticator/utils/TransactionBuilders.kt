package io.gnosis.safe.authenticator.utils

import io.gnosis.safe.authenticator.BuildConfig
import io.gnosis.safe.authenticator.MultiSend
import io.gnosis.safe.authenticator.repositories.SafeRepository
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.hexStringToByteArray
import java.math.BigInteger

object MultiSendTransactionBuilder {

    private val MULTI_SEND_LIB = BuildConfig.MULTI_SEND_ADDRESS.asEthereumAddress()!!

    fun build(transactions: List<SafeRepository.SafeTx>): SafeRepository.SafeTx =
        SafeRepository.SafeTx(
            MULTI_SEND_LIB, BigInteger.ZERO, MultiSend.MultiSend.encode(
                    Solidity.Bytes(
                        transactions.joinToString(separator = "") {
                            val data = it.data.hexStringToByteArray()
                            Solidity.UInt8(it.operation.id.toBigInteger()).encodePacked() + // Operation
                                    it.to.encodePacked() + // To
                                    Solidity.UInt256(it.value).encodePacked() + // Value
                                    Solidity.UInt256(data.size.toBigInteger()).encodePacked() + // Data length
                                    Solidity.Bytes(data).encodePacked() // Data
                        }.hexStringToByteArray()
                    )
                ), SafeRepository.SafeTx.Operation.DELEGATE
        )
}
