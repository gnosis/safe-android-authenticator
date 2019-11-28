package io.gnosis.safe.authenticator.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.gnosis.safe.authenticator.BuildConfig
import pm.gnosis.model.Solidity
import retrofit2.http.Body
import retrofit2.http.POST
import java.math.BigInteger


interface JsonRpcApi {
    @POST(".")
    suspend fun receipt(@Body jsonRpcRequest: JsonRpcRequest): JsonRpcTransactionReceiptResult

    @POST(".")
    suspend fun logs(@Body jsonRpcRequest: JsonRpcRequest):  JsonRpcLogsResult

    @POST(".")
    suspend fun post(@Body jsonRpcRequest: JsonRpcRequest): JsonRpcResult

    @POST(".")
    suspend fun post(@Body jsonRpcRequests: List<JsonRpcRequest>): List<JsonRpcResult>

    @JsonClass(generateAdapter = true)
    data class JsonRpcRequest(
        @Json(name = "jsonrpc") val jsonRpc: String = "2.0",
        @Json(name = "method") val method: String,
        @Json(name = "params") val params: List<Any> = emptyList(),
        @Json(name = "id") val id: Int = 1
    )

    @JsonClass(generateAdapter = true)
    data class JsonRpcResult(
        @Json(name = "id") val id: Int,
        @Json(name = "jsonrpc") val jsonRpc: String,
        @Json(name = "error") val error: JsonRpcError? = null,
        @Json(name = "result") val result: String?
    )

    @JsonClass(generateAdapter = true)
    data class JsonRpcError(
        @Json(name = "code") val code: Int,
        @Json(name = "message") val message: String
    )

    @JsonClass(generateAdapter = true)
    data class JsonRpcLogsResult(
        @Json(name = "id") val id: Int,
        @Json(name = "jsonrpc") val jsonRpc: String,
        @Json(name = "error") val error: JsonRpcError? = null,
        @Json(name = "result") val result: List<ContractLog>
    ) {
        @JsonClass(generateAdapter = true)
        data class ContractLog(
            @Json(name = "address") val address: String,
            @Json(name = "topics") val topics: List<String>,
            @Json(name = "data") val data: String,
            @Json(name = "transactionHash") val transactionHash: String

        )
    }

    @JsonClass(generateAdapter = true)
    data class JsonRpcTransactionReceiptResult(
        @Json(name = "id") val id: Int,
        @Json(name = "jsonrpc") val jsonRpc: String,
        @Json(name = "error") val error: JsonRpcError? = null,
        @Json(name = "result") val result: TransactionReceipt?
    ) {
        @JsonClass(generateAdapter = true)
        data class TransactionReceipt(
            @Json(name = "status") val status: BigInteger,
            @Json(name = "transactionHash") val transactionHash: String,
            @Json(name = "transactionIndex") val transactionIndex: BigInteger,
            @Json(name = "blockHash") val blockHash: String,
            @Json(name = "blockNumber") val blockNumber: BigInteger,
            @Json(name = "from") val from: Solidity.Address,
            @Json(name = "to") val to: Solidity.Address,
            @Json(name = "cumulativeGasUsed") val cumulativeGasUsed: BigInteger,
            @Json(name = "gasUsed") val gasUsed: BigInteger,
            @Json(name = "contractAddress") val contractAddress: Solidity.Address?,
            @Json(name = "logs") val logs: List<Event>
        ) {
            @JsonClass(generateAdapter = true)
            data class Event(
                @Json(name = "logIndex") val logIndex: BigInteger,
                @Json(name = "data") val data: String,
                @Json(name = "topics") val topics: List<String>
            )
        }
    }

    companion object {
        const val BASE_URL = BuildConfig.BLOCKCHAIN_NET_URL
    }
}
