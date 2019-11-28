package io.gnosis.safe.authenticator.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.gnosis.safe.authenticator.data.adapter.DecimalNumber
import pm.gnosis.model.Solidity
import java.math.BigInteger

@JsonClass(generateAdapter = true)
data class PaginatedResult<T>(
    @Json(name = "count") val count: Int,
    @Json(name = "next") val next: String?,
    @Json(name = "previous") val previous: String?,
    @Json(name = "results") val results: List<T>
)

@JsonClass(generateAdapter = true)
data class ServiceTransaction(
    @Json(name = "to") val to: String?,
    @Json(name = "value") val value: String,
    @Json(name = "data") val data: String?,
    @Json(name = "operation") val operation: Int,
    @Json(name = "gasToken") val gasToken: String?,
    @Json(name = "safeTxGas") val safeTxGas: String,
    @Json(name = "baseGas") val baseGas: String,
    @Json(name = "gasPrice") val gasPrice: String,
    @Json(name = "refundReceiver") val refundReceiver: String?,
    @Json(name = "nonce") val nonce: String,
    @Json(name = "safeTxHash") val safeTxHash: String,
    @Json(name = "submissionDate") val submissionDate: String,
    @Json(name = "executionDate") val executionDate: String?,
    @Json(name = "confirmations") val confirmations: List<ServiceConfirmation>,
    @Json(name = "isExecuted") val isExecuted: Boolean
)

@JsonClass(generateAdapter = true)
data class ServiceConfirmation(
    @Json(name = "owner") val owner: String,
    @Json(name = "submissionDate") val submissionDate: String,
    @Json(name = "signature") val signature: String?
)

@JsonClass(generateAdapter = true)
data class ServiceTransactionRequest(
    @Json(name = "to") val to: String?,
    @Json(name = "value") val value: String,
    @Json(name = "data") val data: String?,
    @Json(name = "operation") val operation: Int,
    @Json(name = "gasToken") val gasToken: String?,
    @Json(name = "safeTxGas") val safeTxGas: String,
    @Json(name = "baseGas") val baseGas: String,
    @Json(name = "gasPrice") val gasPrice: String,
    @Json(name = "refundReceiver") val refundReceiver: String?,
    @Json(name = "nonce") val nonce: String?,
    @Json(name = "contractTransactionHash") val safeTxHash: String,
    @Json(name = "sender") val sender: String,
    @Json(name = "confirmationType") val confirmationType: String,
    @Json(name = "signature") val signature: String?,
    @Json(name = "transactionHash") val transactionHash: String? = null
) {
    companion object {
        const val CONFIRMATION = "CONFIRMATION"
        const val EXECUTION = "EXECUTION"
    }
}

@JsonClass(generateAdapter = true)
data class ServiceBalance(
    @Json(name = "tokenAddress") val tokenAddress: Solidity.Address?,
    @DecimalNumber @Json(name = "balance") val balance: BigInteger
)
